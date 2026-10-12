package scribe.client.memory;

import scribe.Scribe;
import scribe.config.ScribeConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class BookDetails {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final int MAX_BOOKS = 500;
	private static @Nullable BookDetails instance;

	public record Details(String title, String subtitle, String penName) {
		public static final Details NONE = new Details("", "", "");

		public boolean isEmpty() {
			return this.title.isBlank() && this.subtitle.isBlank() && this.penName.isBlank();
		}
	}

	private final LinkedHashMap<String, Details> books = new LinkedHashMap<>(16, 0.75F, true);
	private final Path file;

	private BookDetails(Path file) {
		this.file = file;
	}

	public static BookDetails get() {
		if (instance == null) {
			instance = new BookDetails(ScribeConfig.directory().resolve("book_details.json"));
			instance.load();
		}
		return instance;
	}

	public Optional<Details> get(@Nullable String key) {
		return key == null ? Optional.empty() : Optional.ofNullable(this.books.get(key));
	}

	public void put(@Nullable String key, Details details) {
		if (key == null) {
			return;
		}
		if (details.isEmpty()) {
			if (this.books.remove(key) != null) {
				this.save();
			}
			return;
		}
		this.books.put(key, details);
		var it = this.books.entrySet().iterator();
		while (this.books.size() > MAX_BOOKS && it.hasNext()) {
			it.next();
			it.remove();
		}
		this.save();
	}

	public void move(@Nullable String oldKey, @Nullable String newKey) {
		if (oldKey == null || newKey == null || oldKey.equals(newKey)) {
			return;
		}
		Details details = this.books.remove(oldKey);
		if (details != null) {
			this.books.put(newKey, details);
			this.save();
		}
	}

	public void remove(@Nullable String key) {
		if (key != null && this.books.remove(key) != null) {
			this.save();
		}
	}

	private void load() {
		Path path = this.file;
		if (!Files.isRegularFile(path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (root.isJsonObject() && root.getAsJsonObject().has("books")) {
				for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().getAsJsonObject("books").entrySet()) {
					if (e.getValue().isJsonObject()) {
						JsonObject o = e.getValue().getAsJsonObject();
						Details details = new Details(text(o, "title"), text(o, "subtitle"), text(o, "pen_name"));
						if (!details.isEmpty()) {
							this.books.put(e.getKey(), details);
						}
					}
				}
			}
		} catch (Exception e) {
			Scribe.LOGGER.warn("Could not read book details from {}", path, e);
		}
	}

	private static String text(JsonObject o, String name) {
		return o.has(name) && o.get(name).isJsonPrimitive() ? o.get(name).getAsString() : "";
	}

	private void save() {
		Path path = this.file;
		JsonObject books = new JsonObject();
		this.books.forEach((key, details) -> {
			JsonObject o = new JsonObject();
			o.addProperty("title", details.title());
			o.addProperty("subtitle", details.subtitle());
			o.addProperty("pen_name", details.penName());
			books.add(key, o);
		});
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.add("books", books);
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (Exception e) {
			Scribe.LOGGER.warn("Could not save book details to {}", path, e);
		}
	}
}
