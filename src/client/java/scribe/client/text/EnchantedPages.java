package scribe.client.text;

import scribe.BookLimits;
import scribe.EnchantedBooks;
import scribe.Scribe;
import scribe.client.LookAtTooltip;
import scribe.client.mixin.FontAccessor;
import scribe.client.screen.BookStyle;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.fabricmc.fabric.api.item.v1.FabricItemStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

public final class EnchantedPages {
	private static final Style CURSE = Style.EMPTY.withColor(ChatFormatting.DARK_RED);
	private static final Style TITLE = Style.EMPTY.withBold(true);
	private static final Style SOFT = Style.EMPTY.withColor(BookStyle.INK_SOFT & 0xFFFFFF);
	private static final Style NONE = SOFT.withItalic(true);
	private static final Style INK = Style.EMPTY.withColor(BookStyle.INK & 0xFFFFFF);
	public static final FontDescription GALACTIC_FONT = new FontDescription.Resource(Identifier.withDefaultNamespace("alt"));
	private static final Style GALACTIC_TITLE = TITLE.withFont(GALACTIC_FONT);
	private static final Style CURSE_GALACTIC_TITLE = CURSE.withBold(true).withFont(GALACTIC_FONT);
	public static final String APPLIES_KEY = "scribe.enchanted.applies.";
	public static final String ITEM_KEY = "scribe.enchanted.item.";
	static final int MAX_ITEM_NAMES = 3;
	public static final List<String> KINDS = List.of("#minecraft:enchantable/durability", "#minecraft:enchantable/armor", "#minecraft:head_armor",
		"#minecraft:chest_armor", "#minecraft:leg_armor", "#minecraft:foot_armor", "#minecraft:swords", "#minecraft:spears", "#minecraft:pickaxes",
		"#minecraft:shovels", "#minecraft:axes", "#minecraft:hoes", "minecraft:mace", "minecraft:shears", "minecraft:bow", "minecraft:crossbow",
		"minecraft:trident", "minecraft:fishing_rod", "minecraft:shield", "minecraft:elytra", "minecraft:flint_and_steel", "minecraft:brush",
		"minecraft:carrot_on_a_stick", "minecraft:warped_fungus_on_a_stick", "minecraft:compass", "#minecraft:skulls", "minecraft:carved_pumpkin");
	private static final int HEADER_LINES = 2;

	public record Entry(String text, boolean curse, String galactic, String description, String maxLevel, String primaryItems,
		String secondaryItems, String exclusive) {
		public Entry(String text, boolean curse) {
			this(text, curse, "", "", "", "", "", "");
		}
	}

	public record ItemWords(String primary, String secondary) {
		public static final ItemWords NONE = new ItemWords("", "");
	}

	@FunctionalInterface
	public interface Tags {
		@Nullable Collection<Identifier> items(TagKey<Item> tag);

		Tags GAME = tag -> BuiltInRegistries.ITEM.get(tag).map(EnchantedPages::ids).orElse(null);
	}

	public record Book(List<Component> pages, SharedPages shared) {
	}

	private EnchantedPages() {
	}

	public static Book of(ItemStack stack, Font font) {
		return layout(stack.getHoverName().getString(), entries(stack, font), font);
	}

	public static List<Entry> entries(ItemStack stack, Font font) {
		ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		Registry<Enchantment> registry = registry();
		HolderSet<Enchantment> order = tooltipOrder(registry);
		IntPredicate glyphs = galacticGlyphs(font);
		List<Entry> out = new ArrayList<>();
		for (Holder<Enchantment> enchantment : order) {
			int level = stored.getLevel(enchantment);
			if (level > 0) {
				out.add(entry(enchantment, level, registry, order, glyphs));
			}
		}
		for (var entry : stored.entrySet()) {
			if (!order.contains(entry.getKey())) {
				out.add(entry(entry.getKey(), entry.getIntValue(), registry, order, glyphs));
			}
		}
		return out;
	}

