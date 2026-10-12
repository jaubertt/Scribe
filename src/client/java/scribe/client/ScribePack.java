package scribe.client;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import scribe.Scribe;
import scribe.colors.ColorSetup;

public final class ScribePack {
	public static final String ID = ColorSetup.BOOKS_PACK.toString();
	private static volatile boolean selected = true;

	private ScribePack() {
	}

	public static void init() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Scribe.id("books_pack"), (ResourceManagerReloadListener) ScribePack::reloaded);
	}

	public static boolean selected() {
		return selected;
	}

	public static boolean in(ResourceManager resources) {
		return resources.listPacks().anyMatch(pack -> ID.equals(pack.packId()));
	}

	public static void reloaded(ResourceManager resources) {
		selected = in(resources);
	}
}
