package scribe.client.text;

import java.util.ArrayList;
import java.util.List;

public final class PageBreaks {
	public static final char SOFT_MARK = '+';
	public static final char NEWLINE_MARK = '&';
	public static final String SOFT = FormatCodes.SECTION + String.valueOf(SOFT_MARK);
	public static final String SOFT_NEWLINE = FormatCodes.SECTION + String.valueOf(NEWLINE_MARK);

	public enum Kind {
		HARD,
		SOFT,
		SOFT_NEWLINE;

		public boolean soft() {
			return this != HARD;
		}

		public String mark() {
			return switch (this) {
				case HARD -> "";
				case SOFT -> PageBreaks.SOFT;
				case SOFT_NEWLINE -> PageBreaks.SOFT_NEWLINE;
			};
		}

		public String joiner() {
			return this == SOFT_NEWLINE ? "\n" : "";
		}
	}

	private PageBreaks() {
	}

	public static boolean isMark(char c) {
		return c == SOFT_MARK || c == NEWLINE_MARK;
	}

	public static Kind markOf(String page) {
		if (page.length() >= 2 && page.charAt(0) == FormatCodes.SECTION) {
			if (page.charAt(1) == SOFT_MARK) {
				return Kind.SOFT;
			}
			if (page.charAt(1) == NEWLINE_MARK) {
				return Kind.SOFT_NEWLINE;
			}
		}
		return Kind.HARD;
	}

	public static String text(String page) {
		return markOf(page) == Kind.HARD ? page : page.substring(2);
	}

	public static String page(Kind kind, String text) {
		return kind.mark() + text;
	}

	public static String withKind(String page, Kind kind) {
		return page(kind, text(page));
	}

	public static Kind before(List<String> pages, int k) {
		if (k <= 0 || k >= pages.size()) {
			return Kind.HARD;
		}
		return between(text(pages.get(k - 1)), pages.get(k));
	}

	public static Kind between(String previousText, String next) {
		Kind kind = markOf(next);
		if (kind == Kind.HARD) {
			return Kind.HARD;
		}
		String nextText = text(next);
		if (blank(nextText) || Chapters.isContentsPage(previousText) || Chapters.isContentsPage(nextText)
			|| Chapters.startsWithChapter(nextText)) {
			return Kind.HARD;
		}
		return kind;
	}

	public static boolean blank(String text) {
		return FormatCodes.strip(text).isEmpty();
	}

	public static List<String> hardBefore(List<String> pages, int k) {
		if (k < 0 || k >= pages.size() || markOf(pages.get(k)) == Kind.HARD) {
			return pages;
		}
		List<String> out = new ArrayList<>(pages);
		out.set(k, text(pages.get(k)));
		return out;
	}

	public static List<String> clean(List<String> pages) {
		List<String> out = new ArrayList<>(pages.size());
		for (int k = 0; k < pages.size(); k++) {
			String page = pages.get(k);
			Kind kind = before(pages, k);
			out.add(kind == markOf(page) ? page : page(kind, text(page)));
		}
		return out;
	}
}
