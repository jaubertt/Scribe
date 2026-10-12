package scribe.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;
import scribe.RealAuthor;
import scribe.binding.CoverDesign;
import scribe.client.AuthorNames;
import scribe.client.BookNet;
import scribe.config.ScribeConfig;
import scribe.client.compat.Keys;
import scribe.client.compat.TextKeys;
import scribe.client.screen.CoverChoices.Row;
import scribe.client.screen.SignLayout.Rect;
import scribe.client.widget.InlineField;
import scribe.network.Payloads;
import scribe.server.ServerBooks;

public final class SignForm {
	public record Features(boolean scribe, boolean subtitles, boolean penNames, boolean designs, boolean designerOff) {
		public static final Features ALL = new Features(true, true, true, true);
		public static final Features NONE = new Features(false, false, false, false);

		public Features {
			if (!scribe) {
				subtitles = false;
				penNames = false;
				designs = false;
				designerOff = false;
			}
			if (designerOff) {
				designs = false;
			}
		}

		public Features(boolean scribe, boolean subtitles, boolean penNames, boolean designs) {
			this(scribe, subtitles, penNames, designs, false);
		}

		public static Features current() {
			boolean serverDesigns = BookNet.coverDesigns();
			boolean designer = ScribeConfig.get().designerShown();
			return new Features(BookNet.scribeOnServer(), BookNet.subtitles(), BookNet.penNames(), serverDesigns && designer, serverDesigns && !designer);
		}
	}

	public enum Focus {
		TITLE(CoverText.Field.TITLE, null),
		SUBTITLE(CoverText.Field.SUBTITLE, null),
		PEN_NAME(CoverText.Field.PEN_NAME, null),
		TITLE_COLOR(null, Row.TITLE_COLOR),
		HIDE_AUTHOR(null, null),
		SPINE(null, Row.SPINE),
		METALWORK(null, Row.METALWORK),
		DECORATION(null, Row.DECORATION),
		LOOK(null, Row.LOOK),
		TRIM(null, Row.TRIM);

		private final CoverText.@Nullable Field field;
		private final @Nullable Row row;

		Focus(CoverText.@Nullable Field field, @Nullable Row row) {
			this.field = field;
			this.row = row;
		}

		public CoverText.@Nullable Field field() {
			return this.field;
		}

		public @Nullable Row row() {
			return this.row;
		}

		public static Focus of(CoverText.Field field) {
			return switch (field) {
				case TITLE -> TITLE;
				case SUBTITLE -> SUBTITLE;
				case PEN_NAME -> PEN_NAME;
			};
		}

		public static Focus of(Row row) {
			return switch (row) {
				case TITLE_COLOR -> TITLE_COLOR;
				case SPINE -> SPINE;
				case METALWORK -> METALWORK;
				case DECORATION -> DECORATION;
				case LOOK -> LOOK;
				case TRIM -> TRIM;
			};
		}
	}

	public record Hover(CoverText.@Nullable Field field, DesignPanel.@Nullable Hit choice, boolean tick, int resting) {
		public static final Hover NONE = new Hover(null, null);
		public static final Hover TICK = new Hover(null, null, true);

		public Hover(CoverText.@Nullable Field field, DesignPanel.@Nullable Hit choice) {
			this(field, choice, false);
		}

		public Hover(CoverText.@Nullable Field field, DesignPanel.@Nullable Hit choice, boolean tick) {
			this(field, choice, tick, -1);
		}
	}

	public enum Click {
		NONE, FIELD, CHOICE
	}

	static final int TOOLTIP_WIDTH = 170;

	private final Font font;
	private final Features features;
	private final CoverChoices choices;
	private final CoverText text;
	private final String playerName;
	private Focus focus = Focus.TITLE;
	private CoverText.Field lastText = CoverText.Field.TITLE;

	public SignForm(Font font, Features features, CoverChoices choices, String playerName, String title, String subtitle, String penName) {
		this.font = font;
		this.features = features;
		this.choices = choices;
		this.playerName = playerName;
		this.text = new CoverText(font, playerName, features.subtitles(), features.penNames());
		this.text.authorLeftOff(choices::authorHidden);
		this.text.plaque(this::plaqueLook);
		this.text.title.setValue(title);
		this.text.subtitle.setValue(subtitle);
		this.text.penName.setValue(penName);
		this.text.editingPenName = !penName.isBlank();
	}

	public Features features() {
		return this.features;
	}

	public CoverChoices choices() {
		return this.choices;
	}

	public CoverText text() {
		return this.text;
	}

	public Focus focus() {
		return this.focus;
	}

	public InlineField title() {
		return this.text.title;
	}

	public InlineField subtitle() {
		return this.text.subtitle;
	}

	public String penNameToKeep() {
		return this.text.editingPenName ? this.text.penName.value() : "";
	}

