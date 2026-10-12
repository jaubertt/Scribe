package scribe.mixin;

import scribe.access.BookPreviews;
import scribe.config.ScribeConfig;
import scribe.server.BookPreview;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import scribe.colors.BookColors;
import scribe.colors.ColoredBlockEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.ListBackedContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChiseledBookShelfBlockEntity.class)
public abstract class ChiseledBookShelfBlockEntityMixin extends BlockEntity implements ListBackedContainer, ColoredBlockEntity, BookPreviews {
	@Unique
	private static final String COLORS_KEY = "scribe:colors";
	@Unique
	private static final String BOOKS_KEY = "scribe:books";
	@Unique
	private static final Codec<List<ItemStack>> PREVIEWS_CODEC = ItemStack.OPTIONAL_CODEC.listOf();
	@Unique
	private final int[] scribe$colors = new int[ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE];
	@Unique
	private final ItemStack[] scribe$previews = new ItemStack[ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE];

	protected ChiseledBookShelfBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public int scribe$getColor(int index) {
		return index >= 0 && index < this.scribe$colors.length ? this.scribe$colors[index] : -1;
	}

	@Override
	public void scribe$setColor(int index, int color) {
		if (index >= 0 && index < this.scribe$colors.length) {
			this.scribe$colors[index] = color;
		}
	}

	@Override
	public void scribe$writeSyncData(HolderLookup.Provider registries, CompoundTag tag) {
		tag.putIntArray(COLORS_KEY, this.scribe$colors);
		if (ScribeConfig.get().bookInfoOn()) {
			List<ItemStack> previews = new ArrayList<>();
			for (int i = 0; i < this.scribe$colors.length; i++) {
				previews.add(BookPreview.of(this.getItem(i)));
			}
			tag.store(BOOKS_KEY, PREVIEWS_CODEC, registries.createSerializationContext(NbtOps.INSTANCE), previews);
		}
	}

	@Override
	public ItemStack scribe$preview(int slot) {
		if (slot < 0 || slot >= this.scribe$previews.length) {
			return ItemStack.EMPTY;
		}
		if (this.level != null && this.level.isClientSide()) {
			ItemStack preview = this.scribe$previews[slot];
			return preview == null ? ItemStack.EMPTY : preview;
		}
		return BookPreview.of(this.getItem(slot));
	}

	@Inject(method = "loadAdditional", at = @At("RETURN"))
	private void scribe$load(ValueInput input, CallbackInfo ci) {
		Optional<int[]> synced = input.getIntArray(COLORS_KEY);
		if (this.level != null && this.level.isClientSide() && synced.isPresent()) {
			int[] colors = synced.get();
			if (colors.length == this.scribe$colors.length) {
				System.arraycopy(colors, 0, this.scribe$colors, 0, colors.length);
			}
			List<ItemStack> previews = input.read(BOOKS_KEY, PREVIEWS_CODEC).orElse(List.of());
			for (int i = 0; i < this.scribe$previews.length; i++) {
				this.scribe$previews[i] = i < previews.size() ? previews.get(i) : ItemStack.EMPTY;
			}
			return;
		}
		for (int i = 0; i < this.scribe$colors.length; i++) {
			this.scribe$colors[i] = BookColors.dyedOrZero(this.getItem(i));
		}
	}

	@Inject(method = "updateState", at = @At("HEAD"))
	private void scribe$updateState(int slot, CallbackInfo ci) {
		if (slot >= 0 && slot < this.scribe$colors.length) {
			this.scribe$colors[slot] = BookColors.dyedOrZero(this.getItem(slot));
		}
	}
}
