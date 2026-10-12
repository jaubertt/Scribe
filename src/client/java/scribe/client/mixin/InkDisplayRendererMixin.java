package scribe.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import scribe.client.ink.InkPotsClient;

@Mixin(DisplayRenderer.ItemDisplayRenderer.class)
public abstract class InkDisplayRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Display$ItemDisplay;Lnet/minecraft/client/renderer/entity/state/ItemDisplayEntityRenderState;F)V",
		at = @At("RETURN"))
	private void scribe$inkExtract(Display.ItemDisplay entity, ItemDisplayEntityRenderState state, float partialTicks, CallbackInfo ci) {
		InkPotsClient.extract(entity, state);
	}

	@Inject(method = "submitInner(Lnet/minecraft/client/renderer/entity/state/ItemDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
		at = @At("HEAD"))
	private void scribe$inkSubmit(ItemDisplayEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, float progress,
		CallbackInfo ci) {
		InkPotsClient.submit(state, poseStack, progress);
	}
}
