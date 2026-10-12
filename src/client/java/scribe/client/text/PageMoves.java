package scribe.client.text;

import java.util.ArrayList;
import java.util.List;

public final class PageMoves {
	private PageMoves() {
	}

	public static List<String> move(List<String> pages, int from, int to) {
		if (from == to || from < 0 || to < 0 || from >= pages.size() || to >= pages.size()) {
			return pages;
		}
		List<String> out = new ArrayList<>(pages);
		String moved = PageBreaks.text(out.remove(from));
		if (from < out.size()) {
			out.set(from, PageBreaks.text(out.get(from)));
		}
		out.add(to, moved);
		if (to + 1 < out.size()) {
			out.set(to + 1, PageBreaks.text(out.get(to + 1)));
		}
		return out;
	}

	public static int newIndex(int page, int from, int to) {
		if (page == from) {
			return to;
		}
		if (from < to && page > from && page <= to) {
			return page - 1;
		}
		if (to < from && page >= to && page < from) {
			return page + 1;
		}
		return page;
	}
}
