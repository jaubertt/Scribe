package scribe.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;
import scribe.client.text.Alignment;

public final class TextKeys {
	private TextKeys() {
	}

	public enum Shortcut {
		NONE, BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, MAGIC, CLEAR, ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT, CHAPTER, UNDO, REDO, SAVE, SPLIT_PAGE;

		public char code() {
			return switch (this) {
				case BOLD -> 'l';
				case ITALIC -> 'o';
				case UNDERLINE -> 'n';
				case STRIKETHROUGH -> 'm';
				case MAGIC -> 'k';
				case CLEAR -> 'r';
				default -> 0;
			};
		}

		public Alignment.@Nullable Mode alignment() {
			return switch (this) {
				case ALIGN_LEFT -> Alignment.Mode.LEFT;
				case ALIGN_CENTER -> Alignment.Mode.CENTER;
				case ALIGN_RIGHT -> Alignment.Mode.RIGHT;
				default -> null;
			};
		}
	}

	public static Shortcut shortcut(KeyEvent e) {
		return shortcut(e, Keys.mac());
	}

	public static Shortcut shortcut(KeyEvent e, boolean mac) {
		if (!Keys.ctrl(e, mac)) {
			return Shortcut.NONE;
		}
		boolean shift = Keys.shift(e);
		if (Keys.isEnter(e)) {
			return Shortcut.SPLIT_PAGE;
		}
		if (Keys.alt(e)) {
			return !shift && isDigitOne(e) ? Shortcut.CHAPTER : Shortcut.NONE;
		}
		if (Keys.ctrlBackslash(e, mac)) {
			return Shortcut.CLEAR;
		}
		return switch (e.shortcutKey()) {
			case 'b' -> shift ? Shortcut.NONE : Shortcut.BOLD;
			case 'i' -> shift ? Shortcut.NONE : Shortcut.ITALIC;
			case 'u' -> shift ? Shortcut.NONE : Shortcut.UNDERLINE;
			case 'k' -> shift ? Shortcut.NONE : Shortcut.MAGIC;
			case 'x' -> shift ? Shortcut.STRIKETHROUGH : Shortcut.NONE;
			case 'l' -> Shortcut.ALIGN_LEFT;
			case 'e' -> Shortcut.ALIGN_CENTER;
			case 'r' -> Shortcut.ALIGN_RIGHT;
			case 'z' -> shift ? Shortcut.REDO : Shortcut.UNDO;
			case 'y' -> Shortcut.REDO;
			case 's' -> Shortcut.SAVE;
			default -> Shortcut.NONE;
		};
	}

	public static boolean mayTypeText(KeyEvent e) {
		return Keys.alt(e);
	}

	private static boolean isDigitOne(KeyEvent e) {
		return e.shortcutKey() == '1' || e.input() == InputConstants.KEY_1;
	}

	public enum Caret {
		NONE,
		NOTHING,
		CHAR_LEFT, CHAR_RIGHT,
		WORD_LEFT,
		WORD_RIGHT,
		WORD_END,
		LINE_START, LINE_END,
		LINE_UP, LINE_DOWN,
		TEXT_START, TEXT_END,
		DELETE_CHAR_BACK, DELETE_CHAR_FORWARD,
		DELETE_WORD_BACK,
		DELETE_WORD_FORWARD,
		DELETE_TO_WORD_END,
		DELETE_TO_LINE_START,
		DELETE_TO_LINE_END;

		public boolean deletes() {
			return this.ordinal() >= DELETE_CHAR_BACK.ordinal();
		}

		public boolean moves() {
			return this.ordinal() >= CHAR_LEFT.ordinal() && !this.deletes();
		}
	}

	public static Caret caret(KeyEvent e) {
		return caret(e, Keys.mac());
	}

