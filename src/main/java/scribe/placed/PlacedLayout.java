package scribe.placed;

import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class PlacedLayout {
	public static final float PX = 16.0F;
	public static final float THICK = 2.0F;
	public static final float WIDE = 6.0F;
	public static final float TALL = 10.0F;
	public static final float GAP = 0.06F;
	public static final float STEP = THICK + GAP;
	public static final int MAX_PILE = 4;
	public static final int MAX_ROW = 8;
	public static final int MAX_TURN = 7;
	public static final float PILE_BOX = 11.0F;

	public static final float ICON_X0 = 0.0F;
	public static final float ICON_X1 = 16.0F;
	public static final float ICON_Y0 = 1.0F;
	public static final float ICON_Y1 = 15.0F;

	public static final float FLAT_SCALE = 0.5F;
	public static final float FLAT_ROW_STEP = 1.14F;
	public static final float FLAT_ROW_OUT = 1.0F;
	public static final float FLAT_ROW_FAN = 0.35F;
	public static final float FLAT_ROW_LIFT = (ICON_Y1 - ICON_Y0) * FLAT_SCALE / 2.0F;
	public static final float FLAT_ROW_RISE = 0.02F;

	private static final Matrix4fc ITEM_DISPLAY_TURN = new Matrix4f().rotateY((float) Math.PI).translate(-0.5F, -0.5F, -0.5F);
	private static final Matrix4fc ICON_TO_BOOK = new Matrix4f()
		.translate(WIDE / 2.0F / PX, 0.0F, 0.0F)
		.scale(WIDE / (ICON_X1 - ICON_X0), TALL / (ICON_Y1 - ICON_Y0), THICK)
		.translate(-(ICON_X0 + ICON_X1) / 2.0F / PX, -(ICON_Y0 + ICON_Y1) / 2.0F / PX, -0.5F);
	public static final Matrix4fc FITTED_ICON = new Matrix4f(ICON_TO_BOOK).mul(new Matrix4f(ITEM_DISPLAY_TURN).invert());
	private static final Matrix4fc FITTED_ICON_INVERSE = new Matrix4f(FITTED_ICON).invert();

	private PlacedLayout() {
	}

	public enum Kind {
		PILE(MAX_PILE), ROW(MAX_ROW);

		public final int max;

		Kind(int max) {
			this.max = max;
		}

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		public static @Nullable Kind byId(String id) {
			for (Kind kind : values()) {
				if (kind.id().equals(id)) {
					return kind;
				}
			}
			return null;
		}
	}

	public record Place(Kind kind, Direction facing, int index, int count, int turn) {
		public Matrix4f frame() {
			return bookFrame(this.kind, this.facing, this.index, this.count, this.turn);
		}

		public CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			tag.putString("kind", this.kind.id());
			tag.putString("facing", this.facing.getSerializedName());
			tag.putInt("index", this.index);
			tag.putInt("count", this.count);
			tag.putInt("turn", this.turn);
			return tag;
		}

		public static Optional<Place> fromTag(CompoundTag tag) {
			Kind kind = Kind.byId(tag.getStringOr("kind", ""));
			Direction facing = Direction.byName(tag.getStringOr("facing", ""));
			int index = tag.getIntOr("index", -1);
			int count = tag.getIntOr("count", -1);
			int turn = tag.getIntOr("turn", Integer.MIN_VALUE);
			if (kind == null || facing == null || facing.getAxis() == Direction.Axis.Y || count < 1 || count > kind.max || index < 0 || index >= count
				|| Math.abs(turn) > (kind == Kind.PILE ? MAX_TURN : 0)) {
				return Optional.empty();
			}
			return Optional.of(new Place(kind, facing, index, count, turn));
		}
	}

	public static Place place(Kind kind, Direction facing, BlockPos space, int index, int count) {
		return new Place(kind, facing, index, count, kind == Kind.PILE ? turn(space, index) : 0);
	}

	public static Kind kindFor(Direction clickedFace) {
		return clickedFace == Direction.UP ? Kind.PILE : clickedFace == Direction.DOWN ? null : Kind.ROW;
	}

	public static BlockPos spaceFor(BlockPos clicked, Direction clickedFace) {
		return clicked.relative(clickedFace);
	}

	public static Direction facingFor(Kind kind, Direction clickedFace, Direction playerFacing) {
		return kind == Kind.PILE ? playerFacing : clickedFace.getOpposite();
	}

	public static Vec3 anchor(BlockPos space, Kind kind, Direction facing) {
		double x = space.getX() + 0.5;
		double z = space.getZ() + 0.5;
		if (kind == Kind.ROW) {
			double back = (PX / 2.0F - WIDE / 2.0F - GAP) / PX;
			x += facing.getStepX() * back;
			z += facing.getStepZ() * back;
		}
		return new Vec3(x, space.getY(), z);
	}

	public static float yaw(Direction facing) {
		return (float) Math.toRadians(-facing.toYRot());
	}

	public static int turn(BlockPos space, int place) {
		long h = Mth.getSeed(space.getX(), space.getY(), space.getZ()) ^ (place * 0x9E3779B97F4A7C15L);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (int) Math.floorMod(h, 2L * MAX_TURN + 1) - MAX_TURN;
	}

	public static Matrix4f bookFrame(Kind kind, Direction facing, int index, int count, float turnDegrees) {
		Matrix4f m = new Matrix4f();
		if (kind == Kind.PILE) {
			m.translate(0.0F, (GAP + index * STEP + THICK / 2.0F) / PX, 0.0F);
			m.rotateY(yaw(facing) + (float) Math.toRadians(turnDegrees));
			m.mul(new Matrix4f().set(new float[] {-1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 1}));
		} else {
			float along = (index - (count - 1) / 2.0F) * STEP / PX;
			Direction right = facing.getClockWise();
			m.translate(right.getStepX() * along, (GAP + TALL / 2.0F) / PX, right.getStepZ() * along);
			m.rotateY(yaw(facing));
			m.mul(new Matrix4f().set(new float[] {0, 0, 1, 0, 0, 1, 0, 0, -1, 0, 0, 0, 0, 0, 0, 1}));
		}
		m.translate(-WIDE / 2.0F / PX, 0.0F, 0.0F);
		return m;
	}

	public static Matrix4f bookFrame(Kind kind, Direction facing, BlockPos space, int index, int count) {
		return place(kind, facing, space, index, count).frame();
	}

	public static Matrix4f flatIcon(Place place) {
		float yaw = yaw(place.facing());
		Matrix4f m = new Matrix4f();
		if (place.kind() == Kind.PILE) {
			m.translate(0.0F, (GAP + place.index() * STEP + THICK / 2.0F) / PX, 0.0F)
				.rotateY(yaw + (float) Math.toRadians(place.turn()))
				.rotateX((float) Math.toRadians(-90.0F));
		} else {
			Direction right = place.facing().getClockWise();
			float along = (place.index() - (place.count() - 1) / 2.0F) * FLAT_ROW_STEP / PX;
			m.translate(right.getStepX() * along, (FLAT_ROW_LIFT + FLAT_ROW_RISE * place.index()) / PX, right.getStepZ() * along)
				.rotateY(yaw + (float) Math.PI)
				.translate(0.0F, 0.0F, (FLAT_ROW_OUT + FLAT_ROW_FAN * place.index()) / PX);
		}
		return m.scale(FLAT_SCALE).rotateY((float) Math.PI);
	}

	public static Matrix4f fittedIcon(Matrix4fc bookFrame) {
		return new Matrix4f(bookFrame).mul(FITTED_ICON);
	}

	public static Matrix4f frameOfFittedIcon(Matrix4fc transformation) {
		return new Matrix4f(transformation).mul(FITTED_ICON_INVERSE);
	}

	public static Vector3f center(Matrix4fc bookFrame) {
		return bookFrame.transformPosition(new Vector3f(WIDE / 2.0F / PX, 0.0F, 0.0F));
	}

	public static float boxWidth(Kind kind, int count) {
		if (kind == Kind.PILE) {
			return PILE_BOX / PX;
		}
		return Math.max(count * STEP, WIDE + 2.0F * GAP) / PX;
	}

	public static float boxHeight(Kind kind, int count) {
		return (kind == Kind.PILE ? count * STEP : GAP + TALL) / PX;
	}

	public static int aimed(Kind kind, Direction facing, int count, Vec3 anchor, Vec3 hit) {
		if (count <= 1) {
			return 0;
		}
		if (kind == Kind.PILE) {
			double up = (hit.y - anchor.y) * PX;
			return Mth.clamp(Mth.floor(up / STEP), 0, count - 1);
		}
		return Mth.clamp(Mth.floor(along(facing, anchor, hit) / STEP + count / 2.0), 0, count - 1);
	}

	public static int addAt(Kind kind, Direction facing, int count, Vec3 anchor, Vec3 hit) {
		if (kind == Kind.PILE) {
			return count;
		}
		return along(facing, anchor, hit) >= 0.0 ? count : 0;
	}

	public static double along(Direction facing, Vec3 anchor, Vec3 hit) {
		Direction right = facing.getClockWise();
		return ((hit.x - anchor.x) * right.getStepX() + (hit.z - anchor.z) * right.getStepZ()) * PX;
	}

	public static Direction boxFace(Vec3 anchor, double width, double height, Vec3 hit) {
		double half = width / 2.0;
		double dx = hit.x - anchor.x;
		double dy = hit.y - anchor.y;
		double dz = hit.z - anchor.z;
		Direction face = Direction.UP;
		double best = Math.abs(height - dy);
		double[] distances = {Math.abs(dy), Math.abs(half - dx), Math.abs(half + dx), Math.abs(half - dz), Math.abs(half + dz)};
		Direction[] faces = {Direction.DOWN, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
		for (int i = 0; i < faces.length; i++) {
			if (distances[i] < best) {
				best = distances[i];
				face = faces[i];
			}
		}
		return face;
	}

	public static @Nullable BlockPos nextSpace(Kind kind, Direction facing, BlockPos space, Direction face) {
		if (face.getAxis() == Direction.Axis.Y) {
			return null;
		}
		if (kind == Kind.PILE) {
			return space.relative(face);
		}
		return face.getAxis() == facing.getClockWise().getAxis() ? space.relative(face) : null;
	}
}
