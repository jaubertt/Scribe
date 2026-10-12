package scribe.client.mixin;

import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scribe.client.ink.InkPotsClient;

@Mixin(DisplayRenderer.class)
public abstract class InkDisplayOrientationMixin {
	@Inject(method = "calculateOrientation", at = @At("HEAD"), cancellable = true)
	private void scribe$inkOrientation(Display.RenderState renderState, DisplayEntityRenderState state, Quaternionf output,
		CallbackInfoReturnable<Quaternionf> cir) {
		if (InkPotsClient.drawnFixed(state)) {
			cir.setReturnValue(output.identity());
		}
	}
}
