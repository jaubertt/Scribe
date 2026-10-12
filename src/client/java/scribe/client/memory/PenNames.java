package scribe.client.memory;

import scribe.RealAuthor;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;

public final class PenNames {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final int MAX_PER_ACCOUNT = 100;
	private static PenNames instance;

	private final Map<String, List<String>> names = new LinkedHashMap<>();

	public static PenNames get() {
		if (instance == null) {
			instance = new PenNames();
			instance.load();
		}
		return instance;
	}

	private static Path file() {
		return ScribeConfig.directory().resolve("pen_names.json");
	}

	public List<String> of(UUID account) {
		return List.copyOf(this.names.getOrDefault(account.toString(), List.of()));
	}

	public void remember(UUID account, String penName) {
		if (penName == null || penName.isBlank()) {
			return;
		}
		List<String> list = new ArrayList<>(this.names.getOrDefault(account.toString(), List.of()));
		list.remove(penName);
		list.addFirst(penName);
		while (list.size() > MAX_PER_ACCOUNT) {
			list.removeLast();
		}
		this.names.put(account.toString(), list);
		this.save();
	}

	public static boolean isOwnBook(ItemStack stack) {
		if (stack.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
			return true;
		}
		Player player = Minecraft.getInstance().player;
		return player != null && isOwnBook(stack, player.getUUID(), player.getGameProfile().name(), get().of(player.getUUID()));
	}

	public static boolean isOwnBook(ItemStack stack, UUID account, String name, List<String> penNames) {
		if (stack.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
			return true;
		}
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (written == null) {
			return false;
		}
		RealAuthor real = RealAuthor.of(stack);
		if (real != null && real.id() != null) {
			return real.id().equals(account);
		}
		String author = written.author();
		return author.equalsIgnoreCase(name) || penNames.contains(author);
	}

	private void load() {
		Path path = file();
		if (!Files.isRegularFile(path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			Map<String, List<String>> raw = GSON.fromJson(reader, new TypeToken<Map<String, List<String>>>() {}.getType());
			if (raw != null) {
				this.names.putAll(raw);
			}
		} catch (IOException | JsonParseException e) {
			Scribe.LOGGER.warn("Could not read {}", path, e);
		}
	}

	private void save() {
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this.names, writer);
			}
		} catch (IOException e) {
			Scribe.LOGGER.warn("Could not save {}", path, e);
		}
	}
}
