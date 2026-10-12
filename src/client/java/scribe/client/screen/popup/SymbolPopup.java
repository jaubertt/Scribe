package scribe.client.screen.popup;

import scribe.client.mixin.FontAccessor;
import scribe.client.screen.BookStyle;
import scribe.client.text.Symbols;
import scribe.client.widget.InlineField;
import scribe.config.ScribeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public class SymbolPopup extends Popup {
	public static final int FULL_COLUMNS = 9;
	public static final int FULL_ROWS = 6;
	public static final int MIN_COLUMNS = 4;
	public static final int MIN_ROWS = 3;
	public static final int MAX_RECENT = 12;
	private static final int CELL = 13;
	private static final int TAB = 14;
	private static final int TAB_MIN = 13;
	private static final int TABS_Y = 17;
	private static final int TAB_HEIGHT = 13;
	private static final int CLOSE = 11;
	private static final int TAB_IDLE = 0xFFE9DABC;
	private static final int TAB_IDLE_HOVER = 0xFFF4EAD3;

	private final Consumer<String> insert;
	private final InlineField search = new InlineField(30);
	private final IntPredicate hasGlyph;
	private final int columns;
	private final int rows;
	private final boolean full;
	private static int lastCategory;
	private static int lastScroll;

	private int category;
	private int scroll;
	private final scribe.client.screen.WheelSteps wheel = new scribe.client.screen.WheelSteps();
	private List<String> shown = List.of();

	public SymbolPopup(Font font, Consumer<String> insert) {
		this(font, insert, FULL_COLUMNS, FULL_ROWS);
	}

	public SymbolPopup(Font font, Consumer<String> insert, int columns, int rows) {
		this(font, insert, columns, rows, cp -> ((FontAccessor) font).scribe$glyphs(FontDescription.DEFAULT).getGlyph(cp).info() != SpecialGlyphs.MISSING);
	}

	SymbolPopup(Font font, Consumer<String> insert, int columns, int rows, IntPredicate hasGlyph) {
		super(font, panelWidth(clampColumns(columns)), panelHeight(clampColumns(columns), Math.max(1, rows)));
		this.insert = insert;
		this.columns = clampColumns(columns);
		this.rows = Math.max(1, rows);
		this.full = this.columns >= FULL_COLUMNS;
		this.hasGlyph = hasGlyph;
		this.search.setFocused(false);
		this.search.responder(value -> this.refresh());
		this.category = Math.clamp(lastCategory, 0, Symbols.CATEGORIES.size() - 1);
		this.refresh();
		this.scroll = Math.clamp(lastScroll / this.columns, 0, this.maxScroll());
	}

	private static int clampColumns(int columns) {
		return Math.clamp(columns, MIN_COLUMNS, FULL_COLUMNS);
	}

	public static int panelWidth() {
		return panelWidth(FULL_COLUMNS);
	}

	public static int panelWidth(int columns) {
		return columns * CELL + 8;
	}

	public static int panelHeight(int columns, int rows) {
		return gridY(columns) + rows * CELL + footer(columns);
	}

	public static int columnsFor(int width) {
		return Math.min(FULL_COLUMNS, Math.max(0, width - 8) / CELL);
	}

	public static int rowsFor(int columns, int height) {
		int want = columns >= FULL_COLUMNS ? FULL_ROWS : (FULL_COLUMNS * FULL_ROWS + columns - 1) / columns;
		return Math.min(want, Math.floorDiv(height - gridY(columns) - footer(columns), CELL));
	}

	private static int tabsPerRow(int columns) {
		int count = Symbols.CATEGORIES.size();
		return columns * CELL / count >= TAB_MIN ? count : (count + 1) / 2;
	}

	private static int tabRows(int columns) {
		int perRow = tabsPerRow(columns);
		return (Symbols.CATEGORIES.size() + perRow - 1) / perRow;
	}

	private static int tabWidth(int columns) {
		int share = columns * CELL / tabsPerRow(columns);
		return tabRows(columns) == 1 ? Math.min(TAB, share) : share;
	}

	private static int nameY(int columns) {
		return TABS_Y + tabRows(columns) * TAB_HEIGHT + 3;
	}

	private static int gridY(int columns) {
		return nameY(columns) + 11;
	}

	private static int footer(int columns) {
		return columns >= FULL_COLUMNS ? 17 : 15 + recentRows(columns) * CELL;
	}

	private static int recentRows(int columns) {
		return (MAX_RECENT + columns - 1) / columns;
	}

	private void refresh() {
		this.scroll = 0;
		String query = this.search.value();
		this.shown = query.isBlank() ? Symbols.category(this.category, this.hasGlyph) : Symbols.search(query, this.hasGlyph, 600);
	}

	private boolean searching() {
		return !this.search.value().isBlank();
	}

	private int maxScroll() {
		int lines = (this.shown.size() + this.columns - 1) / this.columns;
		return Math.max(0, lines - this.rows);
	}

	private int recentRoom() {
		int start = 9 + this.font.width(Component.translatable("scribe.symbols.recent"));
		return Math.max(0, (this.width - start - 4) / 11);
	}

	private List<String> recent() {
		List<String> out = new ArrayList<>();
		ScribeConfig.get().recentSymbols.codePoints().limit(this.full ? this.recentRoom() : MAX_RECENT).forEach(cp -> out.add(Character.toString(cp)));
		return out;
	}

	private void use(String symbol) {
		this.insert.accept(symbol);
		List<String> recent = new ArrayList<>();
		ScribeConfig.get().recentSymbols.codePoints().forEach(cp -> recent.add(Character.toString(cp)));
		recent.remove(symbol);
		recent.addFirst(symbol);
		ScribeConfig config = ScribeConfig.get();
		config.recentSymbols = String.join("", recent.subList(0, Math.min(MAX_RECENT, recent.size())));
		config.save();
	}

	private int gridTop() {
		return this.y + gridY(this.columns);
	}

	private int cellX(int col) {
		return this.x + 4 + col * CELL;
	}

	private int footerTop() {
		return this.gridTop() + this.rows * CELL + 1;
	}

	private int recentTop() {
		return this.footerTop() + 12;
	}

	private int gridIndex(double mouseX, double mouseY) {
		int gx = (int) Math.floor((mouseX - this.x - 4) / CELL);
		int gy = (int) Math.floor((mouseY - this.gridTop()) / CELL);
		if (gx < 0 || gx >= this.columns || gy < 0 || gy >= this.rows) {
			return -1;
		}
		int index = (this.scroll + gy) * this.columns + gx;
		return index < this.shown.size() ? index : -1;
	}

	private int tabX(int i) {
		return this.x + 4 + i % tabsPerRow(this.columns) * tabWidth(this.columns);
	}

	private int tabY(int i) {
		return this.y + TABS_Y + i / tabsPerRow(this.columns) * TAB_HEIGHT;
	}

	private int tabAt(double mouseX, double mouseY) {
		int row = (int) Math.floor((mouseY - this.y - TABS_Y) / TAB_HEIGHT);
		int col = (int) Math.floor((mouseX - this.x - 4) / tabWidth(this.columns));
		int perRow = tabsPerRow(this.columns);
		if (row < 0 || row >= tabRows(this.columns) || col < 0 || col >= perRow) {
			return -1;
		}
		int tab = row * perRow + col;
		return tab < Symbols.CATEGORIES.size() ? tab : -1;
	}

	private int recentAt(double mouseX, double mouseY) {
		int count = this.recent().size();
		if (this.full) {
			int rowY = this.footerTop() + 3;
			if (mouseY < rowY - 1 || mouseY >= rowY + 10) {
				return -1;
			}
			int start = this.x + 9 + this.font.width(Component.translatable("scribe.symbols.recent"));
			int i = (int) Math.floor((mouseX - start + 1) / 11);
			return i >= 0 && i < count ? i : -1;
		}
		int col = (int) Math.floor((mouseX - this.x - 4) / CELL);
		int row = (int) Math.floor((mouseY - this.recentTop()) / CELL);
		if (col < 0 || col >= this.columns || row < 0) {
			return -1;
		}
		int i = row * this.columns + col;
		return i < count ? i : -1;
	}

	private int searchX() {
		if (!this.full) {
			return this.x + 4;
		}
		return this.x + 9 + this.font.width(Component.translatable("scribe.symbols.title").copy().withStyle(ChatFormatting.BOLD));
	}

	private int searchRight() {
		return this.x + this.width - 4 - CLOSE - 2;
	}

	private boolean inSearch(double mouseX, double mouseY) {
		return mouseX >= this.searchX() && mouseX < this.searchRight() && mouseY >= this.y + 3 && mouseY < this.y + 14;
	}

	private int closeX() {
		return this.x + this.width - 4 - CLOSE;
	}

	private boolean inClose(double mouseX, double mouseY) {
		return mouseX >= this.closeX() && mouseX < this.closeX() + CLOSE && mouseY >= this.y + 3 && mouseY < this.y + 3 + CLOSE;
	}

	private String fit(String text, int width) {
		if (this.font.width(text) <= width) {
			return text;
		}
		return this.font.plainSubstrByWidth(text, width - this.font.width("...")).stripTrailing() + "...";
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		BookStyle.paper(graphics, this.x, this.y, this.width, this.height);
		if (this.full) {
			graphics.text(this.font, Component.translatable("scribe.symbols.title").copy().withStyle(ChatFormatting.BOLD), this.x + 5, this.y + 5, BookStyle.INK, false);
		}
		int sx = this.searchX();
		BookStyle.field(graphics, sx, this.y + 3, this.searchRight() - sx, 11);
		this.search.render(graphics, this.font, sx + 3, this.y + 5, this.searchRight() - 6 - sx, BookStyle.INK,
			Component.translatable("scribe.symbols.search").getString(), BookStyle.INK_SOFT);
		this.closeBox(graphics, this.inClose(mouseX, mouseY));
		if (this.inClose(mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable("scribe.symbols.close"), mouseX, mouseY);
		}

		this.tabs(graphics, mouseX, mouseY);
		Component name = this.searching()
			? Component.translatable("scribe.symbols.results", this.shown.size())
			: Component.translatable("scribe.symbols.category." + Symbols.CATEGORIES.get(this.category).key());
		graphics.text(this.font, this.fit(name.getString(), this.width - 10), this.x + 5, this.y + nameY(this.columns), BookStyle.INK_SOFT, false);

		int hovered = this.gridIndex(mouseX, mouseY);
		for (int row = 0; row < this.rows; row++) {
			for (int col = 0; col < this.columns; col++) {
				int index = (this.scroll + row) * this.columns + col;
				if (index >= this.shown.size()) {
					break;
				}
				this.cell(graphics, this.shown.get(index), this.cellX(col), this.gridTop() + row * CELL, index == hovered);
			}
		}
		if (this.shown.isEmpty()) {
			List<FormattedCharSequence> lines = this.font.split(Component.translatable("scribe.symbols.none"), this.width - 8);
			int ly = this.gridTop() + Math.max(2, Math.min(24, this.rows * CELL - lines.size() * 9));
			for (FormattedCharSequence line : lines) {
				graphics.text(this.font, line, this.x + (this.width - this.font.width(line)) / 2, ly, BookStyle.INK_SOFT, false);
				ly += 9;
			}
		}
		if (this.maxScroll() > 0) {
			int trackTop = this.gridTop();
			int trackHeight = this.rows * CELL - 2;
			int thumb = Math.max(6, trackHeight * this.rows / (this.rows + this.maxScroll()));
			int thumbY = trackTop + (trackHeight - thumb) * this.scroll / this.maxScroll();
			graphics.fill(this.x + this.width - 3, thumbY, this.x + this.width - 2, thumbY + thumb, BookStyle.INK_SOFT);
		}

		int line = this.footerTop();
		graphics.fill(this.x + 4, line, this.x + this.width - 4, line + 1, BookStyle.PAPER_HOVER);
		String recentLabel = Component.translatable("scribe.symbols.recent").getString();
		graphics.text(this.font, this.full ? recentLabel : this.fit(recentLabel, this.width - 10), this.x + 5, line + 3, BookStyle.INK_SOFT, false);
		int hoveredRecent = this.recentAt(mouseX, mouseY);
		List<String> recent = this.recent();
		for (int i = 0; i < recent.size(); i++) {
			if (this.full) {
				int rx = this.x + 9 + this.font.width(recentLabel) + i * 11;
				if (i == hoveredRecent) {
					graphics.fill(rx - 2, line + 2, rx + 9, line + 12, BookStyle.PAPER_HOVER);
				}
				graphics.text(this.font, recent.get(i), rx, line + 3, BookStyle.INK, false);
			} else {
				this.cell(graphics, recent.get(i), this.cellX(i % this.columns), this.recentTop() + i / this.columns * CELL, i == hoveredRecent);
			}
		}

		String tip = hovered >= 0 ? this.shown.get(hovered) : hoveredRecent >= 0 ? recent.get(hoveredRecent) : null;
		if (tip != null) {
			graphics.setTooltipForNextFrame(this.font, List.of(
				Component.literal(tip + "  " + Symbols.name(tip)).getVisualOrderText(),
				Component.literal(Symbols.codePoint(tip)).withStyle(ChatFormatting.GRAY).getVisualOrderText()), mouseX, mouseY);
		}
	}

	private void closeBox(GuiGraphicsExtractor graphics, boolean hovered) {
		int cx = this.closeX();
		int cy = this.y + 3;
		if (hovered) {
			graphics.fill(cx, cy, cx + CLOSE, cy + CLOSE, BookStyle.PAPER_HOVER);
		}
		int ink = hovered ? BookStyle.INK : BookStyle.INK_SOFT;
		for (int i = 0; i < 7; i++) {
			graphics.fill(cx + 2 + i, cy + 2 + i, cx + 4 + i, cy + 3 + i, ink);
			graphics.fill(cx + 7 - i, cy + 2 + i, cx + 9 - i, cy + 3 + i, ink);
		}
	}

	private void cell(GuiGraphicsExtractor graphics, String symbol, int cx, int cy, boolean hovered) {
		if (hovered) {
			graphics.fill(cx - 1, cy - 1, cx + CELL - 1, cy + CELL - 1, BookStyle.PAPER_HOVER);
		}
		graphics.text(this.font, symbol, cx + (CELL - this.font.width(symbol)) / 2, cy + 2, BookStyle.INK, false);
	}

	private void tabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int open = this.searching() ? -1 : this.category;
		int hoveredTab = this.tabAt(mouseX, mouseY);
		int w = tabWidth(this.columns);
		int lineY = this.y + TABS_Y + tabRows(this.columns) * TAB_HEIGHT - 1;
		graphics.fill(this.x + 3, lineY, this.x + this.width - 3, lineY + 1, BookStyle.FIELD_EDGE);
		for (int i = 0; i < Symbols.CATEGORIES.size(); i++) {
			int tx = this.tabX(i);
			int top = this.tabY(i);
			int bottom = top + TAB_HEIGHT;
			int right = tx + w - 1;
			boolean isOpen = i == open;
			int tabTop = isOpen ? top : top + 1;
			graphics.fill(tx, tabTop, right, bottom - (isOpen ? 0 : 1), BookStyle.FIELD_EDGE);
			int fill = isOpen ? BookStyle.PAPER_FILL : i == hoveredTab ? TAB_IDLE_HOVER : TAB_IDLE;
			graphics.fill(tx + 1, tabTop + 1, right - 1, bottom - (isOpen ? 0 : 1), fill);
			String icon = Symbols.CATEGORIES.get(i).icon();
			int iconColor = isOpen ? BookStyle.INK : BookStyle.INK_SOFT;
			graphics.text(this.font, icon, tx + (w - 1 - this.font.width(icon)) / 2 + 1, tabTop + 3, iconColor, false);
		}
		if (hoveredTab >= 0) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable("scribe.symbols.category." + Symbols.CATEGORIES.get(hoveredTab).key()), mouseX, mouseY);
		}
	}

	@Override
	public void mouseClicked(double mouseX, double mouseY, int button) {
		if (this.inClose(mouseX, mouseY)) {
			this.close();
			return;
		}
		this.search.setFocused(this.inSearch(mouseX, mouseY));
		int tab = this.tabAt(mouseX, mouseY);
		if (tab >= 0) {
			this.category = tab;
			lastCategory = tab;
			lastScroll = 0;
			this.search.setValue("");
			this.refresh();
			return;
		}
		int index = this.gridIndex(mouseX, mouseY);
		if (index >= 0) {
			this.use(this.shown.get(index));
			return;
		}
		int recent = this.recentAt(mouseX, mouseY);
		if (recent >= 0) {
			this.use(this.recent().get(recent));
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		int steps = this.wheel.take(amount);
		if (steps != 0) {
			this.scroll = Mth.clamp(this.scroll + steps, 0, this.maxScroll());
			if (!this.searching()) {
				lastScroll = this.scroll * this.columns;
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return this.search.keyPressed(event);
	}

	@Override
	public void onClosed() {
		this.search.setFocused(false);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return this.search.focused() && this.search.charTyped(event);
	}

	@Override
	public boolean modal() {
		return false;
	}

	@Override
	public boolean takesKeys() {
		return this.search.focused();
	}

	@Override
	public void clickedOutside() {
		this.search.setFocused(false);
	}
}
