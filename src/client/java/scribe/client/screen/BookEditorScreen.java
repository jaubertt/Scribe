package scribe.client.screen;

import scribe.BookLimits;
import scribe.SavedDetails;
import scribe.Scribe;
import scribe.binding.Binding;
import scribe.binding.Bookbinding;
import scribe.client.BookNet;
import scribe.client.BookScreens;
import scribe.client.compat.Keys;
import scribe.client.compat.TextKeys;
import scribe.client.io.Backups;
import scribe.client.io.BookFile;
import scribe.client.io.BookFiles;
import scribe.client.memory.BookDetails;
import scribe.client.memory.BookmarkStore;
import scribe.client.memory.PageMemory;
import scribe.client.screen.popup.HelpPopup;
import scribe.client.screen.popup.MenuPopup;
import scribe.client.screen.popup.Popup;
import scribe.client.screen.popup.SymbolPopup;
import scribe.client.text.Alignment;
import scribe.client.text.BookHistory;
import scribe.client.text.BookmarkShift;
import scribe.client.text.PageMoves;
import scribe.client.text.BreakDelete;
import scribe.client.text.Chapters;
import scribe.client.text.ContentsPage;
import scribe.client.text.FormatCodes;
import scribe.client.text.PageBreaks;
import scribe.client.text.PageCodec;
import scribe.client.text.FrameBorders;
import scribe.client.text.PageFitter;
import scribe.client.text.PageFlow;
import scribe.client.text.TidyPages;
import scribe.client.widget.PageEditor;
import scribe.client.widget.PageTextField;
import scribe.client.widget.SpriteButton;
import scribe.config.ScribeConfig;
import scribe.network.Payloads;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import org.jspecify.annotations.Nullable;

public class BookEditorScreen extends PagedBookScreen {
	private static final int WHOLE_BOOK = -1;

	private final EditTarget target;
	private final List<String> pages = new ArrayList<>();
	private List<String> savedPages;
	private String memoryKey;
	private List<BookmarkShift.Anchored> marks = List.of();
	private List<String> marksBook = List.of();
	private List<BookmarkStore.Bookmark> markList = List.of();
	private List<BookmarkStore.Bookmark> savedMarks;
	private final BookmarkStore.Marks editorMarks = new BookmarkStore.Marks() {
		private BookHistory.@Nullable Snapshot before;

		@Override
		public List<BookmarkStore.Bookmark> list() {
			return BookEditorScreen.this.markList;
		}

		@Override
		public void put(BookmarkStore.Bookmark bookmark) {
			this.changing();
			BookEditorScreen.this.putMark(bookmark);
		}

		@Override
		public void remove(int page) {
			this.changing();
			BookEditorScreen.this.removeMark(page);
		}

		private void changing() {
			if (this.before == null) {
				this.before = BookEditorScreen.this.snapshot();
			}
		}

		@Override
		public void done() {
			BookHistory.Snapshot was = this.before;
			this.before = null;
			if (was != null && !was.marks().equals(BookEditorScreen.this.markList)) {
				BookEditorScreen.this.history.record(was, false);
				BookEditorScreen.this.lastState = BookEditorScreen.this.snapshot();
				BookEditorScreen.this.updateButtons();
			}
		}
	};
	private int page;
	private final BookHistory history = new BookHistory();
	private BookHistory.@Nullable Snapshot lastState;
	private boolean finished;
	private boolean openingChild;
	private boolean readingSent;
	private String signTitle = "";
	private String signSubtitle = "";
	private String signPenName = "";
	private String savedTitle = "";
	private String savedSubtitle = "";
	private String savedPenName = "";
	private @Nullable CoverChoices signChoices;
	private boolean onCover;
	private @Nullable CoverView coverView;
	private final List<SpriteButton> writingTools = new ArrayList<>();
	private PageFitter fitter;
	private final List<PageEditor> editors = new ArrayList<>();
	private int suppress;
	private boolean altArmed;
	private boolean swallowText;
	private int symbolSlot;
	private int pendingAnchor = -1;
	private int pendingCaret = -1;
	private @Nullable ContainerListener lecternListener;

	private SpriteButton undoButton;
	private SpriteButton redoButton;
	private final Map<Character, SpriteButton> styleButtons = new HashMap<>();
	private final Map<Alignment.Mode, SpriteButton> alignButtons = new HashMap<>();
	private final List<SpriteButton> insertButtons = new ArrayList<>();
	private final List<SpriteButton> pageToolButtons = new ArrayList<>();
	private @Nullable SpriteButton contentsAdd;
	private @Nullable SpriteButton contentsUpdate;
	private @Nullable SpriteButton contentsRemove;

	public BookEditorScreen(EditTarget target, WritableBookContent content) {
		super(Component.translatable("book.edit.title"));
		this.target = target;
		List<String> stored = new ArrayList<>();
		content.getPages(net.minecraft.client.Minecraft.getInstance().isTextFilteringEnabled()).forEach(stored::add);
		if (stored.isEmpty()) {
			stored.add("");
		}
		this.pages.addAll(PageCodec.decodeBook(stored));
		this.savedPages = this.savedForm();
		this.memoryKey = PageMemory.keyForPages(stored);
		this.setMarks(BookmarkShift.anchor(BookmarkShift.within(BookmarkStore.get().list(this.memoryKey), this.pages.size()), this.pages));
		this.savedMarks = this.markList;
		this.page = this.startPage();
		this.cover = BookStyle.cover(target.book());
		this.showBinding();
		boolean hasText = QuillCover.hasText(stored);
		QuillCover.Opened opened = QuillCover.opened(target.book(), hasText, BookDetails.get().get(this.memoryKey));
		this.signTitle = opened.sign().title();
		this.signSubtitle = opened.sign().subtitle();
		this.signPenName = opened.sign().penName();
		this.savedTitle = opened.shown().title();
		this.savedSubtitle = opened.shown().subtitle();
		this.savedPenName = opened.shown().penName();
		PageMemory memory = PageMemory.get();
		this.onCover = QuillCover.startsAtCover(QuillCover.wanted(), ScribeConfig.get().editorStart, target.onLectern(), hasText,
			memory.knows(this.memoryKey), memory.atCover(this.memoryKey));
	}

	private int startPage() {
		int last = this.pages.size() - 1;
		return switch (ScribeConfig.get().editorStart) {
			case FIRST -> 0;
			case LAST -> last;
			case REMEMBERED -> {
				int remembered = PageMemory.get().get(this.memoryKey);
				yield remembered < 0 ? 0 : Math.min(remembered, last);
			}
		};
	}

	public EditTarget target() {
		return this.target;
	}

