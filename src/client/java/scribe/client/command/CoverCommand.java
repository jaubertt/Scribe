package scribe.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import scribe.RealAuthor;
import scribe.binding.Binding;
import scribe.binding.Bookbinding;
import scribe.binding.Clasp;
import scribe.binding.CoverEdit;
import scribe.binding.CoverLines;
import scribe.client.BookNet;
import scribe.client.screen.CoverEditScreen;
import scribe.config.ScribeConfig;
import scribe.server.ServerBooks;

public final class CoverCommand {
	static final String DECORATION = "decoration";
	static final String LOOK = "look";
	static final String SPINE = "spine";
	static final String COLOR = "color";
	static final String CORNERS = "corners";
	static final String DESIGN = "design";
	static final String AUTHOR = "author";
	public static final String EDIT = "edit";
	static final String EDIT_COMMAND = "/books cover " + EDIT;
	public static final String OVERRIDE = "override";
	static final String OVERRIDE_COMMAND = EDIT_COMMAND + " " + OVERRIDE;

	private CoverCommand() {
	}

	static LiteralArgumentBuilder<FabricClientCommandSource> node() {
		return ClientCommands.literal("cover")
			.executes(CoverCommand::show)
			.then(ClientCommands.literal(CoverEdit.DECORATION)
				.then(choice(CoverEdit.DECORATION)
					.executes(c -> change(c, edit(c, CoverEdit.DECORATION, false)))
					.then(ClientCommands.argument(LOOK, StringArgumentType.word())
						.suggests(CoverCommand::suggestLooks)
						.executes(c -> change(c, edit(c, CoverEdit.DECORATION, true))))))
			.then(ClientCommands.literal(CoverEdit.SPINE).then(choice(CoverEdit.SPINE).executes(c -> change(c, edit(c, CoverEdit.SPINE, false)))))
			.then(ClientCommands.literal(CoverEdit.TITLE).then(choice(CoverEdit.TITLE).executes(c -> change(c, edit(c, CoverEdit.TITLE, false)))))
			.then(ClientCommands.literal(CoverEdit.CORNERS).then(choice(CoverEdit.CORNERS).executes(c -> change(c, edit(c, CoverEdit.CORNERS, false)))))
			.then(ClientCommands.literal(CoverEdit.METALWORK).then(choice(CoverEdit.METALWORK).executes(c -> change(c, edit(c, CoverEdit.METALWORK, false)))))
			.then(ClientCommands.literal(CoverEdit.AUTHOR).then(choice(CoverEdit.AUTHOR).executes(c -> change(c, edit(c, CoverEdit.AUTHOR, false)))))
			.then(ClientCommands.literal(CoverEdit.CLEAR).executes(c -> change(c, CoverEdit.clear())))
			.then(ClientCommands.literal(EDIT).executes(CoverCommand::edit)
				.then(ClientCommands.literal(OVERRIDE).requires(CoverCommand::admin).executes(CoverCommand::editOverride)));
	}

	static boolean admin(@Nullable FabricClientCommandSource source) {
		LocalPlayer player = source == null ? null : source.getPlayer();
		return player != null && RealAuthor.isOperator(player);
	}

	private static RequiredArgumentBuilder<FabricClientCommandSource, String> choice(String part) {
		List<String> words = CoverEdit.choices(part);
		return ClientCommands.argument(argument(part), StringArgumentType.word())
			.suggests((context, builder) -> SharedSuggestionProvider.suggest(words, builder));
	}

	static String argument(String part) {
		return switch (part) {
			case CoverEdit.DECORATION -> DECORATION;
			case CoverEdit.SPINE -> SPINE;
			case CoverEdit.TITLE -> COLOR;
			case CoverEdit.CORNERS -> CORNERS;
			case CoverEdit.METALWORK -> DESIGN;
			case CoverEdit.AUTHOR -> AUTHOR;
			default -> throw new IllegalArgumentException(part);
		};
	}

	static CompletableFuture<Suggestions> suggestLooks(CommandContext<?> context, SuggestionsBuilder builder) {
		String decoration;
		try {
			decoration = StringArgumentType.getString(context, DECORATION);
		} catch (IllegalArgumentException notParsed) {
			decoration = "";
		}
		return CoverEdit.NO_DECORATION.equals(decoration) ? builder.buildFuture() : SharedSuggestionProvider.suggest(CoverEdit.LOOKS, builder);
	}

	static CoverEdit edit(CommandContext<?> context, String part, boolean withLook) throws CommandSyntaxException {
		String value = StringArgumentType.getString(context, argument(part));
		String look = withLook ? StringArgumentType.getString(context, LOOK) : "";
		String wrong = CoverEdit.wrongWord(part, value, look);
		if (wrong != null) {
			throw error("scribe.command.cover.unknown_choice", wrong);
		}
		return new CoverEdit(part, value, look);
	}

	static @Nullable Component refusal(boolean scribeOnServer, boolean designs, ItemStack held, CoverEdit edit, UUID player, String playerName) {
		if (!scribeOnServer) {
			return Component.translatable("scribe.command.cover.needs_scribe");
		}
		if (!designs) {
			return Component.translatable("scribe.sign.designs_off");
		}
		if (!CoverEdit.isBook(held)) {
			return Component.translatable("scribe.command.cover.hold_book");
		}
		if (!mayChange(held, player, playerName)) {
			return ServerBooks.onlyAuthorEdits();
		}
		if (edit.needsTrim() && Bookbinding.materialName(Binding.of(held)) == null) {
			return Component.translatable("scribe.sign.corners_need_trim");
		}
		if (edit.needsBuckle() && Clasp.metal(Binding.of(held)) == null) {
			return Component.translatable("scribe.sign.metalwork_needs_buckle");
		}
		return null;
	}

