package scribe.client.io;

import scribe.BookLimits;
import scribe.client.text.PageFitter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;

public final class BookFiles {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public enum Format {
		JSON(".json", "scribe.files.format_json"),
		TXT(".txt", "scribe.files.format_txt"),
		MARKDOWN(".md", "scribe.files.format_md"),
		PDF(".pdf", "scribe.files.format_pdf"),
		PICTURES(".png", "scribe.files.format_png");

		private final String extension;
		private final String key;

		Format(String extension, String key) {
			this.extension = extension;
			this.key = key;
		}

		public String extension() {
			return this.extension;
		}

		public String key() {
			return this.key;
		}

		public boolean pictures() {
			return this == PDF || this == PICTURES;
		}

		public String rename(String fileName) {
			String lower = fileName.toLowerCase(Locale.ROOT);
			for (Format format : values()) {
				if (lower.endsWith(format.extension)) {
					return fileName.substring(0, fileName.length() - format.extension.length()) + this.extension;
				}
			}
			return fileName + this.extension;
		}
	}

	public enum Source { BOOKS, BACKUPS, BOOK_COPY }

	public record Entry(Path path, String name, Source source, long modified) {
	}

	private BookFiles() {
	}

	public static Path booksDir() {
		return FabricLoader.getInstance().getGameDir().resolve("scribe").resolve("books");
	}

