package scribe.client.mixin;

import scribe.client.colors.ColoredRenderState;
import scribe.client.colors.GuiBookTint;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.pip.GuiBookModelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.GuiBookModelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiBookModelRenderer.class)
public abstract class GuiBookModelRendererMixin {
	@Inject(method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiBookModelRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V", at = @At("TAIL"))
	private void scribe$tint(GuiBookModelRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
		int color = ((ColoredRenderState) (Object) state).scribe$getColor();
		GuiBookTint.submit(collector, state.bookModel(), state.open(), state.flip(), poseStack, color);
	}
}
