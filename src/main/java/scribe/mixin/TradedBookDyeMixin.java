package scribe.mixin;

import scribe.colors.DyeCarry;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantContainer.class)
public abstract class TradedBookDyeMixin {
	@Shadow
	public abstract ItemStack getItem(int slot);

	@Inject(method = "updateSellItem", at = @At("TAIL"))
	private void scribe$dyeFromPayment(CallbackInfo ci) {
		DyeCarry.traded(this.getItem(2), this.getItem(0), this.getItem(1));
	}
}
