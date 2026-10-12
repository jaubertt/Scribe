package scribe.client.text;

import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class FormatCodes {
	public static final char SECTION = '§';
	public static final String COLOR_CODES = "0123456789abcdef";
	public static final String STYLE_CODES = "klmno";
	public static final char RESET = 'r';
	public static final String MARKERS = "#*=>+&_/|";

	private FormatCodes() {
	}

	public static boolean isCode(char c) {
		return ChatFormatting.getByCode(c) != null || MARKERS.indexOf(c) >= 0;
	}

	public static boolean isMarker(char c) {
		return MARKERS.indexOf(c) >= 0;
	}

	public static boolean isColor(char c) {
		return COLOR_CODES.indexOf(Character.toLowerCase(c)) >= 0;
	}

	public static boolean isStyle(char c) {
		return STYLE_CODES.indexOf(Character.toLowerCase(c)) >= 0;
	}

	private static boolean hiddenBySectionBefore(String s, int index) {
		int run = 0;
		for (int j = index - 1; j >= 0 && s.charAt(j) == SECTION; j--) {
			run++;
		}
		return (run & 1) == 1;
	}

	public static boolean startsPair(String s, int index) {
		return index >= 0 && index < s.length() && s.charAt(index) == SECTION && !hiddenBySectionBefore(s, index);
	}

	public static boolean isCodeAt(String s, int index) {
		return index >= 0
			&& index + 1 < s.length()
			&& s.charAt(index) == SECTION
			&& isCode(s.charAt(index + 1))
			&& !hiddenBySectionBefore(s, index);
	}

	public static boolean splitsCode(String s, int index) {
		return index > 0 && isCodeAt(s, index - 1);
	}

	public static int runStart(String s, int index) {
		int p = Math.clamp(index, 0, s.length());
		if (splitsCode(s, p)) {
			p--;
		}
		while (p >= 2 && isCodeAt(s, p - 2)) {
			p -= 2;
		}
		return p;
	}

	public static int runEnd(String s, int index) {
		int p = Math.clamp(index, 0, s.length());
		if (splitsCode(s, p)) {
			p--;
		}
		while (isCodeAt(s, p)) {
			p += 2;
		}
		return p;
	}

	public static int rgb(char colorCode) {
		ChatFormatting format = ChatFormatting.getByCode(colorCode);
		TextColor color = format == null ? null : TextColor.fromLegacyFormat(format);
		return color == null ? 0x000000 : color.getValue();
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

	public record State(char color, int styles) {
		public static final State EMPTY = new State((char) 0, 0);

		private static int bit(char style) {
			int i = STYLE_CODES.indexOf(Character.toLowerCase(style));
			return i < 0 ? 0 : 1 << i;
		}

		public State apply(char code) {
			char c = Character.toLowerCase(code);
			if (c == RESET) {
				return EMPTY;
			}
			if (isColor(c)) {
				return new State(c, 0);
			}
			if (isStyle(c)) {
				return new State(this.color, this.styles | bit(c));
			}
			return this;
		}

		public boolean isEmpty() {
			return this.color == 0 && this.styles == 0;
		}

		public boolean has(char style) {
			return (this.styles & bit(style)) != 0;
		}

		public boolean hasColor() {
			return this.color != 0;
		}

		public State with(char style) {
			return new State(this.color, this.styles | bit(style));
		}

		public State without(char style) {
			return new State(this.color, this.styles & ~bit(style));
		}

		public State withColor(char newColor) {
			return new State(Character.toLowerCase(newColor), this.styles);
		}

		public String codes() {
			StringBuilder out = new StringBuilder();
			if (this.color != 0) {
				out.append(SECTION).append(this.color);
			}
			return out.append(this.styleCodes()).toString();
		}

		public String styleCodes() {
			StringBuilder out = new StringBuilder();
			for (int i = 0; i < STYLE_CODES.length(); i++) {
				if ((this.styles & (1 << i)) != 0) {
					out.append(SECTION).append(STYLE_CODES.charAt(i));
				}
			}
			return out.toString();
		}
	}

	public static State stateAt(String s, int index) {
		int end = Math.min(index, s.length());
		State state = State.EMPTY;
		for (int i = 0; i < end; i++) {
			if (s.charAt(i) == SECTION) {
				if (i + 1 < end) {
					state = state.apply(s.charAt(i + 1));
				}
				i++;
			}
		}
		return state;
	}

	public static State apply(State state, String text) {
		State out = state;
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

	public static State applyInLine(State lineStart, String text) {
		State out = lineStart;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == SECTION) {
				if (i + 1 < text.length()) {
					char code = Character.toLowerCase(text.charAt(i + 1));
					out = code == RESET ? lineStart : out.apply(code);
				}
				i++;
			}
		}
		return out;
	}

	public static Style style(State state) {
		Style style = Style.EMPTY;
		String codes = state.codes();
		for (int i = 1; i < codes.length(); i += 2) {
			ChatFormatting format = ChatFormatting.getByCode(codes.charAt(i));
			if (format != null) {
				style = style.applyLegacyFormat(format);
			}
		}
		return style;
	}

	public static String transition(State from, State to) {
		if (from.equals(to)) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		boolean onlyAdds = from.color() == to.color() && (from.styles() & ~to.styles()) == 0;
		if (onlyAdds) {
			for (int i = 0; i < STYLE_CODES.length(); i++) {
				int bit = 1 << i;
				if ((to.styles() & bit) != 0 && (from.styles() & bit) == 0) {
					out.append(SECTION).append(STYLE_CODES.charAt(i));
				}
			}
			return out.toString();
		}
		if (to.hasColor()) {
			out.append(SECTION).append(to.color());
		} else {
			out.append(SECTION).append(RESET);
		}
		return out.append(to.styleCodes()).toString();
	}

	public record Edit(String text, int selectionStart, int selectionEnd) {
	}

	public static Edit restyle(String s, int start, int end, UnaryOperator<State> change) {
		int from = runStart(s, Math.min(start, end));
		int to = runEnd(s, Math.max(start, end));
		State before = stateAt(s, from);
		State after = stateAt(s, to);
		String head = s.substring(0, from);
		String tail = s.substring(to);

		if (!hasVisible(s, from, to)) {
			State typing = stateAt(s, Math.clamp(start, from, to));
			State wanted = change.apply(typing);
			String marks = markersIn(s, from, to);
			String open = transition(before, wanted);
			String close = transition(wanted, after);
			int caret = from + marks.length() + open.length();
			return new Edit(head + marks + open + close + tail, caret, caret);
		}

		StringBuilder body = new StringBuilder();
		State original = before;
		State written = before;
		int selectionStart = -1;
		int selectionEnd = from;
		for (int i = from; i < to; i++) {
			char c = s.charAt(i);
			if (c == SECTION) {
				if (i + 1 < s.length()) {
					char code = s.charAt(i + 1);
					original = original.apply(code);
					if (isMarker(code) && !hiddenBySectionBefore(s, i)) {
						body.append(SECTION).append(code);
					}
				}
				i++;
				continue;
			}
			State wanted = change.apply(original);
			body.append(transition(written, wanted));
			written = wanted;
			if (selectionStart < 0) {
				selectionStart = from + body.length();
			}
			body.append(c);
			if (Character.isHighSurrogate(c) && i + 1 < to && Character.isLowSurrogate(s.charAt(i + 1))) {
				body.append(s.charAt(++i));
			}
			selectionEnd = from + body.length();
		}
		body.append(transition(written, after));
		return new Edit(head + body + tail, selectionStart, selectionEnd);
	}

	public static boolean allHave(String s, int start, int end, char style) {
		if (start >= end) {
			return stateAt(s, start).has(style);
		}
		State state = stateAt(s, start);
		boolean any = false;
		for (int i = start; i < end; i++) {
			char c = s.charAt(i);
			if (c == SECTION) {
				if (i + 1 < s.length()) {
					state = state.apply(s.charAt(i + 1));
				}
				i++;
				continue;
			}
			if (!state.has(style)) {
				return false;
			}
			any = true;
		}
		return any || stateAt(s, end).has(style);
	}

	public static Edit toggleStyle(String s, int start, int end, char style) {
		boolean remove = allHave(s, Math.min(start, end), Math.max(start, end), style);
		return restyle(s, start, end, state -> remove ? state.without(style) : state.with(style));
	}

	public static Edit setColor(String s, int start, int end, char color) {
		return restyle(s, start, end, state -> state.withColor(color));
	}

	static String markersIn(String s, int from, int to) {
		StringBuilder out = new StringBuilder();
		for (int i = from; i + 1 < to; i++) {
			if (isCodeAt(s, i)) {
				if (isMarker(s.charAt(i + 1))) {
					out.append(SECTION).append(s.charAt(i + 1));
				}
				i++;
			}
		}
		return out.toString();
	}

	public static Edit clear(String s, int start, int end) {
		return restyle(s, start, end, state -> State.EMPTY);
	}

	public static String normalizeLead(String s) {
		int end = runEnd(s, 0);
		if (end == 0) {
			return s;
		}
		StringBuilder marks = new StringBuilder();
		for (int i = 0; i + 1 < end; i += 2) {
			char code = s.charAt(i + 1);
			if (isMarker(code) && marks.indexOf(String.valueOf(code)) < 0) {
				marks.append(SECTION).append(code);
			}
		}
		return marks + apply(State.EMPTY, s.substring(0, end)).codes() + s.substring(end);
	}

	public static Edit replace(String s, int start, int end, String text) {
		int from = Math.clamp(Math.min(start, end), 0, s.length());
		int to = Math.clamp(Math.max(start, end), 0, s.length());
		if (from < to) {
			if (splitsCode(s, from)) {
				from--;
			}
			if (splitsCode(s, to)) {
				to++;
			}
		}
		State before = stateAt(s, from);
		State after = stateAt(s, to);
		State first = after;
		int firstVisible = firstVisible(s, from, to);
		if (firstVisible >= 0) {
			first = stateAt(s, firstVisible);
		}
		boolean loneSection = endsWithLoneSection(text);
		String body = loneSection ? text.substring(0, text.length() - 1) : text;
		String open = transition(before, first);
		String close = transition(apply(first, body), after);
		String inserted = open + body + close + (loneSection ? String.valueOf(SECTION) : "");
		int caret = from + open.length() + body.length() + (loneSection ? close.length() + 1 : 0);
		String rest = s.substring(to);
		if (s.substring(from, to).indexOf('\n') >= 0) {
			rest = Alignment.dropMarks(rest, 0, runEnd(rest, 0));
		}
		return new Edit(s.substring(0, from) + inserted + rest, caret, caret);
	}

	public static boolean endsWithLoneSection(String text) {
		int run = 0;
		for (int i = text.length() - 1; i >= 0 && text.charAt(i) == SECTION; i--) {
			run++;
		}
		return (run & 1) == 1;
	}

	private static boolean hasVisible(String s, int from, int to) {
		return firstVisible(s, from, to) >= 0;
	}

	private static int firstVisible(String s, int from, int to) {
		for (int i = from; i < to; i++) {
			if (s.charAt(i) == SECTION) {
				i++;
			} else {
				return i;
			}
		}
		return -1;
	}

	public static String toLegacy(FormattedText text) {
		StringBuilder out = new StringBuilder();
		State[] written = {State.EMPTY};
		text.visit((style, contents) -> {
			State wanted = stateOf(style);
			out.append(transition(written[0], wanted));
			written[0] = apply(wanted, contents);
			out.append(contents);
			return Optional.empty();
		}, Style.EMPTY);
		return out.toString();
	}

	private static State stateOf(Style style) {
		State state = State.EMPTY;
		TextColor color = style.getColor();
		if (color != null) {
			state = state.withColor(nearestColorCode(color.getValue()));
		}
		if (style.isObfuscated()) state = state.with('k');
		if (style.isBold()) state = state.with('l');
		if (style.isStrikethrough()) state = state.with('m');
		if (style.isUnderlined()) state = state.with('n');
		if (style.isItalic()) state = state.with('o');
		return state;
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
}
