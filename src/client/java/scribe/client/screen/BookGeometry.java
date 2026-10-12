package scribe.client.screen;

import scribe.BookLimits;
import scribe.config.ScribeConfig;

public final class BookGeometry {
	public static final int SPREAD_WIDTH = 295;
	public static final int SINGLE_WIDTH = 146;
	public static final int HEIGHT = 180;
	public static final int BAR_WIDTH = 204;
	public static final int CELL_WIDTH = 12;
	public static final int CELL_HEIGHT = 15;
	public static final int CELL_STEP = 14;
	public static final int SPREAD_MIN_SCREEN_WIDTH = 340;

	private static final int TEXT_LEFT_0 = 22;
	private static final int TEXT_LEFT_1 = 159;
	private static final int SINGLE_TEXT_LEFT = 16;
	private static final int TEXT_TOP = 21;
	private static final int[] NUMBER_CENTER = {79, 217};

	public final boolean spread;
	public final int screenWidth;
	public final int screenHeight;
	public final int shift;
	public final int left;
	public final int top;
	public final int width;

	private BookGeometry(boolean spread, int screenWidth, int screenHeight, int shift) {
		this.spread = spread;
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
		this.shift = shift;
		this.width = spread ? SPREAD_WIDTH : SINGLE_WIDTH;
		this.left = (screenWidth - this.width) / 2 + shift;
		int top = (screenHeight - HEIGHT) / 2 - 4;
		this.top = Math.max(20, Math.min(top, screenHeight - HEIGHT - 26));
	}

	public static BookGeometry of(int screenWidth, int screenHeight) {
		return new BookGeometry(wantsSpread(screenWidth), screenWidth, screenHeight, 0);
	}

	public static BookGeometry of(int screenWidth, int screenHeight, boolean spread) {
		return new BookGeometry(spread, screenWidth, screenHeight, 0);
	}

	public BookGeometry shifted(int shift) {
		return shift == this.shift ? this : new BookGeometry(this.spread, this.screenWidth, this.screenHeight, shift);
	}

	public static boolean wantsSpread(int screenWidth) {
		return switch (ScribeConfig.get().layout) {
			case SINGLE -> false;
			case SPREAD -> true;
			case AUTO -> screenWidth >= SPREAD_MIN_SCREEN_WIDTH;
		};
	}

	public int slots() {
		return this.spread ? 2 : 1;
	}

	public int right() {
		return this.left + this.width;
	}

	public int bottom() {
		return this.top + HEIGHT;
	}

	public int centerX() {
		return this.left + this.width / 2;
	}

	public int textX(int slot) {
		if (!this.spread) {
			return this.left + SINGLE_TEXT_LEFT;
		}
		return this.left + (slot == 0 ? TEXT_LEFT_0 : TEXT_LEFT_1);
	}

	public int textY() {
		return this.top + TEXT_TOP;
	}

	public int textBottom() {
		return this.textY() + BookLimits.PAGE_LINES * BookLimits.LINE_HEIGHT;
	}

	public int pageCenterX(int slot) {
		if (!this.spread) {
			return this.left + SINGLE_TEXT_LEFT + BookLimits.PAGE_WIDTH / 2;
		}
		return this.left + NUMBER_CENTER[slot];
	}

	public int pageNumberY() {
		return this.top + 157;
	}

	public int headerY() {
		return this.top + 10;
	}

	public int textLeftEdge() {
		return this.textX(0);
	}

	public int textRightEdge() {
		return this.textX(this.slots() - 1) + BookLimits.PAGE_WIDTH;
	}

	public int ribbonX(int slot) {
		if (!this.spread) {
			return this.left + SINGLE_TEXT_LEFT + 105;
		}
		return this.left + (slot == 0 ? 127 : 161);
	}

	public int ribbonY() {
		return this.top + 4;
	}

	public int barX() {
		return this.left + (this.width - BAR_WIDTH) / 2;
	}

	public int barY() {
		return this.top - 18;
	}

	public int indexX() {
		return this.left - 15;
	}

	public int indexY() {
		return this.top + 14;
	}

	public int toolsX() {
		return this.right() + 3;
	}

	public int toolsY() {
		return this.top + 14;
	}

	public int writingToolY(int index) {
		return this.toolsY() + index * CELL_STEP + (index >= 2 ? 4 : 0);
	}

	public int writingToolsBottom() {
		return this.writingToolY(4) + CELL_HEIGHT;
	}

	public int cellUnderIndexY(int index) {
		return this.indexY() + CELL_STEP + 6 + index * CELL_STEP;
	}

	public int contentsCellY(int index, boolean indexOpen) {
		return indexOpen || this.spread ? this.cellUnderIndexY(index) : this.cellUnderIndexY(3 + index) + CONTENTS_CELLS_GAP;
	}

	public int cellsUnderIndexBottom() {
		return this.cellUnderIndexY(2) + CELL_HEIGHT;
	}

	public int cellsLeft() {
		return this.indexX();
	}

	public int cellsRight() {
		return this.toolsX() + CELL_WIDTH;
	}

	public int pageToolsY() {
		return this.top + 154;
	}

	public int pageToolX(int slot, int tool) {
		int[] x = slot == 0 ? new int[] {112, 126, 98} : new int[] {156, 170, 184};
		return this.left + x[tool];
	}

	public int edgeTabX(boolean rightSide) {
		return rightSide ? this.right() - 1 : this.left - 7;
	}

	public int edgeTabTop(boolean rightSide) {
		return this.top + (rightSide ? 60 : 44);
	}

	public int writingEdgeTabTop(boolean rightSide) {
		int cells = rightSide ? this.writingToolsBottom() : this.spread ? this.indexY() + CELL_HEIGHT : this.cellsUnderIndexBottom();
		return Math.max(this.edgeTabTop(rightSide), cells + EDGE_TAB_GAP);
	}

	public int writingEdgeTabTopUnder(int cellsBottom) {
		return Math.max(this.writingEdgeTabTop(false), cellsBottom + EDGE_TAB_GAP);
	}

	public int edgeTabBottom() {
		return this.top + HEIGHT - 10;
	}

	public int edgeTabSpan(boolean rightSide) {
		return this.edgeTabBottom() - this.edgeTabTop(rightSide);
	}

	public static final int EDGE_TAB_HEIGHT = 7;
	private static final int EDGE_TAB_STEP = 12;
	private static final int EDGE_TAB_MIN_STEP = 5;
	private static final int EDGE_TAB_GAP = 3;
	private static final int CONTENTS_CELLS_GAP = 6;

	public static int edgeTabStep(int span, int count) {
		if (count <= 1) {
			return EDGE_TAB_STEP;
		}
		return Math.clamp((span - EDGE_TAB_HEIGHT) / (count - 1), EDGE_TAB_MIN_STEP, EDGE_TAB_STEP);
	}

	public static int edgeTabCapacity(int span) {
		return (span - EDGE_TAB_HEIGHT) / EDGE_TAB_MIN_STEP + 1;
	}

	public int buttonsY() {
		return Math.min(this.top + 186, this.screenHeight - 22);
	}

	public static final int BUTTON_HEIGHT = 20;

	public static int buttonWidth(int count) {
		return count <= 2 ? 98 : 78;
	}

	public static int buttonsWidth(int count) {
		return count * buttonWidth(count) + (count - 1) * 4;
	}

	public int buttonsX(int count) {
		return this.screenWidth / 2 + this.shift - buttonsWidth(count) / 2;
	}

	public int cornerButtonsLeft() {
		return this.screenWidth - 47;
	}

	public static final int CORNER_BUTTONS_BOTTOM = 17;
}
