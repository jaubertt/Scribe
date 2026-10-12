package scribe.colors;

import scribe.Scribe;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ColorSetup {
	public static final String SHELF_VARIANTS_MOD = "lolmcbv";

	private ColorSetup() {
	}

	public static void init() {
		TableDye.register();
		Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, Scribe.id("random_dye"), RandomDyeFunction.MAP_CODEC);
		DefaultItemComponentEvents.MODIFY.register(context -> {
			DyeOptions options = DyeOptions.get();
			dull(context, Items.WRITTEN_BOOK, options.writtenBooksShine);
			dull(context, Items.ENCHANTED_BOOK, options.enchantedBooksShine);
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> DyeOptions.reload());
		TableDyeing.init();
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.isModLoaded(SHELF_VARIANTS_MOD)) {
			loader.getModContainer(Scribe.MOD_ID).ifPresent(scribe ->
				ResourceLoader.registerBuiltinPack(Scribe.id("compat/" + SHELF_VARIANTS_MOD), scribe, PackActivationType.ALWAYS_ENABLED));
		}
	}

	private static void dull(DefaultItemComponentEvents.ModifyContext context, Item book, boolean shines) {
		if (!shines) {
			context.modify(book, components -> components.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, null));
		}
	}
}
