package scribe.client.colors;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public record RibbonTint() implements ItemTintSource {
	public static final MapCodec<RibbonTint> MAP_CODEC = MapCodec.unit(new RibbonTint());

	@Override
	public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
		return ColorsClient.enchantedRibbon(stack);
	}

	@Override
	public MapCodec<RibbonTint> type() {
		return MAP_CODEC;
	}
}
