package scribe.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.function.IntPredicate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import scribe.network.LibrarianReadingPayload;

public final class LibrarianBooks {
	public static final int TURN_MIN = 40;
	public static final int TURN_MAX = 70;
	public static final int FIRST_TURN = 20;

	private static final Int2ObjectMap<Reader> READERS = new Int2ObjectOpenHashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();

	public static final class Reader {
		ItemStack book = ItemStack.EMPTY;
		boolean reading;
		final BookMotion motion = new BookMotion(false);
		int nextTurn = FIRST_TURN;

		public ItemStack book() {
			return this.book;
		}

		public boolean reading() {
			return this.reading;
		}

		public BookMotion motion() {
			return this.motion;
		}

		boolean done() {
			return !this.reading && this.motion.open(0.0F) == 0.0F && this.motion.open(1.0F) == 0.0F;
		}
	}

	private LibrarianBooks() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(LibrarianReadingPayload.TYPE, (payload, context) ->
			set(payload.entityId(), payload.reading(), payload.book()));
		ClientTickEvents.END_LEVEL_TICK.register(level -> tick(id -> level.getEntity(id) != null));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	public static void set(int entityId, boolean reading, ItemStack book) {
		Reader reader = READERS.get(entityId);
		if (reading) {
			if (reader == null) {
				reader = new Reader();
				READERS.put(entityId, reader);
			}
			if (!reader.reading) {
				reader.nextTurn = FIRST_TURN;
			}
			reader.book = book;
			reader.reading = true;
			reader.motion.setOpen(true);
		} else if (reader != null) {
			reader.reading = false;
			reader.motion.setOpen(false);
		}
	}

	public static @Nullable Reader of(int entityId) {
		return READERS.get(entityId);
	}

	public static void clear() {
		READERS.clear();
	}

	public static int count() {
		return READERS.size();
	}

	public static void tick(IntPredicate exists) {
		READERS.int2ObjectEntrySet().removeIf(entry -> {
			Reader reader = entry.getValue();
			reader.motion.tick();
			if (reader.reading && reader.motion.open(1.0F) >= 1.0F && --reader.nextTurn <= 0) {
				reader.motion.turn(1);
				reader.nextTurn = TURN_MIN + RANDOM.nextInt(TURN_MAX - TURN_MIN + 1);
			}
			return reader.done() || !exists.test(entry.getIntKey());
		});
	}
}
