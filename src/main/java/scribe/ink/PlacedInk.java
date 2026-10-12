package scribe.ink;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import scribe.Scribe;
import scribe.config.ScribeConfig;
import scribe.mixin.DisplayAccessor;
import scribe.mixin.InteractionAccessor;
import scribe.placed.PlacedBooks;

public final class PlacedInk {
	public static final String POT_TAG = Scribe.MOD_ID + ".ink_pot";
	public static final String SHOWN_TAG = Scribe.MOD_ID + ".placed_ink";
	public static final String PLACED = "scribe:placed_ink";
	public static final String QUILL = "scribe:placed_quill";
	public static final Display.BillboardConstraints BILLBOARD = Display.BillboardConstraints.VERTICAL;
	public static final SoundType SOUND = SoundType.DECORATED_POT;
	private static final int ORPHAN_TICKS = 100;
	private static final double SAME_POINT = 1.0E-6;

	private static final Map<ServerLevel, Set<Interaction>> POTS = new WeakHashMap<>();
	private static final Map<ServerLevel, Set<Interaction>> FRESH = new WeakHashMap<>();
	private static final Map<ServerLevel, Map<Display.ItemDisplay, Long>> DISPLAYS = new WeakHashMap<>();

	private PlacedInk() {
	}

	static void init() {
		Scribe.LOGGER.debug("Placed ink bottles are saved as {}", Saved.POT.identifier());
		ItemEvents.USE_ON.register(PlacedInk::useOn);
		UseEntityCallback.EVENT.register(PlacedInk::useEntity);
		AttackEntityCallback.EVENT.register(PlacedInk::attack);
		ServerEntityEvents.ENTITY_LOAD.register(PlacedInk::loaded);
		ServerEntityEvents.ENTITY_UNLOAD.register(PlacedInk::unloaded);
		ServerTickEvents.END_LEVEL_TICK.register(PlacedInk::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			POTS.clear();
			FRESH.clear();
			DISPLAYS.clear();
		});
	}

	private static final class Saved {
		static final AttachmentType<InkSpot> POT = AttachmentRegistry.createPersistent(Scribe.id("ink_pot"), InkSpot.CODEC);
	}

	public static boolean isPot(Entity entity) {
		return entity instanceof Interaction && (entity.entityTags().contains(POT_TAG) || spotOf(entity) != null);
	}

	public static @Nullable InkSpot spotOf(Entity box) {
		return ((AttachmentTarget) box).getAttached(Saved.POT);
	}

	private static void setSpot(Entity box, @Nullable InkSpot spot) {
		((AttachmentTarget) box).setAttached(Saved.POT, spot);
	}

	public static List<Interaction> potsAt(Level level, BlockPos space) {
		List<Interaction> pots = new ArrayList<>();
		for (Interaction box : boxesIn(level, space)) {
			if (isPot(box)) {
				pots.add(box);
			}
		}
		return pots;
	}

	public static int bottlesIn(Level level, BlockPos space) {
		int bottles = 0;
		for (Interaction box : boxesIn(level, space)) {
			if (isPot(box) || stands(level, box, PlacedInk::isPotCopy)) {
				bottles++;
			}
		}
		return bottles;
	}

	public static boolean canAdd(Level level, BlockPos space) {
		int bottles = bottlesIn(level, space);
		return bottles >= 1 && bottles < InkLayout.MAX_BOTTLES && PlacedBooks.supported(level, space);
	}

	public static @Nullable Interaction potAt(Level level, BlockPos space) {
		for (Interaction box : boxesIn(level, space)) {
			if (isPot(box)) {
				return box;
			}
		}
		return null;
	}

	public record Shown(boolean open) {
	}

	public static ItemStack potCopy(InkSpot spot) {
		ItemStack copy = spot.pot().copyWithCount(1);
		CustomData.update(DataComponents.CUSTOM_DATA, copy, tag -> {
			CompoundTag placed = new CompoundTag();
			placed.putBoolean("open", spot.open());
			tag.put(PLACED, placed);
		});
		return copy;
	}

	public static @Nullable Shown shownOf(ItemStack copy) {
		CustomData data = copy.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		return data.copyTag().getCompound(PLACED).map(placed -> new Shown(placed.getBooleanOr("open", false))).orElse(null);
	}

	public static boolean isPotCopy(ItemStack stack) {
		return InkPots.isInk(stack) && shownOf(stack) != null;
	}

	public static ItemStack quillCopy(InkSpot spot) {
		ItemStack feather = new ItemStack(Items.FEATHER);
		DyeColor color = spot.pot().get(DataComponents.DYE);
		if (color != null) {
			feather.set(DataComponents.DYE, color);
		}
		CustomData.update(DataComponents.CUSTOM_DATA, feather, tag -> {
			CompoundTag quill = new CompoundTag();
			quill.putInt("rotation", spot.quillRotation());
			tag.put(QUILL, quill);
		});
		return feather;
	}

	public static boolean isQuillCopy(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(Items.FEATHER)) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(QUILL);
	}

	public static int quillRotationOf(ItemStack copy) {
		CustomData data = copy.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return 0;
		}
		return data.copyTag().getCompound(QUILL).map(quill -> InkLayout.normalize(quill.getIntOr("rotation", 0))).orElse(0);
	}

	public static boolean canPlace(Level level, BlockPos space) {
		BlockState state = level.getBlockState(space);
		return state.canBeReplaced() && !PlacedBooks.burns(level, space, state) && PlacedBooks.supported(level, space) && !potIn(level, space)
			&& !booksIn(level, space);
	}

	public static boolean potIn(Level level, BlockPos space) {
		for (Interaction box : boxesIn(level, space)) {
			if (isPot(box) || stands(level, box, PlacedInk::isPotCopy)) {
				return true;
			}
		}
		return false;
	}

	public static boolean booksIn(Level level, BlockPos space) {
		for (Interaction box : boxesIn(level, space)) {
			if (PlacedBooks.isSpot(box) || stands(level, box, PlacedBooks::isMarked)) {
				return true;
			}
		}
		return false;
	}

	private static List<Interaction> boxesIn(Level level, BlockPos space) {
		return level.getEntitiesOfClass(Interaction.class, new AABB(space), box -> !box.isRemoved() && box.blockPosition().equals(space));
	}

	public static boolean stands(Level level, Entity box, Predicate<ItemStack> shows) {
		Vec3 at = box.position();
		return !level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(at, at).inflate(1.0 / 64.0), display -> {
			var slot = display.getSlot(0);
			return !display.isRemoved() && slot != null && shows.test(slot.get());
		}).isEmpty();
	}

	private static @Nullable InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return null;
		}
		ItemStack stack = context.getItemInHand();
		if (!InkPots.isInk(stack) || !ScribeConfig.get().placeableBooksOn()) {
			return null;
		}
		BlockPos space = context.getClickedPos().relative(context.getClickedFace());
		if (!PlacedBooks.mayChange(player, level, space)) {
			return null;
		}
		if (canPlace(level, space)) {
			createPot(level, space, stack.split(1));
		} else if (canAdd(level, space)) {
			addBottle(level, space, stack.split(1));
		} else {
			return null;
		}
		placed(level, player, space);
		return InteractionResult.SUCCESS_SERVER;
	}

	private static void placed(ServerLevel level, ServerPlayer player, BlockPos space) {
		level.playSound(null, space, SOUND.getPlaceSound(), SoundSource.BLOCKS, (SOUND.getVolume() + 1.0F) / 2.0F, SOUND.getPitch() * 0.8F);
		level.gameEvent(player, GameEvent.BLOCK_PLACE, space);
	}

	private static InteractionResult useEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer) || !isPot(entity) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		Interaction box = (Interaction) entity;
		InkSpot spot = spotOf(box);
		if (hand != InteractionHand.MAIN_HAND || spot == null) {
			return InteractionResult.CONSUME;
		}
		ItemStack main = serverPlayer.getMainHandItem();
		if (InkPots.isInk(main) && ScribeConfig.get().placeableBooksOn() && canAdd(server, box.blockPosition())) {
			return addByHand(server, serverPlayer, box.blockPosition(), main);
		}
		if (spot.open()) {
			return dropQuill(server, serverPlayer, box, spot);
		}
		if (main.is(Items.FEATHER)) {
			return putQuill(server, serverPlayer, box, spot, InteractionHand.MAIN_HAND, main);
		}
		if (!main.isEmpty()) {
			return InteractionResult.CONSUME;
		}
		ItemStack off = serverPlayer.getOffhandItem();
		return off.is(Items.FEATHER) ? putQuill(server, serverPlayer, box, spot, InteractionHand.OFF_HAND, off) : InteractionResult.CONSUME;
	}

	private static InteractionResult addByHand(ServerLevel level, ServerPlayer player, BlockPos space, ItemStack stack) {
		if (!PlacedBooks.mayChange(player, level, space)) {
			return InteractionResult.FAIL;
		}
		ItemStack pot = stack.copyWithCount(1);
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		addBottle(level, space, pot);
		placed(level, player, space);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		return InteractionResult.SUCCESS_SERVER;
	}

	private static InteractionResult putQuill(ServerLevel level, ServerPlayer player, Interaction box, InkSpot spot, InteractionHand hand, ItemStack stack) {
		if (spot.open() || !ScribeConfig.get().placeableBooksOn()) {
			return InteractionResult.CONSUME;
		}
		if (!PlacedBooks.mayChange(player, level, box.blockPosition())) {
			return InteractionResult.FAIL;
		}
		SwingAnimation swing = stack.getInteractAnimation();
		ItemStack quill = stack.copyWithCount(1);
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		layout(level, box, spot.withQuill(quill, InkLayout.rotationOf(player.getYRot())));
		level.playSound(null, box.blockPosition(), SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.gameEvent(player, GameEvent.BLOCK_CHANGE, box.blockPosition());
		player.swing(hand, swing, true);
		return InteractionResult.SUCCESS_SERVER;
	}

	private static InteractionResult dropQuill(ServerLevel level, ServerPlayer player, Interaction box, InkSpot spot) {
		if (!PlacedBooks.mayChange(player, level, box.blockPosition())) {
			return InteractionResult.FAIL;
		}
		layout(level, box, spot.withoutQuill());
		Vec3 at = box.position();
		drop(level, at.add(0.0, InkLayout.DROP_HEIGHT, 0.0), spot.quill());
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.gameEvent(player, GameEvent.BLOCK_CHANGE, box.blockPosition());
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		return InteractionResult.SUCCESS_SERVER;
	}

	private static InteractionResult attack(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer) || !isPot(entity)) {
			return InteractionResult.PASS;
		}
		if (!PlacedBooks.mayChange(player, server, entity.blockPosition())) {
			return InteractionResult.FAIL;
		}
		breakPot(server, (Interaction) entity, player, true);
		for (Interaction other : potsAt(server, entity.blockPosition())) {
			breakPot(server, other, player, false);
		}
		return InteractionResult.SUCCESS;
	}

	public static Interaction createPot(ServerLevel level, BlockPos space, ItemStack pot) {
		return createPot(level, InkLayout.anchor(space), pot, InkLayout.boxWidth(1));
	}

	private static Interaction createPot(ServerLevel level, Vec3 at, ItemStack pot, float width) {
		Interaction box = EntityTypes.INTERACTION.create(level, EntitySpawnReason.TRIGGERED);
		box.addTag(POT_TAG);
		box.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		layout(level, box, InkSpot.of(pot), width);
		level.addFreshEntity(box);
		return box;
	}

	public static Interaction addBottle(ServerLevel level, BlockPos space, ItemStack pot) {
		List<Interaction> boxes = potsAt(level, space);
		int count = Math.min(boxes.size() + 1, InkLayout.MAX_BOTTLES);
		int[] spots = InkLayout.assign(space, boxes.stream().map(Entity::position).toList(), count);
		float width = InkLayout.boxWidth(count);
		for (int i = 0; i < boxes.size() && i < spots.length; i++) {
			moveTo(level, boxes.get(i), InkLayout.spot(space, count, spots[i]), width);
		}
		return createPot(level, InkLayout.spot(space, count, InkLayout.leftOver(spots, count)), pot, width);
	}

	private static void moveTo(ServerLevel level, Interaction box, Vec3 to, float width) {
		InkSpot spot = spotOf(box);
		if (spot == null) {
			return;
		}
		for (UUID id : List.of(spot.potDisplay(), spot.quillDisplay())) {
			Display.ItemDisplay display = displayOf(level, box, id);
			if (display != null) {
				display.snapTo(to.x, to.y, to.z, 0.0F, 0.0F);
			}
		}
		box.snapTo(to.x, to.y, to.z, 0.0F, 0.0F);
		layout(level, box, spot, width);
	}

	private static InkSpot layout(ServerLevel level, Interaction box, InkSpot spot) {
		return layout(level, box, spot, InkLayout.boxWidth(potsAt(level, box.blockPosition()).size()));
	}

	private static InkSpot layout(ServerLevel level, Interaction box, InkSpot spot, float width) {
		Vec3 at = box.position();
		Display.ItemDisplay pot = displayOf(level, box, spot.potDisplay());
		pot = show(level, pot, at, potCopy(spot), InkLayout.potIcon());
		Display.ItemDisplay quill = displayOf(level, box, spot.quillDisplay());
		UUID quillId = InkSpot.NO_DISPLAY;
		if (spot.open()) {
			quillId = show(level, quill, at, quillCopy(spot), InkLayout.quillIcon()).getUUID();
		} else if (quill != null) {
			discardDisplay(quill);
		}
		InkSpot next = spot.withDisplays(pot.getUUID(), quillId);
		setSpot(box, next);
		InteractionAccessor access = (InteractionAccessor) box;
		access.scribe$setWidth(width);
		access.scribe$setHeight(next.open() ? InkLayout.BOX_HEIGHT_OPEN : InkLayout.BOX_HEIGHT);
		return next;
	}

	private static Display.ItemDisplay show(ServerLevel level, Display.@Nullable ItemDisplay display, Vec3 at, ItemStack item, org.joml.Matrix4f transformation) {
		boolean made = display == null;
		if (made) {
			display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
			display.addTag(SHOWN_TAG);
			DisplayAccessor access = (DisplayAccessor) display;
			access.scribe$setWidth(InkLayout.DISPLAY_WIDTH);
			access.scribe$setHeight(InkLayout.DISPLAY_HEIGHT);
			display.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		}
		if (!ItemStack.matches(display.getSlot(0).get(), item)) {
			display.getSlot(0).set(item);
		}
		((DisplayAccessor) display).scribe$setTransformation(new Transformation(transformation));
		((DisplayAccessor) display).scribe$setBillboardConstraints(BILLBOARD);
		if (made) {
			level.addFreshEntity(display);
		}
		return display;
	}

	public static void breakPot(ServerLevel level, Interaction box, @Nullable Player cause) {
		breakPot(level, box, cause, true);
	}

	private static void breakPot(ServerLevel level, Interaction box, @Nullable Player cause, boolean sound) {
		InkSpot spot = spotOf(box);
		setSpot(box, null);
		if (spot != null) {
			Vec3 at = box.position().add(0.0, InkLayout.DROP_HEIGHT, 0.0);
			if (cause == null || !cause.preventsBlockDrops()) {
				drop(level, at, spot.pot());
			}
			drop(level, at, spot.quill());
			if (sound) {
				level.playSound(null, box.blockPosition(), SOUND.getBreakSound(), SoundSource.BLOCKS, (SOUND.getVolume() + 1.0F) / 2.0F, SOUND.getPitch() * 0.8F);
			}
			level.gameEvent(cause, GameEvent.BLOCK_DESTROY, box.blockPosition());
		}
		discardPot(level, box, spot);
	}

	private static void drop(ServerLevel level, Vec3 at, ItemStack stack) {
		if (!stack.isEmpty()) {
			ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack.copy());
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
	}

	private static void discardPot(ServerLevel level, Interaction box, @Nullable InkSpot spot) {
		setSpot(box, null);
		if (spot != null) {
			for (UUID id : List.of(spot.potDisplay(), spot.quillDisplay())) {
				Display.ItemDisplay display = displayOf(level, box, id);
				if (display != null) {
					discardDisplay(display);
				}
			}
		}
		box.removeTag(POT_TAG);
		Set<Interaction> pots = POTS.get(level);
		if (pots != null) {
			pots.remove(box);
		}
		Set<Interaction> fresh = FRESH.get(level);
		if (fresh != null) {
			fresh.remove(box);
		}
		if (!box.isRemoved()) {
			box.discard();
		}
	}

	private static void discardDisplay(Display.ItemDisplay display) {
		display.removeTag(SHOWN_TAG);
		if (!display.isRemoved()) {
			display.discard();
		}
	}

	private static Display.@Nullable ItemDisplay displayOf(ServerLevel level, Interaction box, UUID id) {
		return level.getEntity(id) instanceof Display.ItemDisplay display && !display.isRemoved() && display.entityTags().contains(SHOWN_TAG)
			&& display.position().distanceToSqr(box.position()) < SAME_POINT ? display : null;
	}

	private static void loaded(Entity entity, ServerLevel level) {
		if (isPot(entity)) {
			POTS.computeIfAbsent(level, l -> new HashSet<>()).add((Interaction) entity);
			FRESH.computeIfAbsent(level, l -> new HashSet<>()).add((Interaction) entity);
		} else if (entity instanceof Display.ItemDisplay display && display.entityTags().contains(SHOWN_TAG)) {
			DISPLAYS.computeIfAbsent(level, l -> new HashMap<>()).put(display, -1L);
		}
	}

	private static void unloaded(Entity entity, ServerLevel level) {
		Entity.RemovalReason reason = entity.getRemovalReason();
		boolean destroyed = reason != null && reason.shouldDestroy();
		if (isPot(entity)) {
			Set<Interaction> pots = POTS.get(level);
			if (pots != null) {
				pots.remove(entity);
			}
			Set<Interaction> fresh = FRESH.get(level);
			if (fresh != null) {
				fresh.remove(entity);
			}
			if (destroyed && spotOf(entity) != null) {
				breakPot(level, (Interaction) entity, null);
			}
		} else if (entity instanceof Display.ItemDisplay display && display.entityTags().contains(SHOWN_TAG)) {
			Map<Display.ItemDisplay, Long> displays = DISPLAYS.get(level);
			if (displays != null) {
				displays.remove(display);
			}
			if (destroyed) {
				for (Interaction box : potsAt(level, display.blockPosition())) {
					InkSpot spot = spotOf(box);
					if (spot != null && (spot.potDisplay().equals(display.getUUID()) || spot.quillDisplay().equals(display.getUUID()))) {
						breakPot(level, box, null);
					}
				}
			}
		}
	}

	public static void tick(ServerLevel level) {
		Set<Interaction> pots = POTS.get(level);
		if (pots != null && !pots.isEmpty()) {
			for (Interaction box : List.copyOf(pots)) {
				if (box.isRemoved()) {
					pots.remove(box);
				} else {
					check(level, box);
				}
			}
		}
		if (level.getGameTime() % ORPHAN_TICKS == 0) {
			orphans(level);
		}
	}

	public static void check(ServerLevel level, Interaction box) {
		InkSpot spot = spotOf(box);
		if (spot == null) {
			discardPot(level, box, null);
			return;
		}
		BlockPos space = box.blockPosition();
		if (spot.pot().isEmpty() || !PlacedBooks.supported(level, space) || !PlacedBooks.spaceFree(level, space)) {
			breakPot(level, box, null);
			return;
		}
		Set<Interaction> fresh = FRESH.get(level);
		boolean loadedNow = fresh != null && fresh.remove(box);
		if (loadedNow || displayOf(level, box, spot.potDisplay()) == null || (spot.open() && displayOf(level, box, spot.quillDisplay()) == null)) {
			layout(level, box, spot);
		}
	}

	public static void orphans(ServerLevel level) {
		Map<Display.ItemDisplay, Long> displays = DISPLAYS.get(level);
		if (displays == null || displays.isEmpty()) {
			return;
		}
		Set<UUID> claimed = new HashSet<>();
		Set<Interaction> pots = POTS.get(level);
		if (pots != null) {
			for (Interaction box : pots) {
				InkSpot spot = spotOf(box);
				if (spot != null) {
					claimed.add(spot.potDisplay());
					claimed.add(spot.quillDisplay());
				}
			}
		}
		long now = level.getGameTime();
		for (Map.Entry<Display.ItemDisplay, Long> entry : new ArrayList<>(displays.entrySet())) {
			Display.ItemDisplay display = entry.getKey();
			if (display.isRemoved()) {
				displays.remove(display);
			} else if (claimed.contains(display.getUUID())) {
				displays.put(display, -1L);
			} else if (entry.getValue() < 0) {
				displays.put(display, now);
			} else if (now - entry.getValue() >= ORPHAN_TICKS) {
				displays.remove(display);
				discardDisplay(display);
			}
		}
	}

	public static Set<Interaction> loadedPots(ServerLevel level) {
		return Set.copyOf(POTS.getOrDefault(level, Set.of()));
	}
}
