package scribe.client.memory;

import scribe.Scribe;
import scribe.config.ScribeConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

public final class BookmarkStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	public static final int MAX_NAME = 24;
	private static final DyeColor[] DEFAULT_ORDER = {
		DyeColor.RED, DyeColor.YELLOW, DyeColor.LIME, DyeColor.LIGHT_BLUE, DyeColor.PURPLE, DyeColor.ORANGE,
		DyeColor.MAGENTA, DyeColor.CYAN, DyeColor.PINK, DyeColor.GREEN, DyeColor.BLUE, DyeColor.BROWN,
		DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.BLACK};

	private static @Nullable BookmarkStore instance;

	public record Bookmark(int page, DyeColor color, String name) {
	}

	public interface Marks {
		List<Bookmark> list();

		void put(Bookmark bookmark);

		void remove(int page);

		default Optional<Bookmark> at(int page) {
			return this.list().stream().filter(b -> b.page() == page).findFirst();
		}

		default DyeColor nextColor() {
			return BookmarkStore.nextColor(this.list());
		}

		default void done() {
		}
	}

	private static final class Stored {
		int page;
		String color;
		String name;
	}

	private final Path file;
	private final Map<String, List<Bookmark>> books = new LinkedHashMap<>();

	public BookmarkStore(Path file) {
		this.file = file;
		this.load();
	}

	public static BookmarkStore get() {
		if (instance == null) {
			instance = new BookmarkStore(ScribeConfig.directory().resolve("bookmarks.json"));
		}
		return instance;
	}

	public static List<Bookmark> with(List<Bookmark> list, Bookmark bookmark) {
		List<Bookmark> out = new ArrayList<>(list);
		out.removeIf(b -> b.page() == bookmark.page());
		String name = bookmark.name() == null ? "" : bookmark.name().strip();
		if (name.length() > MAX_NAME) {
			name = name.substring(0, MAX_NAME);
		}
		out.add(new Bookmark(bookmark.page(), bookmark.color(), name));
		out.sort(Comparator.comparingInt(Bookmark::page));
		return List.copyOf(out);
	}

	public static List<Bookmark> without(List<Bookmark> list, int page) {
		List<Bookmark> out = new ArrayList<>(list);
		out.removeIf(b -> b.page() == page);
		return List.copyOf(out);
	}

	public static DyeColor nextColor(List<Bookmark> list) {
		for (DyeColor color : DEFAULT_ORDER) {
			if (list.stream().noneMatch(b -> b.color() == color)) {
				return color;
			}
		}
		return DEFAULT_ORDER[list.size() % DEFAULT_ORDER.length];
	}

	private static List<Bookmark> clean(List<Bookmark> list) {
		List<Bookmark> out = List.of();
		for (Bookmark bookmark : list) {
			if (bookmark.page() >= 0 && out.stream().noneMatch(b -> b.page() == bookmark.page())) {
				out = with(out, bookmark);
			}
		}
		return out;
	}

	public List<Bookmark> list(@Nullable String key) {
		if (key == null) {
			return List.of();
		}
		return List.copyOf(this.books.getOrDefault(key, List.of()));
	}

	public void put(@Nullable String key, Bookmark bookmark) {
		if (key == null) {
			return;
		}
		this.books.put(key, with(this.list(key), bookmark));
		this.save();
	}

	public void remove(@Nullable String key, int page) {
		List<Bookmark> list = key == null ? null : this.books.get(key);
		if (list == null || list.stream().noneMatch(b -> b.page() == page)) {
			return;
		}
		List<Bookmark> rest = without(list, page);
		if (rest.isEmpty()) {
			this.books.remove(key);
		} else {
			this.books.put(key, rest);
		}
		this.save();
	}

	public Marks marks(String key) {
		return new Marks() {
			@Override
			public List<Bookmark> list() {
				return BookmarkStore.this.list(key);
			}

			@Override
			public void put(Bookmark bookmark) {
				BookmarkStore.this.put(key, bookmark);
			}

			@Override
			public void remove(int page) {
				BookmarkStore.this.remove(key, page);
			}
		};
	}

	public void replace(@Nullable String key, List<Bookmark> bookmarks) {
		this.move(key, key, bookmarks);
	}

	public void move(@Nullable String oldKey, @Nullable String newKey, List<Bookmark> bookmarks) {
		if (newKey == null) {
			return;
		}
		boolean changed = oldKey != null && !oldKey.equals(newKey) && this.books.remove(oldKey) != null;
		List<Bookmark> kept = clean(bookmarks);
		if (kept.isEmpty()) {
			changed |= this.books.remove(newKey) != null;
		} else if (!kept.equals(this.books.get(newKey))) {
			this.books.put(newKey, kept);
			changed = true;
		}
		if (changed) {
			this.save();
		}
	}

	public boolean adopt(@Nullable String from, @Nullable String to) {
		if (from == null || to == null || from.equals(to) || this.books.containsKey(to)) {
			return false;
		}
		List<Bookmark> moving = this.books.remove(from);
		if (moving == null) {
			return false;
		}
		this.books.put(to, moving);
		this.save();
		return true;
	}

	private void load() {
		Path path = this.file;
		if (!Files.isRegularFile(path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			Map<String, List<Stored>> raw = GSON.fromJson(reader, new TypeToken<Map<String, List<Stored>>>() {}.getType());
			if (raw == null) {
				return;
			}
			raw.forEach((key, list) -> {
				List<Bookmark> out = new ArrayList<>();
				for (Stored s : list == null ? List.<Stored>of() : list) {
					if (s != null && s.page >= 0) {
						DyeColor color = DyeColor.byName(s.color == null ? "" : s.color, DyeColor.RED);
						out.add(new Bookmark(s.page, color, s.name == null ? "" : s.name));
					}
				}
				if (!out.isEmpty()) {
					out.sort(Comparator.comparingInt(Bookmark::page));
					this.books.put(key, List.copyOf(out));
				}
			});
		} catch (IOException | JsonParseException e) {
			Scribe.LOGGER.warn("Could not read {}", path, e);
		}
	}

	private void save() {
		Map<String, List<Stored>> raw = new LinkedHashMap<>();
		this.books.forEach((key, list) -> {
			List<Stored> out = new ArrayList<>();
			for (Bookmark b : list) {
				Stored s = new Stored();
				s.page = b.page();
				s.color = b.color().getSerializedName();
				s.name = b.name();
				out.add(s);
			}
			raw.put(key, out);
		});
		Path path = this.file;
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(raw, writer);
			}
		} catch (IOException e) {
			Scribe.LOGGER.warn("Could not save {}", path, e);
		}
	}
}
