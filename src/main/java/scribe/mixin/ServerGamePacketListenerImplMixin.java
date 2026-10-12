package scribe.mixin;

import net.minecraft.core.component.DataComponents;
import scribe.api.ScribeEvents;

import scribe.RealAuthor;
import scribe.server.ServerBooks;
import scribe.server.SignCodes;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleEditBook", at = @At("HEAD"))
	private void scribe$inOrder(ServerboundEditBookPacket packet, CallbackInfo ci) {
		PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this, this.player.level());
	}

	@ModifyArg(method = "signBook", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setItem(ILnet/minecraft/world/item/ItemStack;)V"), index = 1)
	private ItemStack scribe$signDetails(ItemStack written, @Local(argsOnly = true) int slot) {
		ItemStack signed = RealAuthor.stamp(ServerBooks.linkContents(ServerBooks.applySignDetails(written, ServerBooks.takeSignDetails(this.player, slot))), this.player);
		ScribeEvents.BOOK_SIGNED.invoker().signed(this.player, signed, null);
		return signed;
	}

	@Inject(method = "updateBookContents", at = @At("TAIL"))
	private void scribe$edited(List<FilteredText> pages, int slot, CallbackInfo ci) {
		ItemStack book = this.player.getInventory().getItem(slot);
		if (book.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
			ScribeEvents.BOOK_EDITED.invoker().edited(this.player, book, null);
		}
	}

	@ModifyArg(method = "updateSignText", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/block/entity/SignBlockEntity;updateSignText(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/entity/SignTextSlot;Ljava/util/List;)V"),
		index = 2)
	private List<FilteredText> scribe$signFormatting(List<FilteredText> lines, @Local(argsOnly = true) ServerboundSignUpdatePacket packet) {
		return SignCodes.keepCodes(packet.lines(), lines);
	}
}
