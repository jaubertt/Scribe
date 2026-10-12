package scribe.client.screen;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.function.ToIntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;
import scribe.EnchantedBooks;
import scribe.EnchantedMaker;
import scribe.Scribe;
import scribe.binding.CoverDesign;
import scribe.client.colors.ColorsClient;
import scribe.client.colors.EnchantColors;
import scribe.client.text.EnchantedPages;
import scribe.colors.BookColors;
import scribe.config.ScribeConfig;

public final class EnchantedCover {
	static final List<String> LEVEL_DESIGNS = CoverDesign.METALWORKS;
	static final int TITLE_LINES = 5;
	static final int PLAQUE_LEVEL = 5;
	static final int TITLE_LINES_ON_PLAQUE = 4;
	static final Identifier PLAQUE = Scribe.id("cover/enchanted/plaque");
	static final int PLAQUE_FRAME = 5;
	static final int PLAQUE_PAD_X = 6;
	static final int PLAQUE_PAD_Y = 3;
	static final int PLAQUE_LINE_HEIGHT = 8;
	static final int PLAQUE_TOP = 22;
	static final int PLAQUE_BOTTOM = 87;
	static final int PLAQUE_TEXT_WIDTH = CoverView.TEXT_WIDTH - 4;
	static final int PLAQUE_INK = 0xFF803503;
	static final int PLAQUE_INK_SOFT = 0xFFA0450A;
	static final int PLAQUE_EDGE = 0xFFFFFD90;
	static final Style TITLE = Style.EMPTY.withBold(true).withFont(EnchantedPages.GALACTIC_FONT);
	static final Style PLAIN_TITLE = Style.EMPTY.withBold(true);
	private static final String[] SYLLABLES = {"ar", "bel", "cor", "dra", "el", "fen", "gar", "hal", "is", "jor", "ka", "lin", "mor", "nel", "or",
		"pra", "quil", "ros", "sel", "tor", "ul", "val", "wyn", "zar"};

	record Line(String text, Kind kind) {
	}

	enum Kind {
		GALACTIC,
		PLAIN,
		MORE
	}

	record Plaque(int x, int y, int width, int height) {
	}

	private final int cover;
	private final int[] tones;
	private final int strap;
	private final ColorsClient.@Nullable LecternClasp clasp;
	private final List<Line> title;
	private final @Nullable Plaque plaque;
	private final Component author;
	private final boolean madeUp;

	private EnchantedCover(int cover, int strap, ColorsClient.@Nullable LecternClasp clasp, List<Line> title, @Nullable Plaque plaque, Component author,
		boolean madeUp) {
		this.cover = cover;
		this.tones = CoverView.textTones(cover);
		this.strap = strap;
		this.clasp = clasp;
		this.title = title;
		this.plaque = plaque;
		this.author = author;
		this.madeUp = madeUp;
	}

	public static boolean wanted(ItemStack stack) {
		return ScribeConfig.get().coversShown() && EnchantedBooks.is(stack);
	}

	public static @Nullable EnchantedCover of(ItemStack stack, Font font) {
		if (!wanted(stack)) {
			return null;
		}
		return make(stack, EnchantedPages.entries(stack, font), font);
	}

	static EnchantedCover make(ItemStack stack, List<EnchantedPages.Entry> entries, Font font) {
		EnchantColors colors = EnchantColors.get();
		ColorsClient.LecternClasp clasp = ColorsClient.enchantedClasp(colors.style() != EnchantColors.Style.RIBBON, colors.markLevel(stack));
		boolean onPlaque = onPlaque(clasp);
		List<Line> title = onPlaque ? titleLines(entries, TITLE_LINES_ON_PLAQUE, PLAQUE_TEXT_WIDTH, font) : titleLines(entries, TITLE_LINES, font);
		EnchantedMaker maker = EnchantedMaker.of(stack);
		Component author = maker != null ? authorLine(maker.name(), font) : madeUpAuthor(madeUpName(identity(stack, false).hashCode()));
		return new EnchantedCover(color(stack), strapColor(stack), clasp, title, onPlaque ? plaque(title, font) : null, author, maker == null);
	}

