package scribe.client;

import scribe.client.memory.BookDetails;
import scribe.client.memory.PageMemory;
import scribe.client.screen.QuillCover;
import scribe.server.ServerBooks;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import org.jspecify.annotations.Nullable;

public final class SubtitleTooltip {
	private SubtitleTooltip() {
	}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (stack.has(DataComponents.WRITABLE_BOOK_CONTENT) && !lines.isEmpty()) {
				addPlannedDetails(stack, lines);
				return;
			}
			if (!stack.has(DataComponents.LORE) || lines.size() < 2) {
				return;
			}
			List<Component> subtitle = new ArrayList<>();
			Iterator<Component> it = lines.iterator();
			boolean first = true;
			while (it.hasNext()) {
				Component line = it.next();
				if (first) {
					first = false;
					continue;
				}
				if (ServerBooks.SUBTITLE_MARK.equals(line.getStyle().getInsertion())) {
					subtitle.add(line);
					it.remove();
				}
			}
			if (!subtitle.isEmpty()) {
				lines.addAll(1, subtitle);
			}
		});
	}

	private static @Nullable WritableBookContent lastContent;
	private static @Nullable String lastKey;

	private static void addPlannedDetails(ItemStack stack, List<Component> lines) {
		WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
		if (content == null) {
			return;
		}
		if (content != lastContent) {
			lastContent = content;
			lastKey = PageMemory.keyFor(stack);
		}
		boolean withScribe = BookNet.scribeOnServer();
		QuillCover.planned(stack, BookDetails.get().get(lastKey)).ifPresent(details -> {
			List<Component> add = new ArrayList<>();
			if (!details.title().isBlank()) {
				add.add(Component.literal(details.title()).withStyle(ChatFormatting.GRAY));
			}
			if (!details.subtitle().isBlank() && (!withScribe || BookNet.subtitles())) {
				add.add(Component.literal(details.subtitle()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
			if (!details.penName().isBlank() && (!withScribe || BookNet.penNames())) {
				net.minecraft.world.entity.player.Player player = net.minecraft.client.Minecraft.getInstance().player;
				boolean pen = player == null || scribe.RealAuthor.isPenName(details.penName(), player.getGameProfile().name());
				add.add(AuthorNames.byLine(details.penName(), pen));
			}
			if (!add.isEmpty()) {
				add.add(Component.translatable("scribe.tooltip.not_signed").withStyle(ChatFormatting.DARK_GRAY));
				lines.addAll(1, add);
			}
		});
	}
}
