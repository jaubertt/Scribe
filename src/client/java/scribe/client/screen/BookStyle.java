package scribe.client.screen;

import scribe.Scribe;
import scribe.binding.CoverDesign;
import scribe.client.colors.EnchantColors;
import scribe.colors.BookColors;
import scribe.config.ScribeConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class BookStyle {
	public static final Identifier BOOK_TEXTURE = Scribe.id("textures/gui/book.png");
	public static final int TEXTURE_SIZE = 512;
	public static final Identifier VANILLA_BOOK = Identifier.withDefaultNamespace("textures/gui/book.png");
	public static final Identifier SINGLE_PAGE = Scribe.id("textures/gui/single_page.png");

	public static final int INK = 0xFF7B593D;
	public static final int INK_SOFT = 0xFFB49C7A;
	public static final int INK_HOVER = 0xFFB2825A;
	public static final int PAGE_NUMBER = 0xFFC9B48E;
	public static final int INK_RED = 0xFFA0402A;
	public static final int COVER_UNDYED = 0xFF99452E;

	public static final int PAPER_EDGE = 0xFF613C1E;
	public static final int PAPER_FILL = 0xFFFFFAEE;
	public static final int PAPER_LIGHT = 0xFFFBF2DA;
	public static final int PAPER_SHADE = 0xFFB09374;
	public static final int PAPER_HOVER = 0xFFEFE0BE;
	public static final int FIELD_EDGE = 0xFF9C8566;
	public static final int FIELD_FILL = 0xFFFFFDF6;

	private BookStyle() {
	}

	public static int cover(ItemStack stack) {
		if (!ScribeConfig.get().tintShown()) {
			return COVER_UNDYED;
		}
		int cover = stack.has(DataComponents.DYED_COLOR) ? ARGB.opaque(BookColors.of(stack)) : COVER_UNDYED;
		return ARGB.opaque(EnchantColors.get().bookColor(stack, cover));
	}

	public static int dye(DyeColor color) {
		return ARGB.opaque(color.getTextureDiffuseColor());
	}

	public static void book(GuiGraphicsExtractor graphics, int x, int y, int u, int v, int width, int height, int tint) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, BOOK_TEXTURE, x, y, u, v, width, height, TEXTURE_SIZE, TEXTURE_SIZE, tint);
	}

	public static void singlePage(GuiGraphicsExtractor graphics, int x, int y, int cover) {
		if (cover == COVER_UNDYED) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, VANILLA_BOOK, x, y, 20, 1, BookGeometry.SINGLE_WIDTH, BookGeometry.HEIGHT, 256, 256);
			return;
		}
		int h = BookGeometry.HEIGHT;
		graphics.blit(RenderPipelines.GUI_TEXTURED, SINGLE_PAGE, x, y, 0, 0, BookGeometry.SINGLE_WIDTH, h, BookGeometry.SINGLE_WIDTH, 2 * h, cover);
		graphics.blit(RenderPipelines.GUI_TEXTURED, SINGLE_PAGE, x, y, 0, h, BookGeometry.SINGLE_WIDTH, h, BookGeometry.SINGLE_WIDTH, 2 * h);
	}

	public static final int CORNER_SIZE = 16;
	public static final int DESIGN_SIZE = 14;

	public record Piece(Identifier id, int x, int y, int width, int height) {
	}

	public static @Nullable String corner(ItemStack stack) {
		String corner = CoverDesign.of(stack).corner();
		return CoverDesign.isCornerChoice(corner) ? corner : null;
	}

	public static void corners(GuiGraphicsExtractor graphics, int x, int y, int width, boolean spread, String material, @Nullable String corner, int cover) {
		int tint = vanillaPage(spread, cover) ? -1 : cover;
		for (Piece square : squares(width, spread, corner, cover)) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, square.id(), x + square.x(), y + square.y(), 0, 0, square.width(), square.height(),
				square.width(), square.height(), tint);
		}
		for (Piece metal : cornerPieces(width, spread, material, corner)) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, metal.id(), x + metal.x(), y + metal.y(), metal.width(), metal.height());
		}
	}

	static List<Piece> cornerPieces(int width, boolean spread, String material, @Nullable String corner) {
		if (CoverDesign.NO_CORNERS.equals(corner)) {
			return List.of();
		}
		int size = corner != null ? DESIGN_SIZE : CORNER_SIZE;
		List<Piece> pieces = new ArrayList<>(4);
		for (String at : outerCorners(spread)) {
			String name = corner != null ? "gui/corner/" + corner + "_open_" + at : "gui/open/corner_" + at;
			pieces.add(placed(Scribe.id(name + "_" + material), at, width, size));
		}
		return pieces;
	}

	static List<Piece> squares(int width, boolean spread, @Nullable String corner, int cover) {
		if (corner == null || CoverDesign.NO_CORNERS.equals(corner)) {
			return List.of();
		}
		String frame = spread ? "spread" : vanillaPage(spread, cover) ? "vanilla" : "single";
		List<Piece> pieces = new ArrayList<>(4);
		for (String at : outerCorners(spread)) {
			pieces.add(placed(Scribe.id("textures/gui/corner/square/" + frame + "_" + at + ".png"), at, width, DESIGN_SIZE));
		}
		return pieces;
	}

	static boolean vanillaPage(boolean spread, int cover) {
		return !spread && cover == COVER_UNDYED;
	}

	private static List<String> outerCorners(boolean spread) {
		return spread ? List.of("tl", "bl", "tr", "br") : List.of("tr", "br");
	}

	private static Piece placed(Identifier id, String corner, int width, int size) {
		return new Piece(id, corner.charAt(1) == 'l' ? 0 : width - size, corner.charAt(0) == 't' ? 0 : BookGeometry.HEIGHT - size, size, size);
	}

	public static void paper(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		graphics.fill(x, y, x + width, y + height, PAPER_EDGE);
		graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PAPER_FILL);
		graphics.fill(x + 1, y + 1, x + width - 1, y + 2, PAPER_LIGHT);
		graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, PAPER_SHADE);
	}

	public static void field(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		graphics.fill(x, y, x + width, y + height, FIELD_EDGE);
		graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, FIELD_FILL);
	}

	public static void text(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
		graphics.text(font, text, x, y, color, false);
	}

	public static void centered(GuiGraphicsExtractor graphics, Font font, String text, int centerX, int y, int color) {
		graphics.text(font, text, centerX - font.width(text) / 2, y, color, false);
	}

	public static void centered(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, int color) {
		graphics.text(font, text, centerX - font.width(text) / 2, y, color, false);
	}

	public static int darker(int color) {
		return ARGB.color(255, ARGB.red(color) * 3 / 5, ARGB.green(color) * 3 / 5, ARGB.blue(color) * 3 / 5);
	}

	public static Identifier sprite(String name) {
		return Scribe.id(name);
	}
}
