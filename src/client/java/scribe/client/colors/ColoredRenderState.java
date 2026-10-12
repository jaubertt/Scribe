package scribe.client.colors;

import scribe.client.colors.ColorsClient.LecternClasp;
import org.jspecify.annotations.Nullable;

public interface ColoredRenderState {
	void scribe$setColor(int color);

	int scribe$getColor();

	default void scribe$setFoil(boolean foil) {
	}

	default boolean scribe$isFoil() {
		return false;
	}

	default void scribe$setTrim(@Nullable String material) {
	}

	default @Nullable String scribe$getTrim() {
		return null;
	}

	default void scribe$setStrap(int color) {
	}

	default int scribe$getStrap() {
		return 0;
	}

	default void scribe$setClasp(@Nullable LecternClasp clasp) {
	}

	default @Nullable LecternClasp scribe$getClasp() {
		return null;
	}

	default void scribe$setTitle(int color) {
	}

	default int scribe$getTitle() {
		return 0;
	}

	default void scribe$setSpine(@Nullable String material) {
	}

	default @Nullable String scribe$getSpine() {
		return null;
	}

	default void scribe$setClosed(boolean closed) {
	}

	default boolean scribe$isClosed() {
		return false;
	}
}
