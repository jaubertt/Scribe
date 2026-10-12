package scribe.binding;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import scribe.config.ScribeConfig;

public final class BookbindingRecipe implements SmithingRecipe {
	public enum Part implements StringRepresentable {
		STRAP("strap"),
		TRIM("trim"),
		CLASP("clasp");

		public static final Codec<Part> CODEC = StringRepresentable.fromEnum(Part::values);
		public static final StreamCodec<ByteBuf, Part> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Part::ordinal);

		private final String name;

		Part(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	public static final MapCodec<BookbindingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Part.CODEC.fieldOf("part").forGetter(r -> r.part),
		Ingredient.CODEC.optionalFieldOf("template").forGetter(r -> r.template),
		Ingredient.CODEC.fieldOf("base").forGetter(r -> r.base),
		Ingredient.CODEC.optionalFieldOf("addition").forGetter(r -> r.addition)
	).apply(i, BookbindingRecipe::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, BookbindingRecipe> STREAM_CODEC = StreamCodec.composite(
		Part.STREAM_CODEC, r -> r.part,
		Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC, r -> r.template,
		Ingredient.CONTENTS_STREAM_CODEC, r -> r.base,
		Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC, r -> r.addition,
		BookbindingRecipe::new);
	public static final RecipeSerializer<BookbindingRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);
	public static final boolean STRAP_AND_CLASP = true;

	private final Part part;
	private final Optional<Ingredient> template;
	private final Ingredient base;
	private final Optional<Ingredient> addition;
	private @Nullable PlacementInfo placementInfo;

	public BookbindingRecipe(Part part, Optional<Ingredient> template, Ingredient base, Ingredient addition) {
		this(part, template, base, Optional.of(addition));
	}

	public BookbindingRecipe(Part part, Optional<Ingredient> template, Ingredient base, Optional<Ingredient> addition) {
		this.part = part;
		this.template = part == Part.TRIM ? Optional.empty() : template;
		this.base = base;
		this.addition = addition;
	}

	public Part part() {
		return this.part;
	}

	@Override
	public boolean matches(SmithingRecipeInput input, Level level) {
		return ScribeConfig.get().bookbindingOn() && this.fits(input);
	}

	public boolean fits(SmithingRecipeInput input) {
		return this.next(input) != null;
	}

	@Override
	public ItemStack assemble(SmithingRecipeInput input) {
		Binding next = this.next(input);
		if (next == null) {
			return ItemStack.EMPTY;
		}
		ItemStack book = input.base().copyWithCount(1);
		next.applyTo(book);
		return book;
	}

	private @Nullable Binding next(SmithingRecipeInput input) {
		ItemStack book = input.base();
		if (!this.base.test(book) || !Ingredient.testOptionalIngredient(this.addition, input.addition()) || !Bookbinding.isBindable(book)) {
			return null;
		}
		if (this.part != Part.CLASP && this.addition.isEmpty()) {
			return null;
		}
		ItemStack template = input.template();
		boolean leather = !template.isEmpty() && this.template.isPresent() && this.template.get().test(template);
		if (this.part == Part.TRIM ? !template.isEmpty() : !leather) {
			return null;
		}
		Binding current = Binding.of(book);
		Binding next = switch (this.part) {
			case STRAP -> {
				DyeColor dye = input.addition().get(DataComponents.DYE);
				yield dye == null ? null : current.withStrap(dye);
			}
			case TRIM -> {
				ResourceKey<TrimMaterial> material = Bookbinding.canTrim(book) ? materialOf(input.addition()) : null;
				yield material == null ? null : current.withTrim(material);
			}
			case CLASP -> clasp(book, current, input.addition());
		};
		return next == null || next.equals(current) ? null : next;
	}

	private static @Nullable Binding clasp(ItemStack book, Binding current, ItemStack metal) {
		if (metal.isEmpty()) {
			if (!current.hasStrap()) {
				return current.withStrap(null);
			}
			return Clasp.isLeather(Clasp.shown(current)) ? null : current.withClasp(null);
		}
		ResourceKey<TrimMaterial> material = Bookbinding.canTrim(book) ? materialOf(metal) : null;
		if (material == null) {
			return null;
		}
		if (!current.hasStrap()) {
			return STRAP_AND_CLASP ? current.withStrap(null).withClasp(Clasp.stored(material)) : null;
		}
		return current.withClasp(Clasp.stored(material));
	}

	static @Nullable ResourceKey<TrimMaterial> materialOf(ItemStack item) {
		Holder<TrimMaterial> material = item.get(DataComponents.PROVIDES_TRIM_MATERIAL);
		ResourceKey<TrimMaterial> key = material == null ? null : material.unwrapKey().orElse(null);
		return key != null && Bookbinding.isSupported(key) ? key : null;
	}

	@Override
	public Optional<Ingredient> templateIngredient() {
		return this.template;
	}

	@Override
	public Ingredient baseIngredient() {
		return this.base;
	}

	@Override
	public Optional<Ingredient> additionIngredient() {
		return this.addition;
	}

	@Override
	public RecipeSerializer<BookbindingRecipe> getSerializer() {
		return SERIALIZER;
	}

	@Override
	public boolean showNotification() {
		return false;
	}

	@Override
	public String group() {
		return "";
	}

	@Override
	public PlacementInfo placementInfo() {
		if (this.placementInfo == null) {
			this.placementInfo = PlacementInfo.createFromOptionals(List.of(this.template, Optional.of(this.base), this.addition));
		}
		return this.placementInfo;
	}

	@Override
	public List<RecipeDisplay> display() {
		return List.of(new SmithingRecipeDisplay(
			Ingredient.optionalIngredientToDisplay(this.template),
			this.base.display(),
			Ingredient.optionalIngredientToDisplay(this.addition),
			this.base.display(),
			new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)));
	}
}
