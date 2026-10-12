package scribe.client;

import scribe.EnchantedBooks;
import scribe.Scribe;
import scribe.client.compat.Keys;
import scribe.client.screen.BookEditorScreen;
import scribe.client.screen.BookReaderScreen;
import scribe.client.screen.EditTarget;
import scribe.client.screen.LecternBookScreen;
import scribe.client.screen.popup.HelpPopup;
import scribe.config.ScribeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.Level;

public final class BookScreens {
	private BookScreens() {
	}

	public static boolean openEditor(LocalPlayer player, ItemStack stack, InteractionHand hand) {
		if (!ScribeConfig.get().editorShown() || Keys.shiftHeldNow()) {
			return false;
		}
		WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
		if (content == null) {
			return false;
		}
		try {
			Minecraft.getInstance().gui.setScreen(new BookEditorScreen(new EditTarget.Hand(player, stack, hand), content));
			return true;
		} catch (RuntimeException e) {
			Scribe.LOGGER.error("Could not open Scribe's editor, using the vanilla one", e);
			return false;
		}
	}

	public static boolean openReader(InteractionHand hand) {
		if (!ScribeConfig.get().readerShown() || Keys.shiftHeldNow()) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return false;
		}
		ItemStack stack = minecraft.player.getItemInHand(hand);
		if (BookViewScreen.BookAccess.fromItem(stack) == null) {
			return false;
		}
		try {
			minecraft.gui.setScreen(new BookReaderScreen(stack, hand));
			return true;
		} catch (RuntimeException e) {
			Scribe.LOGGER.error("Could not open Scribe's reader, using the vanilla one", e);
			return false;
		}
	}

	public static final net.minecraft.resources.Identifier FIRST = Scribe.id("before_other_mods");

	public static InteractionResult useItem(Player player, Level level, InteractionHand hand) {
		if (!level.isClientSide() || !(player instanceof LocalPlayer)) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		Minecraft minecraft = Minecraft.getInstance();
		if (!opensEnchantedReader(stack, ScribeConfig.get().enchantedReaderShown(), Keys.shiftHeldNow() || player.isShiftKeyDown())
			|| minecraft.gui.screen() != null) {
			return InteractionResult.PASS;
		}
		try {
			minecraft.gui.setScreen(new BookReaderScreen(stack, hand));
			return InteractionResult.CONSUME;
		} catch (RuntimeException e) {
			Scribe.LOGGER.error("Could not open Scribe's reader for an enchanted book", e);
			return InteractionResult.PASS;
		}
	}

	public static boolean opensEnchantedReader(ItemStack stack, boolean settingOn, boolean shiftOrSneaking) {
		return settingOn && !shiftOrSneaking && EnchantedBooks.is(stack);
	}

	public static Screen replace(Screen screen) {
		if (screen != null && screen.getClass() == LecternScreen.class && ScribeConfig.get().lecternScreenShown()
			&& !vanillaLecternScreen(Keys.shiftHeldNow(), BookNet.closedLecterns())) {
			try {
				LecternMenu menu = ((LecternScreen) screen).getMenu();
				ItemStack book = menu.getBook();
				Minecraft minecraft = Minecraft.getInstance();
				WritableBookContent writable = book.get(DataComponents.WRITABLE_BOOK_CONTENT);
				if (writable != null && BookNet.lecternEditing() && minecraft.player != null && minecraft.player.mayBuild()) {
					return new BookEditorScreen(new EditTarget.Lectern(menu), writable);
				}
				return new LecternBookScreen(menu);
			} catch (RuntimeException e) {
				Scribe.LOGGER.error("Could not open Scribe's lectern screen, using the vanilla one", e);
			}
		}
		return screen;
	}

	public static boolean vanillaLecternScreen(boolean shiftHeld, boolean serverClosesBooks) {
		return shiftHeld && !serverClosesBooks;
	}

	public static HelpPopup.Line shiftHelp(boolean onLectern) {
		if (onLectern && BookNet.closedLecterns()) {
			Options options = Minecraft.getInstance().options;
			String keys = options.keyShift.getTranslatedKeyMessage().getString() + " + " + options.keyUse.getTranslatedKeyMessage().getString();
			return new HelpPopup.Line(keys, Component.translatable("scribe.help.close_lectern"));
		}
		return new HelpPopup.Line("Shift", Component.translatable("scribe.help.vanilla"));
	}
}
