package scribe.client.screen;

import scribe.BookLimits;
import scribe.client.screen.popup.SymbolPopup;

public record SymbolPlacement(int shift, int x, int y, int columns, int rows) {
	public static final int GAP = 3;
	public static final int EDGE = 2;
	public static final int COMFORTABLE_COLUMNS = 7;

	public static final int SIGN_TITLE_Y = 40;
	public static final int SIGN_HALF_WIDTH = 47;
	public static final int SIGN_TEXT_BOTTOM = 145;

	public static int signDoneY(int screenHeight) {
		return screenHeight / 4 + 144;
	}

	public int width() {
		return SymbolPopup.panelWidth(this.columns);
	}

	public int height() {
		return SymbolPopup.panelHeight(this.columns, this.rows);
	}

	public static SymbolPlacement forBook(BookGeometry book, int buttons, int slot, int line) {
		BookGeometry home = book.shifted(0);
		if (home.spread) {
			return overPage(home, slot, line);
		}
		SymbolPlacement here = better(besideBook(home, buttons, true), besideBook(home, buttons, false));
		if (here != null && here.columns >= COMFORTABLE_COLUMNS) {
			return here;
		}
		SymbolPlacement moved = better(movedAside(home, buttons, true), movedAside(home, buttons, false));
		if (moved != null && (here == null || moved.columns > here.columns)) {
			return moved;
		}
		return here != null ? here : overPage(home, slot, line);
	}

	private static SymbolPlacement better(SymbolPlacement right, SymbolPlacement left) {
		if (right == null || left == null) {
			return right == null ? left : right;
		}
		return left.columns > right.columns ? left : right;
	}

	private static int room(BookGeometry book, boolean right) {
		return right ? book.screenWidth - EDGE - (book.cellsRight() + GAP) : book.cellsLeft() - GAP - EDGE;
	}

	private static SymbolPlacement movedAside(BookGeometry home, int buttons, boolean right) {
		int buttonsLeft = home.buttonsX(buttons);
		int buttonsRight = buttonsLeft + BookGeometry.buttonsWidth(buttons);
		int reach = right
			? Math.min(home.cellsLeft(), Math.min(home.barX(), buttonsLeft)) - EDGE
			: home.screenWidth - EDGE - Math.max(home.cellsRight(), Math.max(home.barX() + BookGeometry.BAR_WIDTH, buttonsRight));
		if (reach <= 0) {
			return null;
		}
		int room = room(home, right);
		int columns = SymbolPopup.columnsFor(room + reach);
		if (columns < SymbolPopup.MIN_COLUMNS) {
			return null;
		}
		int move = Math.max(0, SymbolPopup.panelWidth(columns) - room);
		return besideBook(home.shifted(right ? -move : move), buttons, right, columns);
	}

	private static SymbolPlacement besideBook(BookGeometry book, int buttons, boolean right) {
		return besideBook(book, buttons, right, SymbolPopup.columnsFor(room(book, right)));
	}

	private static SymbolPlacement besideBook(BookGeometry book, int buttons, boolean right, int columns) {
		if (columns < SymbolPopup.MIN_COLUMNS) {
			return null;
		}
		int width = SymbolPopup.panelWidth(columns);
		int x = right ? book.cellsRight() + GAP : book.cellsLeft() - GAP - width;
		int top = EDGE;
		int bottom = book.screenHeight - EDGE;
		if (x + width > book.cornerButtonsLeft()) {
			top = BookGeometry.CORNER_BUTTONS_BOTTOM + GAP;
		}
		if (x < book.barX() + BookGeometry.BAR_WIDTH && x + width > book.barX()) {
			top = Math.max(top, book.barY() + BookGeometry.CELL_HEIGHT + GAP);
		}
		int buttonsX = book.buttonsX(buttons);
		if (x < buttonsX + BookGeometry.buttonsWidth(buttons) && x + width > buttonsX) {
			bottom = Math.min(bottom, book.buttonsY() - GAP);
		}
		int rows = SymbolPopup.rowsFor(columns, bottom - top);
		if (rows < SymbolPopup.MIN_ROWS) {
			return null;
		}
		int y = Math.clamp(book.top, top, Math.max(top, bottom - SymbolPopup.panelHeight(columns, rows)));
		return new SymbolPlacement(book.shift, x, y, columns, rows);
	}

	private static SymbolPlacement overPage(BookGeometry home, int slot, int line) {
		int columns = SymbolPopup.FULL_COLUMNS;
		int width = SymbolPopup.panelWidth(columns);
		if (home.spread) {
			int top = home.textY() - 4;
			int rows = Math.max(1, SymbolPopup.rowsFor(columns, home.pageToolsY() - GAP - top));
			return new SymbolPlacement(0, home.pageCenterX(slot == 0 ? 1 : 0) - width / 2, top, columns, rows);
		}
		int rows = SymbolPopup.FULL_ROWS;
		int y = line < BookLimits.PAGE_LINES / 2 ? home.bottom() - SymbolPopup.panelHeight(columns, rows) : home.textY() - 4;
		return new SymbolPlacement(0, home.centerX() - width / 2, y, columns, rows);
	}

