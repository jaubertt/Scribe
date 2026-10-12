package scribe.client.mixin;

import scribe.client.BookScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "openItemGui", at = @At("HEAD"), cancellable = true)
	private void scribe$openEditor(ItemStack itemStack, InteractionHand hand, CallbackInfo ci) {
		if (BookScreens.openEditor((LocalPlayer) (Object) this, itemStack, hand)) {
			ci.cancel();
		}
	}
}
