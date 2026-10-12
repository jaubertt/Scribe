package scribe;

import scribe.binding.Bookbinding;
import scribe.colors.ColorSetup;
import scribe.config.ScribeConfig;
import scribe.network.Payloads;
import scribe.placed.PlacedBooks;
import scribe.server.ServerBooks;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Scribe implements ModInitializer {
	public static final String MOD_ID = "scribe";
	public static final Logger LOGGER = LoggerFactory.getLogger("Scribe");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ScribeConfig.get();
		ColorSetup.init();
		Bookbinding.init();
		Payloads.register();
		ServerBooks.init();
		LibrarianLecterns.init();
		LibrarianReading.init();
		PlacedBooks.init();
		scribe.ink.InkPots.init();
		scribe.command.ReloadCommand.init();
		scribe.command.AuthorCommand.init();
		scribe.data.ScribeBooks.init();
		scribe.command.BookCommand.init();
	}
}
