package scribe.mixin;

import scribe.colors.ColoredBlockEntity;
import scribe.network.Payloads;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin extends AbstractContainerMenu implements ColoredBlockEntity {
	@Unique
	private final int[] scribe$rgb = {255, 255, 255};

	protected EnchantmentMenuMixin(@Nullable MenuType<?> type, int containerId) {
		super(type, containerId);
	}

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
	private void scribe$init(int containerId, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
		if (inventory.player instanceof ServerPlayer player && !Payloads.hasScribe(player)) {
			return;
		}
		for (int i = 0; i < 3; i++) {
			this.addDataSlot(DataSlot.shared(this.scribe$rgb, i));
		}
		access.evaluate(Level::getBlockEntity).ifPresent(blockEntity -> {
			if (blockEntity instanceof ColoredBlockEntity colored) {
				int color = colored.scribe$getColor(0);
				this.scribe$rgb[0] = ARGB.red(color);
				this.scribe$rgb[1] = ARGB.green(color);
				this.scribe$rgb[2] = ARGB.blue(color);
			}
		});
	}

	@Override
	public int scribe$getColor(int index) {
		return ARGB.color(255, this.scribe$rgb[0], this.scribe$rgb[1], this.scribe$rgb[2]);
	}
}
