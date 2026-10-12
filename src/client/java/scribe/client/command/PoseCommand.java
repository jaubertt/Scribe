package scribe.client.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import scribe.BookLimits;
import scribe.EnchantedBooks;
import scribe.client.BookNet;
import scribe.client.screen.BookScreen;
import scribe.client.screen.CoverEditScreen;
import scribe.client.screen.SignBookScreen;
import scribe.config.ScribeConfig;

public final class PoseCommand {
	public static final String WORD = "pose";
	static final String COMMAND = "/books " + WORD;
	static final List<Class<? extends Screen>> BOOK_SCREENS = List.of(BookScreen.class, SignBookScreen.class, CoverEditScreen.class,
		BookViewScreen.class, BookEditScreen.class, BookSignScreen.class);

	public enum Stop {
		TYPED(true),
		ESCAPE(true),
		BOOK_GONE(true),
		BOOK_SCREEN(true),
		TAKEN_OVER(false),
		DIED(true),
		NEW_PLAYER(true),
		LEFT(false);

		final boolean tells;

		Stop(boolean tells) {
			this.tells = tells;
		}
	}

	record Held(InteractionHand hand, int slot, Item item, Object player) {
	}

	static final int TURN_MIN_TICKS = 40;
	static final int TURN_MAX_TICKS = 70;

	private static @Nullable Held held;
	private static int turnIn;
	private static final RandomSource RANDOM = RandomSource.create();

