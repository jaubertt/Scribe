package scribe.client.screen;

import scribe.BookLimits;
import scribe.Scribe;
import scribe.client.BookMotion;
import scribe.client.BookNet;
import scribe.client.compat.Keys;
import scribe.client.memory.BookmarkStore;
import scribe.client.screen.popup.BookmarkPopup;
import scribe.client.screen.popup.HelpPopup;
import scribe.client.text.BookCounts;
import scribe.client.text.Chapters;
import scribe.client.text.FormatCodes;
import scribe.client.text.PageCodec;
import scribe.client.widget.InlineField;
import scribe.client.widget.SpriteButton;
import scribe.config.ScribeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

public abstract class PagedBookScreen extends BookScreen implements IndexView.Host {
	private static final int ARROW_PAD = 2;
	private static final int RIBBON_WIDTH = 7;
	private static final int RIBBON_HEIGHT = 13;
	protected final IndexView index = new IndexView(this);
	protected boolean indexOpen;
	private @Nullable SpriteButton indexButton;
	private @Nullable SpriteButton firstButton;
	private @Nullable SpriteButton previousButton;
	private @Nullable SpriteButton nextButton;
	private @Nullable SpriteButton lastButton;
	private double scrollProgress;
	private int jumpSlot = -1;
	private final InlineField jumpField = new InlineField(3).filter(value -> value.chars().allMatch(Character::isDigit));
	private @Nullable List<Chapters.Chapter> chapterCache;
	private int turnSpread = Integer.MIN_VALUE;
	private boolean turnTwoPages;

	protected PagedBookScreen(Component title) {
		super(title);
	}

	@Override
	public void tick() {
		super.tick();
		this.noticeTurn();
	}

	protected boolean holdsBookOpen() {
		return false;
	}

	private void noticeTurn() {
		boolean two = this.geo != null && this.geo.spread;
		int spread = this.showsPages() ? this.basePage() : -1;
		int was = this.turnSpread;
		boolean sameView = two == this.turnTwoPages;
		this.turnSpread = spread;
		this.turnTwoPages = two;
		if (was != Integer.MIN_VALUE && sameView && spread != was) {
			int leaves = turnLeaves(was, spread, two ? 2 : 1);
			if (this.holdsBookOpen()) {
				BookNet.pageTurned(leaves);
			} else if (was >= 0 && spread >= 0) {
				this.turnedAtLectern(leaves);
			}
		}
	}

	protected void turnedAtLectern(int leaves) {
	}

	static int turnLeaves(int from, int to, int step) {
		int a = from < 0 ? -step : from;
		int b = to < 0 ? -step : to;
		if (a == b) {
			return 0;
		}
		int leaves = Math.min(BookMotion.MAX_LEAVES, (Math.abs(b - a) + step - 1) / step);
		return b > a ? leaves : -leaves;
	}

	protected abstract int pageCount();

	@Override
	public int totalPages() {
		return this.pageCount();
	}

	protected abstract int basePage();

	protected abstract void showPage(int index);

	protected abstract boolean turnPages(int delta);

	protected abstract List<String> pageTexts();

	protected abstract void initPages();

	protected abstract List<List<HelpPopup.Line>> helpGroups();

	protected boolean hasIndex() {
		return true;
	}

	protected boolean showsPages() {
		return true;
	}

	protected boolean canTurnBack() {
		return this.basePage() > 0;
	}

	protected void turnToStart() {
		this.turnTo(0);
	}

	protected Component startTooltip() {
		return Component.translatable("scribe.button.first_page.tooltip");
	}

	protected void chaptersChanged() {
		this.chapterCache = null;
	}

	@Override
	public List<Chapters.Chapter> chapters() {
		if (this.chapterCache == null) {
			this.chapterCache = Chapters.find(this.pageTexts());
		}
		return this.chapterCache;
	}

	protected BookmarkStore.@Nullable Marks marks() {
		return null;
	}

	@Override
	public List<BookmarkStore.Bookmark> bookmarks() {
		BookmarkStore.Marks marks = this.marks();
		return marks == null ? List.of() : marks.list();
	}

	@Override
	public boolean showsBookmarks() {
		return this.marks() != null;
	}

	@Override
	public String firstWords(int page) {
		List<String> texts = this.pageTexts();
		if (page < 0 || page >= texts.size()) {
			return "";
		}
		String plain = FormatCodes.strip(PageCodec.decodeText(texts.get(page))).replace('\n', ' ').strip();
		return plain.length() > 40 ? plain.substring(0, 40) : plain;
	}

