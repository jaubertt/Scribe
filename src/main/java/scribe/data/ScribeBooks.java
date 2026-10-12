package scribe.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;
import scribe.Scribe;

public final class ScribeBooks {
	private static volatile Map<Identifier, ItemStack> books = Map.of();
	private static volatile Map<Identifier, List<BookData.Found>> found = Map.of();

	private ScribeBooks() {
	}

	public static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Scribe.id("books"), new Reader());
		Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, Scribe.id("set_book"), SetBookFunction.CODEC);
		LootTableEvents.MODIFY_DROPS.register(ScribeBooks::addFound);
	}

	public static @Nullable ItemStack book(Identifier id) {
		ItemStack book = books.get(id);
		return book == null ? null : book.copy();
	}

	public static Set<Identifier> ids() {
		return new TreeSet<>(books.keySet());
	}

	public static Map<Identifier, List<Identifier>> foundIn() {
		Map<Identifier, List<Identifier>> out = new LinkedHashMap<>();
		found.forEach((table, list) -> list.forEach(entry -> out.computeIfAbsent(entry.book(), b -> new ArrayList<>()).add(table)));
		return out;
	}

	public static void set(Map<Identifier, ItemStack> newBooks, List<BookData.Found> newFound) {
		Map<Identifier, List<BookData.Found>> byTable = new LinkedHashMap<>();
		for (BookData.Found entry : newFound) {
			entry.lootTables().keySet().forEach(table -> byTable.computeIfAbsent(table, t -> new ArrayList<>()).add(entry));
		}
		books = Map.copyOf(newBooks);
		found = Map.copyOf(byTable);
	}

	static void addFound(Holder<LootTable> table, LootContext context, List<ItemStack> drops) {
		Map<Identifier, List<BookData.Found>> all = found;
		if (all.isEmpty()) {
			return;
		}
		table.unwrapKey().ifPresent(key -> drops.addAll(rolled(key.identifier(), context.getRandom()::nextFloat)));
	}

	public static List<ItemStack> rolled(Identifier table, java.util.function.Supplier<Float> random) {
		List<ItemStack> out = new ArrayList<>();
		for (BookData.Found entry : found.getOrDefault(table, List.of())) {
			float chance = entry.lootTables().getOrDefault(table, 0.0F);
			if (random.get() < chance) {
				ItemStack book = book(entry.book());
				if (book != null) {
					out.add(BookData.withGeneration(book, entry.generation()));
				}
			}
		}
		return out;
	}

	private static final class Reader extends SimpleReloadListener<Reader.Read> {
		record Read(Map<Identifier, ItemStack> books, List<BookData.Found> found) {
		}

		@Override
		protected Read prepare(PreparableReloadListener.SharedState state) {
			ResourceManager manager = state.resourceManager();
			HolderLookup.Provider registries = state.get(ResourceLoader.REGISTRY_LOOKUP_KEY);
			Map<Identifier, ItemStack> read = new LinkedHashMap<>();
			for (Map.Entry<Identifier, Resource> file : manager.listResources(BookData.BOOKS, id -> id.getPath().endsWith(".json")).entrySet()) {
				Identifier id = idOf(file.getKey(), BookData.BOOKS);
				JsonElement json = json(file.getKey(), file.getValue());
				if (json != null) {
					BookData.readBook(json, registries).resultOrPartial(error -> Scribe.LOGGER.warn("Book {} ({}) left out: {}", id, file.getKey(), error))
						.ifPresent(book -> read.put(id, book));
				}
			}
			List<BookData.Found> foundBooks = new ArrayList<>();
			for (Map.Entry<Identifier, Resource> file : manager.listResources(BookData.FOUND, id -> id.getPath().endsWith(".json")).entrySet()) {
				JsonElement json = json(file.getKey(), file.getValue());
				if (json != null) {
					BookData.readFound(json).resultOrPartial(error -> Scribe.LOGGER.warn("Found book {} left out: {}", file.getKey(), error)).ifPresent(entry -> {
						if (!read.containsKey(entry.book())) {
							Scribe.LOGGER.warn("Found book {} names {}, which no data pack has", file.getKey(), entry.book());
						}
						foundBooks.add(entry);
					});
				}
			}
			return new Read(read, foundBooks);
		}

		@Override
		protected void apply(Read read, PreparableReloadListener.SharedState state) {
			set(read.books(), read.found());
			if (!read.books().isEmpty()) {
				Scribe.LOGGER.info("Books from data packs: {}, found in loot tables: {}", read.books().size(), read.found().size());
			}
		}

		private static @Nullable JsonElement json(Identifier file, Resource resource) {
			try (java.io.Reader reader = resource.openAsReader()) {
				return JsonParser.parseReader(reader);
			} catch (Exception e) {
				Scribe.LOGGER.warn("Could not read {}: {}", file, e.toString());
				return null;
			}
		}
	}

	static Identifier idOf(Identifier file, String folder) {
		String path = file.getPath();
		path = path.substring(folder.length() + 1, path.length() - ".json".length());
		return Identifier.fromNamespaceAndPath(file.getNamespace(), path);
	}
}
