package scribe.binding;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import org.jspecify.annotations.Nullable;

public final class Clasp {
	public static final String LEATHER = "leather";

	private Clasp() {
	}

	public static @Nullable String shown(Binding binding) {
		if (!binding.hasStrap()) {
			return null;
		}
		String own = own(binding);
		return own != null ? own : LEATHER;
	}

	public static @Nullable String shown(ItemStack book) {
		return shown(Binding.of(book));
	}

	public static @Nullable String own(Binding binding) {
		String clasp = binding.clasp();
		if (clasp == null) {
			return null;
		}
		if (LEATHER.equals(clasp)) {
			return LEATHER;
		}
		Identifier id = Identifier.tryParse(clasp);
		return id != null && Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace()) && Bookbinding.MATERIALS.contains(id.getPath()) ? id.getPath() : null;
	}

	public static boolean isLeather(@Nullable String clasp) {
		return LEATHER.equals(clasp);
	}

	public static @Nullable String metal(Binding binding) {
		String shown = shown(binding);
		return shown == null || isLeather(shown) ? null : shown;
	}

	public static @Nullable String metal(ItemStack book) {
		return metal(Binding.of(book));
	}

	public static String stored(ResourceKey<TrimMaterial> material) {
		return material.identifier().toString();
	}
}
