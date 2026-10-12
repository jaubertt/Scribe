package scribe.binding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.jspecify.annotations.Nullable;

public record CoverDesign(@Nullable String corner, @Nullable String decoration, @Nullable String decorationLook,
	@Nullable String spine, @Nullable String titleColor, @Nullable String author, @Nullable String metalwork) {
	public static final CoverDesign NONE = new CoverDesign(null, null, null, null, null, null, null);
	public static final String NO_CORNERS = "none";
	public static final List<String> CORNERS = List.of(
		"angle_mitred", "round", "crescent_full", "scallop_edge", "fan", "window", "slats_2slot", "lozenge",
		"grooved", "banded2", "riveted", "lattice_band3", "lattice", "cloud_e", "scroll2", "baroque_plate");
	public static final List<String> DECORATIONS = List.of(
		"sentry", "vex", "wild", "dune", "wayfinder", "raiser", "shaper", "ward",
		"silence", "tide", "snout", "rib", "eye", "spire", "flow", "bolt");
	public static final String CARVED = "carved";
	public static final String NO_SPINE_METAL = "none";
	public static final List<String> TITLE_COLORS = List.of(
		"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
		"dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white");
	public static final String NO_DECORATION = "none";
	public static final String NO_TITLE_COLOR = "none";
	public static final String AUTHOR_HIDDEN = "hidden";
	public static final String AUTHOR_SHOWN = "shown";
	public static final List<String> METALWORKS = List.of("stud", "buckle", "ornate", "plaque");
	public static final String PLAQUE = "plaque";
	public static final String METALWORK_AUTO = "auto";

	static final int CORNER = 2;
	static final int DECORATION = 3;
	static final int DECORATION_LOOK = 4;
	static final int SPINE = 5;
	static final int TITLE_COLOR = 6;
	static final int AUTHOR = 8;
	static final int METALWORK = 9;
	private static final int SHORTEST = 4;

	public CoverDesign {
		corner = orNull(corner);
		decoration = orNull(decoration);
		decorationLook = orNull(decorationLook);
		spine = orNull(spine);
		titleColor = orNull(titleColor);
		author = orNull(author);
		metalwork = orNull(metalwork);
	}

	public CoverDesign(@Nullable String corner, @Nullable String decoration, @Nullable String decorationLook, @Nullable String spine,
		@Nullable String titleColor, @Nullable String author) {
		this(corner, decoration, decorationLook, spine, titleColor, author, null);
	}

	public CoverDesign(@Nullable String corner, @Nullable String decoration, @Nullable String decorationLook, @Nullable String spine,
		@Nullable String titleColor) {
		this(corner, decoration, decorationLook, spine, titleColor, null);
	}

	public CoverDesign(@Nullable String corner, @Nullable String decoration) {
		this(corner, decoration, null, null, null);
	}

	private static @Nullable String orNull(@Nullable String value) {
		return value == null || value.isEmpty() ? null : value;
	}

	public boolean isEmpty() {
		return this.corner == null && this.decoration == null && this.decorationLook == null && this.spine == null && this.titleColor == null
			&& this.author == null && this.metalwork == null;
	}

	public static CoverDesign plain(@Nullable String material) {
		return material == null ? NONE : new CoverDesign(NO_CORNERS, null, null, NO_SPINE_METAL, null);
	}

	public static boolean isCornerChoice(@Nullable String id) {
		return id != null && (NO_CORNERS.equals(id) || CORNERS.contains(id));
	}

	public static boolean isDecoration(@Nullable String id) {
		return id != null && DECORATIONS.contains(id);
	}

	public static boolean isMetal(@Nullable String id) {
		return id != null && Bookbinding.MATERIALS.contains(id);
	}

	public static boolean isDecorationLook(@Nullable String id) {
		return CARVED.equals(id) || isMetal(id);
	}

	public static boolean isSpineChoice(@Nullable String id) {
		return NO_SPINE_METAL.equals(id) || isMetal(id);
	}

	public static boolean isTitleColor(@Nullable String id) {
		return id != null && TITLE_COLORS.contains(id);
	}

	public static boolean isMetalwork(@Nullable String id) {
		return id != null && METALWORKS.contains(id);
	}

	public CoverDesign withCorner(@Nullable String id) {
		return new CoverDesign(id, this.decoration, this.decorationLook, this.spine, this.titleColor, this.author, this.metalwork);
	}

	public CoverDesign withDecoration(@Nullable String id) {
		return new CoverDesign(this.corner, id, id == null ? null : this.decorationLook, this.spine, this.titleColor, this.author, this.metalwork);
	}

	public CoverDesign withDecoration(@Nullable String id, @Nullable String look) {
		return new CoverDesign(this.corner, id, id == null ? null : look, this.spine, this.titleColor, this.author, this.metalwork);
	}

	public CoverDesign withSpine(@Nullable String id) {
		return new CoverDesign(this.corner, this.decoration, this.decorationLook, id, this.titleColor, this.author, this.metalwork);
	}

	public CoverDesign withTitleColor(@Nullable String id) {
		return new CoverDesign(this.corner, this.decoration, this.decorationLook, this.spine, id, this.author, this.metalwork);
	}

	public CoverDesign withAuthor(@Nullable String author) {
		return new CoverDesign(this.corner, this.decoration, this.decorationLook, this.spine, this.titleColor, author, this.metalwork);
	}

	public CoverDesign withMetalwork(@Nullable String id) {
		return new CoverDesign(this.corner, this.decoration, this.decorationLook, this.spine, this.titleColor, this.author, id);
	}

	public boolean hidesAuthor() {
		return AUTHOR_HIDDEN.equals(this.author);
	}

	public @Nullable String shownDecoration() {
		return isDecoration(this.decoration) ? this.decoration : null;
	}

	public @Nullable String decorationMetal() {
		return this.shownDecoration() != null && isMetal(this.decorationLook) ? this.decorationLook : null;
	}

	public @Nullable String spineMetal(Binding binding) {
		if (NO_SPINE_METAL.equals(this.spine)) {
			return null;
		}
		return isMetal(this.spine) ? this.spine : Bookbinding.materialName(binding);
	}

	public @Nullable ChatFormatting titleFormatting() {
		return isTitleColor(this.titleColor) ? ChatFormatting.valueOf(this.titleColor.toUpperCase(Locale.ROOT)) : null;
	}

	public static String byGeneration(int generation) {
		return generation <= 0 ? "ornate" : generation == 1 ? "buckle" : "stud";
	}

	public String metalworkShown(int generation) {
		return isMetalwork(this.metalwork) ? this.metalwork : byGeneration(generation);
	}

	public static CoverDesign of(ItemStack stack) {
		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		if (data == null) {
			return NONE;
		}
		return new CoverDesign(data.getString(CORNER), data.getString(DECORATION), data.getString(DECORATION_LOOK),
			data.getString(SPINE), data.getString(TITLE_COLOR), data.getString(AUTHOR), data.getString(METALWORK));
	}

	public void applyTo(ItemStack stack) {
		write(stack, Binding.of(stack), this);
	}

	static void write(ItemStack stack, Binding binding, CoverDesign design) {
		if (design.author != null && !design.hidesAuthor()) {
			design = design.withAuthor(null);
		}
		if (design.isEmpty()) {
			if (binding.isEmpty()) {
				stack.remove(DataComponents.CUSTOM_MODEL_DATA);
			} else {
				stack.set(DataComponents.CUSTOM_MODEL_DATA, binding.toModelData());
			}
			return;
		}
		CustomModelData base = binding.toModelData();
		String[] parts = {
			base.strings().isEmpty() ? "" : base.strings().get(0),
			base.strings().size() > 1 ? base.strings().get(1) : "",
			orEmpty(design.corner), orEmpty(design.decoration), orEmpty(design.decorationLook), orEmpty(design.spine), orEmpty(design.titleColor),
			base.strings().size() > Binding.CLASP ? base.strings().get(Binding.CLASP) : "",
			design.hidesAuthor() ? AUTHOR_HIDDEN : "",
			orEmpty(design.metalwork)};
		int size = SHORTEST;
		for (int i = SHORTEST; i < parts.length; i++) {
			if (!parts[i].isEmpty()) {
				size = i + 1;
			}
		}
		List<String> strings = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			strings.add(parts[i]);
		}
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(base.floats(), base.flags(), List.copyOf(strings), base.colors()));
	}

	private static String orEmpty(@Nullable String value) {
		return value == null ? "" : value;
	}
}
