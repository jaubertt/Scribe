package scribe.placed;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import scribe.Scribe;
import scribe.config.ScribeConfig;
import scribe.ink.PlacedInk;
import scribe.mixin.DisplayAccessor;
import scribe.mixin.InteractionAccessor;
import scribe.placed.PlacedLayout.Kind;
import scribe.placed.PlacedLayout.Place;
import scribe.server.BookPreview;

public final class PlacedBooks {
	public static final String SPOT_TAG = Scribe.MOD_ID + ".book_spot";
	public static final String BOOK_TAG = Scribe.MOD_ID + ".placed_book";
	public static final String MARK = "scribe:placed_book";
	public static final String PLACE = "scribe:place";
	public static final TagKey<Item> PLACEABLE = TagKey.create(Registries.ITEM, Scribe.id("placeable_books"));
	private static final float DISPLAY_BOX_WIDTH = 1.125F;
	private static final float DISPLAY_BOX_HEIGHT = 0.75F;
	private static final int ORPHAN_TICKS = 100;
	private static final double SAME_POINT = 1.0E-6;
	private static final CompoundTag MARK_TAG = markTag();

	private static final Map<ServerLevel, Set<Interaction>> SPOTS = new WeakHashMap<>();
	private static final Map<ServerLevel, Set<Interaction>> FRESH = new WeakHashMap<>();
	private static final Map<ServerLevel, Map<Display.ItemDisplay, Long>> DISPLAYS = new WeakHashMap<>();
	private static final Map<UUID, Long> PUT_DOWN = new ConcurrentHashMap<>();

	private PlacedBooks() {
	}

	public static void init() {
		Scribe.LOGGER.debug("Placed books are saved as {}", Saved.SPOT.identifier());
		ItemEvents.USE_ON.register(PlacedBooks::useOn);
		UseEntityCallback.EVENT.register(PlacedBooks::useEntity);
		AttackEntityCallback.EVENT.register(PlacedBooks::attack);
		UseItemCallback.EVENT.register(PlacedBooks::useItem);
		ServerEntityEvents.ENTITY_LOAD.register(PlacedBooks::loaded);
		ServerEntityEvents.ENTITY_UNLOAD.register(PlacedBooks::unloaded);
		ServerTickEvents.END_LEVEL_TICK.register(PlacedBooks::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SPOTS.clear();
			FRESH.clear();
			DISPLAYS.clear();
			PUT_DOWN.clear();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PUT_DOWN.remove(handler.player.getUUID()));
	}

	public static boolean isPlaceable(ItemStack stack) {
		return !stack.isEmpty() && stack.is(PLACEABLE);
	}

	public static boolean placesWith(ItemStack stack, InteractionHand hand, boolean sneaking) {
		return isPlaceable(stack) && (sneaking || placesWithoutSneaking(stack, hand));
	}

	public static boolean placesWithoutSneaking(ItemStack stack, InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND && stack.is(Items.BOOK);
	}

	public static boolean isSpot(Entity entity) {
		return entity instanceof Interaction && (entity.entityTags().contains(SPOT_TAG) || spotOf(entity) != null);
	}

	public static ItemStack aimedBook(Entity entity, Vec3 hit) {
		BookSpot spot = isSpot(entity) ? spotOf(entity) : null;
		if (spot == null || spot.books().isEmpty()) {
			return ItemStack.EMPTY;
		}
		return spot.books().get(PlacedLayout.aimed(spot.kind(), spot.facing(), spot.count(), entity.position(), hit)).copy();
	}

	public static @Nullable BookSpot spotOf(Entity box) {
		return ((AttachmentTarget) box).getAttached(Saved.SPOT);
	}

	private static void setSpot(Entity box, @Nullable BookSpot spot) {
		((AttachmentTarget) box).setAttached(Saved.SPOT, spot);
	}

	private static final class Saved {
		static final AttachmentType<BookSpot> SPOT = AttachmentRegistry.createPersistent(Scribe.id("book_spot"), BookSpot.CODEC);
	}

	public static ItemStack lightCopy(ItemStack book) {
		ItemStack copy = BookPreview.of(book);
		CustomData.update(DataComponents.CUSTOM_DATA, copy, tag -> tag.putBoolean(MARK, true));
		return copy;
	}

	public static boolean isMarked(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.matchedBy(MARK_TAG);
	}

