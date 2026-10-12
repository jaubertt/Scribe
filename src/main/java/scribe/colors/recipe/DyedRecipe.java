package scribe.colors.recipe;

import scribe.colors.ColorsConfig;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

public final class DyedRecipe<T extends CraftingRecipe> implements CraftingRecipe {
	private final T delegate;
	private final RecipeSerializer<DyedRecipe<T>> serializer;

	public DyedRecipe(T delegate, RecipeSerializer<DyedRecipe<T>> serializer) {
		this.delegate = delegate;
		this.serializer = serializer;
	}

	@Override
	public RecipeType<CraftingRecipe> getType() {
		return this.delegate.getType();
	}

	@Override
	public RecipeSerializer<DyedRecipe<T>> getSerializer() {
		return this.serializer;
	}

	@Override
	public CraftingBookCategory category() {
		return this.delegate.category();
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		return this.delegate.getRemainingItems(input);
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		return this.delegate.recipeBookCategory();
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return this.delegate.matches(input, level);
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		return keepColor(this.delegate.assemble(input), input);
	}

	public static ItemStack keepColor(ItemStack result, CraftingInput input) {
		Optional<ItemStack> dyed = input.items().stream().filter(stack -> stack.has(DataComponents.DYED_COLOR)).findFirst();
		return dyed.map(base -> ColorsConfig.get().craftingStrategy(result.getItem()).apply(result, base)).orElse(result);
	}

	@Override
	public boolean isSpecial() {
		return this.delegate.isSpecial();
	}

	@Override
	public boolean showNotification() {
		return this.delegate.showNotification();
	}

	@Override
	public String group() {
		return this.delegate.group();
	}

	@Override
	public PlacementInfo placementInfo() {
		return this.delegate.placementInfo();
	}

	@Override
	public List<RecipeDisplay> display() {
		return this.delegate.display();
	}

	public static <T extends CraftingRecipe> RecipeSerializer<DyedRecipe<T>> createSerializer(RecipeSerializer<T> delegate) {
		AtomicReference<RecipeSerializer<DyedRecipe<T>>> self = new AtomicReference<>();
		RecipeSerializer<DyedRecipe<T>> serializer = new RecipeSerializer<>(
			delegate.codec().xmap(recipe -> new DyedRecipe<>(recipe, self.get()), recipe -> recipe.delegate),
			delegate.streamCodec().map(recipe -> new DyedRecipe<>(recipe, self.get()), recipe -> recipe.delegate));
		self.set(serializer);
		return serializer;
	}
}
