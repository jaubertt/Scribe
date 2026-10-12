package scribe.client.screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import scribe.client.ScribePack;
import scribe.client.compat.OtherMods;
import scribe.config.ScribeConfig;
import scribe.network.Payloads.ServerFeatures;

public final class SettingsLayout {
	public enum Place {
		MENU,
		HOSTED,
		SERVER,
		NO_SCRIBE;

		public boolean ownsServer() {
			return this == MENU || this == HOSTED;
		}
	}

	public enum Kind {
		TOGGLE,
		CHOICE,
		SLIDER,
		BUTTON,
		NOTE
	}

	public record Item(Kind kind, String key) {
	}

	public record Row(List<Item> items, boolean wide) {
	}

	public record Section(String key, List<Row> rows) {
	}

	public record Switch(String key, Predicate<ScribeConfig> get, BiConsumer<ScribeConfig, Boolean> set, int flag) {
		public boolean server() {
			return this.flag != 0;
		}
	}

	public static final String SCREENS = "screens";
	public static final String PACK = "pack";
	public static final String OFF = "!";
	public static final String MOD = "mod:";
	public static final String NOTE = "server_note";
	public static final String ENCHANT_COLORS = "enchant_colors";
	public static final String PRESETS = "presets";
	public static final String GUIDE = "guide";
	public static final String WRITING_ONLY = "writing_only";
	public static final String WRITING_UTILITIES = "writing_utilities";
	public static final String PERSONALIZATION = "personalization";
	public static final String WORLD_BOOKS = "world_books";
	public static final String WRITING = "writing";

	private static final Map<String, Switch> SWITCHES = new LinkedHashMap<>();
	private static final Map<String, List<String>> NEEDS = new HashMap<>();

