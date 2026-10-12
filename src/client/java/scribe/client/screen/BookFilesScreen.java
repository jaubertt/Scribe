package scribe.client.screen;

import scribe.Scribe;
import scribe.client.io.BookFile;
import scribe.client.io.BookFiles;
import scribe.client.io.FileDialogs;
import scribe.client.io.PageExport;
import scribe.client.text.PageFitter;
import java.io.IOException;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class BookFilesScreen extends Screen {
	private static final int ROW_HEIGHT = 20;
	private static final int ROW_GAP = 2;
	private static final int LIST_WIDTH = 280;

	private final Screen parent;
	private final boolean exporting;
	private final @Nullable BookFile toExport;
	private boolean leavingToParent;

	private EditBox nameBox;
	private String fileName = "";
	private BookFiles.Format format = BookFiles.Format.JSON;

	private EditBox searchBox;
	private String search = "";
	private List<BookFiles.Entry> all = List.of();
	private List<BookFiles.Entry> entries = List.of();
	private int scroll;
	private final WheelSteps wheel = new WheelSteps();
	private int selected = -1;
	private int armedDelete = -1;
	private @Nullable Component status;

	private BookFilesScreen(Screen parent, boolean exporting, @Nullable BookFile toExport) {
		super(Component.translatable(exporting ? "scribe.files.export_title" : "scribe.files.import_title"));
		this.parent = parent;
		this.exporting = exporting;
		this.toExport = toExport;
		if (exporting && toExport != null) {
			String suggestion = toExport.title().orElseGet(() -> firstWords(toExport.pages()));
			this.fileName = BookFiles.freeName(BookFiles.fileName(suggestion));
		} else {
			this.reload();
		}
	}

	public static BookFilesScreen forExport(Screen parent, BookFile book) {
		return new BookFilesScreen(parent, true, book);
	}

	public static BookFilesScreen forImport(BookEditorScreen editor) {
		return new BookFilesScreen(editor, false, null);
	}

	private static String firstWords(List<String> pages) {
		for (String page : pages) {
			String plain = page.replaceAll("§.", "").strip();
			if (!plain.isEmpty()) {
				String[] words = plain.split("\\s+");
				return String.join(" ", java.util.Arrays.copyOf(words, Math.min(4, words.length)));
			}
		}
		return "book";
	}

	private void reload() {
		this.all = BookFiles.list(true);
		this.filter();
	}

	private void filter() {
		String query = this.search.toLowerCase(Locale.ROOT).strip();
		List<BookFiles.Entry> out = new ArrayList<>();
		for (BookFiles.Entry entry : this.all) {
			if (query.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(query)) {
				out.add(entry);
			}
		}
		this.entries = out;
		this.selected = -1;
		this.armedDelete = -1;
	}

	@Override
	protected void init() {
		int center = this.width / 2;
		if (this.exporting) {
			int y = this.height / 2 - 30;
			this.nameBox = this.addRenderableWidget(new EditBox(this.font, center - 150, y, 300, 20, Component.translatable("scribe.files.name")));
			this.nameBox.setMaxLength(80);
			this.nameBox.setValue(this.fileName);
			this.nameBox.setResponder(value -> this.fileName = value);
			this.addRenderableWidget(CycleButton.<BookFiles.Format>builder(value -> Component.translatable(value.key()), this.format)
				.withValues(BookFiles.Format.values())
				.create(center - 150, y + 26, 300, 20, Component.translatable("scribe.files.format"), (button, value) -> this.formatChanged(value)));
			int buttonsY = y + 56;
			this.addRenderableWidget(Button.builder(Component.translatable("scribe.files.save"), b -> this.export()).bounds(center - 150, buttonsY, 72, 20).build());
			this.addRenderableWidget(Button.builder(Component.translatable("scribe.files.browse"), b -> this.browseExport()).bounds(center - 74, buttonsY, 72, 20).build());
			this.addRenderableWidget(Button.builder(Component.translatable("scribe.files.open_folder"), b -> this.openFolder()).bounds(center + 2, buttonsY, 72, 20).build());
			this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> this.onClose()).bounds(center + 78, buttonsY, 72, 20).build());
			return;
		}

		boolean refocus = this.searchBox != null && this.searchBox.isFocused();
		if (this.searchBox == null) {
			this.searchBox = new EditBox(this.font, center - LIST_WIDTH / 2, 30, LIST_WIDTH, 18, Component.translatable("scribe.files.search"));
			this.searchBox.setHint(Component.translatable("scribe.files.search").withStyle(ChatFormatting.DARK_GRAY));
			this.searchBox.setValue(this.search);
			this.searchBox.setResponder(value -> {
				if (!value.equals(this.search)) {
					this.search = value;
					this.filter();
					this.scroll = 0;
					this.rebuildWidgets();
				}
			});
		} else {
			this.searchBox.setX(center - LIST_WIDTH / 2);
		}
		this.addRenderableWidget(this.searchBox);
		if (refocus) {
			this.setFocused(this.searchBox);
		}
		int top = 54;
		int bottomButtons = this.height - 28;
		int rows = Math.max(1, (bottomButtons - 20 - top) / (ROW_HEIGHT + ROW_GAP));
		this.scroll = Math.clamp(this.scroll, 0, Math.max(0, this.entries.size() - rows));
		int left = center - LIST_WIDTH / 2;
		for (int i = 0; i < rows && this.scroll + i < this.entries.size(); i++) {
			int index = this.scroll + i;
			BookFiles.Entry entry = this.entries.get(index);
			int y = top + i * (ROW_HEIGHT + ROW_GAP);
			this.addRenderableWidget(Button.builder(this.rowLabel(entry, index == this.selected), b -> {
				if (this.selected == index) {
					this.importSelected(false);
				} else {
					this.selected = index;
					this.armedDelete = -1;
					this.rebuildWidgets();
				}
			}).bounds(left, y, LIST_WIDTH - 24, ROW_HEIGHT).build());
			if (entry.source() != BookFiles.Source.BOOK_COPY) {
				boolean armed = this.armedDelete == index;
				Button delete = Button.builder(Component.literal(armed ? "?" : "✖").withStyle(armed ? ChatFormatting.RED : ChatFormatting.GRAY), b -> this.delete(index))
					.bounds(left + LIST_WIDTH - 22, y, 22, ROW_HEIGHT).build();
				delete.setTooltip(Tooltip.create(Component.translatable(armed ? "scribe.files.delete_confirm" : "scribe.files.delete")));
				this.addRenderableWidget(delete);
			}
		}
		Button insert = this.addRenderableWidget(Button.builder(Component.translatable("scribe.files.insert"), b -> this.importSelected(false))
			.bounds(center - 154, bottomButtons, 74, 20).build());
		Button replace = this.addRenderableWidget(Button.builder(Component.translatable("scribe.files.replace"), b -> this.importSelected(true))
			.bounds(center - 76, bottomButtons, 74, 20).build());
		insert.active = this.selected >= 0;
		replace.active = this.selected >= 0;
		this.addRenderableWidget(Button.builder(Component.translatable("scribe.files.browse"), b -> this.browseImport())
			.bounds(center + 2, bottomButtons, 74, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("scribe.files.open_folder"), b -> this.openFolder())
			.bounds(center + 80, bottomButtons, 74, 20).build());
	}

	private Component rowLabel(BookFiles.Entry entry, boolean isSelected) {
		String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(entry.modified()));
		Component source = Component.translatable("scribe.files.source." + entry.source().name().toLowerCase(Locale.ROOT));
		Component label = Component.literal(entry.name()).append(Component.literal("  ").append(source).append(" " + date).withStyle(ChatFormatting.GRAY));
		return isSelected ? Component.literal("> ").withStyle(ChatFormatting.YELLOW).append(label) : label;
	}

	private void delete(int index) {
		if (index < 0 || index >= this.entries.size()) {
			return;
		}
		if (this.armedDelete != index) {
			this.armedDelete = index;
			this.status = Component.translatable("scribe.files.delete_confirm").withStyle(ChatFormatting.YELLOW);
			this.rebuildWidgets();
			return;
		}
		BookFiles.Entry entry = this.entries.get(index);
		this.status = BookFiles.delete(entry.path())
			? Component.translatable("scribe.files.deleted", entry.name()).withStyle(ChatFormatting.GRAY)
			: Component.translatable("scribe.files.delete_failed", entry.name()).withStyle(ChatFormatting.RED);
		this.reload();
		this.rebuildWidgets();
	}

	private void formatChanged(BookFiles.Format value) {
		this.format = value;
		if (this.nameBox != null && !this.nameBox.getValue().isBlank()) {
			this.nameBox.setValue(value.rename(this.nameBox.getValue()));
		}
	}

	private void export() {
		if (this.toExport == null) {
			return;
		}
		String name = this.format.rename(BookFiles.fileName(this.fileName));
		try {
			this.writeExport(BookFiles.ensureBooksDir().resolve(name));
		} catch (IOException e) {
			Scribe.LOGGER.warn("Could not export book to {}", name, e);
			this.status = Component.translatable("scribe.notice.export_failed", name).withStyle(ChatFormatting.RED);
		}
	}

	private void writeExport(Path path) throws IOException {
		if (this.toExport == null) {
			return;
		}
		if (this.format.pictures()) {
			this.exportPictures(path);
			return;
		}
		BookFiles.write(path, this.toExport);
		this.backToParent();
		if (this.minecraft.player != null) {
			this.minecraft.player.sendOverlayMessage(Component.translatable("scribe.notice.exported", path.getFileName().toString()).withStyle(ChatFormatting.GREEN));
		}
	}

	private void exportPictures(Path path) {
		int cover = this.parent instanceof BookScreen book ? book.cover : BookStyle.COVER_UNDYED;
		boolean pdf = this.format == BookFiles.Format.PDF;
		int pages = this.toExport.pages().size();
		Minecraft minecraft = this.minecraft;
		PageExport.export(path, this.toExport, cover, pdf).whenCompleteAsync((written, error) -> {
			if (minecraft.player == null) {
				return;
			}
			if (error != null) {
				Scribe.LOGGER.warn("Could not save the pages to {}", path, error);
				minecraft.player.sendOverlayMessage(Component.translatable("scribe.notice.export_failed", path.getFileName().toString()).withStyle(ChatFormatting.RED));
			} else if (pdf) {
				minecraft.player.sendOverlayMessage(Component.translatable("scribe.notice.exported", written.getFileName().toString()).withStyle(ChatFormatting.GREEN));
			} else {
				minecraft.player.sendOverlayMessage(Component.translatable("scribe.notice.exported_pictures", pages, written.getFileName().toString())
					.withStyle(ChatFormatting.GREEN));
			}
		}, minecraft);
		this.backToParent();
	}

	private void browseExport() {
		String name = this.format.rename(BookFiles.fileName(this.fileName));
		Path suggested;
		try {
			suggested = BookFiles.ensureBooksDir().resolve(name);
		} catch (IOException e) {
			suggested = BookFiles.booksDir().resolve(name);
		}
		boolean shown = FileDialogs.save(suggested, path -> {
			try {
				this.writeExport(path);
			} catch (IOException e) {
				Scribe.LOGGER.warn("Could not export book to {}", path, e);
				this.status = Component.translatable("scribe.notice.export_failed", path.getFileName().toString()).withStyle(ChatFormatting.RED);
			}
		});
		if (!shown) {
			this.status = Component.translatable("scribe.files.browse_failed").withStyle(ChatFormatting.RED);
		}
	}

	private void browseImport() {
		Path start;
		try {
			start = BookFiles.ensureBooksDir();
		} catch (IOException e) {
			start = BookFiles.booksDir();
		}
		boolean shown = FileDialogs.open(start, path -> this.importFile(path, false));
		if (!shown) {
			this.status = Component.translatable("scribe.files.browse_failed").withStyle(ChatFormatting.RED);
		}
	}

	private void importSelected(boolean replace) {
		if (this.selected < 0 || this.selected >= this.entries.size()) {
			return;
		}
		this.importFile(this.entries.get(this.selected).path(), replace);
	}

	private void importFile(Path path, boolean replace) {
		if (!(this.parent instanceof BookEditorScreen editor)) {
			return;
		}
		try {
			BookFile book = BookFiles.read(path, new PageFitter(this.font));
			editor.importPages(book.pages(), replace);
			book.title().ifPresent(editor::setSignTitle);
			String me = editor.target().playerName();
			book.author().filter(author -> !author.isBlank() && !author.equalsIgnoreCase(me)).ifPresent(author -> {
				if (this.minecraft.player != null) {
					this.minecraft.player.sendSystemMessage(Component.translatable("scribe.notice.original_author", author).withStyle(ChatFormatting.GRAY));
				}
			});
			this.backToParent();
		} catch (IOException e) {
			Scribe.LOGGER.warn("Could not import {}", path, e);
			this.status = Component.translatable("scribe.notice.import_failed", path.getFileName().toString()).withStyle(ChatFormatting.RED);
		}
	}

	private void openFolder() {
		try {
			com.mojang.blaze3d.Blaze3D.openPath(BookFiles.ensureBooksDir());
		} catch (IOException e) {
			Scribe.LOGGER.warn("Could not open the books folder", e);
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (!this.exporting && scrollY != 0) {
			int steps = this.wheel.take(scrollY);
			if (steps != 0) {
				this.scroll = Math.max(0, this.scroll + steps);
				this.rebuildWidgets();
			}
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.exporting && this.nameBox != null && this.nameBox.isFocused() && event.isConfirmation()) {
			this.export();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void setInitialFocus() {
		if (this.searchBox != null) {
			this.setInitialFocus(this.searchBox);
		} else if (this.nameBox != null) {
			this.setInitialFocus(this.nameBox);
		} else {
			super.setInitialFocus();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, this.exporting ? this.height / 2 - 60 : 12, 0xFFFFFFFF);
		Component where = Component.translatable("scribe.files.folder_hint", "scribe/books").withStyle(ChatFormatting.GRAY);
		if (this.exporting) {
			graphics.centeredText(this.font, where, this.width / 2, this.height / 2 - 46, 0xFFA0A0A0);
		} else if (this.entries.isEmpty()) {
			Component empty = Component.translatable(this.all.isEmpty() ? "scribe.files.empty" : "scribe.files.no_match");
			graphics.centeredText(this.font, empty, this.width / 2, this.height / 2 - 10, 0xFFA0A0A0);
			graphics.centeredText(this.font, where, this.width / 2, this.height / 2 + 4, 0xFFA0A0A0);
		}
		if (this.status != null) {
			graphics.centeredText(this.font, this.status, this.width / 2, this.height - 42, 0xFFFFFFFF);
		}
	}

	private void backToParent() {
		this.leavingToParent = true;
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void onClose() {
		this.backToParent();
	}

	@Override
	public void removed() {
		super.removed();
		if (!this.leavingToParent) {
			SettingsScreen.closedByGame(this.parent);
		}
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}
}
