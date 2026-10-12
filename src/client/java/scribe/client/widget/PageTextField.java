package scribe.client.widget;

import scribe.BookLimits;
import scribe.client.compat.Keys;
import scribe.client.compat.TextKeys;
import scribe.client.text.Alignment;
import scribe.client.text.FormatCodes;
import scribe.client.text.PageFitter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import org.jspecify.annotations.Nullable;

public class PageTextField extends MultilineTextField {
	private final Font font;
	private final PageFitter fitter;
	private Consumer<String> listener = value -> {};
	private @Nullable Consumer<FormatCodes.Edit> overflow;
	private boolean muted;

	public PageTextField(Font font) {
		super(font, BookLimits.PAGE_WIDTH);
		this.font = font;
		this.fitter = new PageFitter(font);
		this.setCharacterLimit(BookLimits.PAGE_CHARS);
		this.setLineLimit(BookLimits.PAGE_LINES);
		super.setValueListener(value -> {
			if (!this.muted) {
				this.listener.accept(value);
			}
		});
	}

	@Override
	public void setValueListener(Consumer<String> listener) {
		this.listener = listener;
	}

	public void setOverflowHandler(@Nullable Consumer<FormatCodes.Edit> handler) {
		this.overflow = handler;
	}

	public int selectionStart() {
		return this.getSelected().beginIndex();
	}

	public int selectionEnd() {
		return this.getSelected().endIndex();
	}

	public int anchor() {
		int start = this.selectionStart();
		int end = this.selectionEnd();
		return this.cursor() == start ? end : start;
	}

	public boolean caretOnFirstLine() {
		return this.getLineAtCursor() <= 0;
	}

	public int caretLine() {
		return Math.max(0, this.getLineAtCursor());
	}

	public boolean caretOnLastLine() {
		return this.getLineAtCursor() >= this.getLineCount() - 1;
	}

	public boolean caretAtVisualStart() {
		return FormatCodes.runStart(this.value(), this.cursor()) == 0;
	}

	public boolean caretAtVisualEnd() {
		return FormatCodes.runEnd(this.value(), this.cursor()) >= this.value().length();
	}

	public List<int[]> lines() {
		List<int[]> out = new ArrayList<>();
		for (MultilineTextField.StringView view : this.iterateLines()) {
			out.add(new int[] {view.beginIndex(), view.endIndex()});
		}
		return out;
	}

	public boolean fits(String text) {
		return this.fitter.fits(text);
	}

	public int lineOffset(int[] line) {
		return Alignment.rowOffset(this.font, this.value(), line[0], line[1]);
	}

	public void setTextAndSelection(String text, int anchor, int caret) {
		boolean changed = !text.equals(this.value());
		this.muted = true;
		try {
			this.setValue(text, true);
			int length = this.value().length();
			this.select(Mth.clamp(anchor, 0, length), Mth.clamp(caret, 0, length));
		} finally {
			this.muted = false;
		}
		if (changed) {
			this.listener.accept(this.value());
		}
	}

	public void select(int anchor, int caret) {
		this.setSelecting(false);
		super.seekCursor(Whence.ABSOLUTE, anchor);
		this.setSelecting(true);
		super.seekCursor(Whence.ABSOLUTE, caret);
		this.setSelecting(false);
	}

	public void placeCaret(int index) {
		int p = restPosition(this.value(), Mth.clamp(index, 0, this.value().length()));
		this.select(p, p);
	}

	public FormatCodes.Edit previewReplace(String text) {
		return FormatCodes.replace(this.value(), this.selectionStart(), this.selectionEnd(), text);
	}

	public boolean apply(FormatCodes.Edit edit) {
		if (!this.fits(edit.text())) {
			if (this.overflow != null) {
				this.overflow.accept(edit);
				return true;
			}
			return false;
		}
		this.setTextAndSelection(edit.text(), edit.selectionStart(), edit.selectionEnd());
		return true;
	}

	public boolean insertRaw(String text) {
		return this.apply(this.previewReplace(text));
	}

	@Override
	public void insertText(String input) {
		if (input.isEmpty() && !this.hasSelection()) {
			return;
		}
		this.apply(this.previewReplace(StringUtil.filterText(input, true)));
	}

