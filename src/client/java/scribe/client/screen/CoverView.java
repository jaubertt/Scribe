package scribe.client.screen;

import scribe.RealAuthor;
import scribe.Scribe;
import scribe.binding.Binding;
import scribe.binding.Bookbinding;
import scribe.binding.Clasp;
import scribe.binding.CoverDesign;
import scribe.client.colors.ColorsClient;
import scribe.config.ScribeConfig;
import scribe.server.ServerBooks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;

public final class CoverView {
	public static final int WIDTH = 146;
	public static final int HEIGHT = 180;
	static final Identifier COVER = Scribe.id("textures/gui/cover.png");
	static final Identifier STRAP = Scribe.id("textures/gui/cover_strap.png");
	static final Identifier STRAP_KEEPER = Scribe.id("textures/gui/cover_strap_keeper.png");
	static final int SPINE_WIDTH = 11;
	static final int CORNERS_X = 118;
	static final int CORNERS_WIDTH = 28;
	static final int DESIGN_X = 118;
	static final int DESIGN_SIZE = 28;
	static final int STRAP_TOP = 90;
	static final int TEXT_CENTER = 77;
	static final int TEXT_WIDTH = 104;
	static final int TEXT_TOP = 20;
	static final int TEXT_BOTTOM = 84;
	static final int TITLE_LINES = 2;
	static final int SUBTITLE_LINES = 3;
	static final int TITLE_LINE = 10;
	static final int SUBTITLE_LINE = 9;
	static final int SUBTITLE_GAP = 3;
	static final int AUTHOR_Y = 140;
	static final int LEATHER_GRAY = 158;

	public enum CoverTone {
		PRESSED,
		ENGRAVED
	}

	public static final CoverTone COVER_TONE = CoverTone.ENGRAVED;

	public enum TitleColorLook {
		SHADOW,
		ENGRAVED,
		ENGRAVED_RULE
	}

	public static final TitleColorLook TITLE_COLOR_LOOK = TitleColorLook.ENGRAVED_RULE;
	static final int RULE_NEAR = 24;
	static final int ENGRAVED_EDGE_ALPHA = 61;
	static final int ENGRAVED_MIN_VALUE = 64;
	static final int DECORATION_X = 16;
	static final int DECORATION_Y = 8;
	static final int DECORATION_WIDTH = 124;
	static final int DECORATION_HEIGHT = 166;
	static final int DECORATION_CELL = 2;
	static final int CUT_X = 112;
	static final int CUT_HEIGHT = 24;
	static final String TRIANGLES = "triangles";

	private final int cover;
	private final Binding binding;
	private final @Nullable String material;
	private final @Nullable String corner;
	private final @Nullable String claspMaterial;
	private final @Nullable String metalwork;
	private final Metalwork.@Nullable Plaque plaque;
	private final @Nullable String decoration;
	private final @Nullable String decorationMetal;
	private final @Nullable String spine;
	private final @Nullable Integer titleColor;
	private final DecorationCuts.@Nullable Cut cut;
	private final List<String> title;
	private final List<String> subtitle;
	private final String author;
	private final String authorName;
	private final boolean penName;
	private final int[] tones;

	private CoverView(int cover, Binding binding, @Nullable String corner, CoverDesign design, @Nullable String metalwork, List<String> title,
		List<String> subtitle, Metalwork.@Nullable Plaque plaque, String author, String authorName, boolean penName) {
		this.cover = cover;
		this.binding = binding;
		this.material = Bookbinding.materialName(binding);
		this.corner = corner;
		this.claspMaterial = Clasp.shown(binding);
		this.metalwork = metalwork;
		this.plaque = plaque;
		this.decoration = design.shownDecoration();
		this.decorationMetal = design.decorationMetal();
		this.spine = design.spineMetal(binding);
		ChatFormatting color = design.titleFormatting();
		this.titleColor = color == null ? null : ARGB.opaque(TextColor.fromLegacyFormat(color).getValue());
		this.cut = DecorationCuts.of(this.decoration, cornerKey(this.material, corner));
		this.title = title;
		this.subtitle = subtitle;
		this.author = author;
		this.authorName = authorName;
		this.penName = penName;
		this.tones = textTones(cover);
	}