	static {
		client(WRITING_ONLY, c -> c.writingOnly, (c, v) -> c.writingOnly = v);
		client(WRITING_UTILITIES, c -> c.writingUtilities, (c, v) -> c.writingUtilities = v);
		client(PERSONALIZATION, c -> c.personalization, (c, v) -> c.personalization = v, OFF + WRITING_ONLY);
		client(WORLD_BOOKS, c -> c.worldBooks, (c, v) -> c.worldBooks = v, OFF + WRITING_ONLY);

		client("replace_editor", c -> c.replaceEditor, (c, v) -> c.replaceEditor = v, WRITING_UTILITIES);
		client("replace_reader", c -> c.replaceReader, (c, v) -> c.replaceReader = v, WRITING_UTILITIES);
		client("replace_lectern", c -> c.replaceLectern, (c, v) -> c.replaceLectern = v, WRITING_UTILITIES);
		client("enchanted_reader", c -> c.enchantedReader, (c, v) -> c.enchantedReader = v, WRITING_UTILITIES);
		needs("layout", WRITING_UTILITIES, SCREENS);
		needs("escape", WRITING_UTILITIES);
		needs("overflow", WRITING_UTILITIES);
		needs("section_key", WRITING_UTILITIES);
		needs("editor_start", WRITING_UTILITIES);
		client("running_header", c -> c.runningHeader, (c, v) -> c.runningHeader = v, WRITING_UTILITIES, SCREENS);
		client("saved_message", c -> c.savedMessage, (c, v) -> c.savedMessage = v, WRITING_UTILITIES);
		client("paste_codes", c -> c.pasteKeepsFormatting, (c, v) -> c.pasteKeepsFormatting = v, WRITING_UTILITIES);
		client("marks", c -> c.formatMarks, (c, v) -> c.formatMarks = v, WRITING_UTILITIES);
		client("backups", c -> c.keepBackups, (c, v) -> c.keepBackups = v, WRITING_UTILITIES);
		client("frames", c -> c.lineUpFrames, (c, v) -> c.lineUpFrames = v, WRITING_UTILITIES);
		client("scroll", c -> c.scrollTurnsPages, (c, v) -> c.scrollTurnsPages = v, WRITING_UTILITIES);
		needs("fast_scroll", "scroll");
		client("invert_scroll", c -> c.invertScroll, (c, v) -> c.invertScroll = v, "scroll");
		client("remember", c -> c.rememberPages, (c, v) -> c.rememberPages = v, WRITING_UTILITIES);
		server("server_pen_names", c -> c.allowPenNames, (c, v) -> c.allowPenNames = v, ServerFeatures.PEN_NAMES, WRITING_UTILITIES);
		server("server_subtitles", c -> c.allowSubtitles, (c, v) -> c.allowSubtitles = v, ServerFeatures.SUBTITLES, WRITING_UTILITIES);
		server("server_contents_links", c -> c.linkContents, (c, v) -> c.linkContents = v, ServerFeatures.CONTENTS_LINKS, WRITING_UTILITIES);
		server("server_sign_formatting", c -> c.signFormatting, (c, v) -> c.signFormatting = v, ServerFeatures.SIGN_FORMATTING, WRITING_UTILITIES);

		client("covers", c -> c.showCovers, (c, v) -> c.showCovers = v, PERSONALIZATION, SCREENS);
		client("cover_designer", c -> c.coverDesigner, (c, v) -> c.coverDesigner = v, "covers", "server_bookbinding");
		client("tint", c -> c.tintDyedBooks, (c, v) -> c.tintDyedBooks = v, PERSONALIZATION);
		client("engraved", c -> c.engravedCovers, (c, v) -> c.engravedCovers = v, "covers");
		client("item_icons", c -> c.itemIcons, (c, v) -> c.itemIcons = v, PERSONALIZATION, PACK);
		client("neutral_glint", c -> c.neutralGlint, (c, v) -> c.neutralGlint = v, "item_icons");
		needs(ENCHANT_COLORS, PERSONALIZATION);
		server("server_bookbinding", c -> c.allowBookbinding, (c, v) -> c.allowBookbinding = v, ServerFeatures.BOOKBINDING, PERSONALIZATION);
		server("server_book_dyeing", c -> c.allowBookDyeing, (c, v) -> c.allowBookDyeing = v, ServerFeatures.BOOK_DYEING, PERSONALIZATION);

		client("lectern_look", c -> c.lecternLook, (c, v) -> c.lecternLook = v, WORLD_BOOKS, PERSONALIZATION);
		client("closed_books", c -> c.closedBooks, (c, v) -> c.closedBooks = v, WORLD_BOOKS, "server_closed_lecterns");
		client("table_look", c -> c.tableLook, (c, v) -> c.tableLook = v, WORLD_BOOKS, PERSONALIZATION);
		client("shelf_colors", c -> c.shelfColors, (c, v) -> c.shelfColors = v, WORLD_BOOKS, PERSONALIZATION, PACK);
		client("reading_pose", c -> c.showReadingPose, (c, v) -> c.showReadingPose = v, WORLD_BOOKS);
		client("bookmarks", c -> c.sendBookmarks, (c, v) -> c.sendBookmarks = v, WORLD_BOOKS, "server_lectern_pages");
		client("reading_librarians", c -> c.showReadingLibrarians, (c, v) -> c.showReadingLibrarians = v, WORLD_BOOKS, "server_reading_librarians");
		client("look_at", c -> c.lookAtTooltips, (c, v) -> c.lookAtTooltips = v, WORLD_BOOKS, MOD + OtherMods.JADE, MOD + OtherMods.WTHIT);
		client("look_lecterns", c -> c.lookAtLecterns, (c, v) -> c.lookAtLecterns = v, "look_at", "server_book_info");
		client("look_chiseled", c -> c.lookAtChiseledBookshelves, (c, v) -> c.lookAtChiseledBookshelves = v, "look_at", "server_book_info",
			MOD + OtherMods.SHELF_VISUALIZER, MOD + OtherMods.BOOKSHELF_INSPECTOR);
		client("look_shelves", c -> c.lookAtShelves, (c, v) -> c.lookAtShelves = v, "look_at");
		client("look_placed", c -> c.lookAtPlacedBooks, (c, v) -> c.lookAtPlacedBooks = v, "look_at");
		needs("look_levels", "look_at");
		needs("look_size", "look_at");
		server("server_closed_lecterns", c -> c.allowClosedLecterns, (c, v) -> c.allowClosedLecterns = v, ServerFeatures.CLOSED_LECTERNS, WORLD_BOOKS);
		server("server_enchanted_lecterns", c -> c.allowEnchantedLecterns, (c, v) -> c.allowEnchantedLecterns = v, ServerFeatures.ENCHANTED_LECTERNS,
			WORLD_BOOKS);
		server("server_lectern_editing", c -> c.allowLecternEditing, (c, v) -> c.allowLecternEditing = v, ServerFeatures.LECTERN_EDITING, WORLD_BOOKS);
		server("server_lectern_pages", c -> c.lecternBookmarks, (c, v) -> c.lecternBookmarks = v, ServerFeatures.LECTERN_PAGES, WORLD_BOOKS);
		server("server_book_info", c -> c.shareBookInfo, (c, v) -> c.shareBookInfo = v, ServerFeatures.BOOK_INFO, WORLD_BOOKS);
		server("server_reading_animation", c -> c.readingAnimation, (c, v) -> c.readingAnimation = v, ServerFeatures.READING_POSE, WORLD_BOOKS);
		server("server_table_dyeing", c -> c.allowTableDyeing, (c, v) -> c.allowTableDyeing = v, ServerFeatures.TABLE_DYEING, WORLD_BOOKS);
		server("server_placeable_books", c -> c.allowPlaceableBooks, (c, v) -> c.allowPlaceableBooks = v, ServerFeatures.PLACED_BOOKS, WORLD_BOOKS);
		server("server_reading_librarians", c -> c.readingLibrarians, (c, v) -> c.readingLibrarians = v, ServerFeatures.READING_LIBRARIANS, WORLD_BOOKS);
	}