	public static ItemStack shownCopy(ItemStack book, Place place) {
		ItemStack copy = lightCopy(book);
		CustomData.update(DataComponents.CUSTOM_DATA, copy, tag -> tag.put(PLACE, place.toTag()));
		return copy;
	}

	public static @Nullable Place placeOf(ItemStack copy) {
		CustomData data = copy.get(DataComponents.CUSTOM_DATA);
		if (data == null || !data.matchedBy(MARK_TAG)) {
			return null;
		}
		return data.copyTag().getCompound(PLACE).flatMap(Place::fromTag).orElse(null);
	}

	public static @Nullable Interaction spotAt(Level level, BlockPos space) {
		for (Interaction box : level.getEntitiesOfClass(Interaction.class, new AABB(space), PlacedBooks::isSpot)) {
			if (!box.isRemoved() && box.blockPosition().equals(space)) {
				return box;
			}
		}
		return null;
	}

	public static boolean mayChange(Player player, Level level, BlockPos pos) {
		return player.mayBuild() && level.mayInteract(player, pos);
	}

	public static boolean canPutDown(Level level, BlockPos clicked, Direction face, BlockPos space) {
		Kind kind = PlacedLayout.kindFor(face);
		return kind != null && canStart(level, kind, space, clicked);
	}

	private static boolean canStart(Level level, Kind kind, BlockPos space, BlockPos behind) {
		BlockState state = level.getBlockState(space);
		if (!state.canBeReplaced() || burns(level, space, state) || !supported(level, space) || PlacedInk.potIn(level, space)) {
			return false;
		}
		return kind == Kind.PILE || standsAgainst(level.getBlockState(behind));
	}

	public static boolean standsAgainst(BlockState behind) {
		return !behind.isAir() && !behind.canBeReplaced();
	}

	public static boolean burns(Level level, BlockPos space, BlockState state) {
		return state.is(BlockTags.FIRE) || level.getFluidState(space).is(FluidTags.LAVA);
	}

	public static boolean supported(Level level, BlockPos space) {
		return Block.canSupportCenter(level, space.below(), Direction.UP);
	}

	public static boolean spaceFree(Level level, BlockPos space) {
		return level.getBlockState(space).canBeReplaced();
	}

