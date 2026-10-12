package scribe.binding;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import org.jspecify.annotations.Nullable;

public record Binding(@Nullable String strap, @Nullable ResourceKey<TrimMaterial> trim, @Nullable String clasp) {
	public static final Binding NONE = new Binding(null, null, null);
	public static final String LEATHER = "leather";
	public static final int LEATHER_COLOR = DyedItemColor.LEATHER_COLOR;

	private static final int STRAP_FLAG = 0;
	private static final int TRIM = 0;
	private static final int STRAP_DYE = 1;
	static final int CLASP = 7;

	public Binding {
		clasp = clasp == null || clasp.isEmpty() ? null : clasp;
	}

	public Binding(@Nullable String strap, @Nullable ResourceKey<TrimMaterial> trim) {
		this(strap, trim, null);
	}

	public boolean isEmpty() {
		return this.strap == null && this.trim == null && this.clasp == null;
	}

	public boolean hasStrap() {
		return this.strap != null;
	}

	public Binding withStrap(@Nullable DyeColor dye) {
		return new Binding(dye == null ? LEATHER : dye.getSerializedName(), this.trim, this.clasp);
	}

	public Binding withTrim(ResourceKey<TrimMaterial> material) {
		return new Binding(this.strap, material, this.clasp);
	}

	public Binding withClasp(@Nullable String material) {
		return new Binding(this.strap, this.trim, material);
	}

	public @Nullable DyeColor strapDye() {
		return this.strap == null || LEATHER.equals(this.strap) ? null : DyeColor.byName(this.strap, null);
	}

	public int strapColor() {
		DyeColor dye = this.strapDye();
		return (dye == null ? LEATHER_COLOR : dye.getTextureDiffuseColor()) & 0xFFFFFF;
	}

	public static Binding of(ItemStack stack) {
		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		if (data == null) {
			return NONE;
		}
		String strap = null;
		if (data.getBoolean(STRAP_FLAG) == Boolean.TRUE) {
			String dye = data.getString(STRAP_DYE);
			strap = dye == null || dye.isEmpty() ? LEATHER : dye;
		}
		ResourceKey<TrimMaterial> trim = null;
		String material = data.getString(TRIM);
		if (material != null && !material.isEmpty()) {
			Identifier id = Identifier.tryParse(material);
			if (id != null) {
				trim = ResourceKey.create(Registries.TRIM_MATERIAL, id);
			}
		}
		return new Binding(strap, trim, data.getString(CLASP));
	}

	public void applyTo(ItemStack stack) {
		CoverDesign.write(stack, this, CoverDesign.of(stack));
	}

	CustomModelData toModelData() {
		List<String> strings = new ArrayList<>(2);
		strings.add(this.trim == null ? "" : this.trim.identifier().toString());
		if (this.strap != null) {
			strings.add(this.strap);
		}
		if (this.clasp != null) {
			while (strings.size() < CLASP) {
				strings.add("");
			}
			strings.add(this.clasp);
		}
		return new CustomModelData(
			List.of(),
			this.strap == null ? List.of() : List.of(true),
			List.copyOf(strings),
			this.strap == null ? List.of() : List.of(this.strapColor()));
	}

	public static ItemStack removeFrom(ItemStack stack) {
		if (!of(stack).isEmpty()) {
			CoverDesign.write(stack, NONE, CoverDesign.of(stack));
		}
		return stack;
	}

	public Optional<String> trimName() {
		return Optional.ofNullable(this.trim).map(key -> key.identifier().getPath());
	}
}