	public static boolean wanted(ItemStack stack) {
		return ScribeConfig.get().coversShown() && stack.has(DataComponents.WRITTEN_BOOK_CONTENT);
	}

	public static @Nullable CoverView of(ItemStack stack, Font font, boolean filtered) {
		return wanted(stack) ? build(stack, BookStyle.cover(stack), font, filtered) : null;
	}

	static @Nullable CoverView build(ItemStack stack, int cover, Font font, boolean filtered) {
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (written == null) {
			return null;
		}
		return make(stack, cover, font, written.title().get(filtered), subtitleLines(stack), written.author(), true, RealAuthor.isPenName(stack),
			written.generation());
	}

	static CoverView unsigned(ItemStack book, int cover, Font font, String title, List<String> subtitle, String author, boolean penName) {
		return make(book, cover, font, title, subtitle, author, !author.isEmpty(), penName, 0);
	}

	private static CoverView make(ItemStack stack, int cover, Font font, String titleText, List<String> subtitleLines, String authorName,
		boolean byLine, boolean penName, int generation) {
		CoverDesign design = CoverDesign.of(stack);
		if (design.hidesAuthor()) {
			byLine = false;
		}
		Binding binding = Binding.of(stack);
		String buckle = Clasp.metal(binding);
		String metalwork = buckle == null ? null : design.metalworkShown(generation);
		ToIntFunction<String> bold = s -> font.width(Component.literal(s).withStyle(ChatFormatting.BOLD));
		ToIntFunction<String> plain = font::width;
		List<String> title;
		List<String> subtitle = new ArrayList<>();
		Metalwork.Plaque plaque = null;
		if (Metalwork.plaque(metalwork) && !wrap(titleText, Metalwork.PLAQUE_TEXT_WIDTH, Metalwork.PLAQUE_TITLE_LINES, bold).isEmpty()) {
			title = wrap(titleText, Metalwork.PLAQUE_TEXT_WIDTH, Metalwork.PLAQUE_TITLE_LINES, bold);
			subtitle.addAll(wrap(String.join(" ", subtitleLines), Metalwork.PLAQUE_TEXT_WIDTH, Metalwork.PLAQUE_LINES - title.size(), plain));
			int widest = 0;
			for (String line : title) {
				widest = Math.max(widest, bold.applyAsInt(line));
			}
			for (String line : subtitle) {
				widest = Math.max(widest, plain.applyAsInt(line));
			}
			plaque = Metalwork.plaque(widest, title.size(), subtitle.size());
		} else {
			title = wrap(titleText, TEXT_WIDTH, TITLE_LINES, bold);
			for (String line : subtitleLines) {
				if (subtitle.size() >= SUBTITLE_LINES) {
					break;
				}
				subtitle.addAll(wrap(line, TEXT_WIDTH, SUBTITLE_LINES - subtitle.size(), plain));
			}
		}
		String author = "";
		if (byLine) {
			author = Component.translatable("book.byAuthor", authorName).getString();
			author = wrap(author, TEXT_WIDTH, 1, plain).stream().findFirst().orElse("");
		}
		return new CoverView(cover, binding, BookStyle.corner(stack), design, metalwork, title, subtitle, plaque, author, authorName, penName);
	}

	static Component authorLine(String line, String name, boolean penName) {
		String[] around = CoverText.aroundName();
		if (!penName || name.isEmpty() || !line.startsWith(around[0])) {
			return Component.literal(line);
		}
		int start = around[0].length();
		int nameEnd = start + name.length();
		int end;
		if (line.equals(around[0] + name + around[1])) {
			end = nameEnd;
		} else {
			int kept = line.endsWith("...") ? line.length() - 3 : line.length();
			end = kept >= nameEnd ? nameEnd : line.length();
		}
		if (end <= start) {
			return Component.literal(line);
		}
		return Component.empty().append(line.substring(0, start)).append(Component.literal(line.substring(start, end)).withStyle(ChatFormatting.ITALIC))
			.append(line.substring(end));
	}

	static List<String> subtitleLines(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		List<String> out = new ArrayList<>();
		if (lore != null) {
			for (Component line : lore.lines()) {
				if (ServerBooks.SUBTITLE_MARK.equals(line.getStyle().getInsertion())) {
					String text = line.getString().strip();
					if (!text.isEmpty()) {
						out.add(text);
					}
				}
			}
		}
		return out;
	}

