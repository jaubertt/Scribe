package scribe.colors;

import scribe.Scribe;
import scribe.config.ScribeConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DyeOptions {
	public static final String FILE_NAME = "dyes.json";
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static volatile DyeOptions current;

	@SerializedName("crafted_books_keep_dye")
	public boolean craftedBooksKeepDye = true;
	@SerializedName("traded_books_keep_dye")
	public boolean tradedBooksKeepDye = true;
	@SerializedName("written_books_shine")
	public boolean writtenBooksShine = true;
	@SerializedName("enchanted_books_shine")
	public boolean enchantedBooksShine = true;

	public static DyeOptions get() {
		DyeOptions options = current;
		if (options == null) {
			options = read(file());
			current = options;
		}
		return options;
	}

	public static void reload() {
		current = read(file());
	}

	public static Path file() {
		return ScribeConfig.directory().resolve(FILE_NAME);
	}

	static DyeOptions read(Path path) {
		if (!Files.isRegularFile(path)) {
			DyeOptions fresh = new DyeOptions();
			fresh.write(path);
			return fresh;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			DyeOptions options = JSON.fromJson(reader, DyeOptions.class);
			return options == null ? new DyeOptions() : options;
		} catch (IOException | JsonParseException e) {
			Scribe.LOGGER.error("Could not read {}, books keep their dye and shine as usual until it is fixed", path, e);
			return new DyeOptions();
		}
	}

	void write(Path path) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				JSON.toJson(this, writer);
			}
		} catch (IOException e) {
			Scribe.LOGGER.error("Could not write {}", path, e);
		}
	}
}
