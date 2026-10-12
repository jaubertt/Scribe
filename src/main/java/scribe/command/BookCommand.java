package scribe.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import scribe.Scribe;
import scribe.data.BookData;
import scribe.data.ScribeBooks;

public final class BookCommand {
	public static final String PACK = "scribe_books";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final SimpleCommandExceptionType HOLD_WRITTEN = new SimpleCommandExceptionType(
		Component.translatableWithFallback("scribe.cover_edit.hold_written", "Hold a written book in your main hand"));
	private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(id ->
		Component.translatableWithFallback("scribe.command.book.unknown", "No data pack has the book %s", String.valueOf(id)));
	private static final DynamicCommandExceptionType FAILED = new DynamicCommandExceptionType(error ->
		Component.translatableWithFallback("scribe.command.book.failed", "Could not write the data pack: %s", String.valueOf(error)));

	private BookCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("scribe").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("book").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("save")
					.then(Commands.argument("id", IdentifierArgument.id())
						.executes(c -> save(c.getSource(), IdentifierArgument.getId(c, "id")))))
				.then(Commands.literal("found")
					.then(Commands.argument("id", IdentifierArgument.id()).suggests((c, b) -> SharedSuggestionProvider.suggestResource(ScribeBooks.ids(), b))
						.then(Commands.argument("loot_table", IdentifierArgument.id())
							.suggests((c, b) -> SharedSuggestionProvider.suggestResource(lootTables(c.getSource().getServer()), b))
							.then(Commands.argument("chance", FloatArgumentType.floatArg(0.0F, 1.0F))
								.executes(c -> found(c.getSource(), IdentifierArgument.getId(c, "id"), IdentifierArgument.getId(c, "loot_table"),
									FloatArgumentType.getFloat(c, "chance")))))))
				.then(Commands.literal("give")
					.then(Commands.argument("id", IdentifierArgument.id()).suggests((c, b) -> SharedSuggestionProvider.suggestResource(ScribeBooks.ids(), b))
						.executes(c -> give(c.getSource(), IdentifierArgument.getId(c, "id"), List.of(c.getSource().getPlayerOrException())))
						.then(Commands.argument("players", EntityArgument.players())
							.executes(c -> give(c.getSource(), IdentifierArgument.getId(c, "id"), EntityArgument.getPlayers(c, "players"))))))
				.then(Commands.literal("list").executes(c -> list(c.getSource())))));
	}

	private static List<Identifier> lootTables(MinecraftServer server) {
		return server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds().map(key -> key.identifier()).sorted().toList();
	}

	private static int save(CommandSourceStack source, Identifier id) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ItemStack book = player.getMainHandItem();
		if (!book.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
			throw HOLD_WRITTEN.create();
		}
		try {
			Path file = file(source.getServer(), id, BookData.BOOKS);
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(BookData.writeBook(book, source.registryAccess())), StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException e) {
			Scribe.LOGGER.warn("Could not save the book {}", id, e);
			throw FAILED.create(e.getMessage());
		}
		source.sendSuccess(() -> Component.translatableWithFallback("scribe.command.book.saved",
			"Saved %s as %s in this world's data pack \"scribe_books\". Run %s to use it.", book.getHoverName(), id.toString(), reload())
			.withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int found(CommandSourceStack source, Identifier id, Identifier table, float chance) throws CommandSyntaxException {
		try {
			Path file = file(source.getServer(), id, BookData.FOUND);
			Map<Identifier, Float> tables = new LinkedHashMap<>();
			OptionalInt generation = OptionalInt.empty();
			if (Files.isRegularFile(file)) {
				JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
				BookData.Found before = BookData.readFound(json).result().orElse(null);
				if (before != null) {
					tables.putAll(before.lootTables());
					generation = before.generation();
				}
			}
			if (chance <= 0) {
				tables.remove(table);
			} else {
				tables.put(table, chance);
			}
			if (tables.isEmpty()) {
				Files.deleteIfExists(file);
			} else {
				Files.createDirectories(file.getParent());
				Files.writeString(file, GSON.toJson(BookData.writeFound(new BookData.Found(id, tables, generation))), StandardCharsets.UTF_8);
			}
		} catch (IOException | RuntimeException e) {
			Scribe.LOGGER.warn("Could not save where the book {} is found", id, e);
			throw FAILED.create(e.getMessage());
		}
		if (chance <= 0) {
			source.sendSuccess(() -> Component.translatableWithFallback("scribe.command.book.unfound", "%s is no longer found in %s. Run %s to use it.",
				id.toString(), table.toString(), reload()).withStyle(ChatFormatting.GREEN), true);
		} else {
			source.sendSuccess(() -> Component.translatableWithFallback("scribe.command.book.found", "%s is found in %s at a chance of %s. Run %s to use it.",
				id.toString(), table.toString(), String.valueOf(chance), reload()).withStyle(ChatFormatting.GREEN), true);
		}
		return 1;
	}

	private static int give(CommandSourceStack source, Identifier id, Collection<ServerPlayer> players) throws CommandSyntaxException {
		ItemStack book = ScribeBooks.book(id);
		if (book == null) {
			throw UNKNOWN.create(id);
		}
		for (ServerPlayer player : players) {
			player.getInventory().placeItemBackInInventory(book.copy(), Prediction.SERVER_ONLY);
		}
		Component name = book.getDisplayName();
		if (players.size() == 1) {
			source.sendSuccess(() -> Component.translatable("commands.give.success.single", 1, name, players.iterator().next().getDisplayName()), true);
		} else {
			source.sendSuccess(() -> Component.translatable("commands.give.success.multiple", 1, name, players.size()), true);
		}
		return players.size();
	}

	private static int list(CommandSourceStack source) {
		var ids = ScribeBooks.ids();
		if (ids.isEmpty()) {
			source.sendSuccess(() -> Component.translatableWithFallback("scribe.command.book.none",
				"No data pack has a book yet. Save the written book in your hand with /scribe book save <name>."), false);
			return 0;
		}
		Map<Identifier, List<Identifier>> foundIn = ScribeBooks.foundIn();
		source.sendSuccess(() -> Component.translatableWithFallback("scribe.command.book.list", "Books from data packs (%s):", ids.size())
			.withStyle(ChatFormatting.GOLD), false);
		for (Identifier id : ids) {
			String command = "/scribe book give " + id;
			MutableComponent line = Component.literal(" - " + id).withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand(command))
				.withColor(ChatFormatting.AQUA));
			List<Identifier> tables = foundIn.getOrDefault(id, List.of());
			if (!tables.isEmpty()) {
				line.append(Component.literal(" ").append(Component.translatableWithFallback("scribe.command.book.found_in", "found in %s",
					String.join(", ", tables.stream().map(Identifier::toString).toList()))).withStyle(ChatFormatting.GRAY));
			}
			source.sendSuccess(() -> line, false);
		}
		return ids.size();
	}

	private static Component reload() {
		return Component.literal("/reload").withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand("/reload"))
			.withColor(ChatFormatting.AQUA).withUnderlined(true));
	}

	static Path file(MinecraftServer server, Identifier id, String folder) throws IOException {
		Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK).toAbsolutePath().normalize();
		writePackMeta(pack);
		Path data = pack.resolve("data").resolve(id.getNamespace()).resolve(folder);
		Path file = data.resolve(id.getPath() + ".json").normalize();
		if (!file.startsWith(data)) {
			throw new IOException("not a book id: " + id);
		}
		return file;
	}

	static void writePackMeta(Path pack) throws IOException {
		Path meta = pack.resolve("pack.mcmeta");
		if (Files.isRegularFile(meta)) {
			return;
		}
		Files.createDirectories(pack);
		PackMetadataSection section = new PackMetadataSection(Component.literal("Books saved with /scribe book save (Scribe)"),
			new InclusiveRange<>(SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA)));
		JsonElement encoded = PackMetadataSection.SERVER_TYPE.codec().encodeStart(JsonOps.INSTANCE, section).getOrThrow();
		com.google.gson.JsonObject file = new com.google.gson.JsonObject();
		file.add(PackMetadataSection.SERVER_TYPE.name(), encoded);
		Files.writeString(meta, GSON.toJson(file), StandardCharsets.UTF_8);
	}
}
