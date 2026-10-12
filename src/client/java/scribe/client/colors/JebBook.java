package scribe.client.colors;

import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class JebBook {
	public static final String NAME = "jeb_";
	private static final int TICKS_PER_COLOR = 25;
	private static final int SEARCH = 6;

	private JebBook() {
	}

	public static boolean named(@Nullable Component name) {
		return name != null && NAME.equals(name.getString());
	}

	public static int color(float time) {
		return color(time, DyeColor::getTextureDiffuseColor);
	}

	public static int color(float time, ToIntFunction<DyeColor> palette) {
		DyeColor[] dyes = DyeColor.values();
		int ticks = Mth.floor(time);
		int step = Math.floorDiv(ticks, TICKS_PER_COLOR);
		int from = Math.floorMod(step, dyes.length);
		int to = Math.floorMod(step + 1, dyes.length);
		float between = (Math.floorMod(ticks, TICKS_PER_COLOR) + Mth.frac(time)) / TICKS_PER_COLOR;
		return ARGB.srgbLerp(between, palette.applyAsInt(dyes[from]), palette.applyAsInt(dyes[to]));
	}

	public static float time(EnchantingTableBlockEntity table, float partialTick) {
		return table.time + partialTick;
	}

	public static int screenColor(@Nullable Component title, @Nullable EnchantingTableBlockEntity table, float partialTick, float clock, int tableColor) {
		boolean jeb = named(title) || table != null && named(table.getCustomName());
		if (!jeb) {
			return tableColor;
		}
		return color(table != null ? time(table, partialTick) : clock);
	}

	public static @Nullable BlockPos openedTable(Function<BlockPos, @Nullable BlockEntity> blocks, @Nullable HitResult hit, @Nullable Vec3 eyes) {
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
			&& blocks.apply(block.getBlockPos()) instanceof EnchantingTableBlockEntity) {
			return block.getBlockPos().immutable();
		}
		if (eyes == null) {
			return null;
		}
		BlockPos center = BlockPos.containing(eyes);
		BlockPos best = null;
		double nearest = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SEARCH, -SEARCH, -SEARCH), center.offset(SEARCH, SEARCH, SEARCH))) {
			if (blocks.apply(pos) instanceof EnchantingTableBlockEntity) {
				double distance = pos.distToCenterSqr(eyes);
				if (distance < nearest) {
					nearest = distance;
					best = pos.immutable();
				}
			}
		}
		return best;
	}
}
