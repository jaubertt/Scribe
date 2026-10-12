package scribe.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import scribe.Scribe;
import scribe.client.command.PoseCommand;
import scribe.client.screen.SettingsScreen;

public final class ScribeKeys {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Scribe.id("keys"));
	public static final KeyMapping GUIDE = key("guide");
	public static final KeyMapping SETTINGS = key("settings");
	public static final KeyMapping POSE = key("pose");

	private ScribeKeys() {
	}

	private static KeyMapping key(String name) {
		return new KeyMapping("scribe.key." + name, InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
	}

	public static void init() {
		KeyMappingHelper.registerKeyMapping(GUIDE);
		KeyMappingHelper.registerKeyMapping(SETTINGS);
		KeyMappingHelper.registerKeyMapping(POSE);
		ClientTickEvents.END_CLIENT_TICK.register(ScribeKeys::tick);
	}

	private static void tick(Minecraft minecraft) {
		while (GUIDE.consumeClick()) {
			GuideBook.open(null);
		}
		while (SETTINGS.consumeClick()) {
			minecraft.gui.setScreen(new SettingsScreen(null));
		}
		while (POSE.consumeClick()) {
			if (minecraft.player != null) {
				try {
					minecraft.player.sendSystemMessage(PoseCommand.toggleNow(minecraft));
				} catch (CommandSyntaxException e) {
					minecraft.player.sendSystemMessage(Component.literal(e.getMessage()).withStyle(net.minecraft.ChatFormatting.RED));
				}
			}
		}
	}
}
