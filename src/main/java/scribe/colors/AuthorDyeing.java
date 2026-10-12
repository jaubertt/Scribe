package scribe.colors;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.DyeRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import scribe.RealAuthor;
import scribe.config.ScribeConfig;

public final class AuthorDyeing {
	private AuthorDyeing() {
	}

	public static Component onlyAuthorDyes() {
		return Component.translatableWithFallback("scribe.author.only_dye", "Only the author can dye this book");
	}

	public static Component onlyAuthorWashes() {
		return Component.translatableWithFallback("scribe.author.only_wash", "Only the author can wash this book");
	}

	static boolean dyesWrittenBook(Recipe<?> recipe, ItemStack result) {
		return recipe instanceof DyeRecipe && RealAuthor.guardsColor(result);
	}

	public static ItemStack craftedBy(Recipe<?> recipe, ItemStack result, Player player) {
		if (!result.isEmpty() && recipe instanceof DyeRecipe && BookDyeing.isBook(result) && !BookDyeing.books(player.level())) {
			return ItemStack.EMPTY;
		}
		if (result.isEmpty() || !dyesWrittenBook(recipe, result) || RealAuthor.mayRecolor(result, player)) {
			return result;
		}
		player.sendOverlayMessage(onlyAuthorDyes());
		return ItemStack.EMPTY;
	}

	public static Optional<RecipeHolder<CraftingRecipe>> forCrafter(Optional<RecipeHolder<CraftingRecipe>> recipe, CraftingInput input) {
		if (recipe.isEmpty() || !(recipe.get().value() instanceof DyeRecipe)) {
			return recipe;
		}
		for (ItemStack stack : input.items()) {
			if (RealAuthor.hasAuthor(stack) || (BookDyeing.isBook(stack) && !ScribeConfig.get().bookDyeingOn())) {
				return Optional.empty();
			}
		}
		return recipe;
	}

	public static @Nullable InteractionResult wash(Level level, Player player, ItemStack item) {
		if (item.has(DataComponents.DYED_COLOR) && BookDyeing.isBook(item) && !BookDyeing.books(level)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!item.has(DataComponents.DYED_COLOR) || RealAuthor.mayRecolor(item, player)) {
			return null;
		}
		if (!level.isClientSide()) {
			player.sendOverlayMessage(onlyAuthorWashes());
		}
		return InteractionResult.CONSUME;
	}
}
