package scribe.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import scribe.Scribe;

public final class SetBookFunction extends LootItemConditionalFunction {
	public static final MapCodec<SetBookFunction> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i).and(i.group(
		Identifier.CODEC.fieldOf("book").forGetter(f -> f.book),
		Codec.intRange(0, WrittenBookContent.MAX_GENERATION).optionalFieldOf("generation").forGetter(f -> f.generation)
	)).apply(i, SetBookFunction::new));

	private final Identifier book;
	private final Optional<Integer> generation;

	private SetBookFunction(Optional<Holder<LootItemCondition>> condition, Identifier book, Optional<Integer> generation) {
		super(condition);
		this.book = book;
		this.generation = generation;
	}

	@Override
	public MapCodec<SetBookFunction> codec() {
		return CODEC;
	}

	@Override
	protected ItemStack run(ItemStack stack, LootContext context) {
		ItemStack book = ScribeBooks.book(this.book);
		if (book == null) {
			Scribe.LOGGER.warn("Loot function scribe:set_book: no data pack has the book {}", this.book);
			return stack;
		}
		ItemStack out = BookData.withGeneration(book, this.generation.map(OptionalInt::of).orElse(OptionalInt.empty()));
		out.setCount(Math.max(1, stack.getCount()));
		return out;
	}
}
