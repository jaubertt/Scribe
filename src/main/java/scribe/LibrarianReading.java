package scribe;

import scribe.config.ScribeConfig;
import scribe.mixin.AbstractVillagerAccessor;
import scribe.network.LibrarianReadingPayload;
import scribe.server.BookPreview;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jspecify.annotations.Nullable;

public final class LibrarianReading {
	public static final int CHECK_INTERVAL = 20;
	public static final int START_ONE_IN = 72;
	public static final int MIN_TICKS = 300;
	public static final int MAX_TICKS = 400;
	public static final int REST_TICKS = 300;

	public enum Stop {
		GONE,
		SETTING_OFF,
		NOT_LIBRARIAN,
		SLEEPING,
		PANIC,
		NOT_AT_WORK,
		TRADING,
		WORKING,
		IN_WATER,
		DONE
	}

	private static final class Watched {
		final Villager villager;
		int lastTick = -1;
		int restUntil;
		@Nullable ItemStack book;
		int readUntil;
		@Nullable BehaviorControl<?> work;
		boolean paused;

		Watched(Villager villager) {
			this.villager = villager;
		}
	}

	private static final Map<Villager, Watched> WATCHED = new IdentityHashMap<>();
	private static final List<Set<Watched>> GROUPS = new ArrayList<>();
	private static final Set<Watched> READING = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final RandomSource RANDOM = RandomSource.create();
	private static int serverTick;
	private static @Nullable Stop lastStop;

	static {
		for (int i = 0; i < CHECK_INTERVAL; i++) {
			GROUPS.add(Collections.newSetFromMap(new IdentityHashMap<>()));
		}
	}

