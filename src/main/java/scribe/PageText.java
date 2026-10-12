package scribe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class PageText {
	public static final char SECTION = '§';
	public static final String COLOR_CODES = "0123456789abcdef";
	public static final String STYLE_CODES = "klmno";
	public static final String MARKERS = "#*=>+&_/|";
	public static final char CHAPTER_MARK = '#';
	public static final char PAD_MARK = '_';
	public static final char BREAK_SPACE_MARK = '/';
	public static final char BREAK_WORD_MARK = '|';
	public static final char SOFT_MARK = '+';
	public static final char NEWLINE_MARK = '&';
	private static final char RESET = 'r';

	private PageText() {
	}

	public record Heading(int page, int start, String title) {
	}

	public static boolean isCode(char c) {
		return ChatFormatting.getByCode(c) != null || MARKERS.indexOf(c) >= 0;
	}

	public static boolean hiddenBySectionBefore(String s, int index) {
		int run = 0;
		for (int j = index - 1; j >= 0 && s.charAt(j) == SECTION; j--) {
			run++;
		}
		return (run & 1) == 1;
	}

	public static boolean isCodeAt(String s, int index) {
		return index >= 0
			&& index + 1 < s.length()
			&& s.charAt(index) == SECTION
			&& isCode(s.charAt(index + 1))
			&& !hiddenBySectionBefore(s, index);
	}

	public static String strip(String s) {
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == SECTION) {
				i++;
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}

	public static boolean isBreakMark(char c) {
		return c == BREAK_SPACE_MARK || c == BREAK_WORD_MARK;
	}

	public static boolean isPageBreakMark(char c) {
		return c == SOFT_MARK || c == NEWLINE_MARK;
	}

	public static boolean isInsertedBreak(String text, int index) {
		return index >= 2 && index < text.length() && text.charAt(index) == '\n'
			&& isBreakMark(text.charAt(index - 1)) && isCodeAt(text, index - 2);
	}

	public static String decodeText(String saved) {
		if (saved.indexOf(SECTION) < 0) {
			return saved;
		}
		StringBuilder out = new StringBuilder(saved.length());
		int rowStart = 0;
		for (int i = 0; i < saved.length(); i++) {
			char c = saved.charAt(i);
			if (c == '\n') {
				out.append(c);
				rowStart = out.length();
				continue;
			}
			if (!isCodeAt(saved, i)) {
				out.append(c);
				continue;
			}
			char code = saved.charAt(i + 1);
			if (isBreakMark(code)) {
				if (i + 2 < saved.length() && saved.charAt(i + 2) == '\n') {
					if (code == BREAK_SPACE_MARK) {
						out.append(' ');
					}
					i++;
					rowStart = out.length();
				}
				i++;
			} else if (code == PAD_MARK) {
				int keep = out.length();
				while (keep > rowStart && out.charAt(keep - 1) == ' ') {
					keep--;
				}
				out.setLength(keep);
				i++;
			} else if (isPageBreakMark(code)) {
				i++;
			} else {
				out.append(c).append(code);
				i++;
			}
		}
		return out.toString();
	}

	public static boolean isContentsPage(String page) {
		return ContentsLinks.isContentsPage(page);
	}

	public static int paragraphEnd(String text, int index) {
		int from = Math.clamp(index, 0, text.length());
		while (true) {
			int nl = text.indexOf('\n', from);
			if (nl < 0) {
				return text.length();
			}
			if (!isInsertedBreak(text, nl)) {
				return nl;
			}
			from = nl + 1;
		}
	}

	public static boolean hasChapterMark(String text, int start, int end) {
		int i = start;
		while (i + 1 < end && isCodeAt(text, i)) {
			if (text.charAt(i + 1) == CHAPTER_MARK) {
				return true;
			}
			i += 2;
		}
		return false;
	}

	public static List<Heading> headings(List<String> pages) {
		List<Heading> out = new ArrayList<>();
		for (int p = 0; p < pages.size(); p++) {
			String text = pages.get(p);
			if (isContentsPage(text)) {
				continue;
			}
			int start = 0;
			while (true) {
				int end = paragraphEnd(text, start);
				if (hasChapterMark(text, start, end)) {
					String title = strip(decodeText(text.substring(start, end))).strip();
					if (!title.isEmpty()) {
						out.add(new Heading(p, start, title));
					}
				}
				if (end >= text.length()) {
					break;
				}
				start = end + 1;
			}
		}
		return out;
	}

	public static int rgb(char colorCode) {
		ChatFormatting format = ChatFormatting.getByCode(colorCode);
		TextColor color = format == null ? null : TextColor.fromLegacyFormat(format);
		return color == null ? 0x000000 : color.getValue();
	}

	public static char nearestColorCode(int rgb) {
		char best = '0';
		long bestDistance = Long.MAX_VALUE;
		for (char code : COLOR_CODES.toCharArray()) {
			int other = rgb(code);
			long dr = ((rgb >> 16) & 0xFF) - ((other >> 16) & 0xFF);
			long dg = ((rgb >> 8) & 0xFF) - ((other >> 8) & 0xFF);
			long db = (rgb & 0xFF) - (other & 0xFF);
			long distance = dr * dr + dg * dg + db * db;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = code;
			}
		}
		return best;
	}

	public static String toLegacy(FormattedText text) {
		StringBuilder out = new StringBuilder();
		Look[] written = {Look.PLAIN};
		text.visit((style, contents) -> {
			Look wanted = Look.of(style);
			out.append(written[0].to(wanted));
			written[0] = wanted.after(contents);
			out.append(contents);
			return Optional.empty();
		}, Style.EMPTY);
		return out.toString();
	}

	private record Look(char color, int styles) {
		static final Look PLAIN = new Look((char) 0, 0);

		static Look of(Style style) {
			Look look = PLAIN;
			TextColor color = style.getColor();
			if (color != null) {
				look = new Look(nearestColorCode(color.getValue()), look.styles);
			}
			if (style.isObfuscated()) look = look.with('k');
			if (style.isBold()) look = look.with('l');
			if (style.isStrikethrough()) look = look.with('m');
			if (style.isUnderlined()) look = look.with('n');
			if (style.isItalic()) look = look.with('o');
			return look;
		}

		private static int bit(char style) {
			int i = STYLE_CODES.indexOf(Character.toLowerCase(style));
			return i < 0 ? 0 : 1 << i;
		}

		Look with(char style) {
			return new Look(this.color, this.styles | bit(style));
		}

		Look apply(char code) {
			char c = Character.toLowerCase(code);
			if (c == RESET) {
				return PLAIN;
			}
			if (COLOR_CODES.indexOf(c) >= 0) {
				return new Look(c, 0);
			}
			if (STYLE_CODES.indexOf(c) >= 0) {
				return new Look(this.color, this.styles | bit(c));
			}
			return this;
		}

		Look after(String text) {
			Look out = this;
			for (int i = 0; i < text.length(); i++) {
				if (text.charAt(i) == SECTION) {
					if (i + 1 < text.length()) {
						out = out.apply(text.charAt(i + 1));
					}
					i++;
				}
			}
			return out;
		}

		String styleCodes() {
			StringBuilder out = new StringBuilder();
			for (int i = 0; i < STYLE_CODES.length(); i++) {
				if ((this.styles & (1 << i)) != 0) {
					out.append(SECTION).append(STYLE_CODES.charAt(i));
				}
			}
			return out.toString();
		}

		String to(Look next) {
			if (this.equals(next)) {
				return "";
			}
			StringBuilder out = new StringBuilder();
			boolean onlyAdds = this.color == next.color && (this.styles & ~next.styles) == 0;
			if (onlyAdds) {
				for (int i = 0; i < STYLE_CODES.length(); i++) {
					int bit = 1 << i;
					if ((next.styles & bit) != 0 && (this.styles & bit) == 0) {
						out.append(SECTION).append(STYLE_CODES.charAt(i));
					}
				}
				return out.toString();
			}
			if (next.color != 0) {
				out.append(SECTION).append(next.color);
			} else {
				out.append(SECTION).append(RESET);
			}
			return out.append(next.styleCodes()).toString();
		}
	}
}