	private SettingsLayout() {
	}

	private static void client(String key, Predicate<ScribeConfig> get, BiConsumer<ScribeConfig, Boolean> set, String... needs) {
		SWITCHES.put(key, new Switch(key, get, set, 0));
		needs(key, needs);
	}

	private static void server(String key, Predicate<ScribeConfig> get, BiConsumer<ScribeConfig, Boolean> set, int flag, String... needs) {
		SWITCHES.put(key, new Switch(key, get, set, flag));
		needs(key, needs);
	}

	private static void needs(String key, String... needs) {
		if (needs.length > 0) {
			NEEDS.put(key, List.of(needs));
		}
	}

	public static List<Row> top() {
		return List.of(row(button(PRESETS), button(GUIDE)), wide(toggle(WRITING_ONLY)));
	}

	public static List<Section> sections() {
		return List.of(
			section(WRITING,
				wide(toggle(WRITING_UTILITIES)),
				row(toggle("replace_editor"), toggle("replace_reader")),
				row(toggle("replace_lectern"), choice("layout")),
				wide(toggle("enchanted_reader")),
				row(choice("escape"), toggle("saved_message")),
				row(choice("overflow"), toggle("paste_codes")),
				wide(choice("section_key")),
				wide(toggle("marks")),
				row(choice("editor_start"), toggle("backups")),
				row(toggle("frames"), toggle("running_header")),
				row(toggle("scroll"), new Item(Kind.SLIDER, "fast_scroll")),
				row(toggle("invert_scroll"), toggle("remember")),
				note(WRITING),
				row(toggle("server_pen_names"), toggle("server_subtitles")),
				row(toggle("server_contents_links"), toggle("server_sign_formatting"))),
			section(PERSONALIZATION,
				wide(toggle(PERSONALIZATION)),
				row(toggle("covers"), toggle("cover_designer")),
				row(toggle("item_icons"), toggle("neutral_glint")),
				row(toggle("tint"), toggle("engraved")),
				wide(button(ENCHANT_COLORS)),
				note(PERSONALIZATION),
				row(toggle("server_bookbinding"), toggle("server_book_dyeing"))),
			section(WORLD_BOOKS,
				wide(toggle(WORLD_BOOKS)),
				row(toggle("lectern_look"), toggle("closed_books")),
				row(toggle("table_look"), toggle("shelf_colors")),
				row(toggle("reading_pose"), toggle("reading_librarians")),
				row(toggle("bookmarks")),
				row(toggle("look_at"), toggle("look_lecterns")),
				row(toggle("look_chiseled"), toggle("look_shelves")),
				row(toggle("look_placed")),
				row(choice("look_levels"), choice("look_size")),
				note(WORLD_BOOKS),
				row(toggle("server_closed_lecterns"), toggle("server_enchanted_lecterns")),
				row(toggle("server_lectern_editing"), toggle("server_lectern_pages")),
				row(toggle("server_book_info"), toggle("server_table_dyeing")),
				row(toggle("server_reading_animation"), toggle("server_reading_librarians")),
				row(toggle("server_placeable_books"))));
	}

