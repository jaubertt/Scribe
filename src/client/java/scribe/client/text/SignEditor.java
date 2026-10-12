package scribe.client.text;

import scribe.client.compat.Keys;
import scribe.client.compat.TextKeys;
import scribe.client.widget.PageTextField;
import scribe.config.ScribeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.StringUtil;
import org.jspecify.annotations.Nullable;

public final class SignEditor {
	public static final int MAX_CHARS = 384;
	public static final int LINES = 4;

	public interface Line {
		String text();

		int cursor();

		int anchor();

		void set(String text, int cursor, int anchor);

		int maxWidth();
	}

	private final Font font;
	private final Line line;
	private final BooleanSupplier formatting;
	private final Supplier<Component> unavailable;
	private final Supplier<String> clipboard;
	private final Consumer<String> setClipboard;
	private final Consumer<Component> notice;
	private boolean altArmed;

	public SignEditor(Font font, Line line, BooleanSupplier formatting, Supplier<Component> unavailable, Supplier<String> clipboard,
		Consumer<String> setClipboard, Consumer<Component> notice) {
		this.font = font;
		this.line = line;
		this.formatting = formatting;
		this.unavailable = unavailable;
		this.clipboard = clipboard;
		this.setClipboard = setClipboard;
		this.notice = notice;
	}

	public boolean formattingAvailable() {
		return this.formatting.getAsBoolean();
	}

	private record Sel(String text, int cursor, int anchor) {
		int start() {
			return Math.min(this.cursor, this.anchor);
		}

		int end() {
			return Math.max(this.cursor, this.anchor);
		}
	}

	private Sel sel() {
		String text = this.line.text();
		if (text == null) {
			text = "";
		}
		return new Sel(text, Math.clamp(this.line.cursor(), 0, text.length()), Math.clamp(this.line.anchor(), 0, text.length()));
	}

	public boolean toggleStyle(char style) {
		if (!this.ready()) {
			return false;
		}
		Sel s = this.sel();
		return this.commit(s, FormatCodes.toggleStyle(s.text, s.start(), s.end(), style),
			style == 'l' ? "scribe.sign_tools.bold_too_long" : "scribe.sign_tools.too_long");
	}

	public boolean setColor(char color) {
		if (!this.ready()) {
			return false;
		}
		Sel s = this.sel();
		return this.commit(s, FormatCodes.setColor(s.text, s.start(), s.end(), color), "scribe.sign_tools.too_long");
	}

	public boolean clearFormatting() {
		if (!this.ready()) {
			return false;
		}
		Sel s = this.sel();
		return this.commit(s, FormatCodes.clear(s.text, s.start(), s.end()), "scribe.sign_tools.too_long");
	}

	public boolean insertSymbol(String symbol) {
		Sel s = this.sel();
		return this.commit(s, FormatCodes.replace(s.text, s.start(), s.end(), symbol), "scribe.sign_tools.too_long");
	}

	public boolean has(char style) {
		Sel s = this.sel();
		return s.start() != s.end() ? FormatCodes.allHave(s.text, s.start(), s.end(), style) : FormatCodes.stateAt(s.text, s.cursor).has(style);
	}

	private boolean ready() {
		if (this.formattingAvailable()) {
			return true;
		}
		this.notice.accept(this.unavailable.get());
		return false;
	}

	private boolean commit(Sel before, FormatCodes.Edit edit, @Nullable String tooWide) {
		if (!this.fits(edit.text(), before.text)) {
			if (tooWide != null) {
				this.notice.accept(Component.translatable(tooWide));
			}
			return false;
		}
		boolean caretAtEnd = before.cursor >= before.anchor;
		int start = edit.selectionStart();
		int end = edit.selectionEnd();
		this.line.set(trimEnd(edit.text(), end), caretAtEnd ? end : start, caretAtEnd ? start : end);
		return true;
	}

	static String trimEnd(String text, int keepTo) {
		int tail = FormatCodes.runStart(text, text.length());
		int cut = Math.max(tail, Math.clamp(keepTo, 0, text.length()));
		if (FormatCodes.splitsCode(text, cut)) {
			cut++;
		}
		return cut < text.length() ? text.substring(0, cut) : text;
	}

	public boolean fits(String text, String before) {
		if (text.length() > MAX_CHARS && text.length() > before.length()) {
			return false;
		}
		int width = this.font.width(text);
		return width <= this.line.maxWidth() || width <= this.font.width(before);
	}

	public static char shortcut(KeyEvent event) {
		return shortcut(event, Keys.mac());
	}

	public static char shortcut(KeyEvent event, boolean mac) {
		TextKeys.Shortcut shortcut = TextKeys.shortcut(event, mac);
		return switch (shortcut) {
			case BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, MAGIC -> shortcut.code();
			case CLEAR -> FormatCodes.RESET;
			default -> 0;
		};
	}

	public boolean keyPressed(KeyEvent event) {
		return this.keyPressed(event, Keys.mac());
	}