	static Identifier strapFor(@Nullable String material) {
		return material != null && !Clasp.isLeather(material) ? STRAP : STRAP_KEEPER;
	}

	static List<BookStyle.Piece> cornerPieces(@Nullable String material, @Nullable String corner) {
		if (material == null || CoverDesign.NO_CORNERS.equals(corner)) {
			return List.of();
		}
		if (corner == null) {
			return List.of(new BookStyle.Piece(Scribe.id("gui/cover/trim_corners_" + material), CORNERS_X, 0, CORNERS_WIDTH, HEIGHT));
		}
		String design = "gui/corner/" + corner + "_cover_";
		return List.of(
			new BookStyle.Piece(Scribe.id(design + "tr_" + material), DESIGN_X, 0, DESIGN_SIZE, DESIGN_SIZE),
			new BookStyle.Piece(Scribe.id(design + "br_" + material), DESIGN_X, HEIGHT - DESIGN_SIZE, DESIGN_SIZE, DESIGN_SIZE));
	}

	static @Nullable String cornerKey(@Nullable String material, @Nullable String corner) {
		if (material == null || CoverDesign.NO_CORNERS.equals(corner)) {
			return null;
		}
		return corner == null ? TRIANGLES : corner;
	}

	static Identifier decorationSprite(String decoration, String metal) {
		return Scribe.id("gui/decoration/" + decoration + "_" + metal);
	}

	static Identifier cutSprite(String decoration, String metal) {
		return Scribe.id("gui/decoration/cuts/" + decoration + "_" + metal);
	}

	static Identifier carvedTexture(String decoration) {
		return Scribe.id("textures/gui/decoration/carved/" + decoration + ".png");
	}

	static Identifier carvedEdgeTexture(String decoration) {
		return Scribe.id("textures/gui/decoration/carved_edge/" + decoration + ".png");
	}

	static Identifier carvedCutTexture(String decoration) {
		return Scribe.id("textures/gui/decoration/carved/cuts/" + decoration + ".png");
	}

	static Identifier carvedEdgeCutTexture(String decoration) {
		return Scribe.id("textures/gui/decoration/carved_edge/cuts/" + decoration + ".png");
	}

	static Identifier spineSprite(String metal) {
		return Scribe.id("gui/cover/trim_spine_" + metal);
	}

	static int textTop(int titleLines, int subtitleLines) {
		int height = titleLines * TITLE_LINE + (subtitleLines > 0 ? SUBTITLE_GAP + subtitleLines * SUBTITLE_LINE : 0);
		return Math.max(DESIGN_SIZE, TEXT_TOP + (TEXT_BOTTOM - TEXT_TOP - height) / 2);
	}

	static int leather(int cover) {
		return ARGB.color(255, ARGB.red(cover) * LEATHER_GRAY / 255, ARGB.green(cover) * LEATHER_GRAY / 255, ARGB.blue(cover) * LEATHER_GRAY / 255);
	}

	static int[] tones(int cover) {
		int r = ARGB.red(cover) * LEATHER_GRAY / 255;
		int g = ARGB.green(cover) * LEATHER_GRAY / 255;
		int b = ARGB.blue(cover) * LEATHER_GRAY / 255;
		double luminance = (r * 0.299 + g * 0.587 + b * 0.114) / 255.0;
		if (luminance < 0.45) {
			return new int[] {lighter(r, g, b, 0.62), lighter(r, g, b, 0.48), scaled(r, g, b, 0.55)};
		}
		return new int[] {scaled(r, g, b, 0.42), scaled(r, g, b, 0.52), scaled(r, g, b, 1.12)};
	}

	public static int titleTone(int cover) {
		return textTones(cover)[0];
	}

	static int[] textTones(int cover) {
		return textTones(cover, coverTone(), titleColorLook());
	}

	static CoverTone coverTone() {
		return ScribeConfig.get().engravedCovers ? COVER_TONE : CoverTone.PRESSED;
	}

	static TitleColorLook titleColorLook() {
		return ScribeConfig.get().engravedCovers ? TITLE_COLOR_LOOK : TitleColorLook.SHADOW;
	}

	static int[] textTones(int cover, CoverTone tone) {
		return textTones(cover, tone, TITLE_COLOR_LOOK);
	}

