package scribe.client.mixin;

import scribe.client.command.PoseCommand;
import scribe.client.screen.BookEditorScreen;
import scribe.client.screen.SignBookScreen;
import scribe.config.ScribeConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@ModifyExpressionValue(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/GameNarrator;isActive()Z"))
	private boolean scribe$noNarratorWhileWriting(boolean active) {
		Screen screen = Minecraft.getInstance().gui.screen();
		return active && !(screen instanceof BookEditorScreen || screen instanceof SignBookScreen
			|| screen instanceof AbstractSignEditScreen && ScribeConfig.get().writingUtilitiesOn());
	}

	@WrapWithCondition(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;pauseGame(Z)V"))
	private boolean scribe$escapeClosesBook(Minecraft minecraft, boolean pauseOnly) {
		return pauseOnly || minecraft.gui.screen() != null || !PoseCommand.escape(minecraft.player);
	}
}
