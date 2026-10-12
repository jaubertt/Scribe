package scribe.client.text;

import scribe.BookLimits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;

public final class ContentsPage {
	private static final int FIRST_PAGE_ENTRIES = BookLimits.PAGE_LINES - 2;
	private static final int NEXT_PAGE_ENTRIES = BookLimits.PAGE_LINES;

	private ContentsPage() {
	}

	public static boolean has(List<String> pages) {
		return pages.stream().anyMatch(Chapters::isContentsPage);
	}

	public static List<String> remove(List<String> pages) {
		List<String> out = new ArrayList<>(pages);
		out.removeIf(Chapters::isContentsPage);
		if (out.isEmpty()) {
			out.add("");
		}
		return out;
	}

	public static List<String> print(List<String> pages, Font font, String heading) {
		int at = firstContentsIndex(pages);
		List<String> body = remove(pages);
		if (at < 0) {
			List<Chapters.Chapter> chapters = Chapters.find(body);
			at = chapters.isEmpty() ? 0 : chapters.getFirst().page();
		}
		return insertAt(body, Math.min(at, body.size()), font, heading);
	}

	public static List<String> refresh(List<String> pages, Font font, String heading) {
		int at = firstContentsIndex(pages);
		if (at < 0) {
			return pages;
		}
		List<String> body = remove(pages);
		return insertAt(body, Math.min(at, body.size()), font, heading);
	}

	public static int firstContentsIndex(List<String> pages) {
		for (int i = 0; i < pages.size(); i++) {
			if (Chapters.isContentsPage(pages.get(i))) {
				return i;
			}
		}
		return -1;
	}

	private static List<String> insertAt(List<String> body, int at, Font font, String heading) {
		List<Chapters.Chapter> chapters = Chapters.find(body);
		int count = pagesNeeded(chapters.size());
		if (body.size() + count > BookLimits.MAX_PAGES) {
			count = Math.max(0, BookLimits.MAX_PAGES - body.size());
		}
		List<String> entries = new ArrayList<>();
		for (Chapters.Chapter chapter : chapters) {
			int page = (chapter.page() >= at ? chapter.page() + count : chapter.page()) + 1;
			entries.add(line(font, chapter.title(), page));
		}
		List<String> contents = new ArrayList<>();
		int next = 0;
		for (int p = 0; p < count; p++) {
			StringBuilder page = new StringBuilder(Chapters.CONTENTS_MARK);
			int room = p == 0 ? FIRST_PAGE_ENTRIES : NEXT_PAGE_ENTRIES;
			if (p == 0) {
				page.append(FormatCodes.SECTION).append('l').append(heading).append(FormatCodes.SECTION).append('r').append("\n");
			}
			for (int i = 0; i < room && next < entries.size(); i++) {
				page.append('\n').append(entries.get(next++));
			}
			contents.add(page.toString().replace(Chapters.CONTENTS_MARK + "\n", Chapters.CONTENTS_MARK));
		}
		List<String> out = new ArrayList<>(body);
		out.addAll(at, contents);
		return out;
	}

	static int pagesNeeded(int entries) {
		if (entries <= FIRST_PAGE_ENTRIES) {
			return 1;
		}
		return 1 + (entries - FIRST_PAGE_ENTRIES + NEXT_PAGE_ENTRIES - 1) / NEXT_PAGE_ENTRIES;
	}

	static String line(Font font, String title, int page) {
		String number = Integer.toString(page);
		int width = BookLimits.PAGE_WIDTH;
		int space = font.width(" ");
		int dot = Math.max(1, font.width("."));
		int titleRoom = width - font.width(number) - space * 2 - dot * 2;
		String fitted = title;
		if (font.width(fitted) > titleRoom) {
			fitted = font.plainSubstrByWidth(title, titleRoom - font.width("...")).stripTrailing() + "...";
		}
		int dots = (width - font.width(fitted) - font.width(number) - space * 2) / dot;
		String result = fitted + " " + ".".repeat(Math.max(0, dots)) + " " + number;
		while (font.width(result) > width && dots > 0) {
			dots--;
			result = fitted + " " + ".".repeat(dots) + " " + number;
		}
		return result;
	}
}
