package scribe.client.colors;

import scribe.config.ScribeConfig;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface ColoredBookScreen {
	void scribe$setColor(int color);

	static boolean dyed() {
		return ScribeConfig.get().tintShown();
	}

	static void drawCover(GuiGraphicsExtractor graphics, RenderPipeline pipeline, int x, int y, float u, float v, int width, int height,
		int textureWidth, int textureHeight, int color) {
		graphics.blit(pipeline, ColorsClient.GUI_BOOK, x, y, u, v, width, height, textureWidth, textureHeight, color);
		graphics.blit(pipeline, ColorsClient.GUI_BOOK_OVERLAY, x, y, u, v, width, height, textureWidth, textureHeight);
	}
}
