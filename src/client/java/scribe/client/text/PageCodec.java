package scribe.client.text;

import scribe.BookLimits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;

public final class PageCodec {
	public static final char PAD_MARK = '_';
	public static final char BREAK_SPACE_MARK = '/';
	public static final char BREAK_WORD_MARK = '|';

	private PageCodec() {
	}

	public static boolean isBreakMark(char c) {
		return c == BREAK_SPACE_MARK || c == BREAK_WORD_MARK;
	}

	public static boolean isInsertedBreak(String text, int index) {
		return index >= 2 && index < text.length() && text.charAt(index) == '\n'
			&& isBreakMark(text.charAt(index - 1)) && FormatCodes.isCodeAt(text, index - 2);
	}

	public static String encodeText(Font font, String text) {
		String clean = decodeText(text);
		if (!Alignment.hasAligned(clean)) {
			return clean;
		}
		List<int[]> rows = new ArrayList<>();
		font.getSplitter().splitLines(clean, BookLimits.PAGE_WIDTH, Style.EMPTY, false, (style, start, end) -> rows.add(new int[] {start, end}));
		StringBuilder out = new StringBuilder(clean.length() + 64);
		int copied = 0;
		for (int r = 0; r < rows.size(); r++) {
			int start = rows.get(r)[0];
			int end = rows.get(r)[1];
			int paragraph = Chapters.paragraphStart(clean, start);
			Alignment.Mode mode = Alignment.modeOf(clean, paragraph);
			if (mode == Alignment.Mode.LEFT) {
				continue;
			}
			if (start > paragraph && r > 0) {
				int previousEnd = rows.get(r - 1)[1];
				out.append(clean, copied, previousEnd)
					.append(FormatCodes.SECTION).append(previousEnd < start ? BREAK_SPACE_MARK : BREAK_WORD_MARK).append('\n');
				copied = start;
			}
			if (FormatCodes.strip(clean.substring(start, end)).isEmpty()) {
				continue;
			}
			int spaces = Alignment.padSpaces(font, clean, start, end, mode);
			if (spaces > 0) {
				int at = Math.min(FormatCodes.runEnd(clean, start), end);
				out.append(clean, copied, at).append(" ".repeat(spaces)).append(FormatCodes.SECTION).append(PAD_MARK);
				copied = at;
			}
		}
		return out.append(clean, copied, clean.length()).toString();
	}

	public static int savedLength(Font font, String page) {
		if (page.indexOf(FormatCodes.SECTION) < 0) {
			return page.length();
		}
		return PageBreaks.markOf(page).mark().length() + encodeText(font, PageBreaks.text(page)).length();
	}

	public static String encodePage(Font font, PageBreaks.Kind kind, String text) {
		String mark = kind.mark();
		String aligned = encodeText(font, text);
		if (mark.length() + aligned.length() <= BookLimits.PAGE_CHARS) {
			return mark + aligned;
		}
		if (aligned.length() <= BookLimits.PAGE_CHARS) {
			return aligned;
		}
		String plain = decodeText(text);
		return mark.length() + plain.length() <= BookLimits.PAGE_CHARS ? mark + plain : plain;
	}

	public static List<String> encodeBook(Font font, List<String> pages) {
		List<String> out = new ArrayList<>(pages.size());
		for (int k = 0; k < pages.size(); k++) {
			out.add(encodePage(font, PageBreaks.before(pages, k), PageBreaks.text(pages.get(k))));
		}
		return out;
	}

	public static String decode(String saved) {
		return PageBreaks.page(PageBreaks.markOf(saved), decodeText(PageBreaks.text(saved)));
	}

	public static List<String> decodeBook(List<String> saved) {
		List<String> out = new ArrayList<>(saved.size());
		for (String page : saved) {
			out.add(decode(page));
		}
		return out;
	}

	public static String decodeText(String saved) {
		if (saved.indexOf(FormatCodes.SECTION) < 0) {
			return saved;
		}
		StringBuilder out = new StringBuilder(saved.length());
		int rowStart = 0;
		for (int i = 0; i < saved.length(); i++) {
			char c = saved.charAt(i);
			if (c == '\n') {
				out.append(c);
				rowStart = out.length();
				continue;
			}
			if (!FormatCodes.isCodeAt(saved, i)) {
				out.append(c);
				continue;
			}
			char code = saved.charAt(i + 1);
			if (isBreakMark(code)) {
				if (i + 2 < saved.length() && saved.charAt(i + 2) == '\n') {
					if (code == BREAK_SPACE_MARK) {
						out.append(' ');
					}
					i++;
					rowStart = out.length();
				}
				i++;
			} else if (code == PAD_MARK) {
				int keep = out.length();
				while (keep > rowStart && out.charAt(keep - 1) == ' ') {
					keep--;
				}
				out.setLength(keep);
				i++;
			} else if (PageBreaks.isMark(code)) {
				i++;
			} else {
				out.append(c).append(code);
				i++;
			}
		}
		return out.toString();
	}
}