	private static Entry entry(Holder<Enchantment> enchantment, int level, @Nullable Registry<Enchantment> registry,
		HolderSet<Enchantment> order, IntPredicate glyphs) {
		String text = nameWithLevel(enchantment, level);
		boolean curse = enchantment.is(EnchantmentTags.CURSE);
		try {
			Enchantment value = enchantment.value();
			String name = value.description().getString();
			String id = enchantment.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
			List<String> others = new ArrayList<>();
			for (Holder<Enchantment> other : incompatible(enchantment, candidates(registry, order, value))) {
				others.add(other.value().description().getString());
			}
			ItemWords items = itemWords(enchantment);
			return new Entry(text, curse, galactic(name, id, glyphs), description(enchantment), levelName(value.getMaxLevel()),
				items.primary(), items.secondary(), join(others));
		} catch (RuntimeException e) {
			Scribe.LOGGER.warn("Could not read the details of the enchantment {}: {}", text, e.toString());
			return new Entry(text, curse);
		}
	}

	public static String nameWithLevel(Holder<Enchantment> enchantment, int level) {
		MutableComponent text = enchantment.value().description().copy();
		if (level != 1 || enchantment.value().getMaxLevel() != 1) {
			text.append(CommonComponents.SPACE).append(levelName(level));
		}
		return text.getString();
	}

	public static String levelName(int level) {
		return Component.translatableWithFallback("enchantment.level." + level, LookAtTooltip.roman(level)).getString();
	}

	public static String galactic(String name, String id, IntPredicate drawable) {
		String spelled = spell(name, drawable);
		if (spelled == null) {
			spelled = spell(id.replace('_', ' ').replace('/', ' ').replace('.', ' '), drawable);
		}
		return spelled == null ? "" : spelled;
	}

	private static @Nullable String spell(String text, IntPredicate drawable) {
		String plain = Normalizer.normalize(text, Normalizer.Form.NFD);
		StringBuilder out = new StringBuilder();
		boolean space = false;
		for (int i = 0; i < plain.length(); ) {
			int cp = plain.codePointAt(i);
			i += Character.charCount(cp);
			if (Character.getType(cp) == Character.NON_SPACING_MARK) {
				continue;
			}
			if (Character.isLetter(cp)) {
				if (!drawable.test(cp)) {
					return null;
				}
				if (space && !out.isEmpty()) {
					out.append(' ');
				}
				space = false;
				out.appendCodePoint(cp);
			} else if (Character.isWhitespace(cp) || cp == '-' || cp == '_') {
				space = true;
			}
		}
		return out.isEmpty() ? null : out.toString();
	}

	private static IntPredicate galacticGlyphs(Font font) {
		if (font instanceof FontAccessor accessor) {
			GlyphSource glyphs = accessor.scribe$glyphs(GALACTIC_FONT);
			return cp -> glyphs.getGlyph(cp).info() != SpecialGlyphs.MISSING;
		}
		return EnchantedPages::isBasicLetter;
	}

