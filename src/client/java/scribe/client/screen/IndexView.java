package scribe.client.screen;

import scribe.client.compat.Keys;

import scribe.BookLimits;
import scribe.client.memory.BookmarkStore;
import scribe.client.text.Chapters;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import scribe.Scribe;

public final class IndexView {
	private static final int CHAPTER_ROW = 11;
	private static final int BOOKMARK_ROW = 13;
	private static final int CHAPTERS_BOTTOM = 70;
	private static final int BOOKMARKS_BELOW = 18;
	private static final int HOVER_FILL = 0x307B593D;

	public interface Host {
		List<Chapters.Chapter> chapters();

		List<BookmarkStore.Bookmark> bookmarks();

		boolean showsBookmarks();

		default boolean writing() {
			return false;
		}

		String firstWords(int page);

		void goTo(int page);

		int totalPages();

		default void bookmarkMenu(int page, int x, int y) {
		}

		default void bookmarkTooltip(GuiGraphicsExtractor graphics, BookmarkStore.Bookmark bookmark, int mouseX, int mouseY) {
		}
	}

	private record Row(int x, int y, int width, int height, Runnable action, @Nullable Runnable menu) {
		boolean contains(double mx, double my) {
			return mx >= this.x && mx < this.x + this.width && my >= this.y && my < this.y + this.height;
		}
	}

	private final Host host;
	private final List<Row> rows = new ArrayList<>();
	private int chapterScroll;
	private int bookmarkScroll;
	private final WheelSteps wheel = new WheelSteps();
	private int bookmarkTop = Integer.MAX_VALUE;

	public IndexView(Host host) {
		this.host = host;
	}

	public void render(GuiGraphicsExtractor graphics, Font font, BookGeometry geo, int mouseX, int mouseY) {
		this.rows.clear();
		this.bookmarkTop = Integer.MAX_VALUE;
		int top = geo.textY();
		int bottom = geo.pageNumberY() - 2;
		this.title(graphics, font, Component.translatable("scribe.index.contents"), geo.pageCenterX(0), top);
		this.count(graphics, font, geo.pageCenterX(0), top + 11);
		if (geo.spread) {
			this.chapters(graphics, font, geo.textX(0), top + 24, bottom, bottom, mouseX, mouseY);
			if (this.host.showsBookmarks()) {
				this.title(graphics, font, Component.translatable("scribe.index.bookmarks"), geo.pageCenterX(1), top);
				this.bookmarks(graphics, font, geo.textX(1), top + 18, bottom, mouseX, mouseY);
				if (!this.host.bookmarks().isEmpty()) {
					BookStyle.centered(graphics, font, Component.translatable("scribe.index.bookmark_hint"), geo.pageCenterX(1), geo.pageNumberY(), BookStyle.INK_SOFT);
				}
			}
		} else if (this.host.showsBookmarks()) {
			this.chaptersAndBookmarks(graphics, font, geo, top, bottom, mouseX, mouseY);
		} else {
			this.chapters(graphics, font, geo.textX(0), top + 24, bottom, bottom, mouseX, mouseY);
		}
	}

	private void chaptersAndBookmarks(GuiGraphicsExtractor graphics, Font font, BookGeometry geo, int top, int bottom, int mouseX, int mouseY) {
		int left = geo.textX(0);
		int room = this.host.bookmarks().isEmpty()
			? font.split(Component.translatable("scribe.index.no_bookmarks"), BookLimits.PAGE_WIDTH).size() * font.lineHeight
			: 2 * BOOKMARK_ROW;
		int y = this.chapters(graphics, font, left, top + 24, top + CHAPTERS_BOTTOM, bottom - BOOKMARKS_BELOW - room, mouseX, mouseY);
		this.bookmarkTop = y;
		this.title(graphics, font, Component.translatable("scribe.index.bookmarks"), geo.pageCenterX(0), y + 4);
		this.bookmarks(graphics, font, left, y + BOOKMARKS_BELOW, bottom, mouseX, mouseY);
	}

	private void count(GuiGraphicsExtractor graphics, Font font, int centerX, int y) {
		Component text = Component.translatable("scribe.sign.stats", this.host.totalPages(), this.host.chapters().size());
		BookStyle.centered(graphics, font, text, centerX, y, BookStyle.INK_SOFT);
	}

	private void title(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y) {
		BookStyle.centered(graphics, font, text.copy().withStyle(ChatFormatting.BOLD), centerX, y, BookStyle.INK);
	}

