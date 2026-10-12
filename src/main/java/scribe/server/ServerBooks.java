package scribe.server;

import scribe.BookLimits;
import scribe.Bookmarks;
import scribe.ClosedLecterns;
import scribe.ContentsLinks;
import scribe.EnchantedBooks;
import scribe.LecternLocks;
import scribe.RealAuthor;
import scribe.SavedDetails;
import scribe.Scribe;
import scribe.access.LecternAccess;
import scribe.access.LecternMenuAccess;
import scribe.binding.Binding;
import scribe.binding.Bookbinding;
import scribe.binding.Clasp;
import scribe.binding.CoverDesign;
import scribe.binding.CoverEdit;
import scribe.binding.CoverLines;
import scribe.config.ScribeConfig;
import scribe.network.Payloads;
import scribe.network.Payloads.SignDetails;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.network.Filterable;
import net.minecraft.server.network.TextFilter;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.jspecify.annotations.Nullable;

public final class ServerBooks {
	public static final String SUBTITLE_MARK = Scribe.MOD_ID + ":subtitle";
	public static final Style SUBTITLE_STYLE = Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(true).withInsertion(SUBTITLE_MARK);
	private static final long PENDING_MS = 30_000;

	private record Pending(int slot, SignDetails details, long time) {
	}

	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> READING = new ConcurrentHashMap<>();

