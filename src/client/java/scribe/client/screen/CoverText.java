package scribe.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import scribe.BookLimits;
import scribe.RealAuthor;
import scribe.client.screen.SignLayout.Rect;
import scribe.client.widget.InlineField;
import scribe.network.Payloads;

public final class CoverText {
	public enum Field {
		TITLE, SUBTITLE, PEN_NAME
	}

	public static final int FAINT = 110;
	public static final int FOCUS_FRAME = 110;
	public static final int HOVER_FRAME = 70;
	public static final int FRAME_PAD = 3;
	static final int FRAME_BOTTOM = 11;

	final InlineField title = new InlineField(BookLimits.TITLE_CHARS);
	final InlineField subtitle = new InlineField(Payloads.SignDetails.SUBTITLE_MAX);
	final InlineField penName = new InlineField(Payloads.SignDetails.PEN_NAME_MAX);
	private final Font font;
	private final String playerName;
	private final boolean subtitles;
	private final boolean penNames;
	boolean editingPenName;
	private BooleanSupplier authorLeftOff = () -> false;
	private Supplier<@Nullable PlaqueLook> plaque = () -> null;

	public record PlaqueLook(String metal, @Nullable Integer titleColor) {
	}

	public record Line(String text, int start, int end, int x, int y, int width, boolean faint, boolean cut) {
		public int right() {
			return this.x + this.width;
		}
	}

	public record Layout(List<Line> title, List<Line> subtitle, Line author, int nameX, boolean nameFaint, Metalwork.@Nullable Plaque plaque) {
		public Layout(List<Line> title, List<Line> subtitle, Line author, int nameX, boolean nameFaint) {
			this(title, subtitle, author, nameX, nameFaint, null);
		}
	}

	CoverText(Font font, String playerName, boolean subtitles, boolean penNames) {
		this.font = font;
		this.playerName = playerName;
		this.subtitles = subtitles;
		this.penNames = penNames;
		this.title.filter(value -> titleFits(font, value) || value.length() < this.title.value().length());
		this.subtitle.filter(value -> subtitleFits(font, value) || value.length() < this.subtitle.value().length());
		this.penName.filter(value -> authorFits(font, value) || value.length() < this.penName.value().length());
	}

	public InlineField field(Field field) {
		return switch (field) {
			case TITLE -> this.title;
			case SUBTITLE -> this.subtitle;
			case PEN_NAME -> this.penName;
		};
	}

	public boolean shown(Field field) {
		return switch (field) {
			case TITLE -> true;
			case SUBTITLE -> this.subtitles;
			case PEN_NAME -> this.penNames;
		};
	}

	void authorLeftOff(BooleanSupplier leftOff) {
		this.authorLeftOff = leftOff;
	}

	public boolean authorLeftOff() {
		return this.authorLeftOff.getAsBoolean();
	}

	void plaque(Supplier<@Nullable PlaqueLook> plaque) {
		this.plaque = plaque;
	}

	public @Nullable PlaqueLook plaqueLook() {
		return this.plaque.get();
	}

	void startPenName() {
		if (!this.editingPenName) {
			this.editingPenName = true;
			this.penName.setValue(this.playerName);
		}
	}

	static ToIntFunction<String> bold(Font font) {
		return text -> font.width(Component.literal(text).withStyle(ChatFormatting.BOLD));
	}

	public static boolean titleFits(Font font, String value) {
		return CoverView.lineRanges(value, CoverView.TEXT_WIDTH, Integer.MAX_VALUE, bold(font)).size() <= CoverView.TITLE_LINES;
	}

	public static boolean subtitleFits(Font font, String value) {
		return CoverView.lineRanges(value, CoverView.TEXT_WIDTH, Integer.MAX_VALUE, font::width).size() <= CoverView.SUBTITLE_LINES;
	}

	public static boolean authorFits(Font font, String name) {
		return font.width(byAuthor(name)) <= CoverView.TEXT_WIDTH;
	}

	static String byAuthor(String name) {
		return Component.translatable("book.byAuthor", name).getString();
	}