	public static boolean isBasicLetter(int cp) {
		return (cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z');
	}

	public static String description(Holder<Enchantment> enchantment) {
		Language language = Language.getInstance();
		for (String key : descriptionKeys(enchantment)) {
			if (language.has(key)) {
				return Component.translatable(key).getString();
			}
		}
		return "";
	}

	static List<String> descriptionKeys(Holder<Enchantment> enchantment) {
		List<String> keys = new ArrayList<>();
		if (enchantment.value().description().getContents() instanceof TranslatableContents name) {
			keys.add(name.getKey() + ".desc");
		}
		enchantment.unwrapKey().ifPresent(key -> {
			String byId = Util.makeDescriptionId("enchantment", key.identifier()) + ".desc";
			if (!keys.contains(byId)) {
				keys.add(byId);
			}
		});
		return keys;
	}

	public static ItemWords itemWords(Holder<Enchantment> enchantment) {
		Enchantment value = enchantment.value();
		try {
			boolean table = enchantment.is(EnchantmentTags.IN_ENCHANTING_TABLE);
			return itemWords(value.getSupportedItems(), value.definition().primaryItems(), item -> table && fromTable(enchantment, item),
				item -> withAnvil(enchantment, item), Tags.GAME);
		} catch (RuntimeException e) {
			Scribe.LOGGER.warn("Could not read the items of the enchantment {}: {}", value.description().getString(), e.toString());
			return ItemWords.NONE;
		}
	}

	private static boolean fromTable(Holder<Enchantment> enchantment, Holder<Item> item) {
		ItemStack stack = new ItemStack(item);
		if (!stack.isEnchantable()) {
			return false;
		}
		return (Object) stack instanceof FabricItemStack fabric ? fabric.canBeEnchantedWith(enchantment, EnchantingContext.PRIMARY)
			: enchantment.value().isPrimaryItem(stack);
	}

	private static boolean withAnvil(Holder<Enchantment> enchantment, Holder<Item> item) {
		ItemStack stack = new ItemStack(item);
		return (Object) stack instanceof FabricItemStack fabric ? fabric.canBeEnchantedWith(enchantment, EnchantingContext.ACCEPTABLE)
			: enchantment.value().canEnchant(stack);
	}

	public static ItemWords itemWords(HolderSet<Item> supported, Optional<HolderSet<Item>> primaryItems, Predicate<Holder<Item>> fromTable,
		Predicate<Holder<Item>> withAnvil, Tags tags) {
		List<Holder<Item>> all = contents(supported);
		if (all == null) {
			return ItemWords.NONE;
		}
		List<Holder<Item>> primary = new ArrayList<>();
		List<Holder<Item>> secondary = new ArrayList<>();
		for (Holder<Item> item : all) {
			if (fromTable.test(item)) {
				primary.add(item);
			} else if (withAnvil.test(item)) {
				secondary.add(item);
			}
		}
		List<HolderSet<Item>> own = new ArrayList<>(List.of(supported));
		primaryItems.ifPresent(own::add);
		return new ItemWords(itemWords(primary, own, tags), itemWords(secondary, own, tags));
	}

	public static String itemWords(List<Holder<Item>> items, List<HolderSet<Item>> own, Tags tags) {
		if (items.isEmpty()) {
			return "";
		}
		Map<Identifier, Holder<Item>> left = new LinkedHashMap<>();
		for (Holder<Item> item : items) {
			left.putIfAbsent(id(item), item);
		}
		Set<Identifier> all = Set.copyOf(left.keySet());
		for (HolderSet<Item> tag : own) {
			Optional<TagKey<Item>> key = tag.unwrapKey();
			List<Identifier> whole = key.isPresent() ? ids(tag) : null;
			if (whole != null && all.equals(Set.copyOf(whole))) {
				String words = tagWords(key.get());
				if (words != null) {
					return words;
				}
			}
		}
		List<String> parts = new ArrayList<>();
		for (String kind : KINDS) {
			Collection<Identifier> group = kindItems(kind, tags);
			String words = kindWords(kind);
			if (words != null && group != null && !group.isEmpty() && all.containsAll(group) && !Collections.disjoint(left.keySet(), group)) {
				parts.add(parts.isEmpty() ? words : lowerFirst(words));
				left.keySet().removeAll(group);
			}
		}
		List<String> names = new ArrayList<>();
		for (Map.Entry<Identifier, Holder<Item>> item : left.entrySet()) {
			String name = itemName(item.getKey(), item.getValue());
			if (!names.contains(name)) {
				names.add(name);
			}
		}
		int shown = Math.min(names.size(), MAX_ITEM_NAMES);
		List<String> words = new ArrayList<>(parts);
		words.addAll(names.subList(0, shown));
		if (names.size() > shown) {
			return Component.translatable("scribe.enchanted.list.more", String.join(", ", words), names.size() - shown).getString();
		}
		return join(words);
	}

	public static @Nullable Collection<Identifier> kindItems(String kind, Tags tags) {
		return kind.startsWith("#") ? tags.items(TagKey.create(Registries.ITEM, Identifier.parse(kind.substring(1)))) : List.of(Identifier.parse(kind));
	}

	public static @Nullable String kindWords(String kind) {
		Identifier id = Identifier.parse(kind.startsWith("#") ? kind.substring(1) : kind);
		String key = (kind.startsWith("#") ? APPLIES_KEY : ITEM_KEY) + id.getNamespace() + "." + id.getPath().replace('/', '.');
		return Language.getInstance().has(key) ? Component.translatable(key).getString() : null;
	}

	private static String lowerFirst(String words) {
		int first = words.codePointAt(0);
		return new StringBuilder().appendCodePoint(Character.toLowerCase(first)).append(words, Character.charCount(first), words.length()).toString();
	}

	private static String itemName(Identifier id, Holder<Item> item) {
		try {
			return new ItemStack(item).getHoverName().getString();
		} catch (RuntimeException noComponents) {
			return id.toString();
		}
	}

	private static Identifier id(Holder<Item> item) {
		return BuiltInRegistries.ITEM.getKey(item.value());
	}

	private static @Nullable List<Holder<Item>> contents(HolderSet<Item> items) {
		try {
			return items.stream().toList();
		} catch (IllegalStateException | UnsupportedOperationException unbound) {
			return null;
		}
	}

	public static @Nullable List<Identifier> ids(HolderSet<Item> items) {
		List<Holder<Item>> contents = contents(items);
		return contents == null ? null : contents.stream().map(EnchantedPages::id).toList();
	}

	static @Nullable String tagWords(TagKey<Item> tag) {
		Identifier id = tag.location();
		String path = id.getNamespace() + "." + id.getPath().replace('/', '.');
		Language language = Language.getInstance();
		for (String key : new String[] {APPLIES_KEY + path, "tag.item." + path}) {
			if (language.has(key)) {
				return Component.translatable(key).getString();
			}
		}
		return null;
	}

	private static List<Holder<Enchantment>> candidates(@Nullable Registry<Enchantment> registry, HolderSet<Enchantment> order, Enchantment enchantment) {
		List<Holder<Enchantment>> out = new ArrayList<>();
		try {
			if (registry == null) {
				enchantment.exclusiveSet().forEach(out::add);
			} else {
				order.forEach(out::add);
				registry.listElements().filter(holder -> !order.contains(holder)).forEach(out::add);
			}
		} catch (IllegalStateException | UnsupportedOperationException unbound) {
		}
		return out;
	}

	public static List<Holder<Enchantment>> incompatible(Holder<Enchantment> enchantment, Iterable<Holder<Enchantment>> candidates) {
		List<Holder<Enchantment>> out = new ArrayList<>();
		for (Holder<Enchantment> other : candidates) {
			if (!other.equals(enchantment) && !out.contains(other) && !Enchantment.areCompatible(enchantment, other)) {
				out.add(other);
			}
		}
		return out;
	}

	public static String join(List<String> names) {
		return join(names, Integer.MAX_VALUE);
	}

	public static String join(List<String> names, int max) {
		if (names.isEmpty()) {
			return "";
		}
		if (names.size() > max) {
			return Component.translatable("scribe.enchanted.list.more", String.join(", ", names.subList(0, max)), names.size() - max).getString();
		}
		if (names.size() == 1) {
			return names.getFirst();
		}
		return Component.translatable("scribe.enchanted.list.and", String.join(", ", names.subList(0, names.size() - 1)), names.getLast()).getString();
	}

	private static @Nullable Registry<Enchantment> registry() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft == null ? null : minecraft.level;
		return level == null ? null : level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}