	public static Caret caret(KeyEvent e, boolean mac) {
		boolean ctrl = Keys.ctrl(e, mac);
		boolean option = mac && Keys.alt(e) && !ctrl;
		if (isLeft(e)) {
			return mac ? (ctrl ? Caret.LINE_START : option ? Caret.WORD_LEFT : Caret.CHAR_LEFT) : ctrl ? Caret.WORD_LEFT : Caret.CHAR_LEFT;
		}
		if (isRight(e)) {
			return mac ? (ctrl ? Caret.LINE_END : option ? Caret.WORD_END : Caret.CHAR_RIGHT) : ctrl ? Caret.WORD_RIGHT : Caret.CHAR_RIGHT;
		}
		if (isUp(e)) {
			if (option) {
				return Caret.NONE;
			}
			return ctrl ? (mac ? Caret.TEXT_START : Caret.NOTHING) : Caret.LINE_UP;
		}
		if (isDown(e)) {
			if (option) {
				return Caret.NONE;
			}
			return ctrl ? (mac ? Caret.TEXT_END : Caret.NOTHING) : Caret.LINE_DOWN;
		}
		if (isHome(e)) {
			return ctrl ? Caret.TEXT_START : Caret.LINE_START;
		}
		if (isEnd(e)) {
			return ctrl ? Caret.TEXT_END : Caret.LINE_END;
		}
		if (isBackspace(e)) {
			if (mac) {
				return ctrl ? Caret.DELETE_TO_LINE_START : option ? Caret.DELETE_WORD_BACK : Caret.DELETE_CHAR_BACK;
			}
			return ctrl ? Caret.DELETE_WORD_BACK : Caret.DELETE_CHAR_BACK;
		}
		if (isDelete(e)) {
			if (mac) {
				return ctrl ? Caret.DELETE_TO_LINE_END : option ? Caret.DELETE_TO_WORD_END : Caret.DELETE_CHAR_FORWARD;
			}
			return ctrl ? Caret.DELETE_WORD_FORWARD : Caret.DELETE_CHAR_FORWARD;
		}
		return Caret.NONE;
	}

	public static boolean macOwnKey(KeyEvent e, boolean mac) {
		Caret caret = caret(e, mac);
		return mac && caret != Caret.NONE && caret != Caret.NOTHING && (Keys.ctrl(e, true) || Keys.alt(e))
			&& !isHome(e) && !isEnd(e);
	}

	static boolean isLeft(KeyEvent e) {
		return e.input() == InputConstants.KEY_LEFT || e.shortcutKey() == InputConstants.KEYCODE_LEFT;
	}

	static boolean isRight(KeyEvent e) {
		return e.input() == InputConstants.KEY_RIGHT || e.shortcutKey() == InputConstants.KEYCODE_RIGHT;
	}

	static boolean isUp(KeyEvent e) {
		return e.input() == InputConstants.KEY_UP || e.shortcutKey() == InputConstants.KEYCODE_UP;
	}

	static boolean isDown(KeyEvent e) {
		return e.input() == InputConstants.KEY_DOWN || e.shortcutKey() == InputConstants.KEYCODE_DOWN;
	}

	static boolean isHome(KeyEvent e) {
		return e.input() == InputConstants.KEY_HOME || e.shortcutKey() == InputConstants.KEYCODE_HOME;
	}

	static boolean isEnd(KeyEvent e) {
		return e.input() == InputConstants.KEY_END || e.shortcutKey() == InputConstants.KEYCODE_END;
	}

	static boolean isBackspace(KeyEvent e) {
		return e.input() == InputConstants.KEY_BACKSPACE || e.shortcutKey() == InputConstants.KEYCODE_BACKSPACE;
	}

	static boolean isDelete(KeyEvent e) {
		return e.input() == InputConstants.KEY_DELETE || e.shortcutKey() == InputConstants.KEYCODE_DELETE;
	}

	public static int wordEnd(String text, int from) {
		int p = Math.clamp(from, 0, text.length());
		while (p < text.length() && Character.isWhitespace(text.charAt(p))) {
			p++;
		}
		while (p < text.length() && !Character.isWhitespace(text.charAt(p))) {
			p++;
		}
		return p;
	}

	public static int nextWordStart(String text, int from) {
		int p = Math.clamp(from, 0, text.length());
		while (p < text.length() && !Character.isWhitespace(text.charAt(p))) {
			p++;
		}
		while (p < text.length() && Character.isWhitespace(text.charAt(p))) {
			p++;
		}
		return p;
	}

	public static int wordStart(String text, int from) {
		int p = Math.clamp(from, 0, text.length());
		while (p > 0 && Character.isWhitespace(text.charAt(p - 1))) {
			p--;
		}
		while (p > 0 && !Character.isWhitespace(text.charAt(p - 1))) {
			p--;
		}
		return p;
	}
}
