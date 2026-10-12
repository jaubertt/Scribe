package scribe.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.function.IntPredicate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class ReadingPose {
	private static final Int2ObjectMap<Held> READING = new Int2ObjectOpenHashMap<>();
	private static boolean localPosed;
	private static @Nullable Held local;
	private static long pausedTickAt;
	private static final float TICK_NANOS = 50_000_000.0F;

	static final class Held {
		boolean offhand;
		boolean reading;
		final BookMotion motion = new BookMotion(false);

		void read(boolean offhand) {
			this.offhand = offhand;
			this.reading = true;
			this.motion.setOpen(true);
		}

		void stop() {
			this.reading = false;
			this.motion.setOpen(false);
		}

		boolean done() {
			return !this.reading && this.motion.open(0.0F) == 0.0F && this.motion.open(1.0F) == 0.0F;
		}
	}

	private ReadingPose() {
	}

	public static void init() {
		ClientTickEvents.END_LEVEL_TICK.register(level -> tick(id -> level.getEntity(id) != null));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.isPaused()) {
				tickLocal();
				pausedTickAt = System.nanoTime();
			}
		});
	}

	public static void set(int entityId, boolean reading, boolean offhand) {
		Held held = READING.get(entityId);
		if (reading) {
			if (held == null) {
				held = new Held();
				READING.put(entityId, held);
			}
			held.read(offhand);
		} else if (held != null) {
			held.stop();
		}
	}

	static void setLocal(boolean reading, boolean offhand, boolean posed) {
		if (reading) {
			localPosed = posed;
			if (local == null) {
				local = new Held();
			}
			local.read(offhand);
		} else if (local != null) {
			local.stop();
		}
	}

	public static void turn(int entityId, int leaves) {
		Held held = READING.get(entityId);
		if (held != null && held.reading) {
			held.motion.turn(leaves);
		}
	}

	static void turnLocal(int leaves) {
		if (local != null && local.reading) {
			local.motion.turn(leaves);
		}
	}

	public static void clear() {
		READING.clear();
		localPosed = false;
		local = null;
		FirstPersonBook.forget();
	}

	static void tick(IntPredicate exists) {
		tickLocal();
		READING.int2ObjectEntrySet().removeIf(entry -> {
			Held held = entry.getValue();
			held.motion.tick();
			return held.done() || !exists.test(entry.getIntKey());
		});
	}

	static void tickLocal() {
		if (local != null) {
			local.motion.tick();
			if (local.done()) {
				local = null;
			}
		}
	}

	public static float partialTick(Entity entity, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (pausedTickAt == 0L || !minecraft.isPaused() || minecraft.player == null || entity.getId() != minecraft.player.getId()) {
			return partialTicks;
		}
		return pausedFraction(System.nanoTime() - pausedTickAt);
	}

	static float pausedFraction(long nanosSinceTick) {
		return Math.clamp(nanosSinceTick / TICK_NANOS, 0.0F, 1.0F);
	}

	public static int stateOf(Entity entity) {
		Held held = heldBy(entity);
		return held == null ? 0 : held.offhand ? 2 : 1;
	}

	public static boolean reads(Entity entity) {
		Held held = heldBy(entity);
		return held != null && held.reading;
	}

	public static boolean posed(Entity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		return localPosed && local != null && minecraft.player != null && entity.getId() == minecraft.player.getId();
	}

	public static @Nullable BookMotion motionOf(Entity entity) {
		Held held = heldBy(entity);
		return held == null ? null : held.motion;
	}

	private static @Nullable Held heldBy(Entity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && entity.getId() == minecraft.player.getId()) {
			return local;
		}
		return READING.get(entity.getId());
	}
}
