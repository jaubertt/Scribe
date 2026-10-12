package scribe.mixin;

import scribe.ClosedLecterns;
import scribe.LecternLocks;
import scribe.access.LecternMenuAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LecternMenu.class)
public abstract class LecternMenuMixin implements LecternMenuAccess {
	@Unique
	private @Nullable LecternBlockEntity scribe$lectern;
	@Unique
	private int scribe$pageBefore;

	@Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
	private void scribe$coverButtons(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
		LecternBlockEntity lectern = this.scribe$lectern;
		if (buttonId == LecternMenu.BUTTON_TAKE_BOOK && lectern != null && player instanceof ServerPlayer server && LecternLocks.refused(lectern, server)) {
			cir.setReturnValue(false);
		} else if (buttonId == ClosedLecterns.BUTTON_OPEN || buttonId == ClosedLecterns.BUTTON_CLOSE) {
			cir.setReturnValue(lectern != null && !lectern.isRemoved() && ClosedLecterns.button(lectern, (LecternMenu) (Object) this, player, buttonId));
		} else if (lectern != null) {
			this.scribe$pageBefore = lectern.getPage();
		}
	}

	@Inject(method = "clickMenuButton", at = @At("RETURN"))
	private void scribe$readingOpens(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
		LecternBlockEntity lectern = this.scribe$lectern;
		if (lectern != null && !lectern.isRemoved() && cir.getReturnValueZ() && ClosedLecterns.turnsPage(buttonId)) {
			ClosedLecterns.read(lectern, player, lectern.getPage() != this.scribe$pageBefore);
		}
	}

	@Override
	public @Nullable LecternBlockEntity scribe$lectern() {
		return this.scribe$lectern;
	}

	@Override
	public void scribe$setLectern(LecternBlockEntity lectern) {
		this.scribe$lectern = lectern;
	}
}
