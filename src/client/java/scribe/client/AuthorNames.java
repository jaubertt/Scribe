package scribe.client;

import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;
import scribe.RealAuthor;
import scribe.server.ServerBooks;

public final class AuthorNames {
	private AuthorNames() {
	}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> apply(stack, lines));
	}

	public static Object name(String author, boolean penName) {
		return penName ? Component.literal(author).withStyle(ChatFormatting.ITALIC) : author;
	}

	public static Component byLine(String author, boolean penName) {
		return Component.translatable("book.byAuthor", name(author, penName)).withStyle(ChatFormatting.GRAY);
	}

	public static void apply(ItemStack stack, List<Component> lines) {
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (written == null || lines.isEmpty()) {
			return;
		}
		Component name = readableName(stack, lines.getFirst());
		if (name != null) {
			lines.set(0, name);
		}
		if (RealAuthor.isPenName(stack)) {
			for (int i = 1; i < lines.size(); i++) {
				if (isByLine(lines.get(i), written.author())) {
					lines.set(i, byLine(written.author(), true));
					break;
				}
			}
		}
	}

	static boolean isByLine(Component line, String author) {
		return line.getContents() instanceof TranslatableContents t && "book.byAuthor".equals(t.getKey()) && t.getArgs().length == 1
			&& author.equals(t.getArgs()[0]);
	}

	static @Nullable Component readableName(ItemStack stack, Component first) {
		ChatFormatting stored = ServerBooks.nameColorOf(stack);
		if (stored == null || ServerBooks.nameColor(stored) == stored) {
			return null;
		}
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		if (name == null || !first.getString().equals(name.getString())) {
			return null;
		}
		ChatFormatting partner = ServerBooks.nameColor(stored);
		MutableComponent line = Component.empty().setStyle(first.getStyle());
		return line.append(Component.literal(name.getString()).withStyle(style -> style.withColor(partner).withItalic(false)));
	}
}