	private static HolderSet<Enchantment> tooltipOrder(@Nullable Registry<Enchantment> registry) {
		if (registry == null) {
			return HolderSet.empty();
		}
		return registry.get(EnchantmentTags.TOOLTIP_ORDER).<HolderSet<Enchantment>>map(set -> set).orElse(HolderSet.empty());
	}

	public static Book layout(String title, List<Entry> entries, Font font) {
		int count = entries.size();
		if (count == 0) {
			Pager pager = new Pager(List.of());
			centeredRows(font, Component.literal(title).withStyle(TITLE)).forEach(pager::add);
			pager.gap();
			pager.keep(rows(font, Component.translatable("scribe.lectern.no_enchantments").withStyle(NONE)));
			return book(List.of(pages(pager)));
		}
		if (count == 1) {
			return book(List.of(detailsPages(entries.getFirst(), font)));
		}
		List<List<Component>> details = new ArrayList<>();
		for (Entry entry : entries) {
			details.add(detailsPages(entry, font));
		}
		int next = 0;
		for (List<Component> pages : listPages(title, entries, new int[count], font)) {
			next += pages.size();
		}
		int[] starts = new int[count];
		for (int i = 0; i < count; i++) {
			starts[i] = next;
			next += details.get(i).size();
		}
		List<List<Component>> shared = new ArrayList<>(listPages(title, entries, starts, font));
		shared.addAll(details);
		return book(shared);
	}

	private static Book book(List<List<Component>> shared) {
		int[] starts = new int[shared.size()];
		List<Component> pages = new ArrayList<>();
		for (int i = 0; i < shared.size(); i++) {
			starts[i] = pages.size();
			pages.addAll(shared.get(i));
		}
		return new Book(List.copyOf(pages), SharedPages.of(starts, pages.size()));
	}

