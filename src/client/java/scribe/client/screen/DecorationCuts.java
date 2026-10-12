package scribe.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import scribe.Scribe;

public final class DecorationCuts {
	static final String FILE = "/assets/scribe/cover/decoration_cuts.json";

	public record Cut(int index, int count) {
	}

	private static @Nullable Map<String, Cut> table;

	private DecorationCuts() {
	}

	public static @Nullable Cut of(@Nullable String decoration, @Nullable String corner) {
		if (decoration == null || corner == null) {
			return null;
		}
		if (table == null) {
			table = load();
		}
		return table.get(key(decoration, corner));
	}

	static String key(String decoration, String corner) {
		return decoration + '/' + corner;
	}

	private static Map<String, Cut> load() {
		try (InputStream in = DecorationCuts.class.getResourceAsStream(FILE)) {
			if (in == null) {
				Scribe.LOGGER.warn("The cover decoration cuts ({}) are missing, drawing decorations whole", FILE);
				return Map.of();
			}
			return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException e) {
			Scribe.LOGGER.warn("Could not read the cover decoration cuts ({}), drawing decorations whole", FILE, e);
			return Map.of();
		}
	}

	static Map<String, Cut> parse(Reader json) {
		Map<String, Cut> out = new HashMap<>();
		JsonObject root = JsonParser.parseReader(json).getAsJsonObject();
		for (Map.Entry<String, JsonElement> decoration : root.entrySet()) {
			JsonObject corners = decoration.getValue().getAsJsonObject();
			int count = 0;
			for (Map.Entry<String, JsonElement> corner : corners.entrySet()) {
				count = Math.max(count, corner.getValue().getAsInt() + 1);
			}
			for (Map.Entry<String, JsonElement> corner : corners.entrySet()) {
				int index = corner.getValue().getAsInt();
				if (index < 0) {
					throw new IllegalArgumentException("a cut's place is never negative: " + decoration.getKey() + " " + corner.getKey());
				}
				out.put(key(decoration.getKey(), corner.getKey()), new Cut(index, count));
			}
		}
		return out;
	}
}
