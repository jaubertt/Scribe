package scribe.client.io;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class PageFont {
	public interface Files {
		@Nullable InputStream open(Identifier id) throws IOException;
	}

	public interface Images {
		@Nullable Image read(Identifier id) throws IOException;
	}

	public record Image(int width, int height, int[] argb) {
		public int at(int x, int y) {
			return x < 0 || y < 0 || x >= this.width || y >= this.height ? 0 : this.argb[y * this.width + x];
		}
	}

	record Bitmap(Image image, int u, int v, int width, int cellHeight, int ascent, float scale, int advance) {
	}

	record Hex(int[] rows, int left, int right) {
		int width() {
			return this.right - this.left + 1;
		}

		boolean bit(int column, int row) {
			int c = this.left + column;
			return c >= 0 && c < 32 && (this.rows[row] >>> (31 - c) & 1) != 0;
		}
	}

	public static final int MISSING_ADVANCE = 6;

	private final Map<Integer, Float> spaces = new HashMap<>();
	private final Map<Integer, Bitmap> bitmaps = new HashMap<>();
	private final Map<Integer, Hex> hex = new HashMap<>();
	private final TreeMap<Integer, List<Integer>> byAdvance = new TreeMap<>();

	private PageFont() {
	}

	public static PageFont load(Files files, Images images, Set<Integer> needed) throws IOException {
		PageFont font = new PageFont();
		JsonObject space = json(files, Identifier.withDefaultNamespace("font/include/space.json"));
		if (space != null) {
			for (JsonElement p : space.getAsJsonArray("providers")) {
				font.readSpace(p.getAsJsonObject());
			}
		}
		JsonObject bitmap = json(files, Identifier.withDefaultNamespace("font/include/default.json"));
		if (bitmap != null) {
			for (JsonElement p : bitmap.getAsJsonArray("providers")) {
				font.readBitmap(p.getAsJsonObject(), images);
			}
		}
		JsonObject unifont = json(files, Identifier.withDefaultNamespace("font/include/unifont.json"));
		if (unifont != null) {
			List<Integer> wanted = new ArrayList<>();
			for (int cp : needed) {
				if (!font.spaces.containsKey(cp) && !font.bitmaps.containsKey(cp)) {
					wanted.add(cp);
				}
			}
			if (!wanted.isEmpty()) {
				for (JsonElement p : unifont.getAsJsonArray("providers")) {
					font.readHex(p.getAsJsonObject(), files, Set.copyOf(wanted));
				}
			}
		}
		for (Map.Entry<Integer, Bitmap> e : font.bitmaps.entrySet()) {
			if (e.getValue().width() > 0) {
				font.byAdvance.computeIfAbsent(e.getValue().advance(), a -> new ArrayList<>()).add(e.getKey());
			}
		}
		font.byAdvance.values().forEach(list -> list.sort(Integer::compare));
		return font;
	}

	private static @Nullable JsonObject json(Files files, Identifier id) throws IOException {
		try (InputStream in = files.open(id)) {
			if (in == null) {
				return null;
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private void readSpace(JsonObject p) {
		if (!"space".equals(p.get("type").getAsString())) {
			return;
		}
		for (Map.Entry<String, JsonElement> e : p.getAsJsonObject("advances").entrySet()) {
			int cp = e.getKey().codePointAt(0);
			this.spaces.putIfAbsent(cp, e.getValue().getAsFloat());
		}
	}

	private void readBitmap(JsonObject p, Images images) throws IOException {
		if (!"bitmap".equals(p.get("type").getAsString()) || filteredOut(p)) {
			return;
		}
		Identifier file = Identifier.parse(p.get("file").getAsString());
		Image image = images.read(file.withPrefix("textures/"));
		if (image == null) {
			return;
		}
		int height = p.has("height") ? p.get("height").getAsInt() : 8;
		int ascent = p.get("ascent").getAsInt();
		JsonArray rows = p.getAsJsonArray("chars");
		int columns = rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
		int cellW = image.width() / columns;
		int cellH = image.height() / rows.size();
		float scale = (float) height / cellH;
		for (int r = 0; r < rows.size(); r++) {
			int[] cps = rows.get(r).getAsString().codePoints().toArray();
			for (int c = 0; c < cps.length; c++) {
				int cp = cps[c];
				if (cp == 0 || this.bitmaps.containsKey(cp) || this.spaces.containsKey(cp)) {
					continue;
				}
				int width = drawnWidth(image, c * cellW, r * cellH, cellW, cellH);
				int advance = (int) (0.5F + width * scale) + 1;
				this.bitmaps.put(cp, new Bitmap(image, c * cellW, r * cellH, width, cellH, ascent, scale, advance));
			}
		}
	}

	private static boolean filteredOut(JsonObject p) {
		if (!p.has("filter")) {
			return false;
		}
		JsonObject filter = p.getAsJsonObject("filter");
		return filter.has("uniform") && filter.get("uniform").getAsBoolean() || filter.has("jp") && filter.get("jp").getAsBoolean();
	}

	private static int drawnWidth(Image image, int u, int v, int cellW, int cellH) {
		for (int x = cellW - 1; x >= 0; x--) {
			for (int y = 0; y < cellH; y++) {
				if ((image.at(u + x, v + y) >>> 24) != 0) {
					return x + 1;
				}
			}
		}
		return 0;
	}

	private void readHex(JsonObject p, Files files, Set<Integer> wanted) throws IOException {
		if (!"unihex".equals(p.get("type").getAsString()) || filteredOut(p)) {
			return;
		}
		List<int[]> overrides = new ArrayList<>();
		if (p.has("size_overrides")) {
			for (JsonElement e : p.getAsJsonArray("size_overrides")) {
				JsonObject o = e.getAsJsonObject();
				overrides.add(new int[] {o.get("from").getAsString().codePointAt(0), o.get("to").getAsString().codePointAt(0), o.get("left").getAsInt(),
					o.get("right").getAsInt()});
			}
		}
		Identifier zip = Identifier.parse(p.get("hex_file").getAsString());
		try (InputStream in = files.open(zip)) {
			if (in == null) {
				return;
			}
			ZipInputStream zis = new ZipInputStream(in);
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (entry.getName().endsWith(".hex")) {
					BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.US_ASCII));
					String line;
					while ((line = reader.readLine()) != null) {
						int colon = line.indexOf(':');
						if (colon <= 0) {
							continue;
						}
						int cp = Integer.parseInt(line, 0, colon, 16);
						if (!wanted.contains(cp) || this.hex.containsKey(cp)) {
							continue;
						}
						Hex glyph = hexGlyph(cp, line.substring(colon + 1), overrides);
						if (glyph != null) {
							this.hex.put(cp, glyph);
						}
					}
				}
			}
		}
	}

	static @Nullable Hex hexGlyph(int cp, String digits, List<int[]> overrides) {
		int bitWidth = switch (digits.length()) {
			case 32 -> 8;
			case 64 -> 16;
			case 96 -> 24;
			case 128 -> 32;
			default -> -1;
		};
		if (bitWidth < 0) {
			return null;
		}
		int perRow = digits.length() / 16;
		int[] rows = new int[16];
		int mask = 0;
		for (int r = 0; r < 16; r++) {
			long value = Long.parseLong(digits, r * perRow, (r + 1) * perRow, 16);
			rows[r] = (int) (value << (32 - bitWidth));
			mask |= rows[r];
		}
		for (int[] o : overrides) {
			if (cp >= o[0] && cp <= o[1]) {
				return new Hex(rows, o[2], o[3]);
			}
		}
		if (mask == 0) {
			return new Hex(rows, 0, bitWidth);
		}
		return new Hex(rows, Integer.numberOfLeadingZeros(mask), 32 - Integer.numberOfTrailingZeros(mask) - 1);
	}

	public float advance(int cp, boolean bold) {
		Float space = this.spaces.get(cp);
		if (space != null) {
			return space + (bold ? 1.0F : 0.0F);
		}
		Bitmap b = this.bitmaps.get(cp);
		if (b != null) {
			return b.advance() + (bold ? 1.0F : 0.0F);
		}
		Hex h = this.hex.get(cp);
		if (h != null) {
			return h.width() / 2 + 1 + (bold ? 0.5F : 0.0F);
		}
		return MISSING_ADVANCE + (bold ? 1.0F : 0.0F);
	}

	public int sameWidth(int cp, int pick) {
		Bitmap b = this.bitmaps.get(cp);
		if (b == null) {
			return cp;
		}
		List<Integer> same = this.byAdvance.get(b.advance());
		return same == null || same.isEmpty() ? cp : same.get(Math.floorMod(pick, same.size()));
	}

	public interface Canvas {
		int scale();

		void set(int px, int py, int argb);
	}

	public void draw(Canvas canvas, int cp, float x, float y, int color, boolean bold, boolean italic) {
		if (this.spaces.containsKey(cp)) {
			return;
		}
		Hex h = this.hex.get(cp);
		Bitmap b = h == null ? this.bitmaps.get(cp) : null;
		if (h == null && b == null) {
			this.missing(canvas, x, y, color, bold);
			return;
		}
		this.once(canvas, b, h, x, y, color, italic);
		if (bold) {
			this.once(canvas, b, h, x + (h != null ? 0.5F : 1.0F), y, color, italic);
		}
	}

	private void once(Canvas canvas, @Nullable Bitmap b, @Nullable Hex h, float x, float y, int color, boolean italic) {
		int s = canvas.scale();
		float texel;
		float top;
		int columns;
		int rows;
		if (h != null) {
			texel = 0.5F;
			top = y;
			columns = h.width();
			rows = 16;
		} else {
			texel = b.scale();
			top = y + 7 - b.ascent();
			columns = b.width();
			rows = b.cellHeight();
		}
		float x1 = x + columns * texel;
		float y1 = top + rows * texel;
		int px0 = (int) Math.floor((x - 1) * s);
		int px1 = (int) Math.ceil((x1 + 1) * s);
		int py0 = (int) Math.floor(top * s);
		int py1 = (int) Math.ceil(y1 * s);
		for (int py = py0; py < py1; py++) {
			float gy = (py + 0.5F) / s;
			if (gy < top || gy >= y1) {
				continue;
			}
			float slant = italic ? 1.0F - 0.25F * (gy - y) : 0.0F;
			int v = (int) Math.floor((gy - top) / texel);
			for (int px = px0; px < px1; px++) {
				float gx = (px + 0.5F) / s - slant;
				if (gx < x || gx >= x1) {
					continue;
				}
				int u = (int) Math.floor((gx - x) / texel);
				boolean on = h != null ? h.bit(u, v) : (b.image().at(b.u() + u, b.v() + v) >>> 24) != 0;
				if (on) {
					canvas.set(px, py, color);
				}
			}
		}
	}

	private void missing(Canvas canvas, float x, float y, int color, boolean bold) {
		int s = canvas.scale();
		for (int k = 0; k <= (bold ? 1 : 0); k++) {
			int left = Math.round((x + k) * s);
			int top = Math.round(y * s);
			for (int py = top; py < top + 8 * s; py++) {
				for (int px = left; px < left + 5 * s; px++) {
					boolean edge = px < left + s || px >= left + 4 * s || py < top + s || py >= top + 7 * s;
					if (edge) {
						canvas.set(px, py, color);
					}
				}
			}
		}
	}
}
