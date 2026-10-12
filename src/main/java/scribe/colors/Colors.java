package scribe.colors;

import scribe.Scribe;
import scribe.colors.loot.DyeRandomlyFunction;
import scribe.colors.recipe.DyedRecipe;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

public final class Colors {
	public static final RecipeSerializer<DyedRecipe<ShapedRecipe>> DYED_SHAPED = DyedRecipe.createSerializer(ShapedRecipe.SERIALIZER);
	public static final RecipeSerializer<DyedRecipe<ShapelessRecipe>> DYED_SHAPELESS = DyedRecipe.createSerializer(ShapelessRecipe.SERIALIZER);

	private Colors() {
	}

	private static Identifier id(String path) {
		return Scribe.id(path);
	}

	public static void init() {
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> ColorsConfig.reload());
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id("dyed_crafting_shaped"), DYED_SHAPED);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id("dyed_crafting_shapeless"), DYED_SHAPELESS);
		Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, id("dye_randomly"), DyeRandomlyFunction.CODEC);
		TableDyeing.init();
		DefaultItemComponentEvents.MODIFY.register(context -> {
			if (!ColorsConfig.get().writtenBookGlint()) {
				context.modify(Items.WRITTEN_BOOK, builder -> builder.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, null));
			}
			if (!ColorsConfig.get().enchantedBookGlint()) {
				context.modify(Items.ENCHANTED_BOOK, builder -> builder.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, null));
			}
		});
		if (FabricLoader.getInstance().isModLoaded("lolmcbv")) {
			ResourceLoader.registerBuiltinPack(id("compat/lolmcbv"), FabricLoader.getInstance().getModContainer(Scribe.MOD_ID).orElseThrow(), PackActivationType.ALWAYS_ENABLED);
		}
	}
}