	static boolean mayChange(ItemStack book, UUID player, String playerName) {
		return !book.has(DataComponents.WRITTEN_BOOK_CONTENT) || RealAuthor.isAuthor(book, player, playerName);
	}

	private static int change(CommandContext<FabricClientCommandSource> context, CoverEdit edit) throws CommandSyntaxException {
		LocalPlayer player = context.getSource().getPlayer();
		Component refusal = refusal(BookNet.coverChanges(), BookNet.coverDesigns(), player.getMainHandItem(), edit, player.getUUID(),
			player.getGameProfile().name());
		if (refusal == null && !ScribeConfig.get().designerShown()) {
			refusal = Component.translatable("scribe.sign.designer_off");
		}
		if (refusal != null) {
			throw new SimpleCommandExceptionType(refusal).create();
		}
		BookNet.sendCoverEdit(player.getInventory().getSelectedSlot(), edit);
		return 1;
	}

	private static int show(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
		LocalPlayer player = context.getSource().getPlayer();
		ItemStack book = player.getMainHandItem();
		if (!CoverEdit.isBook(book)) {
			throw error("scribe.command.cover.hold_book");
		}
		String name = player.getGameProfile().name();
		boolean editable = editRefusal(BookNet.coverDesigner(), book, player.getUUID(), name, false) == null;
		boolean overridable = !editable && RealAuthor.isOperator(player) && editRefusal(BookNet.coverDesigner(), book, player.getUUID(), name, true) == null;
		String editCommand = editable ? EDIT_COMMAND : overridable ? OVERRIDE_COMMAND : null;
		for (Component line : shown(book, BookNet.coverChanges(), BookNet.coverDesigns(), editCommand, mayChange(book, player.getUUID(), name))) {
			context.getSource().sendFeedback(line);
		}
		return 1;
	}

	static List<Component> shown(ItemStack book, boolean scribeOnServer, boolean designs) {
		return shown(book, scribeOnServer, designs, false);
	}

	static List<Component> shown(ItemStack book, boolean scribeOnServer, boolean designs, boolean editable) {
		return shown(book, scribeOnServer, designs, editable, true);
	}

	static List<Component> shown(ItemStack book, boolean scribeOnServer, boolean designs, boolean editable, boolean mayChange) {
		return shown(book, scribeOnServer, designs, editable ? EDIT_COMMAND : null, mayChange);
	}

	static List<Component> shown(ItemStack book, boolean scribeOnServer, boolean designs, @Nullable String editCommand, boolean mayChange) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("scribe.command.cover.shown", book.getHoverName()).withStyle(ChatFormatting.GOLD));
		for (Component row : CoverLines.all(book)) {
			lines.add(CommonComponents.space().append(row));
		}
		if (!scribeOnServer) {
			lines.add(Component.translatable("scribe.command.cover.needs_scribe").withStyle(ChatFormatting.GRAY));
		} else if (!designs) {
			lines.add(Component.translatable("scribe.sign.designs_off").withStyle(ChatFormatting.GRAY));
		} else if (!mayChange) {
			lines.add(ServerBooks.onlyAuthorEdits().withStyle(ChatFormatting.GRAY));
		}
		if (editCommand != null) {
			Component command = Component.literal(editCommand).withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand(editCommand))
				.withColor(ChatFormatting.AQUA).withUnderlined(true));
			lines.add(Component.translatable("scribe.cover_edit.hint", command).withStyle(ChatFormatting.GRAY));
		}
		return lines;
	}

	static @Nullable Component editRefusal(boolean designer, ItemStack held, UUID player, String playerName, boolean override) {
		if (!designer) {
			return Component.translatable("scribe.cover_edit.needs_scribe");
		}
		if (!held.is(Items.WRITTEN_BOOK) || !held.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
			return Component.translatable("scribe.cover_edit.hold_written");
		}
		if (!override && !RealAuthor.isAuthor(held, player, playerName)) {
			return ServerBooks.onlyAuthorEdits();
		}
		return null;
	}

	private static int edit(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
		return openDesigner(context, false);
	}

	private static int editOverride(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
		return openDesigner(context, true);
	}

	private static int openDesigner(CommandContext<FabricClientCommandSource> context, boolean override) throws CommandSyntaxException {
		LocalPlayer player = context.getSource().getPlayer();
		ItemStack held = player.getMainHandItem();
		boolean asAdmin = override && RealAuthor.isOperator(player);
		Component refusal = editRefusal(BookNet.coverDesigner(), held, player.getUUID(), player.getGameProfile().name(), asAdmin);
		if (refusal != null) {
			throw new SimpleCommandExceptionType(refusal).create();
		}
		Minecraft minecraft = context.getSource().getClient();
		int slot = player.getInventory().getSelectedSlot();
		String name = asAdmin ? RealAuthor.realName(held) : player.getGameProfile().name();
		minecraft.schedule(() -> minecraft.gui.setScreen(new CoverEditScreen(held, slot, name, asAdmin)));
		return 1;
	}

	private static CommandSyntaxException error(String key, Object... args) {
		return new SimpleCommandExceptionType(Component.translatable(key, args)).create();
	}
}
