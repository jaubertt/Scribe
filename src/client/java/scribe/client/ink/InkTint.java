package scribe.client.ink;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public record InkTint() implements ItemTintSource {
	public static final MapCodec<InkTint> MAP_CODEC = MapCodec.unit(new InkTint());

	@Override
	public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
		DyeColor color = stack.get(DataComponents.DYE);
		return color == null ? -1 : ARGB.opaque(color.getTextureDiffuseColor());
	}

	@Override
	public MapCodec<InkTint> type() {
		return MAP_CODEC;
	}
}
