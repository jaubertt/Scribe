package scribe.client.mixin;

import scribe.colors.BookColors;
import scribe.colors.ColoredBlockEntity;
import scribe.config.ScribeConfig;
import scribe.client.colors.ColorsClient;
import scribe.client.colors.ColoredRenderState;
import scribe.client.colors.JebBook;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.blockentity.state.EnchantTableRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantTableRenderer.class)
public abstract class EnchantTableRendererMixin {
	@Shadow
	@Final
	private BookModel bookModel;

	@Shadow
	@Final
	private SpriteGetter sprites;

	@Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/entity/EnchantingTableBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/EnchantTableRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V", at = @At("RETURN"))
	private void scribe$extract(EnchantingTableBlockEntity table, EnchantTableRenderState state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.CrumblingOverlay breakProgress, CallbackInfo ci) {
		int color = JebBook.named(table.getCustomName()) ? JebBook.color(state.time) : ((ColoredBlockEntity) table).scribe$getColor(0);
		((ColoredRenderState) state).scribe$setColor(ScribeConfig.get().tableLookShown() ? color : BookColors.DEFAULT);
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/EnchantTableRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
	private void scribe$submit(EnchantTableRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		float flip1 = Mth.clamp(Mth.frac(state.flip + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
		float flip2 = Mth.clamp(Mth.frac(state.flip + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
		BookModel.State bookState = BookModel.State.forAnimation(state.time, flip1, flip2, state.open);
		ColorsClient.submitLeather(collector, this.bookModel, bookState, poseStack, state.lightCoords,
			((ColoredRenderState) state).scribe$getColor(), this.sprites);
	}
}
