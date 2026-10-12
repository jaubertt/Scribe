package scribe.client.colors;

import net.minecraft.util.ARGB;

public final class LevelShade {
	static final float LIGHTEST = 0.45F;
	static final float DARKEST = 0.45F;

	private LevelShade() {
	}

	public static int shade(int color, int level, int maxLevel) {
		if (maxLevel <= 1 || level <= 0) {
			return color;
		}
		float t = Math.clamp((level - 1) / (float) (maxLevel - 1), 0.0F, 1.0F);
		if (t < 0.5F) {
			return mix(color, 0xFFFFFF, LIGHTEST * (1.0F - 2.0F * t));
		}
		return mix(color, 0x000000, DARKEST * (2.0F * t - 1.0F));
	}

	private static int mix(int color, int target, float amount) {
		return ARGB.color(ARGB.alpha(color),
			channel(ARGB.red(color), ARGB.red(target), amount),
			channel(ARGB.green(color), ARGB.green(target), amount),
			channel(ARGB.blue(color), ARGB.blue(target), amount));
	}

	private static int channel(int from, int to, float amount) {
		return Math.round(from + (to - from) * amount);
	}
}
