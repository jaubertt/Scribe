package scribe.client.text;

import scribe.BookLimits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringUtil;

public final class PageFitter {
	private static final int MAX_CARRIED_WORD = 40;

	private final Font font;

	public PageFitter(Font font) {
		this.font = font;
	}

	public Font font() {
		return this.font;
	}

	public int lineCount(String text) {
		return this.font.getSplitter().splitLines(text, BookLimits.PAGE_WIDTH, Style.EMPTY).size()
			+ (StringUtil.endsWithNewLine(text) ? 1 : 0);
	}

	public boolean fits(String text) {
		return text.length() <= BookLimits.PAGE_CHARS && PageCodec.savedLength(this.font, text) <= BookLimits.PAGE_CHARS
			&& lineCount(text) <= BookLimits.PAGE_LINES;
	}

	public int width(String text) {
		return this.font.width(text);
	}

	public int fitLength(String text) {
		if (this.fits(text)) {
			return text.length();
		}
		int low = 0;
		int high = Math.min(text.length(), BookLimits.PAGE_CHARS + 1);
		while (high - low > 1) {
			int mid = (low + high) >>> 1;
			if (this.fits(text.substring(0, mid))) {
				low = mid;
			} else {
				high = mid;
			}
		}
		int cut = safeCut(text, low);
		if (cut <= 0) {
			return 0;
		}
		if (cut < text.length() && !Character.isWhitespace(text.charAt(cut)) && !Character.isWhitespace(text.charAt(cut - 1))) {
			for (int i = cut - 1; i > 0 && cut - i <= MAX_CARRIED_WORD; i--) {
				if (Character.isWhitespace(text.charAt(i))) {
					return i + 1;
				}
			}
		}
		return cut;
	}

	public static int safeCut(String text, int cut) {
		int c = Math.clamp(cut, 0, text.length());
		if (c > 0 && FormatCodes.startsPair(text, c - 1)) {
			c--;
		} else if (c > 0 && c < text.length() && Character.isHighSurrogate(text.charAt(c - 1)) && Character.isLowSurrogate(text.charAt(c))) {
			c--;
		}
		return c;
	}

	public static int boundaryBreak(String text, int cut) {
		int k = FormatCodes.runEnd(text, cut);
		return k < text.length() && text.charAt(k) == '\n' ? k : -1;
	}

	public int pageLength(String text) {
		int n = this.fitLength(text);
		if (n < text.length() && FormatCodes.strip(text.substring(0, n)).isEmpty()) {
			int i = 0;
			while (FormatCodes.startsPair(text, i) && i + 1 < text.length()) {
				i += 2;
			}
			n = Math.min(text.length(), i + 1);
		}
		return n;
	}

	public record Pagination(List<String> pages, boolean truncated, List<PageBreaks.Kind> breaks) {
		public List<String> marked() {
			List<String> out = new ArrayList<>(this.pages.size());
			for (int i = 0; i < this.pages.size(); i++) {
				out.add(PageBreaks.page(i < this.breaks.size() ? this.breaks.get(i) : PageBreaks.Kind.HARD, this.pages.get(i)));
			}
			return out;
		}
	}

	public Pagination paginate(String text, int maxPages) {
		List<String> pages = new ArrayList<>();
		List<PageBreaks.Kind> breaks = new ArrayList<>();
		String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
		if (normalized.startsWith("---\n")) {
			normalized = normalized.substring(4);
		}
		String[] chunks = normalized.split("\n---[ \t]*(?:\n|$)", -1);
		boolean truncated = false;
		for (int c = 0; c < chunks.length; c++) {
			PageBreaks.Kind kind = PageBreaks.markOf(chunks[c]);
			String remaining = PageCodec.decodeText(PageBreaks.text(chunks[c]));
			if (c == chunks.length - 1 && remaining.isEmpty() && c > 0) {
				break;
			}
			do {
				if (pages.size() >= maxPages) {
					truncated = true;
					break;
				}
				int n = this.pageLength(remaining);
				int lineBreak = boundaryBreak(remaining, n);
				String rest = lineBreak >= 0
					? remaining.substring(n, lineBreak) + remaining.substring(lineBreak + 1)
					: remaining.substring(n);
				pages.add(remaining.substring(0, n));
				breaks.add(kind);
				kind = lineBreak >= 0 ? PageBreaks.Kind.SOFT_NEWLINE : PageBreaks.Kind.SOFT;
				String carried = kind == PageBreaks.Kind.SOFT ? Alignment.carried(remaining, n) : "";
				remaining = FormatCodes.strip(rest).isBlank() ? "" : FormatCodes.normalizeLead(carried + FormatCodes.stateAt(remaining, n).codes() + rest);
			} while (!remaining.isEmpty());
			if (truncated) {
				break;
			}
		}
		if (pages.isEmpty()) {
			pages.add("");
			breaks.add(PageBreaks.Kind.HARD);
		}
		return new Pagination(pages, truncated, breaks);
	}
}