	public boolean keyPressed(KeyEvent event, boolean mac) {
		this.altArmed = isSectionKey(event) && !Keys.otherModifierHeld(event);
		char code = shortcut(event, mac);
		if (code != 0) {
			if (code == FormatCodes.RESET) {
				this.clearFormatting();
			} else {
				this.toggleStyle(code);
			}
			return true;
		}
		boolean codes = this.sel().text.indexOf(FormatCodes.SECTION) >= 0;
		if (event.isPaste() && (codes || this.formattingAvailable())) {
			this.paste();
			return true;
		}
		if (!codes) {
			return TextKeys.macOwnKey(event, mac) && this.caretKey(TextKeys.caret(event, mac), Keys.shift(event));
		}
		if (event.isCopy()) {
			this.setClipboard.accept(this.selectedWithLook());
			return true;
		}
		if (event.isCut()) {
			this.cut();
			return true;
		}
		return this.caretKey(TextKeys.caret(event, mac), Keys.shift(event));
	}

	private boolean caretKey(TextKeys.Caret caret, boolean select) {
		Sel s = this.sel();
		switch (caret) {
			case CHAR_LEFT -> this.moveTo(PageTextField.visualStep(s.text, s.cursor, -1), select);
			case CHAR_RIGHT -> this.moveTo(PageTextField.visualStep(s.text, s.cursor, 1), select);
			case WORD_LEFT -> this.moveTo(StringSplitter.getWordPosition(s.text, -1, s.cursor, true), select);
			case WORD_RIGHT -> this.moveTo(StringSplitter.getWordPosition(s.text, 1, s.cursor, true), select);
			case WORD_END -> this.moveTo(TextKeys.wordEnd(s.text, s.cursor), select);
			case LINE_START, TEXT_START -> this.moveTo(0, select);
			case LINE_END, TEXT_END -> this.moveTo(s.text.length(), select);
			case DELETE_CHAR_BACK -> this.delete(-1, false);
			case DELETE_CHAR_FORWARD -> this.delete(1, false);
			case DELETE_WORD_BACK -> this.delete(-1, true);
			case DELETE_WORD_FORWARD -> this.delete(1, true);
			case DELETE_TO_WORD_END -> this.deleteTo(TextKeys.wordEnd(s.text, s.cursor));
			case DELETE_TO_LINE_START -> this.deleteTo(0);
			case DELETE_TO_LINE_END -> this.deleteTo(s.text.length());
			default -> {
				return false;
			}
		}
		return true;
	}

	private void moveTo(int to, boolean select) {
		Sel s = this.sel();
		int p = PageTextField.restPosition(s.text, to);
		this.line.set(s.text, p, select ? s.anchor : p);
	}

	private void deleteTo(int target) {
		Sel s = this.sel();
		if (s.start() != s.end()) {
			this.commit(s, FormatCodes.replace(s.text, s.start(), s.end(), ""), null);
			return;
		}
		String t = s.text;
		int caret = s.cursor;
		int to = Math.clamp(target, 0, t.length());
		boolean visible = to < caret ? FormatCodes.runStart(t, caret) > to : FormatCodes.runEnd(t, caret) < to;
		if (!visible) {
			return;
		}
		FormatCodes.Edit edit = FormatCodes.replace(t, Math.min(caret, to), Math.max(caret, to), "");
		if (to > caret) {
			edit = new FormatCodes.Edit(edit.text(), caret, caret);
		}
		this.commit(s, edit, null);
	}

	public void afterKeyPressed(KeyEvent event) {
		if (event.isUp() || event.isDown() || event.isConfirmation()) {
			this.restCaret();
		}
	}

	public boolean keyReleased(KeyEvent event) {
		if (this.altArmed && isSectionKey(event)) {
			this.altArmed = false;
			if (this.formattingAvailable()) {
				this.type(String.valueOf(FormatCodes.SECTION));
				return true;
			}
		}
		return false;
	}

	public boolean charTyped(CharacterEvent event) {
		this.altArmed = false;
		if (event.codepoint() == FormatCodes.SECTION) {
			if (!this.formattingAvailable()) {
				return false;
			}
			this.type(String.valueOf(FormatCodes.SECTION));
			return true;
		}
		if (!event.isAllowedChatCharacter() || this.sel().text.indexOf(FormatCodes.SECTION) < 0) {
			return false;
		}
		this.type(event.codepointAsString());
		return true;
	}

	public void mouseClicked() {
		this.altArmed = false;
	}

	private static boolean isSectionKey(KeyEvent event) {
		return switch (ScribeConfig.get().sectionSignKey) {
			case LEFT_ALT -> Keys.isLeftAlt(event);
			case RIGHT_ALT -> Keys.isRightAlt(event);
			case OFF -> false;
		};
	}

	public void restCaret() {
		Sel s = this.sel();
		if (s.cursor != s.anchor || s.text.indexOf(FormatCodes.SECTION) < 0) {
			return;
		}
		int p = PageTextField.restPosition(s.text, s.cursor);
		if (p != s.cursor) {
			this.line.set(s.text, p, p);
		}
	}

