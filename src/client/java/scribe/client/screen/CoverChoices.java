package scribe.client.screen;

import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import scribe.binding.Binding;
import scribe.binding.Bookbinding;
import scribe.binding.Clasp;
import scribe.binding.CoverDesign;

public final class CoverChoices {
	public static final int LEAVE_UP = -1;
	public static final int LEAVE_DOWN = -2;

	public enum Row {
		TITLE_COLOR("title_color", 8, CoverDesign.TITLE_COLORS.size()),
		SPINE("spine", 11, Bookbinding.MATERIALS.size()),
		METALWORK("metalwork", CoverDesign.METALWORKS.size(), CoverDesign.METALWORKS.size() - 1),
		DECORATION("decoration", 8, CoverDesign.DECORATIONS.size()),
		LOOK("look", 11, Bookbinding.MATERIALS.size()),
		TRIM("trim", 8, CoverDesign.CORNERS.size());

		private final String key;
		private final int columns;
		private final int squares;

		Row(String key, int columns, int squares) {
			this.key = key;
			this.columns = columns;
			this.squares = squares;
		}

		public int count() {
			return 1 + this.squares;
		}

		public int columns() {
			return this.columns;
		}

		public int lines() {
			return (this.squares + this.columns - 1) / this.columns;
		}

		public Component label() {
			return Component.translatable(this == METALWORK ? "scribe.sign.metalwork" : "scribe.sign.row." + this.key);
		}
	}

	private final @Nullable String material;
	private final CoverDesign own;
	private @Nullable String corner;
	private @Nullable String decoration;
	private String look;
	private String spine;
	private @Nullable String titleColor;
	private boolean authorHidden;
	private @Nullable String metalwork;
	private @Nullable String buckle;
	private @Nullable Integer strap;
	private int generation;

	public CoverChoices(@Nullable String material, @Nullable String corner, @Nullable String decoration, @Nullable String look,
		@Nullable String spine, @Nullable String titleColor) {
		this(material, corner, decoration, look, spine, titleColor, CoverDesign.NONE);
	}

	private CoverChoices(@Nullable String material, @Nullable String corner, @Nullable String decoration, @Nullable String look,
		@Nullable String spine, @Nullable String titleColor, CoverDesign own) {
		this.material = material;
		this.own = own;
		this.corner = CoverDesign.isCornerChoice(corner) ? corner : CoverDesign.NO_CORNERS;
		this.decoration = CoverDesign.isDecoration(decoration) ? decoration : null;
		this.look = CoverDesign.isDecorationLook(look) ? look : CoverDesign.CARVED;
		this.spine = CoverDesign.isSpineChoice(spine) ? spine : CoverDesign.NO_SPINE_METAL;
		this.titleColor = CoverDesign.isTitleColor(titleColor) ? titleColor : null;
	}

	public static CoverChoices start(@Nullable String material) {
		return new CoverChoices(material, CoverDesign.NO_CORNERS, null, CoverDesign.CARVED, CoverDesign.NO_SPINE_METAL, null);
	}

	public static CoverChoices resume(@Nullable CoverChoices kept, @Nullable String material) {
		return resume(kept, material, CoverDesign.NONE);
	}

	public static CoverChoices resume(@Nullable CoverChoices kept, @Nullable String material, CoverDesign own) {
		if (kept == null || !Objects.equals(kept.material, material)) {
			return of(material, own);
		}
		CoverChoices choices = new CoverChoices(material, kept.corner, kept.decoration, kept.look, kept.spine, kept.titleColor, own);
		choices.authorHidden = kept.authorHidden;
		choices.metalwork = kept.metalwork;
		choices.buckle = kept.buckle;
		choices.strap = kept.strap;
		choices.generation = kept.generation;
		return choices;
	}

	public static CoverChoices of(@Nullable String material, CoverDesign own) {
		CoverChoices choices = new CoverChoices(material, own.corner(), own.decoration(), own.decorationLook(), own.spine(), own.titleColor(), own);
		choices.authorHidden = own.hidesAuthor();
		choices.metalwork = CoverDesign.isMetalwork(own.metalwork()) ? own.metalwork() : null;
		return choices;
	}

