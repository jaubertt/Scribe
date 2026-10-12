package scribe.ink;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;
import scribe.colors.DyeCarry;

public class InkRecipe extends NormalCraftingRecipe {
	public static final MapCodec<InkRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Recipe.CommonInfo.MAP_CODEC.forGetter(r -> r.commonInfo),
		CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(r -> r.bookInfo),
		ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result),
		Ingredient.CODEC.listOf(1, 8).fieldOf("ingredients").forGetter(r -> r.ingredients),
		Ingredient.CODEC.fieldOf("ink").forGetter(r -> r.ink)
	).apply(i, InkRecipe::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, InkRecipe> STREAM_CODEC = StreamCodec.composite(
		Recipe.CommonInfo.STREAM_CODEC, r -> r.commonInfo,
		CraftingRecipe.CraftingBookInfo.STREAM_CODEC, r -> r.bookInfo,
		ItemStackTemplate.STREAM_CODEC, r -> r.result,
		Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), r -> r.ingredients,
		Ingredient.CONTENTS_STREAM_CODEC, r -> r.ink,
		InkRecipe::new);
	public static final RecipeSerializer<InkRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private final ItemStackTemplate result;
	private final List<Ingredient> ingredients;
	private final Ingredient ink;
	private final ShapelessRecipe rest;

	public InkRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ItemStackTemplate result, List<Ingredient> ingredients,
		Ingredient ink) {
		super(commonInfo, bookInfo);
		this.result = result;
		this.ingredients = List.copyOf(ingredients);
		this.ink = ink;
		this.rest = new ShapelessRecipe(commonInfo, bookInfo, result, this.ingredients);
	}

	public List<Ingredient> ingredients() {
		return this.ingredients;
	}

	public Ingredient ink() {
		return this.ink;
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		if (input.ingredientCount() != this.ingredients.size() + 1) {
			return false;
		}
		List<ItemStack> others = new ArrayList<>();
		boolean inked = false;
		for (ItemStack stack : input.items()) {
			if (stack.isEmpty()) {
				continue;
			}
			if (!inked && InkPots.isInk(stack) && this.ink.test(stack)) {
				inked = true;
			} else {
				others.add(stack);
			}
		}
		return inked && this.rest.matches(CraftingInput.of(others.size(), 1, others), level);
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		return DyeCarry.crafted(this.result.create(), input);
	}

	@Override
	public RecipeSerializer<InkRecipe> getSerializer() {
		return SERIALIZER;
	}

	@Override
	protected PlacementInfo createPlacementInfo() {
		List<Ingredient> all = new ArrayList<>(this.ingredients);
		all.add(this.ink);
		return PlacementInfo.create(all);
	}

	@Override
	public List<RecipeDisplay> display() {
		List<SlotDisplay> slots = new ArrayList<>();
		for (Ingredient ingredient : this.ingredients) {
			slots.add(ingredient.display());
		}
		slots.add(inkDisplay());
		return List.of(new ShapelessCraftingRecipeDisplay(slots, new SlotDisplay.ItemStackSlotDisplay(this.result),
			new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
	}

	public SlotDisplay inkDisplay() {
		List<SlotDisplay> pots = new ArrayList<>();
		for (DyeColor color : DyeColor.values()) {
			if (this.ink.acceptsItem(Items.DYE.pick(color).builtInRegistryHolder())) {
				pots.add(new SlotDisplay.ItemStackSlotDisplay(InkPots.template(color)));
			}
		}
		return new SlotDisplay.Composite(pots);
	}
}
