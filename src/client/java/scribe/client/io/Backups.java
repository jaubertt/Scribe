package scribe.client.io;

import scribe.Scribe;
import scribe.config.ScribeConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class Backups {
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	private static final Map<String, Integer> LAST_SAVED = new HashMap<>();

	private Backups() {
	}

	public static void save(String bookKey, BookFile book) {
		if (!ScribeConfig.get().keepBackups) {
			return;
		}
		boolean empty = book.pages().stream().allMatch(String::isBlank);
		if (empty) {
			return;
		}
		int contentHash = book.hashCode();
		if (bookKey != null && Integer.valueOf(contentHash).equals(LAST_SAVED.get(bookKey))) {
			return;
		}
		String label = book.title().orElseGet(() -> firstWords(book.pages()));
		String name = STAMP.format(LocalDateTime.now()) + "_" + BookFiles.fileName(label);
		Path path = BookFiles.backupsDir().resolve(name);
		try {
			BookFiles.write(path, book);
			if (bookKey != null) {
				LAST_SAVED.put(bookKey, contentHash);
			}
			prune();
		} catch (IOException e) {
			Scribe.LOGGER.warn("Could not write book backup {}", path, e);
		}
	}

	private static String firstWords(List<String> pages) {
		for (String page : pages) {
			String plain = page.replaceAll("§.", "").strip();
			if (!plain.isEmpty()) {
				String[] words = plain.split("\\s+");
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < Math.min(5, words.length); i++) {
					if (i > 0) {
						sb.append('_');
					}
					sb.append(words[i]);
				}
				return sb.toString();
			}
		}
		return "book";
	}

	private static void prune() throws IOException {
		Path dir = BookFiles.backupsDir();
		List<Path> files = new ArrayList<>();
		try (Stream<Path> stream = Files.list(dir)) {
			stream.filter(Files::isRegularFile).forEach(files::add);
		}
		int max = ScribeConfig.get().maxBackups;
		if (files.size() <= max) {
			return;
		}
		files.sort(Comparator.comparing(p -> p.getFileName().toString()));
		for (int i = 0; i < files.size() - max; i++) {
			Files.deleteIfExists(files.get(i));
		}
	}
}
