package scribe.client.colors;

import scribe.Scribe;
import scribe.config.ScribeConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

public final class EnchantColors {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static EnchantColors instance;

	public enum Mode { OFF, CATEGORY, ENCHANTMENT }

	public enum Style { CLASSIC, RIBBON }

	public enum RibbonMode { ALL, CATEGORY }

	public static final int REGULAR_RIBBON = 0xFFF22C43;
	public static final int CURSED_RIBBON = 0xFF3A3A3A;

	public enum Category {
		ARMOR(DyeColor.ORANGE), WEAPONS(DyeColor.RED), TOOLS(DyeColor.CYAN), BOWS(DyeColor.PURPLE),
		TRIDENTS(DyeColor.MAGENTA), FISHING(DyeColor.GREEN), CURSES(DyeColor.BLACK), OTHER(DyeColor.LIME);

		final DyeColor defaultColor;

		Category(DyeColor defaultColor) {
			this.defaultColor = defaultColor;
		}

		public String key() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	static final Map<String, DyeColor> DEFAULTS = Map.ofEntries(
		Map.entry("minecraft:protection", DyeColor.ORANGE),
		Map.entry("minecraft:fire_protection", DyeColor.ORANGE),
		Map.entry("minecraft:feather_falling", DyeColor.WHITE),
		Map.entry("minecraft:blast_protection", DyeColor.ORANGE),
		Map.entry("minecraft:projectile_protection", DyeColor.ORANGE),
		Map.entry("minecraft:respiration", DyeColor.LIGHT_BLUE),
		Map.entry("minecraft:aqua_affinity", DyeColor.BLUE),
		Map.entry("minecraft:thorns", DyeColor.GREEN),
		Map.entry("minecraft:depth_strider", DyeColor.LIGHT_GRAY),
		Map.entry("minecraft:frost_walker", DyeColor.LIGHT_GRAY),
		Map.entry("minecraft:binding_curse", DyeColor.BLACK),
		Map.entry("minecraft:soul_speed", DyeColor.CYAN),
		Map.entry("minecraft:swift_sneak", DyeColor.CYAN),
		Map.entry("minecraft:sharpness", DyeColor.GRAY),
		Map.entry("minecraft:smite", DyeColor.GRAY),
		Map.entry("minecraft:bane_of_arthropods", DyeColor.GRAY),
		Map.entry("minecraft:knockback", DyeColor.PINK),
		Map.entry("minecraft:fire_aspect", DyeColor.RED),
		Map.entry("minecraft:looting", DyeColor.YELLOW),
		Map.entry("minecraft:sweeping_edge", DyeColor.WHITE),
		Map.entry("minecraft:efficiency", DyeColor.CYAN),
		Map.entry("minecraft:silk_touch", DyeColor.YELLOW),
		Map.entry("minecraft:unbreaking", DyeColor.BLUE),
		Map.entry("minecraft:fortune", DyeColor.YELLOW),
		Map.entry("minecraft:power", DyeColor.PURPLE),
		Map.entry("minecraft:punch", DyeColor.PINK),
		Map.entry("minecraft:flame", DyeColor.RED),
		Map.entry("minecraft:infinity", DyeColor.LIME),
		Map.entry("minecraft:luck_of_the_sea", DyeColor.GREEN),
		Map.entry("minecraft:lure", DyeColor.PINK),
		Map.entry("minecraft:loyalty", DyeColor.MAGENTA),
		Map.entry("minecraft:impaling", DyeColor.PURPLE),
		Map.entry("minecraft:riptide", DyeColor.MAGENTA),
		Map.entry("minecraft:channeling", DyeColor.LIGHT_BLUE),
		Map.entry("minecraft:multishot", DyeColor.BROWN),
		Map.entry("minecraft:quick_charge", DyeColor.CYAN),
		Map.entry("minecraft:piercing", DyeColor.BROWN),
		Map.entry("minecraft:density", DyeColor.GRAY),
		Map.entry("minecraft:breach", DyeColor.GRAY),
		Map.entry("minecraft:wind_burst", DyeColor.WHITE),
		Map.entry("minecraft:lunge", DyeColor.MAGENTA),
		Map.entry("minecraft:mending", DyeColor.LIME),
		Map.entry("minecraft:vanishing_curse", DyeColor.BLACK)
	);

	private static final class Stored {
		String style = "classic";
		String mode = "off";
		boolean levels;
		Map<String, String> categories = new LinkedHashMap<>();
		Map<String, String> enchantments = new LinkedHashMap<>();
		String ribbon_mode = "all";
		String ribbon_all = "none";
		Map<String, String> ribbon_categories = new LinkedHashMap<>();
		boolean curse_look = true;
	}

