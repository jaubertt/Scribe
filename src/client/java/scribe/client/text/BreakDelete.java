package scribe.client.text;

import scribe.client.widget.PageTextField;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public final class BreakDelete {
	private BreakDelete() {
	}

	public enum Kind {
		NOTHING,
		MOVE,
		EDIT,
		FULL
	}

	public record Outcome(Kind kind, List<String> pages, int caretPage, int caretPos, int removed, int editPage, int editPos) {
		static Outcome nothing() {
			return new Outcome(Kind.NOTHING, List.of(), -1, -1, -1, -1, -1);
		}

		static Outcome full() {
			return new Outcome(Kind.FULL, List.of(), -1, -1, -1, -1, -1);
		}

		static Outcome move(int page, int pos) {
			return new Outcome(Kind.MOVE, List.of(), page, pos, -1, -1, -1);
		}

		static Outcome edit(List<String> pages, int caretPage, int caretPos, int removed, int editPage, int editPos) {
			return new Outcome(Kind.EDIT, List.copyOf(pages), caretPage, caretPos, removed, editPage, editPos);
		}
	}

	public record Joined(String text, int headEnd, int nextStart) {
	}

	public static Outcome delete(List<String> pages, int index, int caret, PageFitter fitter, boolean flow, int maxPages) {
		if (index < 0 || index + 1 >= pages.size()) {
			return Outcome.nothing();
		}
		String text = PageBreaks.text(pages.get(index));
		String next = PageBreaks.text(pages.get(index + 1));
		if (PageBreaks.blank(text)) {
			if (blankFrom(pages, index + 1)) {
				return Outcome.nothing();
			}
			List<String> work = without(pages, index);
			return Outcome.edit(work, index, PageTextField.restPosition(PageBreaks.text(work.get(index)), 0), index, index, BookmarkShift.PAGE_START);
		}
		if (PageBreaks.blank(next)) {
			if (blankFrom(pages, index + 1)) {
				return Outcome.nothing();
			}
			return Outcome.edit(without(pages, index + 1), index, caret, index + 1, index + 1, BookmarkShift.PAGE_START);
		}
		if (Chapters.isContentsPage(text) || Chapters.isContentsPage(next)) {
			return Outcome.move(index + 1, 0);
		}
		PageBreaks.Kind kind = PageBreaks.before(pages, index + 1);
		int head = headLength(text);
		int c = Math.min(caret, head);
		if (kind.soft() && !flow) {
			List<String> work = new ArrayList<>(pages);
			if (kind == PageBreaks.Kind.SOFT_NEWLINE) {
				work.set(index + 1, oneParagraph(next));
				return Outcome.edit(work, index, c, -1, index + 1, BookmarkShift.PAGE_START);
			}
			int first = FormatCodes.runEnd(next, 0);
			work.set(index + 1, PageBreaks.page(kind, FormatCodes.replace(next, first, first + step(next, first), "").text()));
			return Outcome.edit(work, index, c, -1, index + 1, first);
		}
		List<String> work = without(pages, index + 1, false);
		String joined;
		int editPos;
		if (kind.soft()) {
			String both = PageFlow.join(text, kind, next);
			int from = kind == PageBreaks.Kind.SOFT_NEWLINE ? head : both.length() - (next.length() - FormatCodes.runEnd(next, 0));
			joined = FormatCodes.replace(both, from, from + (kind == PageBreaks.Kind.SOFT_NEWLINE ? 1 : step(both, from)), "").text();
			editPos = kind == PageBreaks.Kind.SOFT_NEWLINE ? BookmarkShift.PAGE_START : FormatCodes.runEnd(next, 0);
		} else {
			joined = join(text, next, fitter).text();
			editPos = BookmarkShift.PAGE_START;
		}
		PageFlow.Result result = PageFlow.reflow(work, index, joined, c, fitter, flow, maxPages);
		if (result == null) {
			return Outcome.full();
		}
		return Outcome.edit(result.pages(), result.caretPage(), result.caretPos(), -1, index + 1, editPos);
	}

	public static Outcome backspace(List<String> pages, int index, int caret, PageFitter fitter, boolean flow, int maxPages) {
		if (index <= 0 || index >= pages.size()) {
			return Outcome.nothing();
		}
		String previous = PageBreaks.text(pages.get(index - 1));
		String text = PageBreaks.text(pages.get(index));
		if (PageBreaks.blank(text)) {
			return Outcome.edit(without(pages, index), index - 1, -1, index, index, BookmarkShift.PAGE_START);
		}
		if (PageBreaks.blank(previous)) {
			return Outcome.edit(without(pages, index - 1), index - 1, caret, index - 1, index - 1, BookmarkShift.PAGE_START);
		}
		if (Chapters.isContentsPage(previous) || Chapters.isContentsPage(text)) {
			return Outcome.move(index - 1, -1);
		}
		PageBreaks.Kind kind = PageBreaks.before(pages, index);
		int head = headLength(previous);
		if (kind.soft() && !flow) {
			List<String> work = new ArrayList<>(pages);
			if (kind == PageBreaks.Kind.SOFT_NEWLINE) {
				String joined = oneParagraph(text);
				work.set(index, joined);
				String now = PageBreaks.text(joined);
				int at = PageTextField.restPosition(now, Math.max(0, caret - (text.length() - now.length())));
				return Outcome.edit(work, index, at, -1, index, BookmarkShift.PAGE_START);
			}
			int last = FormatCodes.runStart(previous, head);
			int from = last - stepBack(previous, last);
			work.set(index - 1, PageBreaks.page(PageBreaks.markOf(pages.get(index - 1)), FormatCodes.replace(previous, from, last, "").text()));
			return Outcome.edit(work, index, caret, -1, index - 1, from);
		}
		List<String> work = without(pages, index, false);
		String joined;
		int c;
		int editPage;
		int editPos;
		if (kind.soft()) {
			String both = PageFlow.join(previous, kind, text);
			int from;
			int to;
			if (kind == PageBreaks.Kind.SOFT_NEWLINE) {
				from = head;
				to = head + 1;
				editPage = index;
				editPos = BookmarkShift.PAGE_START;
			} else {
				to = FormatCodes.runStart(previous, head);
				from = to - stepBack(previous, to);
				editPage = index - 1;
				editPos = from;
			}
			FormatCodes.Edit edit = FormatCodes.replace(both, from, to, "");
			joined = edit.text();
			c = edit.selectionStart();
		} else {
			Joined both = join(previous, text, fitter);
			joined = both.text();
			c = both.nextStart();
			editPage = index;
			editPos = BookmarkShift.PAGE_START;
		}
		PageFlow.Result result = PageFlow.reflow(work, index - 1, joined, c, fitter, flow, maxPages);
		if (result == null) {
			return Outcome.full();
		}
		return Outcome.edit(result.pages(), result.caretPage(), result.caretPos(), -1, editPage, editPos);
	}

	public static Joined join(String first, String second, PageFitter fitter) {
		String head = FormatCodes.endsWithLoneSection(first) ? first.substring(0, first.length() - 1) : first;
		String reset = FormatCodes.transition(FormatCodes.stateAt(head, head.length()), FormatCodes.State.EMPTY);
		String visibleHead = FormatCodes.strip(head);
		String visibleNext = FormatCodes.strip(second);
		int lead = FormatCodes.runEnd(second, 0);
		String separator;
		String next = second;
		if (visibleHead.endsWith("\n")) {
			separator = "";
		} else if (visibleNext.startsWith("\n")) {
			separator = "";
			next = Alignment.dropMarks(second, 0, lead);
		} else if (Chapters.startsWithChapter(second)) {
			separator = "\n";
		} else if (!fitter.fits(head + " W")) {
			separator = PageFlow.separator(head, second);
			next = Alignment.dropMarks(second, 0, lead);
		} else {
			separator = "\n";
		}
		String joined = head + reset + separator + next;
		return new Joined(joined, head.length(), joined.length() - (next.length() - FormatCodes.runEnd(next, 0)));
	}

	private static int headLength(String text) {
		return FormatCodes.endsWithLoneSection(text) ? text.length() - 1 : text.length();
	}

	private static String oneParagraph(String text) {
		return PageBreaks.page(PageBreaks.Kind.SOFT, Alignment.dropMarks(text, 0, FormatCodes.runEnd(text, 0)));
	}

	private static List<String> without(List<String> pages, int index) {
		return without(pages, index, true);
	}

	private static List<String> without(List<String> pages, int index, boolean hard) {
		List<String> work = new ArrayList<>(pages);
		work.remove(index);
		if (work.isEmpty()) {
			work.add("");
		}
		return hard ? PageBreaks.hardBefore(work, index) : work;
	}

	private static boolean blankFrom(List<String> pages, int from) {
		return IntStream.range(from, pages.size()).allMatch(k -> PageBreaks.blank(PageBreaks.text(pages.get(k))));
	}

	private static int step(String text, int p) {
		if (p + 1 < text.length() && (FormatCodes.startsPair(text, p)
			|| Character.isHighSurrogate(text.charAt(p)) && Character.isLowSurrogate(text.charAt(p + 1)))) {
			return 2;
		}
		return 1;
	}

	private static int stepBack(String text, int p) {
		if (p >= 2 && (FormatCodes.startsPair(text, p - 2)
			|| Character.isLowSurrogate(text.charAt(p - 1)) && Character.isHighSurrogate(text.charAt(p - 2)))) {
			return 2;
		}
		return 1;
	}
}
