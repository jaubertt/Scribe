package scribe.client.screen;

import scribe.RealAuthor;
import scribe.client.AuthorNames;
import scribe.BookLimits;
import scribe.ContentsLinks;
import scribe.EnchantedBooks;
import scribe.binding.Binding;
import scribe.binding.Bookbinding;
import scribe.client.BookNet;
import scribe.client.BookScreens;
import scribe.client.compat.Keys;
import scribe.client.io.BookFile;
import scribe.client.memory.BookmarkStore;
import scribe.client.memory.PageMemory;
import scribe.client.memory.PenNames;
import scribe.client.screen.popup.HelpPopup;
import scribe.client.text.Chapters;
import scribe.client.text.TextWrap;
import scribe.client.text.EnchantedPages;
import scribe.client.text.FormatCodes;
import scribe.client.text.SharedPages;
import scribe.client.widget.SpriteButton;
import scribe.config.ScribeConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;

public class BookReaderScreen extends PagedBookScreen {
	private static final Style PAGE_TEXT_STYLE = Style.EMPTY.withoutShadow().withColor(BookStyle.INK & 0xFFFFFF);
	private static final long FLASH_MS = 1200;
	private static final long FLASH_FADE_MS = 600;
	private static final int FLASH_ALPHA = 0x60;

	protected ItemStack stack = ItemStack.EMPTY;
	protected @Nullable InteractionHand hand;
	protected List<Component> pages = List.of();
	protected SharedPages pageMap = SharedPages.identity(0);
	protected boolean enchanted;
	private List<String> legacy = List.of();
	protected int page;
	private final Map<Integer, List<FormattedCharSequence>> lineCache = new HashMap<>();
	private final Map<Integer, List<Chapters.TitleRun>> titleCache = new HashMap<>();
	private @Nullable String bookKey;
	private boolean closed;
	private boolean openingChild;
	private boolean readingSent;
	private @Nullable CoverView coverView;
	private @Nullable EnchantedCover enchantedCover;
	private boolean onCover;
	private @Nullable SpriteButton saveTool;
	private @Nullable SpriteButton copyTool;
	private int flashPage = -1;
	private long flashUntil;

	public BookReaderScreen(ItemStack stack, @Nullable InteractionHand hand) {
		super(Component.translatable("book.view.title"));
		this.hand = hand;
		this.setBook(stack);
		if (hand != null && this.bookKey != null) {
			int remembered = PageMemory.get().get(this.bookKey);
			if (remembered >= 0) {
				this.page = Math.min(remembered, Math.max(0, this.pages.size() - 1));
			} else {
				this.onCover = this.coverView != null || this.enchantedCover != null;
			}
		}
	}

	protected void setBook(ItemStack stack) {
		this.stack = stack;
		BookViewScreen.BookAccess access = BookViewScreen.BookAccess.fromItem(stack);
		this.enchanted = access == null && EnchantedBooks.is(stack);
		if (access != null) {
			this.pages = access.pages();
			this.pageMap = SharedPages.identity(this.pages.size());
		} else if (this.enchanted) {
			EnchantedPages.Book book = EnchantedPages.of(stack, this.font);
			this.pages = book.pages();
			this.pageMap = book.shared();
		} else {
			this.pages = List.of();
			this.pageMap = SharedPages.identity(0);
		}
		List<String> texts = new ArrayList<>();
		for (Component page : this.pages) {
			texts.add(FormatCodes.toLegacy(page));
		}
		this.legacy = texts;
		this.lineCache.clear();
		this.titleCache.clear();
		this.chaptersChanged();
		this.flashPage = -1;
		this.page = Math.clamp(this.page, 0, Math.max(0, this.pages.size() - 1));
		this.cover = this.enchanted ? EnchantedCover.color(stack) : BookStyle.cover(stack);
		this.material = Bookbinding.materialName(Binding.of(stack));
		this.corner = BookStyle.corner(stack);
		this.bookKey = this.enchanted ? EnchantedCover.memoryKey(stack) : PageMemory.keyFor(stack);
		this.adoptWritingBookmarks(stack);
		this.coverView = CoverView.of(stack, this.font, this.minecraft.isTextFilteringEnabled());
		this.enchantedCover = this.enchanted ? EnchantedCover.of(stack, this.font) : null;
		if (this.coverView == null && this.enchantedCover == null) {
			this.onCover = false;
		}
	}

