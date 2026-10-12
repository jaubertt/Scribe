package scribe.client.colors;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

public final class BookLooks {
	public static final RenderStateDataKey<ColorsClient.LecternLook> LECTERN = RenderStateDataKey.create(() -> "scribe:lectern_look");
	public static final RenderStateDataKey<Boolean> SHUT_ON_LECTERN = RenderStateDataKey.create(() -> "scribe:lectern_shut");
	public static final RenderStateDataKey<Integer> TABLE_LEATHER = RenderStateDataKey.create(() -> "scribe:table_leather");

	private BookLooks() {
	}
}
