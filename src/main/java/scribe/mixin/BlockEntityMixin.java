package scribe.mixin;

import scribe.colors.ColoredBlockEntity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {
	@ModifyReturnValue(method = "getUpdatePacket", at = @At("RETURN"))
	private Packet<ClientGamePacketListener> scribe$updatePacket(Packet<ClientGamePacketListener> original) {
		if (original == null && (Object) this instanceof ColoredBlockEntity) {
			return ClientboundBlockEntityDataPacket.create((BlockEntity) (Object) this);
		}
		return original;
	}

	@ModifyReturnValue(method = "getUpdateTag", at = @At("RETURN"))
	private CompoundTag scribe$updateTag(CompoundTag original, @Local(argsOnly = true) HolderLookup.Provider registries) {
		if ((Object) this instanceof ColoredBlockEntity colored) {
			colored.scribe$writeSyncData(registries, original);
		}
		return original;
	}
}
