package scribe.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.world.entity.Display;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import scribe.client.colors.ColorsClient;
import scribe.client.placed.PlacedBooksClient;
import scribe.placed.PlacedBooks;

@Mixin(DisplayRenderer.ItemDisplayRenderer.class)
public abstract class ItemDisplayRendererMixin {
	@Unique
	private BookModel scribe$book;

	@Unique
	private SpriteGetter scribe$sprites;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void scribe$init(EntityRendererProvider.Context context, CallbackInfo ci) {
		this.scribe$book = new BookModel(context.bakeLayer(ModelLayers.BOOK));
		this.scribe$sprites = context.getSprites();
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Display$ItemDisplay;Lnet/minecraft/client/renderer/entity/state/ItemDisplayEntityRenderState;F)V",
		at = @At("RETURN"))
	private void scribe$extract(Display.ItemDisplay entity, ItemDisplayEntityRenderState state, float partialTicks, CallbackInfo ci) {
		Display.ItemDisplay.ItemRenderState item = entity.itemRenderState();
		boolean placed = item != null && state.renderState != null && PlacedBooks.isMarked(item.itemStack());
		ColorsClient.LecternLook look = placed ? PlacedBooksClient.look(item.itemStack()) : null;
		FabricRenderState data = (FabricRenderState) state;
		if (look != null || data.getData(PlacedBooksClient.LOOK) != null) {
			data.setData(PlacedBooksClient.LOOK, look);
			data.setData(PlacedBooksClient.FRAME, placed
				? PlacedBooksClient.frameOf(item.itemStack(), state.renderState.transformation().get(state.interpolationProgress)) : null);
		}
	}

	@Inject(method = "submitInner(Lnet/minecraft/client/renderer/entity/state/ItemDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
		at = @At("HEAD"), cancellable = true)
	private void scribe$submit(ItemDisplayEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, float progress,
		CallbackInfo ci) {
		FabricRenderState data = (FabricRenderState) state;
		ColorsClient.LecternLook look = data.getData(PlacedBooksClient.LOOK);
		Matrix4f frame = data.getData(PlacedBooksClient.FRAME);
		if (look == null || frame == null || this.scribe$book == null || state.renderState == null) {
			return;
		}
		Matrix4f pose = PlacedBooksClient.fromTransformation(state.renderState.transformation().get(progress).getMatrix(), frame);
		if (pose == null) {
			return;
		}
		poseStack.mulPose(pose);
		PlacedBooksClient.submitBook(collector, this.scribe$book, look, poseStack, lightCoords, state.outlineColor, this.scribe$sprites);
		ci.cancel();
	}
}
