package scribe.colors;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

public interface ColoredBlockEntity {
	int scribe$getColor(int index);

	default void scribe$setColor(int index, int color) {
	}

	default void scribe$writeSyncData(HolderLookup.Provider registries, CompoundTag tag) {
	}
}
