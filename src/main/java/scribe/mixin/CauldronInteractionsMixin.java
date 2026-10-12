package scribe.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scribe.colors.AuthorDyeing;

@Mixin(CauldronInteractions.class)
public abstract class CauldronInteractionsMixin {
	@Inject(method = "dyedItemIteration", at = @At("HEAD"), cancellable = true)
	private static void scribe$authorWashes(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack itemInHand,
		CallbackInfoReturnable<InteractionResult> cir) {
		InteractionResult refused = AuthorDyeing.wash(level, player, itemInHand);
		if (refused != null) {
			cir.setReturnValue(refused);
		}
	}
}
