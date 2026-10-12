package scribe.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import scribe.client.screen.SignLayout.Rect;
import scribe.client.screen.popup.SymbolPopup;
import scribe.client.widget.SpriteButton;

public final class CoverSymbols {
	private final Font font;
	private final SignForm form;
	private final Runnable relayout;
	private @Nullable SymbolPopup popup;
	private @Nullable SymbolPlacement placement;
	private int shift;
	private int width;
	private int height;

	public CoverSymbols(Font font, SignForm form, Runnable relayout) {
		this.font = font;
		this.form = form;
		this.relayout = relayout;
	}

	public SignLayout layout(int width, int height) {
		if (this.popup != null) {
			SymbolPopup open = this.popup;
			this.popup = null;
			this.placement = null;
			this.shift = 0;
			open.onClosed();
		}
		this.width = width;
		this.height = height;
		return SignLayout.of(width, height).shifted(this.shift);
	}

	public SpriteButton cell(SignLayout layout) {
		Rect at = layout.symbols();
		return new SpriteButton(at.x(), at.y(), at.width(), at.height(), "tool/symbols", Component.translatable("scribe.button.symbols"), this::toggle)
			.noFocus();
	}

	public boolean isOpen() {
		return this.popup != null;
	}

	public @Nullable SymbolPlacement placement() {
		return this.placement;
	}

	public void toggle() {
		if (this.popup != null) {
			this.close();
			return;
		}
		SymbolPlacement place = SymbolPlacement.forCover(SignLayout.of(this.width, this.height));
		if (place.shift() != this.shift) {
			this.shift = place.shift();
			this.relayout.run();
		}
		SymbolPopup popup = new SymbolPopup(this.font, this.form::insertSymbol, place.columns(), place.rows());
		popup.at(place.x(), place.y(), this.width, this.height);
		popup.setCloser(this::close);
		this.popup = popup;
		this.placement = place;
	}

	public void close() {
		SymbolPopup open = this.popup;
		this.popup = null;
		this.placement = null;
		if (open != null) {
			open.onClosed();
		}
		if (this.shift != 0) {
			this.shift = 0;
			this.relayout.run();
		}
		this.keyboardBack();
	}

	public void removed() {
		SymbolPopup open = this.popup;
		this.popup = null;
		this.placement = null;
		this.shift = 0;
		if (open != null) {
			open.onClosed();
		}
	}

	private void keyboardBack() {
		if (this.popup == null || !this.popup.takesKeys()) {
			this.form.focus(this.form.focus());
		}
	}

	public boolean over(double mouseX, double mouseY) {
		return this.popup != null && this.popup.contains(mouseX, mouseY);
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		SymbolPopup popup = this.popup;
		if (popup == null) {
			return false;
		}
		if (popup.contains(mouseX, mouseY)) {
			popup.mouseClicked(mouseX, mouseY, button);
			this.keyboardBack();
			return true;
		}
		popup.clickedOutside();
		this.keyboardBack();
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.popup != null && this.popup.contains(mouseX, mouseY)) {
			this.popup.mouseScrolled(mouseX, mouseY, amount);
			return true;
		}
		return false;
	}

	public boolean keyPressed(KeyEvent event) {
		SymbolPopup popup = this.popup;
		if (popup == null) {
			return false;
		}
		if (popup.takesKeys()) {
			if (!popup.keyPressed(event) && event.isEscape()) {
				this.close();
			}
			return true;
		}
		if (event.isEscape()) {
			this.close();
			return true;
		}
		return false;
	}

	public boolean charTyped(CharacterEvent event) {
		if (this.popup != null && this.popup.takesKeys()) {
			this.popup.charTyped(event);
			return true;
		}
		return false;
	}

	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (this.popup != null) {
			graphics.nextStratum();
			this.popup.render(graphics, mouseX, mouseY);
		}
	}

	public boolean coversDesignPanel(SignLayout layout) {
		SymbolPlacement place = this.placement;
		return place != null && new Rect(place.x(), place.y(), place.width(), place.height()).intersects(layout.panel());
	}
}
