package scribe.client.text;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import org.jspecify.annotations.Nullable;

public final class FrameBorders {
	static final String DOWN = "┌┍┎┏┐┑┒┓┬┭┮┯┰┱┲┳╒╓╔╕╖╗╤╥╦╭╮╷╻";
	static final String UP_AND_DOWN = "│┃┆┇┊┋├┝┞┟┠┡┢┣┤┥┦┧┨┩┪┫┼┽┾┿╀╁╂╃╄╅╆╇╈╉╊╋╎╏║╞╟╠╡╢╣╪╫╬╽╿";
	static final String UP = "└┕┖┗┘┙┚┛┴┵┶┷┸┹┺┻╘╙╚╛╜╝╧╨╩╯╰╵╹";
	private static final int MAX_SPACES = 40;
	private static final float EPSILON = 0.01F;

	private FrameBorders() {
	}

	private record Change(int start, int end, int length) {
	}

	public static final class Result {
		private final String text;
		private final List<Change> changes;

		private Result(String text, List<Change> changes) {
			this.text = text;
			this.changes = changes;
		}

		public String text() {
			return this.text;
		}

		public int map(int position) {
			int shift = 0;
			for (Change change : this.changes) {
				if (position <= change.start()) {
					break;
				}
				if (position < change.end()) {
					return change.start() + shift;
				}
				shift += change.length() - (change.end() - change.start());
			}
			return position + shift;
		}

		public FormatCodes.Edit apply(FormatCodes.Edit edit) {
			return new FormatCodes.Edit(this.text, this.map(edit.selectionStart()), this.map(edit.selectionEnd()));
		}
	}

	public enum Kind { DOWN, UP_AND_DOWN, UP, NONE }

	public static Kind kind(char c) {
		if (c == 0) {
			return Kind.NONE;
		}
		if (DOWN.indexOf(c) >= 0) {
			return Kind.DOWN;
		}
		if (UP_AND_DOWN.indexOf(c) >= 0) {
			return Kind.UP_AND_DOWN;
		}
		return UP.indexOf(c) >= 0 ? Kind.UP : Kind.NONE;
	}

	public static @Nullable Result lineUp(PageFitter fitter, String page, int keep) {
		if (!fitter.fits(page)) {
			return null;
		}
		Font font = fitter.font();
		String current = page;
		int shift = 0;
		List<Change> changes = new ArrayList<>();
		float corner = -1;
		int start = 0;
		while (true) {
			int end = page.indexOf('\n', start);
			if (end < 0) {
				end = page.length();
			}
			int last = lastSymbol(page, start, end);
			Kind kind = kind(last < 0 ? 0 : page.charAt(last));
			if (kind == Kind.DOWN) {
				String codes = FormatCodes.stateAt(page, start).codes();
				corner = fitter.lineCount(codes + page.substring(start, end)) == 1 ? width(font, codes + page.substring(start, last)) : -1;
			} else if (corner >= 0 && kind != Kind.NONE) {
				NewRun change = run(fitter, page, start, end, last, corner, keep);
				if (change != null) {
					String run = change.run();
					String candidate = current.substring(0, change.from() + shift) + run + current.substring(change.to() + shift);
					if (fitter.fits(candidate)) {
						current = candidate;
						changes.add(new Change(change.from(), change.to(), run.length()));
						shift += run.length() - (change.to() - change.from());
					}
				}
				if (kind == Kind.UP) {
					corner = -1;
				}
			} else {
				corner = -1;
			}
			if (end >= page.length()) {
				break;
			}
			start = end + 1;
		}
		return changes.isEmpty() ? null : new Result(current, changes);
	}

	private record NewRun(int from, int to, String run) {
	}

