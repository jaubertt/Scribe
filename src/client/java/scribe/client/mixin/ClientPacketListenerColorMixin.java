package scribe.client.mixin;

import scribe.colors.BookColors;
import scribe.client.colors.ColoredBookScreen;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerColorMixin {
	@ModifyArg(method = "handleOpenBook", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
	private Screen scribe$color(Screen screen, @Local(argsOnly = true) ClientboundOpenBookPacket packet) {
		Minecraft minecraft = Minecraft.getInstance();
		if (screen instanceof ColoredBookScreen colored && minecraft.player != null) {
			colored.scribe$setColor(BookColors.of(minecraft.player.getItemInHand(packet.hand())));
		}
		return screen;
	}
}