	private int chapters(GuiGraphicsExtractor graphics, Font font, int x, int y, int bottom, int textBottom, int mouseX, int mouseY) {
		List<Chapters.Chapter> chapters = this.host.chapters();
		if (chapters.isEmpty()) {
			return graphics.textWithWordWrap(font, this.noChapters(font, y, textBottom), x, y, BookLimits.PAGE_WIDTH, BookStyle.INK_SOFT, false);
		}
		int fit = Math.max(1, (bottom - y) / CHAPTER_ROW);
		this.chapterScroll = Math.clamp(this.chapterScroll, 0, Math.max(0, chapters.size() - fit));
		int end = Math.min(chapters.size(), this.chapterScroll + fit);
		for (int i = this.chapterScroll; i < end; i++) {
			Chapters.Chapter chapter = chapters.get(i);
			int rowY = y + (i - this.chapterScroll) * CHAPTER_ROW;
			Row row = new Row(x - 2, rowY - 2, BookLimits.PAGE_WIDTH + 4, CHAPTER_ROW, () -> this.host.goTo(chapter.page()), null);
			this.rows.add(row);
			boolean hot = row.contains(mouseX, mouseY);
			if (hot) {
				graphics.fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), HOVER_FILL);
			}
			this.leaders(graphics, font, x, x + BookLimits.PAGE_WIDTH, rowY, chapter.title(), chapter.page() + 1, hot);
		}
		this.scrollMarks(graphics, x + BookLimits.PAGE_WIDTH + 3, y, (end - this.chapterScroll) * CHAPTER_ROW, this.chapterScroll > 0, end < chapters.size());
		return y + (end - this.chapterScroll) * CHAPTER_ROW;
	}

	private Component noChapters(Font font, int y, int textBottom) {
		Component howTo = Component.translatable("scribe.index.no_chapters");
		if (this.host.writing() && y + font.split(howTo, BookLimits.PAGE_WIDTH).size() * font.lineHeight <= textBottom) {
			return howTo;
		}
		return Component.translatable("scribe.index.no_chapters_reading");
	}

	private void leaders(GuiGraphicsExtractor graphics, Font font, int x0, int x1, int y, String title, int page, boolean hot) {
		int color = hot ? BookStyle.INK_HOVER : BookStyle.INK;
		String number = Integer.toString(page);
		int numberX = x1 - font.width(number);
		String shown = font.width(title) > numberX - x0 - 8 ? font.plainSubstrByWidth(title, numberX - x0 - 14) + "..." : title;
		graphics.text(font, shown, x0, y, color, false);
		graphics.text(font, number, numberX, y, color, false);
		for (int dx = x0 + font.width(shown) + 2; dx + 2 <= numberX - 2; dx += 3) {
			graphics.fill(dx, y + 6, dx + 1, y + 7, BookStyle.INK_SOFT);
		}
	}

	private void bookmarks(GuiGraphicsExtractor graphics, Font font, int x, int y, int bottom, int mouseX, int mouseY) {
		List<BookmarkStore.Bookmark> bookmarks = this.host.bookmarks();
		if (bookmarks.isEmpty()) {
			graphics.textWithWordWrap(font, Component.translatable("scribe.index.no_bookmarks"), x, y, BookLimits.PAGE_WIDTH, BookStyle.INK_SOFT, false);
			return;
		}
		int fit = Math.max(1, (bottom - y) / BOOKMARK_ROW);
		this.bookmarkScroll = Math.clamp(this.bookmarkScroll, 0, Math.max(0, bookmarks.size() - fit));
		int end = Math.min(bookmarks.size(), this.bookmarkScroll + fit);
		for (int i = this.bookmarkScroll; i < end; i++) {
			BookmarkStore.Bookmark bookmark = bookmarks.get(i);
			int rowY = y + (i - this.bookmarkScroll) * BOOKMARK_ROW;
			Row row = new Row(x - 2, rowY - 2, BookLimits.PAGE_WIDTH + 4, BOOKMARK_ROW - 1, () -> this.host.goTo(bookmark.page()),
				() -> this.host.bookmarkMenu(bookmark.page(), x + BookLimits.PAGE_WIDTH, rowY));
			this.rows.add(row);
			boolean hot = row.contains(mouseX, mouseY);
			if (hot) {
				graphics.fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), HOVER_FILL);
				this.host.bookmarkTooltip(graphics, bookmark, mouseX, mouseY);
			}
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Scribe.id("ribbon/ribbon"), x, rowY - 1, 5, 9, BookStyle.dye(bookmark.color()));
			String number = Integer.toString(bookmark.page() + 1);
			graphics.text(font, number, x + 9, rowY, hot ? BookStyle.INK_HOVER : BookStyle.INK, false);
			String label = bookmark.name().isEmpty() ? this.host.firstWords(bookmark.page()) : bookmark.name();
			int labelX = x + 26;
			graphics.text(font, font.plainSubstrByWidth(label, BookLimits.PAGE_WIDTH - 26), labelX, rowY, BookStyle.INK_SOFT, false);
		}
		this.scrollMarks(graphics, x + BookLimits.PAGE_WIDTH + 3, y, (end - this.bookmarkScroll) * BOOKMARK_ROW, this.bookmarkScroll > 0, end < bookmarks.size());
	}

	private void scrollMarks(GuiGraphicsExtractor graphics, int x, int y, int height, boolean above, boolean below) {
		if (above) {
			graphics.fill(x, y, x + 3, y + 1, BookStyle.INK_SOFT);
			graphics.fill(x + 1, y - 1, x + 2, y, BookStyle.INK_SOFT);
		}
		if (below) {
			int by = y + height - 4;
			graphics.fill(x, by, x + 3, by + 1, BookStyle.INK_SOFT);
			graphics.fill(x + 1, by + 1, x + 2, by + 2, BookStyle.INK_SOFT);
		}
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Row row : this.rows) {
			if (row.contains(mouseX, mouseY)) {
				if (Keys.isLeftClick(button)) {
					row.action().run();
					return true;
				}
				if (Keys.isRightClick(button) && row.menu() != null) {
					row.menu().run();
					return true;
				}
				return false;
			}
		}
		return false;
	}

	public void mouseScrolled(BookGeometry geo, double mouseX, double mouseY, double amount) {
		int step = this.wheel.take(amount);
		if (step == 0) {
			return;
		}
		boolean rightPage = geo.spread && mouseX >= geo.centerX();
		boolean overBookmarks = this.bookmarkTop == Integer.MAX_VALUE ? rightPage : !rightPage && mouseY >= this.bookmarkTop;
		if (overBookmarks) {
			this.bookmarkScroll = Math.max(0, this.bookmarkScroll + step);
		} else {
			this.chapterScroll = Math.max(0, this.chapterScroll + step);
		}
	}
}