	public static CoverChoices ofSigned(@Nullable String material, CoverDesign own) {
		String spine = CoverDesign.isSpineChoice(own.spine()) ? own.spine() : material != null ? material : CoverDesign.NO_SPINE_METAL;
		CoverChoices choices = new CoverChoices(material, own.corner(), own.decoration(), own.decorationLook(), spine, own.titleColor(), own);
		if (material != null && !CoverDesign.isCornerChoice(own.corner())) {
			choices.corner = null;
		}
		choices.authorHidden = own.hidesAuthor();
		choices.metalwork = CoverDesign.isMetalwork(own.metalwork()) ? own.metalwork() : null;
		return choices;
	}

	public CoverChoices bound(Binding binding, int generation) {
		this.buckle = Clasp.metal(binding);
		this.strap = binding.hasStrap() ? binding.strapColor() : null;
		this.generation = generation;
		return this;
	}

	public @Nullable String material() {
		return this.material;
	}

	public @Nullable String corner() {
		return this.corner;
	}

	public @Nullable String decoration() {
		return this.decoration;
	}

	public String look() {
		return this.look;
	}

	public String spine() {
		return this.spine;
	}

	public @Nullable String titleColor() {
		return this.titleColor;
	}

	public @Nullable String shownSpine() {
		return CoverDesign.NO_SPINE_METAL.equals(this.spine) ? null : this.spine;
	}

	public boolean authorHidden() {
		return this.authorHidden;
	}

	public void setAuthorHidden(boolean hidden) {
		this.authorHidden = hidden;
	}

	public void toggleAuthorHidden() {
		this.authorHidden = !this.authorHidden;
	}

	public @Nullable String buckle() {
		return this.buckle;
	}

	public @Nullable Integer strap() {
		return this.strap;
	}

	public int generation() {
		return this.generation;
	}

	public String metalwork() {
		return this.metalwork != null ? this.metalwork : CoverDesign.byGeneration(this.generation);
	}

	public @Nullable String plaqueMetal() {
		return this.buckle != null && CoverDesign.PLAQUE.equals(this.metalwork()) ? this.buckle : null;
	}

	public boolean available(Row row) {
		return switch (row) {
			case LOOK -> this.decoration != null;
			case TRIM -> this.material != null;
			case METALWORK -> this.buckle != null;
			default -> true;
		};
	}

	public int chosen(Row row) {
		return switch (row) {
			case TITLE_COLOR -> this.titleColor == null ? 0 : 1 + CoverDesign.TITLE_COLORS.indexOf(this.titleColor);
			case DECORATION -> this.decoration == null ? 0 : 1 + CoverDesign.DECORATIONS.indexOf(this.decoration);
			case LOOK -> CoverDesign.CARVED.equals(this.look) ? 0 : 1 + Bookbinding.MATERIALS.indexOf(this.look);
			case SPINE -> {
				String shown = this.shownSpine();
				yield shown == null ? 0 : 1 + Bookbinding.MATERIALS.indexOf(shown);
			}
			case TRIM -> this.corner == null ? -1 : CoverDesign.NO_CORNERS.equals(this.corner) ? 0 : 1 + CoverDesign.CORNERS.indexOf(this.corner);
			case METALWORK -> CoverDesign.METALWORKS.indexOf(this.metalwork());
		};
	}

	public boolean choose(Row row, int index) {
		if (!this.available(row) || index < 0 || index >= row.count() || index == this.chosen(row)) {
			return false;
		}
		switch (row) {
			case TITLE_COLOR -> this.titleColor = index == 0 ? null : CoverDesign.TITLE_COLORS.get(index - 1);
			case DECORATION -> this.decoration = index == 0 ? null : CoverDesign.DECORATIONS.get(index - 1);
			case LOOK -> this.look = index == 0 ? CoverDesign.CARVED : Bookbinding.MATERIALS.get(index - 1);
			case SPINE -> this.spine = index == 0 ? CoverDesign.NO_SPINE_METAL : Bookbinding.MATERIALS.get(index - 1);
			case TRIM -> this.corner = index == 0 ? CoverDesign.NO_CORNERS : CoverDesign.CORNERS.get(index - 1);
			case METALWORK -> this.metalwork = CoverDesign.METALWORKS.get(index);
		}
		return true;
	}

