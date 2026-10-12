package scribe.server;

import scribe.colors.BookColors;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public record ShelfShare(List<Integer> dyes, List<ItemStack> books) {
	public static final String KEY = "scribe:shelf";
	public static final ShelfShare NOTHING = new ShelfShare(List.of(), List.of());
	public static final Codec<ShelfShare> CODEC = RecordCodecBuilder.create(fields -> fields.group(
		Codec.INT.listOf().optionalFieldOf("dyes", List.of()).forGetter(ShelfShare::dyes),
		ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("books", List.of()).forGetter(ShelfShare::books)
	).apply(fields, ShelfShare::new));

	public static ShelfShare of(Container shelf, boolean withBooks) {
		List<Integer> dyes = new ArrayList<>(shelf.getContainerSize());
		List<ItemStack> books = withBooks ? new ArrayList<>(shelf.getContainerSize()) : List.of();
		for (int slot = 0; slot < shelf.getContainerSize(); slot++) {
			ItemStack stack = shelf.getItem(slot);
			dyes.add(BookColors.dyedOrZero(stack));
			if (withBooks) {
				books.add(BookPreview.of(stack));
			}
		}
		return new ShelfShare(dyes, books);
	}

	public int dye(int slot) {
		return slot >= 0 && slot < this.dyes.size() ? this.dyes.get(slot) : 0;
	}

	public ItemStack book(int slot) {
		return slot >= 0 && slot < this.books.size() ? this.books.get(slot) : ItemStack.EMPTY;
	}
}
