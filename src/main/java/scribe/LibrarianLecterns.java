package scribe;

import scribe.access.LecternAccess;
import scribe.access.LecternWatch;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.jspecify.annotations.Nullable;

public final class LibrarianLecterns {
	private static final Set<LecternBlockEntity> HELD = Collections.newSetFromMap(new IdentityHashMap<>());
	public static final float STAYS_OPEN = 0.5F;
	public static final int WAIT_TICKS = 1200;
	private static final RandomSource DICE = RandomSource.create();
	public static BooleanSupplier staysOpen = () -> DICE.nextFloat() < STAYS_OPEN;
	private static int leavesTurned;

	private LibrarianLecterns() {
	}

	public static final class Hold {
		private final Villager librarian;
		private BehaviorControl<?> work;
		private boolean done;
		private long waitingSince = -1L;

		Hold(Villager librarian, BehaviorControl<?> work) {
			this.librarian = librarian;
			this.work = work;
		}

		public Villager librarian() {
			return this.librarian;
		}

		public boolean done() {
			return this.done;
		}

		public boolean waiting() {
			return this.waitingSince >= 0L;
		}
	}

	public enum Start {
		OPEN,
		PAGE,
		NONE
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick(server.getPlayerList().getPlayers()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HELD.clear());
	}

	public static Start start(boolean enabled, boolean librarian, boolean hasBook, boolean closed, boolean openedByIt) {
		if (!enabled || !librarian || !hasBook) {
			return Start.NONE;
		}
		if (closed) {
			return Start.OPEN;
		}
		return openedByIt ? Start.PAGE : Start.NONE;
	}

	public static boolean stopsWaiting(boolean gone, boolean atWork, long waited) {
		return gone || !atWork || waited >= WAIT_TICKS;
	}

	public static boolean workOver(boolean working, boolean removed, boolean alive) {
		return !working || removed || !alive;
	}

	public static boolean closesNow(int readers) {
		return readers <= 0;
	}

	public static boolean savedClosed(boolean closed, boolean heldByLibrarian) {
		return closed || heldByLibrarian;
	}

	public static boolean isLibrarian(Villager villager) {
		return isLibrarian(villager.getVillagerData().profession());
	}

	public static boolean isLibrarian(Holder<VillagerProfession> profession) {
		return profession.is(VillagerProfession.LIBRARIAN);
	}

	public static boolean startsWork(ServerLevel level, Villager villager, BehaviorControl<?> work) {
		boolean enabled = ClosedLecterns.enabled();
		boolean librarian = isLibrarian(villager);
		LecternBlockEntity lectern = enabled && librarian ? ownLectern(level, villager) : null;
		if (lectern == null) {
			return false;
		}
		LecternAccess access = (LecternAccess) lectern;
		Hold held = access.scribe$librarian();
		switch (start(enabled, librarian, lectern.hasBook(), ClosedLecterns.isClosed(lectern), held != null && held.librarian == villager)) {
			case OPEN -> {
				ClosedLecterns.change(lectern, false, ClosedLecterns.sound(false));
				access.scribe$setLibrarian(new Hold(villager, work));
				HELD.add(lectern);
				return true;
			}
			case PAGE -> {
				held.work = work;
				held.done = false;
				held.waitingSince = -1L;
				HELD.add(lectern);
				turnLeaf(level, lectern.getBlockPos());
				return false;
			}
			default -> {
				return false;
			}
		}
	}

	private static @Nullable LecternBlockEntity ownLectern(ServerLevel level, Villager villager) {
		Brain<Villager> brain = villager.getBrain();
		if (!brain.hasMemoryValue(MemoryModuleType.JOB_SITE)) {
			return null;
		}
		GlobalPos site = brain.getMemory(MemoryModuleType.JOB_SITE).orElse(null);
		if (site == null || site.dimension() != level.dimension()) {
			return null;
		}
		BlockPos pos = site.pos();
		return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof LecternBlockEntity lectern ? lectern : null;
	}

	static void turnLeaf(ServerLevel level, BlockPos pos) {
		level.levelEvent(null, LevelEvent.SOUND_PAGE_TURN, pos, LecternWatch.LEAF_TURN);
		leavesTurned++;
	}

	public static int leavesTurned() {
		return leavesTurned;
	}

	public static void tick(Iterable<? extends ServerPlayer> players) {
		if (HELD.isEmpty()) {
			return;
		}
		for (Iterator<LecternBlockEntity> it = HELD.iterator(); it.hasNext();) {
			LecternBlockEntity lectern = it.next();
			LecternAccess access = (LecternAccess) lectern;
			Hold hold = access.scribe$librarian();
			if (hold == null || lectern.isRemoved() || lectern.getLevel() == null) {
				it.remove();
				continue;
			}
			boolean gone = hold.librarian.isRemoved() || !hold.librarian.isAlive() || !isLibrarian(hold.librarian);
			long now = lectern.getLevel().getGameTime();
			if (hold.waiting()) {
				if (!stopsWaiting(gone, hold.librarian.getBrain().isActive(Activity.WORK), now - hold.waitingSince)) {
					continue;
				}
				hold.waitingSince = -1L;
				hold.done = true;
			} else if (!hold.done) {
				if (!workOver(hold.work.getStatus() == Behavior.Status.RUNNING, hold.librarian.isRemoved(), hold.librarian.isAlive())) {
					continue;
				}
				if (!gone && staysOpen.getAsBoolean()) {
					hold.waitingSince = now;
					continue;
				}
			}
			hold.done = true;
			if (!closesNow(readers(players, lectern))) {
				continue;
			}
			it.remove();
			access.scribe$setLibrarian(null);
			if (lectern.hasBook() && !ClosedLecterns.isClosed(lectern)) {
				ClosedLecterns.change(lectern, true, ClosedLecterns.enabled() ? ClosedLecterns.sound(true) : null);
			}
		}
	}

	public static int readers(Iterable<? extends ServerPlayer> players, LecternBlockEntity lectern) {
		int count = 0;
		for (ServerPlayer reader : ClosedLecterns.readers(players, lectern)) {
			if (!reader.isSpectator()) {
				count++;
			}
		}
		return count;
	}

	public static boolean held(LecternBlockEntity lectern) {
		return ((LecternAccess) lectern).scribe$librarian() != null;
	}

	public static boolean waiting(LecternBlockEntity lectern) {
		Hold hold = ((LecternAccess) lectern).scribe$librarian();
		return hold != null && hold.waiting();
	}
}
