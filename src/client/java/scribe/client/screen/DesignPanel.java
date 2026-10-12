package scribe.client.screen;

import java.util.List;
import java.util.function.IntPredicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import scribe.Scribe;
import scribe.binding.Bookbinding;
import scribe.binding.CoverDesign;
import scribe.client.screen.CoverChoices.Row;
import scribe.client.screen.SignLayout.Rect;

public final class DesignPanel {
	public static final int WIDTH = 158;
	public static final int HEIGHT = 180;
	public static final int PAD = 5;
	public static final int GRID_TOP = 10;
	public static final int APART = 2;
	public static final int LABEL_GAP = 4;
	public static final int CORNER_CELL = 17;
	public static final int DECO_CELL = 17;
	public static final int SQUARE = 7;
	public static final int BUTTON_WIDTH = 12;
	public static final int BUTTON_HEIGHT = 15;
	public static final int FACE_X = 2;
	public static final int FACE_Y = 4;
	public static final int THICKNESS = 2;
	public static final int METAL_DROP = BUTTON_HEIGHT - (SQUARE + THICKNESS);
	public static final int FOOT = DECO_CELL - 2;
	public static final int HINT_ICON = 16;
	public static final int HINT_ICON_Y = 7;
	public static final int HINT_TEXT_X = 20;
	public static final int HINT_TEXT_Y = 2;
	public static final int HINT_LINE = 10;
	public static final int HINT_LINES = 3;
	public static final int HINT_WIDTH = WIDTH - 2 * PAD - HINT_TEXT_X;
	public static final Identifier SMITHING_TABLE = Identifier.withDefaultNamespace("textures/block/smithing_table_front.png");
	public static final String BLANK = "tool/blank";
	public static final int TICK_X = 79;
	public static final int TICK_LABEL_X = TICK_X + BUTTON_WIDTH + LABEL_GAP;
	public static final int TICK_LABEL_WIDTH = WIDTH - PAD - TICK_LABEL_X;
	public static final int METALWORK_CELL = 13;
	public static final int METALWORK_X = WIDTH - PAD - (METALWORK_CELL + 3 * (METALWORK_CELL - 1) + APART + 1);
	public static final int PLAQUE_X = METALWORK_X + METALWORK_CELL + 2 * (METALWORK_CELL - 1) + APART;
	static final int BUCKLES = 3;
	static final int GHOST_ALPHA = 140;

	static final int OUTLINE_INK = 0xFF6E4C30;
	static final int EDGE = 0xFF4E321D;
	static final int[] SIDE = {0xFFB39470, 0xFFC4A57E, 0xFF9E7F5B};
	static final int[] SIDE_PRESSED = {0xFFD6BC98, 0xFFDEC6A4, 0xFFC9AD87};
	static final int REST = 0xFFC6A561;
	static final double REST_MIX = 0.7;
	static final int REST_FACE_ALPHA = 255 * 45 / 100;
	static final int CARVED_GRAY = 105;
	static final int CARVED_EDGE_ALPHA = 61;
	static final int[] METAL_COLORS = {
		0xFFE3D4C4, 0xFFECECEC, 0xFF625859, 0xFF971607, 0xFFB4684D, 0xFFDEB12D, 0xFF11A036, 0xFF6EECD2, 0xFF416E97, 0xFF9A5CC6, 0xFFFC7812};
	private static final String COLOR_CODES = "0123456789abcdef";
	static final int[][] CHECK = {{0, 2}, {1, 3}, {2, 2}, {3, 1}, {4, 0}};
	static final int TICK_FIELD = EDGE;
	static final int TICK_CHECK = 0xFFFEF7E6;

	private DesignPanel() {
	}

	public record Hit(Row row, int index) {
	}

	private enum Face {
		NORMAL, PRESSED, REST
	}

	public static int rowY(Row row) {
		return switch (row) {
			case TITLE_COLOR -> 4;
			case SPINE, METALWORK -> 31;
			case DECORATION -> 58;
			case LOOK -> 105;
			case TRIM -> 132;
		};
	}

	public static int gridY(Row row) {
		return rowY(row) + GRID_TOP;
	}

	public static boolean button(Row row) {
		return row == Row.TITLE_COLOR || row == Row.LOOK || row == Row.SPINE;
	}

