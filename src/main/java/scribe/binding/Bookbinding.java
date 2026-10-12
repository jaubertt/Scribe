package scribe.binding;

import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import org.jspecify.annotations.Nullable;
import scribe.Scribe;

public final class Bookbinding {
	public static final List<String> MATERIALS = List.of(
		"quartz", "iron", "netherite", "redstone", "copper", "gold", "emerald", "diamond", "lapis", "amethyst", "resin");
	public static final int COPY_OF_COPY = 2;

	private Bookbinding() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Scribe.id("bookbinding"), BookbindingRecipe.SERIALIZER);
	}

	public static boolean isSupported(ResourceKey<TrimMaterial> material) {
		Identifier id = material.identifier();
		return Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace()) && MATERIALS.contains(id.getPath());
	}

	public static @Nullable String materialName(Binding binding) {
		return binding.trim() != null && isSupported(binding.trim()) ? binding.trimName().orElse(null) : null;
	}

	public static boolean isBindable(ItemStack stack) {
		return stack.has(DataComponents.WRITTEN_BOOK_CONTENT) || stack.has(DataComponents.WRITABLE_BOOK_CONTENT);
	}

	public static boolean canTrim(ItemStack book) {
		if (book.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
			return true;
		}
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		return content != null && content.generation() < COPY_OF_COPY;
	}

	public static boolean canUnbind(ItemStack stack) {
		return isBindable(stack) && !Binding.of(stack).isEmpty();
	}

	public static @Nullable ItemStack unbound(ItemStack input, ItemStack additional) {
		ItemStack book = input.isEmpty() ? additional : additional.isEmpty() ? input : ItemStack.EMPTY;
		if (book.isEmpty() || book.getCount() != 1 || !canUnbind(book)) {
			return null;
		}
		return Binding.removeFrom(book.copy());
	}
}
