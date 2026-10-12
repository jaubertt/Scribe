package scribe;

import java.util.function.BooleanSupplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class EnchantedBooks {
	public static final int FIRST_PAGE = 6;
	public static final int NEXT_PAGES = 7;

	private static BooleanSupplier clientAllows = () -> false;

	private EnchantedBooks() {
	}

	public static boolean is(ItemStack stack) {
		return stack.is(Items.ENCHANTED_BOOK);
	}

	public static void setClientAllows(BooleanSupplier allows) {
		clientAllows = allows;
	}

	public static boolean clientAllows() {
		return clientAllows.getAsBoolean();
	}

	public static int count(ItemStack stack) {
		return stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).size();
	}

	public static int listPages(int enchantments) {
		if (enchantments <= 1) {
			return 0;
		}
		return enchantments <= FIRST_PAGE ? 1 : 1 + Math.ceilDiv(enchantments - FIRST_PAGE, NEXT_PAGES);
	}

	public static int pageCount(int enchantments) {
		return enchantments <= 1 ? 1 : listPages(enchantments) + enchantments;
	}

	public static int pageCount(ItemStack stack) {
		return pageCount(count(stack));
	}

	public static int detailsPage(int enchantments, int index) {
		return listPages(enchantments) + index;
	}

	public static int firstOnPage(int page) {
		return page <= 0 ? 0 : FIRST_PAGE + (page - 1) * NEXT_PAGES;
	}

	public static int roomOnPage(int page) {
		return page <= 0 ? FIRST_PAGE : NEXT_PAGES;
	}
}