	static boolean onPlaque(ColorsClient.@Nullable LecternClasp clasp) {
		return clasp != null && clasp.level() >= PLAQUE_LEVEL;
	}

	public static int color(ItemStack stack) {
		if (!ScribeConfig.get().tintShown()) {
			return BookStyle.COVER_UNDYED;
		}
		EnchantColors colors = EnchantColors.get();
		int base = stack.has(DataComponents.DYED_COLOR) ? ARGB.opaque(BookColors.of(stack))
			: colors.style() == EnchantColors.Style.RIBBON ? ColorsClient.RIBBON_COVER : BookStyle.COVER_UNDYED;
		return ARGB.opaque(colors.bookColor(stack, base));
	}

	public static int strapColor(ItemStack stack) {
		return ColorsClient.enchantedRibbon(stack);
	}

	static List<Line> titleLines(List<EnchantedPages.Entry> entries, int maxLines, Font font) {
		return titleLines(entries, maxLines, CoverView.TEXT_WIDTH, font);
	}

	static List<Line> titleLines(List<EnchantedPages.Entry> entries, int maxLines, int width, Font font) {
		List<List<Line>> names = new ArrayList<>();
		int total = 0;
		for (EnchantedPages.Entry entry : entries) {
			boolean galactic = !entry.galactic().isEmpty();
			List<Line> name = new ArrayList<>();
			for (String part : split(galactic ? entry.galactic() : entry.text(), galactic ? TITLE : PLAIN_TITLE, width, font)) {
				name.add(new Line(part, galactic ? Kind.GALACTIC : Kind.PLAIN));
			}
			names.add(name);
			total += name.size();
		}
		List<Line> out = new ArrayList<>();
		if (total <= maxLines) {
			names.forEach(out::addAll);
			return out;
		}
		int shown = 0;
		for (List<Line> name : names) {
			if (shown > 0 && out.size() + name.size() > maxLines - 1) {
				break;
			}
			out.addAll(name.subList(0, Math.min(name.size(), maxLines - 1 - out.size())));
			shown++;
		}
		out.add(new Line(Component.translatable("scribe.look.more", names.size() - shown).getString(), Kind.MORE));
		return out;
	}

	static List<String> split(String text, Style style, Font font) {
		return split(text, style, CoverView.TEXT_WIDTH, font);
	}

	static List<String> split(String text, Style style, int width, Font font) {
		List<String> out = new ArrayList<>();
		for (FormattedText line : font.getSplitter().splitLines(FormattedText.of(text, style), width, Style.EMPTY)) {
			String part = line.getString().strip();
			if (!part.isEmpty()) {
				out.add(part);
			}
		}
		return out;
	}

	static Component text(Line line) {
		Style style = switch (line.kind()) {
			case GALACTIC -> TITLE;
			case PLAIN -> PLAIN_TITLE;
			case MORE -> Style.EMPTY;
		};
		return Component.literal(line.text()).withStyle(style);
	}

	static @Nullable Plaque plaque(List<Line> title, Font font) {
		if (title.isEmpty()) {
			return null;
		}
		int widest = 0;
		for (Line line : title) {
			widest = Math.max(widest, font.width(text(line)));
		}
		int width = widest + 2 * (PLAQUE_FRAME + PLAQUE_PAD_X);
		int height = 2 * (PLAQUE_FRAME + PLAQUE_PAD_Y) + (title.size() - 1) * CoverView.TITLE_LINE + PLAQUE_LINE_HEIGHT;
		return new Plaque(CoverView.TEXT_CENTER - width / 2, PLAQUE_TOP + (PLAQUE_BOTTOM - PLAQUE_TOP + 1 - height) / 2, width, height);
	}

	static Component authorLine(String name, Font font) {
		ToIntFunction<String> plain = font::width;
		String line = Component.translatable("book.byAuthor", name).getString();
		return Component.literal(CoverView.wrap(line, CoverView.TEXT_WIDTH, 1, plain).stream().findFirst().orElse(""));
	}

	static Component madeUpAuthor(String name) {
		return Component.translatable("book.byAuthor", Component.literal(name).withStyle(ChatFormatting.OBFUSCATED));
	}

