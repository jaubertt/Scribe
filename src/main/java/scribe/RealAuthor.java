package scribe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;

public record RealAuthor(@Nullable UUID id, String name, boolean penName) {
	public static final String KEY = Scribe.MOD_ID + ":author";
	public static final Codec<RealAuthor> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.LENIENT_CODEC.optionalFieldOf("id").forGetter(author -> Optional.ofNullable(author.id())),
		Codec.STRING.optionalFieldOf("name", "").forGetter(RealAuthor::name),
		Codec.BOOL.optionalFieldOf("pen_name", false).forGetter(RealAuthor::penName)
	).apply(i, (id, name, penName) -> new RealAuthor(id.orElse(null), name, penName)));

	public RealAuthor {
		name = name == null ? "" : name;
	}

	public static @Nullable RealAuthor of(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return null;
		}
		Tag tag = data.copyTag().get(KEY);
		return tag == null ? null : CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
	}

	public static boolean isPenName(ItemStack stack) {
		RealAuthor author = of(stack);
		return author != null && author.penName();
	}

	public static boolean isPenName(String shown, String realName) {
		return !StringUtil.isBlank(shown) && !shown.strip().equalsIgnoreCase(realName);
	}

	public static ItemStack stamp(ItemStack written, Player signer) {
		return stamp(written, signer.getUUID(), signer.getGameProfile().name());
	}

	public static ItemStack stamp(ItemStack written, UUID id, String name) {
		WrittenBookContent content = written.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (content == null) {
			return written;
		}
		write(written, new RealAuthor(id, name, isPenName(content.author(), name)));
		return written;
	}

	private static void write(ItemStack stack, RealAuthor author) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.store(KEY, CODEC, author));
	}

	public static void copyPenName(ItemStack from, ItemStack preview) {
		if (isPenName(from)) {
			write(preview, new RealAuthor(null, "", true));
		}
	}

	public static boolean isAuthor(ItemStack book, UUID player, String playerName) {
		RealAuthor author = of(book);
		if (author != null && author.id() != null) {
			return author.id().equals(player);
		}
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (content == null || StringUtil.isBlank(content.author())) {
			return true;
		}
		return content.author().strip().equalsIgnoreCase(playerName);
	}

	public static boolean isAuthor(ItemStack book, Player player) {
		return isAuthor(book, player.getUUID(), player.getGameProfile().name());
	}

	public static String realName(ItemStack book) {
		RealAuthor author = of(book);
		if (author != null && author.id() != null && !StringUtil.isBlank(author.name())) {
			return author.name();
		}
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		return content == null ? "" : content.author();
	}

	public static boolean isOperator(Player player) {
		return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
	}

	public static boolean guardsColor(ItemStack book) {
		return book.has(DataComponents.WRITTEN_BOOK_CONTENT);
	}

	public static boolean mayRecolor(ItemStack book, Player player) {
		return !guardsColor(book) || isAuthor(book, player) || isOperator(player);
	}

	public static boolean hasAuthor(ItemStack book) {
		if (!guardsColor(book)) {
			return false;
		}
		RealAuthor author = of(book);
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		return (author != null && author.id() != null) || (content != null && !StringUtil.isBlank(content.author()));
	}
}
