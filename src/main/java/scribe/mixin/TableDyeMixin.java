package scribe.mixin;

import scribe.colors.TableDye;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantingTableBlockEntity.class)
public abstract class TableDyeMixin extends BlockEntity {
	protected TableDyeMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Inject(method = "collectImplicitComponents", at = @At("TAIL"))
	private void scribe$dyeToItem(DataComponentMap.Builder components, CallbackInfo ci) {
		components.set(DataComponents.DYED_COLOR, TableDye.get(this));
	}

	@Inject(method = "applyImplicitComponents", at = @At("TAIL"))
	private void scribe$dyeFromItem(DataComponentGetter components, CallbackInfo ci) {
		TableDye.set(this, components.get(DataComponents.DYED_COLOR));
	}

	@Inject(method = "loadAdditional", at = @At("TAIL"))
	private void scribe$dyeFromOlderSave(ValueInput input, CallbackInfo ci) {
		input.read(TableDye.LEGACY_KEY, DyedItemColor.CODEC).ifPresent(dye -> TableDye.set(this, dye));
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		Packet<ClientGamePacketListener> packet = super.getUpdatePacket();
		return packet != null ? packet : ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		tag.merge(this.saveCustomOnly(registries));
		return tag;
	}
}
