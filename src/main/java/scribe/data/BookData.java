package scribe.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import scribe.BookLimits;
import scribe.server.ServerBooks;

public final class BookData {
	public static final String BOOKS = "scribe_book";
	public static final String FOUND = "scribe_found_book";

	private BookData() {
	}

	public record Found(Identifier book, Map<Identifier, Float> lootTables, OptionalInt generation) {
	}

	public static DataResult<ItemStack> readBook(JsonElement json, HolderLookup.Provider registries) {
		if (!json.isJsonObject()) {
			return DataResult.error(() -> "a book file is a JSON object");
		}
		JsonObject file = json.getAsJsonObject();
		if (file.has("item")) {
			return ItemStack.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), file.get("item")).flatMap(stack ->
				stack.has(DataComponents.WRITTEN_BOOK_CONTENT) ? DataResult.success(stack.copyWithCount(1)) : DataResult.error(() -> "its item is not a written book"));
		}
		if (!file.has("pages") || !file.get("pages").isJsonArray()) {
			return DataResult.error(() -> "a book file has \"item\" or \"pages\"");
		}
		List<Filterable<Component>> pages = new ArrayList<>();
		for (JsonElement page : file.getAsJsonArray("pages")) {
			if (pages.size() >= BookLimits.MAX_PAGES) {
				break;
			}
			pages.add(Filterable.passThrough(Component.literal(page.isJsonPrimitive() ? page.getAsString() : page.toString())));
		}
		String title = file.has("title") ? file.get("title").getAsString() : "";
		if (title.length() > BookLimits.TITLE_CHARS) {
			title = title.substring(0, BookLimits.TITLE_CHARS);
		}
		String author = file.has("author") ? file.get("author").getAsString() : "";
		int generation = file.has("generation") ? Math.clamp(file.get("generation").getAsInt(), 0, WrittenBookContent.MAX_GENERATION) : 0;
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), author, generation, pages, true));
		return DataResult.success(ServerBooks.linkContents(book));
	}

	public static JsonObject writeBook(ItemStack book, HolderLookup.Provider registries) {
		JsonObject file = new JsonObject();
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (content != null) {
			file.addProperty("title", content.title().raw());
			file.addProperty("author", content.author());
		}
		file.add("item", ItemStack.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), book.copyWithCount(1)).getOrThrow());
		return file;
	}

	public static DataResult<Found> readFound(JsonElement json) {
		if (!json.isJsonObject()) {
			return DataResult.error(() -> "a found book file is a JSON object");
		}
		JsonObject file = json.getAsJsonObject();
		Identifier book = file.has("book") ? Identifier.tryParse(file.get("book").getAsString()) : null;
		if (book == null) {
			return DataResult.error(() -> "a found book file names its \"book\"");
		}
		Map<Identifier, Float> tables = new LinkedHashMap<>();
		JsonElement lootTables = file.get("loot_tables");
		if (lootTables != null && lootTables.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : lootTables.getAsJsonObject().entrySet()) {
				Identifier table = Identifier.tryParse(entry.getKey());
				float chance = entry.getValue().getAsFloat();
				if (table != null && chance > 0) {
					tables.put(table, Math.min(1.0F, chance));
				}
			}
		} else if (lootTables != null && lootTables.isJsonArray()) {
			float chance = file.has("chance") ? Math.clamp(file.get("chance").getAsFloat(), 0.0F, 1.0F) : 1.0F;
			for (JsonElement entry : lootTables.getAsJsonArray()) {
				Identifier table = Identifier.tryParse(entry.getAsString());
				if (table != null && chance > 0) {
					tables.put(table, chance);
				}
			}
		}
		if (tables.isEmpty()) {
			return DataResult.error(() -> "a found book file names its \"loot_tables\" and a chance above 0 in each");
		}
		OptionalInt generation = file.has("generation")
			? OptionalInt.of(Math.clamp(file.get("generation").getAsInt(), 0, WrittenBookContent.MAX_GENERATION)) : OptionalInt.empty();
		return DataResult.success(new Found(book, tables, generation));
	}

	public static JsonObject writeFound(Found found) {
		JsonObject file = new JsonObject();
		file.addProperty("book", found.book().toString());
		JsonObject tables = new JsonObject();
		found.lootTables().forEach((table, chance) -> tables.addProperty(table.toString(), chance));
		file.add("loot_tables", tables);
		found.generation().ifPresent(generation -> file.addProperty("generation", generation));
		return file;
	}

	public static ItemStack withGeneration(ItemStack book, OptionalInt generation) {
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (generation.isEmpty() || content == null || content.generation() == generation.getAsInt()) {
			return book;
		}
		ItemStack out = book.copy();
		out.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(content.title(), content.author(), generation.getAsInt(), content.pages(), content.resolved()));
		return out;
	}
}
