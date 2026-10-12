package scribe.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scribe.EnchantedMaker;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMakerMixin {
	@Shadow
	@Final
	private Container enchantSlots;

	@Unique
	private boolean scribe$enchantingBook;

	@Inject(method = "clickMenuButton", at = @At("HEAD"))
	private void scribe$beforeEnchanting(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
		this.scribe$enchantingBook = this.enchantSlots.getItem(0).is(Items.BOOK);
	}

	@Inject(method = "clickMenuButton", at = @At("RETURN"))
	private void scribe$recordMaker(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
		if (player instanceof ServerPlayer && EnchantedMaker.madeAtTable(this.scribe$enchantingBook, this.enchantSlots.getItem(0))) {
			EnchantedMaker.stamp(this.enchantSlots.getItem(0), player);
		}
		this.scribe$enchantingBook = false;
	}
}
