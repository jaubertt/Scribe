package scribe.client.colors;

import scribe.client.BookNet;
import scribe.colors.BookColors;
import scribe.colors.TableDye;
import scribe.config.ScribeConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import org.jspecify.annotations.Nullable;

public final class TableLeather {
	private TableLeather() {
	}

	public static int inWorld(EnchantingTableBlockEntity table, float time) {
		if (!ScribeConfig.get().tableLookShown()) {
			return BookColors.DEFAULT;
		}
		return JebBook.named(table.getCustomName()) ? JebBook.color(time) : TableDye.rgb(table);
	}

	public static int onScreen(@Nullable Component title, @Nullable EnchantingTableBlockEntity table, float partialTick, float clock) {
		if (!ScribeConfig.get().tableLookShown()) {
			return GuiBookTint.NO_TINT;
		}
		int dyed = table != null && BookNet.scribeOnServer() ? TableDye.rgb(table) : GuiBookTint.NO_TINT;
		return JebBook.screenColor(title, table, partialTick, clock, dyed);
	}
}
