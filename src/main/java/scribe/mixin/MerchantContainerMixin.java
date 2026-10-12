package scribe.mixin;

import scribe.colors.ColorsConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MerchantContainer.class)
public abstract class MerchantContainerMixin {
	@Shadow
	public abstract ItemStack getItem(int slot);

	@ModifyExpressionValue(method = "updateSellItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/trading/MerchantOffer;assemble()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack scribe$keepColor(ItemStack original) {
		if (!original.is(Items.ENCHANTED_BOOK)) {
			return original;
		}
		ItemStack first = this.getItem(0);
		ItemStack second = this.getItem(1);
		ItemStack book = first.is(Items.BOOK) ? first : second.is(Items.BOOK) ? second : null;
		return book == null ? original : ColorsConfig.get().villagerTradeStrategy().apply(original, book);
	}
}
