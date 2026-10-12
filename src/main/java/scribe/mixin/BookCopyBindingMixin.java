package scribe.mixin;

import scribe.binding.Binding;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BookCloningRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BookCloningRecipe.class)
public abstract class BookCopyBindingMixin {
	@ModifyReturnValue(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
	private ItemStack scribe$copiesComeUnbound(ItemStack copy) {
		return Binding.removeFrom(copy);
	}
}
