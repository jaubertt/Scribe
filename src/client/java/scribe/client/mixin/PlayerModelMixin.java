package scribe.client.mixin;

import scribe.client.ReadingPoseRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerModel.class, priority = 1500)
public abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("RETURN"))
	private void scribe$readingPoseLast(AvatarRenderState state, CallbackInfo ci) {
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		ReadingPoseRenderer.holdBook(state, model.body, model.rightArm, model.leftArm);
	}
}
