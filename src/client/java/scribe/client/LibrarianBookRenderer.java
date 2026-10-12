package scribe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import scribe.client.colors.ColorsClient;
import scribe.config.ScribeConfig;

public final class LibrarianBookRenderer {
	public static final RenderStateDataKey<Held> BOOK = RenderStateDataKey.create(() -> "scribe:librarian_book");
	public static final float BOOK_SCALE = 1.0F;
	public static final float BOOK_Y = 5.0F;
	public static final float BOOK_Z = -12.5F;
	public static final float BOOK_TILT = 47.5F;
	public static final float HEAD_TILT = ReadingPoseRenderer.HEAD_TILT;

	public record Held(ColorsClient.LecternLook look, BookModel.State state, float open) {
		public boolean shut() {
			return this.open <= 0.0F;
		}
	}

	private LibrarianBookRenderer() {
	}

	public static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof VillagerRenderer villagers) {
				helper.register(new Layer(villagers, new BookModel(context.bakeLayer(ModelLayers.BOOK)), context.getSprites()));
			}
		});
	}

	public static void extract(Villager villager, VillagerRenderState state, float partialTicks) {
		FabricRenderState data = (FabricRenderState) state;
		LibrarianBooks.Reader reader = ScribeConfig.get().readingLibrariansShown() && !state.isBaby && state.heldItem.isEmpty()
			? LibrarianBooks.of(villager.getId()) : null;
		if (reader == null) {
			data.setData(BOOK, null);
			return;
		}
		Held held = held(reader.book(), reader.motion(), partialTicks);
		data.setData(BOOK, held);
		if (!state.isUnhappy) {
			state.xRot = headPitch(state.xRot, held.open());
			state.yRot = headYaw(state.yRot, held.open());
		}
	}

	public static Held held(ItemStack book, BookMotion motion, float partialTicks) {
		float open = motion.open(partialTicks);
		BookModel.State state = open <= 0.0F ? ColorsClient.CLOSED_BOOK : motion.state(partialTicks, ReadingPoseRenderer.OPENNESS);
		return new Held(ReadingPoseRenderer.look(book), state, open);
	}

	public static float headPitch(float own, float open) {
		return ReadingPoseRenderer.headPitch(own, open);
	}

	public static float headYaw(float own, float open) {
		return ReadingPoseRenderer.headYaw(own, open);
	}

	public static void placeBook(PoseStack poseStack) {
		placeBook(poseStack, BOOK_Y, BOOK_Z, BOOK_TILT, BOOK_SCALE);
	}

	public static void placeBook(PoseStack poseStack, float y, float z, float tilt, float scale) {
		poseStack.translate(0.0F, y / 16.0F, z / 16.0F);
		poseStack.rotateDegrees(Axis.XP, tilt);
		poseStack.rotateDegrees(Axis.YP, -90.0F);
		poseStack.scale(scale, scale, scale);
		poseStack.rotateDegrees(Axis.XP, 180.0F);
	}

	public static void submitBook(SubmitNodeCollector collector, BookModel model, Held book, PoseStack poseStack, int light, SpriteGetter sprites) {
		collector.submitModel(model, book.state(), poseStack, light, OverlayTexture.NO_OVERLAY, -1, EnchantTableRenderer.BOOK_TEXTURE, sprites, 0);
		if (!ScribeConfig.get().lecternLookShown()) {
			return;
		}
		ColorsClient.submitHeldLayers(collector, model, book.state(), poseStack, light, book.look(), book.shut(), sprites);
	}

	private static final class Layer extends RenderLayer<VillagerRenderState, VillagerModel> {
		private final BookModel model;
		private final SpriteGetter sprites;

		Layer(RenderLayerParent<VillagerRenderState, VillagerModel> parent, BookModel model, SpriteGetter sprites) {
			super(parent);
			this.model = model;
			this.sprites = sprites;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, VillagerRenderState state, float yRot, float xRot) {
			Held book = ((FabricRenderState) state).getData(BOOK);
			if (book == null || state.isInvisible) {
				return;
			}
			poseStack.pushPose();
			this.getParentModel().root().translateAndRotate(poseStack);
			placeBook(poseStack);
			LecternMotions.lay(poseStack, book.open());
			submitBook(collector, this.model, book, poseStack, light, this.sprites);
			poseStack.popPose();
		}
	}
}
