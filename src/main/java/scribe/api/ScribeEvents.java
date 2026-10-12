package scribe.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class ScribeEvents {
	public static final Event<BookSigned> BOOK_SIGNED = EventFactory.createArrayBacked(BookSigned.class, listeners -> (player, book, lectern) -> {
		for (BookSigned listener : listeners) {
			listener.signed(player, book, lectern);
		}
	});

	public static final Event<BookEdited> BOOK_EDITED = EventFactory.createArrayBacked(BookEdited.class, listeners -> (player, book, lectern) -> {
		for (BookEdited listener : listeners) {
			listener.edited(player, book, lectern);
		}
	});

	private ScribeEvents() {
	}

	@FunctionalInterface
	public interface BookSigned {
		void signed(ServerPlayer player, ItemStack book, @Nullable BlockPos lectern);
	}

	@FunctionalInterface
	public interface BookEdited {
		void edited(ServerPlayer player, ItemStack book, @Nullable BlockPos lectern);
	}
}
