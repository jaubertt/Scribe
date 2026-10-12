package scribe.client.colors;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import scribe.config.ScribeConfig;

public record BookIconsProperty() implements ConditionalItemModelProperty {
	public static final MapCodec<BookIconsProperty> MAP_CODEC = MapCodec.unit(new BookIconsProperty());

	@Override
	public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext) {
		return ScribeConfig.get().iconsShown();
	}

	@Override
	public MapCodec<BookIconsProperty> type() {
		return MAP_CODEC;
	}
}