	@Override
	public void goTo(int page) {
		this.setIndexOpen(false);
		this.showPage(page);
		this.playPageTurn();
	}

	@Override
	protected void initBook() {
		this.addCornerButtons(true);
		this.indexButton = !this.hasIndex() ? null : this.addRenderableWidget(new SpriteButton(this.geo.indexX(), this.geo.indexY(),
			BookGeometry.CELL_WIDTH, BookGeometry.CELL_HEIGHT, "tool/index", Component.translatable("scribe.button.index"), this::toggleIndex));
		int y = this.geo.pageNumberY() + 1 - ARROW_PAD;
		int left = this.geo.textLeftEdge() - ARROW_PAD;
		int right = this.geo.textRightEdge();
		this.firstButton = this.arrow(left, y, 10, "first", Component.translatable("scribe.button.first_page.tooltip"), this::turnToStart);
		this.previousButton = this.arrow(left + 12, y, 7, "previous", Component.translatable("scribe.button.previous_page.tooltip"), () -> {
			if (Keys.shiftHeldNow()) {
				this.turnToStart();
			} else {
				this.turnPages(-1);
			}
		});
		this.nextButton = this.arrow(right - 15 - ARROW_PAD, y, 7, "next", Component.translatable("scribe.button.next_page.tooltip"), () -> {
			if (Keys.shiftHeldNow()) {
				this.turnTo(this.pageCount() - 1);
			} else {
				this.turnPages(1);
			}
		});
		this.lastButton = this.arrow(right - 6 - ARROW_PAD, y, 10, "last", Component.translatable("scribe.button.last_page.tooltip", 1),
			() -> this.turnTo(this.pageCount() - 1));
		this.endJump();
		this.initPages();
		this.updateNavigation();
	}

	private SpriteButton arrow(int x, int y, int width, String sprite, Component tooltip, Runnable action) {
		return this.addRenderableWidget(new SpriteButton(x, y, width, 8, "page/" + sprite, tooltip, action).pageSound());
	}

	protected void updateNavigation() {
		if (this.firstButton == null || this.previousButton == null || this.nextButton == null || this.lastButton == null) {
			return;
		}
		boolean pages = this.showsPages();
		boolean back = pages && this.canTurnBack() && !this.indexOpen;
		boolean forward = pages && this.canTurnForward() && !this.indexOpen;
		this.firstButton.visible = back;
		this.previousButton.visible = back;
		this.firstButton.setTooltipText(this.startTooltip());
		this.previousButton.setTooltipText(this.basePage() == 0 ? this.startTooltip() : Component.translatable("scribe.button.previous_page.tooltip"));
		this.nextButton.visible = forward;
		this.lastButton.visible = forward;
		this.lastButton.setTooltipText(Component.translatable("scribe.button.last_page.tooltip", Math.max(1, this.pageCount())));
		if (this.indexButton != null) {
			this.indexButton.visible = pages;
			this.indexButton.setSelected(this.indexOpen);
		}
	}

	protected boolean canTurnForward() {
		return this.basePage() < this.lastBase();
	}

	protected int lastBase() {
		int last = Math.max(0, this.pageCount() - 1);
		return this.geo != null && this.geo.spread ? last & ~1 : last;
	}

	protected void turnTo(int page) {
		int target = Math.clamp(page, 0, Math.max(0, this.pageCount() - 1));
		if (target != this.basePage()) {
			this.showPage(target);
		}
	}

	protected void toggleIndex() {
		this.setIndexOpen(!this.indexOpen);
	}

	protected void setIndexOpen(boolean open) {
		this.indexOpen = open;
		this.endJump();
		this.updateNavigation();
	}

	protected void playPageTurn() {
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
	}

