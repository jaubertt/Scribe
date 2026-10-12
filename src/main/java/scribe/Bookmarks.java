package scribe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class Bookmarks {
	public static final String KEY = Scribe.MOD_ID + ":page";

	private Bookmarks() {
	}

	public static boolean isBook(ItemStack stack) {
		return stack.has(DataComponents.WRITTEN_BOOK_CONTENT) || stack.has(DataComponents.WRITABLE_BOOK_CONTENT);
	}

	public static int get(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return 0;
		}
		return Math.max(0, data.copyTag().getIntOr(KEY, 0));
	}

	public static void set(ItemStack stack, int page) {
		if (stack.isEmpty()) {
			return;
		}
		int clamped = Math.clamp(page, 0, BookLimits.MAX_PAGES - 1);
		if (get(stack) == clamped) {
			return;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			if (clamped <= 0) {
				tag.remove(KEY);
			} else {
				tag.putInt(KEY, clamped);
			}
		});
	}
}
