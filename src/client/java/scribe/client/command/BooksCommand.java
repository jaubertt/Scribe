package scribe.client.command;

import scribe.Scribe;
import scribe.BookLimits;
import scribe.RealAuthor;
import scribe.client.AuthorNames;
import scribe.client.io.BookFile;
import scribe.client.io.BookFiles;
import scribe.client.memory.PenNames;
import scribe.client.text.FormatCodes;
import scribe.client.text.PageCodec;
import scribe.client.text.PageFitter;
import com.mojang.blaze3d.Blaze3D;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

public final class BooksCommand {
	private BooksCommand() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("books")
			.then(ClientCommands.literal("export")
				.then(ClientCommands.argument("name", StringArgumentType.string())
					.executes(c -> export(c, false))
					.then(ClientCommands.argument("overwrite", BoolArgumentType.bool())
						.executes(c -> export(c, BoolArgumentType.getBool(c, "overwrite"))))))
			.then(ClientCommands.literal("import")
				.then(ClientCommands.argument("name", StringArgumentType.string())
					.suggests((c, builder) -> SharedSuggestionProvider.suggest(quoted(BookFiles.commandNames()), builder))
					.executes(BooksCommand::importBook)))
			.then(ClientCommands.literal("list").executes(BooksCommand::list))
			.then(ClientCommands.literal("folder").executes(BooksCommand::folder))
			.then(ClientCommands.literal("guide").executes(c -> {
				c.getSource().getClient().schedule(() -> scribe.client.GuideBook.open(null));
				return 1;
			}))
			.then(CoverCommand.node())
			.then(PoseCommand.node()));
	}

	private static List<String> quoted(List<String> names) {
		List<String> out = new ArrayList<>();
		for (String name : names) {
			out.add(StringArgumentType.escapeIfRequired(name));
		}
		return out;
	}

	private static CommandSyntaxException error(String key, Object... args) {
		return new SimpleCommandExceptionType(Component.translatable(key, args)).create();
	}

	private static InteractionHand handWith(LocalPlayer player, boolean writableOnly) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.is(Items.WRITABLE_BOOK) || (!writableOnly && stack.is(Items.WRITTEN_BOOK))) {
				return hand;
			}
		}
		return null;
	}

	private static int export(CommandContext<FabricClientCommandSource> context, boolean overwrite) throws CommandSyntaxException {
		LocalPlayer player = context.getSource().getPlayer();
		InteractionHand hand = handWith(player, false);
		if (hand == null) {
			throw error("scribe.command.hold_any_book");
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!PenNames.isOwnBook(stack)) {
			WrittenBookContent other = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
			throw error("scribe.files.not_own", other == null ? "?" : AuthorNames.name(other.author(), RealAuthor.isPenName(stack)));
		}
		BookFile book;
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (written != null) {
			List<String> pages = new ArrayList<>();
			written.getPages(false).forEach(page -> pages.add(FormatCodes.toLegacy(page)));
			book = BookFile.of(written.title().raw(), written.author(), pages);
		} else {
			WritableBookContent writable = stack.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY);
			book = BookFile.of(null, player.getName().getString(), writable.getPages(false).toList());
		}
		if (book.pages().isEmpty()) {
			throw error("scribe.command.empty_book");
		}
		String name = BookFiles.fileName(StringArgumentType.getString(context, "name"));
		try {
			Path path = BookFiles.ensureBooksDir().resolve(name);
			if (Files.exists(path) && !overwrite) {
				String command = "/books export " + StringArgumentType.escapeIfRequired(StringArgumentType.getString(context, "name")) + " true";
				MutableComponent fix = Component.literal(command).withStyle(style -> style
					.withClickEvent(new ClickEvent.SuggestCommand(command))
					.withColor(ChatFormatting.AQUA)
					.withUnderlined(true));
				context.getSource().sendError(Component.translatable("scribe.command.exists", name, fix));
				return 0;
			}
			BookFiles.write(path, book);
		} catch (IOException e) {
			Scribe.LOGGER.warn("Could not export book to {}", name, e);
			throw error("scribe.notice.export_failed", name);
		}
		context.getSource().sendFeedback(Component.translatable("scribe.notice.exported", name).withStyle(ChatFormatting.GREEN));
		return book.pages().size();
	}

	private static int importBook(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
		LocalPlayer player = context.getSource().getPlayer();
		InteractionHand hand = handWith(player, true);
		if (hand == null) {
			throw error("scribe.command.hold_writable");
		}
		String name = StringArgumentType.getString(context, "name");
		Path path = BookFiles.resolve(name);
		if (path == null) {
			throw error("scribe.command.not_found", name);
		}
		BookFile book;
		PageFitter fitter = new PageFitter(context.getSource().getClient().font);
		try {
			book = BookFiles.read(path, fitter);
		} catch (IOException e) {
			Scribe.LOGGER.warn("Could not import {}", path, e);
			throw error("scribe.notice.import_failed", name);
		}
		List<String> pages = PageCodec.encodeBook(fitter.font(), PageCodec.decodeBook(BookFiles.fitToBook(book.pages(), fitter)));
		ClientPacketListener connection = context.getSource().getClient().getConnection();
		if (connection == null) {
			return 0;
		}
		int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : BookLimits.OFFHAND_SLOT;
		player.getItemInHand(hand).set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(pages.stream().map(Filterable::passThrough).toList()));
		connection.send(new ServerboundEditBookPacket(slot, pages, Optional.empty()));
		context.getSource().sendFeedback(Component.translatable("scribe.command.imported", path.getFileName().toString(), pages.size()).withStyle(ChatFormatting.GREEN));
		book.author().filter(author -> !author.isBlank() && !author.equalsIgnoreCase(player.getGameProfile().name()))
			.ifPresent(author -> context.getSource().sendFeedback(Component.translatable("scribe.notice.original_author", author).withStyle(ChatFormatting.GRAY)));
		return pages.size();
	}

	private static int list(CommandContext<FabricClientCommandSource> context) {
		List<BookFiles.Entry> entries = BookFiles.list(false);
		if (entries.isEmpty()) {
			context.getSource().sendFeedback(Component.translatable("scribe.files.empty"));
			return 0;
		}
		context.getSource().sendFeedback(Component.translatable("scribe.command.list_header", entries.size()).withStyle(ChatFormatting.GOLD));
		for (BookFiles.Entry entry : entries) {
			String command = "/books import " + StringArgumentType.escapeIfRequired(entry.name());
			context.getSource().sendFeedback(Component.literal(" - " + entry.name()).withStyle(style -> style
				.withClickEvent(new ClickEvent.SuggestCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(command)))
				.withColor(ChatFormatting.AQUA)));
		}
		return entries.size();
	}

	private static int folder(CommandContext<FabricClientCommandSource> context) {
		try {
			Blaze3D.openPath(BookFiles.ensureBooksDir());
		} catch (IOException e) {
			Scribe.LOGGER.warn("Could not open the books folder", e);
		}
		return 1;
	}
}