	static String madeUpName(long seed) {
		Random random = new Random(seed);
		int syllables = 2 + random.nextInt(2);
		StringBuilder name = new StringBuilder();
		for (int i = 0; i < syllables; i++) {
			name.append(SYLLABLES[random.nextInt(SYLLABLES.length)]);
		}
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	static String identity(ItemStack stack, boolean withMaker) {
		ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		List<String> parts = new ArrayList<>();
		for (var entry : stored.entrySet()) {
			parts.add(entry.getKey().unwrapKey().map(key -> key.identifier().toString()).orElse("?") + "=" + entry.getIntValue());
		}
		parts.sort(Comparator.naturalOrder());
		StringBuilder out = new StringBuilder(String.join(";", parts));
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		out.append('|').append(name == null ? "" : name.getString());
		if (withMaker) {
			EnchantedMaker maker = EnchantedMaker.of(stack);
			out.append('|').append(maker == null ? "" : maker.name());
		}
		return out.toString();
	}

	public static String memoryKey(ItemStack stack) {
		String text = "e|" + identity(stack, true);
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 12);
		} catch (NoSuchAlgorithmException e) {
			return Integer.toHexString(text.hashCode());
		}
	}

	static String design(int level) {
		return LEVEL_DESIGNS.get(Math.clamp(level - ColorsClient.FIRST_CLASP, 0, LEVEL_DESIGNS.size() - 1));
	}

	Identifier strap() {
		return this.clasp != null ? CoverView.STRAP : CoverView.STRAP_KEEPER;
	}

	int cover() {
		return this.cover;
	}

	int strapTint() {
		return this.strap;
	}

	ColorsClient.@Nullable LecternClasp clasp() {
		return this.clasp;
	}

	List<Line> title() {
		return this.title;
	}

	@Nullable Plaque plaque() {
		return this.plaque;
	}

	Component author() {
		return this.author;
	}

	boolean madeUp() {
		return this.madeUp;
	}

	int[] inks() {
		return this.tones.clone();
	}

	int textTop() {
		return this.plaque != null ? this.plaque.y() + PLAQUE_FRAME + PLAQUE_PAD_Y : CoverView.textTop(this.title.size(), 0);
	}

	public void drawArt(GuiGraphicsExtractor graphics, int x, int y) {
		int w = CoverView.WIDTH;
		int h = CoverView.HEIGHT;
		graphics.blit(RenderPipelines.GUI_TEXTURED, CoverView.COVER, x, y, 0, 0, w, h, w, h, this.cover);
		graphics.blit(RenderPipelines.GUI_TEXTURED, this.strap(), x, y, 0, 0, w, h, w, h, this.strap);
		if (this.plaque != null) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PLAQUE, x + this.plaque.x(), y + this.plaque.y(), this.plaque.width(), this.plaque.height());
		}
		if (this.clasp != null) {
			Metalwork.draw(graphics, x, y, design(this.clasp.level()), this.clasp.material());
		}
	}

	public void drawText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
		int center = x + CoverView.TEXT_CENTER;
		int row = y + this.textTop();
		for (Line line : this.title) {
			boolean more = line.kind() == Kind.MORE;
			if (this.plaque != null) {
				pressed(graphics, font, text(line), center, row, more ? PLAQUE_INK_SOFT : PLAQUE_INK, PLAQUE_EDGE);
			} else {
				pressed(graphics, font, text(line), center, row, more ? this.tones[1] : this.tones[0], more ? this.tones[2] : this.tones[3]);
			}
			row += CoverView.TITLE_LINE;
		}
		pressed(graphics, font, this.author, center, y + CoverView.AUTHOR_Y, this.tones[1], this.tones[2]);
	}

	private static void pressed(GuiGraphicsExtractor graphics, Font font, Component text, int center, int y, int color, int shadow) {
		MutableComponent shaded = Component.empty().withStyle(Style.EMPTY.withShadowColor(shadow)).append(text);
		graphics.text(font, shaded.getVisualOrderText(), center - font.width(shaded) / 2, y, color, false);
	}
}
