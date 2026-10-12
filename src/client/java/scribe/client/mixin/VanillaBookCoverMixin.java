package scribe.client.mixin;

import scribe.client.colors.ColorsClient;
import scribe.client.colors.CoverTints;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({BookViewScreen.class, BookEditScreen.class, BookSignScreen.class})
public abstract class VanillaBookCoverMixin {
	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void scribe$dyedLeather(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
		int width, int height, int textureWidth, int textureHeight, Operation<Void> original) {
		Identifier top = texture;
		if (CoverTints.shown()) {
			CoverTints.paintLeather(graphics, pipeline, x, y, u, v, width, height, textureWidth, textureHeight, CoverTints.dyeOf(this));
			top = ColorsClient.GUI_BOOK_OVERLAY;
		}
		original.call(graphics, pipeline, top, x, y, u, v, width, height, textureWidth, textureHeight);
	}
}
