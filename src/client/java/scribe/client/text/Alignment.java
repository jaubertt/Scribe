package scribe.client.text;

import scribe.BookLimits;
import scribe.client.widget.PageTextField;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;

public final class Alignment {
	public enum Mode { LEFT, CENTER, RIGHT }

	public static final char CENTER_MARK = '=';
	public static final char RIGHT_MARK = '>';

	private Alignment() {
	}

	public static boolean isMark(char c) {
		return c == CENTER_MARK || c == RIGHT_MARK;
	}

	public static String mark(Mode mode) {
		return switch (mode) {
			case LEFT -> "";
			case CENTER -> FormatCodes.SECTION + String.valueOf(CENTER_MARK);
			case RIGHT -> FormatCodes.SECTION + String.valueOf(RIGHT_MARK);
		};
	}

	public static Mode modeOf(String text, int paragraphStart) {
		int end = Chapters.paragraphEnd(text, paragraphStart);
		int i = Math.clamp(paragraphStart, 0, text.length());
		while (i + 1 < end && FormatCodes.isCodeAt(text, i)) {
			char code = text.charAt(i + 1);
			if (code == CENTER_MARK) {
				return Mode.CENTER;
			}
			if (code == RIGHT_MARK) {
				return Mode.RIGHT;
			}
			i += 2;
		}
		return Mode.LEFT;
	}

	public static Mode modeAt(String text, int index) {
		return modeOf(text, Chapters.paragraphStart(text, index));
	}

	public static boolean hasAligned(String text) {
		for (int i = text.indexOf(FormatCodes.SECTION); i >= 0 && i + 1 < text.length(); i = text.indexOf(FormatCodes.SECTION, i + 2)) {
			if (isMark(text.charAt(i + 1)) && FormatCodes.isCodeAt(text, i)) {
				return true;
			}
		}
		return false;
	}

	public static String dropMarks(String s, int from, int to) {
		int end = Math.min(to, s.length());
		StringBuilder out = new StringBuilder(s.length());
		out.append(s, 0, from);
		int i = from;
		while (i < end) {
			if (FormatCodes.isCodeAt(s, i) && i + 1 < end) {
				if (!isMark(s.charAt(i + 1))) {
					out.append(s, i, i + 2);
				}
				i += 2;
			} else {
				out.append(s.charAt(i));
				i++;
			}
		}
		return out.append(s.substring(end)).toString();
	}

	public static String carried(String text, int cut) {
		int start = Chapters.paragraphStart(text, cut);
		if (start >= cut) {
			return "";
		}
		return mark(modeOf(text, start));
	}

	public record BookEdit(List<String> pages, FormatCodes.Edit page) {
	}

	public static BookEdit set(List<String> pages, int index, int start, int end, Mode mode) {
		List<String> book = new ArrayList<>(pages);
		String value = PageBreaks.text(book.get(index));
		boolean before = PageBreaks.before(book, index) == PageBreaks.Kind.SOFT;
		boolean after = PageBreaks.before(book, index + 1) == PageBreaks.Kind.SOFT;
		FormatCodes.Edit edit = set(value, start, end, mode, before, after);
		book.set(index, PageBreaks.page(PageBreaks.markOf(book.get(index)), edit.text()));
		int from = Math.clamp(Math.min(start, end), 0, value.length());
		int to = Math.clamp(Math.max(start, end), 0, value.length());
		if (before && Chapters.paragraphStart(value, from) == 0) {
			for (int k = index - 1; k >= 0; k--) {
				String text = PageBreaks.text(book.get(k));
				int paragraph = Chapters.paragraphStart(text, text.length());
				boolean whole = paragraph == 0 && PageBreaks.before(book, k) == PageBreaks.Kind.SOFT;
				book.set(k, PageBreaks.page(PageBreaks.markOf(book.get(k)), set(text, paragraph, paragraph, mode, whole, true).text()));
				if (!whole) {
					break;
				}
			}
		}
		if (after && Chapters.paragraphEnd(value, to) == value.length()) {
			for (int k = index + 1; k < book.size(); k++) {
				String text = PageBreaks.text(book.get(k));
				boolean whole = Chapters.paragraphEnd(text, 0) == text.length() && PageBreaks.before(book, k + 1) == PageBreaks.Kind.SOFT;
				book.set(k, PageBreaks.page(PageBreaks.markOf(book.get(k)), set(text, 0, 0, mode, true, whole).text()));
				if (!whole) {
					break;
				}
			}
		}
		return new BookEdit(book, edit);
	}

	public static FormatCodes.Edit set(String text, int start, int end, Mode mode) {
		return set(text, start, end, mode, false, false);
	}

