package scribe.client.colors;

import scribe.EnchantedBooks;
import scribe.access.BookPreviews;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class ShelfGlint {
	public static final int SLOTS = ChiseledBookShelfBlockEntity.MAX_BOOKS_IN_STORAGE;

	private static final int[][] FACES = {
		{10, 8, 16, 16}, {5, 8, 10, 16}, {0, 8, 5, 16},
		{10, 0, 16, 8}, {5, 0, 10, 8}, {0, 0, 5, 8}
	};
	private static final int[][] BOOKS = {
		{11, 9, 15, 15}, {6, 9, 10, 15}, {1, 9, 5, 15},
		{11, 1, 15, 7}, {6, 1, 10, 7}, {1, 1, 5, 7}
	};

	private ShelfGlint() {
	}

	public static boolean shines(ItemStack book) {
		return EnchantedBooks.is(book) && book.hasFoil();
	}

	public static int slots(BookPreviews books, BlockState state) {
		int slots = 0;
		for (int slot = 0; slot < SLOTS; slot++) {
			if (shines(books.scribe$preview(slot)) && occupied(state, slot)) {
				slots |= 1 << slot;
			}
		}
		return slots != 0 && state.hasProperty(HorizontalDirectionalBlock.FACING) ? slots : 0;
	}

	private static boolean occupied(BlockState state, int slot) {
		BooleanProperty property = ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(slot);
		return !state.hasProperty(property) || state.getValue(property);
	}

	public static int[] face(int slot) {
		return FACES[slot].clone();
	}

	public static int[] books(int slot) {
		return BOOKS[slot].clone();
	}

	public static void turn(PoseStack poseStack, Direction facing) {
		if (facing != Direction.NORTH) {
			poseStack.translate(0.5F, 0.0F, 0.5F);
			poseStack.rotateDegrees(Axis.YP, 180.0F - facing.toYRot());
			poseStack.translate(-0.5F, 0.0F, -0.5F);
		}
	}
}
