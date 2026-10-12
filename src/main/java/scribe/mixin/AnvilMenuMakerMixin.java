package scribe.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import scribe.EnchantedMaker;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMakerMixin extends ItemCombinerMenu {
	protected AnvilMenuMakerMixin(@Nullable MenuType<?> type, int containerId, Inventory inventory, ContainerLevelAccess access,
		ItemCombinerMenuSlotDefinition slots) {
		super(type, containerId, inventory, access, slots);
	}

	@Inject(method = "createResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AnvilMenu;broadcastChanges()V"))
	private void scribe$recordMaker(CallbackInfo ci) {
		ItemStack result = this.resultSlots.getItem(0);
		if (this.player instanceof ServerPlayer && EnchantedMaker.madeAtAnvil(this.inputSlots.getItem(0), this.inputSlots.getItem(1), result)) {
			EnchantedMaker.stamp(result, this.player);
		}
	}
}
