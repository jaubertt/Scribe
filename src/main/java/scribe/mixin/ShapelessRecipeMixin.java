package scribe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import scribe.ink.InkDyeing;

@Mixin(ShapelessRecipe.class)
public abstract class ShapelessRecipeMixin {
	@Shadow
	@Final
	private ItemStackTemplate result;

	@WrapMethod(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z")
	private boolean scribe$inkDyeingFirst(CraftingInput input, Level level, Operation<Boolean> original) {
		return InkDyeing.shapelessMayTake(this.result, input) && original.call(input, level);
	}
}
