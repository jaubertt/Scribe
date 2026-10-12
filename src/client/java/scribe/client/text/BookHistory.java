package scribe.client.text;

import scribe.client.memory.BookmarkStore;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.util.Util;

public final class BookHistory {
	private static final int LIMIT = 200;
	private static final long TYPING_GROUP_MS = 1200;

	public record Snapshot(List<String> pages, int page, int cursor, int anchor, List<BookmarkStore.Bookmark> marks, List<Integer> marksAt) {
		public Snapshot {
			pages = List.copyOf(pages);
			marks = List.copyOf(marks);
			marksAt = marksAt.size() == marks.size() ? List.copyOf(marksAt) : List.of();
		}

		public Snapshot(List<String> pages, int page, int cursor, int anchor, List<BookmarkStore.Bookmark> marks) {
			this(pages, page, cursor, anchor, marks, List.of());
		}

		public Snapshot(List<String> pages, int page, int cursor, int anchor) {
			this(pages, page, cursor, anchor, List.of());
		}

		public List<BookmarkShift.Anchored> anchored() {
			if (this.marksAt.isEmpty()) {
				return BookmarkShift.anchor(this.marks, this.pages);
			}
			List<BookmarkShift.Anchored> out = new java.util.ArrayList<>();
			for (int i = 0; i < this.marks.size(); i++) {
				out.add(new BookmarkShift.Anchored(this.marks.get(i), this.marksAt.get(i)));
			}
			return List.copyOf(out);
		}

		public static Snapshot of(List<String> pages, int page, int cursor, int anchor, List<BookmarkShift.Anchored> marks) {
			return new Snapshot(pages, page, cursor, anchor, BookmarkShift.marks(marks), marks.stream().map(BookmarkShift.Anchored::at).toList());
		}
	}

	private final Deque<Snapshot> undo = new ArrayDeque<>();
	private final Deque<Snapshot> redo = new ArrayDeque<>();
	private long lastTypingTime;
	private int lastTypingPage = -1;

	public void record(Snapshot before, boolean typing) {
		if (before == null) {
			return;
		}
		long now = Util.getMillis();
		if (typing && before.page() == this.lastTypingPage && now - this.lastTypingTime < TYPING_GROUP_MS && !this.undo.isEmpty()) {
			this.lastTypingTime = now;
			this.redo.clear();
			return;
		}
		this.undo.push(before);
		while (this.undo.size() > LIMIT) {
			this.undo.removeLast();
		}
		this.redo.clear();
		this.lastTypingPage = typing ? before.page() : -1;
		this.lastTypingTime = now;
	}

	public void breakGroup() {
		this.lastTypingPage = -1;
	}

	public boolean canUndo() {
		return !this.undo.isEmpty();
	}

	public boolean canRedo() {
		return !this.redo.isEmpty();
	}

	public Snapshot undo(Snapshot current) {
		if (this.undo.isEmpty()) {
			return null;
		}
		this.redo.push(current);
		this.breakGroup();
		return this.undo.pop();
	}

	public Snapshot redo(Snapshot current) {
		if (this.redo.isEmpty()) {
			return null;
		}
		this.undo.push(current);
		this.breakGroup();
		return this.redo.pop();
	}
}
