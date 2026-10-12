package scribe.client.mixin;

import scribe.client.BookMotion;
import scribe.client.ReadingPose;
import scribe.client.ReadingPoseRenderer;
import scribe.client.command.PoseCommand;
import scribe.config.ScribeConfig;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void scribe$reading(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		int reading = ScribeConfig.get().readingPoseShown() && entity instanceof LivingEntity ? ReadingPose.stateOf(entity) : 0;
		FabricRenderState data = (FabricRenderState) state;
		ItemStack book = ItemStack.EMPTY;
		if (reading != 0) {
			book = ((LivingEntity) entity).getItemInHand(reading == 2 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
			if (!ReadingPose.reads(entity) && !PoseCommand.opensWithPose(book)) {
				reading = 0;
			}
		}
		data.setData(ReadingPoseRenderer.READING, reading);
		data.setData(ReadingPoseRenderer.POSED, reading != 0 && ReadingPose.posed(entity));
		if (reading == 0) {
			data.setData(ReadingPoseRenderer.BOOK, null);
			return;
		}
		BookMotion motion = ReadingPose.motionOf(entity);
		float bookTicks = ReadingPose.partialTick(entity, partialTicks);
		float open = motion == null ? 1.0F : motion.open(bookTicks);
		data.setData(ReadingPoseRenderer.BOOK, new ReadingPoseRenderer.Held(ReadingPoseRenderer.look(book),
			ReadingPoseRenderer.state(motion, bookTicks), open));
		state.xRot = ReadingPoseRenderer.headPitch(state.xRot, open);
		state.yRot = ReadingPoseRenderer.headYaw(state.yRot, open);
		HumanoidArm arm = reading == 1 ? state.mainArm : state.mainArm.getOpposite();
		if (arm == HumanoidArm.RIGHT) {
			state.rightHandItemState.clear();
		} else {
			state.leftHandItemState.clear();
		}
		state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
		state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
	}
}
