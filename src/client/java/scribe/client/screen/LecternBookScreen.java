package scribe.client.screen;

import scribe.client.BookNet;
import scribe.client.LecternReading;
import scribe.client.text.SharedPages;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import org.jspecify.annotations.Nullable;

public class LecternBookScreen extends BookReaderScreen implements MenuAccess<LecternMenu> {
	private final LecternMenu menu;
	private final ContainerListener listener = new ContainerListener() {
		@Override
		public void slotChanged(AbstractContainerMenu container, int slotIndex, ItemStack itemStack) {
			LecternBookScreen.this.bookChanged();
		}

		@Override
		public void dataChanged(AbstractContainerMenu container, int id, int value) {
			if (id == 0) {
				LecternBookScreen.this.pageChanged();
			}
		}
	};
	private boolean listening;
	private boolean editorPending;
	private boolean bookShown;
	private boolean pageSent;
	private final SharedPages.Turns turns = new SharedPages.Turns();

	public LecternBookScreen(LecternMenu menu) {
		super(menu.getBook(), null);
		this.menu = menu;
		this.page = this.pageMap.firstPageOf(Math.max(0, menu.getPage()));
		if (!menu.getBook().isEmpty()) {
			this.bookShown = true;
			this.startReading();
		}
	}

	@Override
	public LecternMenu getMenu() {
		return this.menu;
	}

	@Override
	protected void initPages() {
		super.initPages();
		if (!this.listening) {
			this.menu.addSlotListener(this.listener);
			this.listening = true;
		}
	}

	private boolean canTakeBook() {
		return this.minecraft.player != null && this.minecraft.player.mayBuild();
	}

	@Override
	protected void createBottomButtons() {
		List<Component> labels = new ArrayList<>();
		List<Runnable> actions = new ArrayList<>();
		labels.add(CommonComponents.GUI_DONE);
		actions.add(this::onClose);
		if (this.canTakeBook()) {
			labels.add(Component.translatable("lectern.take_book"));
			actions.add(() -> this.sendButton(LecternMenu.BUTTON_TAKE_BOOK));
		}
		List<Button> buttons = this.bottomButtons(labels, actions);
		String writer = this.writer();
		if (buttons.size() > 1 && writer != null && this.menu.getBook().has(DataComponents.WRITABLE_BOOK_CONTENT)) {
			Button take = buttons.get(1);
			take.active = false;
			take.setTooltip(Tooltip.create(Component.translatable("scribe.lectern.writing", writer)));
		}
	}

	private @Nullable Boolean lecternClosed() {
		return this.menu == null ? null : BookNet.lecternClosed(this.menu.containerId);
	}

	@Override
	protected boolean hasCover() {
		return LecternReading.hasCover(super.hasCover(), this.lecternClosed());
	}

	@Override
	protected int openingPage() {
		return Math.clamp(this.pageMap.firstPageOf(Math.max(0, this.menu.getPage())), 0, Math.max(0, this.pages.size() - 1));
	}

	private void startReading() {
		Boolean closed = this.lecternClosed();
		this.setOnCover(LecternReading.startsAtCover(closed, this.hasCover()));
		if (LecternReading.opensOnShow(closed, this.hasCover())) {
			BookNet.openLectern(this.menu.containerId);
		}
	}

	public void lecternStateChanged() {
		if (!this.bookShown) {
			return;
		}
		switch (LecternReading.follow(this.onCover(), this.lecternClosed(), this.hasCover())) {
			case TO_COVER -> {
				this.closePopup();
				super.showCover();
			}
			case TO_PAGE -> {
				this.leaveCover();
				this.page = this.openingPage();
				this.updateNavigation();
			}
			case STAY -> {
			}
		}
	}

	@Override
	protected void showCover() {
		boolean wasOnCover = this.onCover();
		super.showCover();
		if (!wasOnCover && this.onCover()) {
			BookNet.closeLectern(this.menu.containerId);
		}
	}

	@Override
	protected void showPage(int index) {
		this.leaveCover();
		int target = Math.clamp(index, 0, Math.max(0, this.pages.size() - 1));
		int request = this.turns.turn(this.pageMap, this.page, target, this.menu.getPage());
		int button = LecternReading.turnButton(request, this.lecternClosed());
		if (button >= 0) {
			this.sendButton(button);
			BookNet.lecternPageTurned(this.menu.containerId);
			this.pageSent = true;
		}
		this.page = target;
		this.updateNavigation();
	}

	@Override
	protected void turnedAtLectern(int leaves) {
		if (!this.pageSent) {
			BookNet.lecternLeaves(this.menu.containerId, leaves);
		}
		this.pageSent = false;
	}

	private void sendButton(int button) {
		if (this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, button);
		}
	}

	private void bookChanged() {
		ItemStack book = this.menu.getBook();
		if (book.isEmpty()) {
			return;
		}
		if (this.opensEditor(book)) {
			if (!this.editorPending) {
				this.editorPending = true;
				this.minecraft.schedule(this::openEditor);
			}
			return;
		}
		this.showBook(book);
	}

	private boolean opensEditor(ItemStack book) {
		return book.has(DataComponents.WRITABLE_BOOK_CONTENT) && BookNet.lecternEditing() && this.canTakeBook() && this.writer() == null;
	}

	private @Nullable String writer() {
		String writer = BookNet.lecternWriter(this.menu.containerId);
		return writer == null || writer.isEmpty() ? null : writer;
	}

	public void lecternWriterChanged() {
		this.bookChanged();
	}

	@Override
	protected @Nullable Component lastingNotice() {
		String writer = this.writer();
		return writer != null && this.menu.getBook().has(DataComponents.WRITABLE_BOOK_CONTENT) ? Component.translatable("scribe.lectern.writing", writer) : null;
	}

	private void openEditor() {
		this.editorPending = false;
		if (this.minecraft.gui.screen() != this) {
			return;
		}
		ItemStack book = this.menu.getBook();
		WritableBookContent writable = book.get(DataComponents.WRITABLE_BOOK_CONTENT);
		if (writable == null || !this.opensEditor(book)) {
			if (!book.isEmpty()) {
				this.showBook(book);
			}
			return;
		}
		this.menu.removeSlotListener(this.listener);
		this.listening = false;
		this.minecraft.gui.setScreen(new BookEditorScreen(new EditTarget.Lectern(this.menu), writable));
	}

	private void showBook(ItemStack book) {
		boolean first = !this.bookShown;
		this.bookShown = true;
		this.setBook(book);
		this.turns.clear();
		this.page = first ? this.pageMap.firstPageOf(this.menu.getPage()) : this.pageMap.follow(this.page, this.menu.getPage());
		if (first) {
			this.startReading();
		}
		this.rebuildWidgets();
	}

	private void pageChanged() {
		this.page = this.turns.heard(this.pageMap, this.page, this.menu.getPage());
		this.updateNavigation();
	}

	@Override
	public void onClose() {
		if (this.minecraft.player != null) {
			this.minecraft.player.closeContainer();
		}
		super.onClose();
	}

	@Override
	protected void closeContainerOnServer() {
		if (this.minecraft.player != null) {
			this.minecraft.player.closeContainer();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
