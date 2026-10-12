package scribe.ink;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;
import scribe.Scribe;

public final class InkPots {
	public static final String MARK = "scribe:ink_pot";
	public static final int MAX_STACK = 16;
	public static final String NAME_KEY = "scribe.ink.";
	public static final ResourceKey<CreativeModeTab> INGREDIENTS = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("ingredients"));
	private static final CompoundTag MARK_TAG = markTag();

	private InkPots() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Scribe.id("crafting_with_ink"), InkRecipe.SERIALIZER);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Scribe.id("ink_dyeing"), InkDyeing.SERIALIZER);
		CreativeModeTabEvents.modifyOutputEvent(INGREDIENTS).register(InkPots::addToCreative);
		PlacedInk.init();
	}

	public static void addToCreative(FabricCreativeModeTabOutput output) {
		List<DyeColor> order = new ArrayList<>();
		ItemStack lastDye = ItemStack.EMPTY;
		for (ItemStack stack : output.getDisplayStacks()) {
			DyeColor color = isPlainDye(stack) ? stack.get(DataComponents.DYE) : null;
			if (color != null) {
				lastDye = stack;
				if (!order.contains(color)) {
					order.add(color);
				}
			}
		}
		for (DyeColor color : DyeColor.values()) {
			if (!order.contains(color)) {
				order.add(color);
			}
		}
		List<ItemStack> inks = new ArrayList<>();
		for (DyeColor color : order) {
			inks.add(make(color));
		}
		if (lastDye.isEmpty()) {
			output.acceptAll(inks);
		} else {
			output.insertAfter(lastDye, inks);
		}
	}

	public static boolean isInk(ItemStack stack) {
		if (stack.isEmpty() || !stack.has(DataComponents.DYE)) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.matchedBy(MARK_TAG);
	}

	public static boolean isPlainDye(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof DyeItem && stack.get(DataComponents.DYE) != null && !isInk(stack);
	}

	public static @Nullable DyeColor color(ItemStack stack) {
		return stack.get(DataComponents.DYE);
	}

	public static ItemStack make(DyeColor color) {
		ItemStack stack = new ItemStack(Items.DYE.pick(color));
		stack.applyComponents(patch(color));
		return stack;
	}

	public static ItemStackTemplate template(DyeColor color) {
		return new ItemStackTemplate(Items.DYE.pick(color).builtInRegistryHolder(), 1, patch(color));
	}

	public static DataComponentPatch patch(DyeColor color) {
		return DataComponentPatch.builder()
			.set(DataComponents.CUSTOM_DATA, CustomData.of(markTag()))
			.set(DataComponents.MAX_STACK_SIZE, MAX_STACK)
			.set(DataComponents.ITEM_NAME, name(color))
			.build();
	}

	public static Component name(DyeColor color) {
		return Component.translatableWithFallback(NAME_KEY + color.getSerializedName(), english(color));
	}

	public static String english(DyeColor color) {
		StringBuilder out = new StringBuilder();
		for (String word : color.getSerializedName().split("_")) {
			out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1)).append(' ');
		}
		return out.append("Ink").toString();
	}

	public static @Nullable ItemStackTemplate remainder(ItemStack stack) {
		return isInk(stack) ? new ItemStackTemplate(Items.GLASS_BOTTLE) : null;
	}

	private static CompoundTag markTag() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(MARK, true);
		return tag;
	}
}
