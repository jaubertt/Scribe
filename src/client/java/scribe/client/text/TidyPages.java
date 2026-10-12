package scribe.client.text;

import scribe.BookLimits;
import java.util.ArrayList;
import java.util.List;

public final class TidyPages {
	private TidyPages() {
	}

	public static List<String> tidy(List<String> pages, int from, PageFitter fitter) {
		if (from < 0 || from >= pages.size() || Chapters.isContentsPage(PageBreaks.text(pages.get(from)))) {
			return pages;
		}
		int end = from + 1;
		while (end < pages.size() && !Chapters.startsWithChapter(PageBreaks.text(pages.get(end)))
			&& !Chapters.isContentsPage(PageBreaks.text(pages.get(end)))) {
			end++;
		}
		String joined = PageBreaks.text(pages.get(from));
		for (int i = from + 1; i < end; i++) {
			joined = join(joined, pages.get(i), fitter);
		}
		List<String> laid = layout(joined, fitter);
		if (laid.size() > BookLimits.MAX_PAGES - from - (pages.size() - end)) {
			return pages;
		}
		laid.set(0, PageBreaks.withKind(laid.getFirst(), PageBreaks.markOf(pages.get(from))));
		List<String> out = new ArrayList<>(pages.subList(0, from));
		out.addAll(laid);
		out.addAll(pages.subList(end, pages.size()));
		return out;
	}

	static String join(String first, String second, PageFitter fitter) {
		String text = PageBreaks.text(second);
		PageBreaks.Kind kind = PageBreaks.between(first, second);
		if (kind.soft()) {
			return PageFlow.join(first, kind, text);
		}
		if (FormatCodes.strip(text).isEmpty()) {
			return first;
		}
		if (FormatCodes.strip(first).isEmpty()) {
			return text;
		}
		String reset = FormatCodes.transition(FormatCodes.stateAt(first, first.length()), FormatCodes.State.EMPTY);
		boolean full = !fitter.fits(first + " W");
		if (!full) {
			return first + reset + "\n" + text;
		}
		return first + reset + PageFlow.separator(first, text) + Alignment.dropMarks(text, 0, FormatCodes.runEnd(text, 0));
	}

	static List<String> layout(String text, PageFitter fitter) {
		List<String> pages = new ArrayList<>();
		String remaining = text;
		PageBreaks.Kind kind = PageBreaks.Kind.HARD;
		while (pages.size() <= BookLimits.MAX_PAGES) {
			int n = fitter.pageLength(remaining);
			int lineBreak = PageFitter.boundaryBreak(remaining, n);
			String rest = lineBreak >= 0
				? remaining.substring(n, lineBreak) + remaining.substring(lineBreak + 1)
				: remaining.substring(n);
			pages.add(PageBreaks.page(kind, remaining.substring(0, n)));
			if (FormatCodes.strip(rest).isBlank()) {
				break;
			}
			kind = lineBreak >= 0 ? PageBreaks.Kind.SOFT_NEWLINE : PageBreaks.Kind.SOFT;
			String carried = kind == PageBreaks.Kind.SOFT ? Alignment.carried(remaining, n) : "";
			remaining = FormatCodes.normalizeLead(carried + FormatCodes.stateAt(remaining, n).codes() + rest);
		}
		return pages;
	}
}
