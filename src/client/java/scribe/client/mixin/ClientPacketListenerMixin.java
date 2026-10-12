package scribe.client.mixin;

import scribe.client.BookScreens;
import net.minecraft.client.Minecraft;
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
}
