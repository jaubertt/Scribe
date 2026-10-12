package scribe.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Optional;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import scribe.colors.AuthorDyeing;

@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {
	@ModifyReturnValue(method = "getPotentialResults", at = @At("RETURN"))
	private static Optional<RecipeHolder<CraftingRecipe>> scribe$noAuthorDyeing(Optional<RecipeHolder<CraftingRecipe>> recipe,
		@Local(argsOnly = true) CraftingInput input) {
		return AuthorDyeing.forCrafter(recipe, input);
	}
}
