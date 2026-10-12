package scribe.access;

import net.minecraft.world.item.ItemStack;

public interface BookPreviews {
	ItemStack scribe$preview(int slot);

	default int scribe$page() {
		return -1;
	}

	default int scribe$pageCount() {
		return 0;
	}
}
