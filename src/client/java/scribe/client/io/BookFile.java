package scribe.client.io;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;

public record BookFile(Optional<String> title, Optional<String> author, List<String> pages) {
	public static final Codec<BookFile> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.optionalFieldOf("title").forGetter(BookFile::title),
		Codec.STRING.optionalFieldOf("author").forGetter(BookFile::author),
		Codec.STRING.listOf().fieldOf("pages").forGetter(BookFile::pages)
	).apply(i, BookFile::new));

	public BookFile {
		pages = List.copyOf(pages);
	}

	public static BookFile of(String title, String author, List<String> pages) {
		return new BookFile(Optional.ofNullable(blankToNull(title)), Optional.ofNullable(blankToNull(author)), pages);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}
}
