package scribe.client.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import scribe.client.text.Chapters;
import scribe.client.text.FormatCodes;
import scribe.client.text.PageBreaks;
import scribe.client.text.PageCodec;

public final class MarkdownBooks {
	private static final Pattern FRONT_FIELD = Pattern.compile("^\\s*([A-Za-z][A-Za-z0-9_-]*)\\s*:\\s*(.*?)\\s*$");
	private static final Pattern THEMATIC = Pattern.compile("^ {0,3}(?:(?:\\*[ \\t]*){3,}|(?:-[ \\t]*){3,}|(?:_[ \\t]*){3,})$");
	private static final Pattern ATX = Pattern.compile("^ {0,3}(#{1,6})(?:[ \\t]+(.*?))?(?:[ \\t]+#+)?[ \\t]*$");
	private static final Pattern SETEXT_EQ = Pattern.compile("^ {0,3}=+[ \\t]*$");
	private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,}).*$");
	private static final Pattern ORDERED = Pattern.compile("^(\\d{1,9})([.)])");
	private static final Pattern ENTITY = Pattern.compile("&(?:#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]{1,31});");
	private static final char[] STYLES = {'n', 'm', 'l', 'o'};
	private static final char BOLD = 'l';
	private static final char ITALIC = 'o';
	private static final char STRIKE = 'm';
	private static final char UNDERLINE = 'n';

	private MarkdownBooks() {
	}

	public static String write(BookFile book) {
		StringBuilder out = new StringBuilder();
		String title = book.title().map(FormatCodes::strip).map(String::strip).orElse("");
		String author = book.author().map(FormatCodes::strip).map(String::strip).orElse("");
		if (!title.isEmpty() || !author.isEmpty()) {
			out.append("---\n");
			if (!title.isEmpty()) {
				out.append("title: ").append(quoted(title)).append('\n');
			}
			if (!author.isEmpty()) {
				out.append("author: ").append(quoted(author)).append('\n');
			}
			out.append("---\n\n");
		}
		List<String> chunks = chunks(PageCodec.decodeBook(book.pages()));
		for (int i = 0; i < chunks.size(); i++) {
			if (i > 0) {
				out.append("\n\n---\n\n");
			}
			out.append(chunk(chunks.get(i)));
		}
		int end = out.length();
		while (end > 0 && out.charAt(end - 1) == '\n') {
			end--;
		}
		out.setLength(end);
		return out.append('\n').toString();
	}

	private static String quoted(String value) {
		return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
	}

	static List<String> chunks(List<String> pages) {
		List<String> out = new ArrayList<>();
		StringBuilder current = null;
		String previousText = null;
		for (String page : pages) {
			String text = PageBreaks.text(page);
			if (Chapters.isContentsPage(text)) {
				previousText = text;
				continue;
			}
			PageBreaks.Kind kind = previousText == null ? PageBreaks.Kind.HARD : PageBreaks.between(previousText, page);
			if (current == null || kind == PageBreaks.Kind.HARD) {
				if (current != null) {
					out.add(current.toString());
				}
				current = new StringBuilder(text);
			} else {
				current.append(kind.joiner()).append(text);
			}
			previousText = text;
		}
		if (current != null) {
			out.add(current.toString());
		}
		return out;
	}

	static String chunk(String text) {
		String[] lines = text.split("\n", -1);
		StringBuilder out = new StringBuilder();
		FormatCodes.State state = FormatCodes.State.EMPTY;
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			boolean chapter = Chapters.hasMark(line, 0, line.length());
			StringBuilder md = new StringBuilder();
			state = line(line, state, !chapter, md);
			String written = md.toString();
			boolean hasText = !written.isBlank();
			if (chapter && hasText) {
				written = "# " + written.strip();
			}
			out.append(written);
			if (i < lines.length - 1) {
				String next = lines[i + 1];
				boolean nextHasText = !FormatCodes.strip(next).isBlank();
				if (hasText && !chapter && nextHasText && !Chapters.hasMark(next, 0, next.length())) {
					out.append("  ");
				}
				out.append('\n');
			}
		}
		return out.toString();
	}

	private static FormatCodes.State line(String line, FormatCodes.State start, boolean lineStart, StringBuilder out) {
		StringBuilder chars = new StringBuilder();
		List<Integer> styles = new ArrayList<>();
		FormatCodes.State state = start;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (FormatCodes.startsPair(line, i)) {
				if (i + 1 < line.length()) {
					state = state.apply(line.charAt(i + 1));
				}
				i++;
				continue;
			}
			chars.append(c);
			styles.add(markdownStyles(state));
		}
		int n = chars.length();
		for (int i = 0; i < n; i++) {
			if (Character.isWhitespace(chars.charAt(i))) {
				int before = 0;
				for (int j = i - 1; j >= 0; j--) {
					if (!Character.isWhitespace(chars.charAt(j))) {
						before = styles.get(j);
						break;
					}
				}
				int after = 0;
				for (int j = i + 1; j < n; j++) {
					if (!Character.isWhitespace(chars.charAt(j))) {
						after = styles.get(j);
						break;
					}
				}
				styles.set(i, before & after);
			}
		}
		int leadingSpaces = 0;
		while (leadingSpaces < n && chars.charAt(leadingSpaces) == ' ') {
			leadingSpaces++;
		}
		boolean indent = leadingSpaces >= 4 && leadingSpaces < n;
		int orderedDot = -1;
		if (lineStart) {
			Matcher ordered = ORDERED.matcher(chars).region(leadingSpaces, n);
			if (ordered.lookingAt() && (ordered.end() >= n || chars.charAt(ordered.end()) == ' ')) {
				orderedDot = ordered.end() - 1;
			}
		}
		List<Character> open = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			int wanted = styles.get(i);
			int cut = open.size();
			for (int j = 0; j < open.size(); j++) {
				if ((wanted & bit(open.get(j))) == 0) {
					cut = j;
					break;
				}
			}
			for (int j = open.size() - 1; j >= cut; j--) {
				out.append(close(open.remove(j)));
			}
			for (char style : STYLES) {
				if ((wanted & bit(style)) != 0 && !open.contains(style)) {
					out.append(open(style));
					open.add(style);
				}
			}
			char c = chars.charAt(i);
			if (i < leadingSpaces) {
				out.append(indent ? "&nbsp;" : " ");
			} else if (lineStart && i == leadingSpaces) {
				out.append(escapeFirst(chars, i));
			} else if (i == orderedDot) {
				out.append('\\').append(c);
			} else {
				out.append(escape(chars, i));
			}
		}
		for (int j = open.size() - 1; j >= 0; j--) {
			out.append(close(open.get(j)));
		}
		return state;
	}

	private static int markdownStyles(FormatCodes.State state) {
		int bits = 0;
		for (char style : STYLES) {
			if (state.has(style)) {
				bits |= bit(style);
			}
		}
		return bits;
	}

	private static int bit(char style) {
		return 1 << FormatCodes.STYLE_CODES.indexOf(style);
	}

	private static String open(char style) {
		return switch (style) {
			case UNDERLINE -> "<u>";
			case STRIKE -> "~~";
			case BOLD -> "**";
			default -> "*";
		};
	}

	private static String close(char style) {
		return style == UNDERLINE ? "</u>" : open(style);
	}

	private static String escape(CharSequence chars, int i) {
		char c = chars.charAt(i);
		return switch (c) {
			case '\\', '`', '*', '_', '~', '[', ']', '<' -> "\\" + c;
			case '&' -> ENTITY.matcher(chars).region(i, chars.length()).lookingAt() ? "\\&" : "&";
			default -> String.valueOf(c);
		};
	}

	private static String escapeFirst(CharSequence chars, int i) {
		char c = chars.charAt(i);
		int n = chars.length();
		String rest = chars.subSequence(i, n).toString().strip();
		boolean spaceAfter = i + 1 >= n || chars.charAt(i + 1) == ' ' || chars.charAt(i + 1) == '\t';
		boolean needs = switch (c) {
			case '#' -> {
				int run = 1;
				while (i + run < n && chars.charAt(i + run) == '#') {
					run++;
				}
				yield run <= 6 && (i + run >= n || chars.charAt(i + run) == ' ' || chars.charAt(i + run) == '\t');
			}
			case '>' -> true;
			case '-' -> spaceAfter || rest.chars().allMatch(ch -> ch == '-' || ch == ' ' || ch == '\t');
			case '+' -> spaceAfter;
			case '=' -> rest.chars().allMatch(ch -> ch == '=' || ch == ' ' || ch == '\t');
			default -> false;
		};
		return needs ? "\\" + c : escape(chars, i);
	}

	public record Parsed(@Nullable String title, @Nullable String author, String text) {
	}

	public static Parsed read(String markdown) {
		String s = markdown.replace("\r\n", "\n").replace('\r', '\n');
		if (s.startsWith("﻿")) {
			s = s.substring(1);
		}
		List<String> lines = new ArrayList<>(List.of(s.split("\n", -1)));
		if (!lines.isEmpty() && lines.getLast().isEmpty()) {
			lines.removeLast();
		}
		String title = null;
		String author = null;
		int start = 0;
		if (lines.size() > 1 && lines.getFirst().strip().equals("---")) {
			int end = -1;
			for (int i = 1; i < lines.size() && i < 60; i++) {
				String l = lines.get(i).strip();
				if (l.equals("---") || l.equals("...")) {
					end = i;
					break;
				}
				if (!l.isEmpty() && !FRONT_FIELD.matcher(lines.get(i)).matches()) {
					break;
				}
			}
			if (end > 0) {
				for (int i = 1; i < end; i++) {
					Matcher field = FRONT_FIELD.matcher(lines.get(i));
					if (field.matches()) {
						String key = field.group(1).toLowerCase(Locale.ROOT);
						String value = unquote(field.group(2));
						if (key.equals("title")) {
							title = value;
						} else if (key.equals("author")) {
							author = value;
						}
					}
				}
				start = end + 1;
				if (start < lines.size() && lines.get(start).isBlank()) {
					start++;
				}
			}
		}
		List<String> out = new ArrayList<>();
		List<String> paragraph = new ArrayList<>();
		String fence = null;
		boolean comment = false;
		for (int i = start; i < lines.size(); i++) {
			String line = lines.get(i);
			if (fence != null) {
				char fenceChar = fence.charAt(0);
				if (line.strip().startsWith(fence) && line.strip().chars().allMatch(ch -> ch == fenceChar)) {
					fence = null;
				} else {
					out.add(line);
				}
				continue;
			}
			if (comment || line.contains("<!--")) {
				StringBuilder kept = new StringBuilder();
				int pos = 0;
				while (pos < line.length()) {
					if (comment) {
						int close = line.indexOf("-->", pos);
						if (close < 0) {
							break;
						}
						comment = false;
						pos = close + 3;
					} else {
						int open = line.indexOf("<!--", pos);
						if (open < 0) {
							kept.append(line, pos, line.length());
							break;
						}
						kept.append(line, pos, open);
						comment = true;
						pos = open + 4;
					}
				}
				if (kept.toString().isBlank()) {
					continue;
				}
				line = kept.toString();
			}
			Matcher fenceStart = FENCE.matcher(line);
			if (fenceStart.matches()) {
				flush(paragraph, out);
				fence = fenceStart.group(1);
				continue;
			}
			if (line.isBlank()) {
				flush(paragraph, out);
				out.add("");
				continue;
			}
			if (THEMATIC.matcher(line).matches()) {
				flush(paragraph, out);
				if (!out.isEmpty() && out.getLast().isEmpty()) {
					out.removeLast();
				}
				out.add("---");
				if (i + 1 < lines.size() && lines.get(i + 1).isBlank()) {
					i++;
				}
				continue;
			}
			Matcher heading = ATX.matcher(line);
			if (heading.matches()) {
				flush(paragraph, out);
				String text = heading.group(2) == null ? "" : inline(heading.group(2));
				out.add(FormatCodes.strip(text).isBlank() ? "" : Chapters.MARK + text);
				continue;
			}
			if (!paragraph.isEmpty() && SETEXT_EQ.matcher(line).matches()) {
				String last = paragraph.removeLast();
				flush(paragraph, out);
				out.add(Chapters.MARK + inline(last.strip()));
				continue;
			}
			paragraph.add(withoutBlockMarks(line));
		}
		flush(paragraph, out);
		return new Parsed(title, author, String.join("\n", out));
	}

	private static String unquote(String value) {
		String v = value.strip();
		if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
			StringBuilder out = new StringBuilder();
			for (int i = 1; i < v.length() - 1; i++) {
				char c = v.charAt(i);
				if (c == '\\' && i + 1 < v.length() - 1) {
					out.append(v.charAt(++i));
				} else {
					out.append(c);
				}
			}
			return out.toString();
		}
		if (v.length() >= 2 && v.startsWith("'") && v.endsWith("'")) {
			return v.substring(1, v.length() - 1).replace("''", "'");
		}
		return v;
	}

	private static String withoutBlockMarks(String line) {
		String l = line;
		while (true) {
			int i = 0;
			while (i < l.length() && i < 3 && l.charAt(i) == ' ') {
				i++;
			}
			if (i < l.length() && l.charAt(i) == '>') {
				l = l.substring(i + 1);
				if (l.startsWith(" ")) {
					l = l.substring(1);
				}
			} else {
				break;
			}
		}
		int end = l.length();
		while (end > 0 && (l.charAt(end - 1) == ' ' || l.charAt(end - 1) == '\t')) {
			end--;
		}
		int slashes = 0;
		while (end - slashes > 0 && l.charAt(end - slashes - 1) == '\\') {
			slashes++;
		}
		if (slashes % 2 == 1) {
			end--;
		}
		return l.substring(0, end);
	}

	private static void flush(List<String> paragraph, List<String> out) {
		if (paragraph.isEmpty()) {
			return;
		}
		String text = inline(String.join("\n", paragraph));
		out.addAll(List.of(text.split("\n", -1)));
		paragraph.clear();
	}

	private static final int TEXT = 0;
	private static final int DELIMITER = 1;
	private static final int TAG = 2;

	private static final class Node {
		final int kind;
		final char c;
		int count;
		final int original;
		boolean canOpen;
		boolean canClose;
		boolean active;
		final int tagBit;
		final boolean tagOn;

		Node(int kind, char c, int count, int tagBit, boolean tagOn) {
			this.kind = kind;
			this.c = c;
			this.count = count;
			this.original = count;
			this.tagBit = tagBit;
			this.tagOn = tagOn;
			this.active = kind == DELIMITER;
		}

		static Node text(char c) {
			return new Node(TEXT, c, 1, 0, false);
		}
	}

	private record Span(int from, int to, int bit) {
	}

	static String inline(String source) {
		List<Node> nodes = parse(source);
		List<Span> spans = emphasis(nodes);
		StringBuilder out = new StringBuilder();
		FormatCodes.State state = FormatCodes.State.EMPTY;
		int[] tagDepth = new int[FormatCodes.STYLE_CODES.length()];
		for (int k = 0; k < nodes.size(); k++) {
			Node node = nodes.get(k);
			if (node.kind == TAG) {
				int index = Integer.numberOfTrailingZeros(node.tagBit);
				tagDepth[index] = Math.max(0, tagDepth[index] + (node.tagOn ? 1 : -1));
				continue;
			}
			int bits = 0;
			for (Span span : spans) {
				if (span.from < k && k < span.to) {
					bits |= span.bit;
				}
			}
			for (int i = 0; i < tagDepth.length; i++) {
				if (tagDepth[i] > 0) {
					bits |= 1 << i;
				}
			}
			String text = node.kind == DELIMITER ? String.valueOf(node.c).repeat(node.count) : String.valueOf(node.c);
			for (int i = 0; i < text.length(); i++) {
				char c = text.charAt(i);
				if (c == '\n') {
					if (!state.isEmpty()) {
						out.append(FormatCodes.SECTION).append(FormatCodes.RESET);
						state = FormatCodes.State.EMPTY;
					}
					out.append('\n');
					continue;
				}
				FormatCodes.State wanted = new FormatCodes.State((char) 0, bits);
				out.append(FormatCodes.transition(state, wanted));
				state = wanted;
				out.append(c);
			}
		}
		if (!state.isEmpty()) {
			out.append(FormatCodes.SECTION).append(FormatCodes.RESET);
		}
		return out.toString();
	}

	private static List<Node> parse(String s) {
		List<Node> nodes = new ArrayList<>();
		List<int[]> skips = new ArrayList<>();
		int i = 0;
		while (i < s.length()) {
			for (int k = skips.size() - 1; k >= 0; k--) {
				if (skips.get(k)[0] == i) {
					i = skips.get(k)[1];
					skips.remove(k);
				}
			}
			if (i >= s.length()) {
				break;
			}
			char c = s.charAt(i);
			if (c == '\\' && i + 1 < s.length() && isAsciiPunctuation(s.charAt(i + 1))) {
				nodes.add(Node.text(s.charAt(i + 1)));
				i += 2;
				continue;
			}
			if (c == '`') {
				int run = runLength(s, i, '`');
				int close = s.indexOf("`".repeat(run), i + run);
				while (close >= 0 && runLength(s, close, '`') != run) {
					close = s.indexOf("`".repeat(run), close + runLength(s, close, '`'));
				}
				if (close >= 0) {
					String code = s.substring(i + run, close);
					if (code.length() >= 2 && code.startsWith(" ") && code.endsWith(" ") && !code.isBlank()) {
						code = code.substring(1, code.length() - 1);
					}
					for (char ch : code.toCharArray()) {
						nodes.add(Node.text(ch));
					}
					i = close + run;
					continue;
				}
				for (int k = 0; k < run; k++) {
					nodes.add(Node.text('`'));
				}
				i += run;
				continue;
			}
			if ((c == '[' || (c == '!' && i + 1 < s.length() && s.charAt(i + 1) == '[')) ) {
				int open = c == '!' ? i + 1 : i;
				int[] link = link(s, open);
				if (link != null) {
					skips.add(new int[] {link[0], link[1]});
					i = open + 1;
					continue;
				}
			}
			if (c == '<') {
				int close = s.indexOf('>', i + 1);
				if (close > i) {
					String inside = s.substring(i + 1, close);
					String tag = inside.strip().toLowerCase(Locale.ROOT);
					if (inside.matches("(?i)(https?://|mailto:)[^\\s<>]*")) {
						for (char ch : inside.toCharArray()) {
							nodes.add(Node.text(ch));
						}
						i = close + 1;
						continue;
					}
					if (inside.matches("/?[A-Za-z][A-Za-z0-9]*(\\s[^<>]*)?/?")) {
						boolean on = !tag.startsWith("/");
						String name = tag.replaceFirst("^/", "").replaceFirst("[\\s/].*$", "");
						int bit = switch (name) {
							case "b", "strong" -> bit(BOLD);
							case "i", "em" -> bit(ITALIC);
							case "s", "del", "strike" -> bit(STRIKE);
							case "u", "ins" -> bit(UNDERLINE);
							default -> 0;
						};
						if (name.equals("br")) {
							nodes.add(Node.text('\n'));
						} else if (bit != 0 && !tag.endsWith("/")) {
							nodes.add(new Node(TAG, '<', 0, bit, on));
						}
						i = close + 1;
						continue;
					}
				}
			}
			if (c == '&') {
				Matcher entity = ENTITY.matcher(s).region(i, s.length());
				if (entity.lookingAt()) {
					String decoded = decodeEntity(entity.group());
					if (decoded != null) {
						for (char ch : decoded.toCharArray()) {
							nodes.add(Node.text(ch));
						}
						i = entity.end();
						continue;
					}
				}
			}
			if (c == '*' || c == '_' || c == '~') {
				int run = runLength(s, i, c);
				Node delimiter = new Node(DELIMITER, c, run, 0, false);
				char before = i == 0 ? '\n' : s.charAt(i - 1);
				char after = i + run >= s.length() ? '\n' : s.charAt(i + run);
				boolean left = !Character.isWhitespace(after);
				boolean right = !Character.isWhitespace(before);
				if (c == '_') {
					delimiter.canOpen = left && (!right || isPunctuation(before));
					delimiter.canClose = right && (!left || isPunctuation(after));
				} else if (c == '~') {
					delimiter.canOpen = run <= 2 && left;
					delimiter.canClose = run <= 2 && right;
				} else {
					delimiter.canOpen = left;
					delimiter.canClose = right;
				}
				nodes.add(delimiter);
				i += run;
				continue;
			}
			nodes.add(Node.text(c));
			i++;
		}
		return nodes;
	}

	private static int @Nullable [] link(String s, int open) {
		int depth = 0;
		for (int i = open; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\') {
				i++;
				continue;
			}
			if (c == '[') {
				depth++;
			} else if (c == ']') {
				depth--;
				if (depth == 0) {
					if (i + 1 < s.length() && s.charAt(i + 1) == '(') {
						int parens = 0;
						for (int j = i + 1; j < s.length(); j++) {
							char d = s.charAt(j);
							if (d == '\\') {
								j++;
							} else if (d == '(') {
								parens++;
							} else if (d == ')' && --parens == 0) {
								return new int[] {i, j + 1};
							} else if (d == '\n') {
								return null;
							}
						}
						return null;
					}
					if (i + 1 < s.length() && s.charAt(i + 1) == '[') {
						int close = s.indexOf(']', i + 2);
						return close < 0 ? null : new int[] {i, close + 1};
					}
					return null;
				}
			}
		}
		return null;
	}

	private static List<Span> emphasis(List<Node> nodes) {
		List<Span> spans = new ArrayList<>();
		for (int k = 0; k < nodes.size(); k++) {
			Node closer = nodes.get(k);
			if (closer.kind != DELIMITER || !closer.active || !closer.canClose) {
				continue;
			}
			while (closer.count > 0) {
				int found = -1;
				for (int j = k - 1; j >= 0; j--) {
					Node opener = nodes.get(j);
					if (opener.kind != DELIMITER || !opener.active || !opener.canOpen || opener.c != closer.c || opener.count == 0) {
						continue;
					}
					if (closer.c == '~') {
						if (opener.count != closer.count) {
							continue;
						}
					} else if ((opener.canClose || closer.canOpen) && (opener.original + closer.original) % 3 == 0
						&& !(opener.original % 3 == 0 && closer.original % 3 == 0)) {
						continue;
					}
					found = j;
					break;
				}
				if (found < 0) {
					if (!closer.canOpen) {
						closer.active = false;
					}
					break;
				}
				Node opener = nodes.get(found);
				int use = closer.c == '~' ? closer.count : (closer.count >= 2 && opener.count >= 2 ? 2 : 1);
				int bit = closer.c == '~' ? bit(STRIKE) : use == 2 ? bit(BOLD) : bit(ITALIC);
				spans.add(new Span(found, k, bit));
				for (int j = found + 1; j < k; j++) {
					if (nodes.get(j).kind == DELIMITER) {
						nodes.get(j).active = false;
					}
				}
				opener.count -= use;
				closer.count -= use;
				if (opener.count == 0) {
					opener.active = false;
				}
			}
		}
		return spans;
	}

	private static int runLength(String s, int i, char c) {
		int n = 0;
		while (i + n < s.length() && s.charAt(i + n) == c) {
			n++;
		}
		return n;
	}

	private static boolean isAsciiPunctuation(char c) {
		return c < 128 && "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".indexOf(c) >= 0;
	}

	private static boolean isPunctuation(char c) {
		if (isAsciiPunctuation(c)) {
			return true;
		}
		int type = Character.getType(c);
		return type == Character.CONNECTOR_PUNCTUATION || type == Character.DASH_PUNCTUATION || type == Character.START_PUNCTUATION
			|| type == Character.END_PUNCTUATION || type == Character.INITIAL_QUOTE_PUNCTUATION || type == Character.FINAL_QUOTE_PUNCTUATION
			|| type == Character.OTHER_PUNCTUATION;
	}

	private static @Nullable String decodeEntity(String entity) {
		String name = entity.substring(1, entity.length() - 1);
		if (name.startsWith("#x") || name.startsWith("#X")) {
			return codePoint(name.substring(2), 16);
		}
		if (name.startsWith("#")) {
			return codePoint(name.substring(1), 10);
		}
		return switch (name) {
			case "nbsp" -> " ";
			case "amp" -> "&";
			case "lt" -> "<";
			case "gt" -> ">";
			case "quot" -> "\"";
			case "apos" -> "'";
			case "mdash" -> "—";
			case "ndash" -> "–";
			case "hellip" -> "…";
			case "laquo" -> "«";
			case "raquo" -> "»";
			case "copy" -> "©";
			default -> null;
		};
	}

	private static @Nullable String codePoint(String digits, int radix) {
		try {
			int cp = Integer.parseInt(digits, radix);
			return cp > 0 && Character.isValidCodePoint(cp) ? new String(Character.toChars(cp)) : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