	static String[] aroundName() {
		String marked = byAuthor("\u0001");
		int at = marked.indexOf('\u0001');
		return at < 0 ? new String[] {marked + " ", ""} : new String[] {marked.substring(0, at), marked.substring(at + 1)};
	}

	public List<String> subtitleLines() {
		String value = this.subtitle.value();
		List<String> lines = new ArrayList<>();
		for (int[] range : CoverView.lineRanges(value, CoverView.TEXT_WIDTH, Integer.MAX_VALUE, this.font::width)) {
			lines.add(value.substring(range[0], range[1]));
		}
		return lines;
	}

	String author() {
		return this.editingPenName && !this.penName.value().isBlank() ? this.penName.value().strip() : this.playerName;
	}

	public boolean showsPenName() {
		return this.editingPenName && RealAuthor.isPenName(this.penName.value(), this.playerName);
	}

	public Layout layout() {
		boolean onPlaque = this.plaqueLook() != null;
		int width = onPlaque ? Metalwork.PLAQUE_TEXT_WIDTH : CoverView.TEXT_WIDTH;
		List<Line> titleLines = this.lines(this.title.value(), Component.translatable("scribe.sign.placeholder.title").getString(),
			onPlaque ? Metalwork.PLAQUE_TITLE_LINES : CoverView.TITLE_LINES, width, bold(this.font));
		int subtitleMax = onPlaque ? Metalwork.PLAQUE_LINES - titleLines.size() : CoverView.SUBTITLE_LINES;
		List<Line> subtitleLines = this.subtitles
			? this.lines(this.subtitle.value(), Component.translatable("scribe.sign.placeholder.subtitle").getString(), subtitleMax, width, this.font::width)
			: List.of();
		Metalwork.Plaque plaque = null;
		if (onPlaque) {
			int widest = 0;
			for (Line line : titleLines) {
				widest = Math.max(widest, line.width());
			}
			for (Line line : subtitleLines) {
				widest = Math.max(widest, line.width());
			}
			plaque = Metalwork.plaque(widest, titleLines.size(), subtitleLines.size());
		}
		int y = plaque != null ? plaque.textTop() : CoverView.textTop(titleLines.size(), subtitleLines.size());
		List<Line> title = new ArrayList<>();
		for (Line line : titleLines) {
			title.add(placed(line, y));
			y += CoverView.TITLE_LINE;
		}
		List<Line> subtitle = new ArrayList<>();
		if (!subtitleLines.isEmpty()) {
			y += CoverView.SUBTITLE_GAP;
			for (Line line : subtitleLines) {
				subtitle.add(placed(line, y));
				y += CoverView.SUBTITLE_LINE;
			}
		}
		String[] around = aroundName();
		boolean faint = this.editingPenName && this.penName.value().isEmpty();
		String name = this.editingPenName && !faint ? this.penName.value() : this.playerName;
		String full = around[0] + name + around[1];
		boolean cut = false;
		if (!this.editingPenName && this.font.width(full) > CoverView.TEXT_WIDTH) {
			full = CoverView.ellipsis(full, CoverView.TEXT_WIDTH, this.font::width);
			cut = true;
		}
		int authorWidth = this.font.width(full);
		int x = CoverView.TEXT_CENTER - authorWidth / 2;
		Line author = new Line(full, 0, name.length(), x, CoverView.AUTHOR_Y, authorWidth, false, cut);
		return new Layout(List.copyOf(title), List.copyOf(subtitle), author, x + this.font.width(around[0]), faint, plaque);
	}

	private List<Line> lines(String value, String placeholder, int maxLines, int width, ToIntFunction<String> widthOf) {
		boolean faint = value.isBlank();
		String text = faint ? placeholder : value;
		List<int[]> ranges = CoverView.lineRanges(text, width, maxLines, widthOf);
		List<Line> out = new ArrayList<>();
		for (int i = 0; i < ranges.size(); i++) {
			int[] range = ranges.get(i);
			String shown = text.substring(range[0], range[1]);
			boolean cut = i == ranges.size() - 1 && widthOf.applyAsInt(shown) > width;
			if (cut) {
				shown = CoverView.ellipsis(shown, width, widthOf);
			}
			out.add(new Line(shown, range[0], range[1], 0, 0, widthOf.applyAsInt(shown), faint, cut));
		}
		return out;
	}