	protected boolean hasCover() {
		return this.coverView != null || this.enchantedCover != null;
	}

	protected boolean onCover() {
		return this.onCover;
	}

	protected void setOnCover(boolean onCover) {
		this.onCover = onCover && this.hasCover();
		this.updateNavigation();
	}

	@Override
	protected boolean showsPages() {
		return !this.onCover;
	}

	protected void showCover() {
		if (!this.hasCover() || this.onCover) {
			return;
		}
		this.setIndexOpen(false);
		this.onCover = true;
		this.page = 0;
		if (this.hand != null && this.bookKey != null) {
			PageMemory.get().forget(this.bookKey);
		}
		this.updateNavigation();
	}

	protected void leaveCover() {
		if (this.onCover) {
			this.onCover = false;
			this.updateNavigation();
		}
	}

	private int coverX() {
		return this.geo.centerX() - CoverView.WIDTH / 2;
	}

	private boolean overCover(double mouseX, double mouseY) {
		int x = this.coverX();
		int y = this.geo.top;
		return mouseX >= x && mouseX < x + CoverView.WIDTH && mouseY >= y && mouseY < y + CoverView.HEIGHT;
	}

	protected int openingPage() {
		return 0;
	}

	private void openFromCover() {
		this.showPage(this.openingPage());
		this.playPageTurn();
	}

	@Override
	protected void drawBook(GuiGraphicsExtractor graphics) {
		if (this.onCover && this.coverView != null) {
			this.coverView.drawArt(graphics, this.coverX(), this.geo.top);
			return;
		}
		if (this.onCover && this.enchantedCover != null) {
			this.enchantedCover.drawArt(graphics, this.coverX(), this.geo.top);
			return;
		}
		super.drawBook(graphics);
	}

	@Override
	protected boolean hasIndex() {
		return !this.enchanted;
	}

	@Override
	protected void initPages() {
		this.saveTool = null;
		this.copyTool = null;
		if (!this.enchanted) {
			this.addBookTools();
		}
		this.createBottomButtons();
		if (this.hand != null && !this.readingSent) {
			this.readingSent = true;
			BookNet.sendReading(true, this.hand);
		}
	}

	@Override
	protected boolean holdsBookOpen() {
		return this.readingSent && this.hand != null && !this.closed;
	}

	private void addBookTools() {
		int x = this.geo.toolsX();
		int y = this.geo.toolsY();
		boolean own = PenNames.isOwnBook(this.stack);
		SpriteButton save = this.addRenderableWidget(new SpriteButton(x, y, BookGeometry.CELL_WIDTH, BookGeometry.CELL_HEIGHT, "tool/save",
			Component.translatable("scribe.button.save"), this::openExport));
		SpriteButton copy = this.addRenderableWidget(new SpriteButton(x, y + BookGeometry.CELL_STEP + 4, BookGeometry.CELL_WIDTH, BookGeometry.CELL_HEIGHT, "tool/copy_book",
			Component.translatable("scribe.button.copy_book"), this::copyBook));
		this.saveTool = save;
		this.copyTool = copy;
		if (!own) {
			Component why = this.notOwnReason();
			save.active = false;
			copy.active = false;
			save.setTooltipText(Component.translatable("scribe.button.save").append("\n").append(why));
			copy.setTooltipText(Component.translatable("scribe.button.copy_book").append("\n").append(why));
		}
	}

	private Component notOwnReason() {
		WrittenBookContent written = this.stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		Object author = written == null ? "?" : AuthorNames.name(written.author(), RealAuthor.isPenName(this.stack));
		return Component.translatable("scribe.files.not_own", author).withStyle(ChatFormatting.GRAY);
	}

	protected void createBottomButtons() {
		this.bottomButtons(List.of(CommonComponents.GUI_DONE), List.of(this::onClose));
	}