	private ServerBooks() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(Payloads.SetBookmark.TYPE, (payload, context) -> storeBookmark(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.SignDetailsPayload.TYPE, (payload, context) -> rememberSignDetails(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.LecternEdit.TYPE, (payload, context) -> editOnLectern(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.LecternPen.TYPE, (payload, context) -> lecternPen(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Reading.TYPE, (payload, context) -> setReading(context.player(), payload.reading(), payload.offhand()));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.PageTurn.TYPE, (payload, context) -> turnPages(context.player(), payload.leaves()));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.LecternLeaves.TYPE, (payload, context) -> lecternLeaves(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.CoverEditPayload.TYPE, (payload, context) -> editCover(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.SaveDesign.TYPE, (payload, context) -> saveDesign(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.SaveDetails.TYPE, (payload, context) -> saveDetails(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.CoverDesignerSave.TYPE, (payload, context) -> saveCoverDesigner(context.player(), payload));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ServerPlayNetworking.canSend(handler.player, Payloads.ServerFeatures.TYPE)) {
				ServerPlayNetworking.send(handler.player, new Payloads.ServerFeatures(features()));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			PENDING.remove(handler.player.getUUID());
			READING.remove(handler.player.getUUID());
		});
		EntityTrackingEvents.START_TRACKING.register((entity, viewer) -> {
			Boolean offhand = READING.get(entity.getUUID());
			if (offhand != null && entity instanceof ServerPlayer && ScribeConfig.get().sharedReadingPoseOn()
				&& ServerPlayNetworking.canSend(viewer, Payloads.ReadingState.TYPE)) {
				ServerPlayNetworking.send(viewer, new Payloads.ReadingState(entity.getId(), true, offhand));
			}
		});
	}

	public static int features() {
		ScribeConfig config = ScribeConfig.get();
		int flags = 0;
		if (config.allowPenNames) flags |= Payloads.ServerFeatures.PEN_NAMES;
		if (config.allowSubtitles) flags |= Payloads.ServerFeatures.SUBTITLES;
		if (config.lecternEditingOn()) flags |= Payloads.ServerFeatures.LECTERN_EDITING;
		if (config.sharedReadingPoseOn()) flags |= Payloads.ServerFeatures.READING_POSE;
		if (config.bookInfoOn()) flags |= Payloads.ServerFeatures.BOOK_INFO;
		if (config.lecternPagesOn()) flags |= Payloads.ServerFeatures.LECTERN_PAGES;
		if (config.enchantedLecternsOn()) flags |= Payloads.ServerFeatures.ENCHANTED_LECTERNS;
		if (config.bookbindingOn()) flags |= Payloads.ServerFeatures.BOOKBINDING;
		if (config.closedLecternsOn()) flags |= Payloads.ServerFeatures.CLOSED_LECTERNS;
		if (config.signFormatting) flags |= Payloads.ServerFeatures.SIGN_FORMATTING;
		if (config.placeableBooksOn()) flags |= Payloads.ServerFeatures.PLACED_BOOKS;
		if (config.linkContents) flags |= Payloads.ServerFeatures.CONTENTS_LINKS;
		if (config.bookDyeingOn()) flags |= Payloads.ServerFeatures.BOOK_DYEING;
		if (config.tableDyeingOn()) flags |= Payloads.ServerFeatures.TABLE_DYEING;
		if (config.readingLibrariansOn()) flags |= Payloads.ServerFeatures.READING_LIBRARIANS;
		return flags;
	}

	private static void storeBookmark(ServerPlayer player, Payloads.SetBookmark payload) {
		if (!ScribeConfig.get().lecternPagesOn()) {
			return;
		}
		int slot = payload.slot();
		if (!Inventory.isHotbarSlot(slot) && slot != BookLimits.OFFHAND_SLOT) {
			return;
		}
		ItemStack stack = player.getInventory().getItem(slot);
		if (Bookmarks.isBook(stack)) {
			Bookmarks.set(stack, payload.page());
		}
	}

	private static void rememberSignDetails(ServerPlayer player, Payloads.SignDetailsPayload payload) {
		UUID id = player.getUUID();
		filter(player, payload.details(), clean -> PENDING.put(id, new Pending(payload.slot(), clean, Util.getMillis())));
	}

	private static void filter(ServerPlayer player, SignDetails details, java.util.function.Consumer<SignDetails> then) {
		TextFilter filter = player.getTextFilter();
		if (filter == TextFilter.DUMMY || (!details.hasPenName() && !details.hasSubtitle())) {
			then.accept(details);
			return;
		}
		List<String> texts = new ArrayList<>(details.subtitle());
		texts.add(details.penName());
		filter.processMessageBundle(texts).thenAccept(results -> {
			boolean penOk = !results.getLast().isFiltered();
			boolean subtitleOk = results.subList(0, results.size() - 1).stream().noneMatch(FilteredText::isFiltered);
			then.accept(details.withText(penOk ? details.penName() : "", subtitleOk ? details.subtitle() : List.of()));
		});
	}

	public static SignDetails takeSignDetails(ServerPlayer player, int slot) {
		Pending pending = PENDING.remove(player.getUUID());
		if (pending == null || pending.slot() != slot || Util.getMillis() - pending.time() > PENDING_MS) {
			return SignDetails.NONE;
		}
		return pending.details();
	}

	public static ItemStack applySignDetails(ItemStack written, SignDetails details) {
		SavedDetails.remove(written);
		ScribeConfig config = ScribeConfig.get();
		WrittenBookContent content = written.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (content != null && details.hasPenName() && config.allowPenNames) {
			written.set(DataComponents.WRITTEN_BOOK_CONTENT,
				new WrittenBookContent(content.title(), details.penName(), content.generation(), content.pages(), content.resolved()));
		}
		if (details.hasSubtitle() && config.allowSubtitles) {
			addSubtitle(written, details.subtitle());
		}
		applyDesign(written, details);
		return written;
	}

	public static ItemStack linkContents(ItemStack written) {
		WrittenBookContent content = written.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (content != null && ScribeConfig.get().linkContents) {
			WrittenBookContent linked = ContentsLinks.link(content);
			if (linked != content) {
				written.set(DataComponents.WRITTEN_BOOK_CONTENT, linked);
			}
		}
		return written;
	}

	public static void addSubtitle(ItemStack written, List<String> subtitle) {
		List<Component> lines = new ArrayList<>();
		for (String line : subtitle) {
			lines.add(Component.literal(line).withStyle(SUBTITLE_STYLE));
		}
		ItemLore old = written.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		for (Component line : old.lines()) {
			if (lines.size() >= ItemLore.MAX_LINES) {
				break;
			}
			lines.add(line);
		}
		written.set(DataComponents.LORE, new ItemLore(lines));
	}

	static void applyDesign(ItemStack written, SignDetails details) {
		if (!details.hasDesign() || !ScribeConfig.get().bookbindingOn()) {
			return;
		}
		CoverDesign before = CoverDesign.of(written);
		CoverDesign design = signedDesign(written, details);
		if (!design.equals(before)) {
			design.applyTo(written);
		}
		ChatFormatting color = CoverDesign.isTitleColor(details.titleColor()) ? design.titleFormatting() : null;
		if (color != null) {
			nameInColor(written, color);
		}
	}

	public static CoverDesign signedDesign(ItemStack written, SignDetails details) {
		CoverDesign design = CoverDesign.of(written);
		if (details.hasCorner() && Bookbinding.materialName(Binding.of(written)) != null && CoverDesign.isCornerChoice(details.corner())) {
			design = design.withCorner(details.corner());
		}
		if (CoverDesign.NO_DECORATION.equals(details.decoration())) {
			design = design.withDecoration(null);
		} else if (CoverDesign.isDecoration(details.decoration())) {
			design = design.withDecoration(details.decoration(),
				CoverDesign.isDecorationLook(details.decorationLook()) ? details.decorationLook() : null);
		}
		if (CoverDesign.isSpineChoice(details.spine())) {
			design = design.withSpine(details.spine());
		}
		if (CoverDesign.NO_TITLE_COLOR.equals(details.titleColor())) {
			design = design.withTitleColor(null);
		} else if (CoverDesign.isTitleColor(details.titleColor())) {
			design = design.withTitleColor(details.titleColor());
		}
		if (CoverDesign.AUTHOR_SHOWN.equals(details.author())) {
			design = design.withAuthor(null);
		} else if (CoverDesign.AUTHOR_HIDDEN.equals(details.author())) {
			design = design.withAuthor(CoverDesign.AUTHOR_HIDDEN);
		}
		if (CoverDesign.METALWORK_AUTO.equals(details.metalwork())) {
			design = design.withMetalwork(null);
		} else if (CoverDesign.isMetalwork(details.metalwork()) && Clasp.metal(Binding.of(written)) != null) {
			design = design.withMetalwork(details.metalwork());
		}
		return design;
	}

	public static void nameInColor(ItemStack written, ChatFormatting color) {
		WrittenBookContent content = written.get(DataComponents.WRITTEN_BOOK_CONTENT);
		String title = content == null ? null : content.title().raw();
		if (title == null || StringUtil.isBlank(title)) {
			return;
		}
		written.set(DataComponents.CUSTOM_NAME, titleInColor(title, nameColor(color)));
	}

	private static Component titleInColor(String title, ChatFormatting color) {
		return Component.literal(title).withStyle(style -> style.withColor(color).withItalic(false));
	}

	public static ChatFormatting nameColor(ChatFormatting titleColor) {
		return switch (titleColor) {
			case BLACK -> ChatFormatting.DARK_GRAY;
			case DARK_BLUE -> ChatFormatting.BLUE;
			default -> titleColor;
		};
	}

	public static boolean hasNameInColor(ItemStack book) {
		return nameColorOf(book) != null;
	}

	public static @Nullable ChatFormatting nameColorOf(ItemStack book) {
		Component name = book.get(DataComponents.CUSTOM_NAME);
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (name == null || content == null) {
			return null;
		}
		String title = content.title().raw();
		for (String color : CoverDesign.TITLE_COLORS) {
			ChatFormatting format = ChatFormatting.valueOf(color.toUpperCase(Locale.ROOT));
			if (name.equals(titleInColor(title, format))) {
				return format;
			}
		}
		return null;
	}

	public enum CoverResult {
		CHANGED,
		UNCHANGED,
		NOT_A_BOOK,
		NEEDS_TRIM,
		NEEDS_BUCKLE,
		NOT_A_CHOICE,
		OFF,
		NOT_AUTHOR
	}

	private static void editCover(ServerPlayer player, Payloads.CoverEditPayload payload) {
		CoverResult result = changeCoverInHand(player, payload.slot(), payload.edit());
		player.sendSystemMessage(coverMessage(result, player.getMainHandItem(), payload.edit()));
	}

	public static CoverResult changeCoverInHand(ServerPlayer player, int slot, CoverEdit edit) {
		if (!ScribeConfig.get().bookbindingOn()) {
			return CoverResult.OFF;
		}
		if (slot != player.getInventory().getSelectedSlot()) {
			return CoverResult.NOT_A_BOOK;
		}
		ItemStack book = player.getMainHandItem();
		if (book.has(DataComponents.WRITTEN_BOOK_CONTENT) && !RealAuthor.isAuthor(book, player)) {
			return CoverResult.NOT_AUTHOR;
		}
		return changeCover(book, edit);
	}

	public static CoverResult changeCover(ItemStack book, CoverEdit edit) {
		if (!CoverEdit.isBook(book)) {
			return CoverResult.NOT_A_BOOK;
		}
		if (!edit.isValid()) {
			return CoverResult.NOT_A_CHOICE;
		}
		String material = Bookbinding.materialName(Binding.of(book));
		if (edit.needsTrim() && material == null) {
			return CoverResult.NEEDS_TRIM;
		}
		if (edit.needsBuckle() && Clasp.metal(Binding.of(book)) == null) {
			return CoverResult.NEEDS_BUCKLE;
		}
		CoverDesign before = CoverDesign.of(book);
		Component nameBefore = book.get(DataComponents.CUSTOM_NAME);
		CoverDesign after = edit.isClear() ? CoverDesign.plain(material) : signedDesign(book, edit.details(before));
		if (!after.equals(before)) {
			after.applyTo(book);
		}
		ChatFormatting color = edit.colorsTitle() ? after.titleFormatting() : null;
		if (color != null) {
			nameInColor(book, color);
		} else if (edit.uncolorsTitle() && hasNameInColor(book)) {
			book.remove(DataComponents.CUSTOM_NAME);
		}
		return after.equals(before) && Objects.equals(nameBefore, book.get(DataComponents.CUSTOM_NAME)) ? CoverResult.UNCHANGED : CoverResult.CHANGED;
	}

	public static Component coverMessage(CoverResult result, ItemStack book, CoverEdit edit) {
		return switch (result) {
			case CHANGED, UNCHANGED -> {
				Component name = book.getHoverName();
				if (edit.isClear()) {
					yield Component.translatable("scribe.command.cover.cleared", name).withStyle(ChatFormatting.GREEN);
				}
				Component rows = ComponentUtils.formatList(CoverLines.changed(book, edit.part()), Component.literal(", "));
				yield result == CoverResult.CHANGED ? Component.translatable("scribe.command.cover.changed", name, rows).withStyle(ChatFormatting.GREEN)
					: Component.translatable("scribe.command.cover.unchanged", name, rows).withStyle(ChatFormatting.GRAY);
			}
			case NOT_A_BOOK -> Component.translatable("scribe.command.cover.hold_book").withStyle(ChatFormatting.RED);
			case NEEDS_TRIM -> Component.translatable("scribe.sign.corners_need_trim").withStyle(ChatFormatting.RED);
			case NEEDS_BUCKLE -> Component.translatable("scribe.sign.metalwork_needs_buckle").withStyle(ChatFormatting.RED);
			case NOT_A_CHOICE -> Component.translatable("scribe.command.cover.unknown_choice", Objects.requireNonNullElse(edit.wrongWord(), ""))
				.withStyle(ChatFormatting.RED);
			case OFF -> Component.translatable("scribe.sign.designs_off").withStyle(ChatFormatting.RED);
			case NOT_AUTHOR -> onlyAuthorEdits().withStyle(ChatFormatting.RED);
		};
	}

	public enum DesignerResult {
		SAVED,
		UNCHANGED,
		NOT_A_BOOK,
		NOT_AUTHOR,
		NO_TITLE
	}

	private static void saveCoverDesigner(ServerPlayer player, Payloads.CoverDesignerSave payload) {
		filter(player, payload.details(), details -> player.getTextFilter().processMessageBundle(List.of(payload.title())).thenAcceptAsync(titles -> {
			if (player.isRemoved()) {
				return;
			}
			DesignerResult result = saveCoverInHand(player, payload.slot(), titles.getFirst(), details, payload.override());
			player.sendSystemMessage(designerMessage(result, player.getMainHandItem()));
		}, player.level().getServer()));
	}

	public static DesignerResult saveCoverInHand(ServerPlayer player, int slot, FilteredText title, SignDetails details) {
		return saveCoverInHand(player, slot, title, details, false);
	}

	public static DesignerResult saveCoverInHand(ServerPlayer player, int slot, FilteredText title, SignDetails details, boolean override) {
		if (slot != player.getInventory().getSelectedSlot()) {
			return DesignerResult.NOT_A_BOOK;
		}
		ItemStack book = player.getMainHandItem();
		if (!book.is(Items.WRITTEN_BOOK) || !book.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
			return DesignerResult.NOT_A_BOOK;
		}
		if (override && RealAuthor.isOperator(player)) {
			return saveCoverKeepingAuthor(book, filterable(player, title), details);
		}
		if (!RealAuthor.isAuthor(book, player)) {
			return DesignerResult.NOT_AUTHOR;
		}
		return saveCover(book, player, filterable(player, title), details);
	}

	public static DesignerResult saveCoverKeepingAuthor(ItemStack book, Filterable<String> title, SignDetails details) {
		RealAuthor author = RealAuthor.of(book);
		return saveCover(book, author == null ? null : author.id(), RealAuthor.realName(book), title, details);
	}

	public static DesignerResult saveCover(ItemStack book, Player author, Filterable<String> title, SignDetails details) {
		return saveCover(book, author.getUUID(), author.getGameProfile().name(), title, details);
	}

	public static DesignerResult saveCover(ItemStack book, @Nullable UUID authorId, String authorName, Filterable<String> title, SignDetails details) {
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (content == null) {
			return DesignerResult.NOT_A_BOOK;
		}
		String raw = SignDetails.cleanTitle(title.raw());
		if (raw.isEmpty()) {
			return DesignerResult.NO_TITLE;
		}
		Filterable<String> cleanTitle = raw.equals(title.raw()) ? title : Filterable.passThrough(raw);
		ScribeConfig config = ScribeConfig.get();
		ItemStack before = book.copy();
		boolean scribeName = hasNameInColor(book);
		String shown = content.author();
		if (config.allowPenNames) {
			shown = details.hasPenName() ? details.penName() : authorName;
		}
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(cleanTitle, shown, content.generation(), content.pages(), content.resolved()));
		if (config.allowSubtitles) {
			setSubtitle(book, details.subtitle());
		}
		boolean colorChosen = false;
		if (config.bookbindingOn() && details.hasDesign()) {
			CoverDesign was = CoverDesign.of(book);
			CoverDesign now = signedDesign(book, details);
			if (!now.equals(was)) {
				now.applyTo(book);
			}
			colorChosen = CoverDesign.isTitleColor(details.titleColor());
		}
		ChatFormatting color = CoverDesign.of(book).titleFormatting();
		if (color != null && (scribeName || colorChosen)) {
			nameInColor(book, color);
		} else if (color == null && scribeName) {
			book.remove(DataComponents.CUSTOM_NAME);
		}
		if (authorId != null) {
			RealAuthor.stamp(book, authorId, authorName);
		}
		return ItemStack.isSameItemSameComponents(before, book) ? DesignerResult.UNCHANGED : DesignerResult.SAVED;
	}

	public static void setSubtitle(ItemStack written, List<String> subtitle) {
		ItemLore old = written.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		List<Component> rest = old.lines().stream().filter(line -> !SUBTITLE_MARK.equals(line.getStyle().getInsertion())).toList();
		if (rest.size() != old.lines().size()) {
			if (rest.isEmpty()) {
				written.remove(DataComponents.LORE);
			} else {
				written.set(DataComponents.LORE, new ItemLore(rest));
			}
		}
		if (!subtitle.isEmpty()) {
			addSubtitle(written, subtitle);
		}
	}

	public static MutableComponent onlyAuthorEdits() {
		return Component.translatableWithFallback("scribe.author.only_edit", "Only the author can edit this cover");
	}

	public static Component designerMessage(DesignerResult result, ItemStack book) {
		return switch (result) {
			case SAVED -> Component.translatable("scribe.cover_edit.saved", book.getHoverName()).withStyle(ChatFormatting.GREEN);
			case UNCHANGED -> Component.translatable("scribe.cover_edit.unchanged", book.getHoverName()).withStyle(ChatFormatting.GRAY);
			case NOT_A_BOOK -> Component.translatable("scribe.cover_edit.hold_written").withStyle(ChatFormatting.RED);
			case NOT_AUTHOR -> onlyAuthorEdits().withStyle(ChatFormatting.RED);
			case NO_TITLE -> Component.translatable("scribe.cover_edit.no_title").withStyle(ChatFormatting.RED);
		};
	}

	public enum SaveResult {
		SAVED,
		UNCHANGED,
		NOT_A_BOOK,
		OFF
	}

	private static void saveDesign(ServerPlayer player, Payloads.SaveDesign payload) {
		if (payload.slot() >= 0) {
			saveDesignInHand(player, payload.slot(), payload.details());
			return;
		}
		if (!ScribeConfig.get().lecternEditingOn()) {
			return;
		}
		LecternBlockEntity lectern = openLectern(player, payload.containerId());
		if (lectern != null && saveDesign(lectern.getBook(), payload.details()) == SaveResult.SAVED) {
			((LecternAccess) lectern).scribe$bookEdited();
		}
	}

	public static SaveResult saveDesignInHand(ServerPlayer player, int slot, SignDetails details) {
		ItemStack book;
		if (slot == BookLimits.OFFHAND_SLOT) {
			book = player.getOffhandItem();
		} else if (slot == player.getInventory().getSelectedSlot()) {
			book = player.getMainHandItem();
		} else {
			return SaveResult.NOT_A_BOOK;
		}
		return saveDesign(book, details);
	}

	public static SaveResult saveDesign(ItemStack book, SignDetails details) {
		if (!ScribeConfig.get().bookbindingOn()) {
			return SaveResult.OFF;
		}
		if (!book.is(Items.WRITABLE_BOOK)) {
			return SaveResult.NOT_A_BOOK;
		}
		return keepDesign(book, details) ? SaveResult.SAVED : SaveResult.UNCHANGED;
	}

	public static boolean keepDesign(ItemStack book, SignDetails details) {
		CoverDesign before = CoverDesign.of(book);
		CoverDesign after = details.hasDesign() ? signedDesign(book, details) : before;
		if (after.equals(before)) {
			return false;
		}
		after.applyTo(book);
		return true;
	}

	private static void saveDetails(ServerPlayer player, Payloads.SaveDetails payload) {
		SavedDetails asked = new SavedDetails(payload.title(), payload.subtitle(), payload.penName());
		filterDetails(player, asked, details -> {
			if (payload.slot() >= 0) {
				keepDetailsInHand(player, payload.slot(), details);
				return;
			}
			if (!ScribeConfig.get().lecternEditingOn()) {
				return;
			}
			LecternBlockEntity lectern = openLectern(player, payload.containerId());
			if (lectern != null && keepDetails(lectern.getBook(), details) == SaveResult.SAVED) {
				((LecternAccess) lectern).scribe$bookEdited();
			}
		});
	}

	private static void filterDetails(ServerPlayer player, SavedDetails details, java.util.function.Consumer<SavedDetails> then) {
		TextFilter filter = player.getTextFilter();
		if (filter == TextFilter.DUMMY || details.isEmpty()) {
			then.accept(details);
			return;
		}
		filter.processMessageBundle(List.of(details.title(), details.subtitle(), details.penName())).thenAcceptAsync(results -> then.accept(
			new SavedDetails(results.get(0).isFiltered() ? "" : details.title(), results.get(1).isFiltered() ? "" : details.subtitle(),
				results.get(2).isFiltered() ? "" : details.penName())), player.level().getServer());
	}

	public static SaveResult keepDetailsInHand(ServerPlayer player, int slot, SavedDetails details) {
		ItemStack book;
		if (slot == BookLimits.OFFHAND_SLOT) {
			book = player.getOffhandItem();
		} else if (slot == player.getInventory().getSelectedSlot()) {
			book = player.getMainHandItem();
		} else {
			return SaveResult.NOT_A_BOOK;
		}
		return keepDetails(book, details);
	}

	public static SaveResult keepDetails(ItemStack book, SavedDetails details) {
		if (!book.is(Items.WRITABLE_BOOK)) {
			return SaveResult.NOT_A_BOOK;
		}
		ScribeConfig config = ScribeConfig.get();
		return SavedDetails.write(book, details.taken(config.allowSubtitles, config.allowPenNames)) ? SaveResult.SAVED : SaveResult.UNCHANGED;
	}

	private static void editOnLectern(ServerPlayer player, Payloads.LecternEdit payload) {
		if (!ScribeConfig.get().lecternEditingOn()) {
			return;
		}
		LecternBlockEntity lectern = openLectern(player, payload.containerId());
		if (lectern == null) {
			return;
		}
		ItemStack book = lectern.getBook();
		if (!book.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
			return;
		}
		if (!LecternLocks.mayWrite(lectern, player, payload.containerId())) {
			return;
		}
		LecternLocks.saving(lectern);
		List<String> texts = new ArrayList<>();
		payload.title().ifPresent(texts::add);
		texts.addAll(payload.pages());
		filter(player, payload.details(), details -> player.getTextFilter().processMessageBundle(texts).thenAcceptAsync(filtered -> {
			if (lectern.isRemoved() || lectern.getBook() != book || player.isRemoved()
				|| player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(lectern.getBlockPos())) > 64.0) {
				return;
			}
			if (payload.title().isPresent()) {
				signOnLectern(player, lectern, book, filtered.getFirst(), filtered.subList(1, filtered.size()), details);
			} else {
				List<Filterable<String>> pages = filtered.stream().map(text -> filterable(player, text)).toList();
				book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(pages));
				((LecternAccess) lectern).scribe$bookEdited();
				scribe.api.ScribeEvents.BOOK_EDITED.invoker().edited(player, book, lectern.getBlockPos());
			}
		}, player.level().getServer()).whenCompleteAsync((ignored, error) -> LecternLocks.saved(lectern), player.level().getServer()));
	}

