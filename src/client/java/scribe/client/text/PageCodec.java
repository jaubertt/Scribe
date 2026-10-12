package scribe.client.text;

import scribe.BookLimits;
import scribe.PageText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;

public final class PageCodec {
	public static final char PAD_MARK = PageText.PAD_MARK;
	public static final char BREAK_SPACE_MARK = PageText.BREAK_SPACE_MARK;
	public static final char BREAK_WORD_MARK = PageText.BREAK_WORD_MARK;

	private PageCodec() {
	}

	public static boolean isBreakMark(char c) {
		return PageText.isBreakMark(c);
	}

	public static boolean isInsertedBreak(String text, int index) {
		return PageText.isInsertedBreak(text, index);
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
		return PageText.decodeText(saved);
	}
}
