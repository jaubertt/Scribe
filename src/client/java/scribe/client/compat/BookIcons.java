package scribe.client.compat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import scribe.config.ScribeConfig;

public final class BookIcons {
	private BookIcons() {
	}

	public static @Nullable Identifier model(ItemStack stack, @Nullable Identifier named) {
		return model(stack, named, ScribeConfig.get().iconsShown());
	}

	public static @Nullable Identifier model(ItemStack stack, @Nullable Identifier named, boolean iconsShown) {
		if (!iconsShown || !isScribeBook(stack)) {
			return named;
		}
		Identifier own = stack.get(DataComponents.ITEM_MODEL);
		return own != null ? own : named;
	}

	public static boolean isScribeBook(ItemStack stack) {
		return !stack.isEmpty() && (stack.is(Items.BOOK) || stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK) || stack.is(Items.ENCHANTED_BOOK));
	}
}