	public static int cell(Row row) {
		return switch (row) {
			case DECORATION -> DECO_CELL;
			case TRIM -> CORNER_CELL;
			case METALWORK -> METALWORK_CELL;
			default -> SQUARE;
		};
	}

	private static int drop(Row row) {
		return row == Row.LOOK || row == Row.SPINE ? METAL_DROP : 0;
	}

	public static int squaresX(Row row) {
		return PAD + (button(row) ? BUTTON_WIDTH : cell(row)) + APART;
	}

	public static Rect choice(Row row, int index) {
		int gy = gridY(row);
		int cell = cell(row);
		if (row == Row.METALWORK) {
			int x = index < BUCKLES ? METALWORK_X + Math.max(0, index) * (cell - 1) : PLAQUE_X;
			return new Rect(x, gy, cell, cell + THICKNESS);
		}
		if (index <= 0) {
			return button(row) ? new Rect(PAD, gy, BUTTON_WIDTH, BUTTON_HEIGHT) : new Rect(PAD, gy, cell, cell + THICKNESS);
		}
		int i = index - 1;
		int line = i / row.columns();
		int column = i % row.columns();
		boolean bottom = line == row.lines() - 1;
		return new Rect(squaresX(row) + column * (cell - 1), gy + drop(row) + line * (cell - 1), cell, bottom ? cell + THICKNESS : cell);
	}

	public static Rect inside(Row row, int index) {
		Rect r = choice(row, index);
		if (index <= 0 && button(row)) {
			return new Rect(r.x() + FACE_X, r.y() + FACE_Y, SQUARE, SQUARE);
		}
		int cell = cell(row);
		return new Rect(r.x() + 1, r.y() + 1, cell - 2, cell - 2);
	}

	public static Rect rowChoices(Row row) {
		Rect first = choice(row, 0);
		Rect last = choice(row, row.count() - 1);
		Rect corner = choice(row, row.columns());
		int bottom = Math.max(first.bottom(), last.bottom());
		return new Rect(first.x(), first.y(), Math.max(last.right(), corner.right()) - first.x(), bottom - first.y());
	}

	public static Rect label(Row row) {
		return switch (row) {
			case METALWORK -> new Rect(METALWORK_X, rowY(row), WIDTH - PAD - METALWORK_X, GRID_TOP);
			case SPINE -> new Rect(PAD, rowY(row), METALWORK_X - LABEL_GAP - PAD, GRID_TOP);
			default -> new Rect(PAD, rowY(row), WIDTH - 2 * PAD, GRID_TOP);
		};
	}

	public static Rect hintIcon() {
		return new Rect(PAD, gridY(Row.TRIM) + HINT_ICON_Y, HINT_ICON, HINT_ICON);
	}

	public static Rect hintText(int lines) {
		return new Rect(PAD + HINT_TEXT_X, gridY(Row.TRIM) + HINT_TEXT_Y, HINT_WIDTH, lines * HINT_LINE);
	}

	public static Rect tickButton() {
		return new Rect(TICK_X, gridY(Row.TITLE_COLOR), BUTTON_WIDTH, BUTTON_HEIGHT);
	}

	public static Rect tickBox(boolean pressed) {
		return new Rect(TICK_X + FACE_X, gridY(Row.TITLE_COLOR) + FACE_Y + (pressed ? 1 : 0), SQUARE, SQUARE);
	}

	public static Rect tickLabel() {
		return new Rect(TICK_LABEL_X, gridY(Row.TITLE_COLOR) + FACE_Y, TICK_LABEL_WIDTH, 9);
	}

	public static Rect tickArea() {
		return new Rect(TICK_X, gridY(Row.TITLE_COLOR), WIDTH - PAD - TICK_X, BUTTON_HEIGHT);
	}

	public static boolean overTick(int px, int py, double mouseX, double mouseY) {
		return tickArea().contains(Math.floor(mouseX) - px, Math.floor(mouseY) - py);
	}

	public static Component tickName() {
		return Component.translatable("scribe.sign.hide_author");
	}

	public static int choiceAt(Row row, int x, int y) {
		for (int i = 0; i < row.count(); i++) {
			if (choice(row, i).contains(x, y)) {
				return i;
			}
		}
		return -1;
	}