	private Style style = Style.CLASSIC;
	private Mode mode = Mode.OFF;
	private boolean levelShades;
	private final Map<Category, @Nullable DyeColor> categories = new LinkedHashMap<>();
	private final Map<String, DyeColor> enchantments = new LinkedHashMap<>();
	private RibbonMode ribbonMode = RibbonMode.ALL;
	private @Nullable DyeColor ribbonAll;
	private final Map<Category, @Nullable DyeColor> ribbonCategories = new LinkedHashMap<>();
	private boolean curseLook = true;

	public static EnchantColors get() {
		if (instance == null) {
			instance = new EnchantColors();
			instance.load();
		}
		return instance;
	}

	private EnchantColors() {
		for (Category category : Category.values()) {
			this.categories.put(category, category.defaultColor);
			this.ribbonCategories.put(category, null);
		}
	}

	public Style style() {
		return this.style;
	}

	public void setStyle(Style style) {
		this.style = style;
		this.save();
	}

	public Mode mode() {
		return this.mode;
	}

	public void setMode(Mode mode) {
		this.mode = mode;
		this.save();
	}

	public boolean levelShades() {
		return this.levelShades;
	}

	public void setLevelShades(boolean on) {
		this.levelShades = on;
		this.save();
	}

	public @Nullable DyeColor category(Category category) {
		return this.categories.get(category);
	}

	public void setCategory(Category category, @Nullable DyeColor color) {
		this.categories.put(category, color);
		this.save();
	}

	public Optional<DyeColor> enchantment(String id) {
		if (this.enchantments.containsKey(id)) {
			return Optional.ofNullable(this.enchantments.get(id));
		}
		return Optional.ofNullable(DEFAULTS.get(id));
	}

	public void setEnchantment(String id, @Nullable DyeColor color) {
		this.enchantments.put(id, color);
		this.save();
	}

	public RibbonMode ribbonMode() {
		return this.ribbonMode;
	}

	public void setRibbonMode(RibbonMode mode) {
		this.ribbonMode = mode;
		this.save();
	}

	public @Nullable DyeColor ribbonAll() {
		return this.ribbonAll;
	}

	public void setRibbonAll(@Nullable DyeColor color) {
		this.ribbonAll = color;
		this.save();
	}

	public @Nullable DyeColor ribbonCategory(Category category) {
		return this.ribbonCategories.get(category);
	}

	public void setRibbonCategory(Category category, @Nullable DyeColor color) {
		this.ribbonCategories.put(category, color);
		this.save();
	}

	public boolean curseLook() {
		return this.curseLook;
	}

	public void setCurseLook(boolean on) {
		this.curseLook = on;
		this.save();
	}

	public static Category categoryOf(Holder<Enchantment> enchantment) {
		if (enchantment.is(EnchantmentTags.CURSE)) {
			return Category.CURSES;
		}
		String tag = enchantment.value().definition().supportedItems().unwrapKey().map(key -> key.location().getPath()).orElse("");
		return switch (tag) {
			case "enchantable/armor", "enchantable/foot_armor", "enchantable/leg_armor", "enchantable/chest_armor", "enchantable/head_armor" -> Category.ARMOR;
			case "enchantable/sword", "enchantable/sharp_weapon", "enchantable/fire_aspect", "enchantable/weapon", "enchantable/melee_weapon",
				"enchantable/mace", "enchantable/spear", "enchantable/lunge" -> Category.WEAPONS;
			case "enchantable/mining", "enchantable/mining_loot", "enchantable/durability" -> Category.TOOLS;
			case "enchantable/bow", "enchantable/crossbow" -> Category.BOWS;
			case "enchantable/trident" -> Category.TRIDENTS;
			case "enchantable/fishing" -> Category.FISHING;
			default -> Category.OTHER;
		};
	}

	public int bookColor(ItemStack stack, int color) {
		if (!stack.is(Items.ENCHANTED_BOOK)) {
			return color;
		}
		ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
		if (stored == null || stored.isEmpty()) {
			return color;
		}
		Holder<Enchantment> first = stored.keySet().iterator().next();
		if (!stack.has(DataComponents.DYED_COLOR)) {
			DyeColor display = this.displayColor(first);
			if (display != null) {
				color = ARGB.opaque(display.getTextureDiffuseColor());
			}
		}
		if (this.levelShades) {
			color = LevelShade.shade(color, stored.getLevel(first), first.value().getMaxLevel());
		}
		return color;
	}