	private static List<List<Component>> listPages(String title, List<Entry> entries, int[] starts, Font font) {
		int pages = EnchantedBooks.listPages(entries.size());
		List<List<Component>> out = new ArrayList<>(pages);
		for (int page = 0; page < pages; page++) {
			Pager pager = new Pager(List.of());
			if (page == 0) {
				centeredRows(font, Component.literal(title).withStyle(TITLE)).forEach(pager::add);
				pager.gap();
			}
			int first = EnchantedBooks.firstOnPage(page);
			int end = Math.min(entries.size(), first + EnchantedBooks.roomOnPage(page));
			for (int i = first; i < end; i++) {
				Entry entry = entries.get(i);
				Style style = (entry.curse() ? CURSE : Style.EMPTY).withClickEvent(new ClickEvent.ChangePage(starts[i] + 1));
				pager.keep(rows(font, Component.literal(entry.text()).withStyle(style)));
			}
			out.add(pages(pager));
		}
		return out;
	}

	private enum Kind { HEADING, GAP, DESCRIPTION, FACT }

	private record Part(Kind kind, Component text, boolean centered, int dropOrder) {
		static Part gap(int dropOrder) {
			return new Part(Kind.GAP, Component.empty(), false, dropOrder);
		}
	}

	private static List<Part> parts(Entry entry) {
		List<Part> parts = new ArrayList<>();
		boolean galactic = !entry.galactic().isEmpty();
		if (galactic) {
			parts.add(new Part(Kind.HEADING, Component.literal(entry.galactic()).withStyle(entry.curse() ? CURSE_GALACTIC_TITLE : GALACTIC_TITLE), true, 0));
		}
		Style name = galactic ? (entry.curse() ? CURSE : SOFT) : (entry.curse() ? CURSE.withBold(true) : TITLE);
		parts.add(new Part(Kind.HEADING, Component.literal(entry.text()).withStyle(name), true, 0));
		parts.add(Part.gap(2));
		if (!entry.description().isEmpty()) {
			parts.add(new Part(Kind.DESCRIPTION, Component.literal(entry.description()), false, 0));
			parts.add(Part.gap(1));
		}
		fact(parts, "scribe.enchanted.max_level", entry.maxLevel());
		if (!entry.primaryItems().isEmpty() || !entry.secondaryItems().isEmpty()) {
			fact(parts, "scribe.enchanted.primary_items", entry.primaryItems().isEmpty()
				? Component.translatable("scribe.enchanted.none").withStyle(NONE) : Component.literal(entry.primaryItems()).withStyle(INK));
			fact(parts, "scribe.enchanted.secondary_items", entry.secondaryItems());
		}
		fact(parts, "scribe.enchanted.exclusive", entry.exclusive());
		return parts;
	}

	private static void fact(List<Part> parts, String key, String value) {
		if (!value.isEmpty()) {
			fact(parts, key, Component.literal(value).withStyle(INK));
		}
	}

	private static void fact(List<Part> parts, String key, Component value) {
		parts.add(new Part(Kind.FACT, Component.translatable(key, value).withStyle(SOFT), false, 0));
	}

	private static List<Component> detailsPages(Entry entry, Font font) {
		List<Part> parts = parts(entry);
		int[] lines = new int[parts.size()];
		for (int i = 0; i < parts.size(); i++) {
			lines[i] = parts.get(i).kind() == Kind.GAP ? 1 : lineCount(font, parts.get(i).text());
		}
		int[] spaced = lines.clone();
		dropGaps(parts, lines);
		if (sum(lines) > BookLimits.PAGE_LINES) {
			lines = spaced;
		}
		List<FormattedText> name = centeredRows(font, Component.literal(entry.text()).withStyle(entry.curse() ? CURSE : SOFT));
		List<FormattedText> header = new ArrayList<>();
		if (name.size() <= HEADER_LINES) {
			header.addAll(name);
			header.add(FormattedText.EMPTY);
		}
		Pager pager = new Pager(header);
		for (int i = 0; i < parts.size(); i++) {
			Part part = parts.get(i);
			if (lines[i] <= 0) {
				continue;
			}
			if (part.kind() == Kind.GAP) {
				pager.gap();
				continue;
			}
			List<FormattedText> rows = part.centered() ? centeredRows(font, part.text()) : rows(font, part.text());
			if (part.kind() == Kind.DESCRIPTION) {
				rows.forEach(pager::add);
			} else {
				pager.keep(rows);
			}
		}
		return pages(pager);
	}

