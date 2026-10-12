package scribe.colors;

import java.util.function.BooleanSupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import scribe.Scribe;
import scribe.config.ScribeConfig;

public final class BookDyeing {
	private static BooleanSupplier clientBooks = () -> true;
	private static BooleanSupplier clientTables = () -> true;

	private BookDyeing() {
	}

	public static void setClientAllows(BooleanSupplier books, BooleanSupplier tables) {
		clientBooks = books;
		clientTables = tables;
	}

	public static boolean books(Level level) {
		return level.isClientSide() ? clientBooks.getAsBoolean() : ScribeConfig.get().bookDyeingOn();
	}

	public static boolean tables(Level level) {
		return level.isClientSide() ? clientTables.getAsBoolean() : ScribeConfig.get().tableDyeingOn();
	}

	public static final TagKey<Item> DYEABLE = TagKey.create(Registries.ITEM, Scribe.id("dyeable_books"));

	public static boolean isBook(ItemStack stack) {
		return stack.is(Items.BOOK) || stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK) || stack.is(Items.ENCHANTED_BOOK) || stack.is(DYEABLE);
	}
}