	@Override
	public void deleteText(int direction) {
		if (this.hasSelection()) {
			this.insertText("");
			return;
		}
		if (direction == 0) {
			return;
		}
		String value = this.value();
		int caret = this.cursor();
		int from;
		int to;
		if (direction == -1) {
			int q = FormatCodes.runStart(value, caret);
			if (q <= 0) {
				return;
			}
			to = q;
			from = q - stepBack(value, q);
		} else if (direction == 1) {
			int f = FormatCodes.runEnd(value, caret);
			if (f >= value.length()) {
				return;
			}
			from = f;
			to = f + stepForward(value, f);
		} else {
			from = Mth.clamp(Math.min(caret, caret + direction), 0, value.length());
			to = Mth.clamp(Math.max(caret, caret + direction), 0, value.length());
		}
		FormatCodes.Edit edit = FormatCodes.replace(value, from, to, "");
		if (direction == 1) {
			edit = new FormatCodes.Edit(edit.text(), caret, caret);
		}
		this.apply(edit);
	}

	@Override
	public void seekCursor(Whence whence, int offset) {
		if (whence == Whence.RELATIVE && (offset == 1 || offset == -1)) {
			super.seekCursor(Whence.ABSOLUTE, visualStep(this.value(), this.cursor(), offset));
			return;
		}
		super.seekCursor(whence, offset);
		int rested = restPosition(this.value(), this.cursor());
		if (rested != this.cursor()) {
			super.seekCursor(Whence.ABSOLUTE, rested);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return this.keyPressed(event, Keys.mac());
	}

	public boolean keyPressed(KeyEvent event, boolean mac) {
		TextKeys.Caret caret = TextKeys.caret(event, mac);
		if (caret == TextKeys.Caret.NONE) {
			return super.keyPressed(event);
		}
		this.caretKey(caret, Keys.shift(event));
		return true;
	}

	public void caretKey(TextKeys.Caret caret, boolean select) {
		String value = this.value();
		int cursor = this.cursor();
		if (caret.deletes()) {
			int[] line = this.lineAtCaret();
			switch (caret) {
				case DELETE_CHAR_BACK -> this.deleteText(-1);
				case DELETE_CHAR_FORWARD -> this.deleteText(1);
				case DELETE_WORD_BACK -> this.deleteText(this.getPreviousWord().beginIndex() - cursor);
				case DELETE_WORD_FORWARD -> this.deleteText(this.getNextWord().beginIndex() - cursor);
				case DELETE_TO_WORD_END -> this.deleteText(TextKeys.wordEnd(value, cursor) - cursor);
				case DELETE_TO_LINE_START -> this.deleteText(FormatCodes.runStart(value, cursor) <= line[0] ? -1 : line[0] - cursor);
				case DELETE_TO_LINE_END -> this.deleteText(FormatCodes.runEnd(value, cursor) >= line[1] ? 1 : line[1] - cursor);
				default -> {
				}
			}
			return;
		}
		this.setSelecting(select);
		try {
			switch (caret) {
				case CHAR_LEFT -> this.seekCursor(Whence.RELATIVE, -1);
				case CHAR_RIGHT -> this.seekCursor(Whence.RELATIVE, 1);
				case WORD_LEFT -> this.seekCursor(Whence.ABSOLUTE, this.getPreviousWord().beginIndex());
				case WORD_RIGHT -> this.seekCursor(Whence.ABSOLUTE, this.getNextWord().beginIndex());
				case WORD_END -> this.seekCursor(Whence.ABSOLUTE, TextKeys.wordEnd(value, cursor));
				case LINE_START -> this.seekCursor(Whence.ABSOLUTE, this.lineAtCaret()[0]);
				case LINE_END -> this.seekCursor(Whence.ABSOLUTE, this.lineAtCaret()[1]);
				case LINE_UP -> this.seekCursorLine(-1);
				case LINE_DOWN -> this.seekCursorLine(1);
				case TEXT_START -> this.seekCursor(Whence.ABSOLUTE, 0);
				case TEXT_END -> this.seekCursor(Whence.END, 0);
				default -> {
				}
			}
		} finally {
			this.setSelecting(false);
		}
	}

	private int[] lineAtCaret() {
		List<int[]> lines = this.lines();
		if (lines.isEmpty()) {
			return new int[] {0, this.value().length()};
		}
		int index = this.getLineAtCursor();
		return lines.get(index < 0 ? lines.size() - 1 : Math.min(index, lines.size() - 1));
	}

	@Override
	public void seekCursorLine(int lineOffset) {
		if (lineOffset == 0) {
			return;
		}
		List<int[]> lines = this.lines();
		int current = this.getLineAtCursor();
		if (current < 0 || lines.isEmpty()) {
			super.seekCursorLine(lineOffset);
			return;
		}
		int target = Mth.clamp(current + lineOffset, 0, lines.size() - 1);
		int[] line = lines.get(current);
		String value = this.value();
		int x = this.lineOffset(line) + this.font.width(FormatCodes.stateAt(value, line[0]).codes() + value.substring(line[0], this.cursor()));
		this.seekCursor(Whence.ABSOLUTE, this.indexAtX(lines.get(target), x - this.lineOffset(lines.get(target))));
	}

	public void seekToPoint(double x, double y, boolean extendSelection) {
		List<int[]> lines = this.lines();
		if (lines.isEmpty()) {
			return;
		}
		int lineIndex = Mth.clamp(Mth.floor(y / BookLimits.LINE_HEIGHT), 0, lines.size() - 1);
		int[] line = lines.get(lineIndex);
		int target = restPosition(this.value(), this.indexAtX(line, x - this.lineOffset(line)));
		this.setSelecting(extendSelection);
		super.seekCursor(Whence.ABSOLUTE, target);
		this.setSelecting(false);
	}

	private int indexAtX(int[] line, double x) {
		String value = this.value();
		String prefix = FormatCodes.stateAt(value, line[0]).codes();
		int best = 0;
		int previousWidth = 0;
		for (int i = 1; i <= line[1] - line[0]; i++) {
			int index = line[0] + i;
			if (FormatCodes.splitsCode(value, index)
				|| (index < value.length() && Character.isLowSurrogate(value.charAt(index)) && Character.isHighSurrogate(value.charAt(index - 1)))) {
				continue;
			}
			int w = this.font.width(prefix + value.substring(line[0], index));
			if (w > x) {
				if (x - previousWidth > (w - previousWidth) / 2.0) {
					best = i;
				}
				break;
			}
			best = i;
			previousWidth = w;
		}
		return line[0] + best;
	}

	public static int restPosition(String value, int index) {
		int p = Mth.clamp(index, 0, value.length());
		int start = FormatCodes.runStart(value, p);
		int end = FormatCodes.runEnd(value, p);
		if (start == end) {
			return p;
		}
		boolean lineStart = start == 0 || value.charAt(start - 1) == '\n';
		return lineStart ? end : start;
	}

	public static int visualStep(String value, int caret, int direction) {
		int length = value.length();
		int p = Mth.clamp(caret, 0, length);
		if (direction > 0) {
			p = FormatCodes.runEnd(value, p);
			if (p < length) {
				p += stepForward(value, p);
			}
		} else {
			p = FormatCodes.runStart(value, p);
			if (p > 0) {
				p -= stepBack(value, p);
			}
		}
		return restPosition(value, p);
	}

	private static int stepForward(String value, int p) {
		if (p + 1 < value.length()) {
			if (FormatCodes.startsPair(value, p)) {
				return 2;
			}
			if (Character.isHighSurrogate(value.charAt(p)) && Character.isLowSurrogate(value.charAt(p + 1))) {
				return 2;
			}
		}
		return 1;
	}

	private static int stepBack(String value, int p) {
		if (p >= 2) {
			if (FormatCodes.startsPair(value, p - 2)) {
				return 2;
			}
			if (Character.isLowSurrogate(value.charAt(p - 1)) && Character.isHighSurrogate(value.charAt(p - 2))) {
				return 2;
			}
		}
		return 1;
	}
}
