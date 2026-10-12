package scribe.client.text;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

public final class BookCounts {
	private BookCounts() {
	}

	public static int words(List<String> pages) {
		int words = 0;
		for (String page : pages) {
			if (!Chapters.isContentsPage(page)) {
				words += words(FormatCodes.strip(PageCodec.decodeText(page)));
			}
		}
		return words;
	}

	public static int words(String text) {
		int words = 0;
		boolean inWord = false;
		for (int i = 0; i < text.length(); ) {
			int c = text.codePointAt(i);
			i += Character.charCount(c);
			if (ownWord(c)) {
				words++;
				inWord = false;
			} else if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
				inWord = false;
			} else if (Character.isLetterOrDigit(c) && !inWord) {
				words++;
				inWord = true;
			}
		}
		return words;
	}

	static boolean ownWord(int c) {
		Character.UnicodeScript script = Character.UnicodeScript.of(c);
		return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA;
	}

	public static int percent(int page, int pages) {
		if (pages <= 0) {
			return 0;
		}
		return Math.max(0, Math.min(100, (page + 1) * 100 / pages));
	}

	public static List<Component> pageNumberLines(int page, int pages, int words, int chapters) {
		String[] jump = Component.translatable("scribe.button.page_jump", page + 1, pages).getString().split("\n", 2);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(jump[0]));
		lines.add(Component.translatable("scribe.book.progress", percent(page, pages)));
		lines.add(words == 1 ? Component.translatable("scribe.tooltip.words.one") : Component.translatable("scribe.tooltip.words.many", words));
		if (chapters > 0) {
			lines.add(chapters == 1 ? Component.translatable("scribe.tooltip.chapters.one") : Component.translatable("scribe.tooltip.chapters.many", chapters));
		}
		if (jump.length > 1) {
			lines.add(Component.literal(jump[1]));
		}
		return lines;
	}
}
