package scribe.mixin;

import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractVillager.class)
public interface AbstractVillagerAccessor {
	@Accessor("offers")
	@Nullable MerchantOffers scribe$offers();
}
