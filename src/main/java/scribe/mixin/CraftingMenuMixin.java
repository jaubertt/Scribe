package scribe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import scribe.colors.AuthorDyeing;

@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
	@WrapOperation(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;)Lnet/minecraft/world/item/ItemStack;"))
	private static ItemStack scribe$authorDyes(CraftingRecipe recipe, RecipeInput input, Operation<ItemStack> original, @Local(argsOnly = true) Player player) {
		return AuthorDyeing.craftedBy(recipe, original.call(recipe, input), player);
	}
}
