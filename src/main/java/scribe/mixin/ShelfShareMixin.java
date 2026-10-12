package scribe.mixin;

import scribe.access.BookPreviews;
import scribe.access.ShelfDyes;
import scribe.colors.BookColors;
import scribe.config.ScribeConfig;
import scribe.server.BookPreview;
import scribe.server.ShelfShare;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.ListBackedContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChiseledBookShelfBlockEntity.class)
public abstract class ShelfShareMixin extends BlockEntity implements ListBackedContainer, BookPreviews, ShelfDyes {
	@Unique
	private ShelfShare scribe$heard = ShelfShare.NOTHING;

	protected ShelfShareMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		Packet<ClientGamePacketListener> packet = super.getUpdatePacket();
		return packet != null ? packet : ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		tag.store(ShelfShare.KEY, ShelfShare.CODEC, registries.createSerializationContext(NbtOps.INSTANCE),
			ShelfShare.of(this, ScribeConfig.get().bookInfoOn()));
		return tag;
	}

	@Inject(method = "loadAdditional", at = @At("TAIL"))
	private void scribe$hear(ValueInput input, CallbackInfo ci) {
		if (this.scribe$onClient()) {
			this.scribe$heard = input.read(ShelfShare.KEY, ShelfShare.CODEC).orElse(ShelfShare.NOTHING);
		}
	}

	@Override
	public int scribe$slotDye(int slot) {
		return this.scribe$onClient() ? this.scribe$heard.dye(slot) : BookColors.dyedOrZero(this.scribe$stack(slot));
	}

	@Override
	public ItemStack scribe$preview(int slot) {
		return this.scribe$onClient() ? this.scribe$heard.book(slot) : BookPreview.of(this.scribe$stack(slot));
	}

	@Unique
	private ItemStack scribe$stack(int slot) {
		return slot >= 0 && slot < this.getContainerSize() ? this.getItem(slot) : ItemStack.EMPTY;
	}

	@Unique
	private boolean scribe$onClient() {
		return this.level != null && this.level.isClientSide();
	}
}