	@Override
	protected void extractPages(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (!this.showsPages()) {
			return;
		}
		if (this.indexOpen) {
			this.index.render(graphics, this.font, this.geo, mouseX, mouseY);
			return;
		}
		List<String> texts = this.pageTexts();
		boolean headers = ScribeConfig.get().runningHeader;
		for (int slot = 0; slot < this.geo.slots(); slot++) {
			int page = this.basePage() + slot;
			if (page >= this.pageCount()) {
				continue;
			}
			int cx = this.geo.pageCenterX(slot);
			if (slot == this.jumpSlot) {
				String value = this.jumpField.value();
				int w = Math.max(14, this.font.width(value) + 6);
				BookStyle.field(graphics, cx - w / 2 - 1, this.geo.pageNumberY() - 2, w + 2, 12);
				this.jumpField.render(graphics, this.font, cx - this.font.width(value) / 2, this.geo.pageNumberY(), 30, BookStyle.INK, null, BookStyle.INK_SOFT);
			} else {
				String number = Integer.toString(page + 1);
				boolean hot = this.overPageNumber(slot, mouseX, mouseY);
				BookStyle.centered(graphics, this.font, number, cx, this.geo.pageNumberY(), hot ? BookStyle.INK_HOVER : BookStyle.PAGE_NUMBER);
				if (hot) {
					List<FormattedCharSequence> tip = new ArrayList<>();
					for (Component line : BookCounts.pageNumberLines(page, this.pageCount(), BookCounts.words(texts), this.chapters().size())) {
						tip.addAll(this.font.split(line, 170));
					}
					graphics.setTooltipForNextFrame(this.font, tip, mouseX, mouseY);
				}
			}
			if (headers && page < texts.size()) {
				String header = Chapters.headerFor(this.chapters(), texts, page);
				if (header != null) {
					String shown = this.font.width(header) > BookLimits.PAGE_WIDTH ? this.font.plainSubstrByWidth(header, BookLimits.PAGE_WIDTH - 8) + "..." : header;
					BookStyle.centered(graphics, this.font, shown, cx, this.geo.headerY(), BookStyle.INK_SOFT);
				}
			}
		}
		this.drawRibbons(graphics, mouseX, mouseY);
	}

	protected boolean ribbonsShown() {
		return this.marks() != null && this.showsPages() && !this.indexOpen;
	}

	protected int edgeTabTop(boolean rightSide) {
		return this.geo.edgeTabTop(rightSide);
	}

	private record EdgeTab(BookmarkStore.Bookmark bookmark, int x, int y, int hit, boolean right) {
		boolean contains(double mx, double my) {
			return mx >= this.x && mx < this.x + 8 && my >= this.y && my < this.y + this.hit;
		}
	}

	private List<EdgeTab> edgeTabs() {
		List<EdgeTab> out = new ArrayList<>();
		if (!this.ribbonsShown()) {
			return out;
		}
		int base = this.basePage();
		int lastShown = base + this.geo.slots() - 1;
		List<BookmarkStore.Bookmark> before = new ArrayList<>();
		List<BookmarkStore.Bookmark> after = new ArrayList<>();
		for (BookmarkStore.Bookmark bookmark : this.bookmarks()) {
			if (bookmark.page() >= this.pageCount()) {
				continue;
			}
			if (bookmark.page() < base) {
				before.add(bookmark);
			} else if (bookmark.page() > lastShown) {
				after.add(bookmark);
			}
		}
		this.addEdgeTabs(out, before, false);
		this.addEdgeTabs(out, after, true);
		return out;
	}

	private void addEdgeTabs(List<EdgeTab> out, List<BookmarkStore.Bookmark> side, boolean right) {
		int top = this.edgeTabTop(right);
		int span = this.geo.edgeTabBottom() - top;
		int capacity = BookGeometry.edgeTabCapacity(span);
		List<BookmarkStore.Bookmark> shown = side.size() <= capacity ? side
			: right ? side.subList(0, capacity) : side.subList(side.size() - capacity, side.size());
		int step = BookGeometry.edgeTabStep(span, shown.size());
		for (int i = 0; i < shown.size(); i++) {
			int hit = i == shown.size() - 1 ? BookGeometry.EDGE_TAB_HEIGHT : Math.min(BookGeometry.EDGE_TAB_HEIGHT, step);
			out.add(new EdgeTab(shown.get(i), this.geo.edgeTabX(right), top + i * step, hit, right));
		}
	}

	private boolean overRibbon(int slot, double mouseX, double mouseY) {
		BookmarkStore.Marks marks = this.marks();
		int page = this.basePage() + slot;
		if (marks == null || !this.ribbonsShown() || page >= this.pageCount()) {
			return false;
		}
		int x = this.geo.ribbonX(slot);
		int y = this.geo.ribbonY();
		boolean marked = marks.at(page).isPresent();
		return mouseX >= x && mouseX < x + RIBBON_WIDTH && mouseY >= y && mouseY < y + (marked ? RIBBON_HEIGHT : 8);
	}

