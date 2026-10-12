package scribe.binding;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;

public final class CoverLines {
	private static final String TITLE_COLOR = "title_color";
	private static final String AUTHOR = "author";
	private static final String DECORATION = "decoration";
	private static final String LOOK = "look";
	private static final String SPINE = "spine";
	private static final String TRIM = "trim";

	private CoverLines() {
	}

	public static List<Component> all(ItemStack book) {
		List<Component> rows = new ArrayList<>(7);
		rows.add(titleColor(book));
		rows.add(author(book));
		rows.add(spine(book));
		Component metalwork = metalwork(book);
		if (metalwork != null) {
			rows.add(metalwork);
		}
		rows.addAll(decoration(book));
		rows.add(trim(book));
		return rows;
	}

	public static List<Component> changed(ItemStack book, String part) {
		return switch (part) {
			case CoverEdit.DECORATION -> decoration(book);
			case CoverEdit.SPINE -> List.of(spine(book));
			case CoverEdit.TITLE -> List.of(titleColor(book));
			case CoverEdit.CORNERS -> List.of(trim(book));
			case CoverEdit.METALWORK -> {
				Component metalwork = metalwork(book);
				yield metalwork == null ? List.of() : List.of(metalwork);
			}
			case CoverEdit.AUTHOR -> List.of(author(book));
			default -> all(book);
		};
	}

	public static CoverDesign shown(ItemStack book) {
		CoverDesign design = CoverDesign.of(book);
		if (book.is(Items.WRITABLE_BOOK) && Bookbinding.materialName(Binding.of(book)) != null) {
			if (!CoverDesign.isCornerChoice(design.corner())) {
				design = design.withCorner(CoverDesign.NO_CORNERS);
			}
			if (!CoverDesign.isSpineChoice(design.spine())) {
				design = design.withSpine(CoverDesign.NO_SPINE_METAL);
			}
		}
		return design;
	}

	public static Component titleColor(ItemStack book) {
		CoverDesign design = shown(book);
		return row(TITLE_COLOR, design.titleFormatting() == null ? "scribe.sign.cover_tone" : "scribe.color." + design.titleColor());
	}

	public static Component author(ItemStack book) {
		return row(AUTHOR, shown(book).hidesAuthor() ? "scribe.sign.author_hidden" : "scribe.sign.author_shown");
	}

	public static List<Component> decoration(ItemStack book) {
		CoverDesign design = shown(book);
		String decoration = design.shownDecoration();
		if (decoration == null) {
			return List.of(row(DECORATION, "scribe.sign.decoration_none"));
		}
		String metal = design.decorationMetal();
		return List.of(row(DECORATION, "scribe.decoration." + decoration), row(LOOK, metal == null ? "scribe.sign.carved" : "scribe.metal." + metal));
	}

	public static Component spine(ItemStack book) {
		String metal = shown(book).spineMetal(Binding.of(book));
		return row(SPINE, metal == null ? "scribe.sign.leather" : "scribe.metal." + metal);
	}

	public static @Nullable Component metalwork(ItemStack book) {
		if (Clasp.metal(Binding.of(book)) == null) {
			return null;
		}
		WrittenBookContent written = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		String design = shown(book).metalworkShown(written == null ? 0 : written.generation());
		return Component.translatable("scribe.tooltip.metalwork", Component.translatable("scribe.metalwork." + design));
	}

	public static Component trim(ItemStack book) {
		if (Bookbinding.materialName(Binding.of(book)) == null) {
			return row(TRIM, "scribe.sign.trim_none");
		}
		String corner = shown(book).corner();
		return row(TRIM, CoverDesign.isCornerChoice(corner) ? "scribe.corner." + corner : "scribe.command.cover.triangles");
	}

	private static Component row(String row, String choice) {
		return Component.translatable("scribe.sign.row." + row).append(" ").append(Component.translatable(choice));
	}
}
