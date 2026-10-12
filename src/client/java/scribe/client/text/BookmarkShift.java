package scribe.client.text;

import scribe.client.memory.BookmarkStore.Bookmark;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class BookmarkShift {
	public static final int ANYWHERE = Integer.MAX_VALUE;
	public static final int PAGE_START = -1;
	static final char BREAK = '\0';
	private static final int MAX_DIFFERENCES = 500;

	private BookmarkShift() {
	}

	public record Anchored(Bookmark mark, int at) {
		public int page() {
			return this.mark.page();
		}
	}

	public static final class BookText {
		private final List<String> pages;
		private final String text;
		private final int[] starts;
		private final int[] textStarts;

		private BookText(List<String> pages, String text, int[] starts, int[] textStarts) {
			this.pages = pages;
			this.text = text;
			this.starts = starts;
			this.textStarts = textStarts;
		}

		public static BookText of(List<String> pages) {
			int n = pages.size();
			int[] starts = new int[n];
			int[] textStarts = new int[n];
			StringBuilder out = new StringBuilder();
			for (int k = 0; k < n; k++) {
				starts[k] = out.length();
				switch (PageBreaks.before(pages, k)) {
					case HARD -> out.append(BREAK);
					case SOFT_NEWLINE -> out.append('\n');
					case SOFT -> {
					}
				}
				textStarts[k] = out.length();
				out.append(FormatCodes.strip(PageBreaks.text(pages.get(k))));
			}
			return new BookText(pages, out.toString(), starts, textStarts);
		}

		public String text() {
			return this.text;
		}

		public int length() {
			return this.text.length();
		}

		public int start(int page) {
			return page >= this.starts.length ? this.text.length() : this.starts[page];
		}

		public int textStart(int page) {
			return page >= this.textStarts.length ? this.text.length() : this.textStarts[page];
		}

		public int pageAt(int offset) {
			int low = 0;
			int high = this.starts.length - 1;
			while (low < high) {
				int mid = (low + high + 1) >>> 1;
				if (this.starts[mid] <= offset) {
					low = mid;
				} else {
					high = mid - 1;
				}
			}
			return low;
		}

		public int at(int page, int pos) {
			if (page < 0 || page >= this.starts.length) {
				return this.text.length();
			}
			if (pos == PAGE_START) {
				return this.starts[page];
			}
			String raw = PageBreaks.text(this.pages.get(page));
			return this.textStarts[page] + FormatCodes.strip(raw.substring(0, Math.clamp(pos, 0, raw.length()))).length();
		}
	}

	public static List<Anchored> anchor(List<Bookmark> marks, List<String> pages) {
		if (marks.isEmpty()) {
			return List.of();
		}
		BookText text = BookText.of(pages);
		List<Anchored> out = new ArrayList<>();
		for (Bookmark mark : marks) {
			if (mark.page() >= 0 && mark.page() < pages.size()) {
				out.add(new Anchored(mark, text.start(mark.page())));
			}
		}
		return List.copyOf(out);
	}

	public static int pageStart(List<String> pages, int page) {
		return BookText.of(pages).start(page);
	}

	public static List<Bookmark> marks(List<Anchored> anchored) {
		return anchored.stream().map(Anchored::mark).toList();
	}

	public static int editAt(String before, String after, int caret) {
		int max = Math.min(before.length(), after.length());
		int i = 0;
		while (i < max && before.charAt(i) == after.charAt(i)) {
			i++;
		}
		return Math.max(0, Math.min(i, caret));
	}

	public static List<Bookmark> follow(List<Bookmark> marks, List<String> before, List<String> after, int from) {
		if (marks.isEmpty() || after.isEmpty()) {
			return after.isEmpty() ? List.of() : marks;
		}
		return marks(follow(anchor(marks, before), before, after, from, PAGE_START, Set.of()));
	}

	public static List<Anchored> follow(List<Anchored> marks, List<String> before, List<String> after, int page, int pos, Set<Integer> removed) {
		if (marks.isEmpty() || after.isEmpty()) {
			return List.of();
		}
		BookText old = BookText.of(before);
		BookText now = before.equals(after) ? old : BookText.of(after);
		int hint = page == ANYWHERE ? ANYWHERE : old.at(page, pos);
		Change change = Change.of(old.text(), now.text(), hint);
		List<Placed> placed = new ArrayList<>();
		for (Anchored mark : marks) {
			int was = mark.page();
			if (was < 0 || was >= before.size() || removed.contains(was)) {
				continue;
			}
			int at = Math.clamp(mark.at(), 0, Math.max(0, old.length() - 1));
			int moved = Math.clamp(change.map(at), 0, Math.max(0, now.length() - 1));
			int target = now.pageAt(moved);
			placed.add(new Placed(mark.mark(), moved, target, moved <= now.textStart(target), change.kept(at)));
		}
		return settle(placed, after.size());
	}

	private record Placed(Bookmark mark, int at, int target, boolean startsPage, boolean kept) {
		Anchored on(int page) {
			return new Anchored(page == this.mark.page() ? this.mark : new Bookmark(page, this.mark.color(), this.mark.name()), this.at);
		}
	}

	private static List<Anchored> settle(List<Placed> placed, int pageCount) {
		Map<Integer, Anchored> taken = new HashMap<>();
		List<Placed> owners = new ArrayList<>();
		List<Placed> others = new ArrayList<>();
		for (Placed p : placed) {
			(p.startsPage() ? owners : others).add(p);
		}
		owners.sort(Comparator.comparingInt(Placed::target).thenComparing(p -> !p.kept())
			.thenComparingInt(p -> Math.abs(p.mark().page() - p.target())).thenComparingInt(Placed::at));
		for (Placed p : owners) {
			taken.putIfAbsent(p.target(), p.on(p.target()));
		}
		others.sort(Comparator.comparingInt(Placed::at).thenComparingInt(p -> p.mark().page()));
		List<Placed> pushed = new ArrayList<>();
		for (Placed p : others) {
			if (taken.containsKey(p.target())) {
				pushed.add(p);
			} else {
				taken.put(p.target(), p.on(p.target()));
			}
		}
		for (Placed p : pushed) {
			int next = p.target() + 1;
			if (next < pageCount && !taken.containsKey(next)) {
				taken.put(next, p.on(next));
			}
		}
		List<Anchored> out = new ArrayList<>(taken.values());
		out.sort(Comparator.comparingInt(Anchored::page));
		return List.copyOf(out);
	}

	public static List<Bookmark> within(List<Bookmark> marks, int pageCount) {
		return marks.stream().filter(b -> b.page() >= 0 && b.page() < pageCount).toList();
	}

	static final class Change {
		private final int prefix;
		private final int oldEnd;
		private final int delta;
		private final int oldLength;
		private final String newMiddle;
		private final int @Nullable [] match;

		private Change(int prefix, int oldEnd, int delta, int oldLength, String newMiddle, int @Nullable [] match) {
			this.prefix = prefix;
			this.oldEnd = oldEnd;
			this.delta = delta;
			this.oldLength = oldLength;
			this.newMiddle = newMiddle;
			this.match = match;
		}

		static Change of(String before, String after, int hint) {
			int max = Math.min(before.length(), after.length());
			int prefix;
			int suffix;
			if (hint == ANYWHERE) {
				suffix = commonSuffix(before, after, max);
				prefix = commonPrefix(before, after, max - suffix);
			} else {
				prefix = commonPrefix(before, after, Math.clamp(hint, 0, max));
				suffix = commonSuffix(before, after, max - prefix);
			}
			String oldMiddle = before.substring(prefix, before.length() - suffix);
			String newMiddle = after.substring(prefix, after.length() - suffix);
			int[] match;
			if (oldMiddle.isEmpty() || newMiddle.isEmpty()) {
				match = new int[oldMiddle.length()];
				Arrays.fill(match, -1);
			} else {
				match = match(oldMiddle, newMiddle, MAX_DIFFERENCES);
			}
			return new Change(prefix, before.length() - suffix, after.length() - before.length(), oldMiddle.length(), newMiddle, match);
		}

		boolean kept(int at) {
			if (at < this.prefix || at >= this.oldEnd) {
				return true;
			}
			return this.match != null && this.match[at - this.prefix] >= 0;
		}

		int map(int at) {
			if (at < this.prefix) {
				return at;
			}
			if (at >= this.oldEnd) {
				return at + this.delta;
			}
			int i = at - this.prefix;
			if (this.newMiddle.isEmpty()) {
				return this.prefix;
			}
			if (this.match == null) {
				return this.prefix + (int) ((long) i * this.newMiddle.length() / this.oldLength);
			}
			if (this.match[i] >= 0) {
				return this.prefix + this.match[i];
			}
			int from = -1;
			for (int j = i - 1; j >= 0; j--) {
				if (this.match[j] >= 0) {
					from = this.match[j];
					break;
				}
			}
			int to = this.newMiddle.length();
			for (int j = i + 1; j < this.match.length; j++) {
				if (this.match[j] >= 0) {
					to = this.match[j];
					break;
				}
			}
			for (int j = from + 1; j < to; j++) {
				if (this.newMiddle.charAt(j) == BREAK) {
					return this.prefix + j;
				}
			}
			return this.prefix + to;
		}
	}

	static int commonPrefix(String a, String b, int limit) {
		int i = 0;
		while (i < limit && a.charAt(i) == b.charAt(i)) {
			i++;
		}
		return i;
	}

	static int commonSuffix(String a, String b, int limit) {
		int i = 0;
		while (i < limit && a.charAt(a.length() - 1 - i) == b.charAt(b.length() - 1 - i)) {
			i++;
		}
		return i;
	}

	public static int @Nullable [] match(String a, String b, int maxDifferences) {
		int n = a.length();
		int m = b.length();
		int limit = Math.min(n + m, maxDifferences);
		int offset = limit + 1;
		int[] v = new int[2 * limit + 3];
		int[][] trace = new int[limit + 1][];
		for (int d = 0; d <= limit; d++) {
			for (int k = -d; k <= d; k += 2) {
				int x = k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1]) ? v[offset + k + 1] : v[offset + k - 1] + 1;
				int y = x - k;
				while (x < n && y < m && a.charAt(x) == b.charAt(y)) {
					x++;
					y++;
				}
				v[offset + k] = x;
				if (x >= n && y >= m) {
					trace[d] = Arrays.copyOfRange(v, offset - d, offset + d + 1);
					return backtrack(trace, d, n, m);
				}
			}
			trace[d] = Arrays.copyOfRange(v, offset - d, offset + d + 1);
		}
		return null;
	}

	private static int[] backtrack(int[][] trace, int steps, int n, int m) {
		int[] match = new int[n];
		Arrays.fill(match, -1);
		int x = n;
		int y = m;
		for (int d = steps; d > 0; d--) {
			int[] previous = trace[d - 1];
			int k = x - y;
			boolean down = k == -d || (k != d && previous[k - 1 + d - 1] < previous[k + 1 + d - 1]);
			int previousK = down ? k + 1 : k - 1;
			int previousX = previous[previousK + d - 1];
			int startX = down ? previousX : previousX + 1;
			while (x > startX) {
				x--;
				y--;
				match[x] = y;
			}
			x = previousX;
			y = previousX - previousK;
		}
		while (x > 0 && y > 0) {
			x--;
			y--;
			match[x] = y;
		}
		return match;
	}
}