	private LibrarianReading() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager villager) {
				watch(villager);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Villager villager) {
				forget(villager);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		EntityTrackingEvents.START_TRACKING.register((entity, viewer) -> {
			LibrarianReadingPayload state = trackingPayload(entity, viewer);
			if (state != null) {
				ServerPlayNetworking.send(viewer, state);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	public static @Nullable Stop why(boolean gone, boolean librarian, boolean sleeping, boolean panicking, boolean atWork, boolean trading,
		boolean showingTrade, boolean working, boolean inWater) {
		if (gone) {
			return Stop.GONE;
		}
		if (!librarian) {
			return Stop.NOT_LIBRARIAN;
		}
		if (sleeping) {
			return Stop.SLEEPING;
		}
		if (panicking) {
			return Stop.PANIC;
		}
		if (!atWork) {
			return Stop.NOT_AT_WORK;
		}
		if (trading || showingTrade) {
			return Stop.TRADING;
		}
		if (working) {
			return Stop.WORKING;
		}
		return inWater ? Stop.IN_WATER : null;
	}

	public static int duration(int roll) {
		return MIN_TICKS + Math.clamp(roll, 0, MAX_TICKS - MIN_TICKS);
	}

	public static ItemStack chooseBook(@Nullable Collection<ItemStack> results, int pick) {
		List<ItemStack> books = new ArrayList<>();
		if (results != null) {
			for (ItemStack result : results) {
				if (sellsEnchantedBook(result)) {
					books.add(result);
				}
			}
		}
		if (books.isEmpty()) {
			return new ItemStack(Items.BOOK);
		}
		return BookPreview.of(books.get(Math.floorMod(pick, books.size())));
	}

	public static boolean sellsEnchantedBook(ItemStack result) {
		return result.is(Items.ENCHANTED_BOOK) && !result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
	}

	public static @Nullable List<ItemStack> offerResults(Villager villager) {
		List<MerchantOffer> offers = ((AbstractVillagerAccessor) villager).scribe$offers();
		if (offers == null) {
			return null;
		}
		List<ItemStack> results = new ArrayList<>(offers.size());
		for (MerchantOffer offer : offers) {
			results.add(offer.getResult());
		}
		return results;
	}

	public static int group(int entityId) {
		return Math.floorMod(entityId, CHECK_INTERVAL);
	}

	public static @Nullable Stop why(Villager villager) {
		Watched watched = WATCHED.get(villager);
		return why(villager, watched != null && running(watched.work));
	}

	private static @Nullable Stop why(Villager villager, boolean working) {
		Brain<Villager> brain = villager.getBrain();
		return why(villager.isRemoved() || !villager.isAlive(), LibrarianLecterns.isLibrarian(villager), villager.isSleeping(), brain.isActive(Activity.PANIC),
			!villager.isNoAi() && brain.isActive(Activity.WORK), villager.isTrading(), !villager.getMainHandItem().isEmpty(), working, villager.isInWater());
	}

	private static boolean running(@Nullable BehaviorControl<?> work) {
		return work != null && work.getStatus() == Behavior.Status.RUNNING;
	}

	public static void watch(Villager villager) {
		if (!WATCHED.containsKey(villager)) {
			Watched watched = new Watched(villager);
			WATCHED.put(villager, watched);
			GROUPS.get(group(villager.getId())).add(watched);
		}
	}

	public static void forget(Villager villager) {
		Watched watched = WATCHED.remove(villager);
		if (watched != null) {
			GROUPS.get(group(villager.getId())).remove(watched);
			end(watched);
		}
	}

	private static void clear() {
		WATCHED.clear();
		READING.clear();
		for (Set<Watched> group : GROUPS) {
			group.clear();
		}
	}

	public static void tick() {
		serverTick++;
		boolean enabled = ScribeConfig.get().readingLibrariansOn();
		if (!READING.isEmpty()) {
			for (Watched watched : List.copyOf(READING)) {
				if (!enabled) {
					stop(watched, Stop.SETTING_OFF);
				} else {
					follow(watched);
				}
			}
		}
		if (!enabled) {
			return;
		}
		for (Watched watched : List.copyOf(GROUPS.get(Math.floorMod(serverTick, CHECK_INTERVAL)))) {
			if (watched.book == null) {
				look(watched, RANDOM.nextInt(START_ONE_IN) == 0);
			}
		}
	}

	private static void follow(Watched watched) {
		Villager villager = watched.villager;
		Stop stop = why(villager, running(watched.work));
		boolean timeUp = villager.tickCount >= watched.readUntil;
		if (stop == Stop.WORKING && !timeUp) {
			pause(watched);
			return;
		}
		if (stop == null && timeUp) {
			stop = Stop.DONE;
		}
		if (stop != null) {
			stop(watched, stop);
		} else if (watched.paused) {
			resume(watched);
		}
	}

	private static boolean look(Watched watched, boolean lucky) {
		Villager villager = watched.villager;
		int now = villager.tickCount;
		if (now == watched.lastTick) {
			return false;
		}
		watched.lastTick = now;
		if (!lucky || now < watched.restUntil || why(villager, running(watched.work)) != null) {
			return false;
		}
		start(watched, chooseBook(offerResults(villager), RANDOM.nextInt(Integer.MAX_VALUE)), duration(RANDOM.nextInt(MAX_TICKS - MIN_TICKS + 1)));
		return true;
	}

	private static void start(Watched watched, ItemStack book, int ticks) {
		watched.book = book;
		watched.readUntil = watched.villager.tickCount + ticks;
		watched.paused = false;
		READING.add(watched);
		tellWatchers(watched.villager, new LibrarianReadingPayload(watched.villager.getId(), true, book));
	}

	private static void stop(Watched watched, Stop why) {
		boolean shown = !watched.paused;
		watched.book = null;
		watched.paused = false;
		watched.restUntil = watched.villager.tickCount + REST_TICKS;
		end(watched);
		lastStop = why;
		if (shown) {
			tellWatchers(watched.villager, new LibrarianReadingPayload(watched.villager.getId(), false, ItemStack.EMPTY));
		}
	}

	private static void end(Watched watched) {
		READING.remove(watched);
	}

	private static void pause(Watched watched) {
		if (!watched.paused) {
			watched.paused = true;
			tellWatchers(watched.villager, new LibrarianReadingPayload(watched.villager.getId(), false, ItemStack.EMPTY));
		}
	}

	private static void resume(Watched watched) {
		watched.paused = false;
		if (watched.book != null) {
			tellWatchers(watched.villager, new LibrarianReadingPayload(watched.villager.getId(), true, watched.book));
		}
	}

	public static void startsWork(Villager villager, BehaviorControl<?> work) {
		Watched watched = WATCHED.get(villager);
		if (watched == null) {
			return;
		}
		watched.work = work;
		if (watched.book != null) {
			pause(watched);
		}
	}

	private static void tellWatchers(Villager villager, LibrarianReadingPayload state) {
		for (ServerPlayer viewer : receivers(PlayerLookup.tracking(villager))) {
			ServerPlayNetworking.send(viewer, state);
		}
	}

	public static List<ServerPlayer> receivers(Collection<ServerPlayer> players) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (takes(player)) {
				out.add(player);
			}
		}
		return out;
	}

	private static boolean takes(ServerPlayer player) {
		try {
			return ServerPlayNetworking.canSend(player, LibrarianReadingPayload.TYPE);
		} catch (RuntimeException noConnection) {
			return false;
		}
	}

	public static @Nullable LibrarianReadingPayload trackingPayload(Entity entity, ServerPlayer viewer) {
		if (!(entity instanceof Villager villager)) {
			return null;
		}
		Watched watched = WATCHED.get(villager);
		if (watched == null || watched.book == null || watched.paused || !takes(viewer)) {
			return null;
		}
		return new LibrarianReadingPayload(villager.getId(), true, watched.book);
	}

	public static @Nullable Stop lastStop() {
		return lastStop;
	}

	public static @Nullable ItemStack reading(Villager villager) {
		Watched watched = WATCHED.get(villager);
		return watched == null || watched.paused ? null : watched.book;
	}

	public static boolean paused(Villager villager) {
		Watched watched = WATCHED.get(villager);
		return watched != null && watched.book != null && watched.paused;
	}

	public static boolean watched(Villager villager) {
		return WATCHED.containsKey(villager);
	}

	public static boolean look(Villager villager, boolean lucky) {
		Watched watched = WATCHED.get(villager);
		return watched != null && watched.book == null && ScribeConfig.get().readingLibrariansOn() && look(watched, lucky);
	}

	public static void follow(Villager villager) {
		Watched watched = WATCHED.get(villager);
		if (watched != null && watched.book != null) {
			if (ScribeConfig.get().readingLibrariansOn()) {
				follow(watched);
			} else {
				stop(watched, Stop.SETTING_OFF);
			}
		}
	}

	public static void rested(Villager villager) {
		Watched watched = WATCHED.get(villager);
		if (watched != null) {
			watched.restUntil = 0;
			watched.lastTick = -1;
		}
	}

	public static void readFor(Villager villager, int ticks) {
		Watched watched = WATCHED.get(villager);
		if (watched != null && watched.book != null) {
			watched.readUntil = villager.tickCount + ticks;
		}
	}

	public static int readers() {
		return READING.size();
	}
}
