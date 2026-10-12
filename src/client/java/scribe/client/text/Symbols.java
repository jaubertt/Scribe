package scribe.client.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;

public final class Symbols {
	public record Category(String icon, String key, int[][] ranges) {
	}

	public static final List<Category> CATEGORIES = List.of(
		new Category("⭐", "symbols", new int[][] {{0x2600, 0x26FF}, {0x2700, 0x27BF}, {0x2300, 0x23FF}, {0x2100, 0x214F}, {0x2B50, 0x2B59}, {0x3000, 0x303F}}),
		new Category("→", "arrows", new int[][] {{0x2190, 0x21FF}, {0x27F0, 0x27FF}, {0x2900, 0x297F}, {0x2B00, 0x2B4F}, {0x2B5A, 0x2BFF}}),
		new Category("●", "shapes", new int[][] {{0x25A0, 0x25FF}, {0x2580, 0x259F}, {0x2500, 0x257F}, {0x2800, 0x28FF}}),
		new Category("♪", "music", new int[][] {{0x2669, 0x266F}, {0x2654, 0x2667}, {0x2680, 0x2685}, {0x1D100, 0x1D1FF}, {0x1F0A0, 0x1F0FF}, {0x1F000, 0x1F09F}}),
		new Category("∞", "math", new int[][] {{0x2200, 0x22FF}, {0x2A00, 0x2AFF}, {0x2150, 0x218F}, {0x2070, 0x209F}, {0x20A0, 0x20CF}, {0x00A1, 0x00BF}, {0x00D7, 0x00D7}, {0x00F7, 0x00F7}, {0x2010, 0x205E}}),
		new Category("Ω", "latin_greek", new int[][] {{0x0370, 0x03FF}, {0x00C0, 0x00D6}, {0x00D8, 0x00F6}, {0x00F8, 0x024F}, {0x0250, 0x02AF}, {0x1E00, 0x1EFF}}),
		new Category("Я", "alphabets", new int[][] {{0x0400, 0x04FF}, {0x0530, 0x058F}, {0x0590, 0x05FF}, {0x0600, 0x06FF}, {0x0E00, 0x0E7F}, {0x10A0, 0x10FF}, {0x16A0, 0x16FF}, {0x3040, 0x30FF}}),
		new Category("☺", "faces", new int[][] {{0x2639, 0x263C}, {0x1F600, 0x1F64F}, {0x1F300, 0x1F5FF}, {0x1F680, 0x1F6FF}, {0x1F900, 0x1F9FF}})
	);

	private static final Map<Integer, List<String>> CACHE = new HashMap<>();

	private Symbols() {
	}

	public static List<String> category(int index, IntPredicate hasGlyph) {
		return CACHE.computeIfAbsent(index, i -> {
			List<String> out = new ArrayList<>();
			for (int[] range : CATEGORIES.get(i).ranges()) {
				for (int cp = range[0]; cp <= range[1]; cp++) {
					if (usable(cp, hasGlyph)) {
						out.add(Character.toString(cp));
					}
				}
			}
			return out;
		});
	}

	public static List<String> search(String query, IntPredicate hasGlyph, int limit) {
		String[] words = query.toUpperCase(Locale.ROOT).trim().split("\\s+");
		List<String> out = new ArrayList<>();
		if (query.isBlank()) {
			return out;
		}
		for (int i = 0; i < CATEGORIES.size() && out.size() < limit; i++) {
			for (String symbol : category(i, hasGlyph)) {
				String name = Character.getName(symbol.codePointAt(0));
				if (name != null && containsAll(name, words) && !out.contains(symbol)) {
					out.add(symbol);
					if (out.size() >= limit) {
						break;
					}
				}
			}
		}
		return out;
	}

	private static boolean containsAll(String name, String[] words) {
		for (String word : words) {
			if (!name.contains(word)) {
				return false;
			}
		}
		return true;
	}

	static boolean usable(int cp, IntPredicate hasGlyph) {
		if (!Character.isDefined(cp) || Character.isISOControl(cp) || cp == 0xA7) {
			return false;
		}
		int type = Character.getType(cp);
		if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK
			|| type == Character.FORMAT || type == Character.SPACE_SEPARATOR || type == Character.LINE_SEPARATOR
			|| type == Character.PARAGRAPH_SEPARATOR || type == Character.UNASSIGNED) {
			return false;
		}
		return hasGlyph.test(cp);
	}

	public static String name(String symbol) {
		int cp = symbol.codePointAt(0);
		String name = Character.getName(cp);
		if (name == null) {
			return "";
		}
		StringBuilder out = new StringBuilder(name.length());
		boolean start = true;
		for (char c : name.toCharArray()) {
			out.append(start ? c : Character.toLowerCase(c));
			start = c == ' ' || c == '-';
		}
		return out.toString();
	}

	public static String codePoint(String symbol) {
		return String.format(Locale.ROOT, "U+%04X", symbol.codePointAt(0));
	}
}
