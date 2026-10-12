package scribe.access;

import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.jspecify.annotations.Nullable;

public final class LecternWatch {
	public static final int LEAF_TURN = 0x5C1B;

	@FunctionalInterface
	public interface Listener {
		void arrived(LecternBlockEntity lectern, boolean wasClosed, boolean closed, int oldPage, int newPage);
	}

	private static @Nullable Listener listener;

	private LecternWatch() {
	}

	public static void listen(@Nullable Listener what) {
		listener = what;
	}

	public static void arrived(LecternBlockEntity lectern, boolean wasClosed, boolean closed, int oldPage, int newPage) {
		Listener current = listener;
		if (current != null) {
			current.arrived(lectern, wasClosed, closed, oldPage, newPage);
		}
	}
}
