package scribe.mixin;

import scribe.binding.Binding;
import scribe.colors.ColorsConfig;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BookCloningRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BookCloningRecipe.class)
public abstract class BookCloningRecipeMixin {
	@ModifyReturnValue(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
	private ItemStack scribe$assemble(ItemStack result, @Local(argsOnly = true) CraftingInput input) {
		if (result.isEmpty()) {
			return result;
		}
		for (ItemStack stack : input.items()) {
			if (stack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
				return Binding.removeFrom(ColorsConfig.get().bookCloningStrategy().apply(result, stack));
			}
		}
		return Binding.removeFrom(result);
	}
}
