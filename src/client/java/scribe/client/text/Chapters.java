package scribe.client.text;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import scribe.ContentsLinks;
import scribe.client.widget.PageTextField;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class Chapters {
	public static final String MARK = FormatCodes.SECTION + "#";
	public static final String CONTENTS_MARK = ContentsLinks.MARK;
	private static final char MARK_LETTER = '#';

	private Chapters() {
	}

	public record Chapter(int page, int start, String title) {
	}

	public static List<Chapter> find(List<String> pages) {
		List<Chapter> out = new ArrayList<>();
		for (int p = 0; p < pages.size(); p++) {
			String text = pages.get(p);
			if (isContentsPage(text)) {
				continue;
			}
			int start = 0;
			while (true) {
				int end = paragraphEnd(text, start);
				if (hasMark(text, start, end)) {
					String title = FormatCodes.strip(PageCodec.decodeText(text.substring(start, end))).strip();
					if (!title.isEmpty()) {
						out.add(new Chapter(p, start, title));
					}
				}
				if (end >= text.length()) {
					break;
				}
				start = end + 1;
			}
		}
		return out;
	}

	public static boolean hasMark(String text, int start, int end) {
		int i = start;
		while (i + 1 < end && FormatCodes.isCodeAt(text, i)) {
			if (text.charAt(i + 1) == MARK_LETTER) {
				return true;
			}
			i += 2;
		}
		return false;
	}

	public static boolean isContentsPage(String page) {
		return page.startsWith(CONTENTS_MARK);
	}

	public static boolean startsWithChapter(String page) {
		int start = 0;
		while (start <= page.length()) {
			int end = paragraphEnd(page, start);
			if (!FormatCodes.strip(page.substring(start, end)).isBlank()) {
				return hasMark(page, start, end);
			}
			if (end >= page.length()) {
				return false;
			}
			start = end + 1;
		}
		return false;
	}

	public record TitleRun(int first, int count) {
		public int last() {
			return this.first + this.count - 1;
		}

		public boolean has(int line) {
			return line >= this.first && line <= this.last();
		}
	}

	public static Set<Integer> titleLines(Font font, String page, int width) {
		Set<Integer> out = new HashSet<>();
		for (TitleRun run : titleRuns(font, page, width)) {
			for (int line = run.first(); line <= run.last(); line++) {
				out.add(line);
			}
		}
		return out;
	}

	public static List<TitleRun> titleRuns(Font font, String page, int width) {
		List<TitleRun> out = new ArrayList<>();
		int line = 0;
		int start = 0;
		while (true) {
			int end = paragraphEnd(page, start);
			String prefix = FormatCodes.stateAt(page, start).codes();
			int count = Math.max(1, font.split(Component.literal(prefix + page.substring(start, end)), width).size());
			if (hasMark(page, start, end)) {
				out.add(new TitleRun(line, count));
			}
			line += count;
			if (end >= page.length()) {
				return out;
			}
			start = end + 1;
		}
	}

	public static int paragraphStart(String text, int index) {
		int i = Math.clamp(index, 0, text.length());
		while (i > 0) {
			int nl = text.lastIndexOf('\n', i - 1);
			if (nl < 0) {
				return 0;
			}
			if (!PageCodec.isInsertedBreak(text, nl)) {
				return nl + 1;
			}
			i = nl;
		}
		return 0;
	}

	public static int paragraphEnd(String text, int index) {
		int from = Math.clamp(index, 0, text.length());
		while (true) {
			int nl = text.indexOf('\n', from);
			if (nl < 0) {
				return text.length();
			}
			if (!PageCodec.isInsertedBreak(text, nl)) {
				return nl;
			}
			from = nl + 1;
		}
	}

	public static FormatCodes.Edit toggle(String text, int caret) {
		int start = paragraphStart(text, caret);
		int end = paragraphEnd(text, caret);
		int visibleCaret = FormatCodes.strip(text.substring(0, Math.clamp(caret, 0, text.length()))).length();
		String result;
		if (hasMark(text, start, end)) {
			String without = removeMarks(text, start, end);
			int newEnd = end - (text.length() - without.length());
			result = hasVisible(without, start, newEnd) && FormatCodes.allHave(without, start, newEnd, 'l')
				? FormatCodes.restyle(without, start, newEnd, state -> state.without('l')).text()
				: without;
		} else {
			String marked = text.substring(0, start) + MARK + text.substring(start);
			int newEnd = end + MARK.length();
			if (!hasVisible(marked, start + MARK.length(), newEnd)) {
				result = FormatCodes.restyle(marked, start + MARK.length(), newEnd, state -> state.with('l')).text();
			} else if (!FormatCodes.allHave(marked, start + MARK.length(), newEnd, 'l')) {
				result = FormatCodes.restyle(marked, start + MARK.length(), newEnd, state -> state.with('l')).text();
			} else {
				result = marked;
			}
		}
		int c = PageTextField.restPosition(result, indexOfVisible(result, visibleCaret));
		return new FormatCodes.Edit(result, c, c);
	}

	private static String removeMarks(String text, int start, int end) {
		StringBuilder out = new StringBuilder(text.substring(0, start));
		int i = start;
		while (i + 1 < end && FormatCodes.isCodeAt(text, i)) {
			if (text.charAt(i + 1) != MARK_LETTER) {
				out.append(text, i, i + 2);
			}
			i += 2;
		}
		out.append(text.substring(i));
		return out.toString();
	}

	private static boolean hasVisible(String text, int from, int to) {
		return !FormatCodes.strip(text.substring(from, Math.min(to, text.length()))).isEmpty();
	}

	public static int indexOfVisible(String text, int visible) {
		int seen = 0;
		int i = 0;
		while (i < text.length() && seen < visible) {
			if (FormatCodes.startsPair(text, i)) {
				i += 2;
				continue;
			}
			seen++;
			i++;
		}
		return Math.min(i, text.length());
	}

	public static @Nullable String headerFor(List<Chapter> chapters, List<String> pages, int page) {
		if (page < 0 || page >= pages.size()) {
			return null;
		}
		String text = pages.get(page);
		if (isContentsPage(text) || startsWithChapter(text)) {
			return null;
		}
		Chapter current = null;
		for (Chapter chapter : chapters) {
			if (chapter.page() < page) {
				current = chapter;
			} else {
				break;
			}
		}
		return current == null ? null : current.title();
	}

	public static int nextChapterPage(List<Chapter> chapters, int page) {
		for (Chapter chapter : chapters) {
			if (chapter.page() > page) {
				return chapter.page();
			}
		}
		return -1;
	}

	public static int previousChapterPage(List<Chapter> chapters, int page) {
		int found = -1;
		for (Chapter chapter : chapters) {
			if (chapter.page() < page) {
				found = chapter.page();
			}
		}
		return found;
	}
}