	static int[] textTones(int cover, CoverTone tone, TitleColorLook look) {
		int[] pressed = tones(cover);
		int leather = leather(cover);
		int colorShadow = look == TitleColorLook.SHADOW ? pressed[2] : lightEdge(leather);
		if (tone == CoverTone.ENGRAVED && engraves(leather)) {
			int cut = ColorsClient.engravedTitle(leather);
			int edge = lightEdge(leather);
			return new int[] {cut, cut, edge, edge, colorShadow, pressed[2]};
		}
		return new int[] {pressed[0], pressed[1], pressed[2], pressed[2], colorShadow, pressed[2]};
	}

	public static int titleShadow(int[] tones, @Nullable Integer titleColor) {
		return titleShadow(tones, titleColor, titleColorLook());
	}

	static int titleShadow(int[] tones, @Nullable Integer titleColor, TitleColorLook look) {
		if (titleColor == null) {
			return tones.length > 3 ? tones[3] : tones[2];
		}
		if (tones.length <= 4) {
			return tones[2];
		}
		int coverShadow = tones.length > 5 ? tones[5] : tones[2];
		return look == TitleColorLook.ENGRAVED_RULE && nearEdge(titleColor, tones[4]) ? coverShadow : tones[4];
	}

	static boolean nearEdge(int letters, int edge) {
		return Math.abs(brightness(letters) - brightness(edge)) < RULE_NEAR;
	}

	static double brightness(int rgb) {
		return ARGB.red(rgb) * 0.299 + ARGB.green(rgb) * 0.587 + ARGB.blue(rgb) * 0.114;
	}

	static boolean engraves(int leather) {
		return Math.max(ARGB.red(leather), Math.max(ARGB.green(leather), ARGB.blue(leather))) >= ENGRAVED_MIN_VALUE;
	}

	static int lightEdge(int leather) {
		float k = ENGRAVED_EDGE_ALPHA / 255.0F;
		return ARGB.color(255, Math.round(ARGB.red(leather) + (255 - ARGB.red(leather)) * k), Math.round(ARGB.green(leather) + (255 - ARGB.green(leather)) * k),
			Math.round(ARGB.blue(leather) + (255 - ARGB.blue(leather)) * k));
	}

	private static int lighter(int r, int g, int b, double t) {
		return ARGB.color(255, (int) (r + (255 - r) * t), (int) (g + (255 - g) * t), (int) (b + (255 - b) * t));
	}

	private static int scaled(int r, int g, int b, double k) {
		return ARGB.color(255, Math.min(255, (int) (r * k)), Math.min(255, (int) (g * k)), Math.min(255, (int) (b * k)));
	}

	static List<String> wrap(String text, int width, int maxLines, ToIntFunction<String> widthOf) {
		List<String> lines = new ArrayList<>();
		List<int[]> ranges = lineRanges(text, width, maxLines, widthOf);
		for (int i = 0; i < ranges.size(); i++) {
			String line = text.substring(ranges.get(i)[0], ranges.get(i)[1]);
			boolean last = i == ranges.size() - 1;
			lines.add(last && widthOf.applyAsInt(line) > width ? ellipsis(line, width, widthOf) : line);
		}
		return lines;
	}