	public boolean editingPenName() {
		return this.text.editingPenName;
	}

	public boolean panelShown() {
		return this.features.designs();
	}

	public @Nullable Component note() {
		if (!this.features.scribe()) {
			return Component.translatable("scribe.sign.needs_server");
		}
		if (this.features.designerOff()) {
			return Component.translatable("scribe.sign.designer_off");
		}
		return this.features.designs() ? null : Component.translatable("scribe.sign.designs_off");
	}

	public List<Focus> order() {
		List<Focus> order = new ArrayList<>(List.of(Focus.TITLE));
		if (this.features.subtitles()) {
			order.add(Focus.SUBTITLE);
		}
		if (this.features.penNames()) {
			order.add(Focus.PEN_NAME);
		}
		if (this.features.designs()) {
			for (Row row : Row.values()) {
				if (this.choices.available(row)) {
					order.add(Focus.of(row));
				}
				if (row == Row.TITLE_COLOR) {
					order.add(Focus.HIDE_AUTHOR);
				}
			}
		}
		return order;
	}

	public void focus(Focus target) {
		if (target == Focus.PEN_NAME) {
			this.text.startPenName();
		}
		if (target == Focus.TITLE || target == Focus.SUBTITLE) {
			this.lastText = target.field();
		}
		this.focus = target;
		CoverText.Field field = target.field();
		if (field != null) {
			this.text.field(field).setFocused(true);
		}
		for (CoverText.Field other : CoverText.Field.values()) {
			if (other != field) {
				this.text.field(other).setFocused(false);
			}
		}
	}

	private void keepFocusAvailable() {
		List<Focus> order = this.order();
		if (order.contains(this.focus)) {
			return;
		}
		Focus[] all = Focus.values();
		for (int i = this.focus.ordinal() - 1; i >= 0; i--) {
			if (order.contains(all[i])) {
				this.focus(all[i]);
				return;
			}
		}
		this.focus(Focus.TITLE);
	}

	public boolean keyPressed(KeyEvent event) {
		return this.keyPressed(event, Keys.mac());
	}

	public boolean keyPressed(KeyEvent event, boolean mac) {
		List<Focus> order = this.order();
		int at = Math.max(0, order.indexOf(this.focus));
		if (Keys.isTab(event)) {
			this.focus(order.get(Math.floorMod(at + (Keys.shift(event) ? -1 : 1), order.size())));
			return true;
		}
		if (this.focus == Focus.HIDE_AUTHOR) {
			return this.tickKey(event, order, at);
		}
		Row row = this.focus.row();
		if (row != null) {
			return this.rowKey(row, event, order, at);
		}
		TextKeys.Caret caret = TextKeys.caret(event, mac);
		boolean fieldEnds = caret == TextKeys.Caret.TEXT_START || caret == TextKeys.Caret.TEXT_END;
		if ((Keys.isUp(event) || Keys.isDown(event)) && !fieldEnds) {
			int next = at + (Keys.isUp(event) ? -1 : 1);
			if (next >= 0 && next < order.size()) {
				this.focus(order.get(next));
			}
			return true;
		}
		CoverText.Field field = this.focus.field();
		return field != null && this.text.field(field).keyPressed(event, mac);
	}

	private boolean rowKey(Row row, KeyEvent event, List<Focus> order, int at) {
		int index = this.choices.chosen(row);
		int next;
		if (Keys.isLeft(event)) {
			next = CoverChoices.moved(row, index, -1, 0);
		} else if (Keys.isRight(event)) {
			next = CoverChoices.moved(row, index, 1, 0);
		} else if (Keys.isUp(event)) {
			next = CoverChoices.moved(row, index, 0, -1);
		} else if (Keys.isDown(event)) {
			next = CoverChoices.moved(row, index, 0, 1);
		} else if (Keys.isHome(event)) {
			next = 0;
		} else if (Keys.isEnd(event)) {
			next = row.count() - 1;
		} else {
			return false;
		}
		if (row == Row.METALWORK && Keys.isLeft(event) && index == 0) {
			int spine = order.indexOf(Focus.SPINE);
			if (spine >= 0) {
				this.focus(order.get(spine));
			}
			return true;
		}
		if (next == CoverChoices.LEAVE_UP || next == CoverChoices.LEAVE_DOWN) {
			int step = next == CoverChoices.LEAVE_UP ? -1 : 1;
			int to = at + step;
			while (to >= 0 && to < order.size() && beside(order.get(to))) {
				to += step;
			}
			if (to >= 0 && to < order.size()) {
				this.focus(order.get(to));
			}
		} else {
			this.choices.choose(row, next);
			this.keepFocusAvailable();
		}
		return true;
	}

