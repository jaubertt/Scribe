package scribe.client.screen;

import scribe.client.text.FormatCodes;
import java.util.ArrayList;
import java.util.List;

public final class SignEditLayout {
	public static final int TITLE_Y = 40;
	public static final int DONE_WIDTH = 200;
	public static final int DONE_HEIGHT = 20;
	public static final int BUTTON_Y = 3;
	public static final int BUTTON_WIDTH = 50;
	public static final int BUTTON_HEIGHT = 16;
	public static final int BUTTON_GAP = 2;
	public static final int BUTTON_COUNT = 4;
	public static final int BUTTONS_WIDTH = BUTTON_COUNT * BUTTON_WIDTH + (BUTTON_COUNT - 1) * BUTTON_GAP;
	public static final int BAR_Y = BUTTON_Y + BUTTON_HEIGHT + 3;
	public static final int BAR_HEIGHT = BookGeometry.CELL_HEIGHT;
	public static final int CELL_STEP = 11;
	public static final int COLORS_X = 69;
	public static final int COLOR_STEP = 6;
	public static final int SYMBOLS_X = 120;
	public static final int BAR_WIDTH = SYMBOLS_X + BookGeometry.CELL_WIDTH;

	public static final String STYLE_CODES = "lonmk";
	private static final String[] STYLE_SPRITES = {"bold", "italic", "underline", "strikethrough", "obfuscated"};

	private SignEditLayout() {
	}

	public enum Kind { STYLE, CLEAR, COLOR, SYMBOLS }

	public record Cell(Kind kind, char code, String sprite, int x, int y, int width, int height) {
		public SignLayout.Rect rect() {
			return new SignLayout.Rect(this.x, this.y, this.width, this.height);
		}
	}

	public static int buttonX(int screenWidth, int index) {
		return screenWidth / 2 - BUTTONS_WIDTH / 2 + index * (BUTTON_WIDTH + BUTTON_GAP);
	}

	public static SignLayout.Rect buttons(int screenWidth) {
		return new SignLayout.Rect(buttonX(screenWidth, 0), BUTTON_Y, BUTTONS_WIDTH, BUTTON_HEIGHT);
	}

	public static int barX(int screenWidth) {
		return screenWidth / 2 - BAR_WIDTH / 2;
	}

	public static SignLayout.Rect bar(int screenWidth) {
		return new SignLayout.Rect(barX(screenWidth), BAR_Y, BAR_WIDTH, BAR_HEIGHT);
	}

	public static int toolsBottom() {
		return BAR_Y + BAR_HEIGHT;
	}

	public static int doneY(int screenHeight) {
		return screenHeight / 4 + 144;
	}

	public static List<Cell> cells(int screenWidth) {
		int bx = barX(screenWidth);
		int by = BAR_Y;
		int w = BookGeometry.CELL_WIDTH;
		int h = BookGeometry.CELL_HEIGHT;
		List<Cell> out = new ArrayList<>();
		for (int i = 0; i < STYLE_CODES.length(); i++) {
			out.add(new Cell(Kind.STYLE, STYLE_CODES.charAt(i), "tool/" + STYLE_SPRITES[i], bx + i * CELL_STEP, by, w, h));
		}
		out.add(new Cell(Kind.CLEAR, FormatCodes.RESET, "tool/clear_format", bx + STYLE_CODES.length() * CELL_STEP, by, w, h));
		for (int i = 0; i < 16; i++) {
			char code = FormatCodes.COLOR_CODES.charAt(i);
			boolean top = i < 8;
			out.add(new Cell(Kind.COLOR, code, "tool/color_" + code, bx + COLORS_X + COLOR_STEP * (top ? i : i - 8), top ? by : by + 6, 7, top ? 7 : 9));
		}
		out.add(new Cell(Kind.SYMBOLS, (char) 0, "tool/symbols", bx + SYMBOLS_X, by, w, h));
		return out;
	}
}