	public static Path backupsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("scribe").resolve("backups");
	}

	public static Path signsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("scribe").resolve("signs");
	}

	public static boolean delete(Path path) {
		Path normalized = path.toAbsolutePath().normalize();
		boolean inside = normalized.startsWith(booksDir().toAbsolutePath().normalize())
			|| normalized.startsWith(backupsDir().toAbsolutePath().normalize())
			|| normalized.startsWith(signsDir().toAbsolutePath().normalize());
		if (!inside) {
			return false;
		}
		try {
			return Files.deleteIfExists(normalized);
		} catch (IOException e) {
			return false;
		}
	}

	public static Path bookCopyDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("bookcopy");
	}

	public static Path ensureBooksDir() throws IOException {
		return Files.createDirectories(booksDir());
	}

	public static List<Entry> list(boolean includeBackups) {
		List<Entry> entries = new ArrayList<>();
		collect(booksDir(), Source.BOOKS, entries);
		collect(bookCopyDir(), Source.BOOK_COPY, entries);
		if (includeBackups) {
			collect(backupsDir(), Source.BACKUPS, entries);
		}
		return entries;
	}

	public static List<String> commandNames() {
		List<String> names = new ArrayList<>();
		for (Entry e : list(false)) {
			if (!names.contains(e.name())) {
				names.add(e.name());
			}
		}
		return names;
	}

	private static void collect(Path dir, Source source, List<Entry> out) {
		if (!Files.isDirectory(dir)) {
			return;
		}
		List<Entry> found = new ArrayList<>();
		try (Stream<Path> stream = Files.list(dir)) {
			stream.filter(Files::isRegularFile).forEach(p -> {
				String name = p.getFileName().toString();
				if (name.startsWith(".")) {
					return;
				}
				long modified = 0;
				try {
					modified = Files.getLastModifiedTime(p).toMillis();
				} catch (IOException ignored) {
				}
				found.add(new Entry(p, name, source, modified));
			});
		} catch (IOException ignored) {
			return;
		}
		found.sort(Comparator.comparingLong(Entry::modified).reversed());
		out.addAll(found);
	}

	public static Path resolve(String name) {
		for (Path dir : List.of(booksDir(), bookCopyDir())) {
			Path p = dir.resolve(name).normalize();
			if (p.startsWith(dir) && Files.isRegularFile(p)) {
				return p;
			}
			if (!name.contains(".")) {
				for (String ext : List.of(".json", ".txt", ".md")) {
					Path withExt = dir.resolve(name + ext).normalize();
					if (withExt.startsWith(dir) && Files.isRegularFile(withExt)) {
						return withExt;
					}
				}
			}
		}
		return null;
	}

	public static BookFile read(Path path, PageFitter fitter) throws IOException {
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		if (name.endsWith(".md")) {
			MarkdownBooks.Parsed markdown = MarkdownBooks.read(Files.readString(path, StandardCharsets.UTF_8));
			return BookFile.of(markdown.title(), markdown.author(), fitter.paginate(markdown.text(), BookLimits.MAX_PAGES).marked());
		}
		if (name.endsWith(".txt")) {
			String text = Files.readString(path, StandardCharsets.UTF_8);
			return BookFile.of(null, null, fitter.paginate(text, BookLimits.MAX_PAGES).marked());
		}
		if (name.endsWith(".json")) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonElement json = JsonParser.parseReader(reader);
				return BookFile.CODEC.parse(new Dynamic<>(JsonOps.INSTANCE, json))
					.getOrThrow(message -> new IOException("Not a book file: " + message));
			} catch (RuntimeException e) {
				throw new IOException("Not a book file: " + e.getMessage(), e);
			}
		}
		CompoundTag tag = NbtIo.read(path);
		if (tag == null) {
			throw new IOException("Empty file");
		}
		return BookFile.CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, tag))
			.getOrThrow(message -> new IOException("Not a book file: " + message));
	}

	public static List<String> fitToBook(List<String> pages, PageFitter fitter) {
		List<String> out = new ArrayList<>();
		for (String page : pages) {
			if (out.size() >= BookLimits.MAX_PAGES) {
				break;
			}
			if (fitter.fits(page)) {
				out.add(page);
			} else {
				for (String part : fitter.paginate(page, BookLimits.MAX_PAGES - out.size()).marked()) {
					out.add(part);
				}
			}
		}
		if (out.isEmpty()) {
			out.add("");
		}
		return out;
	}

	public static void write(Path path, BookFile book) throws IOException {
		Files.createDirectories(path.getParent());
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		if (name.endsWith(".md")) {
			Files.writeString(path, MarkdownBooks.write(book), StandardCharsets.UTF_8);
			return;
		}
		if (name.endsWith(".txt")) {
			Files.writeString(path, String.join("\n---\n", book.pages()), StandardCharsets.UTF_8);
			return;
		}
		JsonElement json = BookFile.CODEC.encodeStart(JsonOps.INSTANCE, book)
			.getOrThrow(message -> new IOException("Could not encode book: " + message));
		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			GSON.toJson(json, writer);
		}
	}

	public static String fileName(String raw) {
		String base = raw == null ? "" : raw.trim();
		String ext = "";
		String lower = base.toLowerCase(Locale.ROOT);
		for (String e : List.of(".json", ".txt", ".md")) {
			if (lower.endsWith(e)) {
				ext = e;
				base = base.substring(0, base.length() - e.length());
				break;
			}
		}
		String cleaned = base.replaceAll("§.", "").replaceAll("[^A-Za-z0-9_\\-]+", "_").replaceAll("_+", "_");
		cleaned = cleaned.replaceAll("^_+|_+$", "");
		if (cleaned.isEmpty()) {
			cleaned = "book";
		}
		if (cleaned.length() > 60) {
			cleaned = cleaned.substring(0, 60);
		}
		return cleaned + (ext.isEmpty() ? ".json" : ext);
	}

	public static String freeName(String fileName) {
		Path dir = booksDir();
		if (!Files.exists(dir.resolve(fileName))) {
			return fileName;
		}
		int dot = fileName.lastIndexOf('.');
		String base = dot > 0 ? fileName.substring(0, dot) : fileName;
		String ext = dot > 0 ? fileName.substring(dot) : "";
		for (int i = 2; i < 10_000; i++) {
			String candidate = base + "_" + i + ext;
			if (!Files.exists(dir.resolve(candidate))) {
				return candidate;
			}
		}
		return fileName;
	}
}