	private @Nullable DyeColor displayColor(Holder<Enchantment> enchantment) {
		if (this.mode == Mode.OFF) {
			return null;
		}
		if (this.mode == Mode.ENCHANTMENT) {
			String id = enchantment.unwrapKey().map(key -> key.identifier().toString()).orElse("");
			DyeColor color = this.enchantment(id).orElse(null);
			if (color != null) {
				return color;
			}
		}
		return this.categories.get(categoryOf(enchantment));
	}

	public int ribbonColor(ItemStack stack) {
		ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
		if (stored == null || stored.isEmpty()) {
			return colorOr(this.ribbonAll, REGULAR_RIBBON);
		}
		if (this.curseLook && hasCurse(stored)) {
			return CURSED_RIBBON;
		}
		if (this.ribbonMode == RibbonMode.CATEGORY) {
			DyeColor color = this.ribbonCategories.get(categoryOf(stored.keySet().iterator().next()));
			if (color != null) {
				return colorOr(color, REGULAR_RIBBON);
			}
		}
		return colorOr(this.ribbonAll, REGULAR_RIBBON);
	}

	private static int colorOr(@Nullable DyeColor color, int regular) {
		return color == null ? regular : ARGB.opaque(color.getTextureDiffuseColor());
	}

	private static boolean hasCurse(ItemEnchantments stored) {
		for (Holder<Enchantment> enchantment : stored.keySet()) {
			if (enchantment.is(EnchantmentTags.CURSE)) {
				return true;
			}
		}
		return false;
	}

	public float styleValue(ItemStack stack) {
		return this.style != Style.RIBBON ? -1.0F : this.markLevel(stack);
	}

	public int markLevel(ItemStack stack) {
		ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
		if (stored == null || stored.isEmpty()) {
			return 1;
		}
		if (this.curseLook && hasCurse(stored)) {
			return 0;
		}
		int level = 1;
		for (var entry : stored.entrySet()) {
			level = Math.max(level, entry.getIntValue());
		}
		return level;
	}

	private static Path file() {
		return ScribeConfig.directory().resolve("enchant_colors.json");
	}

	private static @Nullable DyeColor parse(@Nullable String name) {
		return name == null || name.isEmpty() || name.equals("none") ? null : DyeColor.byName(name, null);
	}

	private void load() {
		Path path = file();
		if (!Files.isRegularFile(path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			Stored stored = GSON.fromJson(reader, Stored.class);
			if (stored == null) {
				return;
			}
			this.mode = parseEnum(Mode.class, stored.mode, Mode.OFF);
			this.style = parseEnum(Style.class, stored.style, Style.CLASSIC);
			this.ribbonMode = parseEnum(RibbonMode.class, stored.ribbon_mode, RibbonMode.ALL);
			this.levelShades = stored.levels;
			this.curseLook = stored.curse_look;
			this.ribbonAll = parse(stored.ribbon_all);
			readCategories(stored.categories, this.categories);
			readCategories(stored.ribbon_categories, this.ribbonCategories);
			if (stored.enchantments != null) {
				stored.enchantments.forEach((id, name) -> this.enchantments.put(id, parse(name)));
			}
		} catch (IOException | JsonParseException e) {
			Scribe.LOGGER.warn("Could not read {}", path, e);
		}
	}

	private static <E extends Enum<E>> E parseEnum(Class<E> type, @Nullable String name, E fallback) {
		try {
			return name == null ? fallback : Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}

	private static void readCategories(@Nullable Map<String, String> stored, Map<Category, @Nullable DyeColor> into) {
		if (stored == null) {
			return;
		}
		for (Category category : Category.values()) {
			if (stored.containsKey(category.key())) {
				into.put(category, parse(stored.get(category.key())));
			}
		}
	}

	private static String name(@Nullable DyeColor color) {
		return color == null ? "none" : color.getSerializedName();
	}

	private void save() {
		Stored stored = new Stored();
		stored.style = this.style.name().toLowerCase(Locale.ROOT);
		stored.mode = this.mode.name().toLowerCase(Locale.ROOT);
		stored.levels = this.levelShades;
		stored.ribbon_mode = this.ribbonMode.name().toLowerCase(Locale.ROOT);
		stored.ribbon_all = name(this.ribbonAll);
		stored.curse_look = this.curseLook;
		this.categories.forEach((category, color) -> stored.categories.put(category.key(), name(color)));
		this.ribbonCategories.forEach((category, color) -> stored.ribbon_categories.put(category.key(), name(color)));
		this.enchantments.forEach((id, color) -> stored.enchantments.put(id, color == null ? "" : color.getSerializedName()));
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(stored, writer);
			}
		} catch (IOException e) {
			Scribe.LOGGER.warn("Could not save {}", path, e);
		}
	}
}
