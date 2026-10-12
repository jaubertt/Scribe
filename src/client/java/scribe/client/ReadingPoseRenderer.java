package scribe.client;

import scribe.client.colors.ColorsClient;
import scribe.colors.BookColors;
import scribe.config.ScribeConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class ReadingPoseRenderer {
	public static final RenderStateDataKey<Integer> READING = RenderStateDataKey.create(() -> "scribe:reading");
	public static final RenderStateDataKey<Held> BOOK = RenderStateDataKey.create(() -> "scribe:held_book");
	public static final RenderStateDataKey<Boolean> POSED = RenderStateDataKey.create(() -> "scribe:posed");
	public static final float BOOK_SCALE = 1.0F;
	public static final float OPENNESS = 0.8F;
	public static final float ARM_X_ROT = -1.1F;
	public static final float BOOK_Y = 0.38F;
	public static final float BOOK_Z = -0.75F;
	public static final float HAND_GRIP = 0.5F;
	private static final float[] TURNS = {
		0.2745F, 0.2670F, 0.2595F, 0.2525F, 0.2455F, 0.2385F, 0.2315F, 0.2250F, 0.2170F, 0.2020F,
		0.1875F, 0.1730F, 0.1585F, 0.1445F, 0.1305F, 0.1165F, 0.1030F, 0.0900F, 0.0770F, 0.0645F,
		0.0520F, 0.0400F, 0.0285F, 0.0175F, 0.0070F, -0.0045F, -0.0215F, -0.0400F, -0.0580F, -0.0750F,
		-0.0910F, -0.1060F, -0.1205F, -0.1340F, -0.1465F, -0.1580F, -0.1690F, -0.1795F, -0.1885F, -0.1970F,
		-0.2050F
	};
	public static final float ARM_Y_ROT = armTurn(BOOK_SCALE);

	public static float armTurn(float scale) {
		return armTurn(scale, 1.0F);
	}

	public static float closedArmTurn(float scale) {
		return armTurn(scale, 0.0F);
	}

	public static float armTurn(float scale, float open) {
		float at = Mth.clamp(open, 0.0F, 1.0F) * (TURNS.length - 1);
		int i = Math.min((int) at, TURNS.length - 2);
		return Mth.lerp(at - i, TURNS[i], TURNS[i + 1]);
	}

	private ReadingPoseRenderer() {
	}

	public static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				BookModel model = new BookModel(context.bakeLayer(ModelLayers.BOOK));
				helper.register(new Layer(avatar, model, context.getSprites()));
				FirstPersonBook.remember(model, context.getSprites());
			}
		});
	}

	public static int reading(Object state) {
		if (!ScribeConfig.get().readingPoseShown() || !(state instanceof FabricRenderState data)) {
			return 0;
		}
		Integer value = data.getData(READING);
		return value == null ? 0 : value;
	}

	public static void poseArms(ModelPart rightArm, ModelPart leftArm, float open) {
		float turn = armTurn(BOOK_SCALE, open);
		rightArm.xRot = ARM_X_ROT;
		rightArm.yRot = -turn;
		rightArm.zRot = 0.0F;
		leftArm.xRot = ARM_X_ROT;
		leftArm.yRot = turn;
		leftArm.zRot = 0.0F;
	}

	public static void poseArms(ModelPart body, ModelPart rightArm, ModelPart leftArm, float open) {
		poseArms(rightArm, leftArm, open);
		withBody(body, rightArm);
		withBody(body, leftArm);
	}

	public static boolean holdBook(Object state, ModelPart body, ModelPart rightArm, ModelPart leftArm) {
		if (reading(state) == 0) {
			return false;
		}
		poseArms(body, rightArm, leftArm, open(state));
		return true;
	}

	private static void withBody(ModelPart body, ModelPart arm) {
		PartPose made = arm.getInitialPose();
		PartPose bodyMade = body.getInitialPose();
		Vector3f shoulder = new Vector3f(made.x() - bodyMade.x(), made.y() - bodyMade.y(), made.z() - bodyMade.z());
		if (body.xRot != 0.0F || body.yRot != 0.0F || body.zRot != 0.0F) {
			Quaternionf lean = new Quaternionf().rotationZYX(body.zRot, body.yRot, body.xRot);
			Vector3f angles = new Quaternionf(lean).mul(new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot)).getEulerAnglesZYX(new Vector3f());
			arm.xRot = angles.x;
			arm.yRot = angles.y;
			arm.zRot = angles.z;
			lean.transform(shoulder);
		}
		arm.x = body.x + shoulder.x;
		arm.y = body.y + shoulder.y;
		arm.z = body.z + shoulder.z;
	}

	public static float open(Object state) {
		Held book = state instanceof FabricRenderState data ? data.getData(BOOK) : null;
		return book == null ? 1.0F : book.open();
	}

	public static void placeBook(PoseStack poseStack, float scale) {
		poseStack.translate(0.0F, BOOK_Y, BOOK_Z);
		poseStack.rotateDegrees(Axis.XP, 25.0F);
		poseStack.rotateDegrees(Axis.YP, -90.0F);
		poseStack.scale(scale, scale, scale);
		poseStack.rotateDegrees(Axis.XP, 180.0F);
	}

	public static ColorsClient.LecternLook look(ItemStack book) {
		return ColorsClient.LecternLook.of(book, BookColors.of(book));
	}

	public record Held(ColorsClient.LecternLook look, BookModel.State state, float open) {
		public boolean shut() {
			return this.open <= 0.0F;
		}
	}

	public static BookModel.State state(@Nullable BookMotion motion, float partialTicks) {
		return (motion == null ? new BookMotion(true) : motion).state(partialTicks, OPENNESS);
	}

	public static final float HEAD_TILT = 20.0F;

	public static float headPitch(float own, float open) {
		return Mth.lerp(open, own, HEAD_TILT);
	}

	public static float headYaw(float own, float open) {
		return Mth.lerp(open, own, 0.0F);
	}

	public static void submitBook(SubmitNodeCollector collector, BookModel model, Held book, PoseStack poseStack, int light, SpriteGetter sprites) {
		collector.submitModel(model, book.state(), poseStack, light, OverlayTexture.NO_OVERLAY, -1, EnchantTableRenderer.BOOK_TEXTURE, sprites, 0);
		if (!ScribeConfig.get().lecternLookShown()) {
			return;
		}
		ColorsClient.submitHeldLayers(collector, model, book.state(), poseStack, light, book.look(), book.shut(), sprites);
	}

	private static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		private final BookModel model;
		private final SpriteGetter sprites;

		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, BookModel model, SpriteGetter sprites) {
			super(parent);
			this.model = model;
			this.sprites = sprites;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
			Held book = ((FabricRenderState) state).getData(BOOK);
			if (reading(state) == 0 || state.isInvisible || book == null) {
				return;
			}
			poseStack.pushPose();
			this.getParentModel().body.translateAndRotate(poseStack);
			placeBook(poseStack, BOOK_SCALE);
			submitBook(collector, this.model, book, poseStack, light, this.sprites);
			poseStack.popPose();
		}
	}
}