	private void drawRibbons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		BookmarkStore.Marks marks = this.marks();
		if (marks == null || !this.ribbonsShown()) {
			return;
		}
		boolean tips = this.popup() == null;
		for (int slot = 0; slot < this.geo.slots(); slot++) {
			int page = this.basePage() + slot;
			if (page >= this.pageCount()) {
				continue;
			}
			boolean hot = this.overRibbon(slot, mouseX, mouseY);
			int x = this.geo.ribbonX(slot);
			int y = this.geo.ribbonY();
			Optional<BookmarkStore.Bookmark> mark = marks.at(page);
			if (mark.isPresent()) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Scribe.id(hot ? "ribbon/ribbon_highlighted" : "ribbon/ribbon"), x, y, RIBBON_WIDTH, RIBBON_HEIGHT,
					BookStyle.dye(mark.get().color()));
				if (hot && tips && !mark.get().name().isEmpty()) {
					this.tooltip(graphics, this.bookmarkTooltip(mark.get()), mouseX, mouseY);
				}
			} else {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Scribe.id(hot ? "ribbon/stub_highlighted" : "ribbon/stub"), x, y, RIBBON_WIDTH, 4);
				if (hot && tips) {
					this.tooltip(graphics, Component.translatable("scribe.bookmark.add"), mouseX, mouseY);
				}
			}
		}
		for (EdgeTab tab : this.edgeTabs()) {
			this.drawEdgeTab(graphics, tab);
			if (tips && tab.contains(mouseX, mouseY)) {
				this.tooltip(graphics, this.bookmarkTooltip(tab.bookmark()), mouseX, mouseY);
			}
		}
	}

	protected void tooltip(GuiGraphicsExtractor graphics, Component text, int mouseX, int mouseY) {
		graphics.setTooltipForNextFrame(this.font, this.font.split(text, 200), mouseX, mouseY);
	}

	@Override
	public void bookmarkMenu(int page, int x, int y) {
		this.openBookmarkPopup(page, x, y);
	}

	@Override
	public void bookmarkTooltip(GuiGraphicsExtractor graphics, BookmarkStore.Bookmark bookmark, int mouseX, int mouseY) {
		if (this.popup() == null) {
			this.tooltip(graphics, this.bookmarkTooltip(bookmark), mouseX, mouseY);
		}
	}

	private Component bookmarkTooltip(BookmarkStore.Bookmark bookmark) {
		Component page = Component.translatable("scribe.bookmark.page", bookmark.page() + 1);
		if (bookmark.name().isEmpty()) {
			return page;
		}
		return Component.literal(bookmark.name()).append(Component.literal("\n").append(page).withStyle(ChatFormatting.GRAY));
	}

	private void drawEdgeTab(GuiGraphicsExtractor graphics, EdgeTab tab) {
		int color = BookStyle.dye(tab.bookmark().color());
		int dark = BookStyle.darker(color);
		String[] rows = tab.right()
			? new String[] {"DDDDDDDD", "DCCCCCCC", "DCCCCCC.", "DCCCCC..", "DCCCCCC.", "DCCCCCCC", "DDDDDDDD"}
			: new String[] {"DDDDDDDD", "CCCCCCCD", ".CCCCCCD", "..CCCCCD", ".CCCCCCD", "CCCCCCCD", "DDDDDDDD"};
		for (int j = 0; j < rows.length; j++) {
			for (int i = 0; i < rows[j].length(); i++) {
				char c = rows[j].charAt(i);
				if (c != '.') {
					graphics.fill(tab.x() + i, tab.y() + j, tab.x() + i + 1, tab.y() + j + 1, c == 'D' ? dark : color);
				}
			}
		}
	}

	private void openBookmarkPopup(int page, int anchorX, int anchorY) {
		BookmarkStore.Marks marks = this.marks();
		if (marks == null) {
			return;
		}
		BookmarkPopup popup = new BookmarkPopup(this.font, marks, page);
		int x = anchorX + 10 + popup.width() < this.width ? anchorX + 10 : anchorX - popup.width() - 4;
		this.openPopup(popup, x, anchorY - 6);
	}

	private void addBookmark(int page, int slot) {
		BookmarkStore.Marks marks = this.marks();
		if (marks == null || page < 0 || page >= this.pageCount()) {
			return;
		}
		if (marks.at(page).isEmpty()) {
			marks.put(new BookmarkStore.Bookmark(page, marks.nextColor(), ""));
		}
		this.openBookmarkPopup(page, this.geo.ribbonX(slot), this.geo.ribbonY() + 10);
	}

	protected boolean clickBookmarks(MouseButtonEvent event) {
		BookmarkStore.Marks marks = this.marks();
		if (marks == null || !this.ribbonsShown()) {
			return false;
		}
		for (int slot = 0; slot < this.geo.slots(); slot++) {
			if (this.overRibbon(slot, event.x(), event.y())) {
				int page = this.basePage() + slot;
				if (marks.at(page).isPresent()) {
					this.openBookmarkPopup(page, this.geo.ribbonX(slot), this.geo.ribbonY() + 10);
				} else {
					this.addBookmark(page, slot);
				}
				return true;
			}
		}
		for (EdgeTab tab : this.edgeTabs()) {
			if (tab.contains(event.x(), event.y())) {
				if (Keys.isRightClick(event.button())) {
					this.openBookmarkPopup(tab.bookmark().page(), tab.x(), tab.y());
				} else {
					this.showPage(tab.bookmark().page());
					this.playPageTurn();
				}
				return true;
			}
		}
		return false;
	}

	private boolean overPageNumber(int slot, double mouseX, double mouseY) {
		int page = this.basePage() + slot;
		if (page >= this.pageCount() || this.indexOpen || !this.showsPages()) {
			return false;
		}
		int w = Math.max(10, this.font.width(Integer.toString(page + 1)));
		int cx = this.geo.pageCenterX(slot);
		return mouseX >= cx - w / 2 - 3 && mouseX < cx + w / 2 + 3 && mouseY >= this.geo.pageNumberY() - 2 && mouseY < this.geo.pageNumberY() + 10;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.popup() == null) {
			if (this.indexOpen && this.index.mouseClicked(event.x(), event.y(), event.button())) {
				return true;
			}
			for (int slot = 0; slot < this.geo.slots(); slot++) {
				if (this.overPageNumber(slot, event.x(), event.y()) && Keys.isLeftClick(event.button())) {
					this.startJump(slot);
					return true;
				}
			}
			if (this.jumpSlot >= 0) {
				this.endJump();
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void startJump(int slot) {
		this.jumpSlot = slot;
		this.jumpField.setValue("");
		this.jumpField.setFocused(true);
		this.setFocused(null);
	}

	protected boolean jumping() {
		return this.jumpSlot >= 0;
	}

	private void endJump() {
		this.jumpSlot = -1;
		this.jumpField.setFocused(false);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.popup() != null) {
			return super.mouseScrolled(x, y, scrollX, scrollY);
		}
		if (this.indexOpen) {
			this.index.mouseScrolled(this.geo, x, y, scrollY);
			return true;
		}
		ScribeConfig config = ScribeConfig.get();
		if (!config.scrollTurnsPages) {
			return super.mouseScrolled(x, y, scrollX, scrollY);
		}
		double amount = -(scrollY + scrollX);
		if (config.invertScroll) {
			amount = -amount;
		}
		double speed = Keys.fastModifierHeld() ? config.fastScrollPages : Keys.slowModifierHeld() ? 0.25 : 1.0;
		this.scrollProgress += amount * speed;
		int pages = (int) this.scrollProgress;
		this.scrollProgress -= pages;
		if (pages != 0 && this.turnPages(pages)) {
			this.playPageTurn();
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.popup() != null) {
			return super.keyPressed(event);
		}
		if (this.jumpSlot >= 0) {
			if (event.isEscape()) {
				this.endJump();
			} else if (event.isConfirmation()) {
				String value = this.jumpField.value();
				this.endJump();
				if (!value.isEmpty()) {
					int page = Integer.parseInt(value) - 1;
					this.turnTo(page);
					this.playPageTurn();
				}
			} else {
				this.jumpField.keyPressed(event);
			}
			return true;
		}
		if (this.indexOpen && event.isEscape()) {
			this.setIndexOpen(false);
			return true;
		}
		if (Keys.alt(event) && !Keys.ctrl(event) && (Keys.isUp(event) || Keys.isDown(event))) {
			int current = this.basePage() + (this.geo.spread ? 1 : 0);
			int target = Keys.isUp(event) ? Chapters.previousChapterPage(this.chapters(), this.basePage()) : Chapters.nextChapterPage(this.chapters(), current);
			if (target >= 0) {
				this.showPage(target);
				this.playPageTurn();
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (this.popup() == null && this.jumpSlot >= 0) {
			this.jumpField.charTyped(event);
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	protected void showHelp() {
		HelpPopup help = new HelpPopup(this.font, Component.translatable("scribe.help.title"), this.helpGroups(), this.width, this.height);
		this.openPopup(help, this.width / 2 - help.width() / 2, this.height / 2 - help.height() / 2);
	}
}
