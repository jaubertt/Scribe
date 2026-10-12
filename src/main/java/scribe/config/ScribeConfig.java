package scribe.config;

import scribe.Scribe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

public final class ScribeConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static ScribeConfig instance;
	private static final List<Runnable> RELOADED = new CopyOnWriteArrayList<>();

	public boolean writingOnly = false;
	public boolean personalization = true;
	public boolean worldBooks = true;
	private @Nullable Boolean worldLooks;

	public boolean coverDesigner = true;
	public boolean engravedCovers = true;
	public boolean itemIcons = true;
	public boolean lecternLook = true;
	public boolean closedBooks = true;
	public boolean tableLook = true;
	public boolean shelfColors = true;
	public boolean showReadingLibrarians = true;

	public boolean replaceEditor = true;
	public boolean replaceReader = true;
	public boolean replaceLectern = true;
	public boolean enchantedReader = true;
	public Layout layout = Layout.AUTO;
	public boolean tintDyedBooks = true;
	public boolean showCovers = true;
	public boolean neutralGlint = true;
	public boolean runningHeader = true;
	public boolean lookAtTooltips = true;
	public boolean lookAtLecterns = true;
	public boolean lookAtChiseledBookshelves = true;
	public boolean lookAtShelves = true;
	public boolean lookAtPlacedBooks = true;
	public LevelNumerals lookAtLevels = LevelNumerals.ROMAN;
	public TextSize lookAtSize = TextSize.NORMAL;
	public boolean showReadingPose = true;

	public EscapeAction escapeAction = EscapeAction.SAVE;
	public boolean savedMessage = true;
	public OverflowMode overflowMode = OverflowMode.FLOW;
	public boolean pasteKeepsFormatting = true;
	public SectionSignKey sectionSignKey = SectionSignKey.LEFT_ALT;
	public boolean formatMarks = false;
	public EditorStart editorStart = EditorStart.REMEMBERED;
	public boolean lineUpFrames = true;
	public String recentSymbols = "";

	public boolean scrollTurnsPages = true;
	public boolean invertScroll = false;
	public int fastScrollPages = 10;
	public static final int MIN_FAST_SCROLL_PAGES = 1;
	public static final int MAX_FAST_SCROLL_PAGES = 100;
	public boolean rememberPages = true;
	public int maxRememberedBooks = 1000;

	public boolean keepBackups = true;
	public int maxBackups = 100;

	public boolean sendBookmarks = true;
	public boolean lecternBookmarks = true;
	public boolean allowPenNames = true;
	public boolean allowSubtitles = true;
	public boolean allowLecternEditing = true;
	public boolean readingAnimation = true;
	public boolean shareBookInfo = true;
	public boolean allowEnchantedLecterns = true;
	public boolean allowClosedLecterns = true;
	public boolean allowBookbinding = true;
	public boolean linkContents = true;
	public boolean signFormatting = true;
	public boolean allowPlaceableBooks = true;
	public boolean allowBookDyeing = true;
	public boolean allowTableDyeing = true;
	public boolean readingLibrarians = true;

	public enum Layout { AUTO, SINGLE, SPREAD }

	public enum EscapeAction { SAVE, ASK, DISCARD }

	public enum OverflowMode { FLOW, NEW_PAGE }

	public enum SectionSignKey { LEFT_ALT, RIGHT_ALT, OFF }

	public enum EditorStart { REMEMBERED, FIRST, LAST }

	public enum LevelNumerals { ROMAN, NUMBERS }

	public enum TextSize {
		SMALLEST(0.5F), SMALL(0.75F), NORMAL(1.0F), LARGE(1.25F), LARGEST(1.5F);

		public final float scale;

		TextSize(float scale) {
			this.scale = scale;
		}
	}

	public static ScribeConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public boolean personalizationOn() {
		return this.personalization && !this.writingOnly;
	}

	public boolean worldBooksOn() {
		return this.worldBooks && !this.writingOnly;
	}

	public boolean anyBookScreen() {
		return this.replaceEditor || this.replaceReader || this.replaceLectern || this.enchantedReader;
	}

	public boolean coversShown() {
		return this.personalizationOn() && this.showCovers && this.anyBookScreen();
	}

	public boolean designerShown() {
		return this.coversShown() && this.coverDesigner;
	}

	public boolean tintShown() {
		return this.personalizationOn() && this.tintDyedBooks;
	}

	public boolean iconsShown() {
		return this.personalizationOn() && this.itemIcons;
	}

	public boolean neutralGlintShown() {
		return this.iconsShown() && this.neutralGlint;
	}

	public boolean lecternLookShown() {
		return this.worldBooksOn() && this.personalizationOn() && this.lecternLook;
	}

	public boolean closedBooksShown() {
		return this.worldBooksOn() && this.closedBooks;
	}

	public boolean tableLookShown() {
		return this.worldBooksOn() && this.personalizationOn() && this.tableLook;
	}

	public boolean shelfColorsShown() {
		return this.worldBooksOn() && this.personalizationOn() && this.shelfColors;
	}

	public boolean readingPoseShown() {
		return this.worldBooksOn() && this.showReadingPose;
	}

	public boolean sendsLastPage() {
		return this.worldBooksOn() && this.sendBookmarks;
	}

	public boolean lookAtShown() {
		return this.worldBooksOn() && this.lookAtTooltips;
	}

	public boolean bookbindingOn() {
		return this.personalizationOn() && this.allowBookbinding;
	}

	public boolean bookDyeingOn() {
		return this.personalizationOn() && this.allowBookDyeing;
	}

	public boolean closedLecternsOn() {
		return this.worldBooksOn() && this.allowClosedLecterns;
	}

	public boolean enchantedLecternsOn() {
		return this.worldBooksOn() && this.allowEnchantedLecterns;
	}

	public boolean lecternEditingOn() {
		return this.worldBooksOn() && this.allowLecternEditing;
	}

	public boolean lecternPagesOn() {
		return this.worldBooksOn() && this.lecternBookmarks;
	}

	public boolean bookInfoOn() {
		return this.worldBooksOn() && this.shareBookInfo;
	}

	public boolean sharedReadingPoseOn() {
		return this.worldBooksOn() && this.readingAnimation;
	}

	public boolean tableDyeingOn() {
		return this.worldBooksOn() && this.allowTableDyeing;
	}

	public boolean placeableBooksOn() {
		return this.worldBooksOn() && this.allowPlaceableBooks;
	}

	public boolean readingLibrariansShown() {
		return this.worldBooksOn() && this.showReadingLibrarians;
	}

	public boolean readingLibrariansOn() {
		return this.worldBooksOn() && this.readingLibrarians;
	}

	public static @Nullable String reload() {
		Path path = file();
		ScribeConfig fresh;
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				fresh = GSON.fromJson(reader, ScribeConfig.class);
			} catch (IOException | JsonParseException e) {
				Scribe.LOGGER.error("Could not read {} again, the settings stay as they were", path, e);
				return String.valueOf(e.getMessage());
			}
			if (fresh == null) {
				return "empty file";
			}
		} else {
			fresh = new ScribeConfig();
		}
		fresh.sanitize();
		ScribeConfig current = get();
		current.copyFrom(fresh);
		current.save();
		for (Runnable listener : RELOADED) {
			listener.run();
		}
		return null;
	}

	private void copyFrom(ScribeConfig other) {
		for (Field field : ScribeConfig.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			try {
				field.set(this, field.get(other));
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	public static void onReload(Runnable listener) {
		RELOADED.add(listener);
	}

	public static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve(Scribe.MOD_ID);
	}

	private static Path file() {
		return directory().resolve("config.json");
	}

	private static ScribeConfig load() {
		Path path = file();
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				ScribeConfig loaded = GSON.fromJson(reader, ScribeConfig.class);
				if (loaded != null) {
					loaded.sanitize();
					loaded.save();
					return loaded;
				}
			} catch (IOException | JsonParseException e) {
				Scribe.LOGGER.error("Could not read {}, using defaults (the broken file is kept as config.json.broken)", path, e);
				try {
					Files.copy(path, path.resolveSibling("config.json.broken"), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ignored) {
				}
			}
		}
		ScribeConfig defaults = new ScribeConfig();
		defaults.save();
		return defaults;
	}

	private void sanitize() {
		if (this.worldLooks != null) {
			if (!this.worldLooks) {
				this.itemIcons = false;
				this.lecternLook = false;
				this.closedBooks = false;
				this.tableLook = false;
				this.shelfColors = false;
			}
			this.worldLooks = null;
		}
		ScribeConfig d = new ScribeConfig();
		if (this.layout == null) this.layout = d.layout;
		if (this.escapeAction == null) this.escapeAction = d.escapeAction;
		if (this.overflowMode == null) this.overflowMode = d.overflowMode;
		if (this.sectionSignKey == null) this.sectionSignKey = d.sectionSignKey;
		if (this.editorStart == null) this.editorStart = d.editorStart;
		if (this.lookAtLevels == null) this.lookAtLevels = d.lookAtLevels;
		if (this.lookAtSize == null) this.lookAtSize = d.lookAtSize;
		if (this.recentSymbols == null) this.recentSymbols = "";
		this.fastScrollPages = Math.clamp(this.fastScrollPages, MIN_FAST_SCROLL_PAGES, MAX_FAST_SCROLL_PAGES);
		this.maxRememberedBooks = Math.clamp(this.maxRememberedBooks, 10, 100_000);
		this.maxBackups = Math.clamp(this.maxBackups, 1, 10_000);
	}

	public void save() {
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			Scribe.LOGGER.error("Could not save {}", path, e);
		}
	}
}
