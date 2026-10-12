package scribe.client.mixin;

import scribe.colors.BookColors;
import scribe.client.colors.ColoredBookScreen;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BookViewScreen.class)
public abstract class BookViewScreenMixin implements ColoredBookScreen {
	@Unique
	private int scribe$color = BookColors.DEFAULT;

	@Override
	public void scribe$setColor(int color) {
		this.scribe$color = color;
	}

	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void scribe$coloredCover(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
		int width, int height, int textureWidth, int textureHeight, Operation<Void> original) {
		if (ColoredBookScreen.dyed()) {
			ColoredBookScreen.drawCover(graphics, pipeline, x, y, u, v, width, height, textureWidth, textureHeight, this.scribe$color);
		} else {
			original.call(graphics, pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
		}
	}
}