	protected List<Button> bottomButtons(List<Component> labels, List<Runnable> actions) {
		int n = labels.size();
		int w = n <= 2 ? 98 : 78;
		int total = n * w + (n - 1) * 4;
		int x0 = this.width / 2 - total / 2;
		List<Button> buttons = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			Runnable action = actions.get(i);
			buttons.add(this.addRenderableWidget(Button.builder(labels.get(i), b -> action.run()).bounds(x0 + i * (w + 4), this.geo.buttonsY(), w, 20).build()));
		}
		return buttons;
	}

	@Override
	protected void updateNavigation() {
		super.updateNavigation();
		for (SpriteButton tool : new SpriteButton[] {this.saveTool, this.copyTool}) {
			if (tool != null) {
				tool.visible = !this.onCover;
			}
		}
	}

	@Override
	protected boolean canTurnBack() {
		return super.canTurnBack() || this.hasCover();
	}

	@Override
	protected void turnToStart() {
		if (this.hasCover()) {
			this.showCover();
		} else {
			super.turnToStart();
		}
	}

	@Override
	protected Component startTooltip() {
		return this.hasCover() ? Component.translatable("scribe.button.cover.tooltip") : super.startTooltip();
	}

	@Override
	protected void turnTo(int page) {
		if (this.onCover) {
			this.showPage(page);
			return;
		}
		super.turnTo(page);
	}

	protected int baseIndex() {
		return this.geo != null && this.geo.spread ? this.page & ~1 : this.page;
	}

	@Override
	protected int basePage() {
		return this.baseIndex();
	}

	@Override
	protected int pageCount() {
		return this.pages.size();
	}

	@Override
	protected List<String> pageTexts() {
		return this.legacy;
	}

	@Override
	protected boolean turnPages(int delta) {
		int step = this.geo.spread ? 2 : 1;
		if (this.onCover) {
			if (delta <= 0) {
				return false;
			}
			this.showPage(delta == 1 ? this.openingPage() : Math.clamp(this.openingPage() + (long) (delta - 1) * step, 0, this.lastBase()));
			return true;
		}
		long wanted = this.baseIndex() + (long) delta * step;
		if (wanted < 0 && this.hasCover()) {
			this.showCover();
			return true;
		}
		int target = Math.clamp(wanted, 0, this.lastBase());
		if (target == this.baseIndex()) {
			return false;
		}
		this.showPage(target);
		return true;
	}

	@Override
	protected void showPage(int index) {
		this.leaveCover();
		this.page = Math.clamp(index, 0, Math.max(0, this.pages.size() - 1));
		if (this.hand != null && this.bookKey != null) {
			PageMemory.get().put(this.bookKey, this.page);
		}
		this.updateNavigation();
	}

	@Override
	protected BookmarkStore.@Nullable Marks marks() {
		return this.bookKey == null || this.enchanted ? null : BookmarkStore.get().marks(this.bookKey);
	}

	private void adoptWritingBookmarks(ItemStack stack) {
		String text = PageMemory.textKey(stack);
		if (this.bookKey != null && text != null) {
			BookmarkStore.get().adopt(text, this.bookKey);
		}
	}

	private List<FormattedCharSequence> lines(int index) {
		return this.lineCache.computeIfAbsent(index, i -> TextWrap.pageLines(this.font, this.pages.get(i), PAGE_TEXT_STYLE, BookLimits.PAGE_WIDTH));
	}

	private void visitText(ActiveTextCollector collector) {
		if (this.indexOpen || this.onCover) {
			return;
		}
		for (int slot = 0; slot < this.geo.slots(); slot++) {
			int index = this.baseIndex() + slot;
			if (index >= this.pages.size()) {
				continue;
			}
			List<FormattedCharSequence> lines = this.lines(index);
			int shown = Math.min(BookLimits.PAGE_LINES, lines.size());
			for (int i = 0; i < shown; i++) {
				collector.accept(this.geo.textX(slot), this.geo.textY() + i * BookLimits.LINE_HEIGHT, lines.get(i));
			}
		}
	}

	private List<Chapters.TitleRun> titleRuns(int index) {
		return this.titleCache.computeIfAbsent(index,
			i -> i < this.legacy.size() ? Chapters.titleRuns(this.font, this.legacy.get(i), BookLimits.PAGE_WIDTH) : List.of());
	}

	private void drawChapterMarks(GuiGraphicsExtractor graphics) {
		if (this.indexOpen || this.onCover) {
			return;
		}
		for (int slot = 0; slot < this.geo.slots(); slot++) {
			int index = this.baseIndex() + slot;
			if (index >= this.pages.size()) {
				continue;
			}
			int x = this.geo.textX(slot);
			for (Chapters.TitleRun run : this.titleRuns(index)) {
				if (run.first() < BookLimits.PAGE_LINES) {
					int top = this.geo.textY() + run.first() * BookLimits.LINE_HEIGHT;
					int lastTop = this.geo.textY() + Math.min(run.last(), BookLimits.PAGE_LINES - 1) * BookLimits.LINE_HEIGHT;
					graphics.fill(x - 7, top, x - 5, lastTop + 8, BookStyle.INK_RED);
				}
			}
		}
	}

	@Override
	protected void extractPages(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (this.onCover && (this.coverView != null || this.enchantedCover != null)) {
			if (this.coverView != null) {
				this.coverView.drawText(graphics, this.font, this.coverX(), this.geo.top);
			} else {
				this.enchantedCover.drawText(graphics, this.font, this.coverX(), this.geo.top);
			}
			if (this.popup() == null && this.overCover(mouseX, mouseY)) {
				graphics.setTooltipForNextFrame(this.font, Component.translatable("scribe.cover.open"), mouseX, mouseY);
			}
			return;
		}
		this.drawChapterMarks(graphics);
		this.drawFlash(graphics);
		this.visitText(graphics.textRenderer(GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR));
		super.extractPages(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.popup() == null && this.onCover) {
			if (Keys.isLeftClick(event.button()) && this.overCover(event.x(), event.y())) {
				this.openFromCover();
				return true;
			}
			return super.mouseClicked(event, doubleClick);
		}
		if (this.popup() == null && !this.indexOpen) {
			if (this.clickBookmarks(event)) {
				return true;
			}
			if (Keys.isLeftClick(event.button())) {
				ActiveTextCollector.ClickableStyleFinder finder = new ActiveTextCollector.ClickableStyleFinder(this.font, (int) event.x(), (int) event.y());
				this.visitText(finder);
				Style clicked = finder.result();
				if (clicked != null && this.handleClickEvent(clicked.getClickEvent())) {
					return true;
				}
				if (this.clickContentsLine(event.x(), event.y())) {
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean clickContentsLine(double mouseX, double mouseY) {
		for (int slot = 0; slot < this.geo.slots(); slot++) {
			int index = this.baseIndex() + slot;
			if (index >= this.legacy.size() || !Chapters.isContentsPage(this.legacy.get(index))) {
				continue;
			}
			int x = this.geo.textX(slot);
			if (mouseX < x || mouseX >= x + BookLimits.PAGE_WIDTH || mouseY < this.geo.textY()) {
				continue;
			}
			int line = (int) ((mouseY - this.geo.textY()) / BookLimits.LINE_HEIGHT);
			List<FormattedCharSequence> lines = this.lines(index);
			if (line < 0 || line >= Math.min(BookLimits.PAGE_LINES, lines.size())) {
				continue;
			}
			StringBuilder text = new StringBuilder();
			lines.get(line).accept((i, style, cp) -> {
				text.appendCodePoint(cp);
				return true;
			});
			int target = ContentsLinks.targetPage(text.toString(), this.pages.size());
			if (target > 0) {
				this.jumpTo(target - 1);
				return true;
			}
		}
		return false;
	}

	protected void jumpTo(int index) {
		int target = Math.clamp(index, 0, Math.max(0, this.pages.size() - 1));
		boolean turns = this.onCover || this.spreadStart(target) != this.baseIndex();
		if (turns || target != this.page) {
			this.showPage(target);
		}
		if (turns) {
			this.playPageTurn();
		}
		this.flashPage = target;
		this.flashUntil = Util.getMillis() + FLASH_MS;
	}

	private int spreadStart(int index) {
		return this.geo != null && this.geo.spread ? index & ~1 : index;
	}

	private void drawFlash(GuiGraphicsExtractor graphics) {
		if (this.flashPage < 0 || this.indexOpen || this.onCover) {
			return;
		}
		long left = this.flashUntil - Util.getMillis();
		int slot = this.flashPage - this.baseIndex();
		if (left <= 0 || this.flashPage >= this.pages.size()) {
			this.flashPage = -1;
			return;
		}
		if (slot < 0 || slot >= this.geo.slots()) {
			return;
		}
		int alpha = Math.round(FLASH_ALPHA * Math.min(1.0F, left / (float) FLASH_FADE_MS));
		int color = (alpha << 24) | (BookStyle.INK_HOVER & 0xFFFFFF);
		List<FormattedCharSequence> lines = this.lines(this.flashPage);
		List<Chapters.TitleRun> titles = this.titleRuns(this.flashPage);
		int x = this.geo.textX(slot);
		for (int line = 0; line < Math.min(BookLimits.PAGE_LINES, lines.size()); line++) {
			int width = this.font.width(lines.get(line));
			int at = line;
			if (width > 0 && (titles.isEmpty() || titles.stream().anyMatch(run -> run.has(at)))) {
				int y = this.geo.textY() + line * BookLimits.LINE_HEIGHT;
				graphics.fill(x - 2, y - 1, x + width + 2, y + BookLimits.LINE_HEIGHT, color);
			}
		}
	}

	protected boolean handleClickEvent(@Nullable ClickEvent event) {
		if (event == null) {
			return false;
		}
		LocalPlayer player = Objects.requireNonNull(this.minecraft.player, "Player not available");
		switch (event) {
			case ClickEvent.ChangePage(int target) -> this.jumpTo(target - 1);
			case ClickEvent.RunCommand(String command) -> {
				this.closeContainerOnServer();
				clickCommandAction(player, command, null);
			}
			default -> defaultHandleGameClickEvent(event, this.minecraft, this);
		}
		return true;
	}

	protected void closeContainerOnServer() {
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.popup() != null || this.jumping()) {
			return super.keyPressed(event);
		}
		boolean arrowsFree = this.getFocused() == null && !Keys.alt(event);
		if (this.onCover && arrowsFree && event.isConfirmation()) {
			this.openFromCover();
			return true;
		}
		int delta = 0;
		if (Keys.isPageUp(event) || (arrowsFree && Keys.isLeft(event))) {
			delta = -1;
		} else if (Keys.isPageDown(event) || (arrowsFree && Keys.isRight(event))) {
			delta = 1;
		} else if (Keys.isHome(event)) {
			if (!this.hasCover()) {
				this.turnTo(0);
			} else if (!this.onCover) {
				this.showCover();
				this.playPageTurn();
			}
			return true;
		} else if (Keys.isEnd(event)) {
			this.turnTo(this.pages.size() - 1);
			return true;
		}
		if (delta != 0) {
			if (this.turnPages(delta)) {
				this.playPageTurn();
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected List<List<HelpPopup.Line>> helpGroups() {
		return ShortcutsHelp.reader(Keys.mac(), ScribeConfig.get().fastScrollPages, !this.enchanted, this.hasIndex(),
			BookScreens.shiftHelp(this.hand == null));
	}

	@Override
	public void onClose() {
		this.finishReading();
		super.onClose();
	}

	@Override
	public void removed() {
		super.removed();
		if (this.openingChild) {
			this.openingChild = false;
			return;
		}
		this.finishReading();
	}

	public void openChild(Screen child) {
		this.closePopup();
		this.openingChild = true;
		this.minecraft.gui.setScreen(child);
	}

	public void finishFromChild() {
		this.finishReading();
	}

	private void finishReading() {
		if (this.closed) {
			return;
		}
		this.closed = true;
		if (this.readingSent && this.hand != null) {
			BookNet.sendReading(false, this.hand);
		}
		if (this.hand == null) {
			return;
		}
		PageMemory memory = PageMemory.get();
		if (this.bookKey != null) {
			if (this.onCover) {
				memory.forget(this.bookKey);
			} else {
				memory.put(this.bookKey, this.page);
			}
		}
		memory.save();
		if (!this.enchanted && this.minecraft.player != null) {
			int slot = this.hand == InteractionHand.MAIN_HAND ? this.minecraft.player.getInventory().getSelectedSlot() : BookLimits.OFFHAND_SLOT;
			BookNet.sendBookmark(slot, this.page);
		}
	}

	@Override
	protected void openSettings() {
		this.openChild(new SettingsScreen(this));
	}

	private void openExport() {
		if (PenNames.isOwnBook(this.stack)) {
			this.openChild(BookFilesScreen.forExport(this, this.asBookFile()));
		}
	}

	private void copyBook() {
		if (PenNames.isOwnBook(this.stack)) {
			this.minecraft.keyboardHandler.setClipboard(String.join("\n---\n", this.legacy));
			this.notice(Component.translatable("scribe.notice.book_copied", this.legacy.size()));
		}
	}

	protected BookFile asBookFile() {
		WrittenBookContent written = this.stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		String title = written == null ? null : written.title().raw();
		String author = written == null ? null : written.author();
		return BookFile.of(title, author, this.legacy);
	}

	@Override
	public void resize(int width, int height) {
		this.lineCache.clear();
		this.titleCache.clear();
		super.resize(width, height);
	}
}