	private PoseCommand() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(PoseCommand::tick);
	}

	static LiteralArgumentBuilder<FabricClientCommandSource> node() {
		return ClientCommands.literal(WORD).executes(PoseCommand::run);
	}

	public static boolean opensWithPose(ItemStack stack) {
		return (stack.is(Items.WRITABLE_BOOK) && stack.has(DataComponents.WRITABLE_BOOK_CONTENT))
			|| (stack.is(Items.WRITTEN_BOOK) && stack.has(DataComponents.WRITTEN_BOOK_CONTENT))
			|| EnchantedBooks.is(stack);
	}

	static @Nullable InteractionHand hand(ItemStack main, ItemStack off) {
		if (opensWithPose(main)) {
			return InteractionHand.MAIN_HAND;
		}
		return opensWithPose(off) ? InteractionHand.OFF_HAND : null;
	}

	public static boolean isBookScreen(@Nullable Screen screen) {
		return screen != null && isBookScreen(screen.getClass());
	}

	static boolean isBookScreen(Class<?> type) {
		for (Class<?> book : BOOK_SCREENS) {
			if (book.isAssignableFrom(type)) {
				return true;
			}
		}
		return false;
	}

	static @Nullable Component refusal(boolean settingOn, boolean bookScreen, @Nullable InteractionHand hand) {
		if (bookScreen) {
			return Component.translatable("scribe.command.pose.close_book");
		}
		if (!settingOn) {
			return Component.translatable("scribe.command.pose.setting_off");
		}
		if (hand == null) {
			return Component.translatable("scribe.command.pose.hold_book");
		}
		return null;
	}

	static Component toggle(Object player, ItemStack main, ItemStack off, int selectedSlot, boolean settingOn, boolean bookScreen)
		throws CommandSyntaxException {
		if (held != null) {
			stop(Stop.TYPED);
			return stopped();
		}
		InteractionHand hand = hand(main, off);
		Component refusal = refusal(settingOn, bookScreen, hand);
		if (refusal != null) {
			throw new SimpleCommandExceptionType(refusal).create();
		}
		ItemStack book = hand == InteractionHand.MAIN_HAND ? main : off;
		start(new Held(hand, hand == InteractionHand.MAIN_HAND ? selectedSlot : BookLimits.OFFHAND_SLOT, book.getItem(), player));
		return started();
	}

	public static Component toggleNow(Minecraft minecraft) throws CommandSyntaxException {
		LocalPlayer player = minecraft.player;
		return toggle(player, player.getItemInHand(InteractionHand.MAIN_HAND), player.getItemInHand(InteractionHand.OFF_HAND),
			player.getInventory().getSelectedSlot(), ScribeConfig.get().readingPoseShown(), isBookScreen(minecraft.gui.screen()));
	}

	private static int run(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
		FabricClientCommandSource source = context.getSource();
		LocalPlayer player = source.getPlayer();
		Minecraft minecraft = source.getClient();
		source.sendFeedback(toggle(player, player.getItemInHand(InteractionHand.MAIN_HAND), player.getItemInHand(InteractionHand.OFF_HAND),
			player.getInventory().getSelectedSlot(), ScribeConfig.get().readingPoseShown(), isBookScreen(minecraft.gui.screen())));
		return 1;
	}

	static Component started() {
		Component command = Component.literal(COMMAND).withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand(COMMAND))
			.withColor(ChatFormatting.AQUA).withUnderlined(true));
		return Component.translatable("scribe.command.pose.started", command).withStyle(ChatFormatting.GREEN);
	}

	static Component stopped() {
		return Component.translatable("scribe.command.pose.stopped");
	}

	public static boolean holding() {
		return held != null;
	}

	static void start(Held book) {
		held = book;
		turnIn = turnDelay(RANDOM);
		BookNet.tellReading(true, book.hand(), true);
	}

	static int turnDelay(RandomSource random) {
		return TURN_MIN_TICKS + random.nextInt(TURN_MAX_TICKS - TURN_MIN_TICKS + 1);
	}

	static boolean turnTick(RandomSource random) {
		if (held == null) {
			return false;
		}
		if (--turnIn > 0) {
			return false;
		}
		turnIn = turnDelay(random);
		return true;
	}

	static void turnPage() {
		BookNet.pageTurned(1);
	}

	static void stop(Stop why) {
		Held was = held;
		if (was == null) {
			return;
		}
		held = null;
		if (why.tells) {
			BookNet.tellReading(false, was.hand(), true);
		}
	}

	public static boolean escape(@Nullable LocalPlayer player) {
		if (held == null) {
			return false;
		}
		stop(Stop.ESCAPE);
		if (player != null) {
			player.sendOverlayMessage(stopped());
		}
		return true;
	}

	public static void screenTookOver() {
		stop(Stop.TAKEN_OVER);
	}

	public static void leftWorld() {
		stop(Stop.LEFT);
	}

	static @Nullable Stop check(Held book, @Nullable Object player, boolean dead, boolean bookScreen, int selectedSlot, ItemStack inHand) {
		if (player == null || player != book.player()) {
			return Stop.NEW_PLAYER;
		}
		if (dead) {
			return Stop.DIED;
		}
		if (bookScreen) {
			return Stop.BOOK_SCREEN;
		}
		if (book.hand() == InteractionHand.MAIN_HAND && selectedSlot != book.slot()) {
			return Stop.BOOK_GONE;
		}
		if (!inHand.is(book.item()) || !opensWithPose(inHand)) {
			return Stop.BOOK_GONE;
		}
		return null;
	}

	private static void tick(Minecraft minecraft) {
		Held book = held;
		if (book == null) {
			return;
		}
		LocalPlayer player = minecraft.player;
		boolean dead = player != null && (player.isDeadOrDying() || minecraft.gui.screen() instanceof DeathScreen);
		Stop why = player == null ? Stop.NEW_PLAYER : check(book, player, dead, isBookScreen(minecraft.gui.screen()),
			player.getInventory().getSelectedSlot(), player.getItemInHand(book.hand()));
		if (why == null) {
			if (!minecraft.isPaused() && turnTick(RANDOM)) {
				turnPage();
			}
			return;
		}
		stop(why);
		if (why == Stop.BOOK_GONE && player != null) {
			player.sendOverlayMessage(stopped());
		}
	}
}
