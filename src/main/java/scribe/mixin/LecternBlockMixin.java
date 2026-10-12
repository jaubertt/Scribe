package scribe.mixin;

import scribe.ClosedLecterns;
import scribe.EnchantedBooks;
import scribe.config.ScribeConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LecternBlock.class)
public abstract class LecternBlockMixin {
	@ModifyExpressionValue(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean scribe$enchantedBooks(boolean lecternBook, @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) Level level) {
		if (lecternBook || !EnchantedBooks.is(stack)) {
			return lecternBook;
		}
		return level.isClientSide() ? EnchantedBooks.clientAllows() : ScribeConfig.get().enchantedLecternsOn();
	}

	@Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
	private void scribe$closeOrOpen(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		if (!level.isClientSide() && player.isSecondaryUseActive() && level.getBlockEntity(pos) instanceof LecternBlockEntity lectern
			&& ClosedLecterns.toggle(level, pos, lectern, player)) {
			cir.setReturnValue(InteractionResult.SUCCESS);
		}
	}
}
