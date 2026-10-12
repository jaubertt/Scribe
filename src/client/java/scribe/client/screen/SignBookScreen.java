package scribe.client.screen;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;
import scribe.binding.Binding;
import scribe.binding.Bookbinding;
import scribe.binding.CoverDesign;
import scribe.client.BookInfoTooltip;
import scribe.client.BookNet;
import scribe.client.compat.Keys;
import scribe.client.screen.SignLayout.Rect;
import scribe.server.ServerBooks;

public class SignBookScreen extends Screen {
	private final BookEditorScreen editor;
	private final int cover;
	private final SignForm form;
	private final CoverSymbols symbols;
	private final int pages;
	private final int chapters;
	private SignLayout layout = SignLayout.of(0, 0);
	private @Nullable Button signButton;
	private boolean leaving;
	private @Nullable CoverView coverArt;
	private @Nullable CoverDesign coverArtDesign;

	public SignBookScreen(BookEditorScreen editor) {
		super(Component.translatable("book.signButton"));
		this.editor = editor;
		this.cover = editor.cover();
		this.pages = editor.pagesForSigning().size();
		this.chapters = editor.chapterCount();
		ItemStack book = editor.target().book();
		CoverChoices choices = CoverChoices.resume(editor.signChoices(), Bookbinding.materialName(Binding.of(book)), CoverDesign.of(book))
			.bound(Binding.of(book), 0);
		SignForm.Features features = SignForm.Features.current();
		this.form = new SignForm(this.font, features, choices, editor.target().playerName(), editor.signTitle(),
			SignForm.startingSubtitle(features, editor.signSubtitle()), SignForm.startingPenName(features, editor.signPenName()));
		this.form.title().responder(value -> this.updateButtons());
		this.symbols = new CoverSymbols(this.font, this.form, this::rebuildWidgets);
	}

	@Override
	protected void init() {
		this.layout = this.symbols.layout(this.width, this.height);
		this.addRenderableWidget(this.symbols.cell(this.layout));
		Rect sign = this.layout.button(0);
		Rect save = this.layout.button(1);
		Rect cancel = this.layout.button(2);
		this.signButton = this.addRenderableWidget(Button.builder(Component.translatable("book.finalizeButton"), b -> this.sign())
			.bounds(sign.x(), sign.y(), sign.width(), sign.height()).build());
		this.addRenderableWidget(Button.builder(Component.translatable("scribe.sign.save_details"), b -> this.saveDetails())
			.tooltip(Tooltip.create(Component.translatable(this.form.note() == null && BookNet.savesDesigns()
				? "scribe.sign.save_details.tooltip_design" : "scribe.sign.save_details.tooltip")))
			.bounds(save.x(), save.y(), save.width(), save.height()).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
			.bounds(cancel.x(), cancel.y(), cancel.width(), cancel.height()).build());
		this.updateButtons();
	}

	private void updateButtons() {
		if (this.signButton != null) {
			this.signButton.active = !this.form.title().value().isBlank();
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
		this.form.text().draw(graphics, at.x(), at.y(), tones, this.form.titleInk(tones), this.form.titleShadow(tones), this.form.focus().field(), hover.field());
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
		if (this.layout.button(0).contains(mouseX, mouseY)) {
			graphics.setComponentTooltipForNextFrame(this.font, List.of(Component.translatable("scribe.sign.warning"),
				BookInfoTooltip.countLine(this.pages, this.chapters)), mouseX, mouseY);
			return;
		}
		if (this.symbols.coversDesignPanel(this.layout)) {
			return;
		}
		CoverChoices.Row row = this.form.focus().row();
		if (row != null && this.form.panelShown()) {
			Rect inside = DesignPanel.inside(row, this.form.choices().chosen(row)).moved(this.layout.panel().x(), this.layout.panel().y());
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
			this.coverArt = CoverView.build(previewBook(this.editor.target().book(), design), this.cover, this.font, false);
		}
		return this.coverArt;
	}

	static ItemStack previewBook(ItemStack bookAndQuill, CoverDesign design) {
		ItemStack written = bookAndQuill.isEmpty() ? new ItemStack(Items.WRITTEN_BOOK) : bookAndQuill.transmuteCopy(Items.WRITTEN_BOOK, 1);
		written.remove(DataComponents.WRITABLE_BOOK_CONTENT);
		written.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(""), "", 0, List.of(), true));
		CoverDesign before = CoverDesign.of(written);
		CoverDesign kept = ServerBooks.signedDesign(written, SignForm.designDetails(design));
		if (!kept.equals(before)) {
			kept.applyTo(written);
		}
		return written;
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
			this.sign();
			return true;
		}
		return this.form.keyPressed(event) || super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return this.symbols.charTyped(event) || this.form.charTyped(event) || super.charTyped(event);
	}

	private void sign() {
		String name = this.form.title().value().trim();
		if (name.isEmpty()) {
			return;
		}
		this.leaving = true;
		this.remember();
		this.editor.signAndClose(name, this.form.details());
	}

	private void saveDetails() {
		this.leaving = true;
		boolean design = this.form.note() == null && BookNet.savesDesigns();
		if (design) {
			this.editor.saveDesign(SignForm.designDetails(this.form.coverDesign()));
		}
		this.editor.saveSignFields(this.form.title().value(), this.form.subtitle().value(), this.form.penNameToKeep(), design);
		this.editor.rememberSignChoices(this.form.choices());
		this.minecraft.gui.setScreen(this.editor);
	}

	private void remember() {
		this.editor.rememberSignFields(this.form.title().value(), this.form.subtitle().value(), this.form.penNameToKeep());
		this.editor.rememberSignChoices(this.form.choices());
	}

	@Override
	public void onClose() {
		this.leaving = true;
		this.remember();
		this.minecraft.gui.setScreen(this.editor);
	}

	@Override
	public void removed() {
		super.removed();
		this.symbols.removed();
		this.minecraft.textInputManager().stopTextInput();
		if (!this.leaving) {
			this.remember();
			this.editor.autosaveFromChild();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return this.editor.isPauseScreen();
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}
}
