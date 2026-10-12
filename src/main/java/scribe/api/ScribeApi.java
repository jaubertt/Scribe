package scribe.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import scribe.RealAuthor;
import scribe.Scribe;
import scribe.colors.BookDyeing;
import scribe.data.ScribeBooks;
import scribe.ink.InkPots;

public final class ScribeApi {
	public static final int VERSION = 1;
	public static final TagKey<Item> DYEABLE_BOOKS = BookDyeing.DYEABLE;
	public static final TagKey<Item> BINDABLE_BOOKS = TagKey.create(Registries.ITEM, Scribe.id("bindable_books"));

	public record Author(@Nullable UUID id, String name, boolean penName) {
	}

	private ScribeApi() {
	}

	public static Optional<Author> realAuthor(ItemStack book) {
		RealAuthor author = RealAuthor.of(book);
		return author == null ? Optional.empty() : Optional.of(new Author(author.id(), author.name(), author.penName()));
	}

	public static boolean isInkPot(ItemStack stack) {
		return InkPots.isInk(stack);
	}

	public static Optional<DyeColor> inkColor(ItemStack stack) {
		return InkPots.isInk(stack) ? Optional.ofNullable(InkPots.color(stack)) : Optional.empty();
	}

	public static Optional<ItemStack> dataPackBook(Identifier id) {
		return Optional.ofNullable(ScribeBooks.book(id));
	}

	public static Set<Identifier> dataPackBooks() {
		return ScribeBooks.ids();
	}
}
