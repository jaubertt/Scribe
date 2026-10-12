package scribe;

import scribe.access.LecternAccess;
import scribe.access.LecternMenuAccess;
import scribe.config.ScribeConfig;
import scribe.network.Payloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public final class ClosedLecterns {
	public static final String KEY = Scribe.MOD_ID + ":closed";
	public static final int BUTTON_OPEN = 4;
	public static final int BUTTON_CLOSE = 5;

	private ClosedLecterns() {
	}

	public static boolean enabled() {
		return ScribeConfig.get().closedLecternsOn();
	}

	public static boolean startsClosed() {
		return enabled();
	}

	public static boolean isClosed(LecternBlockEntity lectern) {
		return ((LecternAccess) lectern).scribe$isClosed();
	}

	public static boolean shownClosed(LecternBlockEntity lectern) {
		return enabled() && lectern.hasBook() && isClosed(lectern);
	}

	public static boolean hasCover(ItemStack book) {
		return book.has(DataComponents.WRITTEN_BOOK_CONTENT) || EnchantedBooks.is(book);
	}

	public static boolean toggle(Level level, BlockPos pos, LecternBlockEntity lectern, Player player) {
		if (!enabled() || player.isSpectator() || !player.mayBuild() || !lectern.hasBook()) {
			return false;
		}
		boolean closing = !isClosed(lectern);
		change(lectern, closing, sound(closing));
		level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
		return true;
	}

	public static SoundEvent sound(boolean closing) {
		return closing ? SoundEvents.BOOK_PUT : SoundEvents.BOOK_PAGE_TURN;
	}

	public static boolean turnsPage(int buttonId) {
		return buttonId == LecternMenu.BUTTON_PREV_PAGE || buttonId == LecternMenu.BUTTON_NEXT_PAGE
			|| buttonId >= LecternMenu.BUTTON_PAGE_JUMP_RANGE_START;
	}

	public static boolean read(LecternBlockEntity lectern, Player reader, boolean pageTurned) {
		if (!enabled() || !lectern.hasBook() || !isClosed(lectern) || reader.isSpectator()) {
			return false;
		}
		change(lectern, false, pageTurned ? null : sound(false));
		return true;
	}

	public static boolean closeAtCover(LecternBlockEntity lectern, LecternMenu menu, Player reader) {
		if (!enabled() || !lectern.hasBook() || !hasCover(lectern.getBook()) || reader.isSpectator()) {
			return false;
		}
		menu.setData(0, 0);
		if (!isClosed(lectern)) {
			change(lectern, true, sound(true));
		}
		return true;
	}

	public static boolean button(LecternBlockEntity lectern, LecternMenu menu, Player player, int buttonId) {
		if (buttonId == BUTTON_OPEN) {
			read(lectern, player, false);
			return true;
		}
		if (buttonId == BUTTON_CLOSE) {
			if (closeAtCover(lectern, menu, player)) {
				return true;
			}
			if (player instanceof ServerPlayer server) {
				tell(server, menu.containerId, lectern);
			}
			return false;
		}
		return false;
	}

	public static void menuOpened(LecternBlockEntity lectern, ServerPlayer player, int containerId, boolean hasScribe) {
		if (hasScribe) {
			tell(player, containerId, lectern);
		} else {
			read(lectern, player, false);
		}
	}

	public static boolean hasScribe(ServerPlayer player) {
		try {
			return ServerPlayNetworking.canSend(player, Payloads.LecternState.TYPE);
		} catch (RuntimeException noConnection) {
			return false;
		}
	}

	public static void signed(LecternBlockEntity lectern, Player player) {
		if (enabled() && lectern.hasBook() && !isClosed(lectern)) {
			change(lectern, true, sound(true));
		}
	}

	static void change(LecternBlockEntity lectern, boolean closed, @Nullable SoundEvent sound) {
		((LecternAccess) lectern).scribe$setClosed(closed);
		Level level = lectern.getLevel();
		if (level == null) {
			return;
		}
		if (sound != null) {
			level.playSound(null, lectern.getBlockPos(), sound, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		if (level instanceof ServerLevel server) {
			for (ServerPlayer reader : readers(server.players(), lectern)) {
				if (hasScribe(reader)) {
					reader.containerMenu.broadcastChanges();
					tell(reader, reader.containerMenu.containerId, lectern);
				}
			}
		}
	}

	public static List<ServerPlayer> readers(Iterable<? extends ServerPlayer> players, LecternBlockEntity lectern) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (player.containerMenu instanceof LecternMenu menu && ((LecternMenuAccess) menu).scribe$lectern() == lectern) {
				out.add(player);
			}
		}
		return out;
	}

	public static Payloads.LecternState state(int containerId, LecternBlockEntity lectern) {
		return new Payloads.LecternState(containerId, shownClosed(lectern), enabled());
	}

	private static void tell(ServerPlayer player, int containerId, LecternBlockEntity lectern) {
		if (hasScribe(player)) {
			ServerPlayNetworking.send(player, state(containerId, lectern));
		}
	}

	public static void save(ValueOutput output, boolean closed, boolean hasBook) {
		if (closed && hasBook) {
			output.putBoolean(KEY, true);
		}
	}

	public static boolean load(ValueInput input) {
		return input.getBooleanOr(KEY, false);
	}

	public static void share(CompoundTag tag, boolean closed, boolean hasBook) {
		if (closed && hasBook && enabled()) {
			tag.putBoolean(KEY, true);
		}
	}
}