	public static int restingMetalwork(CoverChoices choices, int px, int py, double mouseX, double mouseY) {
		if (choices.available(Row.METALWORK)) {
			return -1;
		}
		return choiceAt(Row.METALWORK, (int) Math.floor(mouseX) - px, (int) Math.floor(mouseY) - py);
	}

	public static @Nullable Hit hit(CoverChoices choices, int px, int py, double mouseX, double mouseY) {
		int x = (int) Math.floor(mouseX) - px;
		int y = (int) Math.floor(mouseY) - py;
		if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT) {
			return null;
		}
		for (Row row : Row.values()) {
			if (!choices.available(row)) {
				continue;
			}
			int index = choiceAt(row, x, y);
			if (index >= 0) {
				return new Hit(row, index);
			}
		}
		return null;
	}

	public static boolean valueFits(Font font, Component name, Component value) {
		return PAD + font.width(name) + LABEL_GAP + font.width(value) <= WIDTH - PAD;
	}

	public static boolean valueFits(Font font, Row row, Component name, Component value) {
		return font.width(name) + LABEL_GAP + font.width(value) <= label(row).width();
	}

	public static List<FormattedCharSequence> hintLines(Font font) {
		return font.split(Component.translatable("scribe.sign.corners_need_trim"), HINT_WIDTH);
	}

	public static int metalColor(String metal) {
		int index = Bookbinding.MATERIALS.indexOf(metal);
		return index < 0 ? 0xFFAAAAAA : METAL_COLORS[index];
	}

	static int restColor(int color) {
		return ARGB.color(255, mix(ARGB.red(color), ARGB.red(REST)), mix(ARGB.green(color), ARGB.green(REST)), mix(ARGB.blue(color), ARGB.blue(REST)));
	}

	private static int mix(int a, int b) {
		return (int) Math.rint(a + (b - a) * REST_MIX);
	}

	static int carvedFace(int cover) {
		return ARGB.color(255, ARGB.red(cover) * CARVED_GRAY / 255, ARGB.green(cover) * CARVED_GRAY / 255, ARGB.blue(cover) * CARVED_GRAY / 255);
	}

	static int carvedEdge(int leather) {
		return ARGB.color(255, lighter(ARGB.red(leather)), lighter(ARGB.green(leather)), lighter(ARGB.blue(leather)));
	}

	private static int lighter(int c) {
		return c + (int) Math.round((255 - c) * CARVED_EDGE_ALPHA / 255.0);
	}

	public static Identifier cornerSprite(String design, String material) {
		return Scribe.id("gui/corner/" + design + "_open_tl_" + material);
	}

	public static Identifier colorSprite(int color, Face face) {
		return Scribe.id("tool/color_" + COLOR_CODES.charAt(color) + suffix(face));
	}

	private static String suffix(Face face) {
		return face == Face.PRESSED ? "_highlighted" : face == Face.REST ? "_disabled" : "";
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, int px, int py, CoverChoices choices, int cover, @Nullable Row focus,
		@Nullable Hit hover) {
		draw(graphics, font, px, py, choices, cover, focus, hover, false, false);
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, int px, int py, CoverChoices choices, int cover, @Nullable Row focus,
		@Nullable Hit hover, boolean tickFocus, boolean tickHover) {
		BookStyle.paper(graphics, px, py, WIDTH, HEIGHT);
		int leather = CoverView.leather(cover);
		for (Row row : Row.values()) {
			boolean available = choices.available(row);
			if (row == Row.METALWORK) {
				label(graphics, font, px + METALWORK_X, py + rowY(row), row.label(), null, row == focus, !available);
			} else {
				label(graphics, font, px + PAD, py + rowY(row), row, choices.chosenName(row), row == focus, !available);
			}
			IntPredicate pressed = index -> index == choices.chosen(row)
				|| (available && hover != null && hover.row() == row && hover.index() == index);
			int gy = py + gridY(row);
			int sx = px + squaresX(row);
			switch (row) {
				case TITLE_COLOR -> {
					paperButton(graphics, px + PAD, gy, pressed.test(0) ? Face.PRESSED : Face.NORMAL, CoverView.textTones(cover)[0], 0);
					for (int i = 1; i < row.count(); i++) {
						Rect square = choice(row, i);
						graphics.blitSprite(RenderPipelines.GUI_TEXTURED, colorSprite(i - 1, pressed.test(i) ? Face.PRESSED : Face.NORMAL),
							px + square.x(), py + square.y(), square.width(), square.height());
					}
				}
				case DECORATION -> {
					squares(graphics, px + PAD, gy, DECO_CELL, 1, 1, pressed, (g, i, x, y, w, h) -> g.fill(x, y, x + w, y + h, leather));
					squares(graphics, sx, gy, DECO_CELL, row.columns(), row.count() - 1, i -> pressed.test(i + 1), (g, i, x, y, w, h) -> {
						g.fill(x, y, x + w, y + h, leather);
						decorationFoot(g, CoverDesign.DECORATIONS.get(i), choices.look(), cover, x, y, w, h);
					});
				}
				case LOOK, SPINE -> {
					boolean rest = !available;
					Face face = rest ? Face.REST : pressed.test(0) ? Face.PRESSED : Face.NORMAL;
					paperButton(graphics, px + PAD, gy, face, leather, row == Row.LOOK ? carvedFace(cover) : 0);
					squares(graphics, sx, gy + METAL_DROP, SQUARE, row.columns(), row.count() - 1, i -> pressed.test(i + 1), (g, i, x, y, w, h) -> {
						int color = METAL_COLORS[i];
						g.fill(x, y, x + w, y + h, rest ? restColor(color) : color);
					});
				}
				case TRIM -> {
					String material = choices.material();
					if (material == null) {
						hint(graphics, font, px, py);
						break;
					}
					squares(graphics, px + PAD, gy, CORNER_CELL, 1, 1, pressed, (g, i, x, y, w, h) -> g.fill(x, y, x + w, y + h, leather));
					squares(graphics, sx, gy, CORNER_CELL, row.columns(), row.count() - 1, i -> pressed.test(i + 1), (g, i, x, y, w, h) -> {
						g.fill(x, y, x + w, y + h, leather);
						int art = BookStyle.DESIGN_SIZE;
						g.blitSprite(RenderPipelines.GUI_TEXTURED, cornerSprite(CoverDesign.CORNERS.get(i), material), art, art, 0, 0, x, y, Math.min(w, art),
							Math.min(h, art));
					});
				}
				case METALWORK -> metalwork(graphics, px, py, choices, leather, available ? pressed : i -> false, !available);
			}
		}
		tick(graphics, font, px, py, choices.authorHidden(), tickFocus, tickHover);
	}

	private static void tick(GuiGraphicsExtractor graphics, Font font, int px, int py, boolean ticked, boolean focused, boolean hovered) {
		boolean pressed = ticked || hovered;
		Rect button = tickButton();
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Scribe.id(BLANK + suffix(pressed ? Face.PRESSED : Face.NORMAL)), px + button.x(), py + button.y(),
			BUTTON_WIDTH, BUTTON_HEIGHT);
		Rect box = tickBox(pressed).moved(px, py);
		int last = SQUARE - 1;
		graphics.fill(box.x(), box.y(), box.x() + SQUARE, box.y() + 1, OUTLINE_INK);
		graphics.fill(box.x(), box.y() + last, box.x() + SQUARE, box.y() + SQUARE, OUTLINE_INK);
		graphics.fill(box.x(), box.y() + 1, box.x() + 1, box.y() + last, OUTLINE_INK);
		graphics.fill(box.x() + last, box.y() + 1, box.x() + SQUARE, box.y() + last, OUTLINE_INK);
		if (ticked) {
			graphics.fill(box.x() + 1, box.y() + 1, box.x() + last, box.y() + last, TICK_FIELD);
			for (int[] dot : CHECK) {
				int x = box.x() + 1 + dot[0];
				int y = box.y() + 1 + dot[1];
				graphics.fill(x, y, x + 1, y + 1, TICK_CHECK);
			}
		}
		Rect label = tickLabel().moved(px, py);
		List<FormattedCharSequence> lines = font.split(tickName(), TICK_LABEL_WIDTH);
		if (lines.isEmpty()) {
			return;
		}
		FormattedCharSequence name = lines.getFirst();
		graphics.text(font, name, label.x(), label.y(), BookStyle.INK, false);
		if (focused) {
			graphics.fill(label.x(), label.y() + 9, label.x() + font.width(name) - 1, label.y() + 10, BookStyle.INK);
		}
	}

	private static void label(GuiGraphicsExtractor graphics, Font font, int x, int y, Row row, Component value, boolean focused, boolean resting) {
		Component name = row.label();
		label(graphics, font, x, y, name, valueFits(font, row, name, value) ? value : null, focused, resting);
	}

	private static void label(GuiGraphicsExtractor graphics, Font font, int x, int y, Component name, @Nullable Component value, boolean focused,
		boolean resting) {
		graphics.text(font, name, x, y, resting ? BookStyle.INK_SOFT : BookStyle.INK, false);
		int width = font.width(name);
		if (value != null) {
			graphics.text(font, value, x + width + LABEL_GAP, y, BookStyle.INK_SOFT, false);
		}
		if (focused) {
			graphics.fill(x, y + 9, x + width - 1, y + 10, BookStyle.INK);
		}
	}

	private static void metalwork(GuiGraphicsExtractor graphics, int px, int py, CoverChoices choices, int leather, IntPredicate pressed, boolean resting) {
		String metal = resting || choices.buckle() == null ? "iron" : choices.buckle();
		Integer strapColor = choices.strap();
		int strap = strapColor == null ? leather : CoverView.leather(ARGB.opaque(strapColor));
		Tile tile = (g, i, x, y, w, h) -> {
			String design = CoverDesign.METALWORKS.get(i);
			g.fill(x, y, x + w, y + h, leather);
			if (!Metalwork.plaque(design)) {
				g.fill(x, y + Metalwork.MINI_STRAP_TOP, x + w, y + Math.min(h, Metalwork.MINI_STRAP_BOTTOM + 1), strap);
			}
			g.blitSprite(RenderPipelines.GUI_TEXTURED, Metalwork.miniSprite(design, metal), Metalwork.MINI, Metalwork.MINI, 0, 0, x, y, w, h);
			if (resting) {
				g.fill(x, y, x + w, y + h, ARGB.color(GHOST_ALPHA, BookStyle.PAPER_FILL));
			}
		};
		int gy = py + gridY(Row.METALWORK);
		squares(graphics, px + METALWORK_X, gy, METALWORK_CELL, BUCKLES, BUCKLES, pressed, tile);
		squares(graphics, px + PLAQUE_X, gy, METALWORK_CELL, 1, 1, i -> pressed.test(BUCKLES), (g, i, x, y, w, h) -> tile.draw(g, BUCKLES, x, y, w, h));
	}

	private static void hint(GuiGraphicsExtractor graphics, Font font, int px, int py) {
		Rect icon = hintIcon();
		graphics.blit(RenderPipelines.GUI_TEXTURED, SMITHING_TABLE, px + icon.x(), py + icon.y(), 0, 0, HINT_ICON, HINT_ICON, HINT_ICON, HINT_ICON);
		List<FormattedCharSequence> lines = hintLines(font);
		Rect text = hintText(0);
		for (int i = 0; i < Math.min(lines.size(), HINT_LINES); i++) {
			graphics.text(font, lines.get(i), px + text.x(), py + text.y() + i * HINT_LINE, BookStyle.INK_SOFT, false);
		}
	}

	private static void decorationFoot(GuiGraphicsExtractor graphics, String decoration, String look, int cover, int x, int y, int w, int h) {
		int pictureWidth = CoverView.DECORATION_WIDTH / CoverView.DECORATION_CELL;
		int pictureHeight = CoverView.DECORATION_HEIGHT / CoverView.DECORATION_CELL;
		int u = (pictureWidth - FOOT) / 2;
		int v = pictureHeight - FOOT;
		if (CoverDesign.isMetal(look)) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CoverView.decorationSprite(decoration, look), pictureWidth, pictureHeight, u, v, x, y, w, h);
		} else {
			graphics.blit(RenderPipelines.GUI_TEXTURED, CoverView.carvedTexture(decoration), x, y, u, v, w, h, pictureWidth, pictureHeight, cover);
			graphics.blit(RenderPipelines.GUI_TEXTURED, CoverView.carvedEdgeTexture(decoration), x, y, u, v, w, h, pictureWidth, pictureHeight);
		}
	}

	@FunctionalInterface
	private interface Tile {
		void draw(GuiGraphicsExtractor graphics, int index, int x, int y, int w, int h);
	}

	private static void squares(GuiGraphicsExtractor graphics, int x, int y, int cell, int cols, int count, IntPredicate pressed, Tile tile) {
		int step = cell - 1;
		int lines = (count + cols - 1) / cols;
		int inner = cell - 2;
		for (int i = 0; i < count; i++) {
			int line = i / cols;
			int cx = x + (i % cols) * step;
			int cy = y + line * step;
			boolean bottom = line == lines - 1 || (line == lines - 2 && i + cols >= count);
			boolean down = pressed.test(i);
			int sides = cell - (bottom ? 1 : 0);
			graphics.fill(cx, cy, cx + cell, cy + 1, OUTLINE_INK);
			graphics.fill(cx, cy, cx + 1, cy + sides, OUTLINE_INK);
			graphics.fill(cx + cell - 1, cy, cx + cell, cy + sides, OUTLINE_INK);
			if (!bottom) {
				graphics.fill(cx, cy + cell - 1, cx + cell, cy + cell, OUTLINE_INK);
			}
			if (down) {
				graphics.fill(cx + 1, cy + 1, cx + 1 + inner, cy + 1 + inner, EDGE);
				tile.draw(graphics, i, cx + 2, cy + 2, inner - 1, inner - 1);
			} else {
				tile.draw(graphics, i, cx + 1, cy + 1, inner, inner);
			}
			if (bottom) {
				int ty = cy + cell - 1;
				int[] side = down ? SIDE_PRESSED : SIDE;
				for (int k = 0; k < THICKNESS; k++) {
					graphics.fill(cx, ty + k, cx + 1, ty + k + 1, EDGE);
					graphics.fill(cx + cell - 1, ty + k, cx + cell, ty + k + 1, EDGE);
					graphics.fill(cx + 1, ty + k, cx + 2, ty + k + 1, side[1]);
					graphics.fill(cx + 2, ty + k, cx + cell - 2, ty + k + 1, side[0]);
					graphics.fill(cx + cell - 2, ty + k, cx + cell - 1, ty + k + 1, side[2]);
				}
				graphics.fill(cx, ty + THICKNESS, cx + cell, ty + THICKNESS + 1, EDGE);
			}
		}
		int right = x + (Math.min(cols, count) - 1) * step + cell - 1;
		roundCorner(graphics, x, y, 1);
		roundCorner(graphics, right, y, -1);
	}

	private static void roundCorner(GuiGraphicsExtractor graphics, int outer, int y, int inward) {
		int inner = outer + inward;
		graphics.fill(outer, y, outer + 1, y + 2, BookStyle.PAPER_FILL);
		graphics.fill(inner, y, inner + 1, y + 1, BookStyle.PAPER_FILL);
		graphics.fill(inner, y + 1, inner + 1, y + 2, OUTLINE_INK);
	}

	private static void paperButton(GuiGraphicsExtractor graphics, int x, int y, Face face, int field, int middle) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Scribe.id(BLANK + suffix(face)), x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
		miniSquare(graphics, x + FACE_X, y + FACE_Y + (face == Face.PRESSED ? 1 : 0), field, middle, face == Face.REST ? REST_FACE_ALPHA : 255);
	}

	private static void miniSquare(GuiGraphicsExtractor graphics, int x, int y, int field, int middle, int alpha) {
		int ink = ARGB.color(alpha, OUTLINE_INK);
		int last = SQUARE - 1;
		graphics.fill(x, y, x + SQUARE, y + 1, ink);
		graphics.fill(x, y + last, x + SQUARE, y + SQUARE, ink);
		graphics.fill(x, y + 1, x + 1, y + last, ink);
		graphics.fill(x + last, y + 1, x + SQUARE, y + last, ink);
		int f = ARGB.color(alpha, field);
		if (middle == 0) {
			graphics.fill(x + 1, y + 1, x + last, y + last, f);
			return;
		}
		int edge = ARGB.color(alpha, carvedEdge(field));
		graphics.fill(x + 1, y + 1, x + last, y + 2, f);
		graphics.fill(x + 1, y + 2, x + 2, y + last, f);
		graphics.fill(x + 2, y + last - 1, x + 3, y + last, f);
		graphics.fill(x + last - 1, y + 2, x + last, y + 3, f);
		graphics.fill(x + 3, y + last - 1, x + last, y + last, edge);
		graphics.fill(x + last - 1, y + 3, x + last, y + last - 1, edge);
		graphics.fill(x + 2, y + 2, x + last - 1, y + last - 1, ARGB.color(alpha, middle));
	}
}
