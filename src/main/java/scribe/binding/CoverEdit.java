package scribe.binding;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import scribe.network.Payloads;

public record CoverEdit(String part, String value, String look) {
	public static final String DECORATION = "decoration";
	public static final String SPINE = "spine";
	public static final String TITLE = "title";
	public static final String CORNERS = "corners";
	public static final String METALWORK = "metalwork";
	public static final String AUTHOR = "author";
	public static final String CLEAR = "clear";
	public static final List<String> PARTS = List.of(DECORATION, SPINE, TITLE, CORNERS, METALWORK, AUTHOR, CLEAR);
	public static final String NO_DECORATION = "none";
	public static final String LEATHER = "leather";
	public static final String COVER_TONE = "tone";
	public static final String NO_CORNERS = CoverDesign.NO_CORNERS;
	public static final String SHOW = "show";
	public static final String HIDE = "hide";
	public static final String AUTO = CoverDesign.METALWORK_AUTO;
	public static final List<String> LOOKS = words(CoverDesign.CARVED, Bookbinding.MATERIALS);
	private static final List<String> DECORATIONS = words(NO_DECORATION, CoverDesign.DECORATIONS);
	private static final List<String> SPINES = words(LEATHER, Bookbinding.MATERIALS);
	private static final List<String> TITLES = words(COVER_TONE, CoverDesign.TITLE_COLORS);
	private static final List<String> CORNER_WORDS = words(NO_CORNERS, CoverDesign.CORNERS);
	private static final List<String> AUTHOR_WORDS = List.of(SHOW, HIDE);
	private static final List<String> METALWORK_WORDS = words(AUTO, CoverDesign.METALWORKS);

	public static final StreamCodec<RegistryFriendlyByteBuf, CoverEdit> CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(Payloads.SignDetails.DESIGN_ID_MAX), CoverEdit::part,
		ByteBufCodecs.stringUtf8(Payloads.SignDetails.DESIGN_ID_MAX), CoverEdit::value,
		ByteBufCodecs.stringUtf8(Payloads.SignDetails.DESIGN_ID_MAX), CoverEdit::look,
		CoverEdit::new
	);

	public CoverEdit {
		part = Payloads.SignDetails.cleanId(part);
		value = Payloads.SignDetails.cleanId(value);
		look = Payloads.SignDetails.cleanId(look);
	}

	public static CoverEdit clear() {
		return new CoverEdit(CLEAR, "", "");
	}

	private static List<String> words(String first, List<String> rest) {
		List<String> out = new ArrayList<>(rest.size() + 1);
		out.add(first);
		out.addAll(rest);
		return List.copyOf(out);
	}

	public static List<String> choices(String part) {
		return switch (part) {
			case DECORATION -> DECORATIONS;
			case SPINE -> SPINES;
			case TITLE -> TITLES;
			case CORNERS -> CORNER_WORDS;
			case METALWORK -> METALWORK_WORDS;
			case AUTHOR -> AUTHOR_WORDS;
			default -> List.of();
		};
	}

	public static @Nullable String wrongWord(String part, String value, String look) {
		if (!PARTS.contains(part)) {
			return part;
		}
		if (CLEAR.equals(part)) {
			return !value.isEmpty() ? value : look.isEmpty() ? null : look;
		}
		if (!choices(part).contains(value)) {
			return value;
		}
		if (!look.isEmpty() && (!DECORATION.equals(part) || !LOOKS.contains(look))) {
			return look;
		}
		return null;
	}

	public @Nullable String wrongWord() {
		return wrongWord(this.part, this.value, this.look);
	}

	public boolean isValid() {
		return this.wrongWord() == null;
	}

	public boolean isClear() {
		return CLEAR.equals(this.part);
	}

	public boolean needsTrim() {
		return CORNERS.equals(this.part);
	}

	public boolean needsBuckle() {
		return METALWORK.equals(this.part);
	}

	public boolean colorsTitle() {
		return TITLE.equals(this.part) && CoverDesign.isTitleColor(this.value);
	}

	public boolean uncolorsTitle() {
		return (TITLE.equals(this.part) && COVER_TONE.equals(this.value)) || this.isClear();
	}

	public static boolean isBook(ItemStack stack) {
		return stack.is(Items.WRITTEN_BOOK) || stack.is(Items.WRITABLE_BOOK);
	}

	public Payloads.SignDetails details(CoverDesign current) {
		return switch (this.part) {
			case DECORATION -> {
				String look = this.look.isEmpty() && CoverDesign.isDecorationLook(current.decorationLook()) ? current.decorationLook() : this.look;
				yield design("", NO_DECORATION.equals(this.value) ? CoverDesign.NO_DECORATION : this.value, look, "", "");
			}
			case SPINE -> design("", "", "", LEATHER.equals(this.value) ? CoverDesign.NO_SPINE_METAL : this.value, "");
			case TITLE -> design("", "", "", "", COVER_TONE.equals(this.value) ? CoverDesign.NO_TITLE_COLOR : this.value);
			case CORNERS -> design(this.value, "", "", "", "");
			case AUTHOR -> new Payloads.SignDetails("", List.of(), "", "", "", "", "", HIDE.equals(this.value) ? CoverDesign.AUTHOR_HIDDEN : CoverDesign.AUTHOR_SHOWN);
			case METALWORK -> new Payloads.SignDetails("", List.of(), "", "", "", "", "", "", this.value);
			default -> Payloads.SignDetails.NONE;
		};
	}

	private static Payloads.SignDetails design(String corner, String decoration, String look, String spine, String titleColor) {
		return new Payloads.SignDetails("", List.of(), corner, decoration, look, spine, titleColor);
	}
}
