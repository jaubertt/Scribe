package scribe.access;

import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.jspecify.annotations.Nullable;

public interface LecternMenuAccess {
	@Nullable LecternBlockEntity scribe$lectern();

	void scribe$setLectern(LecternBlockEntity lectern);
}
