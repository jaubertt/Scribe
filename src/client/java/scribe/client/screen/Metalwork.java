package scribe.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import scribe.Scribe;
import scribe.binding.Bookbinding;
import scribe.binding.CoverDesign;

public final class Metalwork {
	static final int STUD_X = 90;
	static final int STUD_Y = 84;
	static final int STUD_WIDTH = 44;
	static final int STUD_HEIGHT = 50;
	static final int BUCKLE_X = 84;
	static final int BUCKLE_Y = 84;
	static final int BUCKLE_WIDTH = 56;
	static final int BUCKLE_HEIGHT = 50;
	static final int STRAP_END_X = 44;
	static final int STRAP_END_Y = 86;
	static final int STRAP_END_WIDTH = 36;
	static final int STRAP_END_HEIGHT = 44;
	public static final int MINI = 11;
	public static final int MINI_STRAP_TOP = 3;
	public static final int MINI_STRAP_BOTTOM = 7;

	static final int PLAQUE_TOP = 22;
	static final int PLAQUE_BOTTOM = 87;
	static final int PLAQUE_FRAME = 5;
	static final int PLAQUE_PAD_X = 6;
	static final int PLAQUE_PAD_Y = 3;
	public static final int PLAQUE_TEXT_WIDTH = CoverView.TEXT_WIDTH - 4;
	public static final int PLAQUE_TITLE_LINES = 2;
	public static final int PLAQUE_LINES = 4;
	static final int LAST_LINE = 9;
	static final int[] PLAQUE_TITLE = {0xFF656156, 0xFF657070, 0xFF1A1616, 0xFF520D06, 0xFF5F2B18, 0xFF803503, 0xFF09631B, 0xFF076578, 0xFF0C285A,
		0xFF361C6A, 0xFFC7490A};
	static final int[] PLAQUE_SUBTITLE = {0xFF908E80, 0xFF717D7D, 0xFF231E1E, 0xFF650B01, 0xFF6D3420, 0xFFA0450A, 0xFF0E7222, 0xFF0C788D, 0xFF112E63,
		0xFF422776, 0xFFDB5F10};
	static final int[] PLAQUE_EDGE = {0xFFF2EFED, 0xFFC5D2D4, 0xFF5A575A, 0xFFE62008, 0xFFE3826C, 0xFFFFFD90, 0xFF82F6AD, 0xFFCBFFF5, 0xFF416E97,
		0xFFC98FF3, 0xFFFFC354};

	public record Plaque(int x, int y, int width, int height) {
		public int textTop() {
			return this.y + PLAQUE_FRAME + PLAQUE_PAD_Y;
		}
	}

	private Metalwork() {
	}

	public static boolean plaque(@Nullable String design) {
		return CoverDesign.PLAQUE.equals(design);
	}

	static boolean strapEnd(String design) {
		return "ornate".equals(design) || plaque(design);
	}

	static Identifier sprite(String design, String metal) {
		return Scribe.id("gui/cover/metalwork/" + (plaque(design) ? "ornate" : design) + "_" + metal);
	}

	static Identifier strapEndSprite(String metal) {
		return Scribe.id("gui/cover/metalwork/strap_end_" + metal);
	}

	public static Identifier miniSprite(String design, String metal) {
		return Scribe.id("gui/cover/metalwork/mini_" + design + "_" + metal);
	}

	static Identifier plaqueSprite(String metal) {
		return Scribe.id("cover/plaque/" + metal);
	}

	static int ink(int[] inks, String metal) {
		int index = Bookbinding.MATERIALS.indexOf(metal);
		return inks[index < 0 ? Bookbinding.MATERIALS.indexOf("gold") : index];
	}

	public static int titleInk(String metal) {
		return ink(PLAQUE_TITLE, metal);
	}

	public static int subtitleInk(String metal) {
		return ink(PLAQUE_SUBTITLE, metal);
	}

	public static int edge(String metal) {
		return ink(PLAQUE_EDGE, metal);
	}

	public static @Nullable Plaque plaque(int widest, int titleLines, int subtitleLines) {
		if (titleLines <= 0 && subtitleLines <= 0) {
			return null;
		}
		int block = 0;
		if (titleLines > 0) {
			block = (titleLines - 1) * CoverView.TITLE_LINE + LAST_LINE;
		}
		if (subtitleLines > 0) {
			block += (titleLines > 0 ? CoverView.TITLE_LINE - LAST_LINE + CoverView.SUBTITLE_GAP : 0) + (subtitleLines - 1) * CoverView.SUBTITLE_LINE
				+ LAST_LINE;
		}
		int width = widest + 2 * (PLAQUE_FRAME + PLAQUE_PAD_X);
		int height = block + 2 * (PLAQUE_FRAME + PLAQUE_PAD_Y);
		return new Plaque(CoverView.TEXT_CENTER - Math.floorDiv(width, 2), PLAQUE_TOP + Math.floorDiv(PLAQUE_BOTTOM - PLAQUE_TOP + 1 - height, 2), width,
			height);
	}

	static void drawPlaque(GuiGraphicsExtractor graphics, int x, int y, Plaque plaque, String metal) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, plaqueSprite(metal), x + plaque.x(), y + plaque.y(), plaque.width(), plaque.height());
	}

	public static void draw(GuiGraphicsExtractor graphics, int x, int y, String design, String metal) {
		if (strapEnd(design)) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, strapEndSprite(metal), x + STRAP_END_X, y + STRAP_END_Y, STRAP_END_WIDTH, STRAP_END_HEIGHT);
		}
		if ("stud".equals(design)) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(design, metal), x + STUD_X, y + STUD_Y, STUD_WIDTH, STUD_HEIGHT);
		} else {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(design, metal), x + BUCKLE_X, y + BUCKLE_Y, BUCKLE_WIDTH, BUCKLE_HEIGHT);
		}
	}
}
