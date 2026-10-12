package scribe.mixin;

import scribe.colors.BookColors;
import scribe.colors.ColoredBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChiseledBookShelfBlock.class)
public abstract class ChiseledBookShelfBlockMixin {
	@Inject(method = "addBook", at = @At("HEAD"))
	private static void scribe$addBook(Level level, BlockPos pos, Player player, ChiseledBookShelfBlockEntity shelf, ItemStack book, int slot, CallbackInfo ci) {
		if (level.isClientSide()) {
			((ColoredBlockEntity) shelf).scribe$setColor(slot, BookColors.dyedOrZero(book));
		}
	}
}