	private static @Nullable NewRun run(PageFitter fitter, String page, int start, int end, int last, float corner, int keep) {
		int textEnd = last;
		int firstSpace = -1;
		while (textEnd > start) {
			if (textEnd - 2 >= start && FormatCodes.startsPair(page, textEnd - 2)) {
				textEnd -= 2;
			} else if (page.charAt(textEnd - 1) == ' ') {
				textEnd--;
				firstSpace = textEnd;
			} else {
				break;
			}
		}
		if (firstSpace < 0 || textEnd <= start || (keep > textEnd && keep <= last)) {
			return null;
		}
		Font font = fitter.font();
		String codes = FormatCodes.stateAt(page, start).codes();
		float textWidth = width(font, codes + page.substring(start, textEnd));
		float oldWidth = width(font, codes + page.substring(start, last)) - textWidth;
		float gap = corner - textWidth;
		float plain = width(font, " ");
		float bold = width(font, FormatCodes.SECTION + "l ");
		int[] best = spaces(gap, plain, bold);
		float bestWidth = best[0] * plain + best[1] * bold;
		if (Math.abs(oldWidth - gap) <= Math.abs(bestWidth - gap) + EPSILON) {
			return null;
		}
		FormatCodes.State before = FormatCodes.stateAt(page, textEnd);
		FormatCodes.State after = FormatCodes.stateAt(page, last);
		FormatCodes.State spaceState = FormatCodes.stateAt(page, firstSpace);
		String marks = FormatCodes.markersIn(page, textEnd, last);
		String shortest = null;
		for (FormatCodes.State base : new FormatCodes.State[] {spaceState, after, before}) {
			for (boolean boldFirst : new boolean[] {false, true}) {
				String run = marks + build(before, after, base.without('l'), best[0], base.with('l'), best[1], boldFirst);
				if (shortest == null || run.length() < shortest.length()) {
					shortest = run;
				}
			}
		}
		if (shortest.equals(page.substring(textEnd, last))) {
			return null;
		}
		String line = page.substring(start, textEnd) + shortest + page.substring(last, end);
		if (fitter.lineCount(codes + line) != 1) {
			return null;
		}
		return new NewRun(textEnd, last, shortest);
	}

	static int[] spaces(float gap, float plain, float bold) {
		int[] best = {1, 0};
		float bestError = Float.MAX_VALUE;
		boolean bestOver = true;
		for (int b = 0; b <= MAX_SPACES; b++) {
			for (int p = 0; p <= MAX_SPACES; p++) {
				if (p + b == 0) {
					continue;
				}
				float total = p * plain + b * bold;
				float error = Math.abs(total - gap);
				boolean over = total > gap + EPSILON;
				boolean better = error < bestError - EPSILON
					|| (Math.abs(error - bestError) <= EPSILON && (bestOver && !over
						|| (over == bestOver && (b < best[1] || (b == best[1] && p + b < best[0] + best[1])))));
				if (better) {
					best = new int[] {p, b};
					bestError = error;
					bestOver = over;
				}
			}
		}
		return best;
	}

	private static String build(FormatCodes.State before, FormatCodes.State after, FormatCodes.State plain, int plainCount,
		FormatCodes.State bold, int boldCount, boolean boldFirst) {
		StringBuilder out = new StringBuilder();
		FormatCodes.State state = before;
		for (int i = 0; i < 2; i++) {
			boolean isBold = boldFirst == (i == 0);
			int count = isBold ? boldCount : plainCount;
			if (count == 0) {
				continue;
			}
			FormatCodes.State look = isBold ? bold : plain;
			out.append(FormatCodes.transition(state, look)).append(" ".repeat(count));
			state = look;
		}
		return out.append(FormatCodes.transition(state, after)).toString();
	}

	static int lastSymbol(String page, int start, int end) {
		int found = -1;
		for (int i = start; i < end; i++) {
			if (FormatCodes.startsPair(page, i)) {
				i++;
			} else if (page.charAt(i) != ' ') {
				found = i;
			}
		}
		return found;
	}

	static float width(Font font, String text) {
		return font.getSplitter().stringWidth(text);
	}
}
