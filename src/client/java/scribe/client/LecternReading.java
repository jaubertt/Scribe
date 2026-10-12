package scribe.client;

import scribe.ClosedLecterns;
import net.minecraft.world.inventory.LecternMenu;
import org.jspecify.annotations.Nullable;

public final class LecternReading {
	public enum Follow {
		STAY,
		TO_COVER,
		TO_PAGE
	}

	private LecternReading() {
	}

	public static boolean hasCover(boolean bookHasCover, @Nullable Boolean closed) {
		return bookHasCover && closed != null;
	}

	public static boolean startsAtCover(@Nullable Boolean closed, boolean hasCover) {
		return Boolean.TRUE.equals(closed) && hasCover;
	}

	public static boolean opensOnShow(@Nullable Boolean closed, boolean hasCover) {
		return Boolean.TRUE.equals(closed) && !hasCover;
	}

	public static Follow follow(boolean onCover, @Nullable Boolean closed, boolean hasCover) {
		boolean isClosed = Boolean.TRUE.equals(closed);
		if (isClosed && hasCover && !onCover) {
			return Follow.TO_COVER;
		}
		if (!isClosed && onCover) {
			return Follow.TO_PAGE;
		}
		return Follow.STAY;
	}

	public static int turnButton(int request, @Nullable Boolean closed) {
		if (request >= 0) {
			return LecternMenu.BUTTON_PAGE_JUMP_RANGE_START + request;
		}
		return Boolean.TRUE.equals(closed) ? ClosedLecterns.BUTTON_OPEN : -1;
	}
}
