package scribe.client.text;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public final class TextWrap {
	private TextWrap() {
	}

	public static List<FormattedCharSequence> pageLines(Font font, Component page, Style pageStyle, int width) {
		List<FormattedCharSequence> out = new ArrayList<>();
		for (FormattedCharSequence line : font.split(ComponentUtils.mergeStyles(page, pageStyle), width)) {
			out.add(sink -> line.accept((position, style, codepoint) -> sink.accept(position, style.applyTo(pageStyle), codepoint)));
		}
		return out;
	}

	public static List<int[]> lines(Font font, String text, int width) {
		List<int[]> out = new ArrayList<>();
		int start = 0;
		int n = text.length();
		if (n == 0) {
			out.add(new int[] {0, 0});
			return out;
		}
		while (start < n) {
			int end = start;
			int lastBreak = -1;
			while (end < n) {
				int next = text.offsetByCodePoints(end, 1);
				if (font.width(text.substring(start, next)) > width) {
					break;
				}
				if (text.charAt(end) == ' ') {
					lastBreak = end;
				}
				end = next;
			}
			if (end >= n) {
				out.add(new int[] {start, n});
				break;
			}
			if (lastBreak > start) {
				out.add(new int[] {start, lastBreak});
				start = lastBreak + 1;
			} else {
				int cut = Math.max(end, text.offsetByCodePoints(start, 1));
				out.add(new int[] {start, cut});
				start = cut;
			}
		}
		return out;
	}

	public static List<String> wrap(Font font, String text, int width) {
		List<String> out = new ArrayList<>();
		for (int[] line : lines(font, text, width)) {
			out.add(text.substring(line[0], line[1]));
		}
		return out;
	}
}
