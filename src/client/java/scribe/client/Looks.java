package scribe.client;

import net.minecraft.client.Minecraft;
import scribe.client.colors.WrittenBookGlint;
import scribe.config.ScribeConfig;

public final class Looks {
	private static boolean shelfColors = true;
	private static boolean neutralGlint = true;

	private Looks() {
	}

	public static void init() {
		ScribeConfig config = ScribeConfig.get();
		shelfColors = config.shelfColorsShown();
		neutralGlint = config.neutralGlintShown();
		ScribeConfig.onReload(() -> Minecraft.getInstance().execute(Looks::apply));
	}

	public static boolean coverDesigner() {
		return ScribeConfig.get().designerShown() && BookNet.coverDesigns();
	}

	public static boolean closedBooks() {
		return ScribeConfig.get().closedBooksShown() && BookNet.closedLecterns();
	}

	public static void apply() {
		Minecraft minecraft = Minecraft.getInstance();
		ScribeConfig config = ScribeConfig.get();
		if (config.shelfColorsShown() != shelfColors) {
			shelfColors = config.shelfColorsShown();
			if (minecraft.level != null) {
				minecraft.levelExtractor.allChanged();
			}
		}
		if (config.neutralGlintShown() != neutralGlint) {
			neutralGlint = config.neutralGlintShown();
			WrittenBookGlint.load(minecraft);
		}
	}
}