	private static List<Component> pages(Pager pager) {
		List<Component> out = new ArrayList<>();
		for (List<FormattedText> rows : pager.finish()) {
			out.add(page(rows));
		}
		return out;
	}

	private static final class Pager {
		private final List<FormattedText> header;
		private final List<List<FormattedText>> pages = new ArrayList<>();
		private List<FormattedText> page = new ArrayList<>();

		Pager(List<FormattedText> header) {
			this.header = header;
		}

		int room() {
			return BookLimits.PAGE_LINES - this.page.size();
		}

		int roomOnNextPage() {
			return BookLimits.PAGE_LINES - this.header.size();
		}

		boolean fresh() {
			return this.page.size() == (this.pages.isEmpty() ? 0 : this.header.size());
		}

		void newPage() {
			this.pages.add(this.page);
			this.page = new ArrayList<>(this.header);
		}

		void add(FormattedText row) {
			if (this.room() <= 0) {
				this.newPage();
			}
			this.page.add(row);
		}

		void keep(List<FormattedText> rows) {
			if (rows.size() > this.room() && rows.size() <= this.roomOnNextPage() && !this.fresh()) {
				this.newPage();
			}
			rows.forEach(this::add);
		}

		void gap() {
			if (!this.fresh() && this.room() > 0) {
				this.page.add(FormattedText.EMPTY);
			}
		}

		List<List<FormattedText>> finish() {
			this.pages.add(this.page);
			for (List<FormattedText> rows : this.pages) {
				while (!rows.isEmpty() && rows.getLast() == FormattedText.EMPTY) {
					rows.removeLast();
				}
			}
			return this.pages;
		}
	}

	private static void dropGaps(List<Part> parts, int[] lines) {
		int over = sum(lines) - BookLimits.PAGE_LINES;
		for (int turn = 1; turn <= 2 && over > 0; turn++) {
			for (int i = 0; i < parts.size() && over > 0; i++) {
				if (parts.get(i).kind() == Kind.GAP && parts.get(i).dropOrder() == turn && lines[i] > 0) {
					lines[i] = 0;
					over--;
				}
			}
		}
	}

	private static int sum(int[] lines) {
		int total = 0;
		for (int count : lines) {
			total += count;
		}
		return total;
	}

	private static int lineCount(Font font, FormattedText text) {
		return Math.max(1, font.getSplitter().splitLines(text, BookLimits.PAGE_WIDTH, Style.EMPTY).size());
	}

	private static List<FormattedText> rows(Font font, FormattedText text) {
		List<FormattedText> all = font.getSplitter().splitLines(text, BookLimits.PAGE_WIDTH, Style.EMPTY);
		return all.isEmpty() ? new ArrayList<>(List.of(FormattedText.EMPTY)) : new ArrayList<>(all);
	}

	private static List<FormattedText> centeredRows(Font font, FormattedText text) {
		List<FormattedText> rows = rows(font, text);
		rows.replaceAll(row -> centered(font, row));
		return rows;
	}

	private static Component page(List<FormattedText> rows) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < rows.size(); i++) {
			if (i > 0) {
				out.append("\n");
			}
			rows.get(i).visit((style, text) -> {
				out.append(Component.literal(text).withStyle(style));
				return Optional.empty();
			}, Style.EMPTY);
		}
		return out;
	}

	private static FormattedText centered(Font font, FormattedText row) {
		int spaces = centerSpaces(font, font.width(row));
		return spaces == 0 ? row : FormattedText.composite(FormattedText.of(" ".repeat(spaces)), row);
	}

	private static int centerSpaces(Font font, int width) {
		int space = Math.max(1, font.width(" "));
		int spaces = Math.max(0, Math.round((BookLimits.PAGE_WIDTH - width) / 2.0F / space));
		while (spaces > 0 && font.width(" ".repeat(spaces)) + width > BookLimits.PAGE_WIDTH) {
			spaces--;
		}
		return spaces;
	}
}