	public static @Nullable String id(Row row, int index) {
		if (row == Row.METALWORK) {
			return CoverDesign.METALWORKS.get(Math.clamp(index, 0, CoverDesign.METALWORKS.size() - 1));
		}
		if (index <= 0) {
			return switch (row) {
				case TITLE_COLOR, DECORATION -> null;
				case LOOK -> CoverDesign.CARVED;
				case SPINE -> CoverDesign.NO_SPINE_METAL;
				case TRIM -> CoverDesign.NO_CORNERS;
				case METALWORK -> throw new IllegalStateException();
			};
		}
		List<String> ids = switch (row) {
			case TITLE_COLOR -> CoverDesign.TITLE_COLORS;
			case DECORATION -> CoverDesign.DECORATIONS;
			case LOOK, SPINE -> Bookbinding.MATERIALS;
			case TRIM -> CoverDesign.CORNERS;
			case METALWORK -> CoverDesign.METALWORKS;
		};
		return ids.get(index - 1);
	}

	public static Component name(Row row, int index) {
		String id = id(row, index);
		return switch (row) {
			case TITLE_COLOR -> Component.translatable(id == null ? "scribe.sign.cover_tone" : "scribe.color." + id);
			case DECORATION -> Component.translatable(id == null ? "scribe.decoration.none" : "scribe.decoration." + id);
			case LOOK -> Component.translatable(index == 0 ? "scribe.sign.carved" : "scribe.metal." + id);
			case SPINE -> Component.translatable(index == 0 ? "scribe.sign.leather" : "scribe.metal." + id);
			case TRIM -> Component.translatable("scribe.corner." + id);
			case METALWORK -> Component.translatable("scribe.metalwork." + id);
		};
	}

	public Component chosenName(Row row) {
		if (row == Row.TRIM && !this.available(row)) {
			return Component.translatable("scribe.sign.trim_none");
		}
		if (row == Row.TRIM && this.corner == null) {
			return Component.translatable("scribe.command.cover.triangles");
		}
		if (row == Row.DECORATION && this.decoration == null) {
			return Component.translatable("scribe.sign.decoration_none");
		}
		return name(row, this.chosen(row));
	}

	public static int moved(Row row, int index, int dx, int dy) {
		int last = row.count() - 1;
		if (dx != 0) {
			return Math.clamp(index + (long) dx, 0, last);
		}
		if (dy == 0) {
			return index;
		}
		if (index <= 0) {
			return dy < 0 ? LEAVE_UP : LEAVE_DOWN;
		}
		int line = (index - 1) / row.columns();
		if (dy < 0) {
			return line == 0 ? LEAVE_UP : index - row.columns();
		}
		return index + row.columns() > last ? LEAVE_DOWN : index + row.columns();
	}

	public CoverDesign design() {
		boolean trimmed = this.material != null;
		boolean ownSpine = this.own.spine() != null && !CoverDesign.NO_SPINE_METAL.equals(this.own.spine());
		String spine = trimmed || ownSpine || !CoverDesign.NO_SPINE_METAL.equals(this.spine) ? this.spine : null;
		String decoration = this.decoration != null ? this.decoration : this.own.decoration() != null ? CoverDesign.NO_DECORATION : null;
		String titleColor = this.titleColor != null ? this.titleColor : this.own.titleColor() != null ? CoverDesign.NO_TITLE_COLOR : null;
		String author = this.authorHidden ? CoverDesign.AUTHOR_HIDDEN : this.own.hidesAuthor() ? CoverDesign.AUTHOR_SHOWN : null;
		return new CoverDesign(trimmed ? this.corner : null, decoration, this.decoration != null ? this.look : null, spine, titleColor, author,
			this.available(Row.METALWORK) ? this.metalwork : null);
	}
}