	static List<int[]> lineRanges(String text, int width, int maxLines, ToIntFunction<String> widthOf) {
		List<int[]> out = new ArrayList<>();
		if (text == null) {
			return out;
		}
		int end = text.length();
		while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
			end--;
		}
		int start = 0;
		while (start < end && Character.isWhitespace(text.charAt(start))) {
			start++;
		}
		while (start < end && out.size() < maxLines) {
			String rest = text.substring(start, end);
			if (widthOf.applyAsInt(rest) <= width || out.size() == maxLines - 1) {
				out.add(new int[] {start, end});
				break;
			}
			int fit = rest.offsetByCodePoints(0, 1);
			while (fit < rest.length()) {
				int next = rest.offsetByCodePoints(fit, 1);
				if (widthOf.applyAsInt(rest.substring(0, next)) > width) {
					break;
				}
				fit = next;
			}
			int space = rest.lastIndexOf(' ', fit);
			int cut = space > 0 ? space : fit;
			int lineEnd = start + cut;
			while (lineEnd > start && Character.isWhitespace(text.charAt(lineEnd - 1))) {
				lineEnd--;
			}
			out.add(new int[] {start, lineEnd});
			start += cut;
			while (start < end && Character.isWhitespace(text.charAt(start))) {
				start++;
			}
		}
		return out;
	}

	static String ellipsis(String text, int width, ToIntFunction<String> widthOf) {
		String shown = text;
		while (!shown.isEmpty() && widthOf.applyAsInt(shown.strip() + "...") > width) {
			shown = shown.substring(0, shown.offsetByCodePoints(shown.length(), -1));
		}
		return shown.strip() + "...";
	}

	List<String> title() {
		return this.title;
	}

	List<String> subtitle() {
		return this.subtitle;
	}

	String author() {
		return this.author;
	}

	Component authorText() {
		return authorLine(this.author, this.authorName, this.penName);
	}

	boolean penName() {
		return this.penName;
	}

	@Nullable String claspMaterial() {
		return this.claspMaterial;
	}

	@Nullable String metalwork() {
		return this.metalwork;
	}

	Metalwork.@Nullable Plaque plaque() {
		return this.plaque;
	}

	@Nullable String material() {
		return this.material;
	}

	@Nullable String corner() {
		return this.corner;
	}

	@Nullable String decoration() {
		return this.decoration;
	}

	@Nullable String decorationMetal() {
		return this.decorationMetal;
	}

	@Nullable String spine() {
		return this.spine;
	}

	@Nullable Integer titleColor() {
		return this.titleColor;
	}

	DecorationCuts.@Nullable Cut cut() {
		return this.cut;
	}

	int textTop() {
		return this.plaque != null ? this.plaque.textTop() : textTop(this.title.size(), this.subtitle.size());
	}

	@Nullable Identifier strap() {
		return this.binding.hasStrap() ? strapFor(this.claspMaterial) : null;
	}

	int[] inks() {
		return this.tones.clone();
	}

	public void drawArt(GuiGraphicsExtractor graphics, int x, int y) {
		this.drawArt(graphics, x, y, true);
	}

	public void drawArt(GuiGraphicsExtractor graphics, int x, int y, boolean withPlaque) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, COVER, x, y, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT, this.cover);
		if (this.decoration != null) {
			this.drawDecoration(graphics, x, y);
		}
		Identifier strap = this.strap();
		if (strap != null) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, strap, x, y, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT, ARGB.opaque(this.binding.strapColor()));
		}
		if (this.spine != null) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, spineSprite(this.spine), x, y, SPINE_WIDTH, HEIGHT);
		}
		if (this.material != null) {
			for (BookStyle.Piece metal : cornerPieces(this.material, this.corner)) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, metal.id(), x + metal.x(), y + metal.y(), metal.width(), metal.height());
			}
		}
		if (this.metalwork != null && this.claspMaterial != null) {
			if (withPlaque && this.plaque != null) {
				Metalwork.drawPlaque(graphics, x, y, this.plaque, this.claspMaterial);
			}
			Metalwork.draw(graphics, x, y, this.metalwork, this.claspMaterial);
		}
	}

	private void drawDecoration(GuiGraphicsExtractor graphics, int x, int y) {
		int dx = x + DECORATION_X;
		int dy = y + DECORATION_Y;
		int w = DECORATION_WIDTH / DECORATION_CELL;
		int h = DECORATION_HEIGHT / DECORATION_CELL;
		DecorationCuts.Cut cut = this.cut;
		if (this.decorationMetal == null) {
			if (cut == null) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, carvedTexture(this.decoration), dx, dy, 0, 0, DECORATION_WIDTH, DECORATION_HEIGHT, w, h, w, h,
					this.cover);
				graphics.blit(RenderPipelines.GUI_TEXTURED, carvedEdgeTexture(this.decoration), dx, dy, 0, 0, DECORATION_WIDTH, DECORATION_HEIGHT, w, h, w, h);
				return;
			}
			int sheetWidth = cutSheetWidth(cut) / DECORATION_CELL;
			int sheetHeight = 2 * CUT_HEIGHT / DECORATION_CELL;
			drawCut(cut, dx, dy, (sheet, u, v, px, py, pw, ph) -> graphics.blit(RenderPipelines.GUI_TEXTURED,
				sheet ? carvedCutTexture(this.decoration) : carvedTexture(this.decoration), px, py, u / DECORATION_CELL, v / DECORATION_CELL, pw, ph,
				pw / DECORATION_CELL, ph / DECORATION_CELL, sheet ? sheetWidth : w, sheet ? sheetHeight : h, this.cover));
			drawCut(cut, dx, dy, (sheet, u, v, px, py, pw, ph) -> graphics.blit(RenderPipelines.GUI_TEXTURED,
				sheet ? carvedEdgeCutTexture(this.decoration) : carvedEdgeTexture(this.decoration), px, py, u / DECORATION_CELL, v / DECORATION_CELL, pw, ph,
				pw / DECORATION_CELL, ph / DECORATION_CELL, sheet ? sheetWidth : w, sheet ? sheetHeight : h));
		} else if (cut == null) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, decorationSprite(this.decoration, this.decorationMetal), dx, dy, DECORATION_WIDTH, DECORATION_HEIGHT);
		} else {
			Identifier whole = decorationSprite(this.decoration, this.decorationMetal);
			Identifier sheets = cutSprite(this.decoration, this.decorationMetal);
			int sheetWidth = cutSheetWidth(cut);
			drawCut(cut, dx, dy, (sheet, u, v, px, py, pw, ph) -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sheet ? sheets : whole,
				sheet ? sheetWidth : DECORATION_WIDTH, sheet ? 2 * CUT_HEIGHT : DECORATION_HEIGHT, u, v, px, py, pw, ph));
		}
	}

	@FunctionalInterface
	interface Part {
		void draw(boolean sheet, int u, int v, int x, int y, int width, int height);
	}

	static int cutSheetWidth(DecorationCuts.Cut cut) {
		return cut.count() * (DECORATION_WIDTH - (CUT_X - DECORATION_X));
	}

	static void drawCut(DecorationCuts.Cut cut, int dx, int dy, Part part) {
		int zx = CUT_X - DECORATION_X;
		int zw = DECORATION_WIDTH - zx;
		int zh = CUT_HEIGHT;
		part.draw(false, 0, 0, dx, dy, zx, DECORATION_HEIGHT);
		part.draw(false, zx, zh, dx + zx, dy + zh, zw, DECORATION_HEIGHT - 2 * zh);
		int u = cut.index() * zw;
		part.draw(true, u, 0, dx + zx, dy, zw, zh);
		part.draw(true, u, zh, dx + zx, dy + DECORATION_HEIGHT - zh, zw, zh);
	}

	public void drawText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
		int center = x + TEXT_CENTER;
		int line = y + this.textTop();
		Style bold = Style.EMPTY.withBold(true);
		boolean onPlaque = this.plaque != null && this.claspMaterial != null;
		int titleInk = this.titleColor != null ? this.titleColor : onPlaque ? Metalwork.titleInk(this.claspMaterial) : this.tones[0];
		int titleShadow = onPlaque ? Metalwork.edge(this.claspMaterial) : titleShadow(this.tones, this.titleColor);
		int subtitleInk = onPlaque ? Metalwork.subtitleInk(this.claspMaterial) : this.tones[1];
		int subtitleShadow = onPlaque ? Metalwork.edge(this.claspMaterial) : this.tones[2];
		for (String text : this.title) {
			this.pressed(graphics, font, Component.literal(text).withStyle(bold).getVisualOrderText(), center, line, titleInk, titleShadow);
			line += TITLE_LINE;
		}
		if (!this.subtitle.isEmpty()) {
			line += SUBTITLE_GAP;
			for (String text : this.subtitle) {
				this.pressed(graphics, font, Component.literal(text).getVisualOrderText(), center, line, subtitleInk, subtitleShadow);
				line += SUBTITLE_LINE;
			}
		}
		if (!this.author.isEmpty()) {
			this.pressed(graphics, font, this.authorText().getVisualOrderText(), center, y + AUTHOR_Y, this.tones[1], this.tones[2]);
		}
	}

	private void pressed(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, int center, int y, int color, int shadow) {
		int x = center - font.width(text) / 2;
		graphics.text(font, text, x + 1, y + 1, shadow, false);
		graphics.text(font, text, x, y, color, false);
	}
}
