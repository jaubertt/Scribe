package scribe.client.widget;

import scribe.BookLimits;
import scribe.client.screen.BookStyle;
import scribe.client.text.Chapters;
import scribe.client.text.FormatCodes;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.components.TextCursorUtils;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class PageEditor extends AbstractWidget {
	public static final int PADDING = 4;
	public static final int WIDTH = BookLimits.PAGE_WIDTH + PADDING * 2;
	public static final int HEIGHT = BookLimits.PAGE_LINES * BookLimits.LINE_HEIGHT + PADDING * 2;
	private static final int TEXT_COLOR = BookStyle.INK;
	private static final int CURSOR_COLOR = 0xFF3A2614;
	private static final int STYLE_MARK_COLOR = 0xFFB49C7A;
	private static final int CHAPTER_MARK_COLOR = 0xFFB2825A;
	private static final int CHAPTER_TEXT_COLOR = 0xFF3B2614;
	private static final int CHAPTER_BAR_COLOR = BookStyle.INK_RED;

	private final Font font;
	private final PageTextField field;
	private int pageIndex = -1;
	private boolean showMarks = true;
	private long focusedTime = Util.getMillis();
	private @Nullable IMEPreeditOverlay preedit;
	private Consumer<PageEditor> focusListener = editor -> {};

	private boolean pressedHere;

	public PageEditor(Font font, int textX, int textY) {
		super(textX - PADDING, textY - PADDING, WIDTH, HEIGHT, Component.translatable("book.edit.title"));
		this.font = font;
		this.field = new PageTextField(font);
	}

	public PageTextField field() {
		return this.field;
	}

	public String getValue() {
		return this.field.value();
	}

	public int pageIndex() {
		return this.pageIndex;
	}

	public void setPageIndex(int pageIndex) {
		this.pageIndex = pageIndex;
	}

	public void setShowMarks(boolean showMarks) {
		this.showMarks = showMarks;
	}

	public void setValueListener(Consumer<String> listener) {
		this.field.setValueListener(listener);
	}

	public void setFocusListener(Consumer<PageEditor> listener) {
		this.focusListener = listener;
	}

	public int textLeft() {
		return this.getX() + PADDING;
	}

	public int textTop() {
		return this.getY() + PADDING;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		String value = this.field.value();
		int left = this.textLeft();
		int top = this.textTop();
		int caret = this.field.cursor();
		boolean showCaret = this.isFocused() && TextCursorUtils.isCursorVisible(Util.getMillis() - this.focusedTime);
		boolean caretInside = caret < value.length();
		int caretX = left;
		int caretY = top;
		boolean caretPlaced = false;
		List<int[]> lines = this.field.lines();

		int y = top;
		for (int n = 0; n < lines.size(); n++) {
			int[] line = lines.get(n);
			String prefix = FormatCodes.stateAt(value, line[0]).codes();
			String text = value.substring(line[0], line[1]);
			int x = left + this.field.lineOffset(line);
			boolean title = isChapterTitle(value, line[0]);
			if (title) {
				graphics.fill(left - 7, y, left - 5, y + (titleGoesOn(value, lines, n) ? BookLimits.LINE_HEIGHT : 8), CHAPTER_BAR_COLOR);
			}
			graphics.text(this.font, prefix + text, x, y, title ? CHAPTER_TEXT_COLOR : TEXT_COLOR, false);
			if (this.showMarks) {
				this.drawMarks(graphics, value, line, prefix, x, y);
			}
			if (!caretPlaced && caret >= line[0] && caret <= line[1]) {
				caretX = x + this.font.width(prefix + value.substring(line[0], caret));
				caretY = y;
				caretPlaced = true;
			}
			y += BookLimits.LINE_HEIGHT;
		}
		if (!caretPlaced && !lines.isEmpty()) {
			int[] last = lines.getLast();
			caretX = left + this.field.lineOffset(last) + this.font.width(FormatCodes.stateAt(value, last[0]).codes() + value.substring(last[0], last[1]));
			caretY = top + (lines.size() - 1) * BookLimits.LINE_HEIGHT;
		}

		if (showCaret) {
			if (caretInside) {
				TextCursorUtils.extractInsertCursor(graphics, caretX, caretY, CURSOR_COLOR, BookLimits.LINE_HEIGHT + 1);
			} else {
				TextCursorUtils.extractAppendCursor(graphics, this.font, caretX, caretY, CURSOR_COLOR, false);
			}
		}

		if (this.field.hasSelection()) {
			this.drawSelection(graphics, value, lines, left, top);
		}

		if (this.isHovered()) {
			graphics.requestCursor(CursorTypes.IBEAM);
		}

		if (this.preedit == null) {
			if (this.capturesInput()) {
				Minecraft.getInstance().textInputManager().setTextInputArea(caretX, caretY, caretX + 1, caretY + BookLimits.LINE_HEIGHT + 1);
			}
		} else {
			this.preedit.updateInputPosition(caretX, caretY);
			graphics.setPreeditOverlay(this.preedit);
		}
	}

	private void drawSelection(GuiGraphicsExtractor graphics, String value, List<int[]> lines, int left, int top) {
		int start = this.field.selectionStart();
		int end = this.field.selectionEnd();
		int y = top;
		for (int[] line : lines) {
			if (start <= line[1] && end >= line[0]) {
				String prefix = FormatCodes.stateAt(value, line[0]).codes();
				int from = Math.max(start, line[0]);
				int to = Math.min(end, line[1]);
				int x = left + this.field.lineOffset(line);
				int x0 = x + this.font.width(prefix + value.substring(line[0], from));
				int x1 = end > line[1] ? Math.max(x0, left + BookLimits.PAGE_WIDTH) : x + this.font.width(prefix + value.substring(line[0], to));
				if (x1 > x0) {
					graphics.textHighlight(x0, y, x1, y + BookLimits.LINE_HEIGHT, true);
				}
			}
			y += BookLimits.LINE_HEIGHT;
		}
	}

	private static boolean isChapterTitle(String value, int lineStart) {
		return Chapters.hasMark(value, Chapters.paragraphStart(value, lineStart), Chapters.paragraphEnd(value, lineStart));
	}

	static boolean titleGoesOn(String value, List<int[]> lines, int n) {
		if (n + 1 >= lines.size() || !isChapterTitle(value, lines.get(n)[0])) {
			return false;
		}
		return Chapters.paragraphStart(value, lines.get(n + 1)[0]) == Chapters.paragraphStart(value, lines.get(n)[0]);
	}

	private void drawMarks(GuiGraphicsExtractor graphics, String value, int[] line, String prefix, int left, int y) {
		for (int i = line[0]; i < line[1]; i++) {
			if (FormatCodes.isCodeAt(value, i)) {
				char code = Character.toLowerCase(value.charAt(i + 1));
				int x = left + this.font.width(prefix + value.substring(line[0], i));
				int color = FormatCodes.isColor(code) ? 0xFF000000 | FormatCodes.rgb(code) : code == '#' ? CHAPTER_MARK_COLOR : STYLE_MARK_COLOR;
				graphics.fill(x, y + 8, x + 2, y + 9, color);
				i++;
			}
		}
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		this.pressedHere = true;
		if (doubleClick) {
			this.field.selectWordAtCursor();
		} else {
			this.field.seekToPoint(event.x() - this.textLeft(), event.y() - this.textTop(), event.hasShiftDown());
		}
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dx, double dy) {
		if (this.pressedHere) {
			this.field.seekToPoint(event.x() - this.textLeft(), event.y() - this.textTop(), true);
		}
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		this.pressedHere = false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return this.visible && this.isFocused() && this.field.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (this.visible && this.isFocused() && event.isAllowedChatCharacter()) {
			this.field.insertText(event.codepointAsString());
			return true;
		}
		return false;
	}

	@Override
	public boolean preeditUpdated(@Nullable PreeditEvent event) {
		this.preedit = event != null ? new IMEPreeditOverlay(event, this.font, BookLimits.LINE_HEIGHT + 1) : null;
		return true;
	}

	@Override
	public boolean capturesInput() {
		return this.visible && this.isFocused();
	}

	@Override
	public void setFocused(boolean focused) {
		super.setFocused(focused);
		if (focused) {
			this.focusedTime = Util.getMillis();
			this.focusListener.accept(this);
		} else {
			this.preedit = null;
		}
		Minecraft.getInstance().onTextInputFocusChange(this, focused);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.editBox", this.getMessage(), this.getValue()));
	}
}
