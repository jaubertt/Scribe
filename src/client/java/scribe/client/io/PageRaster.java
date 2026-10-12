package scribe.client.io;

import java.util.List;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import scribe.client.screen.BookStyle;
import scribe.client.text.Chapters;

public final class PageRaster implements PageFont.Canvas {
	public static final int SCALE = 6;
	public static final int WIDTH = 146;
	public static final int HEIGHT = 180;
	private static final int TEXT_LEFT = 16;
	private static final int TEXT_TOP = 21;
	private static final int LINE_HEIGHT = 9;
	private static final int PAGE_LINES = 14;
	private static final int NUMBER_CENTER = TEXT_LEFT + 114 / 2;
	private static final int NUMBER_Y = 157;

	private final int scale;
	private final int width;
	private final int height;
	private final int[] pixels;

	public PageRaster(int scale) {
		this.scale = scale;
		this.width = WIDTH * scale;
		this.height = HEIGHT * scale;
		this.pixels = new int[this.width * this.height];
	}

	@Override
	public int scale() {
		return this.scale;
	}

	public int width() {
		return this.width;
	}

	public int height() {
		return this.height;
	}

	public int[] pixels() {
		return this.pixels;
	}

	@Override
	public void set(int px, int py, int argb) {
		if (px >= 0 && py >= 0 && px < this.width && py < this.height) {
			this.pixels[py * this.width + px] = argb;
		}
	}

	public static PageRaster page(PageFont font, PageFont.@Nullable Image book, PageFont.@Nullable Image dyed, int cover, List<FormattedCharSequence> lines,
		List<Chapters.TitleRun> titles, int number) {
		PageRaster r = new PageRaster(SCALE);
		if (cover == BookStyle.COVER_UNDYED || dyed == null) {
			if (book != null) {
				r.blit(book, 20, 1, 0xFFFFFFFF);
			}
		} else {
			r.blit(dyed, 0, 0, cover);
			r.blit(dyed, 0, HEIGHT, 0xFFFFFFFF);
		}
		int shown = Math.min(PAGE_LINES, lines.size());
		for (int i = 0; i < shown; i++) {
			r.line(font, lines.get(i), TEXT_LEFT, TEXT_TOP + i * LINE_HEIGHT, i);
		}
		for (Chapters.TitleRun run : titles) {
			if (run.first() < shown) {
				int top = TEXT_TOP + run.first() * LINE_HEIGHT;
				int lastTop = TEXT_TOP + Math.min(run.last(), shown - 1) * LINE_HEIGHT;
				r.fill(TEXT_LEFT - 7, top, TEXT_LEFT - 5, lastTop + 8, BookStyle.INK_RED);
			}
		}
		String digits = Integer.toString(number);
		float w = 0;
		for (int k = 0; k < digits.length(); k++) {
			w += font.advance(digits.charAt(k), false);
		}
		float x = NUMBER_CENTER - (int) w / 2;
		for (int k = 0; k < digits.length(); k++) {
			font.draw(r, digits.charAt(k), x, NUMBER_Y, BookStyle.PAGE_NUMBER, false, false);
			x += font.advance(digits.charAt(k), false);
		}
		return r;
	}

	private void blit(PageFont.Image image, int u, int v, int tint) {
		for (int by = 0; by < HEIGHT; by++) {
			for (int bx = 0; bx < WIDTH; bx++) {
				int texel = multiply(image.at(u + bx, v + by), tint);
				if ((texel >>> 24) == 0) {
					continue;
				}
				for (int py = by * this.scale; py < (by + 1) * this.scale; py++) {
					for (int px = bx * this.scale; px < (bx + 1) * this.scale; px++) {
						int i = py * this.width + px;
						this.pixels[i] = over(this.pixels[i], texel);
					}
				}
			}
		}
	}

	private void line(PageFont font, FormattedCharSequence line, float x0, float y, int lineIndex) {
		float[] x = {x0};
		int[] count = {0};
		line.accept((position, style, codepoint) -> {
			boolean bold = style.isBold();
			TextColor color = style.getColor();
			int argb = color == null ? BookStyle.INK : 0xFF000000 | color.getValue();
			int cp = style.isObfuscated() ? font.sameWidth(codepoint, lineIndex * 131 + count[0] * 31 + codepoint) : codepoint;
			float advance = font.advance(codepoint, bold);
			font.draw(this, cp, x[0], y, argb, bold, style.isItalic());
			float effectX0 = count[0] == 0 ? x[0] - 1.0F : x[0];
			if (style.isStrikethrough()) {
				this.fill(effectX0, y + 3.5F, x[0] + advance, y + 4.5F, argb);
			}
			if (style.isUnderlined()) {
				this.fill(effectX0, y + 8.0F, x[0] + advance, y + 9.0F, argb);
			}
			x[0] += advance;
			count[0]++;
			return true;
		});
	}

	private void fill(float x0, float y0, float x1, float y1, int argb) {
		int px0 = Math.round(x0 * this.scale);
		int px1 = Math.round(x1 * this.scale);
		int py0 = Math.round(y0 * this.scale);
		int py1 = Math.round(y1 * this.scale);
		for (int py = py0; py < py1; py++) {
			for (int px = px0; px < px1; px++) {
				this.set(px, py, argb);
			}
		}
	}

	private static int multiply(int argb, int tint) {
		int a = (argb >>> 24) * (tint >>> 24) / 255;
		int r = (argb >> 16 & 255) * (tint >> 16 & 255) / 255;
		int g = (argb >> 8 & 255) * (tint >> 8 & 255) / 255;
		int b = (argb & 255) * (tint & 255) / 255;
		return a << 24 | r << 16 | g << 8 | b;
	}

	static int over(int under, int top) {
		int ta = top >>> 24;
		if (ta == 255) {
			return top;
		}
		int ua = under >>> 24;
		int oa = ta + ua * (255 - ta) / 255;
		if (oa == 0) {
			return 0;
		}
		int r = ((top >> 16 & 255) * ta + (under >> 16 & 255) * ua * (255 - ta) / 255) / oa;
		int g = ((top >> 8 & 255) * ta + (under >> 8 & 255) * ua * (255 - ta) / 255) / oa;
		int b = ((top & 255) * ta + (under & 255) * ua * (255 - ta) / 255) / oa;
		return oa << 24 | r << 16 | g << 8 | b;
	}
}
