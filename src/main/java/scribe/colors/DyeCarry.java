package scribe.colors;

import scribe.Scribe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingInput;
import org.jspecify.annotations.Nullable;

public final class DyeCarry {
	public static final TagKey<Item> KEEPS_BOOK_DYE = TagKey.create(Registries.ITEM, Scribe.id("keeps_book_dye"));

	private DyeCarry() {
	}

	public static ItemStack crafted(ItemStack result, CraftingInput grid) {
		if (result.isEmpty() || result.has(DataComponents.DYED_COLOR) || !result.is(KEEPS_BOOK_DYE) || !DyeOptions.get().craftedBooksKeepDye) {
			return result;
		}
		DyedItemColor dye = firstDye(grid);
		if (dye != null) {
			result.set(DataComponents.DYED_COLOR, dye);
		}
		return result;
	}

	public static void traded(ItemStack result, ItemStack paymentA, ItemStack paymentB) {
		if (!result.is(Items.ENCHANTED_BOOK) || !DyeOptions.get().tradedBooksKeepDye) {
			return;
		}
		ItemStack book = paymentA.is(Items.BOOK) ? paymentA : paymentB.is(Items.BOOK) ? paymentB : null;
		if (book != null) {
			result.set(DataComponents.DYED_COLOR, book.get(DataComponents.DYED_COLOR));
		}
	}

	private static @Nullable DyedItemColor firstDye(CraftingInput grid) {
		for (int slot = 0; slot < grid.size(); slot++) {
			DyedItemColor dye = grid.getItem(slot).get(DataComponents.DYED_COLOR);
			if (dye != null) {
				return dye;
			}
		}
		return null;
	}
}
