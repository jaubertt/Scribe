package scribe.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import scribe.binding.Bookbinding;

@Mixin(targets = {"net.minecraft.world.inventory.GrindstoneMenu$2", "net.minecraft.world.inventory.GrindstoneMenu$3"})
public abstract class GrindstoneInputSlotMixin {
	@ModifyReturnValue(method = "mayPlace", at = @At("RETURN"))
	private boolean scribe$boundBooks(boolean original, @Local(argsOnly = true) ItemStack stack) {
		return original || Bookbinding.canUnbind(stack);
	}
}