	private boolean beside(Focus target) {
		return target == Focus.HIDE_AUTHOR || target == Focus.METALWORK || (this.focus == Focus.METALWORK && target == Focus.SPINE);
	}

	private boolean tickKey(KeyEvent event, List<Focus> order, int at) {
		if (event.input() == InputConstants.KEY_SPACE) {
			this.choices.toggleAuthorHidden();
			return true;
		}
		int to;
		if (Keys.isLeft(event)) {
			to = at - 1;
		} else if (Keys.isUp(event)) {
			to = at > 0 && order.get(at - 1) == Focus.TITLE_COLOR ? at - 2 : at - 1;
		} else if (Keys.isDown(event)) {
			to = at + 1;
		} else {
			return false;
		}
		if (to >= 0 && to < order.size()) {
			this.focus(order.get(to));
		}
		return true;
	}

	public boolean charTyped(CharacterEvent event) {
		CoverText.Field field = this.focus.field();
		return field != null && this.text.field(field).charTyped(event);
	}

	public CoverText.Field symbolField() {
		CoverText.Field field = this.focus.field();
		if (field == CoverText.Field.TITLE || (field == CoverText.Field.SUBTITLE && this.text.shown(field))) {
			return field;
		}
		return this.lastText == CoverText.Field.SUBTITLE && this.text.shown(CoverText.Field.SUBTITLE) ? CoverText.Field.SUBTITLE : CoverText.Field.TITLE;
	}

	public boolean insertSymbol(String symbol) {
		CoverText.Field field = this.symbolField();
		this.focus(Focus.of(field));
		InlineField input = this.text.field(field);
		String before = input.value();
		input.insert(symbol);
		return !input.value().equals(before);
	}

	public Hover hover(SignLayout layout, double mouseX, double mouseY) {
		Rect cover = layout.cover();
		if (cover.contains(mouseX, mouseY)) {
			CoverText.Field field = this.text.fieldAt(this.text.layout(), (int) Math.floor(mouseX) - cover.x(), (int) Math.floor(mouseY) - cover.y());
			return field == null ? Hover.NONE : new Hover(field, null);
		}
		if (this.panelShown()) {
			DesignPanel.Hit hit = DesignPanel.hit(this.choices, layout.panel().x(), layout.panel().y(), mouseX, mouseY);
			if (hit != null) {
				return new Hover(null, hit);
			}
			if (DesignPanel.overTick(layout.panel().x(), layout.panel().y(), mouseX, mouseY)) {
				return Hover.TICK;
			}
			int resting = DesignPanel.restingMetalwork(this.choices, layout.panel().x(), layout.panel().y(), mouseX, mouseY);
			if (resting >= 0) {
				return new Hover(null, null, false, resting);
			}
		}
		return Hover.NONE;
	}

	public Click click(SignLayout layout, double mouseX, double mouseY) {
		Rect cover = layout.cover();
		if (cover.contains(mouseX, mouseY)) {
			int x = (int) Math.floor(mouseX) - cover.x();
			int y = (int) Math.floor(mouseY) - cover.y();
			CoverText.Field field = this.text.fieldAt(this.text.layout(), x, y);
			if (field == null) {
				return Click.NONE;
			}
			this.focus(Focus.of(field));
			this.text.field(field).setCursor(this.text.caretAt(this.text.layout(), field, x, y));
			return Click.FIELD;
		}
		if (this.panelShown()) {
			DesignPanel.Hit hit = DesignPanel.hit(this.choices, layout.panel().x(), layout.panel().y(), mouseX, mouseY);
			if (hit != null) {
				this.choices.choose(hit.row(), hit.index());
				this.keepFocusAvailable();
				return Click.CHOICE;
			}
			if (DesignPanel.overTick(layout.panel().x(), layout.panel().y(), mouseX, mouseY)) {
				this.choices.toggleAuthorHidden();
				return Click.CHOICE;
			}
		}
		return Click.NONE;
	}

	public List<Component> tooltip(Hover hover) {
		DesignPanel.Hit hit = hover.choice();
		if (hit != null) {
			return this.choiceTooltip(hit.row(), hit.index());
		}
		if (hover.tick()) {
			return tickTooltip();
		}
		if (hover.resting() >= 0) {
			return this.restingTooltip(hover.resting());
		}
		if (hover.field() == CoverText.Field.PEN_NAME && !this.text.editingPenName) {
			return List.of(Component.translatable("scribe.sign.click_name"));
		}
		return List.of();
	}

	public List<Component> focusTooltip() {
		if (this.focus == Focus.HIDE_AUTHOR) {
			return tickTooltip();
		}
		Row row = this.focus.row();
		return row == null ? List.of() : this.choiceTooltip(row, this.choices.chosen(row));
	}

