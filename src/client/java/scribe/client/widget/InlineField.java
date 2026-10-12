package scribe.client.widget;

import scribe.client.compat.Keys;
import scribe.client.compat.TextKeys;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.TextCursorUtils;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class InlineField {
	private final int maxLength;
	private String value = "";
	private int cursor;
	private boolean focused;
	private long focusTime = Util.getMillis();
	private Consumer<String> responder = value -> {};
	private Predicate<String> filter = value -> true;

	public InlineField(int maxLength) {
		this.maxLength = maxLength;
	}

	public String value() {
		return this.value;
	}

	public void setValue(String value) {
		String clean = value;
		if (value.length() > this.maxLength) {
			int end = this.maxLength;
			if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1)) && Character.isLowSurrogate(value.charAt(end))) {
				end--;
			}
			clean = value.substring(0, end);
		}
		this.value = clean;
		this.cursor = clean.length();
	}

	public InlineField responder(Consumer<String> responder) {
		this.responder = responder;
		return this;
	}

	public InlineField filter(Predicate<String> filter) {
		this.filter = filter;
		return this;
	}

	public int cursor() {
		return this.cursor;
	}

	public void setCursor(int cursor) {
		this.cursor = Math.clamp(cursor, 0, this.value.length());
		this.focusTime = Util.getMillis();
	}

	public boolean focused() {
		return this.focused;
	}

	public boolean caretVisible() {
		return this.focused && TextCursorUtils.isCursorVisible(Util.getMillis() - this.focusTime);
	}

	public void setFocused(boolean focused) {
		boolean was = this.focused;
		if (focused && !was) {
			this.focusTime = Util.getMillis();
		}
		this.focused = focused;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return;
		}
		if (focused) {
			minecraft.textInputManager().onTextInputFocusChange(this, true);
		} else if (was) {
			minecraft.textInputManager().onTextInputFocusChange(this, false);
			giveTextInputBack(minecraft);
		}
	}

	private static void giveTextInputBack(Minecraft minecraft) {
		Screen screen = minecraft.gui.screen();
		if (screen == null) {
			return;
		}
		minecraft.schedule(() -> {
			if (minecraft.gui.screen() != screen) {
				return;
			}
			GuiEventListener focusedElement = screen.getFocused();
			if (focusedElement != null && focusedElement.capturesInput()) {
				minecraft.onTextInputFocusChange(focusedElement, true);
			} else if (screen instanceof AbstractSignEditScreen) {
				minecraft.textInputManager().startTextInput(screen);
			}
		});
	}

	private void change(String newValue, int newCursor) {
		if (newValue.length() > this.maxLength || !this.filter.test(newValue)) {
			return;
		}
		boolean changed = !newValue.equals(this.value);
		this.value = newValue;
		this.cursor = Math.clamp(newCursor, 0, newValue.length());
		this.focusTime = Util.getMillis();
		if (changed) {
			this.responder.accept(newValue);
		}
	}

	public void insert(String text) {
		StringBuilder clean = new StringBuilder();
		text.codePoints().filter(cp -> cp != '\n' && cp != '\r' && StringUtil.isAllowedChatCharacter(cp)).forEach(clean::appendCodePoint);
		String add = clean.toString();
		int room = this.maxLength - this.value.length();
		if (add.length() > room) {
			add = add.substring(0, Math.max(0, room));
			if (!add.isEmpty() && Character.isHighSurrogate(add.charAt(add.length() - 1))) {
				add = add.substring(0, add.length() - 1);
			}
		}
		this.change(this.value.substring(0, this.cursor) + add + this.value.substring(this.cursor), this.cursor + add.length());
	}

	public boolean keyPressed(KeyEvent event) {
		return this.keyPressed(event, Keys.mac());
	}

	public boolean keyPressed(KeyEvent event, boolean mac) {
		if (!this.focused) {
			return false;
		}
		if (event.isPaste()) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft != null) {
				this.insert(minecraft.keyboardHandler.getClipboard());
			}
			return true;
		}
		if (event.isSelectAll()) {
			this.cursor = this.value.length();
			return true;
		}
		String v = this.value;
		int c = this.cursor;
		switch (TextKeys.caret(event, mac)) {
			case CHAR_LEFT -> this.moveTo(c > 0 ? v.offsetByCodePoints(c, -1) : 0);
			case CHAR_RIGHT -> this.moveTo(c < v.length() ? v.offsetByCodePoints(c, 1) : v.length());
			case WORD_LEFT -> this.moveTo(TextKeys.wordStart(v, c));
			case WORD_RIGHT -> this.moveTo(TextKeys.nextWordStart(v, c));
			case WORD_END -> this.moveTo(TextKeys.wordEnd(v, c));
			case LINE_START, TEXT_START -> this.moveTo(0);
			case LINE_END, TEXT_END -> this.moveTo(v.length());
			case DELETE_CHAR_BACK -> this.delete(c > 0 ? v.offsetByCodePoints(c, -1) : c, c);
			case DELETE_CHAR_FORWARD -> this.delete(c, c < v.length() ? v.offsetByCodePoints(c, 1) : c);
			case DELETE_WORD_BACK -> this.delete(TextKeys.wordStart(v, c), c);
			case DELETE_WORD_FORWARD -> this.delete(c, TextKeys.nextWordStart(v, c));
			case DELETE_TO_WORD_END -> this.delete(c, TextKeys.wordEnd(v, c));
			case DELETE_TO_LINE_START -> this.delete(0, c);
			case DELETE_TO_LINE_END -> this.delete(c, v.length());
			default -> {
				return false;
			}
		}
		return true;
	}

	private void moveTo(int cursor) {
		this.cursor = Math.clamp(cursor, 0, this.value.length());
		this.focusTime = Util.getMillis();
	}

	private void delete(int from, int to) {
		if (from < to) {
			this.change(this.value.substring(0, from) + this.value.substring(to), from);
		}
	}

	public boolean charTyped(CharacterEvent event) {
		if (!this.focused || !event.isAllowedChatCharacter()) {
			return false;
		}
		this.insert(event.codepointAsString());
		return true;
	}

	public void render(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int color, @Nullable String hint, int hintColor) {
		if (this.value.isEmpty() && hint != null && !this.focused) {
			graphics.text(font, font.plainSubstrByWidth(hint, width), x, y, hintColor, false);
			return;
		}
		String before = this.value.substring(0, this.cursor);
		int start = 0;
		while (font.width(this.value.substring(start, this.cursor)) > width - 2 && start < this.cursor) {
			start = this.value.offsetByCodePoints(start, 1);
		}
		String shown = font.plainSubstrByWidth(this.value.substring(start), width);
		if (this.value.isEmpty() && hint != null) {
			graphics.text(font, font.plainSubstrByWidth(hint, width), x, y, hintColor, false);
		}
		graphics.text(font, shown, x, y, color, false);
		if (this.focused && TextCursorUtils.isCursorVisible(Util.getMillis() - this.focusTime)) {
			int caretX = x + font.width(before.substring(Math.min(start, before.length())));
			graphics.fill(caretX, y - 1, caretX + 1, y + 9, color);
		}
	}
}
