package scribe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import scribe.client.mixin.FirstPersonHandsAndItemsAccessor;

public final class FirstPersonBook {
	static final float PLAYER_SCALE = 0.9375F;
	static final float MODEL_TOP = 1.501F;
	static final float EYE_Y = -(Avatar.DEFAULT_EYE_HEIGHT / PLAYER_SCALE - MODEL_TOP);
	static final float BOOK_MIDDLE = 2.0F;
	static final float MAP_DISTANCE = 0.72F;
	static final float MAP_RAISE = 0.04F;
	static final float MAP_DROP = 0.5F;
	static final float EQUIP_DROP = 1.2F;
	static final float HAND_Z_ROT = 0.1F;

	public record Placement(float far, float lower) {
	}

	public static final Placement BUILT = new Placement(1.0F, 0.0F);

	private static final Vector3f EYE_TO_MIDDLE = eyeToMiddle();
	static final float MIDDLE_BELOW = (float) Math.toDegrees(Math.atan2(-EYE_TO_MIDDLE.y, -EYE_TO_MIDDLE.z));

	private static @Nullable BookModel model;
	private static @Nullable SpriteGetter sprites;
	private static boolean shown;

	private FirstPersonBook() {
	}

	static void remember(BookModel bookModel, SpriteGetter spriteGetter) {
		model = bookModel;
		sprites = spriteGetter;
	}

	static void forget() {
		shown = false;
	}

	public static boolean draws(int reading, ReadingPoseRenderer.@Nullable Held book, boolean posed, boolean scoping) {
		return reading != 0 && posed && book != null && !scoping;
	}

	public static boolean shows(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state) {
		AvatarRenderState avatar = playerState.avatarRenderState;
		ReadingPoseRenderer.Held book = avatar instanceof FabricRenderState data ? data.getData(ReadingPoseRenderer.BOOK) : null;
		return avatar != null && state.handRenderSelection != null && model != null && sprites != null
			&& draws(ReadingPoseRenderer.reading(avatar), book, posed(avatar), state.isScoping);
	}

	static boolean posed(@Nullable AvatarRenderState avatar) {
		return avatar instanceof FabricRenderState data && Boolean.TRUE.equals(data.getData(ReadingPoseRenderer.POSED));
	}

	static boolean screenHolds(PlayerRenderState playerState) {
		AvatarRenderState avatar = playerState.avatarRenderState;
		return avatar != null && ReadingPoseRenderer.reading(avatar) != 0 && !posed(avatar);
	}

	public static InteractionHand hand(int reading) {
		return reading == 2 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
	}

	public static boolean submit(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
		FirstPersonHandsAndItemsRenderState state) {
		if (!shows(playerState, state)) {
			if (shown) {
				shown = false;
				if (!screenHolds(playerState)) {
					lower(state, InteractionHand.MAIN_HAND);
					lower(state, InteractionHand.OFF_HAND);
				}
			}
			return false;
		}
		AvatarRenderState avatar = playerState.avatarRenderState;
		ReadingPoseRenderer.Held book = ((FabricRenderState) avatar).getData(ReadingPoseRenderer.BOOK);
		InteractionHand hand = hand(ReadingPoseRenderer.reading(avatar));
		if (!shown) {
			shown = true;
			lower(state, hand);
		}
		AvatarRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(avatar);
		Identifier skin = avatar.skin.body().texturePath();
		draw(poseStack, collector, avatar, book, hand, state, partialTicks, BUILT, model, sprites, renderer.getModel(), (pose, right) -> {
			if (right) {
				renderer.renderRightHand(pose, collector, avatar.lightCoords, skin, avatar.showRightSleeve);
			} else {
				renderer.renderLeftHand(pose, collector, avatar.lightCoords, skin, avatar.showLeftSleeve);
			}
		});
		return true;
	}

	@FunctionalInterface
	public interface Hands {
		void draw(PoseStack poseStack, boolean right);
	}

