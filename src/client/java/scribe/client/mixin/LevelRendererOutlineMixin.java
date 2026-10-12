package scribe.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import scribe.client.ink.InkPotsClient;
import scribe.client.placed.PlacedBooksClient;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererOutlineMixin {
	@Inject(method = "submitBlockOutline", at = @At("HEAD"))
	private void scribe$placedBookOutline(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, LevelRenderState levelRenderState, CallbackInfo ci) {
		PlacedBooksClient.submitOutline(poseStack, submitNodeCollector, levelRenderState);
		InkPotsClient.submitOutline(poseStack, submitNodeCollector, levelRenderState);
	}
}
