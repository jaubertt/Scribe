package scribe.client.mixin;

import scribe.client.colors.ColoredRenderState;
import scribe.client.colors.GuiBookTint;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
	@ModifyArg(method = "book", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addPicturesInPictureState(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;)V"))
	private PictureInPictureRenderState scribe$tag(PictureInPictureRenderState state) {
		if (GuiBookTint.pending != -1 && state instanceof ColoredRenderState colored) {
			colored.scribe$setColor(GuiBookTint.pending);
		}
		return state;
	}
}
