package scribe.mixin;

import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scribe.binding.Bookbinding;

@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneMenuMixin {
	@Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
	private void scribe$unbind(ItemStack input, ItemStack additional, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack book = Bookbinding.unbound(input, additional);
		if (book != null) {
			cir.setReturnValue(book);
		}
	}
}