	private static Line placed(Line line, int y) {
		return new Line(line.text(), line.start(), line.end(), CoverView.TEXT_CENTER - line.width() / 2, y, line.width(), line.faint(), line.cut());
	}

	private static List<Line> lines(Layout layout, Field field) {
		return switch (field) {
			case TITLE -> layout.title();
			case SUBTITLE -> layout.subtitle();
			case PEN_NAME -> List.of(layout.author());
		};
	}

	public @Nullable Rect frame(Layout layout, Field field) {
		List<Line> lines = lines(layout, field);
		if (!this.shown(field) || lines.isEmpty()) {
			return null;
		}
		int x0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		for (Line line : lines) {
			x0 = Math.min(x0, line.x());
			x1 = Math.max(x1, line.right());
		}
		int y0 = lines.getFirst().y() - FRAME_PAD;
		int y1 = lines.getLast().y() + FRAME_BOTTOM;
		return new Rect(x0 - FRAME_PAD, y0, x1 - x0 + 2 * FRAME_PAD, y1 - y0);
	}

	public @Nullable Field fieldAt(Layout layout, int x, int y) {
		for (Field field : Field.values()) {
			Rect frame = this.frame(layout, field);
			if (frame != null && frame.contains(x, y)) {
				return field;
			}
		}
		return null;
	}

	public int caretAt(Layout layout, Field field, int x, int y) {
		InlineField input = this.field(field);
		String value = input.value();
		if (field == Field.PEN_NAME) {
			return layout.nameFaint() || !this.editingPenName ? 0 : nearest(value, 0, value.length(), x - layout.nameX(), this.font::width);
		}
		List<Line> lines = lines(layout, field);
		if (lines.isEmpty() || lines.getFirst().faint()) {
			return 0;
		}
		Line best = lines.getFirst();
		for (Line line : lines) {
			if (Math.abs(y - (line.y() + 4)) < Math.abs(y - (best.y() + 4))) {
				best = line;
			}
		}
		ToIntFunction<String> widthOf = field == Field.TITLE ? bold(this.font) : this.font::width;
		int end = best.cut() ? best.start() : best.end();
		if (best.cut()) {
			String shown = best.text().substring(0, Math.max(0, best.text().length() - 3));
			end = best.start() + shown.length();
		}
		return nearest(value, best.start(), end, x - best.x(), widthOf);
	}

	static int nearest(String value, int start, int end, int dx, ToIntFunction<String> widthOf) {
		int best = start;
		int bestDistance = Math.abs(dx);
		int i = start;
		while (i < end) {
			i = value.offsetByCodePoints(i, 1);
			int distance = Math.abs(widthOf.applyAsInt(value.substring(start, i)) - dx);
			if (distance < bestDistance) {
				best = i;
				bestDistance = distance;
			}
		}
		return best;
	}

