package scribe.client.mixin;

import scribe.client.LecternMotions;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.LevelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelEventHandler.class)
public abstract class LevelEventHandlerMixin {
	@Inject(method = "levelEvent(ILnet/minecraft/core/BlockPos;I)V", at = @At("HEAD"))
	private void scribe$pageSound(int type, BlockPos pos, int data, CallbackInfo ci) {
		if (type == LevelEvent.SOUND_PAGE_TURN) {
			LecternMotions.pageSound(pos, data);
		}
	}
}
