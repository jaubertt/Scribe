package scribe.mixin;

import scribe.colors.BookColors;
import scribe.colors.ColoredBlockEntity;
import scribe.colors.DyedTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantingTableBlockEntity.class)
public abstract class EnchantingTableBlockEntityMixin extends BlockEntity implements ColoredBlockEntity, DyedTable {
	@Unique
	private static final String COLOR_KEY = "scribe:color";
	@Unique
	private static final String CUSTOM_NAME_KEY = "CustomName";
	@Unique
	private @Nullable DyedItemColor scribe$color;

	protected EnchantingTableBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public int scribe$getColor(int index) {
		return this.scribe$color != null ? this.scribe$color.rgb() : BookColors.DEFAULT;
	}

	@Override
	public @Nullable DyedItemColor scribe$dyedColor() {
		return this.scribe$color;
	}

	@Override
	public void scribe$setDyedColor(@Nullable DyedItemColor color) {
		this.scribe$color = color;
		this.setChanged();
	}

	@Override
	public void scribe$writeSyncData(HolderLookup.Provider registries, CompoundTag tag) {
		if (this.scribe$color != null) {
			DyedItemColor.CODEC.encodeStart(NbtOps.INSTANCE, this.scribe$color).ifSuccess(element -> tag.put(COLOR_KEY, element));
		}
		Component name = ((EnchantingTableBlockEntity) (Object) this).getCustomName();
		if (name != null) {
			tag.store(CUSTOM_NAME_KEY, ComponentSerialization.CODEC, registries.createSerializationContext(NbtOps.INSTANCE), name);
		}
	}

	@Inject(method = "saveAdditional", at = @At("RETURN"))
	private void scribe$save(ValueOutput output, CallbackInfo ci) {
		if (this.scribe$color != null) {
			output.store(COLOR_KEY, DyedItemColor.CODEC, this.scribe$color);
		}
	}

	@Inject(method = "loadAdditional", at = @At("RETURN"))
	private void scribe$load(ValueInput input, CallbackInfo ci) {
		this.scribe$color = input.read(COLOR_KEY, DyedItemColor.CODEC).orElse(null);
	}

	@Inject(method = "collectImplicitComponents", at = @At("RETURN"))
	private void scribe$toItem(DataComponentMap.Builder components, CallbackInfo ci) {
		components.set(DataComponents.DYED_COLOR, this.scribe$color);
	}

	@Inject(method = "applyImplicitComponents", at = @At("RETURN"))
	private void scribe$fromItem(DataComponentGetter components, CallbackInfo ci) {
		this.scribe$color = components.get(DataComponents.DYED_COLOR);
	}
}
