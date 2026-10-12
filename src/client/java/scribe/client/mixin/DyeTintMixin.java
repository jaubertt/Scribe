package scribe.client.mixin;

import scribe.client.colors.EnchantColors;
import net.minecraft.client.color.item.Dye;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Dye.class)
public abstract class DyeTintMixin {
	@Inject(method = "calculate", at = @At("RETURN"), cancellable = true)
	private void scribe$enchantColor(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, CallbackInfoReturnable<Integer> cir) {
		int color = cir.getReturnValue();
		int shown = EnchantColors.get().bookColor(stack, color);
		if (shown != color) {
			cir.setReturnValue(shown);
		}
	}
}
