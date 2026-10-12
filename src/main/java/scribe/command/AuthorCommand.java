package scribe.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import scribe.EnchantedBooks;
import scribe.EnchantedMaker;
import scribe.RealAuthor;
import scribe.placed.PlacedBooks;

public final class AuthorCommand {
	public enum Line {
		LECTERN("lectern", "On the lectern at %s, %s, %s"),
		SHELF("shelf", "In the chiseled bookshelf at %s, %s, %s"),
		PLACED("placed", "Put down at %s, %s, %s"),
		HELD_BY("held_by", "Held by %s"),
		TITLE("title", "%s, by %s"),
		NOBODY("nobody", "nobody"),
		REAL("real", "Real author: %s"),
		ID("id", "Player id: %s"),
		NOW_NAMED("now_named", "Now named %s"),
		PEN_NAME("pen_name", "%s is a pen name"),
		OWN_NAME("own_name", "%s is their own name"),
		COPY("copy", "%s: a copy keeps the original's author"),
		NO_RECORD("no_record", "No record: signed before Scribe kept authors, or made with a command"),
		UNSIGNED("unsigned", "%s: not signed yet"),
		ENCHANTED("enchanted", "%s: %s"),
		MAKER("maker", "Made by %s"),
		NO_MAKER("no_maker", "No record of who made it: found, traded, fished, given by a command, or made before Scribe kept makers"),
		NO_AUTHOR("no_author", "%s has no author"),
		NOT_A_BOOK("not_a_book", "%s is not a book"),
		NOTHING("nothing", "No book: your hands are empty and you are not looking at a book on a lectern, in a chiseled bookshelf or put down"),
		NOT_HOLDING("not_holding", "%s is not holding a book"),
		CONSOLE("console", "From the console, name a player: /scribe author <player>");

		public final String key;
		public final String english;

		Line(String key, String english) {
			this.key = "scribe.command.author." + key;
			this.english = english;
		}

		public MutableComponent text(Object... args) {
			return Component.translatableWithFallback(this.key, this.english, args);
		}
	}

	private static final DynamicCommandExceptionType NOT_A_BOOK = new DynamicCommandExceptionType(item -> Line.NOT_A_BOOK.text(item));
	private static final SimpleCommandExceptionType NOTHING = new SimpleCommandExceptionType(Line.NOTHING.text());
	private static final DynamicCommandExceptionType NOT_HOLDING = new DynamicCommandExceptionType(player -> Line.NOT_HOLDING.text(player));
	private static final SimpleCommandExceptionType CONSOLE = new SimpleCommandExceptionType(Line.CONSOLE.text());

