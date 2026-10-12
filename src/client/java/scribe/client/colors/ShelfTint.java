package scribe.client.colors;

import scribe.access.BookPreviews;
import scribe.access.ShelfDyes;
import scribe.config.ScribeConfig;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public record ShelfTint(int slot) implements BlockTintSource {
	public static final List<BlockTintSource> SLOTS = IntStream.range(0, ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE)
		.<BlockTintSource>mapToObj(ShelfTint::new).toList();

	@Override
	public int color(BlockState state) {
		return ScribeConfig.get().shelfColorsShown() ? ColorsClient.UNDYED_SHELF_BOOK : ColorsClient.HIDDEN_SHELF_BOOK;
	}

	@Override
	public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
		if (!ScribeConfig.get().shelfColorsShown()) {
			return ColorsClient.HIDDEN_SHELF_BOOK;
		}
		BlockEntity shelf = level.getBlockEntity(pos);
		int dye = shelf instanceof ShelfDyes dyes ? dyes.scribe$slotDye(this.slot) : 0;
		ItemStack book = shelf instanceof BookPreviews previews ? previews.scribe$preview(this.slot) : ItemStack.EMPTY;
		return ColorsClient.shelfBookColor(dye, book);
	}

	@Override
	public int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos) {
		return -1;
	}
}
