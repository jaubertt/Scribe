package scribe.client.colors;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public record BookStyleProperty() implements RangeSelectItemModelProperty {
	public static final MapCodec<BookStyleProperty> MAP_CODEC = MapCodec.unit(new BookStyleProperty());

	@Override
	public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
		return EnchantColors.get().styleValue(stack);
	}

	@Override
	public MapCodec<BookStyleProperty> type() {
		return MAP_CODEC;
	}
}