	@Override
	protected void initPages() {
		if (this.onCover && !this.hasCover()) {
			this.onCover = false;
		}
		this.coverView = null;
		this.fitter = new PageFitter(this.font);
		this.editors.clear();
		this.styleButtons.clear();
		this.alignButtons.clear();
		this.insertButtons.clear();
		this.pageToolButtons.clear();
		ScribeConfig config = ScribeConfig.get();
		for (int slot = 0; slot < this.geo.slots(); slot++) {
			PageEditor editor = new PageEditor(this.font, this.geo.textX(slot), this.geo.textY());
			editor.setShowMarks(config.formatMarksShown());
			editor.setValueListener(value -> this.onEditorChanged(editor, value));
			editor.field().setOverflowHandler(edit -> this.placeText(editor.pageIndex(), edit.text(), edit.selectionStart(), edit.selectionEnd()));
			editor.setFocusListener(this::onEditorFocused);
			this.addRenderableWidget(editor);
			this.editors.add(editor);
		}
		int toolsFrom = this.children().size();
		this.buildBar();
		this.buildTools();
		this.writingTools.clear();
		for (GuiEventListener child : this.children().subList(toolsFrom, this.children().size())) {
			if (child instanceof SpriteButton tool) {
				this.writingTools.add(tool);
			}
		}
		this.buildPageTools();
		this.buildContentsTools();
		this.buildButtons();

		if (this.pendingCaret >= 0) {
			this.displayPage(this.page, this.pendingAnchor, this.pendingCaret);
		} else {
			this.openPage(this.page, Integer.MAX_VALUE);
		}
		if (this.lastState == null) {
			this.lastState = this.snapshot();
		}
		if (!this.readingSent && this.target.hand() != null) {
			this.readingSent = true;
			BookNet.sendReading(true, this.target.hand());
		}
		if (this.target instanceof EditTarget.Lectern lectern && this.lecternListener == null) {
			this.lecternListener = new ContainerListener() {
				@Override
				public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) {
					if (!stack.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
						BookEditorScreen.this.minecraft.schedule(BookEditorScreen.this::lecternBookChanged);
					}
				}

				@Override
				public void dataChanged(AbstractContainerMenu menu, int id, int value) {
				}
			};
			lectern.menu().addSlotListener(this.lecternListener);
			BookNet.openLectern(lectern.menu().containerId);
		} else if (this.lecternBookLost()) {
			this.minecraft.schedule(this::lecternBookChanged);
		}
	}

	private static Component tip(String key, @Nullable String shortcut) {
		Component name = Component.translatable("scribe.button." + key);
		return shortcut == null ? name : Component.translatable("scribe.tooltip.with_shortcut", name, shortcut);
	}

	private SpriteButton cell(int x, int y, String name, Component tooltip, Runnable action) {
		return this.addRenderableWidget(new SpriteButton(x, y, BookGeometry.CELL_WIDTH, BookGeometry.CELL_HEIGHT, "tool/" + name, tooltip, action));
	}

	private void buildBar() {
		int bx = this.geo.barX();
		int by = this.geo.barY();
		String ctrl = Keys.ctrlName();
		this.styleButtons.put('l', this.cell(bx, by, "bold", tip("bold", ctrl + "+B"), () -> this.applyStyle('l')));
		this.styleButtons.put('o', this.cell(bx + 11, by, "italic", tip("italic", ctrl + "+I"), () -> this.applyStyle('o')));
		this.styleButtons.put('n', this.cell(bx + 22, by, "underline", tip("underline", ctrl + "+U"), () -> this.applyStyle('n')));
		this.styleButtons.put('m', this.cell(bx + 33, by, "strikethrough", tip("strikethrough", ctrl + "+Shift+X"), () -> this.applyStyle('m')));
		this.styleButtons.put('k', this.cell(bx + 44, by, "obfuscated", tip("obfuscated", ctrl + "+K"), () -> this.applyStyle('k')));
		this.cell(bx + 55, by, "clear_format", tip("reset", Keys.clearFormattingKeys()), this::resetFormatting);
		for (int i = 0; i < 16; i++) {
			char code = FormatCodes.COLOR_CODES.charAt(i);
			ChatFormatting format = ChatFormatting.getByCode(code);
			Component name = Component.translatable("scribe.color." + (format == null ? "black" : format.name().toLowerCase(Locale.ROOT)));
			boolean top = i < 8;
			int x = bx + 69 + 6 * (top ? i : i - 8);
			this.addRenderableWidget(new SpriteButton(x, top ? by : by + 6, 7, top ? 7 : 9, "tool/color_" + code, name, () -> this.applyColor(code)));
		}
		this.cell(bx + 120, by, "align_left", tip("align_left", ctrl + "+L"), () -> this.align(Alignment.Mode.LEFT));
		this.alignButtons.put(Alignment.Mode.CENTER, this.cell(bx + 131, by, "align_center", tip("align_center", ctrl + "+E"), () -> this.align(Alignment.Mode.CENTER)));
		this.alignButtons.put(Alignment.Mode.RIGHT, this.cell(bx + 142, by, "align_right", tip("align_right", ctrl + "+R"), () -> this.align(Alignment.Mode.RIGHT)));
		this.cell(bx + 156, by, "chapter", tip("chapter", ctrl + "+" + Keys.altName() + "+1"), this::toggleChapter);
		this.cell(bx + 167, by, "symbols", tip("symbols", null), this::openSymbols);
		this.undoButton = this.cell(bx + 181, by, "undo", tip("undo", ctrl + "+Z"), this::undo);
		this.redoButton = this.cell(bx + 192, by, "redo", tip("redo", ctrl + "+Shift+Z"), this::redo);
	}

	private void buildTools() {
		int x = this.geo.toolsX();
		this.cell(x, this.geo.writingToolY(0), "load", tip("load", null), this::openImport);
		this.cell(x, this.geo.writingToolY(1), "save", tip("save", null), this::openExport);
		this.cell(x, this.geo.writingToolY(2), "copy_book", tip("copy_book", null), this::copyBook);
		this.cell(x, this.geo.writingToolY(3), "paste_book", tip("paste_book", null), this::pasteBook);
		this.cell(x, this.geo.writingToolY(4), "clear_book", tip("clear_book", null), this::clearBook);
	}

	private void buildPageTools() {
		if (this.geo.spread) {
			int y = this.geo.pageToolsY();
			for (int slot = 0; slot < 2; slot++) {
				this.pageTools(slot, this.geo.pageToolX(slot, 0), y, this.geo.pageToolX(slot, 1), y, this.geo.pageToolX(slot, 2), y);
			}
		} else {
			int x = this.geo.indexX();
			SpriteButton insert = this.cell(x, this.geo.cellUnderIndexY(0), "new_page", tip("new_page", null),
				() -> this.insertPageAt(this.slotPage(0) + (Keys.shiftHeldNow() ? 0 : 1)));
			insert.setTooltipText(Component.translatable("scribe.button.new_page.tooltip"));
			this.insertButtons.add(insert);
			this.pageToolButtons.add(insert);
			this.pageToolButtons.add(this.cell(x, this.geo.cellUnderIndexY(1), "delete_page", tip("delete_page", null), () -> this.deletePage(this.slotPage(0))));
			int moreY = this.geo.cellUnderIndexY(2);
			this.pageToolButtons.add(this.cell(x, moreY, "more", tip("more_page_tools", null),
				() -> this.openPageMenu(0, x + BookGeometry.CELL_WIDTH + 2, moreY, true)));
		}
	}

	private void buildContentsTools() {
		int x = this.geo.indexX();
		int y = this.geo.cellUnderIndexY(0);
		this.contentsAdd = this.cell(x, y, "contents_add", Component.translatable("scribe.index.print_contents"), this::printContents);
		this.contentsUpdate = this.cell(x, y, "contents_update", Component.translatable("scribe.index.update_contents"), this::printContents);
		this.contentsRemove = this.cell(x, this.geo.cellUnderIndexY(1), "contents_remove", Component.translatable("scribe.index.remove_contents"),
			this::removeContents);
		this.contentsRemove.setTooltipText(described("remove_contents", "remove_contents.tooltip"));
		this.updateContentsTools();
	}

	private static Component described(String name, String text) {
		return Component.translatable("scribe.index." + name).append("\n").append(Component.translatable("scribe.index." + text));
	}

	private void updateContentsTools() {
		boolean open = this.indexOpen;
		for (SpriteButton tool : this.pageToolButtons) {
			tool.visible = !open && !this.onCover;
		}
		if (this.contentsAdd == null || this.contentsUpdate == null || this.contentsRemove == null) {
			return;
		}
		boolean inView = !open && !this.onCover && this.contentsInView();
		boolean has = open ? ContentsPage.has(this.pages) : inView;
		boolean chapters = (open || inView) && !this.chapters().isEmpty();
		this.contentsAdd.visible = open && !has;
		this.contentsAdd.active = chapters;
		this.contentsAdd.setTooltipText(described("print_contents", chapters ? "print_contents.tooltip" : "no_chapters_tooltip"));
		this.contentsUpdate.visible = has;
		this.contentsUpdate.setY(this.geo.contentsCellY(0, open));
		this.contentsUpdate.active = chapters;
		this.contentsUpdate.setTooltipText(described("update_contents", chapters ? "update_contents.tooltip" : "no_chapters_tooltip"));
		this.contentsRemove.visible = has;
		this.contentsRemove.setY(this.geo.contentsCellY(1, open));
	}

	private boolean contentsInView() {
		for (int slot = 0; slot < this.geo.slots(); slot++) {
			int index = this.baseIndex() + slot;
			if (index < this.pages.size() && Chapters.isContentsPage(this.pages.get(index))) {
				return true;
			}
		}
		return false;
	}

	private void pageTools(int slot, int insertX, int insertY, int removeX, int removeY, int moreX, int moreY) {
		SpriteButton insert = this.addRenderableWidget(new SpriteButton(insertX, insertY, 13, 13, "page/insert", tip("new_page", null),
			input -> this.insertPageAt(this.slotPage(slot) + (input.hasShiftDown() ? 0 : 1))));
		insert.setTooltipText(Component.translatable("scribe.button.new_page.tooltip"));
		this.insertButtons.add(insert);
		this.pageToolButtons.add(insert);
		this.pageToolButtons.add(this.addRenderableWidget(new SpriteButton(removeX, removeY, 13, 13, "page/remove", tip("delete_page", null),
			() -> this.deletePage(this.slotPage(slot)))));
		this.pageToolButtons.add(this.addRenderableWidget(new SpriteButton(moreX, moreY, 13, 13, "page/more", tip("more_page_tools", null),
			() -> this.openPageMenu(slot, moreX, moreY, false))));
	}

	private int slotPage(int slot) {
		return Math.min(this.baseIndex() + slot, this.pages.size() - 1);
	}

	private void buildButtons() {
		List<Component> labels = new ArrayList<>();
		List<Runnable> actions = new ArrayList<>();
		labels.add(Component.translatable("book.signButton"));
		actions.add(this::openSignScreen);
		labels.add(CommonComponents.GUI_DONE);
		actions.add(this::saveAndClose);
		if (this.target.canTakeBook()) {
			labels.add(Component.translatable("lectern.take_book"));
			actions.add(this::takeBook);
		}
		int n = labels.size();
		int w = BookGeometry.buttonWidth(n);
		int x0 = this.geo.buttonsX(n);
		for (int i = 0; i < n; i++) {
			Runnable action = actions.get(i);
			this.addRenderableWidget(Button.builder(labels.get(i), b -> action.run())
				.bounds(x0 + i * (w + 4), this.geo.buttonsY(), w, BookGeometry.BUTTON_HEIGHT).build());
		}
	}

	private int buttonCount() {
		return this.target.canTakeBook() ? 3 : 2;
	}

	@Override
	protected void rebuildWidgets() {
		this.rememberCaret();
		super.rebuildWidgets();
	}

	private void rememberCaret() {
		PageEditor editor = this.editorFor(this.page);
		if (editor != null) {
			this.pendingAnchor = editor.field().anchor();
			this.pendingCaret = editor.field().cursor();
		}
	}

	@Override
	protected void setInitialFocus() {
		if (this.onCover) {
			return;
		}
		PageEditor editor = this.editorFor(this.page);
		if (editor != null) {
			this.setFocused(editor);
		} else {
			super.setInitialFocus();
		}
	}

	private void updateButtons() {
		boolean room = this.pages.size() < BookLimits.MAX_PAGES;
		this.undoButton.active = this.history.canUndo();
		this.redoButton.active = this.history.canRedo();
		for (SpriteButton insert : this.insertButtons) {
			insert.active = room;
		}
		this.updateNavigation();
	}

	@Override
	protected void updateNavigation() {
		super.updateNavigation();
		for (SpriteButton tool : this.writingTools) {
			tool.visible = !this.onCover;
		}
		this.updateContentsTools();
	}

	@Override
	protected void setIndexOpen(boolean open) {
		super.setIndexOpen(open);
		for (PageEditor editor : this.editors) {
			editor.visible = !open && !this.onCover && editor.pageIndex() >= 0;
			editor.active = editor.visible;
		}
		if (!open && !this.onCover) {
			PageEditor editor = this.editorFor(this.page);
			if (editor != null) {
				this.setFocused(editor);
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		PageEditor editor = this.focusedOrCurrent();
		if (editor != null) {
			PageTextField f = editor.field();
			int start = f.selectionStart();
			int end = f.selectionEnd();
			FormatCodes.State state = FormatCodes.stateAt(f.value(), f.cursor());
			for (Map.Entry<Character, SpriteButton> entry : this.styleButtons.entrySet()) {
				boolean on = start != end ? FormatCodes.allHave(f.value(), start, end, entry.getKey()) : state.has(entry.getKey());
				entry.getValue().setSelected(on);
			}
			Alignment.Mode mode = Alignment.modeAt(f.value(), f.cursor());
			for (Map.Entry<Alignment.Mode, SpriteButton> entry : this.alignButtons.entrySet()) {
				entry.getValue().setSelected(entry.getKey() == mode);
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private int baseIndex() {
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
		return this.pages;
	}

	@Override
	protected boolean canTurnForward() {
		return super.canTurnForward() || this.pages.size() < BookLimits.MAX_PAGES;
	}

	@Override
	protected void showPage(int index) {
		this.leaveCover();
		this.history.breakGroup();
		this.openPage(Math.clamp(index, 0, this.pages.size() - 1), Integer.MAX_VALUE);
	}

	private void openPage(int index, int caret) {
		this.displayPage(index, caret, caret);
		PageEditor focused = this.editorFor(this.page);
		if (focused != null) {
			focused.field().placeCaret(Math.min(caret, focused.getValue().length()));
		}
	}

	private void displayPage(int index, int anchor, int caret) {
		this.page = Math.clamp(index, 0, this.pages.size() - 1);
		int base = this.baseIndex();
		if (this.geo.spread && base + 1 >= this.pages.size() && this.pages.size() < BookLimits.MAX_PAGES) {
			this.pages.add("");
		}
		this.suppress++;
		try {
			for (int slot = 0; slot < this.editors.size(); slot++) {
				PageEditor editor = this.editors.get(slot);
				int pageIndex = base + slot;
				boolean exists = pageIndex < this.pages.size();
				editor.setPageIndex(exists ? pageIndex : -1);
				editor.visible = exists && !this.indexOpen && !this.onCover;
				editor.active = editor.visible;
				if (exists) {
					editor.field().setValue(PageBreaks.text(this.pages.get(pageIndex)), true);
				}
			}
		} finally {
			this.suppress--;
		}
		PageEditor focused = this.editorFor(this.page);
		if (focused != null && !this.indexOpen && !this.onCover) {
			this.setFocused(focused);
			focused.setFocused(true);
			String value = focused.getValue();
			int c = caret < 0 ? value.length() : Math.min(caret, value.length());
			int a = anchor < 0 ? c : Math.min(anchor, value.length());
			focused.field().select(a, c);
		}
		this.pendingAnchor = -1;
		this.pendingCaret = -1;
		this.updateButtons();
	}

	private @Nullable PageEditor editorFor(int pageIndex) {
		for (PageEditor editor : this.editors) {
			if (editor.pageIndex() == pageIndex) {
				return editor;
			}
		}
		return null;
	}

	private @Nullable PageEditor focusedEditor() {
		GuiEventListener focused = this.getFocused();
		return !this.onCover && focused instanceof PageEditor editor && editor.pageIndex() >= 0 ? editor : null;
	}

	private void onEditorFocused(PageEditor editor) {
		for (PageEditor other : this.editors) {
			if (other != editor && other.field().selectionStart() != other.field().selectionEnd()) {
				other.field().select(other.field().cursor(), other.field().cursor());
			}
		}
		if (editor.pageIndex() >= 0 && editor.pageIndex() != this.page) {
			this.page = editor.pageIndex();
			this.history.breakGroup();
			this.updateButtons();
		}
	}

	@Override
	protected boolean turnPages(int delta) {
		int step = this.geo.spread ? 2 : 1;
		if (this.onCover) {
			int open = QuillCover.openingPage(delta, step, this.lastBase());
			if (open < 0) {
				return false;
			}
			this.showPage(open);
			return true;
		}
		int base = this.baseIndex();
		if (QuillCover.turnsToCover(base, delta, step, this.hasCover())) {
			this.showCover();
			return true;
		}
		int last = this.pages.size() - 1;
		int target = base + delta * step;
		if (target > last) {
			if (delta == 1 && base + step > last && this.pages.size() < BookLimits.MAX_PAGES && !PageBreaks.text(this.pages.get(last)).isEmpty()) {
				this.pages.add("");
				last++;
			}
			target = Math.min(target, last);
		}
		target = Math.max(0, target);
		if (target == base || (this.geo.spread && (target & ~1) == base)) {
			return false;
		}
		this.history.breakGroup();
		this.openPage(target, Integer.MAX_VALUE);
		return true;
	}

	private boolean hasCover() {
		return QuillCover.wanted();
	}

	@Override
	protected boolean showsPages() {
		return !this.onCover;
	}

	private void showCover() {
		if (!this.hasCover() || this.onCover) {
			return;
		}
		this.onCover = true;
		this.coverView = null;
		this.closePopup();
		this.history.breakGroup();
		this.page = 0;
		this.setIndexOpen(false);
		this.setFocused(null);
		this.updateButtons();
	}

	private void leaveCover() {
		this.onCover = false;
	}

	private void openFromCover() {
		this.showPage(0);
		this.playPageTurn();
	}

	private CoverView coverView() {
		if (this.coverView == null) {
			this.coverView = QuillCover.view(this, this.font);
		}
		return this.coverView;
	}

	@Override
	protected void drawBook(GuiGraphicsExtractor graphics) {
		if (this.onCover) {
			this.coverView().drawArt(graphics, QuillCover.coverX(this.geo), this.geo.top);
			return;
		}
		super.drawBook(graphics);
	}

	@Override
	protected void extractPages(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (this.onCover) {
			this.coverView().drawText(graphics, this.font, QuillCover.coverX(this.geo), this.geo.top);
			if (this.popup() == null && QuillCover.over(this.geo, mouseX, mouseY)) {
				graphics.setTooltipForNextFrame(this.font, Component.translatable("scribe.cover.open"), mouseX, mouseY);
			}
			return;
		}
		super.extractPages(graphics, mouseX, mouseY, a);
	}

	@Override
	protected boolean canTurnBack() {
		return QuillCover.canTurnBack(this.basePage(), this.hasCover());
	}

	@Override
	protected void turnToStart() {
		if (QuillCover.startIsCover(this.basePage(), this.hasCover())) {
			this.showCover();
		} else {
			super.turnToStart();
		}
	}

	@Override
	protected Component startTooltip() {
		return QuillCover.startIsCover(this.basePage(), this.hasCover()) ? Component.translatable("scribe.button.cover.tooltip") : super.startTooltip();
	}

	@Override
	protected void turnTo(int page) {
		if (this.onCover) {
			this.showPage(page);
			return;
		}
		super.turnTo(page);
	}

	private boolean coverKey(KeyEvent event, TextKeys.Shortcut shortcut) {
		switch (QuillCover.key(event, shortcut, this.getFocused() == null)) {
			case OPEN -> this.openFromCover();
			case OPEN_LAST -> this.turnTo(this.pages.size() - 1);
			case SAVE -> this.save(true);
			case SCREEN -> {
				return super.keyPressed(event);
			}
			case NOTHING -> {
			}
		}
		return true;
	}

	private BookHistory.Snapshot snapshot() {
		PageEditor editor = this.editorFor(this.page);
		int caret = editor == null ? 0 : editor.field().cursor();
		int anchor = editor == null ? 0 : editor.field().anchor();
		this.syncMarks();
		return BookHistory.Snapshot.of(this.pages, this.page, caret, anchor, this.marks);
	}

	private void onEditorChanged(PageEditor editor, String value) {
		if (this.suppress > 0) {
			return;
		}
		int index = editor.pageIndex();
		if (index < 0 || index >= this.pages.size() || value.equals(PageBreaks.text(this.pages.get(index)))) {
			return;
		}
		PageTextField field = editor.field();
		FormatCodes.Edit lined = this.lineUpFrames(new FormatCodes.Edit(value, field.anchor(), field.cursor()), field.cursor());
		if (!lined.text().equals(value)) {
			this.suppress++;
			try {
				field.setTextAndSelection(lined.text(), lined.selectionStart(), lined.selectionEnd());
			} finally {
				this.suppress--;
			}
			value = lined.text();
			if (value.equals(PageBreaks.text(this.pages.get(index)))) {
				return;
			}
		}
		this.history.record(this.lastState, true);
		this.page = index;
		int editAt = BookmarkShift.editAt(PageBreaks.text(this.pages.get(index)), value, field.cursor());
		PageFlow.Result result = this.flowing()
			? PageFlow.reflow(this.pages, index, value, field.cursor(), this.fitter, true, BookLimits.MAX_PAGES)
			: null;
		String typed = PageBreaks.page(PageBreaks.markOf(this.pages.get(index)), value);
		if (result == null || result.pages().size() == this.pages.size() && result.pages().get(index).equals(typed)
			&& sameExcept(result.pages(), this.pages, index)) {
			List<String> before = this.marks.isEmpty() ? List.of() : List.copyOf(this.pages);
			this.pages.set(index, typed);
			this.marksFollow(before, index, editAt, Set.of());
		} else {
			int anchor = field.anchor();
			int caret = field.cursor();
			List<String> before = List.copyOf(this.pages);
			this.pages.clear();
			this.pages.addAll(result.pages());
			this.marksFollow(before, index, editAt, Set.of());
			if (result.caretPage() == index && result.caretPos() == caret) {
				this.displayPage(index, anchor, caret);
			} else {
				this.displayPage(result.caretPage(), result.caretPos(), result.caretPos());
			}
		}
		this.afterChange();
	}

	private static boolean sameExcept(List<String> a, List<String> b, int index) {
		for (int i = 0; i < a.size(); i++) {
			if (i != index && !a.get(i).equals(b.get(i))) {
				return false;
			}
		}
		return true;
	}

	private boolean flowing() {
		return ScribeConfig.get().overflowMode == ScribeConfig.OverflowMode.FLOW;
	}

	private void hardBefore(int index) {
		if (index >= 0 && index < this.pages.size()) {
			this.pages.set(index, PageBreaks.text(this.pages.get(index)));
		}
	}

	private void afterChange() {
		this.lastState = this.snapshot();
		this.chaptersChanged();
		this.updateButtons();
		if (this.target instanceof EditTarget.Lectern lectern) {
			BookNet.lecternTyped(lectern.menu().containerId);
		}
	}

	public void lecternPenTaken(int containerId, String writer) {
		if (this.finished || !(this.target instanceof EditTarget.Lectern lectern) || lectern.menu().containerId != containerId) {
			return;
		}
		this.finished = true;
		boolean dirty = this.isDirty();
		if (dirty) {
			this.backUp();
		}
		if (this.minecraft.player != null) {
			boolean backedUp = dirty && ScribeConfig.get().backupsOn();
			this.minecraft.player.sendOverlayMessage(Component.translatable(backedUp ? "scribe.notice.lectern_pen_backup" : "scribe.notice.lectern_pen", writer));
		}
		this.rememberPage();
		this.keepMarks();
		this.target.closed();
		this.minecraft.gui.setScreen(null);
	}

	private boolean isDirty() {
		return !this.savedForm().equals(this.savedPages);
	}

	private List<String> savedForm() {
		return PageCodec.encodeBook(this.font, trimmed(this.pages));
	}

	private static List<String> trimmed(List<String> pages) {
		List<String> out = new ArrayList<>(pages);
		while (!out.isEmpty() && PageBreaks.text(out.getLast()).isEmpty()) {
			out.removeLast();
		}
		return out;
	}

	private void replacePages(List<String> newPages, int focusPage, int from) {
		this.replacePages(newPages, focusPage, from, Set.of());
	}

	private void replacePages(List<String> newPages, int focusPage, int from, Set<Integer> removed) {
		this.history.record(this.lastState, false);
		List<String> before = List.copyOf(this.pages);
		this.pages.clear();
		this.pages.addAll(newPages.isEmpty() ? List.of("") : newPages);
		this.marksFollow(before, from, BookmarkShift.PAGE_START, removed);
		this.displayPage(Math.clamp(focusPage, 0, this.pages.size() - 1), 0, 0);
		this.afterChange();
	}

	private void marksFollow(List<String> before, int from) {
		this.marksFollow(before, from, BookmarkShift.PAGE_START, Set.of());
	}

	private void marksFollow(List<String> before, int page, int pos, Set<Integer> removed) {
		if (this.marks.isEmpty()) {
			this.markList = List.of();
			return;
		}
		if (page == WHOLE_BOOK) {
			this.setMarks(BookmarkShift.anchor(BookmarkShift.within(this.markList, this.pages.size()), this.pages));
			return;
		}
		List<BookmarkShift.Anchored> current = this.marks;
		if (!this.marksBook.equals(before)) {
			current = BookmarkShift.follow(current, this.marksBook, before, BookmarkShift.ANYWHERE, BookmarkShift.PAGE_START, Set.of());
		}
		this.setMarks(BookmarkShift.follow(current, before, this.pages, page, pos, removed));
	}

	private void setMarks(List<BookmarkShift.Anchored> marks) {
		this.marks = marks;
		this.marksBook = List.copyOf(this.pages);
		this.markList = BookmarkShift.marks(marks);
	}

	private void syncMarks() {
		if (!this.marks.isEmpty() && !this.marksBook.equals(this.pages)) {
			this.setMarks(BookmarkShift.follow(this.marks, this.marksBook, this.pages, BookmarkShift.ANYWHERE, BookmarkShift.PAGE_START, Set.of()));
		}
	}

	private void putMark(BookmarkStore.Bookmark bookmark) {
		this.syncMarks();
		List<BookmarkShift.Anchored> out = new ArrayList<>();
		for (BookmarkStore.Bookmark mark : BookmarkStore.with(this.markList, bookmark)) {
			int at = this.marks.stream().filter(a -> a.page() == mark.page()).mapToInt(BookmarkShift.Anchored::at).findFirst()
				.orElseGet(() -> BookmarkShift.pageStart(this.pages, mark.page()));
			out.add(new BookmarkShift.Anchored(mark, at));
		}
		this.setMarks(List.copyOf(out));
	}

	private void removeMark(int page) {
		this.syncMarks();
		this.setMarks(this.marks.stream().filter(a -> a.page() != page).toList());
	}

	private FormatCodes.Edit lineUpFrames(FormatCodes.Edit edit, int caret) {
		if (!ScribeConfig.get().framesOn() || this.fitter == null) {
			return edit;
		}
		FrameBorders.Result lined = FrameBorders.lineUp(this.fitter, edit.text(), caret);
		return lined == null ? edit : lined.apply(edit);
	}

	private boolean placeText(int index, String text, int anchor, int caret) {
		FormatCodes.Edit lined = this.lineUpFrames(new FormatCodes.Edit(text, anchor, caret), caret);
		text = lined.text();
		anchor = lined.selectionStart();
		caret = lined.selectionEnd();
		int editAt = index < this.pages.size() ? BookmarkShift.editAt(PageBreaks.text(this.pages.get(index)), text, caret) : 0;
		PageFlow.Result result = PageFlow.reflow(this.pages, index, text, caret, this.fitter, this.flowing(), BookLimits.MAX_PAGES);
		if (result == null) {
			this.notice(Component.translatable("scribe.notice.book_full"));
			return false;
		}
		this.history.record(this.lastState, false);
		List<String> before = List.copyOf(this.pages);
		this.pages.clear();
		this.pages.addAll(result.pages());
		this.marksFollow(before, index, editAt, Set.of());
		boolean stayed = result.caretPage() == index && result.caretPos() == caret;
		this.displayPage(result.caretPage(), stayed ? anchor : result.caretPos(), result.caretPos());
		this.afterChange();
		return true;
	}

	private void typeText(PageEditor editor, String text) {
		PageTextField field = editor.field();
		FormatCodes.Edit edit = field.previewReplace(text);
		if (!field.apply(edit)) {
			this.placeText(editor.pageIndex(), edit.text(), edit.selectionStart(), edit.selectionEnd());
		}
	}

	private void insertRaw(PageEditor editor, String text) {
		FormatCodes.Edit edit = editor.field().previewReplace(text);
		this.placeText(editor.pageIndex(), edit.text(), edit.selectionStart(), edit.selectionEnd());
	}

	private void applyEdit(PageEditor editor, FormatCodes.Edit edit) {
		this.placeText(editor.pageIndex(), edit.text(), edit.selectionStart(), edit.selectionEnd());
	}

	private void applyStyle(char code) {
		PageEditor editor = this.focusedOrCurrent();
		if (editor != null) {
			PageTextField f = editor.field();
			this.applyEdit(editor, FormatCodes.toggleStyle(f.value(), f.selectionStart(), f.selectionEnd(), code));
		}
	}

	private void applyColor(char code) {
		PageEditor editor = this.focusedOrCurrent();
		if (editor != null) {
			PageTextField f = editor.field();
			this.applyEdit(editor, FormatCodes.setColor(f.value(), f.selectionStart(), f.selectionEnd(), code));
		}
	}

	private void resetFormatting() {
		PageEditor editor = this.focusedOrCurrent();
		if (editor != null) {
			PageTextField f = editor.field();
			this.applyEdit(editor, FormatCodes.clear(f.value(), f.selectionStart(), f.selectionEnd()));
		}
	}

	private void align(Alignment.Mode mode) {
		PageEditor editor = this.focusedOrCurrent();
		if (editor == null) {
			return;
		}
		PageTextField f = editor.field();
		int index = editor.pageIndex();
		Alignment.BookEdit change = Alignment.set(this.pages, index, f.selectionStart(), f.selectionEnd(), mode);
		FormatCodes.Edit edit = change.page();
		PageFlow.Result result = PageFlow.reflow(change.pages(), index, edit.text(), edit.selectionEnd(), this.fitter, this.flowing(), BookLimits.MAX_PAGES);
		if (result == null) {
			this.notice(Component.translatable("scribe.notice.book_full"));
			return;
		}
		this.history.record(this.lastState, false);
		List<String> before = List.copyOf(this.pages);
		this.pages.clear();
		this.pages.addAll(result.pages());
		this.marksFollow(before, index, BookmarkShift.editAt(PageBreaks.text(before.get(index)), edit.text(), edit.selectionStart()), Set.of());
		boolean stayed = result.caretPage() == index && result.caretPos() == edit.selectionEnd();
		this.displayPage(result.caretPage(), stayed ? edit.selectionStart() : result.caretPos(), result.caretPos());
		this.afterChange();
	}

	private String newParagraph(PageTextField field) {
		return "\n" + Alignment.mark(Alignment.modeAt(field.value(), Math.min(field.selectionStart(), field.selectionEnd())));
	}

	private void toggleChapter() {
		PageEditor editor = this.focusedOrCurrent();
		if (editor != null) {
			PageTextField f = editor.field();
			this.applyEdit(editor, Chapters.toggle(f.value(), f.cursor()));
		}
	}

	private void openSymbols() {
		if (this.popup() instanceof SymbolPopup) {
			this.closePopup();
			return;
		}
		int slot = this.caretSlot();
		SymbolPlacement place = this.symbolPlacement(slot);
		SymbolPopup popup = new SymbolPopup(this.font, symbol -> {
			PageEditor editor = this.focusedOrCurrent();
			if (editor != null) {
				this.typeText(editor, symbol);
			}
		}, place.columns(), place.rows());
		this.openPopup(popup, place.x(), place.y(), place.shift());
		this.symbolSlot = slot;
	}

	private int caretSlot() {
		PageEditor current = this.focusedOrCurrent();
		return current == null ? 0 : Math.max(0, this.editors.indexOf(current));
	}

	private SymbolPlacement symbolPlacement(int slot) {
		PageEditor current = this.focusedOrCurrent();
		int line = current == null ? 0 : current.field().caretLine();
		return SymbolPlacement.forBook(this.geo, this.buttonCount(), slot, line);
	}

	@Override
	public void tick() {
		super.tick();
		this.followCaretWithSymbols();
	}

	private void followCaretWithSymbols() {
		if (!(this.popup() instanceof SymbolPopup) || !this.geo.spread) {
			return;
		}
		int slot = this.caretSlot();
		if (slot != this.symbolSlot) {
			SymbolPlacement place = this.symbolPlacement(slot);
			this.movePopup(place.x(), place.y());
			this.symbolSlot = slot;
		}
	}

	private @Nullable PageEditor focusedOrCurrent() {
		PageEditor focused = this.focusedEditor();
		return focused != null ? focused : this.editorFor(this.page);
	}

	private void insertPageAt(int index) {
		if (this.pages.size() >= BookLimits.MAX_PAGES) {
			this.notice(Component.translatable("scribe.notice.book_full"));
			return;
		}
		this.history.record(this.lastState, false);
		List<String> before = List.copyOf(this.pages);
		int at = Math.clamp(index, 0, this.pages.size());
		this.pages.add(at, "");
		this.hardBefore(at + 1);
		this.marksFollow(before, at);
		this.displayPage(at, 0, 0);
		this.afterChange();
	}

	private void deletePage(int index) {
		this.history.record(this.lastState, false);
		List<String> before = List.copyOf(this.pages);
		if (this.pages.size() <= 1) {
			this.pages.set(0, "");
			this.marksFollow(before, 0, BookmarkShift.PAGE_START, Set.of(0));
		} else {
			int removed = Math.clamp(index, 0, this.pages.size() - 1);
			this.pages.remove(removed);
			this.hardBefore(removed);
			this.marksFollow(before, removed, BookmarkShift.PAGE_START, Set.of(removed));
		}
		this.openPage(Math.min(index, this.pages.size() - 1), Integer.MAX_VALUE);
		this.afterChange();
		this.notice(Component.translatable("scribe.notice.page_deleted", Keys.ctrlName() + "+Z"));
	}

	private void splitPage() {
		PageEditor editor = this.focusedOrCurrent();
		if (editor == null) {
			return;
		}
		if (this.pages.size() >= BookLimits.MAX_PAGES) {
			this.notice(Component.translatable("scribe.notice.book_full"));
			return;
		}
		int index = editor.pageIndex();
		String value = editor.getValue();
		int caret = editor.field().cursor();
		String head = value.substring(0, caret);
		String codes = Alignment.carried(value, caret) + FormatCodes.stateAt(value, caret).codes();
		String tail = codes + value.substring(caret);
		this.history.record(this.lastState, false);
		List<String> before = List.copyOf(this.pages);
		this.pages.set(index, PageBreaks.page(PageBreaks.markOf(this.pages.get(index)), head));
		this.pages.add(index + 1, tail);
		this.marksFollow(before, index, caret, Set.of());
		this.displayPage(index + 1, codes.length(), codes.length());
		this.afterChange();
	}

	private void openPageMenu(int slot, int anchorX, int anchorY, boolean beside) {
		int index = this.slotPage(slot);
		String ctrl = Keys.ctrlName();
		boolean hasText = !PageBreaks.blank(this.pages.get(index));
		List<MenuPopup.Item> items = List.of(
			new MenuPopup.Item(Component.translatable("scribe.page.insert_before"), null, this.pages.size() < BookLimits.MAX_PAGES,
				Component.translatable("scribe.page.insert_before.tooltip"), () -> this.insertPageAt(index)),
			new MenuPopup.Item(Component.translatable("scribe.page.split"), ctrl + "+Enter", true, Component.translatable("scribe.page.split.tooltip"), () -> {
				this.focusPage(index);
				this.splitPage();
			}),
			new MenuPopup.Item(Component.translatable("scribe.page.tidy"), null, index < this.pages.size() - 1, Component.translatable("scribe.page.tidy.tooltip"),
				() -> this.tidyFrom(index)),
			new MenuPopup.Item(Component.translatable("scribe.page.move_earlier"), null, index > 0, Component.translatable("scribe.page.move_earlier.tooltip"),
				() -> this.movePage(index, index - 1)),
			new MenuPopup.Item(Component.translatable("scribe.page.move_later"), null, index < trimmed(this.pages).size() - 1,
				Component.translatable("scribe.page.move_later.tooltip"), () -> this.movePage(index, index + 1)),
			new MenuPopup.Item(Component.translatable("scribe.page.copy"), null, hasText, Component.translatable("scribe.page.copy.tooltip"), () -> this.copyPage(index)),
			new MenuPopup.Item(Component.translatable("scribe.page.paste"), null, true, Component.translatable("scribe.page.paste.tooltip"), () -> this.pastePage(index)),
			new MenuPopup.Item(Component.translatable("scribe.page.clear"), null, hasText, Component.translatable("scribe.page.clear.tooltip"), () -> this.clearPage(index)),
			new MenuPopup.Item(Component.translatable("scribe.page.unformat"), null, hasText, Component.translatable("scribe.page.unformat.tooltip"),
				() -> this.unformatPage(index)));
		MenuPopup menu = new MenuPopup(this.font, items);
		if (beside) {
			this.openPopup(menu, anchorX, anchorY);
		} else {
			this.openPopup(menu, anchorX - menu.width() + 30, anchorY - menu.height() - 2);
		}
	}

	private void focusPage(int index) {
		if (index != this.page) {
			this.openPage(index, Integer.MAX_VALUE);
		}
	}

	private void tidyFrom(int index) {
		List<String> tidy = TidyPages.tidy(this.pages, index, this.fitter);
		if (tidy.equals(this.pages)) {
			this.notice(Component.translatable("scribe.notice.tidy_nothing"));
			return;
		}
		int removed = this.pages.size() - tidy.size();
		this.replacePages(tidy, index, index);
		this.notice(Component.translatable("scribe.notice.tidied", Math.max(0, removed)));
	}

	private void movePage(int from, int to) {
		if (from == to || from < 0 || to < 0 || from >= this.pages.size() || to >= this.pages.size()) {
			return;
		}
		this.syncMarks();
		this.history.record(this.lastState, false);
		List<BookmarkStore.Bookmark> marks = this.markList;
		List<String> moved = PageMoves.move(this.pages, from, to);
		this.pages.clear();
		this.pages.addAll(moved);
		if (!marks.isEmpty()) {
			List<BookmarkStore.Bookmark> following = new ArrayList<>();
			for (BookmarkStore.Bookmark mark : marks) {
				following.add(new BookmarkStore.Bookmark(PageMoves.newIndex(mark.page(), from, to), mark.color(), mark.name()));
			}
			this.setMarks(BookmarkShift.anchor(following, this.pages));
		}
		this.displayPage(to, 0, 0);
		this.afterChange();
		this.notice(Component.translatable("scribe.notice.page_moved", from + 1, to + 1));
	}

	private void copyPage(int index) {
		this.minecraft.keyboardHandler.setClipboard(PageBreaks.text(this.pages.get(index)));
		this.notice(Component.translatable("scribe.notice.page_copied", index + 1));
	}

	private void pastePage(int index) {
		String clip = fromClipboard(this.minecraft.keyboardHandler.getClipboard(), true);
		if (clip.isEmpty()) {
			this.notice(Component.translatable("scribe.notice.clipboard_empty"));
			return;
		}
		boolean empty = PageBreaks.blank(this.pages.get(index));
		int at = empty ? index : index + 1;
		if (!empty && this.pages.size() >= BookLimits.MAX_PAGES) {
			this.notice(Component.translatable("scribe.notice.book_full"));
			return;
		}
		List<String> work = new ArrayList<>(this.pages);
		if (empty) {
			work.set(at, "");
		} else {
			work.add(at, "");
		}
		PageFlow.Result result = PageFlow.reflow(work, at, clip, clip.length(), this.fitter, false, BookLimits.MAX_PAGES);
		if (result == null) {
			this.notice(Component.translatable("scribe.notice.book_full"));
			return;
		}
		int after = at + 1 + result.pages().size() - work.size();
		this.replacePages(PageBreaks.hardBefore(result.pages(), after), at, at);
	}

	private void clearPage(int index) {
		this.history.record(this.lastState, false);
		List<String> before = List.copyOf(this.pages);
		this.pages.set(index, "");
		this.hardBefore(index + 1);
		this.marksFollow(before, index);
		this.displayPage(index, 0, 0);
		this.afterChange();
	}

	private void unformatPage(int index) {
		String text = PageBreaks.text(this.pages.get(index));
		this.placeText(index, FormatCodes.clear(text, 0, text.length()).text(), 0, 0);
	}

	private void copyBook() {
		List<String> out = trimmed(this.pages);
		this.minecraft.keyboardHandler.setClipboard(String.join("\n---\n", out));
		this.notice(Component.translatable("scribe.notice.book_copied", out.size()));
	}

	private void pasteBook() {
		String clip = cleanPastedText(this.minecraft.keyboardHandler.getClipboard(), true);
		if (clip.isBlank()) {
			this.notice(Component.translatable("scribe.notice.clipboard_empty"));
			return;
		}
		PageFitter.Pagination pagination = this.fitter.paginate(clip, BookLimits.MAX_PAGES);
		this.replacePages(pagination.marked(), 0, WHOLE_BOOK);
		this.notice(pagination.truncated()
			? Component.translatable("scribe.notice.pasted_truncated", pagination.pages().size())
			: Component.translatable("scribe.notice.book_pasted", pagination.pages().size()));
	}

	private void clearBook() {
		this.replacePages(List.of(""), 0, WHOLE_BOOK);
		this.notice(Component.translatable("scribe.notice.book_cleared", Keys.ctrlName() + "+Z"));
	}

	private void undo() {
		BookHistory.Snapshot previous = this.history.undo(this.snapshot());
		if (previous != null) {
			this.restore(previous);
		}
	}

	private void redo() {
		BookHistory.Snapshot next = this.history.redo(this.snapshot());
		if (next != null) {
			this.restore(next);
		}
	}

	private void restore(BookHistory.Snapshot state) {
		this.pages.clear();
		this.pages.addAll(state.pages());
		if (this.pages.isEmpty()) {
			this.pages.add("");
		}
		this.setMarks(state.anchored());
		this.displayPage(state.page(), state.anchor(), state.cursor());
		this.lastState = this.snapshot();
		this.chaptersChanged();
		this.updateButtons();
	}

	@Override
	public boolean writing() {
		return true;
	}

	@Override
	protected BookmarkStore.Marks marks() {
		return this.editorMarks;
	}

	@Override
	protected int edgeTabTop(boolean rightSide) {
		if (!rightSide && !this.indexOpen && this.contentsRemove != null && this.contentsRemove.visible) {
			return this.geo.writingEdgeTabTopUnder(this.contentsRemove.getY() + BookGeometry.CELL_HEIGHT);
		}
		return this.geo.writingEdgeTabTop(rightSide);
	}

	private String contentsHeading() {
		return Component.translatable("scribe.contents.heading").getString();
	}

	private void printContents() {
		boolean had = ContentsPage.has(this.pages);
		List<String> printed = ContentsPage.print(trimmed(this.pages), this.font, this.contentsHeading());
		int at = 0;
		for (int i = 0; i < printed.size(); i++) {
			if (Chapters.isContentsPage(printed.get(i))) {
				at = i;
				break;
			}
		}
		this.setIndexOpen(false);
		this.replacePages(printed, at, BookmarkShift.ANYWHERE);
		this.notice(Component.translatable(had ? "scribe.notice.contents_updated" : "scribe.notice.contents_printed"));
	}

	private void removeContents() {
		this.setIndexOpen(false);
		Set<Integer> removed = new HashSet<>();
		for (int i = 0; i < this.pages.size(); i++) {
			if (Chapters.isContentsPage(this.pages.get(i))) {
				removed.add(i);
			}
		}
		this.replacePages(ContentsPage.remove(this.pages), Math.min(this.page, this.pages.size() - 1), BookmarkShift.ANYWHERE, removed);
		this.notice(Component.translatable("scribe.notice.contents_removed", Keys.ctrlName() + "+Z"));
	}

	private void refreshContents() {
		if (!ContentsPage.has(this.pages)) {
			return;
		}
		List<String> before = trimmed(this.pages);
		List<String> refreshed = ContentsPage.refresh(before, this.font, this.contentsHeading());
		if (!refreshed.equals(before)) {
			this.marks = this.marks.stream().filter(a -> a.page() < before.size()).toList();
			this.pages.clear();
			this.pages.addAll(refreshed);
			this.marksFollow(before, BookmarkShift.ANYWHERE);
			this.chaptersChanged();
			if (this.geo != null && !this.editors.isEmpty()) {
				this.rememberCaret();
				this.displayPage(Math.min(this.page, this.pages.size() - 1), Math.max(0, this.pendingAnchor), Math.max(0, this.pendingCaret));
			}
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		this.swallowText = false;
		if (this.popupTakesInput() || this.jumping()) {
			return super.keyPressed(event);
		}
		this.trackAltPress(event);
		if (Keys.isEscape(event) && this.popup() != null) {
			this.closePopup();
			return true;
		}
		if (Keys.isEscape(event)) {
			if (this.indexOpen) {
				this.setIndexOpen(false);
			} else {
				this.onEscape();
			}
			return true;
		}
		boolean ctrl = Keys.ctrl(event);
		TextKeys.Shortcut shortcut = TextKeys.shortcut(event);
		if (this.onCover) {
			return this.coverKey(event, shortcut);
		}
		if (shortcut == TextKeys.Shortcut.UNDO || shortcut == TextKeys.Shortcut.REDO || shortcut == TextKeys.Shortcut.SAVE) {
			switch (shortcut) {
				case UNDO -> this.undo();
				case REDO -> this.redo();
				default -> this.save(true);
			}
			return true;
		}
		if (Keys.isPageUp(event)) {
			if (ctrl) {
				this.turnTo(0);
			} else if (this.turnPages(-1)) {
				this.playPageTurn();
			}
			return true;
		}
		if (Keys.isPageDown(event)) {
			if (ctrl) {
				this.turnTo(this.pages.size() - 1);
			} else if (this.turnPages(1)) {
				this.playPageTurn();
			}
			return true;
		}

		if (Keys.alt(event) && !ctrl && (Keys.isUp(event) || Keys.isDown(event))) {
			return super.keyPressed(event);
		}
		PageEditor editor = this.focusedEditor();
		if (editor != null && !this.indexOpen) {
			if (this.runShortcut(shortcut)) {
				this.swallowText = TextKeys.mayTypeText(event);
				return true;
			}
			if (event.isPaste()) {
				this.paste(editor);
				return true;
			}
			if (this.crossPageKey(event, editor)) {
				return true;
			}
			if (Keys.isEnter(event) && !ctrl) {
				this.typeText(editor, this.newParagraph(editor.field()));
				return true;
			}
			if (editor.keyPressed(event)) {
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private boolean runShortcut(TextKeys.Shortcut shortcut) {
		switch (shortcut) {
			case BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, MAGIC -> this.applyStyle(shortcut.code());
			case CLEAR -> this.resetFormatting();
			case ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT -> this.align(shortcut.alignment());
			case CHAPTER -> this.toggleChapter();
			case SPLIT_PAGE -> this.splitPage();
			default -> {
				return false;
			}
		}
		return true;
	}

	private boolean crossPageKey(KeyEvent event, PageEditor editor) {
		if (Keys.ctrl(event) || Keys.shift(event) || Keys.alt(event)) {
			return false;
		}
		PageTextField field = editor.field();
		if (field.hasSelection()) {
			return false;
		}
		int index = editor.pageIndex();
		boolean atStart = field.caretAtVisualStart();
		boolean hasPrevious = index > 0;
		boolean hasNext = index < this.pages.size() - 1;
		if (Keys.isLeft(event) && atStart && hasPrevious) {
			this.moveTo(index - 1, Integer.MAX_VALUE);
			return true;
		}
		if (Keys.isRight(event) && field.caretAtVisualEnd() && hasNext) {
			this.moveTo(index + 1, 0);
			return true;
		}
		if (Keys.isUp(event) && field.caretOnFirstLine() && hasPrevious) {
			this.moveTo(index - 1, Integer.MAX_VALUE);
			return true;
		}
		if (Keys.isDown(event) && field.caretOnLastLine() && hasNext) {
			this.moveTo(index + 1, 0);
			return true;
		}
		if (Keys.isDelete(event) && field.caretAtVisualEnd() && hasNext) {
			this.deleteAcross(BreakDelete.delete(this.pages, index, field.cursor(), this.fitter, this.flowing(), BookLimits.MAX_PAGES));
			return true;
		}
		if (Keys.isBackspace(event) && atStart && hasPrevious) {
			this.deleteAcross(BreakDelete.backspace(this.pages, index, field.cursor(), this.fitter, this.flowing(), BookLimits.MAX_PAGES));
			return true;
		}
		return false;
	}

	private void deleteAcross(BreakDelete.Outcome outcome) {
		switch (outcome.kind()) {
			case NOTHING -> {
			}
			case FULL -> this.notice(Component.translatable("scribe.notice.book_full"));
			case MOVE -> this.moveTo(outcome.caretPage(), outcome.caretPos() < 0 ? Integer.MAX_VALUE : 0);
			case EDIT -> {
				this.history.record(this.lastState, false);
				List<String> before = List.copyOf(this.pages);
				this.pages.clear();
				this.pages.addAll(outcome.pages());
				this.marksFollow(before, outcome.editPage(), outcome.editPos(), outcome.removed() < 0 ? Set.of() : Set.of(outcome.removed()));
				if (outcome.caretPos() < 0) {
					this.openPage(outcome.caretPage(), Integer.MAX_VALUE);
				} else {
					this.displayPage(outcome.caretPage(), outcome.caretPos(), outcome.caretPos());
				}
				this.afterChange();
			}
		}
	}

	private void moveTo(int index, int caret) {
		this.history.breakGroup();
		this.openPage(index, caret);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		this.altArmed = false;
		if (this.swallowText) {
			return true;
		}
		if (this.popupTakesInput() || this.jumping()) {
			return super.charTyped(event);
		}
		PageEditor editor = this.focusedEditor();
		if (editor == null || this.indexOpen) {
			return super.charTyped(event);
		}
		if (event.codepoint() == FormatCodes.SECTION) {
			this.insertRaw(editor, String.valueOf(FormatCodes.SECTION));
			return true;
		}
		if (!event.isAllowedChatCharacter()) {
			return false;
		}
		this.typeText(editor, event.codepointAsString());
		return true;
	}

	private void trackAltPress(KeyEvent event) {
		this.altArmed = this.isSectionKey(event) && !Keys.otherModifierHeld(event);
	}

	private boolean isSectionKey(KeyEvent event) {
		return switch (ScribeConfig.get().sectionSignKeyShown()) {
			case LEFT_ALT -> Keys.isLeftAlt(event);
			case RIGHT_ALT -> Keys.isRightAlt(event);
			case OFF -> false;
		};
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		this.swallowText = false;
		if (this.altArmed && this.isSectionKey(event)) {
			this.altArmed = false;
			PageEditor editor = this.focusedEditor();
			if (editor != null && !this.popupTakesInput()) {
				this.insertRaw(editor, String.valueOf(FormatCodes.SECTION));
				return true;
			}
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		this.altArmed = false;
		if (this.onCover && this.popup() == null) {
			if (Keys.isLeftClick(event.button()) && QuillCover.over(this.geo, event.x(), event.y())) {
				this.openFromCover();
				return true;
			}
			return super.mouseClicked(event, doubleClick);
		}
		Popup open = this.popup();
		if ((open == null || (!open.modal() && !open.contains(event.x(), event.y()))) && !this.jumping() && this.clickBookmarks(event)) {
			if (open != null && this.popup() == open) {
				open.clickedOutside();
			}
			this.followCaretWithSymbols();
			return true;
		}
		boolean handled = super.mouseClicked(event, doubleClick);
		if (this.minecraft.gui.screen() == this && !this.popupTakesInput() && !this.jumping()) {
			this.refocusPage();
		}
		this.followCaretWithSymbols();
		return handled;
	}

	@Override
	public void closePopup() {
		super.closePopup();
		if (this.minecraft != null && this.minecraft.gui.screen() == this && !this.jumping()) {
			this.refocusPage();
		}
	}

	private void refocusPage() {
		if (this.indexOpen || this.onCover || this.getFocused() instanceof PageEditor) {
			return;
		}
		PageEditor editor = this.editorFor(this.page);
		if (editor != null && this.children().contains(editor)) {
			this.setFocused(editor);
			this.setDragging(false);
		}
	}

	private void paste(PageEditor editor) {
		String cleaned = fromClipboard(this.minecraft.keyboardHandler.getClipboard(), ScribeConfig.get().pasteFormattingOn());
		if (!cleaned.isEmpty() || editor.field().hasSelection()) {
			this.insertRaw(editor, cleaned);
		}
	}

	static String fromClipboard(String text, boolean keepCodes) {
		return cleanPastedText(PageCodec.decodeText(text.replace("\r\n", "\n").replace('\r', '\n')), keepCodes);
	}

	static String cleanPastedText(String text, boolean keepCodes) {
		String normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
		StringBuilder out = new StringBuilder(normalized.length());
		for (int i = 0; i < normalized.length(); i++) {
			char c = normalized.charAt(i);
			if (c == FormatCodes.SECTION) {
				if (keepCodes && FormatCodes.isCodeAt(normalized, i)) {
					out.append(c).append(Character.toLowerCase(normalized.charAt(i + 1)));
					i++;
				} else if (!keepCodes && FormatCodes.isCodeAt(normalized, i)) {
					i++;
				}
			} else if (c == '\n' || StringUtil.isAllowedChatCharacter(c)) {
				out.append(c);
			}
		}
		return out.toString();
	}

	@Override
	protected List<List<HelpPopup.Line>> helpGroups() {
		return ShortcutsHelp.editor(Keys.mac(), Keys.clearFormattingKeys(), ScribeConfig.get().fastScrollPages, BookScreens.shiftHelp(this.target.onLectern()));
	}

	private void onEscape() {
		if (!this.isDirty()) {
			this.finishWithoutSaving();
			return;
		}
		switch (ScribeConfig.get().escapeAction) {
			case SAVE -> this.saveAndClose();
			case DISCARD -> this.finishWithoutSaving();
			case ASK -> this.openChild(new UnsavedChangesScreen(this));
		}
	}

	public void saveAndClose() {
		boolean changed = this.isDirty();
		if (changed) {
			this.save(false);
		}
		this.finish();
		if (changed && ScribeConfig.get().savedMessageShown() && this.minecraft.player != null) {
			this.minecraft.player.sendOverlayMessage(Component.translatable("scribe.notice.saved").withStyle(ChatFormatting.GREEN));
		}
	}

	public void finishWithoutSaving() {
		this.finish();
	}

	private void finish() {
		this.finished = true;
		this.rememberPage();
		this.keepMarks();
		this.stopReading();
		this.target.closed();
		this.minecraft.gui.setScreen(null);
	}

	private void takeBook() {
		if (this.isDirty()) {
			this.save(false);
		}
		this.finished = true;
		this.rememberPage();
		this.keepMarks();
		this.target.takeBook();
		this.target.closed();
		this.minecraft.gui.setScreen(null);
	}

	public void signAndClose(String title, Payloads.SignDetails details) {
		this.refreshContents();
		List<String> out = this.savedForm();
		this.target.sign(out, title.trim(), details);
		String author = details.hasPenName() && BookNet.penNames() ? details.penName() : this.target.playerName();
		Backups.save(this.memoryKey, BookFile.of(title.trim(), author, out));
		BookDetails.get().remove(this.memoryKey);
		BookmarkStore.get().move(this.memoryKey, PageMemory.keyForPages(out), BookmarkShift.within(this.markList, out.size()));
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.0F, 0.6F));
		this.finished = true;
		this.rememberPage();
		this.stopReading();
		if (!this.target.onLectern()) {
			this.minecraft.gui.setScreen(null);
		} else {
			this.minecraft.gui.setScreen(null);
			this.target.closed();
		}
	}

	private void save(boolean showNotice) {
		this.refreshContents();
		List<String> out = this.savedForm();
		this.target.save(out);
		Backups.save(this.memoryKey, BookFile.of(null, this.target.playerName(), out));
		String newKey = PageMemory.keyForPages(out);
		PageMemory.get().move(this.memoryKey, newKey, this.page);
		BookDetails.get().move(this.memoryKey, newKey);
		this.savedMarks = BookmarkShift.within(this.markList, out.size());
		BookmarkStore.get().move(this.memoryKey, newKey, this.savedMarks);
		this.memoryKey = newKey;
		this.savedPages = out;
		if (this.target.slot() >= 0) {
			BookNet.sendBookmark(this.target.slot(), this.page);
		}
		if (showNotice) {
			this.notice(Component.translatable("scribe.notice.saved"));
		}
	}

	private void rememberPage() {
		PageMemory memory = PageMemory.get();
		if (QuillCover.remembers(this.savedPages)) {
			if (this.onCover) {
				memory.putCover(this.memoryKey);
			} else {
				memory.put(this.memoryKey, this.page);
			}
		}
		memory.save();
	}

	private void keepMarks() {
		this.syncMarks();
		int pageCount = trimmed(this.pages).size();
		List<BookmarkShift.Anchored> marks = this.marks.stream().filter(a -> a.page() < pageCount).toList();
		List<BookmarkStore.Bookmark> kept = BookmarkShift.within(BookmarkShift.marks(
			BookmarkShift.follow(marks, this.pages, PageCodec.decodeBook(this.savedPages), BookmarkShift.ANYWHERE, BookmarkShift.PAGE_START, Set.of())),
			this.savedPages.size());
		if (!kept.equals(this.savedMarks)) {
			BookmarkStore.get().replace(this.memoryKey, kept);
			this.savedMarks = kept;
		}
	}

	@Override
	protected boolean holdsBookOpen() {
		return this.readingSent && this.target.hand() != null;
	}

	@Override
	protected void turnedAtLectern(int leaves) {
		if (this.target instanceof EditTarget.Lectern lectern) {
			BookNet.lecternLeaves(lectern.menu().containerId, leaves);
		}
	}

	private void stopReading() {
		InteractionHand hand = this.target.hand();
		if (this.readingSent && hand != null) {
			this.readingSent = false;
			BookNet.sendReading(false, hand);
		}
	}

	@Override
	public void removed() {
		super.removed();
		if (this.openingChild) {
			this.openingChild = false;
			return;
		}
		if (this.lecternListener != null && this.target instanceof EditTarget.Lectern lectern) {
			lectern.menu().removeSlotListener(this.lecternListener);
			this.lecternListener = null;
		}
		if (!this.finished) {
			this.saveOnForcedClose();
		}
	}

	private void saveOnForcedClose() {
		this.finished = true;
		boolean dirty = this.isDirty();
		if (this.lecternBookLost()) {
			if (dirty) {
				this.backUp();
			}
			this.tellLecternChanged(dirty);
		} else if (dirty) {
			this.save(false);
		}
		this.rememberPage();
		this.keepMarks();
		this.stopReading();
	}

	private void backUp() {
		this.refreshContents();
		Backups.save(this.memoryKey, BookFile.of(null, this.target.playerName(), this.savedForm()));
	}

	private boolean lecternBookLost() {
		return this.target.onLectern() && !this.target.book().has(DataComponents.WRITABLE_BOOK_CONTENT);
	}

	private void lecternBookChanged() {
		if (this.finished || this.minecraft.gui.screen() != this || !this.lecternBookLost()) {
			return;
		}
		this.finished = true;
		boolean dirty = this.isDirty();
		if (dirty) {
			this.backUp();
		}
		this.tellLecternChanged(dirty);
		this.rememberPage();
		this.keepMarks();
		this.target.closed();
		this.minecraft.gui.setScreen(null);
	}

	private void tellLecternChanged(boolean dirty) {
		if (this.minecraft.player == null) {
			return;
		}
		boolean backedUp = dirty && ScribeConfig.get().backupsOn();
		this.minecraft.player.sendOverlayMessage(Component.translatable(backedUp ? "scribe.notice.lectern_changed_backup" : "scribe.notice.lectern_changed"));
	}

	public void autosaveFromChild() {
		if (!this.finished) {
			this.saveOnForcedClose();
			this.target.closed();
		}
	}

	@Override
	public void onClose() {
		this.onEscape();
	}

	public void openChild(Screen child) {
		this.closePopup();
		this.rememberCaret();
		this.openingChild = true;
		this.minecraft.gui.setScreen(child);
	}

	private void openSignScreen() {
		this.openChild(new SignBookScreen(this));
	}

	@Override
	protected void openSettings() {
		this.openChild(new SettingsScreen(this));
	}

	private void openImport() {
		this.openChild(BookFilesScreen.forImport(this));
	}

	private void openExport() {
		this.openChild(BookFilesScreen.forExport(this, BookFile.of(this.signTitle.isBlank() ? null : this.signTitle, this.target.playerName(), this.savedForm())));
	}

	public List<String> pagesForSigning() {
		return this.savedForm();
	}

	public int chapterCount() {
		return this.chapters().size();
	}

	public String signTitle() {
		return this.signTitle;
	}

	public String signSubtitle() {
		return this.signSubtitle;
	}

	public String signPenName() {
		return this.signPenName;
	}

	public void rememberSignFields(String title, String subtitle, String penName) {
		this.signTitle = title;
		this.signSubtitle = subtitle;
		this.signPenName = penName;
		this.coverView = null;
	}

	public @Nullable CoverChoices signChoices() {
		return this.signChoices;
	}

	public void rememberSignChoices(CoverChoices choices) {
		this.signChoices = choices;
	}

	public void saveSignFields(String title, String subtitle, String penName, boolean design) {
		this.rememberSignFields(title, subtitle, penName);
		BookDetails.Details kept = new BookDetails.Details(title.strip(), subtitle.strip(), penName.strip());
		SavedDetails onBook = QuillCover.onBook(kept);
		boolean onTheBook = this.target.saveDetails(onBook);
		QuillCover.keepLocally(BookDetails.get(), this.memoryKey, kept, onTheBook);
		BookDetails.Details shown = onTheBook ? QuillCover.details(onBook.taken(BookNet.subtitles(), BookNet.penNames())) : kept;
		this.savedTitle = shown.title();
		this.savedSubtitle = shown.subtitle();
		this.savedPenName = shown.penName();
		this.notice(Component.translatable(design ? "scribe.notice.details_design_saved" : "scribe.notice.details_saved"));
	}

	public String savedTitle() {
		return this.savedTitle;
	}

	public String savedSubtitle() {
		return this.savedSubtitle;
	}

	public String savedPenName() {
		return this.savedPenName;
	}

	public boolean hasText() {
		return QuillCover.hasText(this.pages);
	}

	public boolean saveDesign(Payloads.SignDetails details) {
		boolean kept = this.target.saveDesign(details);
		this.showBinding();
		return kept;
	}

	private void showBinding() {
		ItemStack book = this.target.book();
		this.material = Bookbinding.materialName(Binding.of(book));
		this.corner = BookStyle.corner(book);
		this.coverView = null;
	}

	public int cover() {
		return this.cover;
	}

	public void importPages(List<String> imported, boolean replace) {
		List<String> safe = new ArrayList<>(PageCodec.decodeBook(BookFiles.fitToBook(imported, this.fitter == null ? new PageFitter(this.font) : this.fitter)));
		safe.set(0, PageBreaks.text(safe.getFirst()));
		List<String> work = new ArrayList<>();
		int focus;
		if (replace) {
			work.addAll(safe);
			focus = 0;
		} else {
			work.addAll(this.pages);
			int at = Math.min(this.page + 1, work.size());
			if (!work.isEmpty() && PageBreaks.blank(work.get(this.page))) {
				work.remove(this.page);
				at = this.page;
			}
			work.addAll(at, safe);
			focus = at;
			int after = at + safe.size();
			if (after < work.size()) {
				work.set(after, PageBreaks.text(work.get(after)));
			}
		}
		int dropped = Math.max(0, work.size() - BookLimits.MAX_PAGES);
		while (work.size() > BookLimits.MAX_PAGES) {
			work.removeLast();
		}
		if (this.lastState == null) {
			this.lastState = this.snapshot();
		}
		this.history.record(this.lastState, false);
		List<String> before = List.copyOf(this.pages);
		this.pages.clear();
		this.pages.addAll(work.isEmpty() ? List.of("") : work);
		this.marksFollow(before, replace ? WHOLE_BOOK : focus);
		this.pendingAnchor = 0;
		this.pendingCaret = 0;
		this.page = Math.min(focus, this.pages.size() - 1);
		this.lastState = null;
		this.chaptersChanged();
		if (dropped > 0) {
			this.notice(Component.translatable("scribe.notice.import_truncated", dropped));
		}
	}

	public void setSignTitle(String title) {
		this.signTitle = title;
		this.coverView = null;
	}

	@Override
	public void onFilesDrop(List<Path> files) {
		if (this.onCover && !files.isEmpty()) {
			this.showPage(this.page);
		}
		for (Path file : files) {
			try {
				BookFile imported = BookFiles.read(file, this.fitter);
				int before = this.pages.size();
				this.importPages(imported.pages(), false);
				this.openPage(this.page, 0);
				this.afterChange();
				this.notice(Component.translatable("scribe.notice.imported", file.getFileName().toString(), Math.max(0, this.pages.size() - before)));
			} catch (Exception e) {
				this.notice(Component.translatable("scribe.notice.import_failed", file.getFileName().toString()));
				Scribe.LOGGER.warn("Could not import dropped file {}", file, e);
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return !this.target.onLectern();
	}
}
