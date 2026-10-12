package scribe.server;

import java.util.List;
import scribe.BookSize;
import scribe.RealAuthor;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

public final class BookPreview {
	private static final List<DataComponentType<?>> KEPT = List.of(
		DataComponents.CUSTOM_NAME, DataComponents.ITEM_NAME, DataComponents.DYED_COLOR,
		DataComponents.STORED_ENCHANTMENTS, DataComponents.LORE, DataComponents.RARITY, DataComponents.CUSTOM_MODEL_DATA,
		DataComponents.TOOLTIP_DISPLAY);

	private BookPreview() {
	}

	public static ItemStack of(ItemStack stack) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack preview = new ItemStack(stack.getItem());
		for (DataComponentType<?> type : KEPT) {
			preview.copyFrom(type, stack);
		}
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (written != null) {
			preview.set(DataComponents.WRITTEN_BOOK_CONTENT,
				new WrittenBookContent(written.title(), written.author(), written.generation(), List.of(), written.resolved()));
			RealAuthor.copyPenName(stack, preview);
		}
		BookSize.share(stack, preview);
		return preview;
	}

	public static int pageCount(ItemStack stack) {
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (written != null) {
			return written.pages().size();
		}
		WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
		return writable != null ? writable.pages().size() : 0;
	}
}
