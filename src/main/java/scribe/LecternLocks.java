package scribe;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.jspecify.annotations.Nullable;
import scribe.access.LecternMenuAccess;
import scribe.config.ScribeConfig;
import scribe.network.Payloads;

public final class LecternLocks {
	public static final long IDLE_TICKS = 20L * 60 * 5;

	private record Writer(UUID id, String name, WeakReference<ServerPlayer> player, int containerId, long lastActive) {
		Writer(ServerPlayer player, int containerId, long lastActive) {
			this(player.getUUID(), player.getGameProfile().name(), new WeakReference<>(player), containerId, lastActive);
		}

		Writer typedAt(long now) {
			return new Writer(this.id, this.name, this.player, this.containerId, now);
		}

		@Nullable ServerPlayer inGame() {
			ServerPlayer player = this.player.get();
			return player == null || player.isRemoved() ? null : player;
		}
	}

	private record Reader(UUID id, WeakReference<ServerPlayer> player, int containerId) {
		Reader(ServerPlayer player, int containerId) {
			this(player.getUUID(), new WeakReference<>(player), containerId);
		}

		@Nullable ServerPlayer atLectern(LecternBlockEntity lectern) {
			ServerPlayer player = this.player.get();
			return player != null && !player.isRemoved() && player.containerMenu instanceof LecternMenu menu && menu.containerId == this.containerId
				&& ((LecternMenuAccess) menu).scribe$lectern() == lectern ? player : null;
		}
	}

	private static final Map<LecternBlockEntity, Writer> WRITERS = new WeakHashMap<>();
	private static final Map<LecternBlockEntity, List<Reader>> READERS = new WeakHashMap<>();
	private static final Map<LecternBlockEntity, Integer> SAVING = new WeakHashMap<>();
	private static final Set<LecternBlockEntity> PASS_AFTER_SAVES = Collections.newSetFromMap(new WeakHashMap<>());

	private LecternLocks() {
	}

	public static @Nullable String writerOtherThan(LecternBlockEntity lectern, Player player) {
		return writerOtherThan(lectern, player, now(lectern));
	}

	public static @Nullable String writerOtherThan(LecternBlockEntity lectern, Player player, long now) {
		if (!ScribeConfig.get().lecternEditingOn() || !lectern.getBook().has(DataComponents.WRITABLE_BOOK_CONTENT)) {
			return null;
		}
		Writer writer = valid(lectern, now);
		return writer == null || writer.id().equals(player.getUUID()) ? null : writer.name();
	}

	public static boolean refused(LecternBlockEntity lectern, ServerPlayer player) {
		String writer = writerOtherThan(lectern, player);
		if (writer == null) {
			return false;
		}
		player.sendOverlayMessage(Component.translatableWithFallback("scribe.lectern.writing", "%s is writing in this book", writer));
		return true;
	}

	public static void menuOpened(LecternBlockEntity lectern, ServerPlayer player, int containerId) {
		if (!applies(lectern, player) || !ServerPlayNetworking.canSend(player, Payloads.LecternWriter.TYPE)) {
			return;
		}
		String writer = claim(lectern, player, containerId, now(lectern));
		ServerPlayNetworking.send(player, new Payloads.LecternWriter(containerId, writer));
	}

	public static boolean applies(LecternBlockEntity lectern, ServerPlayer player) {
		return ScribeConfig.get().lecternEditingOn() && lectern.getBook().has(DataComponents.WRITABLE_BOOK_CONTENT) && player.mayBuild();
	}

	public static String claim(LecternBlockEntity lectern, ServerPlayer player, int containerId, long now) {
		Writer held = WRITERS.get(lectern);
		Writer writer = valid(lectern, now);
		if (writer != null && !writer.id().equals(player.getUUID())) {
			List<Reader> readers = READERS.computeIfAbsent(lectern, l -> new ArrayList<>());
			readers.removeIf(reader -> reader.id().equals(player.getUUID()));
			readers.add(new Reader(player, containerId));
			return writer.name();
		}
		take(lectern, player, containerId, now);
		if (held != null && !held.id().equals(player.getUUID())) {
			tellPenTaken(lectern, held, player.getGameProfile().name());
		}
		return "";
	}

	public static boolean mayWrite(LecternBlockEntity lectern, ServerPlayer player, int containerId) {
		long now = now(lectern);
		Writer writer = valid(lectern, now);
		if (writer == null || writer.id().equals(player.getUUID())) {
			take(lectern, player, containerId, now);
			return true;
		}
		if (ServerPlayNetworking.canSend(player, Payloads.LecternWriter.TYPE)) {
			ServerPlayNetworking.send(player, new Payloads.LecternWriter(containerId, writer.name()));
		}
		return false;
	}

