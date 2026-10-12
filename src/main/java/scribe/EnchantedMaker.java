package scribe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

public record EnchantedMaker(@Nullable UUID id, String name) {
	public static final String KEY = Scribe.MOD_ID + ":maker";
	public static final Codec<EnchantedMaker> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.LENIENT_CODEC.optionalFieldOf("id").forGetter(maker -> Optional.ofNullable(maker.id())),
		Codec.STRING.optionalFieldOf("name", "").forGetter(EnchantedMaker::name)
	).apply(i, (id, name) -> new EnchantedMaker(id.orElse(null), name)));

	public EnchantedMaker {
		name = name == null ? "" : name;
	}

	public static @Nullable EnchantedMaker of(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return null;
		}
		Tag tag = data.copyTag().get(KEY);
		EnchantedMaker maker = tag == null ? null : CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
		return maker == null || StringUtil.isBlank(maker.name()) ? null : maker;
	}

	public static ItemStack stamp(ItemStack book, Player maker) {
		return stamp(book, maker.getUUID(), maker.getGameProfile().name());
	}

	public static ItemStack stamp(ItemStack book, UUID id, String name) {
		if (EnchantedBooks.is(book)) {
			EnchantedMaker maker = new EnchantedMaker(id, name);
			CustomData.update(DataComponents.CUSTOM_DATA, book, tag -> tag.store(KEY, CODEC, maker));
		}
		return book;
	}

	public static ItemStack forget(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null && data.copyTag().contains(KEY)) {
			CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(KEY));
		}
		return stack;
	}

	public static boolean madeAtTable(boolean wasBook, ItemStack after) {
		return wasBook && EnchantedBooks.is(after);
	}

	public static boolean madeAtAnvil(ItemStack input, ItemStack addition, ItemStack result) {
		return EnchantedBooks.is(input) && EnchantedBooks.is(result) && EnchantedBooks.is(addition)
			&& !addition.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
	}

	public static ItemStack ground(ItemStack result) {
		return result.is(Items.BOOK) ? forget(result) : result;
	}
}
