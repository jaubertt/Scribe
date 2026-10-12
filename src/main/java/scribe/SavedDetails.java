package scribe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;
import scribe.network.Payloads.SignDetails;

public record SavedDetails(String title, String subtitle, String penName) {
	public static final String KEY = Scribe.MOD_ID + ":details";
	public static final SavedDetails NONE = new SavedDetails("", "", "");
	public static final Codec<SavedDetails> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.optionalFieldOf("title", "").forGetter(SavedDetails::title),
		Codec.STRING.optionalFieldOf("subtitle", "").forGetter(SavedDetails::subtitle),
		Codec.STRING.optionalFieldOf("pen_name", "").forGetter(SavedDetails::penName)
	).apply(i, SavedDetails::new));

	public SavedDetails {
		title = SignDetails.cleanTitle(title);
		subtitle = SignDetails.cleanSubtitleText(subtitle);
		penName = SignDetails.cleanPenName(penName);
	}

	public boolean isEmpty() {
		return this.title.isEmpty() && this.subtitle.isEmpty() && this.penName.isEmpty();
	}

	public SavedDetails taken(boolean subtitles, boolean penNames) {
		return new SavedDetails(this.title, subtitles ? this.subtitle : "", penNames ? this.penName : "");
	}

	public static @Nullable SavedDetails of(ItemStack stack) {
		if (!stack.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return null;
		}
		Tag tag = data.copyTag().get(KEY);
		SavedDetails details = tag == null ? null : CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
		return details == null || details.isEmpty() ? null : details;
	}

	public static boolean write(ItemStack book, SavedDetails details) {
		CustomData before = book.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		if (details.isEmpty()) {
			if (!before.copyTag().contains(KEY)) {
				return false;
			}
			CustomData.update(DataComponents.CUSTOM_DATA, book, tag -> tag.remove(KEY));
			return true;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, book, tag -> tag.store(KEY, CODEC, details));
		return !before.equals(book.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY));
	}

	public static void remove(ItemStack book) {
		CustomData data = book.get(DataComponents.CUSTOM_DATA);
		if (data != null && data.copyTag().contains(KEY)) {
			CustomData.update(DataComponents.CUSTOM_DATA, book, (CompoundTag tag) -> tag.remove(KEY));
		}
	}
}
