package scribe.colors.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public final class DyeRandomlyFunction extends LootItemConditionalFunction {
	private static final Codec<IntProvider> AMOUNT_CODEC = IntProviders.codec(0, 8);
	public static final MapCodec<DyeRandomlyFunction> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i).and(i.group(
		AMOUNT_CODEC.fieldOf("amount").forGetter(f -> f.amount),
		DyeColor.CODEC.listOf(1, Integer.MAX_VALUE).optionalFieldOf("colors").forGetter(f -> f.colors)
	)).apply(i, DyeRandomlyFunction::new));

	private final IntProvider amount;
	private final Optional<List<DyeColor>> colors;

	private DyeRandomlyFunction(Optional<Holder<LootItemCondition>> condition, IntProvider amount, Optional<List<DyeColor>> colors) {
		super(condition);
		this.amount = amount;
		this.colors = colors;
	}

	@Override
	public MapCodec<DyeRandomlyFunction> codec() {
		return CODEC;
	}

	@Override
	protected ItemStack run(ItemStack stack, LootContext context) {
		RandomSource random = context.getRandom();
		List<DyeColor> dyes = new ArrayList<>();
		int count = this.amount.sample(random);
		for (int i = 0; i < count; i++) {
			dyes.add(this.colors.map(list -> list.get(random.nextInt(list.size()))).orElseGet(() -> DyeColor.byId(random.nextInt(16))));
		}
		return dyes.isEmpty() ? stack : DyedItemColor.applyDyes(stack, dyes);
	}
}
