package scribe.client.screen;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;
import scribe.RealAuthor;
import scribe.binding.Binding;
import scribe.binding.Bookbinding;
import scribe.binding.CoverDesign;
import scribe.client.BookNet;
import scribe.client.compat.Keys;
import scribe.client.screen.SignLayout.Rect;
import scribe.network.Payloads;
import scribe.server.ServerBooks;

public class CoverEditScreen extends Screen {
	private final ItemStack book;
	private final int slot;
	private final boolean override;
	private final int cover;
	private final SignForm form;
	private final CoverSymbols symbols;
	private final CoverDesign startDesign;
	private SignLayout layout = SignLayout.of(0, 0);
	private @Nullable Button saveButton;
	private @Nullable CoverView coverArt;
	private @Nullable CoverDesign coverArtDesign;

	public CoverEditScreen(ItemStack book, int slot, String playerName) {
		this(book, slot, playerName, false);
	}

	public CoverEditScreen(ItemStack book, int slot, String playerName, boolean override) {
		super(Component.translatable("scribe.cover_edit.title"));
		this.book = book.copy();
		this.slot = slot;
		this.override = override;
		this.cover = BookStyle.cover(book);
		SignForm.Features features = SignForm.Features.current();
		this.form = form(this.font, book, playerName, features);
		this.startDesign = this.form.coverDesign();
		this.form.title().responder(value -> this.updateButtons());
		this.symbols = new CoverSymbols(this.font, this.form, this::rebuildWidgets);
	}

	public static SignForm form(Font font, ItemStack book, String playerName, SignForm.Features features) {
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		String title = content == null ? "" : content.title().raw();
		String author = content == null ? "" : content.author();
		String penName = RealAuthor.isPenName(author, playerName) ? author : "";
		CoverChoices choices = CoverChoices.ofSigned(Bookbinding.materialName(Binding.of(book)), CoverDesign.of(book))
			.bound(Binding.of(book), content == null ? 0 : content.generation());
		return new SignForm(font, features, choices, playerName, title, String.join(" ", CoverView.subtitleLines(book)), penName);
	}

	public static Payloads.SignDetails changedDesign(CoverDesign now, CoverDesign start) {
		boolean decoration = !Objects.equals(now.decoration(), start.decoration()) || !Objects.equals(now.decorationLook(), start.decorationLook());
		String author = now.hidesAuthor() == start.hidesAuthor() ? "" : now.hidesAuthor() ? CoverDesign.AUTHOR_HIDDEN : CoverDesign.AUTHOR_SHOWN;
		return new Payloads.SignDetails("", List.of(),
			Objects.equals(now.corner(), start.corner()) ? "" : orEmpty(now.corner()),
			decoration ? orEmpty(now.decoration()) : "",
			decoration ? orEmpty(now.decorationLook()) : "",
			Objects.equals(now.spine(), start.spine()) ? "" : orEmpty(now.spine()),
			Objects.equals(now.titleColor(), start.titleColor()) ? "" : orEmpty(now.titleColor()),
			author,
			Objects.equals(now.metalwork(), start.metalwork()) ? "" : orEmpty(now.metalwork()));
	}

	private static String orEmpty(@Nullable String value) {
		return value == null ? "" : value;
	}

	public static Payloads.CoverDesignerSave saved(int slot, SignForm form, CoverDesign startDesign) {
		Payloads.SignDetails design = changedDesign(form.coverDesign(), startDesign);
		Payloads.SignDetails text = form.details();
		return new Payloads.CoverDesignerSave(slot, form.title().value(), new Payloads.SignDetails(text.penName(), text.subtitle(), design.corner(),
			design.decoration(), design.decorationLook(), design.spine(), design.titleColor(), design.author(), design.metalwork()));
	}

	public static ItemStack previewBook(ItemStack book, Payloads.SignDetails changed) {
		ItemStack preview = book.copy();
		if (changed.hasDesign()) {
			CoverDesign before = CoverDesign.of(preview);
			CoverDesign kept = ServerBooks.signedDesign(preview, changed);
			if (!kept.equals(before)) {
				kept.applyTo(preview);
			}
		}
		return preview;
	}

	public SignForm form() {
		return this.form;
	}

	@Override
	protected void init() {
		this.layout = this.symbols.layout(this.width, this.height);
		this.addRenderableWidget(this.symbols.cell(this.layout));
		Rect save = this.layout.button(0, 2);
		Rect cancel = this.layout.button(1, 2);
		this.saveButton = this.addRenderableWidget(Button.builder(Component.translatable("scribe.cover_edit.save"), b -> this.save())
			.bounds(save.x(), save.y(), save.width(), save.height()).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
			.bounds(cancel.x(), cancel.y(), cancel.width(), cancel.height()).build());
		this.updateButtons();
	}

	private void updateButtons() {
		if (this.saveButton != null) {
			this.saveButton.active = !this.form.title().value().isBlank();
		}
	}

