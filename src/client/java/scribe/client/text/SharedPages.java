package scribe.client.text;

import java.util.ArrayDeque;
import java.util.Arrays;

public final class SharedPages {
	private final int[] starts;
	private final int pages;

	private SharedPages(int[] starts, int pages) {
		this.starts = starts;
		this.pages = pages;
	}

	public static SharedPages identity(int pages) {
		int[] starts = new int[Math.max(0, pages)];
		for (int i = 0; i < starts.length; i++) {
			starts[i] = i;
		}
		return new SharedPages(starts, starts.length);
	}

	public static SharedPages of(int[] starts, int pages) {
		if (starts.length == 0 || starts[0] != 0 || pages < starts.length) {
			throw new IllegalArgumentException("pages " + pages + " for lectern pages starting at " + Arrays.toString(starts));
		}
		for (int i = 1; i < starts.length; i++) {
			if (starts[i] <= starts[i - 1] || starts[i] >= pages) {
				throw new IllegalArgumentException("lectern pages out of order: " + Arrays.toString(starts) + " in " + pages);
			}
		}
		return new SharedPages(starts.clone(), pages);
	}

	public int pages() {
		return this.pages;
	}

	public int sharedPages() {
		return this.starts.length;
	}

	public int sharedPageOf(int page) {
		if (this.starts.length == 0) {
			return 0;
		}
		int found = Arrays.binarySearch(this.starts, Math.clamp(page, 0, this.pages - 1));
		return found >= 0 ? found : -found - 2;
	}

	public int firstPageOf(int shared) {
		if (this.starts.length == 0) {
			return 0;
		}
		return this.starts[Math.clamp(shared, 0, this.starts.length - 1)];
	}

	public int pagesOf(int shared) {
		if (this.starts.length == 0) {
			return 0;
		}
		int index = Math.clamp(shared, 0, this.starts.length - 1);
		int end = index + 1 < this.starts.length ? this.starts[index + 1] : this.pages;
		return end - this.starts[index];
	}

	public int request(int from, int to, int lecternPage) {
		if (this.starts.length == 0) {
			return -1;
		}
		int shared = this.sharedPageOf(to);
		return shared != this.sharedPageOf(from) || shared != lecternPage ? shared : -1;
	}

	public int follow(int current, int lecternPage) {
		if (this.starts.length == 0) {
			return 0;
		}
		int shared = Math.clamp(lecternPage, 0, this.starts.length - 1);
		return this.sharedPageOf(current) == shared && current >= 0 && current < this.pages ? current : this.starts[shared];
	}

	public static final class Turns {
		private final ArrayDeque<Integer> asked = new ArrayDeque<>();

		public int turn(SharedPages pages, int from, int to, int lecternPage) {
			Integer last = this.asked.peekLast();
			int request = pages.request(from, to, last == null ? lecternPage : last);
			if (request >= 0) {
				this.asked.addLast(request);
			}
			return request;
		}

		public int heard(SharedPages pages, int current, int lecternPage) {
			if (this.asked.contains(lecternPage)) {
				while (this.asked.pollFirst() != lecternPage) {
				}
				if (!this.asked.isEmpty()) {
					return current;
				}
			} else {
				this.asked.clear();
			}
			return pages.follow(current, lecternPage);
		}

		public void clear() {
			this.asked.clear();
		}

		public int waiting() {
			return this.asked.size();
		}
	}
}
