package scribe.colors;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

public final class BookColors {
	public static final int DEFAULT = 0xFFA06540;

	private BookColors() {
	}

	public static int of(ItemStack stack) {
		return DyedItemColor.getOrDefault(stack, DEFAULT);
	}

	public static int dyedOrZero(ItemStack stack) {
		return stack.has(DataComponents.DYED_COLOR) ? DyedItemColor.getOrDefault(stack, 0) : 0;
	}
}
