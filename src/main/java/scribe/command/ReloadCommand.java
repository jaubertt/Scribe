package scribe.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import scribe.config.ScribeConfig;
import scribe.server.LiveSettings;
import scribe.server.ServerBooks;

public final class ReloadCommand {
	public static final String FILE = "config/scribe/config.json";

	private ReloadCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("scribe")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("reload")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ReloadCommand::reload)));
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		int before = ServerBooks.features();
		String error = ScribeConfig.reload();
		if (error != null) {
			source.sendFailure(failed(error));
			return 0;
		}
		int told = LiveSettings.changed(source.getServer(), before);
		source.sendSuccess(() -> reloaded(told), true);
		return 1;
	}

	public static Component reloaded(int players) {
		return Component.translatableWithFallback("scribe.command.reload.done",
			"Scribe settings read again from %s. Players with Scribe told: %s", FILE, players).withStyle(ChatFormatting.GREEN);
	}

	public static Component failed(String error) {
		return Component.translatableWithFallback("scribe.command.reload.failed",
			"Could not read %s, the settings stay as they were: %s", FILE, error);
	}
}
