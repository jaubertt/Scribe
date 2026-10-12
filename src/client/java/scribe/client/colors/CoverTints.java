package scribe.client.colors;

import scribe.colors.BookColors;
import scribe.config.ScribeConfig;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class CoverTints {
	private static final Map<Object, Integer> OPENED_WITH = new WeakHashMap<>();

	private CoverTints() {
	}

	public static void remember(Object screen, int dye) {
		OPENED_WITH.put(screen, dye);
	}

	public static int dyeOf(Object screen) {
		if (screen instanceof HeldBook held) {
			return BookColors.of(held.scribe$heldBook());
		}
		return OPENED_WITH.getOrDefault(screen, BookColors.DEFAULT);
	}

	public static boolean shown() {
		return ScribeConfig.get().tintShown();
	}

	public static void paintLeather(GuiGraphicsExtractor graphics, RenderPipeline pipeline, int x, int y, float u, float v, int width, int height,
		int textureWidth, int textureHeight, int dye) {
		graphics.blit(pipeline, ColorsClient.GUI_BOOK, x, y, u, v, width, height, textureWidth, textureHeight, dye);
	}
}
