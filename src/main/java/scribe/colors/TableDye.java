package scribe.colors;

import scribe.Scribe;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public final class TableDye {
	public static final String LEGACY_KEY = "scribe:color";
	public static final AttachmentType<DyedItemColor> TYPE = AttachmentRegistry.create(Scribe.id("table_dye"),
		builder -> builder.persistent(DyedItemColor.CODEC).syncWith(DyedItemColor.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private TableDye() {
	}

	public static void register() {
	}

	public static @Nullable DyedItemColor get(BlockEntity table) {
		return ((AttachmentTarget) table).getAttached(TYPE);
	}

	public static void set(BlockEntity table, @Nullable DyedItemColor dye) {
		((AttachmentTarget) table).setAttached(TYPE, dye);
	}

	public static int rgb(BlockEntity table) {
		DyedItemColor dye = get(table);
		return dye == null ? BookColors.DEFAULT : ARGB.opaque(dye.rgb());
	}
}