	public static SymbolPlacement forSign(int screenWidth, int screenHeight, int toolsBottom, int titleWidth) {
		int center = screenWidth / 2;
		int bottom = signDoneY(screenHeight) - GAP;
		for (boolean right : new boolean[] {true, false}) {
			int room = right ? screenWidth - EDGE - (center + SIGN_HALF_WIDTH + GAP) : center - SIGN_HALF_WIDTH - GAP - EDGE;
			int columns = SymbolPopup.columnsFor(room);
			if (columns < SymbolPopup.MIN_COLUMNS) {
				continue;
			}
			int width = SymbolPopup.panelWidth(columns);
			int x = right ? center + SIGN_HALF_WIDTH + GAP : center - SIGN_HALF_WIDTH - GAP - width;
			int top = toolsBottom + GAP;
			int titleLeft = center - titleWidth / 2 - 1;
			int titleRight = center + (titleWidth + 1) / 2 + 1;
			if (x < titleRight + GAP && x + width > titleLeft - GAP) {
				top = Math.max(top, SIGN_TITLE_Y + 9 + GAP);
			}
			int rows = SymbolPopup.rowsFor(columns, bottom - top);
			if (rows >= SymbolPopup.MIN_ROWS) {
				return new SymbolPlacement(0, x, top, columns, rows);
			}
		}
		int columns = SymbolPopup.FULL_COLUMNS;
		int rows = Math.max(1, SymbolPopup.rowsFor(columns, bottom - SIGN_TEXT_BOTTOM - GAP));
		return new SymbolPlacement(0, center - SymbolPopup.panelWidth(columns) / 2, SIGN_TEXT_BOTTOM + GAP, columns, rows);
	}

	public static SymbolPlacement forCover(SignLayout home) {
		SymbolPlacement here = better(besideCover(home, true), besideCover(home, false));
		if (here != null && here.columns >= COMFORTABLE_COLUMNS) {
			return here;
		}
		SymbolPlacement moved = better(coverMovedAside(home, true), coverMovedAside(home, false));
		if (moved != null && (here == null || moved.columns > here.columns)) {
			return moved;
		}
		return here != null ? here : overDesignPanel(home);
	}

	private static int coverRoom(SignLayout layout, boolean right) {
		return right ? layout.screenWidth() - EDGE - (layout.panel().right() + GAP) : layout.cover().x() - GAP - EDGE;
	}

	private static SymbolPlacement coverMovedAside(SignLayout home, boolean right) {
		int reach = right ? home.cover().x() - EDGE : home.screenWidth() - EDGE - home.panel().right();
		if (reach <= 0) {
			return null;
		}
		int room = coverRoom(home, right);
		int columns = SymbolPopup.columnsFor(room + reach);
		if (columns < SymbolPopup.MIN_COLUMNS) {
			return null;
		}
		int move = right ? -Math.max(0, SymbolPopup.panelWidth(columns) - room) : Math.max(0, SymbolPopup.panelWidth(columns) - room);
		return besideCover(home.shifted(move), right, columns, move);
	}

	private static SymbolPlacement besideCover(SignLayout layout, boolean right) {
		return besideCover(layout, right, SymbolPopup.columnsFor(coverRoom(layout, right)), 0);
	}

	private static SymbolPlacement besideCover(SignLayout layout, boolean right, int columns, int shift) {
		if (columns < SymbolPopup.MIN_COLUMNS) {
			return null;
		}
		int width = SymbolPopup.panelWidth(columns);
		int x = right ? layout.panel().right() + GAP : layout.cover().x() - GAP - width;
		int top = EDGE;
		int bottom = layout.screenHeight() - EDGE;
		int rows = SymbolPopup.rowsFor(columns, bottom - top);
		if (rows < SymbolPopup.MIN_ROWS) {
			return null;
		}
		int y = Math.clamp(layout.cover().y(), top, Math.max(top, bottom - SymbolPopup.panelHeight(columns, rows)));
		return new SymbolPlacement(shift, x, y, columns, rows);
	}

	private static SymbolPlacement overDesignPanel(SignLayout home) {
		SignLayout.Rect panel = home.panel();
		int columns = Math.max(SymbolPopup.MIN_COLUMNS, SymbolPopup.columnsFor(panel.width()));
		int rows = Math.max(1, SymbolPopup.rowsFor(columns, panel.height()));
		return new SymbolPlacement(0, panel.x() + (panel.width() - SymbolPopup.panelWidth(columns)) / 2, panel.y(), columns, rows);
	}
}
