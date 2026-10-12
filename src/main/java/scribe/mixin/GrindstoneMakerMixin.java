package scribe.mixin;

import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scribe.EnchantedMaker;

@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneMakerMixin {
	@Inject(method = "computeResult", at = @At("RETURN"))
	private void scribe$plainBookHasNoMaker(ItemStack input, ItemStack additional, CallbackInfoReturnable<ItemStack> cir) {
		EnchantedMaker.ground(cir.getReturnValue());
	}
}
