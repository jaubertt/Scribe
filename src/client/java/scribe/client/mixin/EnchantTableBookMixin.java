package scribe.client.mixin;

import scribe.client.colors.BookLooks;
import scribe.client.colors.ColorsClient;
import scribe.client.colors.GuiBookTint;
import scribe.client.colors.TableLeather;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.blockentity.state.EnchantTableRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantTableRenderer.class)
public abstract class EnchantTableBookMixin {
	@Shadow
	@Final
	private BookModel bookModel;

	@Shadow
	@Final
	private SpriteGetter sprites;

	@Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/entity/EnchantingTableBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/EnchantTableRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V", at = @At("TAIL"))
	private void scribe$pickLeather(EnchantingTableBlockEntity table, EnchantTableRenderState state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.CrumblingOverlay breakProgress, CallbackInfo ci) {
		((FabricRenderState) state).setData(BookLooks.TABLE_LEATHER, TableLeather.inWorld(table, state.time));
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/EnchantTableRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
	private void scribe$coverInLeather(EnchantTableRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
		CallbackInfo ci) {
		Integer leather = ((FabricRenderState) state).getData(BookLooks.TABLE_LEATHER);
		if (leather != null) {
			ColorsClient.submitLeather(collector, this.bookModel, GuiBookTint.bookState(state.time, state.open, state.flip), poseStack,
				state.lightCoords, leather, this.sprites);
		}
	}
}