	public static void typed(LecternBlockEntity lectern, ServerPlayer player) {
		Writer writer = WRITERS.get(lectern);
		if (writer != null && writer.id().equals(player.getUUID())) {
			WRITERS.put(lectern, writer.typedAt(now(lectern)));
		}
	}

	public static void released(LecternBlockEntity lectern, ServerPlayer player) {
		List<Reader> readers = READERS.get(lectern);
		if (readers != null) {
			readers.removeIf(reader -> reader.id().equals(player.getUUID()));
		}
		Writer writer = WRITERS.get(lectern);
		if (writer != null && writer.id().equals(player.getUUID())) {
			WRITERS.remove(lectern);
			if (SAVING.getOrDefault(lectern, 0) > 0) {
				PASS_AFTER_SAVES.add(lectern);
			} else {
				passOn(lectern);
			}
		}
	}

	public static void saving(LecternBlockEntity lectern) {
		SAVING.merge(lectern, 1, Integer::sum);
	}

	public static void saved(LecternBlockEntity lectern) {
		int left = SAVING.getOrDefault(lectern, 0) - 1;
		if (left > 0) {
			SAVING.put(lectern, left);
			return;
		}
		SAVING.remove(lectern);
		if (PASS_AFTER_SAVES.remove(lectern)) {
			passOn(lectern);
		}
	}

	private static void take(LecternBlockEntity lectern, ServerPlayer player, int containerId, long now) {
		Writer before = WRITERS.put(lectern, new Writer(player, containerId, now));
		List<Reader> readers = READERS.get(lectern);
		if (readers != null) {
			readers.removeIf(reader -> reader.id().equals(player.getUUID()));
		}
		if (before == null || !before.id().equals(player.getUUID())) {
			tellReaders(lectern, player.getGameProfile().name());
		}
	}

	private static void passOn(LecternBlockEntity lectern) {
		List<Reader> readers = READERS.get(lectern);
		if (readers == null || valid(lectern, now(lectern)) != null) {
			return;
		}
		for (Iterator<Reader> line = readers.iterator(); line.hasNext();) {
			Reader reader = line.next();
			ServerPlayer next = reader.atLectern(lectern);
			line.remove();
			if (next != null && applies(lectern, next) && ServerPlayNetworking.canSend(next, Payloads.LecternWriter.TYPE)) {
				take(lectern, next, reader.containerId(), now(lectern));
				next.containerMenu.broadcastChanges();
				ServerPlayNetworking.send(next, new Payloads.LecternWriter(reader.containerId(), ""));
				return;
			}
		}
	}

	private static void tellReaders(LecternBlockEntity lectern, String writer) {
		List<Reader> readers = READERS.get(lectern);
		if (readers == null) {
			return;
		}
		List<Reader> gone = new ArrayList<>();
		for (Reader reader : List.copyOf(readers)) {
			ServerPlayer player = reader.atLectern(lectern);
			if (player == null) {
				gone.add(reader);
			} else if (ServerPlayNetworking.canSend(player, Payloads.LecternWriter.TYPE)) {
				ServerPlayNetworking.send(player, new Payloads.LecternWriter(reader.containerId(), writer));
			}
		}
		readers.removeAll(gone);
	}

	public static @Nullable String writerOf(LecternBlockEntity lectern) {
		Writer writer = valid(lectern, now(lectern));
		return writer == null ? null : writer.name();
	}

	private static @Nullable Writer valid(LecternBlockEntity lectern, long now) {
		Writer writer = WRITERS.get(lectern);
		if (writer == null) {
			return null;
		}
		ServerPlayer player = writer.inGame();
		boolean inLectern = player != null && player.containerMenu instanceof LecternMenu menu && ((LecternMenuAccess) menu).scribe$lectern() == lectern;
		if (!inLectern || now - writer.lastActive() > IDLE_TICKS) {
			return null;
		}
		return writer;
	}

	private static void tellPenTaken(LecternBlockEntity lectern, Writer former, String newWriter) {
		ServerPlayer player = former.inGame();
		if (player != null && player.containerMenu instanceof LecternMenu menu && menu.containerId == former.containerId()
			&& ServerPlayNetworking.canSend(player, Payloads.LecternWriter.TYPE)) {
			ServerPlayNetworking.send(player, new Payloads.LecternWriter(former.containerId(), newWriter));
		}
	}

	private static long now(LecternBlockEntity lectern) {
		return lectern.getLevel() == null ? 0 : lectern.getLevel().getGameTime();
	}
}
