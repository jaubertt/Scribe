package scribe.colors;

import net.minecraft.world.item.component.DyedItemColor;
import org.jspecify.annotations.Nullable;

public interface DyedTable {
	@Nullable DyedItemColor scribe$dyedColor();

	void scribe$setDyedColor(@Nullable DyedItemColor color);
}
