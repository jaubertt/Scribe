package scribe;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;

public record BookSize(int pages, int chapters) {
	public static final String KEY = Scribe.MOD_ID + ":size";
	public static final Codec<BookSize> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("pages").forGetter(BookSize::pages),
		Codec.INT.optionalFieldOf("chapters", 0).forGetter(BookSize::chapters)
	).apply(i, BookSize::new));
	private static final Cache<Object, Optional<BookSize>> COUNTED = CacheBuilder.newBuilder().weakKeys().build();

	public static @Nullable BookSize of(ItemStack stack) {
		Object content = stack.has(DataComponents.WRITTEN_BOOK_CONTENT) ? stack.get(DataComponents.WRITTEN_BOOK_CONTENT)
			: stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
		if (content == null) {
			return null;
		}
		Optional<BookSize> known = COUNTED.getIfPresent(content);
		if (known == null) {
			known = Optional.ofNullable(of(texts(content)));
			COUNTED.put(content, known);
		}
		return known.orElse(null);
	}

	public static @Nullable BookSize of(List<String> pages) {
		if (pages.stream().allMatch(page -> PageText.strip(page).isBlank())) {
			return null;
		}
		return new BookSize(pages.size(), PageText.headings(pages).size());
	}

	public static void share(ItemStack book, ItemStack preview) {
		BookSize size = of(book);
		if (size != null) {
			CustomData.update(DataComponents.CUSTOM_DATA, preview, tag -> tag.store(KEY, CODEC, size));
		}
	}

	public static @Nullable BookSize shared(ItemStack preview) {
		CustomData data = preview.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return null;
		}
		Tag tag = data.copyTag().get(KEY);
		return tag == null ? null : CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
	}

	private static List<String> texts(Object content) {
		List<String> pages = new ArrayList<>();
		if (content instanceof WrittenBookContent written) {
			for (Filterable<Component> page : written.pages()) {
				pages.add(PageText.toLegacy(page.raw()));
			}
		} else if (content instanceof WritableBookContent writable) {
			for (Filterable<String> page : writable.pages()) {
				pages.add(page.raw());
			}
		}
		return pages;
	}
}