	private void type(String text) {
		Sel s = this.sel();
		this.commit(s, FormatCodes.replace(s.text, s.start(), s.end(), text), null);
	}

	private void paste() {
		String text = clean(this.clipboard.get(), this.formattingAvailable() && ScribeConfig.get().pasteKeepsFormatting);
		Sel s = this.sel();
		if (text.isEmpty() && s.start() == s.end()) {
			return;
		}
		this.commit(s, FormatCodes.replace(s.text, s.start(), s.end(), text), "scribe.sign_tools.too_long");
	}

	private String selectedWithLook() {
		Sel s = this.sel();
		return s.start() == s.end() ? "" : FormatCodes.stateAt(s.text, s.start()).codes() + s.text.substring(s.start(), s.end());
	}

	private void cut() {
		Sel s = this.sel();
		this.setClipboard.accept(this.selectedWithLook());
		if (s.start() != s.end()) {
			this.commit(s, FormatCodes.replace(s.text, s.start(), s.end(), ""), null);
		}
	}

	private void delete(int direction, boolean word) {
		Sel s = this.sel();
		if (s.start() != s.end()) {
			this.commit(s, FormatCodes.replace(s.text, s.start(), s.end(), ""), null);
			return;
		}
		String t = s.text;
		int caret = s.cursor;
		int from;
		int to;
		if (word) {
			int other = StringSplitter.getWordPosition(t, direction, caret, true);
			from = Math.min(caret, other);
			to = Math.max(caret, other);
		} else if (direction < 0) {
			int q = FormatCodes.runStart(t, caret);
			if (q <= 0) {
				return;
			}
			to = q;
			from = q - stepBack(t, q);
		} else {
			int f = FormatCodes.runEnd(t, caret);
			if (f >= t.length()) {
				return;
			}
			from = f;
			to = f + stepForward(t, f);
		}
		if (from >= to) {
			return;
		}
		FormatCodes.Edit edit = FormatCodes.replace(t, from, to, "");
		if (direction > 0) {
			edit = new FormatCodes.Edit(edit.text(), caret, caret);
		}
		this.commit(s, edit, null);
	}

	private static int stepForward(String value, int p) {
		if (p + 1 < value.length()) {
			if (FormatCodes.startsPair(value, p)) {
				return 2;
			}
			if (Character.isHighSurrogate(value.charAt(p)) && Character.isLowSurrogate(value.charAt(p + 1))) {
				return 2;
			}
		}
		return 1;
	}

	private static int stepBack(String value, int p) {
		if (p >= 2) {
			if (FormatCodes.startsPair(value, p - 2)) {
				return 2;
			}
			if (Character.isLowSurrogate(value.charAt(p - 1)) && Character.isHighSurrogate(value.charAt(p - 2))) {
				return 2;
			}
		}
		return 1;
	}

	public static String clean(String text, boolean keepCodes) {
		String one = text == null ? "" : text.replaceAll("[\\r\\n]+$", "").replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
		StringBuilder out = new StringBuilder(one.length());
		for (int i = 0; i < one.length(); i++) {
			char c = one.charAt(i);
			if (c == FormatCodes.SECTION) {
				if (i + 1 < one.length() && ChatFormatting.getByCode(one.charAt(i + 1)) != null) {
					if (keepCodes) {
						out.append(c).append(Character.toLowerCase(one.charAt(i + 1)));
					}
					i++;
				} else if (i + 1 < one.length() && FormatCodes.isMarker(one.charAt(i + 1))) {
					i++;
				}
			} else if (StringUtil.isAllowedChatCharacter(c)) {
				out.append(c);
			}
		}
		return out.toString();
	}

	public static String fit(Font font, String line, int maxWidth) {
		String head = font.substrByWidth(FormattedText.of(line), maxWidth).getString();
		if (head.length() > MAX_CHARS) {
			head = head.substring(0, MAX_CHARS);
			if (FormatCodes.endsWithLoneSection(head) || Character.isHighSurrogate(head.charAt(head.length() - 1))) {
				head = head.substring(0, head.length() - 1);
			}
		}
		return head;
	}

	public record Lines(List<String> lines, boolean cut) {
	}

	public static Lines signLines(Font font, String text, int maxWidth, boolean keepCodes) {
		String[] parts = (text == null ? "" : text).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
		List<String> lines = new ArrayList<>(LINES);
		boolean cut = false;
		for (int i = 0; i < LINES; i++) {
			String clean = i < parts.length ? clean(parts[i], keepCodes) : "";
			String fitted = trimEnd(fit(font, clean, maxWidth), 0);
			cut |= !FormatCodes.strip(fitted).equals(FormatCodes.strip(clean));
			lines.add(fitted);
		}
		for (int i = LINES; i < parts.length; i++) {
			cut |= !FormatCodes.strip(clean(parts[i], false)).isBlank();
		}
		return new Lines(List.copyOf(lines), cut);
	}
}
