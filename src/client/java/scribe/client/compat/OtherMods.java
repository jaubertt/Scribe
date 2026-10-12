package scribe.client.compat;

import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

public final class OtherMods {
	public static final String JADE = "jade";
	public static final String WTHIT = "wthit";
	public static final String SHELF_VISUALIZER = "chiseledbookshelfvisualizer";
	public static final String BOOKSHELF_INSPECTOR = "bookshelfinspector";
	public static final List<String> LOOK_AT = List.of(JADE, WTHIT);
	public static final List<String> SHELVES = List.of(SHELF_VISUALIZER, BOOKSHELF_INSPECTOR);

	public static Predicate<String> installed = OtherMods::fabricLoaded;

	private OtherMods() {
	}

	private static boolean fabricLoaded(String id) {
		try {
			return FabricLoader.getInstance().isModLoaded(id);
		} catch (RuntimeException e) {
			return false;
		}
	}

	public static @Nullable String firstInstalled(List<String> ids) {
		for (String id : ids) {
			if (installed.test(id)) {
				return id;
			}
		}
		return null;
	}

	public static @Nullable String lookAtBy() {
		return firstInstalled(LOOK_AT);
	}

	public static @Nullable String shelvesBy() {
		return firstInstalled(SHELVES);
	}

	public static String name(String id) {
		try {
			return FabricLoader.getInstance().getModContainer(id).map(mod -> mod.getMetadata().getName()).orElse(id);
		} catch (RuntimeException e) {
			return id;
		}
	}
}
