package scribe.client;

import scribe.EnchantedBooks;
import scribe.RealAuthor;
import scribe.Scribe;
import scribe.access.BookPreviews;
import scribe.config.ScribeConfig;
import scribe.config.ScribeConfig.LevelNumerals;
import scribe.server.ServerBooks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.ShelfBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.ShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import scribe.client.compat.OtherMods;
import scribe.client.ink.InkPotsClient;
import scribe.client.placed.PlacedBooksClient;
import org.jspecify.annotations.Nullable;

public final class LookAtTooltip {
	private static final int MAX_LINES = 6;
	private static final int FRAME = 4;
	private static final int EDGE = 2;
	private static final int MAX_WIDTH = 200;
	private static final int MIN_WIDTH = 80;
	private static final int ROW_HEIGHT = 10;
	private static final int HUD_GAP = 1;
	private static final int BELOW_CROSSHAIR = 13;
	private static final String LEVEL_KEY = "enchantment.level.";
	private static final String CUT = "...";

	private LookAtTooltip() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Scribe.id("look_at_book"), (graphics, delta) -> render(graphics));
	}

	private static void render(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		ScribeConfig config = ScribeConfig.get();
		if (!config.lookAtShown() || OtherMods.lookAtBy() != null || minecraft.level == null || minecraft.player == null || minecraft.gameMode == null) {
			return;
		}
		Lines lines;
		if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			lines = lines(minecraft, hit, config);
		} else if (minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Interaction box) {
			ItemStack pot = config.lookAtPlacedBooks ? InkPotsClient.potOf(minecraft.level, box) : ItemStack.EMPTY;
			lines = !pot.isEmpty() ? inkLines(pot) : config.lookAtPlacedBooks ? placedLines(PlacedBooksClient.aimedBook(minecraft.level, box, hit.getLocation()),
				Item.TooltipContext.of(minecraft.level), minecraft.player) : Lines.NONE;
		} else {
			return;
		}
		List<Component> shown = shown(lines, minecraft.player.isShiftKeyDown());
		if (!shown.isEmpty()) {
			draw(graphics, minecraft.font, withLevels(shown, config.lookAtLevels, Language.getInstance()::has), config.lookAtSize.scale,
				BottomHud.height(BottomHud.read(minecraft)));
		}
	}

	public record Lines(List<Component> all, List<Component> compact) {
		public static final Lines NONE = new Lines(List.of(), List.of());
	}

	public static List<Component> shown(Lines lines, boolean sneaking) {
		return sneaking ? lines.all() : lines.compact();
	}

	private static Lines lines(Minecraft minecraft, BlockHitResult hit, ScribeConfig config) {
		BlockPos pos = hit.getBlockPos();
		BlockEntity entity = minecraft.level.getBlockEntity(pos);
		BlockState state = minecraft.level.getBlockState(pos);
		Item.TooltipContext context = Item.TooltipContext.of(minecraft.level);
		if (entity instanceof LecternBlockEntity && entity instanceof BookPreviews previews) {
			if (!config.lookAtLecterns) {
				return Lines.NONE;
			}
			return lecternLines(previews.scribe$preview(0), previews.scribe$page(), previews.scribe$pageCount(), context, minecraft.player);
		}
		if (entity instanceof ChiseledBookShelfBlockEntity && entity instanceof BookPreviews previews) {
			if (!config.lookAtChiseledBookshelves || OtherMods.shelvesBy() != null || !(state.getBlock() instanceof ChiseledBookShelfBlock shelf)
				|| !state.hasProperty(HorizontalDirectionalBlock.FACING)) {
				return Lines.NONE;
			}
			OptionalInt slot = shelf.getHitSlot(hit, state.getValue(HorizontalDirectionalBlock.FACING));
			if (slot.isEmpty()) {
				return Lines.NONE;
			}
			ItemStack book = previews.scribe$preview(slot.getAsInt());
			List<Component> all = bookLines(book, null, context, minecraft.player);
			return new Lines(all, compactLines(book, all, -1, 0, context));
		}
		if (entity instanceof ShelfBlockEntity shelf) {
			if (!config.lookAtShelves) {
				return Lines.NONE;
			}
			OptionalInt slot = shelfSlot(state, hit);
			if (slot.isEmpty()) {
				return Lines.NONE;
			}
			ItemStack item = shelf.getItem(slot.getAsInt());
			List<Component> all = shelfLines(item, context, minecraft.player);
			return new Lines(all, compactLines(item, all, -1, 0, context));
		}
		return Lines.NONE;
	}

	public static Lines placedLines(ItemStack book, Item.TooltipContext context, @Nullable Player player) {
		List<Component> all = bookLines(book, null, context, player);
		return new Lines(all, compactLines(book, all, -1, 0, context));
	}

	public static Lines inkLines(ItemStack pot) {
		List<Component> all = InkPotsClient.lookLines(pot);
		return new Lines(all, all);
	}

	public static Lines lecternLines(ItemStack book, int page, int pages, Item.TooltipContext context, @Nullable Player player) {
		boolean paged = !EnchantedBooks.is(book) && pages > 0 && page >= 0;
		List<Component> all = bookLines(book, paged ? pageLine(page, pages) : null, context, player);
		return new Lines(all, compactLines(book, all, paged ? page : -1, pages, context));
	}

	public static Component pageLine(int page, int pages) {
		return Component.translatable("scribe.look.page", page + 1, pages).withStyle(ChatFormatting.GRAY);
	}

	private static List<Component> bookLines(ItemStack book, @Nullable Component extra, Item.TooltipContext context, @Nullable Player player) {
		if (book.isEmpty()) {
			return List.of();
		}
		List<Component> lines = new ArrayList<>(book.getTooltipLines(context, player, TooltipFlag.NORMAL));
		if (lines.isEmpty()) {
			return List.of();
		}
		cutShort(lines);
		if (extra != null) {
			lines.add(extra);
		}
		return lines;
	}

	public static List<Component> compactLines(ItemStack item, List<Component> all, int page, int pages, Item.TooltipContext context) {
		if (all.isEmpty()) {
			return List.of();
		}
		Component second = secondLine(item, page, pages, context);
		return second == null ? List.of(all.getFirst()) : List.of(all.getFirst(), second);
	}

	public static @Nullable Component secondLine(ItemStack item, int page, int pages, Item.TooltipContext context) {
		if (item.isEmpty()) {
			return null;
		}
		TooltipDisplay display = item.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
		boolean paged = page >= 0 && pages > 0;
		WrittenBookContent written = item.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (written != null) {
			if (display.shows(DataComponents.WRITTEN_BOOK_CONTENT) && !StringUtil.isBlank(written.author())) {
				Object author = AuthorNames.name(written.author(), RealAuthor.isPenName(item));
				return (paged ? Component.translatable("scribe.look.author_page", author, page + 1, pages)
					: Component.translatable("book.byAuthor", author)).withStyle(ChatFormatting.GRAY);
			}
			return paged ? pageLine(page, pages) : null;
		}
		if (paged) {
			return pageLine(page, pages);
		}
		List<Component> lines = new ArrayList<>();
		item.addToTooltip(DataComponents.STORED_ENCHANTMENTS, context, display, lines::add, TooltipFlag.NORMAL);
		item.addToTooltip(DataComponents.ENCHANTMENTS, context, display, lines::add, TooltipFlag.NORMAL);
		return enchantmentLine(lines, shownEnchantments(item, display, context));
	}

	public static @Nullable Component enchantmentLine(List<Component> lines, int enchantments) {
		if (lines.isEmpty()) {
			return null;
		}
		if (enchantments <= 1) {
			return lines.getFirst();
		}
		return Component.empty().append(lines.getFirst()).append(" ")
			.append(Component.translatable("scribe.look.more", enchantments - 1).withStyle(ChatFormatting.GRAY));
	}

	public static int shownEnchantments(ItemStack item, TooltipDisplay display, Item.TooltipContext context) {
		HolderSet<Enchantment> order = tooltipOrder(context.registries());
		int count = 0;
		for (DataComponentType<ItemEnchantments> type : List.of(DataComponents.STORED_ENCHANTMENTS, DataComponents.ENCHANTMENTS)) {
			ItemEnchantments enchantments = item.get(type);
			if (enchantments != null && display.shows(type)) {
				count += named(enchantments, order);
			}
		}
		return count;
	}

	public static int named(ItemEnchantments enchantments, HolderSet<Enchantment> order) {
		int count = 0;
		for (Holder<Enchantment> enchantment : order) {
			if (enchantments.getLevel(enchantment) > 0) {
				count++;
			}
		}
		for (Holder<Enchantment> enchantment : enchantments.keySet()) {
			if (!order.contains(enchantment)) {
				count++;
			}
		}
		return count;
	}

	private static HolderSet<Enchantment> tooltipOrder(HolderLookup.@Nullable Provider registries) {
		if (registries != null) {
			Optional<HolderSet.Named<Enchantment>> order = registries.lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.TOOLTIP_ORDER);
			if (order.isPresent()) {
				return order.get();
			}
		}
		return HolderSet.empty();
	}

	public static OptionalInt shelfSlot(BlockState state, BlockHitResult hit) {
		if (state.getBlock() instanceof ShelfBlock shelf && state.hasProperty(ShelfBlock.FACING)) {
			return shelf.getHitSlot(hit, state.getValue(ShelfBlock.FACING));
		}
		return OptionalInt.empty();
	}

	public static List<Component> shelfLines(ItemStack item, Item.TooltipContext context, @Nullable Player player) {
		if (item.isEmpty()) {
			return List.of();
		}
		List<Component> lines;
		if (BookInfoTooltip.isBook(item)) {
			lines = new ArrayList<>(item.getTooltipLines(context, player, TooltipFlag.NORMAL));
		} else {
			TooltipDisplay display = item.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
			if (display.hideTooltip()) {
				return List.of();
			}
			lines = new ArrayList<>();
			lines.add(item.getStyledHoverName());
			item.addToTooltip(DataComponents.STORED_ENCHANTMENTS, context, display, lines::add, TooltipFlag.NORMAL);
			item.addToTooltip(DataComponents.ENCHANTMENTS, context, display, lines::add, TooltipFlag.NORMAL);
		}
		if (lines.isEmpty()) {
			return List.of();
		}
		if (item.getCount() > 1) {
			lines.set(0, withCount(lines.getFirst(), item.getCount()));
		}
		cutShort(lines);
		return lines;
	}

	public static Component withCount(Component name, int count) {
		return Component.translatable("scribe.look.count", name, count).withStyle(ChatFormatting.GRAY);
	}

	public static void cutShort(List<Component> lines) {
		if (lines.size() > MAX_LINES) {
			int kept = keptLines(blockSizes(lines), MAX_LINES - 1);
			int hidden = lines.size() - kept;
			lines.subList(kept, lines.size()).clear();
			lines.add(Component.translatable("scribe.look.more", hidden).withStyle(ChatFormatting.GRAY));
		}
	}

	public static List<Integer> blockSizes(List<Component> lines) {
		List<Integer> sizes = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0 && continuesBlock(lines.get(i - 1), lines.get(i))) {
				sizes.set(sizes.size() - 1, sizes.getLast() + 1);
			} else {
				sizes.add(1);
			}
		}
		return sizes;
	}

	private static boolean continuesBlock(Component previous, Component line) {
		if (line.getString().startsWith(" ")) {
			return true;
		}
		return ServerBooks.SUBTITLE_MARK.equals(previous.getStyle().getInsertion()) && ServerBooks.SUBTITLE_MARK.equals(line.getStyle().getInsertion());
	}

	public static int keptLines(List<Integer> blocks, int room) {
		int kept = 0;
		for (int i = 0; i < blocks.size(); i++) {
			int size = blocks.get(i);
			if (kept + size > room) {
				return i == 0 ? Math.max(0, Math.min(size, room)) : kept;
			}
			kept += size;
		}
		return kept;
	}

	public static <R> List<R> fitRows(List<List<R>> blocks, int fit, R cut) {
		List<R> all = new ArrayList<>();
		blocks.forEach(all::addAll);
		if (all.size() <= fit) {
			return all;
		}
		List<R> out = new ArrayList<>(all.subList(0, keptLines(blocks.stream().map(List::size).toList(), fit - 1)));
		out.add(cut);
		return out;
	}

	public static List<Component> withLevels(List<Component> lines, LevelNumerals numerals, Predicate<String> translated) {
		List<Component> out = new ArrayList<>(lines.size());
		for (Component line : lines) {
			out.add(withLevels(line, numerals, translated));
		}
		return out;
	}

	public static Component withLevels(Component line, LevelNumerals numerals, Predicate<String> translated) {
		if (!hasLevelToWrite(line, numerals, translated)) {
			return line;
		}
		String level = levelText(line.getContents(), numerals, translated);
		ComponentContents contents = level == null ? line.getContents() : PlainTextContents.create(level);
		MutableComponent copy = MutableComponent.create(contents).setStyle(line.getStyle());
		for (Component sibling : line.getSiblings()) {
			copy.append(withLevels(sibling, numerals, translated));
		}
		return copy;
	}

	private static boolean hasLevelToWrite(Component line, LevelNumerals numerals, Predicate<String> translated) {
		if (levelText(line.getContents(), numerals, translated) != null) {
			return true;
		}
		for (Component sibling : line.getSiblings()) {
			if (hasLevelToWrite(sibling, numerals, translated)) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable String levelText(ComponentContents contents, LevelNumerals numerals, Predicate<String> translated) {
		if (!(contents instanceof TranslatableContents translatable) || !translatable.getKey().startsWith(LEVEL_KEY)) {
			return null;
		}
		int level;
		try {
			level = Integer.parseInt(translatable.getKey().substring(LEVEL_KEY.length()));
		} catch (NumberFormatException notALevel) {
			return null;
		}
		if (numerals == LevelNumerals.NUMBERS) {
			return Integer.toString(level);
		}
		return translated.test(translatable.getKey()) ? null : roman(level);
	}

	public static String roman(int number) {
		if (number < 1 || number > 3999) {
			return Integer.toString(number);
		}
		int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
		String[] letters = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
		StringBuilder out = new StringBuilder();
		int left = number;
		for (int i = 0; i < values.length; i++) {
			while (left >= values[i]) {
				out.append(letters[i]);
				left -= values[i];
			}
		}
		return out.toString();
	}

	private static void draw(GuiGraphicsExtractor graphics, Font font, List<Component> lines, float scale, int hud) {
		Box box = place(font, lines, graphics.guiWidth(), graphics.guiHeight(), scale, hud);
		if (box == null) {
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		TooltipRenderUtil.extractTooltipBackground(graphics, box.x(), box.y(), box.w(), box.h(), null);
		for (int i = 0; i < box.rows().size(); i++) {
			graphics.text(font, box.rows().get(i), box.x(), box.y() + i * ROW_HEIGHT, 0xFFFFFFFF, true);
		}
		graphics.pose().popMatrix();
	}

	public record Box(List<FormattedCharSequence> rows, int x, int y, int w, int h) {
	}

	public static @Nullable Box place(Font font, List<Component> lines, int screenWidth, int screenHeight, float scale, int hud) {
		if (lines.isEmpty()) {
			return null;
		}
		int wrap = wrapWidth(screenWidth, scale);
		List<List<FormattedCharSequence>> blocks = new ArrayList<>();
		int at = 0;
		for (int size : blockSizes(lines)) {
			List<FormattedCharSequence> block = new ArrayList<>();
			for (Component line : lines.subList(at, at + size)) {
				block.addAll(font.split(line, wrap));
			}
			blocks.add(block);
			at += size;
		}
		int bottom = bottom(screenHeight, hud);
		int fit = maxRows(screenHeight, bottom, scale);
		if (fit < 1) {
			return null;
		}
		List<FormattedCharSequence> rows = fitRows(blocks, fit, Component.literal(CUT).withStyle(ChatFormatting.GRAY).getVisualOrderText());
		int w = 0;
		for (FormattedCharSequence row : rows) {
			w = Math.max(w, font.width(row));
		}
		int h = height(rows.size());
		return new Box(rows, x(screenWidth, w, scale), y(bottom, h, scale), w, h);
	}

	public static int bottom(int screenHeight, int hud) {
		return screenHeight - Math.max(hud + HUD_GAP, EDGE);
	}

	public static int topLimit(int screenHeight) {
		return screenHeight / 2 + BELOW_CROSSHAIR + HUD_GAP;
	}

	public static int wrapWidth(int screenWidth, float scale) {
		return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, Mth.floor((screenWidth - 2 * EDGE) / scale) - 2 * FRAME));
	}

	public static int x(int screenWidth, int w, float scale) {
		int x = Mth.floor((screenWidth / scale - w) / 2);
		int min = Mth.ceil(EDGE / scale) + FRAME;
		int max = Mth.floor((screenWidth - EDGE) / scale) - FRAME - w;
		return Math.max(min, Math.min(x, max));
	}

	public static int y(int bottom, int h, float scale) {
		return Mth.floor(bottom / scale) - FRAME - h;
	}

	public static int height(int rows) {
		return rows * ROW_HEIGHT - 2;
	}

	public static int maxRows(int screenHeight, int bottom, float scale) {
		int room = Mth.floor(bottom / scale) - Mth.ceil(topLimit(screenHeight) / scale) - 2 * FRAME;
		return Math.max(0, (room + 2) / ROW_HEIGHT);
	}

	public static int frame() {
		return FRAME;
	}
}
