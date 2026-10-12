package scribe.client.mixin;

import scribe.access.BookPreviews;
import scribe.access.LecternAccess;
import scribe.client.LecternMotions;
import scribe.client.Looks;
import scribe.config.ScribeConfig;
import scribe.client.colors.ColorsClient;
import scribe.client.colors.BookLooks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.LecternRenderer;
import net.minecraft.client.renderer.blockentity.state.LecternRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LecternRenderer.class)
public abstract class LecternRendererMixin {
	@Shadow
	@Final
	private BookModel bookModel;

	@Shadow
	@Final
	private SpriteGetter sprites;

	@Shadow
	@Final
	private static BookModel.State BOOK_STATE;

	@Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/entity/LecternBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/LecternRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V", at = @At("RETURN"))
	private void scribe$extract(LecternBlockEntity lectern, LecternRenderState state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.CrumblingOverlay breakProgress, CallbackInfo ci) {
		FabricRenderState data = (FabricRenderState) state;
		LecternAccess access = (LecternAccess) lectern;
		ItemStack preview = ((BookPreviews) lectern).scribe$preview(0);
		data.setData(BookLooks.LECTERN, ColorsClient.LecternLook.of(preview, access.scribe$coverDye()));
		boolean closed = access.scribe$isClosed();
		boolean shownClosed = Looks.closedBooks();
		data.setData(BookLooks.SHUT_ON_LECTERN, closed && shownClosed);
		LecternMotions.Moment moment = LecternMotions.moment(lectern, closed, partialTicks);
		data.setData(LecternMotions.MOMENT, shownClosed || moment.open() >= 1.0F ? moment : LecternMotions.OPEN);
	}

	@Unique
	private static LecternMotions.Moment scribe$moment(LecternRenderState state) {
		FabricRenderState data = (FabricRenderState) state;
		LecternMotions.Moment moment = data.getData(LecternMotions.MOMENT);
		return moment != null ? moment : Boolean.TRUE.equals(data.getData(BookLooks.SHUT_ON_LECTERN)) ? LecternMotions.CLOSED : LecternMotions.OPEN;
	}

	@Unique
	private static BookModel.State scribe$bookState(LecternRenderState state, BookModel.State open) {
		LecternMotions.Moment moment = scribe$moment(state);
		return moment.state() != null ? moment.state() : moment.shut() ? ColorsClient.CLOSED_BOOK : open;
	}

	@ModifyExpressionValue(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/LecternRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/blockentity/LecternRenderer;BOOK_STATE:Lnet/minecraft/client/model/object/book/BookModel$State;", opcode = Opcodes.GETSTATIC))
	private BookModel.State scribe$closedBook(BookModel.State open, @Local(argsOnly = true) LecternRenderState state) {
		return scribe$bookState(state, open);
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/LecternRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;IIILnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/client/resources/model/sprite/SpriteGetter;I)V"))
	private void scribe$layClosedBook(LecternRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		LecternMotions.lay(poseStack, scribe$moment(state).open());
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/LecternRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
	private void scribe$submit(LecternRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		ColorsClient.LecternLook look = ((FabricRenderState) state).getData(BookLooks.LECTERN);
		if (!ScribeConfig.get().lecternLookShown() || look == null) {
			return;
		}
		LecternMotions.Moment moment = scribe$moment(state);
		BookModel.State book = scribe$bookState(state, BOOK_STATE);
		boolean shut = moment.shut();
		boolean front = moment.open() < 1.0F;
		ColorsClient.submitLecternLayers(collector, this.bookModel, book, poseStack, state.lightCoords, look, shut, front,
			this.sprites);
	}
}
