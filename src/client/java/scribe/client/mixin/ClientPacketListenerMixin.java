package scribe.client.mixin;

import scribe.client.BookScreens;
import scribe.client.colors.CoverTints;
import scribe.colors.BookColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleOpenBook", at = @At("HEAD"), cancellable = true)
	private void scribe$openReader(ClientboundOpenBookPacket packet, CallbackInfo ci) {
		if (Minecraft.getInstance().isSameThread() && BookScreens.openReader(packet.hand())) {
			ci.cancel();
		}
	}

	@Inject(method = "handleOpenBook", at = @At("RETURN"))
	private void scribe$rememberCover(ClientboundOpenBookPacket packet, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && minecraft.gui.screen() instanceof BookViewScreen opened) {
			CoverTints.remember(opened, BookColors.of(minecraft.player.getItemInHand(packet.hand())));
		}
	}
}
