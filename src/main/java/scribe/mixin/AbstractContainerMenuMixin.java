package scribe.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import scribe.LecternLocks;
import scribe.access.LecternMenuAccess;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
	@Inject(method = "removed", at = @At("HEAD"))
	private void scribe$lecternPenBack(Player player, CallbackInfo ci) {
		if ((Object) this instanceof LecternMenu && player instanceof ServerPlayer server) {
			LecternBlockEntity lectern = ((LecternMenuAccess) this).scribe$lectern();
			if (lectern != null) {
				LecternLocks.released(lectern, server);
			}
		}
	}
}
