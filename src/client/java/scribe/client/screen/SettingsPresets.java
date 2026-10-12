package scribe.client.screen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import scribe.config.ScribeConfig;

public final class SettingsPresets {
	public enum Preset {
		WRITER("writer"),
		READER("reader"),
		VANILLA("vanilla");

		private final String key;

		Preset(String key) {
			this.key = key;
		}

		public String key() {
			return "scribe.presets." + this.key;
		}

		public Map<String, Boolean> switches() {
			return Collections.unmodifiableMap(SettingsPresets.switches(this));
		}

		public void apply(ScribeConfig config) {
			for (Map.Entry<String, Boolean> entry : this.switches().entrySet()) {
				SettingsLayout.set(entry.getKey(), config, entry.getValue());
			}
		}
	}

	private static final String[] LOOKS = {"personalization", "covers", "tint", "item_icons"};
	private static final String[] WORLD = {"world_books", "lectern_look", "closed_books", "table_look", "shelf_colors", "reading_pose", "bookmarks",
		"reading_librarians", "look_at", "look_lecterns", "look_chiseled", "look_shelves", "look_placed"};

	private SettingsPresets() {
	}

	private static Map<String, Boolean> switches(Preset preset) {
		Map<String, Boolean> out = new LinkedHashMap<>();
		switch (preset) {
			case WRITER -> {
				out.put("writing_only", false);
				screens(out, true);
				out.put("running_header", true);
				out.put("saved_message", true);
				out.put("marks", true);
				out.put("backups", true);
				out.put("frames", true);
				out.put("scroll", true);
				out.put("remember", true);
				all(out, LOOKS, true);
				out.put("cover_designer", true);
				all(out, WORLD, true);
			}
			case READER -> {
				out.put("writing_only", false);
				screens(out, true);
				out.put("running_header", true);
				out.put("saved_message", false);
				out.put("marks", false);
				out.put("backups", true);
				out.put("frames", false);
				out.put("scroll", true);
				out.put("remember", true);
				all(out, LOOKS, true);
				out.put("cover_designer", false);
				all(out, WORLD, true);
			}
			case VANILLA -> {
				out.put("writing_only", true);
				out.put("replace_editor", true);
				out.put("replace_reader", true);
				out.put("replace_lectern", true);
				out.put("enchanted_reader", false);
				out.put("running_header", false);
				out.put("saved_message", false);
				out.put("marks", false);
				out.put("backups", true);
				out.put("frames", false);
				out.put("scroll", false);
				out.put("remember", false);
			}
		}
		return out;
	}

	private static void screens(Map<String, Boolean> out, boolean on) {
		out.put("replace_editor", on);
		out.put("replace_reader", on);
		out.put("replace_lectern", on);
		out.put("enchanted_reader", on);
	}

	private static void all(Map<String, Boolean> out, String[] keys, boolean on) {
		for (String key : keys) {
			out.put(key, on);
		}
	}
}