	@Override
	protected void setInitialFocus() {
		this.form.focus(this.form.focus());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean overSymbols = this.symbols.over(mouseX, mouseY);
		int mx = overSymbols ? -1000 : mouseX;
		int my = overSymbols ? -1000 : mouseY;
		SignForm.Hover hover = this.form.hover(this.layout, mx, my);
		Rect at = this.layout.cover();
		CoverView art = this.coverArt();
		if (art != null) {
			art.drawArt(graphics, at.x(), at.y(), false);
		}
		int[] tones = CoverView.textTones(this.cover);
		Integer titleColor = art == null ? null : art.titleColor();
		this.form.text().draw(graphics, at.x(), at.y(), tones, titleColor != null ? titleColor : tones[0], CoverView.titleShadow(tones, titleColor),
			this.form.focus().field(), hover.field());
		Rect panel = this.layout.panel();
		Component note = this.form.note();
		if (note == null) {
			DesignPanel.draw(graphics, this.font, panel.x(), panel.y(), this.form.choices(), this.cover, this.form.focus().row(), hover.choice(),
				this.form.focus() == SignForm.Focus.HIDE_AUTHOR, hover.tick());
		} else {
			this.note(graphics, note);
		}
		super.extractRenderState(graphics, mx, my, a);
		if (!overSymbols) {
			this.tooltips(graphics, hover, mx, my);
		}
		this.symbols.render(graphics, mouseX, mouseY);
	}

	private void tooltips(GuiGraphicsExtractor graphics, SignForm.Hover hover, int mouseX, int mouseY) {
		List<Component> lines = this.form.tooltip(hover);
		if (!lines.isEmpty()) {
			boolean book = hover.choice() != null && SignForm.bookTooltip(hover.choice().row());
			graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY, null, book);
			return;
		}
		if (this.symbols.coversDesignPanel(this.layout)) {
			return;
		}
		CoverChoices.Row row = this.form.focus().row();
		if (row != null && this.form.panelShown()) {
			Rect inside = DesignPanel.inside(row, Math.max(0, this.form.choices().chosen(row))).moved(this.layout.panel().x(), this.layout.panel().y());
			graphics.setTooltipForNextFrame(this.font, this.form.focusTooltip(), Optional.empty(), inside.right() - 12, inside.bottom() + 12, null,
				SignForm.bookTooltip(row));
		} else if (this.form.focus() == SignForm.Focus.HIDE_AUTHOR && this.form.panelShown()) {
			Rect box = DesignPanel.tickBox(this.form.choices().authorHidden()).moved(this.layout.panel().x(), this.layout.panel().y());
			graphics.setTooltipForNextFrame(this.font, this.form.focusTooltip(), Optional.empty(), box.right() - 12, box.bottom() + 12, null, false);
		}
	}

	private void note(GuiGraphicsExtractor graphics, Component text) {
		List<FormattedCharSequence> lines = this.font.split(text, SignLayout.noteTextWidth());
		Rect paper = this.layout.note(lines.size());
		BookStyle.paper(graphics, paper.x(), paper.y(), paper.width(), paper.height());
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(this.font, lines.get(i), paper.x() + SignLayout.NOTE_PAD, paper.y() + SignLayout.NOTE_PAD + i * SignLayout.NOTE_LINE,
				BookStyle.INK, false);
		}
	}

	private @Nullable CoverView coverArt() {
		CoverDesign design = this.form.coverDesign();
		if (this.coverArt == null || !design.equals(this.coverArtDesign)) {
			this.coverArtDesign = design;
			this.coverArt = CoverView.build(previewBook(this.book, changedDesign(design, this.startDesign)), this.cover, this.font, false);
		}
		return this.coverArt;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.symbols.mouseClicked(event.x(), event.y(), event.button())) {
			return true;
		}
		if (Keys.isLeftClick(event.button())) {
			SignForm.Click click = this.form.click(this.layout, event.x(), event.y());
			if (click == SignForm.Click.CHOICE) {
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.3F, 0.35F));
				return true;
			}
			if (click == SignForm.Click.FIELD) {
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return this.symbols.mouseScrolled(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.symbols.keyPressed(event)) {
			return true;
		}
		if (event.isEscape()) {
			this.onClose();
			return true;
		}
		if (event.isConfirmation()) {
			this.save();
			return true;
		}
		return this.form.keyPressed(event) || super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return this.symbols.charTyped(event) || this.form.charTyped(event) || super.charTyped(event);
	}

	private void save() {
		if (this.form.title().value().isBlank()) {
			return;
		}
		Payloads.CoverDesignerSave saved = saved(this.slot, this.form, this.startDesign);
		BookNet.sendCoverDesigner(saved.slot(), saved.title(), saved.details(), this.override);
		this.onClose();
	}

	@Override
	public void removed() {
		super.removed();
		this.symbols.removed();
		this.minecraft.textInputManager().stopTextInput();
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}
}