	private AuthorCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("scribe").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("author").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(context -> own(context.getSource()))
				.then(Commands.argument("player", EntityArgument.player())
					.executes(context -> heldBy(context.getSource(), EntityArgument.getPlayer(context, "player"))))));
	}

	private static int own(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			throw CONSOLE.create();
		}
		ItemStack held = held(player);
		if (!held.isEmpty()) {
			return answer(source, null, held);
		}
		if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
			ItemStack other = player.getMainHandItem().isEmpty() ? player.getOffhandItem() : player.getMainHandItem();
			throw NOT_A_BOOK.create(other.getHoverName());
		}
		Found found = lookedAt(player);
		if (found == null) {
			throw NOTHING.create();
		}
		return answer(source, found.where(), found.book());
	}

	private static int heldBy(CommandSourceStack source, ServerPlayer player) throws CommandSyntaxException {
		ItemStack held = held(player);
		if (held.isEmpty()) {
			throw NOT_HOLDING.create(player.getDisplayName());
		}
		return answer(source, Line.HELD_BY.text(player.getDisplayName()), held);
	}

	public static boolean isBook(ItemStack stack) {
		return stack.has(DataComponents.WRITTEN_BOOK_CONTENT) || stack.has(DataComponents.WRITABLE_BOOK_CONTENT) || EnchantedBooks.is(stack)
			|| stack.is(Items.BOOK);
	}

	public static ItemStack held(Player player) {
		if (isBook(player.getMainHandItem())) {
			return player.getMainHandItem();
		}
		return isBook(player.getOffhandItem()) ? player.getOffhandItem() : ItemStack.EMPTY;
	}

	public record Found(ItemStack book, Component where) {
	}

	public static @Nullable Found lookedAt(ServerPlayer player) {
		HitResult hit = aim(player);
		if (hit instanceof EntityHitResult entityHit) {
			ItemStack placed = PlacedBooks.aimedBook(entityHit.getEntity(), entityHit.getLocation());
			BlockPos at = entityHit.getEntity().blockPosition();
			return placed.isEmpty() ? null : new Found(placed, Line.PLACED.text(at.getX(), at.getY(), at.getZ()));
		}
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = blockHit.getBlockPos();
		ServerLevel level = player.level();
		BlockEntity entity = level.getBlockEntity(pos);
		BlockState state = level.getBlockState(pos);
		if (entity instanceof LecternBlockEntity lectern) {
			return lectern.hasBook() ? new Found(lectern.getBook(), Line.LECTERN.text(pos.getX(), pos.getY(), pos.getZ())) : null;
		}
		if (entity instanceof ChiseledBookShelfBlockEntity shelf && state.getBlock() instanceof ChiseledBookShelfBlock block
			&& state.hasProperty(ChiseledBookShelfBlock.FACING)) {
			OptionalInt slot = block.getHitSlot(blockHit, state.getValue(ChiseledBookShelfBlock.FACING));
			if (slot.isPresent() && !shelf.getItem(slot.getAsInt()).isEmpty()) {
				return new Found(shelf.getItem(slot.getAsInt()), Line.SHELF.text(pos.getX(), pos.getY(), pos.getZ()));
			}
		}
		return null;
	}

	public static HitResult aim(ServerPlayer player) {
		double blockRange = player.blockInteractionRange();
		double entityRange = player.entityInteractionRange();
		double reach = Math.max(blockRange, entityRange);
		double reachSq = Mth.square(reach);
		Vec3 from = player.getEyePosition(1.0F);
		HitResult block = player.pick(reach, 1.0F, false);
		double blockSq = block.getLocation().distanceToSqr(from);
		if (block.getType() != HitResult.Type.MISS) {
			reachSq = blockSq;
			reach = Math.sqrt(reachSq);
		}
		Vec3 view = player.getViewVector(1.0F);
		Vec3 to = from.add(view.x * reach, view.y * reach, view.z * reach);
		AABB box = player.getBoundingBox().expandTowards(view.scale(reach)).inflate(1.0, 1.0, 1.0);
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, from, to, box, EntitySelector.CAN_BE_PICKED, reachSq);
		return entity != null && entity.getLocation().distanceToSqr(from) < blockSq ? within(entity, from, entityRange) : within(block, from, blockRange);
	}

	private static HitResult within(HitResult hit, Vec3 from, double range) {
		Vec3 at = hit.getLocation();
		if (at.closerThan(from, range)) {
			return hit;
		}
		return BlockHitResult.miss(at, Direction.getApproximateNearest(at.x - from.x, at.y - from.y, at.z - from.z), BlockPos.containing(at));
	}

	private static int answer(CommandSourceStack source, @Nullable Component where, ItemStack book) {
		MinecraftServer server = source.getServer();
		List<Component> lines = new ArrayList<>();
		if (where != null) {
			lines.add(where);
		}
		lines.addAll(describe(book, id -> knownName(server, id), source.registryAccess()));
		for (Component line : lines) {
			source.sendSuccess(() -> line, false);
		}
		return 1;
	}

	public static @Nullable String knownName(MinecraftServer server, UUID id) {
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		if (online != null) {
			return online.getGameProfile().name();
		}
		return server.services().nameToIdCache().get(id).map(NameAndId::name).orElse(null);
	}

	public static List<Component> describe(ItemStack book, Function<UUID, @Nullable String> knownName, HolderLookup.@Nullable Provider registries) {
		List<Component> lines = new ArrayList<>();
		WrittenBookContent written = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (written != null) {
			String shown = written.author();
			Component shownName = StringUtil.isBlank(shown) ? Line.NOBODY.text() : Component.literal(shown);
			lines.add(Line.TITLE.text(written.title().raw(), shownName));
			RealAuthor author = RealAuthor.of(book);
			boolean recorded = author != null && (author.id() != null || !StringUtil.isBlank(author.name()));
			if (recorded) {
				person(lines, Line.REAL, author.id(), author.name(), knownName);
				if (!StringUtil.isBlank(shown)) {
					lines.add((author.penName() ? Line.PEN_NAME : Line.OWN_NAME).text(shown));
				}
			} else {
				lines.add(Line.NO_RECORD.text());
			}
			if (written.generation() > 0) {
				lines.add(Line.COPY.text(Component.translatable("book.generation." + written.generation())));
			}
			return lines;
		}
		if (book.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
			lines.add(Line.UNSIGNED.text(book.getHoverName()));
			return lines;
		}
		if (EnchantedBooks.is(book)) {
			List<Component> names = enchantmentNames(book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY), registries);
			lines.add(names.isEmpty() ? book.getHoverName() : Line.ENCHANTED.text(book.getHoverName(), ComponentUtils.formatList(names, Component.literal(", "))));
			EnchantedMaker maker = EnchantedMaker.of(book);
			if (maker != null) {
				person(lines, Line.MAKER, maker.id(), maker.name(), knownName);
			} else {
				lines.add(Line.NO_MAKER.text());
			}
			return lines;
		}
		lines.add(Line.NO_AUTHOR.text(book.getHoverName()));
		return lines;
	}

	public static List<Component> describe(ItemStack book, Function<UUID, @Nullable String> knownName) {
		return describe(book, knownName, null);
	}

	static List<Component> enchantmentNames(ItemEnchantments stored, HolderLookup.@Nullable Provider registries) {
		List<Component> names = new ArrayList<>();
		Set<Holder<Enchantment>> listed = new HashSet<>();
		if (registries != null) {
			Optional<HolderSet.Named<Enchantment>> order = registries.lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.TOOLTIP_ORDER);
			if (order.isPresent()) {
				for (Holder<Enchantment> enchantment : order.get()) {
					int level = stored.getLevel(enchantment);
					if (level > 0) {
						names.add(Enchantment.getFullname(enchantment, level));
						listed.add(enchantment);
					}
				}
			}
		}
		List<Holder<Enchantment>> rest = new ArrayList<>();
		for (var entry : stored.entrySet()) {
			if (!listed.contains(entry.getKey())) {
				rest.add(entry.getKey());
			}
		}
		rest.sort(Comparator.comparing(enchantment -> enchantment.unwrapKey().map(key -> key.identifier().toString()).orElse("")));
		for (Holder<Enchantment> enchantment : rest) {
			names.add(Enchantment.getFullname(enchantment, stored.getLevel(enchantment)));
		}
		return names;
	}

	private static void person(List<Component> lines, Line first, @Nullable UUID id, String name, Function<UUID, @Nullable String> knownName) {
		String now = id == null ? null : knownName.apply(id);
		String stored = StringUtil.isBlank(name) ? (now != null ? now : "?") : name;
		lines.add(first.text(stored));
		if (id != null) {
			lines.add(Line.ID.text(ComponentUtils.copyOnClickText(id.toString())));
			if (now != null && !now.equals(stored)) {
				lines.add(Line.NOW_NAMED.text(now));
			}
		}
	}
}
