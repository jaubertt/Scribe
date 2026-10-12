package scribe.ink;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

public final class InkLayout {
	public static final float BOX_WIDTH = 8.0F / 16.0F;
	public static final float BOX_HEIGHT = 8.0F / 16.0F;
	public static final float BOX_HEIGHT_OPEN = 1.0F;
	public static final float DISPLAY_WIDTH = 1.0F;
	public static final float DISPLAY_HEIGHT = 1.0F;
	public static final float ICON_SCALE = 0.5F;
	public static final float QUILL_UP = 8.0F / 16.0F;
	public static final float QUILL_RIGHT = 1.0F / 16.0F;
	public static final float QUILL_BACK = 1.0F / 16.0F;
	public static final int ROTATIONS = RotationSegment.getMaxSegmentIndex() + 1;
	public static final VoxelShape OUTLINE = Shapes.box(6.0 / 16.0, 0.0, 6.0 / 16.0, 10.0 / 16.0, 5.0 / 16.0, 10.0 / 16.0);
	public static final double DROP_HEIGHT = 4.0 / 16.0;
	public static final int MAX_BOTTLES = 4;
	public static final float BOX_WIDTH_SHARED = 5.0F / 16.0F;
	private static final int[][][] LAYOUTS = {
		{{8, 8}},
		{{10, 12}, {6, 4}},
		{{8, 12}, {12, 5}, {4, 4}},
		{{10, 12}, {12, 5}, {6, 4}, {4, 11}},
	};
	private static final float HALF_TURN = (float) Math.PI;

	private InkLayout() {
	}

	public static Vec3 anchor(BlockPos space) {
		return Vec3.atBottomCenterOf(space);
	}

	public static Vec3 spot(BlockPos space, int count, int index) {
		int[] at = LAYOUTS[count - 1][index];
		return new Vec3(space.getX() + at[0] / 16.0, space.getY(), space.getZ() + at[1] / 16.0);
	}

	public static float boxWidth(int count) {
		return count <= 1 ? BOX_WIDTH : BOX_WIDTH_SHARED;
	}

	public static int[] assign(BlockPos space, List<Vec3> from, int count) {
		int[] spots = new int[count];
		for (int i = 0; i < count; i++) {
			spots[i] = i;
		}
		int[] best = null;
		double bestCost = Double.MAX_VALUE;
		do {
			double cost = 0.0;
			for (int i = 0; i < from.size(); i++) {
				cost += from.get(i).distanceToSqr(spot(space, count, spots[i]));
			}
			if (cost < bestCost - 1.0E-9) {
				bestCost = cost;
				best = java.util.Arrays.copyOf(spots, from.size());
			}
		} while (nextPermutation(spots));
		return best == null ? new int[0] : best;
	}

	public static int leftOver(int[] taken, int count) {
		for (int i = 0; i < count; i++) {
			boolean used = false;
			for (int t : taken) {
				used |= t == i;
			}
			if (!used) {
				return i;
			}
		}
		return count - 1;
	}

	private static boolean nextPermutation(int[] a) {
		int i = a.length - 2;
		while (i >= 0 && a[i] >= a[i + 1]) {
			i--;
		}
		if (i < 0) {
			return false;
		}
		int j = a.length - 1;
		while (a[j] <= a[i]) {
			j--;
		}
		int t = a[i];
		a[i] = a[j];
		a[j] = t;
		for (int l = i + 1, r = a.length - 1; l < r; l++, r--) {
			t = a[l];
			a[l] = a[r];
			a[r] = t;
		}
		return true;
	}

	public static int rotationOf(float yRot) {
		return RotationSegment.convertToSegment(yRot + 180.0F);
	}

	public static int rotationOf(Direction facing) {
		return facing.getAxis() == Direction.Axis.Y ? 0 : rotationOf(facing.toYRot());
	}

	public static int normalize(int rotation) {
		return rotation & RotationSegment.getMaxSegmentIndex();
	}

	public static Matrix4f potIcon() {
		return new Matrix4f().translate(0.0F, ICON_SCALE / 2.0F, 0.0F).rotateY(-HALF_TURN).scale(ICON_SCALE);
	}

	public static Matrix4f quillIcon() {
		return new Matrix4f().translate(0.0F, QUILL_UP, 0.0F).rotateY(-HALF_TURN).translate(-QUILL_RIGHT, 0.0F, QUILL_BACK).scale(ICON_SCALE);
	}

	public static Matrix4f potFrame() {
		return new Matrix4f().translate(0.0F, 0.5F, 0.0F).rotateY(-HALF_TURN);
	}

	public static Matrix4f quillFrame(int rotation) {
		float angle = RotationSegment.convertToDegrees(normalize(rotation));
		return new Matrix4f().translate(0.0F, 0.5F, 0.0F).rotateY((float) Math.toRadians(-angle));
	}
}
