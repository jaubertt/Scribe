package scribe.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import scribe.client.Looks;
import scribe.config.ScribeConfig;

public class PresetsScreen extends OptionsSubScreen {
	public PresetsScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable("scribe.presets.title"));
	}

	@Override
	protected void addOptions() {
		if (this.list == null) {
			return;
		}
		for (SettingsPresets.Preset preset : SettingsPresets.Preset.values()) {
			this.list.addBig(Button.builder(Component.translatable(preset.key()), b -> this.apply(preset))
				.tooltip(Tooltip.create(Component.translatable(preset.key() + ".tooltip"))).build());
		}
	}

	private void apply(SettingsPresets.Preset preset) {
		ScribeConfig config = ScribeConfig.get();
		preset.apply(config);
		config.save();
		Looks.apply();
		this.onClose();
	}
}