	public static List<Component> tickTooltip() {
		List<Component> lines = new ArrayList<>();
		lines.add(DesignPanel.tickName());
		for (String line : Component.translatable("scribe.sign.hide_author.tooltip").getString().split("\n")) {
			lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
		}
		return lines;
	}

	public List<Component> restingTooltip(int design) {
		List<Component> lines = new ArrayList<>();
		lines.add(CoverChoices.name(Row.METALWORK, design));
		for (FormattedText line : this.font.getSplitter().splitLines(Component.translatable("scribe.sign.metalwork_needs_buckle"), TOOLTIP_WIDTH,
			Style.EMPTY)) {
			lines.add(Component.literal(line.getString()).withStyle(ChatFormatting.GRAY));
		}
		return lines;
	}

	public static boolean bookTooltip(@Nullable Row row) {
		return row == Row.TITLE_COLOR;
	}

	private List<Component> choiceTooltip(Row row, int index) {
		if (index < 0) {
			return List.of(this.choices.chosenName(row));
		}
		return row == Row.TITLE_COLOR ? this.bookTooltip(CoverChoices.id(row, index)) : List.of(CoverChoices.name(row, index));
	}

	public List<Component> bookTooltip(@Nullable String color) {
		List<Component> lines = new ArrayList<>();
		String name = this.text.title.value().strip();
		ChatFormatting format = CoverDesign.isTitleColor(color) ? ServerBooks.nameColor(ChatFormatting.valueOf(color.toUpperCase(Locale.ROOT))) : null;
		if (name.isEmpty()) {
			lines.add(Component.translatable("item.minecraft.written_book"));
		} else if (format == null) {
			lines.add(Component.literal(name));
		} else {
			lines.add(Component.literal(name).withStyle(style -> style.withColor(format).withItalic(false)));
		}
		for (String line : this.subtitleLines()) {
			lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		lines.add(AuthorNames.byLine(this.authorShown(), this.signsWithPenName()));
		lines.add(Component.translatable("book.generation.0").withStyle(ChatFormatting.GRAY));
		return lines;
	}

	public boolean signsWithPenName() {
		return RealAuthor.isPenName(this.authorShown(), this.playerName);
	}

	public String chosenPenName() {
		return this.features.penNames() ? this.typedPenName() : "";
	}

	private String typedPenName() {
		if (!this.text.editingPenName) {
			return "";
		}
		String name = Payloads.SignDetails.cleanPenName(this.text.penName.value());
		return name.equals(this.playerName) ? "" : name;
	}

	public String authorShown() {
		String pen = this.typedPenName();
		return pen.isEmpty() ? this.playerName : pen;
	}

	public static String startingPenName(Features features, String kept) {
		return features.penNames() ? kept : "";
	}

	public static String startingSubtitle(Features features, String kept) {
		return features.subtitles() ? kept : "";
	}

	public List<String> subtitleLines() {
		return this.features.subtitles() ? this.text.subtitleLines() : List.of();
	}

	public CoverDesign coverDesign() {
		return this.features.designs() ? this.choices.design() : CoverDesign.NONE;
	}

	public CoverText.@Nullable PlaqueLook plaqueLook() {
		String metal = this.features.designs() ? this.choices.plaqueMetal() : null;
		if (metal == null) {
			return null;
		}
		ChatFormatting color = this.coverDesign().titleFormatting();
		TextColor value = color == null ? null : TextColor.fromLegacyFormat(color);
		return new CoverText.PlaqueLook(metal, value == null ? null : ARGB.opaque(value.getValue()));
	}

	public int titleInk(int[] tones) {
		ChatFormatting color = this.coverDesign().titleFormatting();
		TextColor value = color == null ? null : TextColor.fromLegacyFormat(color);
		return value == null ? tones[0] : ARGB.opaque(value.getValue());
	}

	public int titleShadow(int[] tones) {
		ChatFormatting color = this.coverDesign().titleFormatting();
		TextColor value = color == null ? null : TextColor.fromLegacyFormat(color);
		return CoverView.titleShadow(tones, value == null ? null : ARGB.opaque(value.getValue()));
	}

	public Payloads.SignDetails details() {
		Payloads.SignDetails design = designDetails(this.coverDesign());
		return new Payloads.SignDetails(this.chosenPenName(), this.subtitleLines(), design.corner(), design.decoration(), design.decorationLook(),
			design.spine(), design.titleColor(), design.author(), design.metalwork());
	}

	public static Payloads.SignDetails designDetails(CoverDesign design) {
		return new Payloads.SignDetails("", List.of(), orEmpty(design.corner()), orEmpty(design.decoration()), orEmpty(design.decorationLook()),
			orEmpty(design.spine()), orEmpty(design.titleColor()), orEmpty(design.author()), orEmpty(design.metalwork()));
	}

	private static String orEmpty(@Nullable String value) {
		return value == null ? "" : value;
	}
}
