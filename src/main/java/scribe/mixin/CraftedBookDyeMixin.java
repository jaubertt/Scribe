package scribe.mixin;

import scribe.colors.DyeCarry;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({ShapedRecipe.class, ShapelessRecipe.class})
public abstract class CraftedBookDyeMixin {
	@ModifyReturnValue(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
	private ItemStack scribe$keepBookDye(ItemStack result, @Local(argsOnly = true) CraftingInput grid) {
		return DyeCarry.crafted(result, grid);
	}
}