	public static FormatCodes.Edit set(String text, int start, int end, Mode mode, boolean continuesBefore, boolean continuesAfter) {
		int length = text.length();
		int a = Math.clamp(start, 0, length);
		int b = Math.clamp(end, 0, length);
		int first = Chapters.paragraphStart(text, Math.min(a, b));
		int last = Chapters.paragraphEnd(text, Math.max(a, b));
		StringBuilder out = new StringBuilder(length + 8);
		int[] map = new int[length + 1];
		for (int i = 0; i < first; i++) {
			map[i] = out.length();
			out.append(text.charAt(i));
		}
		int p = first;
		while (true) {
			int pe = Chapters.paragraphEnd(text, p);
			StringBuilder codes = new StringBuilder();
			boolean keepFront = continuesBefore && p == 0;
			int body = p;
			while (body < pe) {
				if (FormatCodes.isCodeAt(text, body) && body + 1 < pe) {
					if (!isMark(text.charAt(body + 1))) {
						codes.append(text, body, body + 2);
					}
					body += 2;
				} else if (text.charAt(body) == ' ' && !keepFront) {
					body++;
				} else {
					break;
				}
			}
			int bodyEnd = pe;
			while (bodyEnd > body && text.charAt(bodyEnd - 1) == ' ' && !(continuesAfter && pe == length)) {
				bodyEnd--;
			}
			out.append(mark(mode)).append(codes);
			int bodyOut = out.length();
			for (int i = p; i <= body; i++) {
				map[i] = bodyOut;
			}
			for (int i = body; i < bodyEnd; i++) {
				map[i] = out.length();
				out.append(text.charAt(i));
			}
			for (int i = bodyEnd; i <= pe; i++) {
				map[i] = out.length();
			}
			if (pe >= last) {
				for (int i = pe; i < length; i++) {
					map[i] = out.length();
					out.append(text.charAt(i));
				}
				map[length] = out.length();
				break;
			}
			out.append('\n');
			p = pe + 1;
		}
		String result = out.toString();
		if (a == b) {
			int caret = PageTextField.restPosition(result, map[a]);
			return new FormatCodes.Edit(result, caret, caret);
		}
		return new FormatCodes.Edit(result, map[a], map[b]);
	}

	public static int spaceWidth(Font font, FormatCodes.State state) {
		String codes = state.codes();
		return Math.max(1, font.width(codes + " ") - font.width(codes));
	}

	public static int rowWidth(Font font, String text, int rowStart, int rowEnd) {
		FormattedText row = FormattedText.of(text.substring(rowStart, rowEnd), FormatCodes.style(FormatCodes.stateAt(text, rowStart)));
		return font.width(row);
	}

	public static FormatCodes.State padState(String text, int rowStart, int rowEnd) {
		int at = Math.min(FormatCodes.runEnd(text, rowStart), Math.max(rowStart, rowEnd));
		return FormatCodes.applyInLine(FormatCodes.stateAt(text, rowStart), text.substring(rowStart, at));
	}

	public static int padSpaces(Font font, String text, int rowStart, int rowEnd, Mode mode) {
		if (mode == Mode.LEFT) {
			return 0;
		}
		int space = spaceWidth(font, padState(text, rowStart, rowEnd));
		int free = BookLimits.PAGE_WIDTH - rowWidth(font, text, rowStart, rowEnd);
		int spaces = mode == Mode.CENTER ? Math.round(free / 2.0F / space) : free / space;
		return Math.max(0, spaces);
	}

	public static int rowOffset(Font font, String text, int rowStart, int rowEnd) {
		Mode mode = modeAt(text, rowStart);
		if (mode == Mode.LEFT) {
			return 0;
		}
		return padSpaces(font, text, rowStart, rowEnd, mode) * spaceWidth(font, padState(text, rowStart, rowEnd));
	}

	public static FormatCodes.Edit align(PageFitter fitter, String text, int caret, Mode mode) {
		int lineStart = text.lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
		if (caret == 0) {
			lineStart = 0;
		}
		int lineEnd = text.indexOf('\n', caret);
		if (lineEnd < 0) {
			lineEnd = text.length();
		}
		String line = text.substring(lineStart, lineEnd);

		StringBuilder codes = new StringBuilder();
		int i = 0;
		while (i < line.length()) {
			if (FormatCodes.isCodeAt(line, i)) {
				codes.append(line, i, i + 2);
				i += 2;
			} else if (line.charAt(i) == ' ') {
				i++;
			} else {
				break;
			}
		}
		String core = codes + stripTrailingSpaces(line.substring(i));
		String state = FormatCodes.stateAt(text, lineStart).codes();

		if (fitter.lineCount(state + core) > 1) {
			return null;
		}

		String padded = core;
		if (mode != Mode.LEFT && !FormatCodes.strip(core).isEmpty()) {
			int coreWidth = fitter.width(state + core);
			int spaceWidth = Math.max(1, fitter.width(state + " ") - fitter.width(state));
			int free = BookLimits.PAGE_WIDTH - coreWidth;
			int spaces = mode == Mode.CENTER ? Math.round(free / 2.0F / spaceWidth) : free / spaceWidth;
			spaces = Math.max(0, spaces);
			padded = " ".repeat(spaces) + core;
			while (spaces > 0 && fitter.lineCount(state + padded) > 1) {
				spaces--;
				padded = " ".repeat(spaces) + core;
			}
		}

		String result = text.substring(0, lineStart) + padded + text.substring(lineEnd);
		int newCaret = lineStart + padded.length();
		return new FormatCodes.Edit(result, newCaret, newCaret);
	}

	private static String stripTrailingSpaces(String s) {
		int end = s.length();
		while (end > 0 && s.charAt(end - 1) == ' ') {
			end--;
		}
		return s.substring(0, end);
	}
}