	public int @Nullable [] caret(Layout layout, Field field) {
		if (!this.shown(field)) {
			return null;
		}
		InlineField input = this.field(field);
		String value = input.value();
		int cursor = Math.min(input.cursor(), value.length());
		if (field == Field.PEN_NAME) {
			int x = layout.nameFaint() ? layout.nameX() : layout.nameX() + this.font.width(value.substring(0, cursor));
			return new int[] {x, layout.author().y()};
		}
		List<Line> lines = lines(layout, field);
		if (lines.isEmpty()) {
			return null;
		}
		if (lines.getFirst().faint()) {
			return new int[] {lines.getFirst().x(), lines.getFirst().y()};
		}
		ToIntFunction<String> widthOf = field == Field.TITLE ? bold(this.font) : this.font::width;
		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			boolean last = i == lines.size() - 1;
			if (cursor < line.start()) {
				return new int[] {line.x(), line.y()};
			}
			if (cursor <= line.end() || last) {
				int x = line.x() + widthOf.applyAsInt(value.substring(line.start(), Math.max(line.start(), cursor)));
				return new int[] {line.cut() ? Math.min(x, line.right()) : x, line.y()};
			}
		}
		return null;
	}

	public void draw(GuiGraphicsExtractor graphics, int x, int y, int[] tones, int titleInk, @Nullable Field focus, @Nullable Field hover) {
		this.draw(graphics, x, y, tones, titleInk, tones[2], focus, hover);
	}

	public void draw(GuiGraphicsExtractor graphics, int x, int y, int[] tones, int titleInk, int titleShadow, @Nullable Field focus, @Nullable Field hover) {
		Layout layout = this.layout();
		PlaqueLook look = layout.plaque() != null ? this.plaqueLook() : null;
		int titleColor = titleInk;
		int titleEdge = titleShadow;
		int subtitleColor = tones[1];
		int subtitleEdge = tones[2];
		if (look != null) {
			Metalwork.drawPlaque(graphics, x, y, layout.plaque(), look.metal());
			titleColor = look.titleColor() != null ? look.titleColor() : Metalwork.titleInk(look.metal());
			titleEdge = Metalwork.edge(look.metal());
			subtitleColor = Metalwork.subtitleInk(look.metal());
			subtitleEdge = Metalwork.edge(look.metal());
		}
		Style bold = Style.EMPTY.withBold(true);
		for (Line line : layout.title()) {
			this.pressed(graphics, Component.literal(line.text()).withStyle(bold).getVisualOrderText(), x + line.x(), y + line.y(), titleColor, titleEdge,
				line.faint());
		}
		for (Line line : layout.subtitle()) {
			this.pressed(graphics, Component.literal(line.text()).getVisualOrderText(), x + line.x(), y + line.y(), subtitleColor, subtitleEdge, line.faint());
		}
		Line author = layout.author();
		boolean leftOff = this.authorLeftOff();
		if (layout.nameFaint()) {
			String[] around = aroundName();
			int ax = x + author.x();
			int ay = y + author.y();
			this.pressed(graphics, Component.literal(around[0]).getVisualOrderText(), ax, ay, tones[1], tones[2], leftOff);
			this.pressed(graphics, Component.literal(this.playerName).getVisualOrderText(), x + layout.nameX(), ay, tones[1], tones[2], true);
			int after = x + layout.nameX() + this.font.width(this.playerName);
			this.pressed(graphics, Component.literal(around[1]).getVisualOrderText(), after, ay, tones[1], tones[2], leftOff);
		} else {
			String name = this.editingPenName ? this.penName.value() : this.playerName;
			this.pressed(graphics, CoverView.authorLine(author.text(), name, this.showsPenName()).getVisualOrderText(), x + author.x(), y + author.y(),
				tones[1], tones[2], leftOff);
		}
		if (hover != null && hover != focus) {
			this.frame(graphics, layout, hover, x, y, ARGB.color(HOVER_FRAME, tones[1]));
		}
		if (focus != null && this.shown(focus)) {
			this.frame(graphics, layout, focus, x, y, ARGB.color(FOCUS_FRAME, tones[1]));
			int[] caret = this.caret(layout, focus);
			if (caret != null && this.field(focus).caretVisible()) {
				int color = focus == Field.TITLE ? titleColor : focus == Field.SUBTITLE ? subtitleColor : tones[1];
				graphics.fill(x + caret[0], y + caret[1] - 1, x + caret[0] + 1, y + caret[1] + 9, color);
			}
		}
	}

	private void pressed(GuiGraphicsExtractor graphics, FormattedCharSequence text, int x, int y, int color, int shadow, boolean faint) {
		if (faint) {
			graphics.text(this.font, text, x, y, ARGB.color(FAINT, color), false);
			return;
		}
		graphics.text(this.font, text, x + 1, y + 1, shadow, false);
		graphics.text(this.font, text, x, y, color, false);
	}

	private void frame(GuiGraphicsExtractor graphics, Layout layout, Field field, int x, int y, int color) {
		Rect frame = this.frame(layout, field);
		if (frame != null) {
			graphics.outline(x + frame.x(), y + frame.y(), frame.width(), frame.height(), color);
		}
	}
}