	private static Section section(String key, Row... rows) {
		return new Section(key, List.of(rows));
	}

	private static Row row(Item... items) {
		return new Row(List.of(items), false);
	}

	private static Row wide(Item item) {
		return new Row(List.of(item), true);
	}

	private static Row note(String section) {
		return wide(new Item(Kind.NOTE, NOTE + "." + section));
	}

	private static Item toggle(String key) {
		return new Item(Kind.TOGGLE, key);
	}

	private static Item choice(String key) {
		return new Item(Kind.CHOICE, key);
	}

	private static Item button(String key) {
		return new Item(Kind.BUTTON, key);
	}

	public static List<Item> items() {
		List<Item> out = new ArrayList<>();
		for (Row row : top()) {
			out.addAll(row.items());
		}
		for (Section section : sections()) {
			for (Row row : section.rows()) {
				out.addAll(row.items());
			}
		}
		return out;
	}

	public static @Nullable Switch switchOf(String key) {
		return SWITCHES.get(key);
	}

	public static List<Switch> switches() {
		return List.copyOf(SWITCHES.values());
	}

	public static List<String> needs(String key) {
		return NEEDS.getOrDefault(key, List.of());
	}

	public static List<String> needs(String key, Place place) {
		Switch s = SWITCHES.get(key);
		if (s != null && s.server() && !place.ownsServer()) {
			return List.of();
		}
		return needs(key);
	}

	public static boolean own(String key, ScribeConfig config, Place place, int serverFlags) {
		if (SCREENS.equals(key)) {
			return config.anyBookScreen();
		}
		if (PACK.equals(key)) {
			return ScribePack.selected();
		}
		if (key.startsWith(OFF)) {
			return !own(key.substring(OFF.length()), config, place, serverFlags);
		}
		if (key.startsWith(MOD)) {
			return !OtherMods.installed.test(key.substring(MOD.length()));
		}
		Switch s = SWITCHES.get(key);
		if (s == null) {
			return true;
		}
		if (s.server() && !place.ownsServer()) {
			return (serverFlags & s.flag()) != 0;
		}
		return s.get().test(config);
	}

	public static @Nullable String blockedBy(String key, ScribeConfig config, Place place, int serverFlags) {
		for (String need : needs(key, place)) {
			String deeper = need.startsWith(OFF) || need.startsWith(MOD) ? null : blockedBy(need, config, place, serverFlags);
			if (deeper != null) {
				return deeper;
			}
			if (!own(need, config, place, serverFlags)) {
				return need;
			}
		}
		return null;
	}

	public static boolean on(String key, ScribeConfig config, Place place, int serverFlags) {
		return own(key, config, place, serverFlags) && blockedBy(key, config, place, serverFlags) == null;
	}

	public static boolean active(String key, ScribeConfig config, Place place, int serverFlags) {
		Switch s = SWITCHES.get(key);
		if (s != null && s.server() && !place.ownsServer()) {
			return false;
		}
		return blockedBy(key, config, place, serverFlags) == null;
	}

	public static boolean shown(String key, ScribeConfig config, Place place, int serverFlags) {
		return on(key, config, place, serverFlags);
	}

	public static void set(String key, ScribeConfig config, boolean value) {
		Switch s = SWITCHES.get(key);
		if (s != null) {
			s.set().accept(config, value);
		}
	}

	public static @Nullable String sectionOf(String key) {
		for (Section section : sections()) {
			for (Row row : section.rows()) {
				for (Item item : row.items()) {
					if (item.key().equals(key)) {
						return section.key();
					}
				}
			}
		}
		return null;
	}

	public static int flagsOf(ScribeConfig config) {
		int flags = 0;
		for (Switch s : SWITCHES.values()) {
			if (s.server() && on(s.key(), config, Place.HOSTED, 0)) {
				flags |= s.flag();
			}
		}
		return flags;
	}

	public static List<String> keys(Kind... kinds) {
		List<Kind> wanted = Arrays.asList(kinds);
		return items().stream().filter(item -> wanted.contains(item.kind())).map(Item::key).toList();
	}
}