	public static void draw(PoseStack poseStack, SubmitNodeCollector collector, AvatarRenderState avatar, ReadingPoseRenderer.Held book,
		InteractionHand hand, FirstPersonHandsAndItemsRenderState state, float partialTicks, Placement where, BookModel bookModel,
		SpriteGetter spriteGetter, PlayerModel arms, Hands hands) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.XP, (state.viewXRot - state.xBob) * 0.1F);
		poseStack.rotateDegrees(Axis.YP, (state.viewYRot - state.yBob) * 0.1F);
		boolean main = hand == InteractionHand.MAIN_HAND;
		float height = main ? Mth.lerp(partialTicks, state.oldMainHandHeight, state.mainHandHeight)
			: Mth.lerp(partialTicks, state.oldOffHandHeight, state.offHandHeight);
		float inverseHeight = (main ? state.mainHandSwapScale : state.offHandSwapScale) * (1.0F - height);
		place(poseStack, state.viewXRot, inverseHeight, avatar.swingAnimation, where);
		if (!avatar.isInvisible) {
			for (boolean right : new boolean[] {true, false}) {
				poseStack.pushPose();
				poseHand(poseStack, right ? arms.rightArm : arms.leftArm, right, book.open());
				hands.draw(poseStack, right);
				poseStack.popPose();
			}
		}
		poseStack.pushPose();
		ReadingPoseRenderer.placeBook(poseStack, ReadingPoseRenderer.BOOK_SCALE);
		ReadingPoseRenderer.submitBook(collector, bookModel, book, poseStack, avatar.lightCoords, spriteGetter);
		poseStack.popPose();
		poseStack.popPose();
	}

	public static void place(PoseStack poseStack, float viewXRot, float inverseArmHeight, float attack, Placement where) {
		float sqrtAttack = Mth.sqrt(attack);
		float ySwing = -0.2F * Mth.sin(attack * Mth.PI);
		float zSwing = -0.4F * Mth.sin(sqrtAttack * Mth.PI);
		poseStack.translate(0.0F, -ySwing / 2.0F, zSwing);
		poseStack.translate(0.0F, inverseArmHeight * -EQUIP_DROP, 0.0F);
		poseStack.rotateDegrees(Axis.XP, lookDown(viewXRot, where));
		Vector3f middle = new Vector3f(EYE_TO_MIDDLE).normalize(distance(viewXRot, where));
		poseStack.translate(middle.x, middle.y, middle.z);
		poseStack.rotateDegrees(Axis.XP, Mth.sin(sqrtAttack * Mth.PI) * 20.0F);
		poseStack.translate(-EYE_TO_MIDDLE.x, -EYE_TO_MIDDLE.y, -EYE_TO_MIDDLE.z);
		poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
		poseStack.rotateDegrees(Axis.ZP, 180.0F);
		poseStack.translate(0.0F, -EYE_Y, 0.0F);
	}

	public static float lookDown(float viewXRot, Placement where) {
		float mapBelow = (float) Math.toDegrees(Math.atan2(-mapHeight(viewXRot), MAP_DISTANCE));
		return MIDDLE_BELOW - mapBelow - where.lower();
	}

	public static float distance(float viewXRot, Placement where) {
		float height = mapHeight(viewXRot);
		return where.far() * (float) Math.sqrt(MAP_DISTANCE * MAP_DISTANCE + height * height);
	}

	public static float mapHeight(float viewXRot) {
		return MAP_RAISE - MAP_DROP * mapTilt(viewXRot);
	}

	public static float mapTilt(float xRot) {
		float tilt = Mth.clamp(1.0F - xRot / 45.0F + 0.1F, 0.0F, 1.0F);
		return -Mth.cos(tilt * Mth.PI) * 0.5F + 0.5F;
	}

	public static void poseHand(PoseStack poseStack, ModelPart arm, boolean right, float open) {
		PartPose made = arm.getInitialPose();
		float turn = ReadingPoseRenderer.armTurn(ReadingPoseRenderer.BOOK_SCALE, open);
		poseStack.translate(made.x() / 16.0F, made.y() / 16.0F, made.z() / 16.0F);
		poseStack.rotate(new Quaternionf().rotationZYX(0.0F, right ? -turn : turn, ReadingPoseRenderer.ARM_X_ROT));
		poseStack.rotate(new Quaternionf().rotationZYX(right ? HAND_Z_ROT : -HAND_Z_ROT, made.yRot(), made.xRot()).conjugate());
		poseStack.translate(-made.x() / 16.0F, -made.y() / 16.0F, -made.z() / 16.0F);
	}

	static void lower(FirstPersonHandsAndItemsRenderState state, InteractionHand hand) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft == null ? null : minecraft.player;
		FirstPersonHandsAndItemsAccessor hands = player == null ? null : (FirstPersonHandsAndItemsAccessor) player.firstPersonHandsAndItems();
		if (hand == InteractionHand.MAIN_HAND) {
			state.mainHandHeight = 0.0F;
			state.oldMainHandHeight = 0.0F;
			if (hands != null) {
				hands.scribe$setMainHandHeight(0.0F);
				hands.scribe$setOldMainHandHeight(0.0F);
			}
		} else {
			state.offHandHeight = 0.0F;
			state.oldOffHandHeight = 0.0F;
			if (hands != null) {
				hands.scribe$setOffHandHeight(0.0F);
				hands.scribe$setOldOffHandHeight(0.0F);
			}
		}
	}

	private static Vector3f eyeToMiddle() {
		PoseStack book = new PoseStack();
		ReadingPoseRenderer.placeBook(book, ReadingPoseRenderer.BOOK_SCALE);
		Vector3f middle = book.last().pose().transformPosition(BOOK_MIDDLE / 16.0F, 0.0F, 0.0F, new Vector3f());
		return new Vector3f(-middle.x, -(middle.y - EYE_Y), middle.z).mul(PLAYER_SCALE);
	}
}
