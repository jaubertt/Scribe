package scribe.client.text;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class PageFlow {
	private PageFlow() {
	}

	public record Result(List<String> pages, int caretPage, int caretPos) {
	}

	public static @Nullable Result place(List<String> pages, int index, String text, int caret, PageFitter fitter, boolean flow, int maxPages) {
		List<String> work = new ArrayList<>(pages);
		while (work.size() <= index) {
			work.add("");
		}
		String carry = text;
		int i = index;
		int c = caret;
		int caretPage = -1;
		int caretPos = -1;
		while (true) {
			if (i >= maxPages) {
				return null;
			}
			if (i >= work.size()) {
				work.add("");
			}
			int n = fitter.pageLength(carry);
			work.set(i, carry.substring(0, n));
			int lineBreak = PageFitter.boundaryBreak(carry, n);
			String tail = lineBreak >= 0
				? carry.substring(n, lineBreak) + carry.substring(lineBreak + 1)
				: carry.substring(n);
			if (caretPage < 0) {
				if (c <= n) {
					caretPage = i;
					caretPos = c;
				} else {
					c = c - n - (lineBreak >= 0 && c > lineBreak ? 1 : 0);
				}
			}
			boolean caretPending = caretPage < 0;
			if (tail.isEmpty() && !caretPending) {
				break;
			}
			String codes = FormatCodes.stateAt(carry, n).codes();
			if (caretPending) {
				c += codes.length();
			}
			boolean hasNext = i + 1 < work.size();
			if (flow && hasNext && !work.get(i + 1).isEmpty()) {
				String next = work.get(i + 1);
				String reset = FormatCodes.transition(FormatCodes.stateAt(carry, carry.length()), FormatCodes.State.EMPTY);
				carry = codes + tail + reset + separator(tail, next) + next;
			} else if (flow && hasNext) {
				carry = codes + tail;
			} else {
				work.add(i + 1, "");
				carry = codes + tail;
			}
			i++;
		}
		while (work.size() > maxPages && work.getLast().isEmpty()) {
			work.removeLast();
		}
		if (work.size() > maxPages) {
			return null;
		}
		return new Result(work, caretPage < 0 ? index : caretPage, caretPage < 0 ? 0 : caretPos);
	}

	public static @Nullable Result reflow(List<String> pages, int index, String text, int caret, PageFitter fitter, boolean flow, int maxPages) {
		List<String> work = new ArrayList<>(pages);
		while (work.size() <= index) {
			work.add("");
		}
		String mark = PageBreaks.markOf(work.get(index)).mark();
		String carry = text;
		int i = index;
		int c = caret;
		int caretPage = -1;
		int caretPos = -1;
		if (flow && PageBreaks.blank(carry) && !FormatCodes.endsWithLoneSection(carry) && index + 1 < work.size()) {
			PageBreaks.Kind next = PageBreaks.between(carry, work.get(index + 1));
			if (next.soft()) {
				carry = join(carry, next, PageBreaks.text(work.get(index + 1)));
				work.remove(index + 1);
				c = Math.min(c, carry.length());
			}
		}
		if (flow && index > 0) {
			String previous = PageBreaks.text(work.get(index - 1));
			PageBreaks.Kind back = PageBreaks.between(previous, mark + carry);
			if (back.soft() && !FormatCodes.endsWithLoneSection(previous)) {
				String joined = join(previous, back, carry);
				if (fitter.pageLength(joined) != previous.length()) {
					int lead = FormatCodes.runEnd(carry, 0);
					int body = joined.length() - (carry.length() - lead);
					work.remove(index);
					i = index - 1;
					mark = PageBreaks.markOf(work.get(i)).mark();
					c = c < 0 ? -1 : body + Math.max(0, c - lead);
					carry = joined;
				}
			}
		}
		while (true) {
			if (i >= maxPages) {
				return null;
			}
			int n = fitter.pageLength(carry);
			if (n >= carry.length()) {
				if (flow && i + 1 < work.size() && !FormatCodes.endsWithLoneSection(carry)) {
					PageBreaks.Kind kind = PageBreaks.between(carry, work.get(i + 1));
					if (kind.soft()) {
						String joined = join(carry, kind, PageBreaks.text(work.get(i + 1)));
						if (fitter.pageLength(joined) != carry.length()) {
							work.remove(i + 1);
							carry = joined;
							continue;
						}
					}
				}
				work.set(i, mark + carry);
				if (caretPage < 0 && c >= 0) {
					caretPage = i;
					caretPos = c;
				}
				break;
			}
			int lineBreak = PageFitter.boundaryBreak(carry, n);
			PageBreaks.Kind made = lineBreak >= 0 ? PageBreaks.Kind.SOFT_NEWLINE : PageBreaks.Kind.SOFT;
			String head = carry.substring(0, n);
			String rest = lineBreak >= 0 ? carry.substring(n, lineBreak) + carry.substring(lineBreak + 1) : carry.substring(n);
			work.set(i, mark + head);
			if (caretPage < 0 && c >= 0) {
				if (c <= n) {
					caretPage = i;
					caretPos = c;
				} else {
					c = c - n - (lineBreak >= 0 && c > lineBreak ? 1 : 0);
				}
			}
			String carried = (made == PageBreaks.Kind.SOFT ? Alignment.carried(carry, n) : "") + FormatCodes.stateAt(carry, n).codes();
			String moved = FormatCodes.normalizeLead(carried + rest);
			if (caretPage < 0 && c >= 0) {
				int run = FormatCodes.runEnd(rest, 0);
				c = c > run ? c + moved.length() - rest.length() : FormatCodes.runEnd(moved, 0);
			}
			int next = i + 1;
			PageBreaks.Kind old = next < work.size() ? PageBreaks.between(head, work.get(next)) : PageBreaks.Kind.HARD;
			if (flow && old.soft()) {
				carry = join(moved, old, PageBreaks.text(work.get(next)));
			} else if (flow && next == work.size() - 1 && PageBreaks.blank(work.get(next))) {
				carry = moved;
			} else {
				work.add(next, "");
				carry = moved;
			}
			mark = made.mark();
			i = next;
		}
		while (work.size() > maxPages && PageBreaks.text(work.getLast()).isEmpty()) {
			work.removeLast();
		}
		if (work.size() > maxPages) {
			return null;
		}
		return new Result(work, caretPage < 0 ? index : caretPage, caretPage < 0 ? 0 : caretPos);
	}

	public static String join(String head, PageBreaks.Kind kind, String next) {
		String first = FormatCodes.endsWithLoneSection(head) ? head.substring(0, head.length() - 1) : head;
		int lead = FormatCodes.runEnd(next, 0);
		StringBuilder marks = new StringBuilder();
		for (int i = 0; i + 1 < lead; i += 2) {
			char code = next.charAt(i + 1);
			boolean sameParagraph = kind != PageBreaks.Kind.SOFT_NEWLINE && Alignment.isMark(code);
			if (FormatCodes.isMarker(code) && !sameParagraph && !PageBreaks.isMark(code)) {
				marks.append(FormatCodes.SECTION).append(code);
			}
		}
		FormatCodes.State from = FormatCodes.stateAt(first, first.length());
		FormatCodes.State to = FormatCodes.apply(FormatCodes.State.EMPTY, next.substring(0, lead));
		return first + kind.joiner() + marks + FormatCodes.transition(from, to) + next.substring(lead);
	}

	static String separator(String tail, String next) {
		String moved = FormatCodes.strip(tail);
		String visible = FormatCodes.strip(next);
		if (moved.isEmpty() || visible.isEmpty()) {
			return "";
		}
		char last = moved.charAt(moved.length() - 1);
		char first = visible.charAt(0);
		return Character.isWhitespace(last) || Character.isWhitespace(first) ? "" : " ";
	}
}
