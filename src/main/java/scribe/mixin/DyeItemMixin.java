package scribe.mixin;

import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import scribe.ink.InkPots;

@Mixin(DyeItem.class)
public abstract class DyeItemMixin implements FabricItem {
	@Override
	public @Nullable ItemStackTemplate getCraftingRemainder(ItemStack stack) {
		ItemStackTemplate bottle = InkPots.remainder(stack);
		return bottle != null ? bottle : ((Item) (Object) this).getCraftingRemainder();
	}
}
