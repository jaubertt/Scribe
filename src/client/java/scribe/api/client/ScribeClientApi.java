package scribe.api.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import scribe.client.GuideBook;
import scribe.client.command.PoseCommand;
import scribe.client.screen.BookReaderScreen;

public final class ScribeClientApi {
	private ScribeClientApi() {
	}

	public static boolean openBook(ItemStack book) {
		if (BookViewScreen.BookAccess.fromItem(book) == null) {
			return false;
		}
		Minecraft.getInstance().gui.setScreen(new BookReaderScreen(book.copy(), null) {
			{
				this.setOnCover(true);
			}
		});
		return true;
	}

	public static void openGuide() {
		GuideBook.open(null);
	}

	public static boolean isBookScreen(@Nullable Screen screen) {
		return PoseCommand.isBookScreen(screen);
	}
}
