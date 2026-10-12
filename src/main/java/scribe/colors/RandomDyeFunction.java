package scribe.colors;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public final class RandomDyeFunction extends LootItemConditionalFunction {
	public static final int MOST_DYES = 8;
	private static final List<DyeColor> EVERY_DYE = List.of(DyeColor.values());
	public static final MapCodec<RandomDyeFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(fields -> commonFields(fields).and(fields.group(
		IntProviders.codec(0, MOST_DYES).fieldOf("dyes").forGetter(RandomDyeFunction::dyes),
		ExtraCodecs.nonEmptyList(DyeColor.CODEC.listOf()).optionalFieldOf("from").forGetter(RandomDyeFunction::from)
	)).apply(fields, RandomDyeFunction::new));

	private final IntProvider dyes;
	private final Optional<List<DyeColor>> from;

	private RandomDyeFunction(Optional<Holder<LootItemCondition>> condition, IntProvider dyes, Optional<List<DyeColor>> from) {
		super(condition);
		this.dyes = dyes;
		this.from = from;
	}

	public IntProvider dyes() {
		return this.dyes;
	}

	public Optional<List<DyeColor>> from() {
		return this.from;
	}

	@Override
	public MapCodec<RandomDyeFunction> codec() {
		return MAP_CODEC;
	}

	@Override
	protected ItemStack run(ItemStack stack, LootContext context) {
		RandomSource random = context.getRandom();
		int count = this.dyes.sample(random);
		if (count <= 0) {
			return stack;
		}
		List<DyeColor> palette = this.from.orElse(EVERY_DYE);
		List<DyeColor> mixed = new ArrayList<>(count);
		while (mixed.size() < count) {
			mixed.add(palette.get(random.nextInt(palette.size())));
		}
		return DyedItemColor.applyDyes(stack, mixed);
	}
}
