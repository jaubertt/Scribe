package scribe.client;

import scribe.client.placed.PlacedBooksClient;

import scribe.client.colors.ColorsClient;
import scribe.client.colors.WrittenBookGlint;
import scribe.client.command.BooksCommand;
import scribe.client.command.PoseCommand;
import scribe.client.memory.PageMemory;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

public final class ScribeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> BooksCommand.register(dispatcher));
		UseItemCallback.EVENT.addPhaseOrdering(BookScreens.FIRST, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
		UseItemCallback.EVENT.register(BookScreens.FIRST, BookScreens::useItem);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> PageMemory.get().save());
		ClientLifecycleEvents.CLIENT_STARTED.register(WrittenBookGlint::load);
		ColorsClient.init();
		BookNet.init();
		SubtitleTooltip.init();
		BookInfoTooltip.init();
		AuthorNames.init();
		LookAtTooltip.init();
		ReadingPoseRenderer.init();
		ReadingPose.init();
		LecternMotions.init();
		PoseCommand.init();
		SignTools.init();
		PlacedBooksClient.init();
		scribe.client.ink.InkPotsClient.init();
		LibrarianBooks.init();
		LibrarianBookRenderer.init();
		ScribeKeys.init();
	}
}
