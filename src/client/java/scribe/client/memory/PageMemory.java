package scribe.client.memory;

import scribe.Scribe;
import scribe.config.ScribeConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;

public final class PageMemory {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final int COVER = -1;
	private static PageMemory instance;

	private final LinkedHashMap<String, Integer> pages = new LinkedHashMap<>(64, 0.75F, true);
	private boolean dirty;

	public static PageMemory get() {
		if (instance == null) {
			instance = new PageMemory();
			instance.load();
		}
		return instance;
	}

	private static Path file() {
		return ScribeConfig.directory().resolve("page_memory.json");
	}

	public static String keyFor(ItemStack stack) {
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (written != null) {
			StringBuilder sb = new StringBuilder("w|").append(written.title().raw()).append('|').append(written.author());
			for (Filterable<Component> page : written.pages()) {
				sb.append('\u0000').append(page.raw().getString());
			}
			return hash(sb.toString());
		}
		WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
		if (writable != null) {
			return keyForPages(writable.getPages(false).toList());
		}
		return null;
	}

	public static @Nullable String textKey(ItemStack stack) {
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (written == null) {
			return null;
		}
		List<String> texts = written.pages().stream().map(page -> page.raw().getString()).toList();
		return texts.stream().allMatch(String::isEmpty) ? null : keyForPages(texts);
	}

	public static String keyForPages(List<String> bookPages) {
		StringBuilder sb = new StringBuilder("q|");
		int end = bookPages.size();
		while (end > 0 && bookPages.get(end - 1).isEmpty()) {
			end--;
		}
		for (int i = 0; i < end; i++) {
			sb.append('\u0000').append(bookPages.get(i));
		}
		return hash(sb.toString());
	}

	private static String hash(String s) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 12);
		} catch (NoSuchAlgorithmException e) {
			return Integer.toHexString(s.hashCode());
		}
	}

	public int get(String key) {
		if (key == null || !ScribeConfig.get().rememberPages) {
			return -1;
		}
		Integer page = this.pages.get(key);
		return page == null ? -1 : page;
	}

	public void put(String key, int page) {
		this.store(key, Math.max(0, page));
	}

	public void putCover(String key) {
		this.store(key, COVER);
	}

	public boolean knows(String key) {
		return key != null && ScribeConfig.get().rememberPages && this.pages.containsKey(key);
	}

	public boolean atCover(String key) {
		if (key == null || !ScribeConfig.get().rememberPages) {
			return false;
		}
		Integer page = this.pages.get(key);
		return page != null && page == COVER;
	}

	private void store(String key, int page) {
		if (key == null || !ScribeConfig.get().rememberPages) {
			return;
		}
		Integer old = this.pages.put(key, page);
		if (old == null || old != page) {
			this.dirty = true;
		}
		int max = ScribeConfig.get().maxRememberedBooks;
		var it = this.pages.entrySet().iterator();
		while (this.pages.size() > max && it.hasNext()) {
			it.next();
			it.remove();
			this.dirty = true;
		}
	}

	public void forget(String key) {
		if (key != null && this.pages.remove(key) != null) {
			this.dirty = true;
		}
	}

	public void move(String oldKey, String newKey, int page) {
		if (oldKey != null && !oldKey.equals(newKey)) {
			if (this.pages.remove(oldKey) != null) {
				this.dirty = true;
			}
		}
		this.put(newKey, page);
	}

	private void load() {
		Path path = file();
		if (!Files.isRegularFile(path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (root.isJsonObject() && root.getAsJsonObject().has("books")) {
				for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().getAsJsonObject("books").entrySet()) {
					if (e.getValue().isJsonPrimitive()) {
						this.pages.put(e.getKey(), e.getValue().getAsInt());
					}
				}
			}
		} catch (Exception e) {
			Scribe.LOGGER.warn("Could not read remembered pages from {}", path, e);
		}
	}

	public void save() {
		if (!this.dirty) {
			return;
		}
		Path path = file();
		JsonObject books = new JsonObject();
		this.pages.forEach(books::addProperty);
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.add("books", books);
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
			this.dirty = false;
		} catch (IOException e) {
			Scribe.LOGGER.warn("Could not save remembered pages to {}", path, e);
		}
	}
}