	private static @Nullable InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return null;
		}
		ItemStack stack = context.getItemInHand();
		Direction face = context.getClickedFace();
		Kind kind = PlacedLayout.kindFor(face);
		if (kind == null || !placesWith(stack, context.getHand(), player.isSecondaryUseActive())) {
			return null;
		}
		BlockPos clicked = context.getClickedPos();
		BlockPos space = PlacedLayout.spaceFor(clicked, face);
		Interaction box = spotAt(level, space);
		if (box != null) {
			return add(level, player, box, context.getHand(), stack, context.getClickLocation(), true);
		}
		if (!ScribeConfig.get().placeableBooksOn() || !mayChange(player, level, space) || !canPutDown(level, clicked, face, space)) {
			return null;
		}
		ItemStack book = stack.split(1);
		createSpot(level, space, kind, PlacedLayout.facingFor(kind, face, player.getDirection()), book);
		putDown(level, player, space, true);
		return InteractionResult.SUCCESS_SERVER;
	}

	private static InteractionResult useEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer) || !isSpot(entity) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		Interaction box = (Interaction) entity;
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.CONSUME;
		}
		Vec3 at = hit != null ? hit.getLocation() : box.position();
		ItemStack main = serverPlayer.getMainHandItem();
		boolean sneaking = serverPlayer.isSecondaryUseActive();
		if (placesWith(main, InteractionHand.MAIN_HAND, sneaking)) {
			return add(server, serverPlayer, box, InteractionHand.MAIN_HAND, main, at, false);
		}
		ItemStack off = serverPlayer.getOffhandItem();
		if (sneaking && main.isEmpty() && isPlaceable(off)) {
			return add(server, serverPlayer, box, InteractionHand.OFF_HAND, off, at, false);
		}
		return dropBook(server, serverPlayer, box, at);
	}

	private static InteractionResult attack(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer) || !isSpot(entity)) {
			return InteractionResult.PASS;
		}
		if (!mayChange(player, server, entity.blockPosition())) {
			return InteractionResult.FAIL;
		}
		breakSpot(server, (Interaction) entity, player);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult useItem(Player player, Level level, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}
		Long when = PUT_DOWN.get(player.getUUID());
		if (when != null && level.getGameTime() - when <= 1 && isPlaceable(player.getItemInHand(hand))) {
			return InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	private static InteractionResult add(ServerLevel level, ServerPlayer player, Interaction box, InteractionHand hand, ItemStack stack, Vec3 at,
		boolean blockClick) {
		return add(level, player, box, hand, stack, at, blockClick, false);
	}

	private static InteractionResult add(ServerLevel level, ServerPlayer player, Interaction box, InteractionHand hand, ItemStack stack, Vec3 at,
		boolean blockClick, boolean sentOn) {
		BookSpot spot = spotOf(box);
		if (spot == null || !ScribeConfig.get().placeableBooksOn()) {
			return InteractionResult.CONSUME;
		}
		if (spot.full()) {
			return blockClick || sentOn ? InteractionResult.CONSUME : addNext(level, player, box, spot, hand, stack, at);
		}
		if (!mayChange(player, level, box.blockPosition())) {
			return InteractionResult.FAIL;
		}
		SwingAnimation swing = stack.getInteractAnimation();
		ItemStack book = oneBook(player, stack, blockClick);
		insert(level, box, spot, PlacedLayout.addAt(spot.kind(), spot.facing(), spot.count(), box.position(), at), book);
		putDown(level, player, box.blockPosition(), blockClick);
		if (!blockClick) {
			player.swing(hand, swing, true);
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	private static ItemStack oneBook(ServerPlayer player, ItemStack stack, boolean blockClick) {
		if (blockClick) {
			return stack.split(1);
		}
		ItemStack book = stack.copyWithCount(1);
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		return book;
	}

	private static InteractionResult addNext(ServerLevel level, ServerPlayer player, Interaction box, BookSpot spot, InteractionHand hand, ItemStack stack,
		Vec3 at) {
		Direction face = PlacedLayout.boxFace(box.position(), box.getBbWidth(), box.getBbHeight(), at);
		BlockPos next = PlacedLayout.nextSpace(spot.kind(), spot.facing(), box.blockPosition(), face);
		if (next == null) {
			return InteractionResult.CONSUME;
		}
		if (!mayChange(player, level, next)) {
			return InteractionResult.FAIL;
		}
		Interaction there = spotAt(level, next);
		if (there != null) {
			return add(level, player, there, hand, stack, at, false, true);
		}
		Direction facing = spot.kind() == Kind.PILE ? player.getDirection() : spot.facing();
		if (!canStart(level, spot.kind(), next, next.relative(facing))) {
			return InteractionResult.CONSUME;
		}
		SwingAnimation swing = stack.getInteractAnimation();
		createSpot(level, next, spot.kind(), facing, oneBook(player, stack, false));
		putDown(level, player, next, false);
		player.swing(hand, swing, true);
		return InteractionResult.SUCCESS_SERVER;
	}

	private static InteractionResult dropBook(ServerLevel level, ServerPlayer player, Interaction box, Vec3 at) {
		BookSpot spot = spotOf(box);
		if (spot == null) {
			return InteractionResult.CONSUME;
		}
		if (!mayChange(player, level, box.blockPosition())) {
			return InteractionResult.FAIL;
		}
		int index = PlacedLayout.aimed(spot.kind(), spot.facing(), spot.count(), box.position(), at);
		Vec3 where = bookCenter(box, spot, index);
		ItemStack book = remove(level, box, spot, index);
		ItemEntity item = new ItemEntity(level, where.x, where.y, where.z, book);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
		level.playSound(null, where.x, where.y, where.z, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.gameEvent(player, GameEvent.BLOCK_CHANGE, box.blockPosition());
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		return InteractionResult.SUCCESS_SERVER;
	}

	private static void putDown(ServerLevel level, ServerPlayer player, BlockPos space, boolean blockClick) {
		level.playSound(null, space, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.gameEvent(player, GameEvent.BLOCK_CHANGE, space);
		if (blockClick) {
			PUT_DOWN.put(player.getUUID(), level.getGameTime());
		}
	}

	public static Interaction createSpot(ServerLevel level, BlockPos space, Kind kind, Direction facing, ItemStack book) {
		Vec3 anchor = PlacedLayout.anchor(space, kind, facing);
		Display.ItemDisplay display = spawnDisplay(level, anchor, book, PlacedLayout.place(kind, facing, space, 0, 1));
		Interaction box = EntityTypes.INTERACTION.create(level, EntitySpawnReason.TRIGGERED);
		box.addTag(SPOT_TAG);
		box.snapTo(anchor.x, anchor.y, anchor.z, 0.0F, 0.0F);
		BookSpot spot = new BookSpot(kind, facing, List.of(book), List.of(display.getUUID()));
		setSpot(box, spot);
		resize(box, spot);
		level.addFreshEntity(box);
		return box;
	}

	public static Place placeAt(Interaction box, BookSpot spot, int index) {
		return PlacedLayout.place(spot.kind(), spot.facing(), box.blockPosition(), index, spot.count());
	}

	private static Display.ItemDisplay spawnDisplay(ServerLevel level, Vec3 anchor, ItemStack book, Place place) {
		Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		display.addTag(BOOK_TAG);
		DisplayAccessor access = (DisplayAccessor) display;
		access.scribe$setWidth(DISPLAY_BOX_WIDTH);
		access.scribe$setHeight(DISPLAY_BOX_HEIGHT);
		display.snapTo(anchor.x, anchor.y, anchor.z, 0.0F, 0.0F);
		show(display, book, place);
		level.addFreshEntity(display);
		return display;
	}

	private static void show(Display.ItemDisplay display, ItemStack book, Place place) {
		if (!place.equals(placeOf(display.getSlot(0).get()))) {
			display.getSlot(0).set(shownCopy(book, place));
		}
		((DisplayAccessor) display).scribe$setTransformation(new Transformation(PlacedLayout.flatIcon(place)));
	}

	private static void insert(ServerLevel level, Interaction box, BookSpot spot, int index, ItemStack book) {
		BookSpot next = spot.with(index, book, BookSpot.NO_DISPLAY);
		Display.ItemDisplay display = spawnDisplay(level, box.position(), book, placeAt(box, next, index));
		next = next.withDisplay(index, display.getUUID());
		setSpot(box, next);
		layout(level, box, next);
	}

	private static ItemStack remove(ServerLevel level, Interaction box, BookSpot spot, int index) {
		ItemStack book = spot.books().get(index);
		discardDisplay(level, box, spot.displays().get(index));
		BookSpot next = spot.without(index);
		if (next.books().isEmpty()) {
			discardSpot(level, box);
		} else {
			setSpot(box, next);
			layout(level, box, next);
		}
		return book;
	}

	private static void layout(ServerLevel level, Interaction box, BookSpot spot) {
		for (int i = 0; i < spot.count(); i++) {
			Display.ItemDisplay display = displayOf(level, box, spot.displays().get(i));
			if (display != null) {
				show(display, spot.books().get(i), placeAt(box, spot, i));
			}
		}
		resize(box, spot);
	}

	private static void resize(Interaction box, BookSpot spot) {
		InteractionAccessor access = (InteractionAccessor) box;
		access.scribe$setWidth(PlacedLayout.boxWidth(spot.kind(), spot.count()));
		access.scribe$setHeight(PlacedLayout.boxHeight(spot.kind(), spot.count()));
	}

	public static Vec3 bookCenter(Interaction box, BookSpot spot, int index) {
		var center = PlacedLayout.center(PlacedLayout.bookFrame(spot.kind(), spot.facing(), box.blockPosition(), index, spot.count()));
		return box.position().add(center.x(), center.y(), center.z());
	}

	public static void breakSpot(ServerLevel level, Interaction box, @Nullable Player cause) {
		BookSpot spot = spotOf(box);
		setSpot(box, null);
		if (spot != null) {
			for (int i = 0; i < spot.count(); i++) {
				Vec3 at = bookCenter(box, spot, i);
				ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, spot.books().get(i).copy());
				item.setDefaultPickUpDelay();
				level.addFreshEntity(item);
			}
			level.playSound(null, box.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.0F);
			level.gameEvent(cause, GameEvent.BLOCK_CHANGE, box.blockPosition());
		}
		discardSpot(level, box, spot);
	}

	private static void discardSpot(ServerLevel level, Interaction box) {
		discardSpot(level, box, spotOf(box));
	}

	private static void discardSpot(ServerLevel level, Interaction box, @Nullable BookSpot spot) {
		setSpot(box, null);
		if (spot != null) {
			spot.displays().forEach(id -> discardDisplay(level, box, id));
		}
		box.removeTag(SPOT_TAG);
		Set<Interaction> spots = SPOTS.get(level);
		if (spots != null) {
			spots.remove(box);
		}
		Set<Interaction> fresh = FRESH.get(level);
		if (fresh != null) {
			fresh.remove(box);
		}
		if (!box.isRemoved()) {
			box.discard();
		}
	}

	private static void discardDisplay(ServerLevel level, Interaction box, UUID id) {
		Display.ItemDisplay display = displayOf(level, box, id);
		if (display != null) {
			display.removeTag(BOOK_TAG);
			if (!display.isRemoved()) {
				display.discard();
			}
		}
	}

	private static Display.@Nullable ItemDisplay displayOf(ServerLevel level, Interaction box, UUID id) {
		return level.getEntity(id) instanceof Display.ItemDisplay display && !display.isRemoved() && display.entityTags().contains(BOOK_TAG)
			&& display.position().distanceToSqr(box.position()) < SAME_POINT ? display : null;
	}

	private static void loaded(Entity entity, ServerLevel level) {
		if (isSpot(entity)) {
			SPOTS.computeIfAbsent(level, l -> new HashSet<>()).add((Interaction) entity);
			FRESH.computeIfAbsent(level, l -> new HashSet<>()).add((Interaction) entity);
		} else if (entity instanceof Display.ItemDisplay display && display.entityTags().contains(BOOK_TAG)) {
			DISPLAYS.computeIfAbsent(level, l -> new HashMap<>()).put(display, -1L);
		}
	}

	private static void unloaded(Entity entity, ServerLevel level) {
		Entity.RemovalReason reason = entity.getRemovalReason();
		boolean destroyed = reason != null && reason.shouldDestroy();
		if (isSpot(entity)) {
			Set<Interaction> spots = SPOTS.get(level);
			if (spots != null) {
				spots.remove(entity);
			}
			Set<Interaction> fresh = FRESH.get(level);
			if (fresh != null) {
				fresh.remove(entity);
			}
			if (destroyed && spotOf(entity) != null) {
				breakSpot(level, (Interaction) entity, null);
			}
		} else if (entity instanceof Display.ItemDisplay display && display.entityTags().contains(BOOK_TAG)) {
			Map<Display.ItemDisplay, Long> displays = DISPLAYS.get(level);
			if (displays != null) {
				displays.remove(display);
			}
			if (destroyed) {
				Interaction box = spotAt(level, display.blockPosition());
				BookSpot spot = box == null ? null : spotOf(box);
				if (spot != null && spot.displays().contains(display.getUUID())) {
					breakSpot(level, box, null);
				}
			}
		}
	}

	public static void tick(ServerLevel level) {
		Set<Interaction> spots = SPOTS.get(level);
		if (spots != null && !spots.isEmpty()) {
			for (Interaction box : List.copyOf(spots)) {
				if (box.isRemoved()) {
					spots.remove(box);
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
		BookSpot spot = spotOf(box);
		if (spot == null || spot.books().isEmpty()) {
			discardSpot(level, box);
			return;
		}
		BlockPos space = box.blockPosition();
		if (!supported(level, space) || !spaceFree(level, space)) {
			breakSpot(level, box, null);
			return;
		}
		BookSpot fixed = spot;
		for (int i = 0; i < spot.count(); i++) {
			if (displayOf(level, box, spot.displays().get(i)) == null) {
				Display.ItemDisplay made = spawnDisplay(level, box.position(), spot.books().get(i), placeAt(box, spot, i));
				fixed = fixed.withDisplay(i, made.getUUID());
			}
		}
		if (fixed != spot) {
			setSpot(box, fixed);
		}
		Set<Interaction> fresh = FRESH.get(level);
		if (fresh != null && fresh.remove(box)) {
			layout(level, box, fixed);
		}
	}

	public static void orphans(ServerLevel level) {
		Map<Display.ItemDisplay, Long> displays = DISPLAYS.get(level);
		if (displays == null || displays.isEmpty()) {
			return;
		}
		Set<UUID> claimed = new HashSet<>();
		Set<Interaction> spots = SPOTS.get(level);
		if (spots != null) {
			for (Interaction box : spots) {
				BookSpot spot = spotOf(box);
				if (spot != null) {
					claimed.addAll(spot.displays());
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
				display.removeTag(BOOK_TAG);
				display.discard();
			}
		}
	}

	public static Set<Interaction> loadedSpots(ServerLevel level) {
		return Set.copyOf(SPOTS.getOrDefault(level, Set.of()));
	}

	private static CompoundTag markTag() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(MARK, true);
		return tag;
	}
}