	private static void lecternPen(ServerPlayer player, Payloads.LecternPen payload) {
		LecternBlockEntity lectern = openLectern(player, payload.containerId());
		if (lectern == null) {
			return;
		}
		if (payload.writing()) {
			LecternLocks.typed(lectern, player);
		} else {
			LecternLocks.released(lectern, player);
		}
	}

	private static void signOnLectern(ServerPlayer player, LecternBlockEntity lectern, ItemStack book, FilteredText title, List<FilteredText> pages, SignDetails details) {
		ItemStack written = book.transmuteCopy(Items.WRITTEN_BOOK);
		written.remove(DataComponents.WRITABLE_BOOK_CONTENT);
		List<Filterable<Component>> content = pages.stream().map(page -> filterable(player, page).map(text -> (Component) Component.literal(text))).toList();
		written.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(filterable(player, title), player.getPlainTextName(), 0, content, true));
		applySignDetails(written, details);
		RealAuthor.stamp(written, player);
		linkContents(written);
		scribe.api.ScribeEvents.BOOK_SIGNED.invoker().signed(player, written, lectern.getBlockPos());
		lectern.setBook(written, player);
		((LecternAccess) lectern).scribe$bookEdited();
		ClosedLecterns.signed(lectern, player);
	}

	private static Filterable<String> filterable(ServerPlayer player, FilteredText text) {
		return player.isTextFilteringEnabled() ? Filterable.passThrough(text.filteredOrEmpty()) : Filterable.from(text);
	}

	private static @Nullable LecternBlockEntity openLectern(ServerPlayer player, int containerId) {
		if (!(player.containerMenu instanceof LecternMenu menu) || menu.containerId != containerId || !menu.stillValid(player) || !player.mayBuild()) {
			return null;
		}
		LecternBlockEntity lectern = ((LecternMenuAccess) menu).scribe$lectern();
		return lectern != null && !lectern.isRemoved() ? lectern : null;
	}

	public static boolean holdsOpen(ItemStack stack) {
		return Bookmarks.isBook(stack) || EnchantedBooks.is(stack);
	}

	private static void setReading(ServerPlayer player, boolean reading, boolean offhand) {
		if (reading && !holdsOpen(player.getItemInHand(offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND))) {
			reading = false;
		}
		Boolean before = reading ? READING.put(player.getUUID(), offhand) : READING.remove(player.getUUID());
		boolean changed = reading ? before == null || before != offhand : before != null;
		if (!changed || !ScribeConfig.get().sharedReadingPoseOn()) {
			return;
		}
		Payloads.ReadingState state = new Payloads.ReadingState(player.getId(), reading, offhand);
		for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
			if (viewer != player && ServerPlayNetworking.canSend(viewer, Payloads.ReadingState.TYPE)) {
				ServerPlayNetworking.send(viewer, state);
			}
		}
	}

	private static void turnPages(ServerPlayer player, int leaves) {
		Payloads.PageTurned turned = pageTurned(player, leaves);
		if (turned != null && mayShowTurn(player, player.level().getServer().getTickCount())) {
			for (ServerPlayer viewer : pageTurnReceivers(player, PlayerLookup.tracking(player))) {
				ServerPlayNetworking.send(viewer, turned);
			}
		}
	}

	public static final int TURNS_PER_SECOND = 40;
	private static final Map<ServerPlayer, int[]> TURNS = new java.util.WeakHashMap<>();

	public static boolean mayShowTurn(ServerPlayer player, long tick) {
		int second = (int) (tick / 20);
		int[] used = TURNS.computeIfAbsent(player, p -> new int[] {second, 0});
		if (used[0] != second) {
			used[0] = second;
			used[1] = 0;
		}
		if (used[1] >= TURNS_PER_SECOND) {
			return false;
		}
		used[1]++;
		return true;
	}

	public static Payloads.@Nullable PageTurned pageTurned(ServerPlayer player, int leaves) {
		if (leaves == 0 || !ScribeConfig.get().sharedReadingPoseOn() || !READING.containsKey(player.getUUID())) {
			return null;
		}
		return new Payloads.PageTurned(player.getId(), leaves);
	}

	private static void lecternLeaves(ServerPlayer player, Payloads.LecternLeaves payload) {
		Payloads.LecternLeavesTurned turned = lecternLeavesTurned(player, payload);
		if (turned != null && player.containerMenu instanceof LecternMenu menu && ((LecternMenuAccess) menu).scribe$lectern() instanceof LecternBlockEntity lectern
			&& mayShowTurn(player, player.level().getServer().getTickCount())) {
			for (ServerPlayer viewer : PlayerLookup.tracking(lectern)) {
				if (canSend(viewer, Payloads.LecternLeavesTurned.TYPE)) {
					ServerPlayNetworking.send(viewer, turned);
				}
			}
		}
	}

	public static Payloads.@Nullable LecternLeavesTurned lecternLeavesTurned(ServerPlayer player, Payloads.LecternLeaves payload) {
		if (payload.leaves() == 0 || !ScribeConfig.get().bookInfoOn() || !(player.containerMenu instanceof LecternMenu menu)
			|| menu.containerId != payload.containerId() || !menu.stillValid(player)) {
			return null;
		}
		LecternBlockEntity lectern = ((LecternMenuAccess) menu).scribe$lectern();
		if (lectern == null || lectern.isRemoved() || !lectern.hasBook() || ((LecternAccess) lectern).scribe$isClosed()) {
			return null;
		}
		return new Payloads.LecternLeavesTurned(lectern.getBlockPos(), payload.leaves());
	}

	public static List<ServerPlayer> pageTurnReceivers(ServerPlayer reader, Collection<ServerPlayer> watching) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer viewer : watching) {
			if (viewer != reader && canSend(viewer, Payloads.PageTurned.TYPE)) {
				out.add(viewer);
			}
		}
		return out;
	}

	private static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		try {
			return ServerPlayNetworking.canSend(player, type);
		} catch (RuntimeException noConnection) {
			return false;
		}
	}

	public static boolean isReading(ServerPlayer player) {
		return READING.containsKey(player.getUUID());
	}

	public static List<ServerPlayer> readers(Collection<ServerPlayer> players) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (READING.containsKey(player.getUUID())) {
				out.add(player);
			}
		}
		return out;
	}

	public static Payloads.ReadingState readingState(ServerPlayer reader, boolean on) {
		return new Payloads.ReadingState(reader.getId(), on, Boolean.TRUE.equals(READING.get(reader.getUUID())));
	}

	public static void tellWatchers(ServerPlayer reader, Payloads.ReadingState state) {
		for (ServerPlayer viewer : PlayerLookup.tracking(reader)) {
			if (viewer != reader && canSend(viewer, Payloads.ReadingState.TYPE)) {
				ServerPlayNetworking.send(viewer, state);
			}
		}
	}
}
