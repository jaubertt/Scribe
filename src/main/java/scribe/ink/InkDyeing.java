package scribe.ink;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
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
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class InkDyeing extends NormalCraftingRecipe {
	public static final MapCodec<InkDyeing> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Recipe.CommonInfo.MAP_CODEC.forGetter(r -> r.commonInfo),
		CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(r -> r.bookInfo),
		DyeColor.CODEC.fieldOf("color").forGetter(r -> r.color)
	).apply(i, InkDyeing::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, InkDyeing> STREAM_CODEC = StreamCodec.composite(
		Recipe.CommonInfo.STREAM_CODEC, r -> r.commonInfo,
		CraftingRecipe.CraftingBookInfo.STREAM_CODEC, r -> r.bookInfo,
		DyeColor.STREAM_CODEC, r -> r.color,
		InkDyeing::new);
	public static final RecipeSerializer<InkDyeing> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private final DyeColor color;

	public InkDyeing(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, DyeColor color) {
		super(commonInfo, bookInfo);
		this.color = color;
	}

	public DyeColor color() {
		return this.color;
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return colorOf(input) == this.color;
	}

	public static @Nullable DyeColor colorOf(CraftingInput input) {
		if (input.ingredientCount() != 2) {
			return null;
		}
		ItemStack pot = ItemStack.EMPTY;
		ItemStack dye = ItemStack.EMPTY;
		for (ItemStack stack : input.items()) {
			if (stack.isEmpty()) {
				continue;
			}
			if (InkPots.isInk(stack)) {
				pot = stack;
			} else {
				dye = stack;
			}
		}
		if (pot.isEmpty() || !InkPots.isPlainDye(dye)) {
			return null;
		}
		DyeColor to = dye.get(DataComponents.DYE);
		return to != InkPots.color(pot) ? to : null;
	}

	public static boolean shapelessMayTake(ItemStackTemplate result, CraftingInput input) {
		boolean ink = false;
		for (ItemStack stack : input.items()) {
			if (InkPots.isInk(stack)) {
				ink = true;
				break;
			}
		}
		if (!ink) {
			return true;
		}
		ItemStack made = result.create();
		return colorOf(input) == null && !InkPots.isInk(made) && !InkPots.isPlainDye(made);
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		return InkPots.make(this.color);
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		return NonNullList.withSize(input.size(), ItemStack.EMPTY);
	}

	@Override
	public RecipeSerializer<InkDyeing> getSerializer() {
		return SERIALIZER;
	}

	@Override
	protected PlacementInfo createPlacementInfo() {
		return PlacementInfo.create(List.of(Ingredient.of(Items.DYE.pick(this.color)), Ingredient.of(otherDyes(this.color).stream())));
	}

	@Override
	public List<RecipeDisplay> display() {
		List<SlotDisplay> pots = new ArrayList<>();
		for (DyeColor other : DyeColor.values()) {
			if (other != this.color) {
				pots.add(new SlotDisplay.ItemStackSlotDisplay(InkPots.template(other)));
			}
		}
		return List.of(new ShapelessCraftingRecipeDisplay(List.of(new SlotDisplay.ItemSlotDisplay(Items.DYE.pick(this.color)), new SlotDisplay.Composite(pots)),
			new SlotDisplay.ItemStackSlotDisplay(InkPots.template(this.color)), new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
	}

	private static List<Item> otherDyes(DyeColor color) {
		List<Item> dyes = new ArrayList<>();
		for (DyeColor other : DyeColor.values()) {
			if (other != color) {
				dyes.add(Items.DYE.pick(other));
			}
		}
		return dyes;
	}
}
