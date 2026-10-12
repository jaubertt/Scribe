package scribe.client.screen;

import scribe.client.screen.popup.Popup;
import scribe.client.widget.CornerButton;
import scribe.config.ScribeConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public abstract class BookScreen extends Screen {
	private static final long NOTICE_MS = 2500;

	protected BookGeometry geo;
	protected int cover = BookStyle.COVER_UNDYED;
	protected @Nullable String material;
	protected @Nullable String corner;
	private @Nullable Popup popup;
	private int shift;
	private @Nullable Component notice;
	private long noticeUntil;

	protected BookScreen(Component title) {
		super(title);
	}

	@Override
	protected final void init() {
		Popup open = this.popup;
		this.popup = null;
		if (open != null) {
			open.onClosed();
			this.shift = 0;
		}
		this.geo = BookGeometry.of(this.width, this.height).shifted(this.shift);
		this.initBook();
	}

	protected abstract void initBook();

	@Override
	public void removed() {
		super.removed();
		this.minecraft.textInputManager().stopTextInput();
	}

	protected void addCornerButtons(boolean layoutToggle) {
		int w = this.width;
		if (layoutToggle) {
			boolean spread = this.geo.spread;
			this.addRenderableWidget(new CornerButton(w - 46, 4, spread ? "icon/layout_single" : "icon/layout_spread",
				Component.translatable(spread ? "scribe.button.layout_single" : "scribe.button.layout_spread"), this::toggleLayout));
		}
		this.addRenderableWidget(new CornerButton(w - 31, 4, "icon/settings", Component.translatable("scribe.button.settings"), this::openSettings));
		this.addRenderableWidget(new CornerButton(w - 15, 4, null, Component.translatable("scribe.button.help"), this::showHelp));
	}

	protected void toggleLayout() {
		ScribeConfig config = ScribeConfig.get();
		config.layout = this.geo.spread ? ScribeConfig.Layout.SINGLE : ScribeConfig.Layout.SPREAD;
		config.save();
		this.rebuildWidgets();
	}

	protected void openSettings() {
	}

	protected void showHelp() {
	}

	public void openPopup(Popup popup, int x, int y) {
		this.openPopup(popup, x, y, 0);
	}

	public void openPopup(Popup popup, int x, int y, int shift) {
		this.closePopup();
		this.moveBook(shift);
		popup.at(x, y, this.width, this.height);
		popup.setCloser(this::closePopup);
		this.popup = popup;
	}

	public void closePopup() {
		Popup open = this.popup;
		this.popup = null;
		if (open != null) {
			open.onClosed();
		}
		this.moveBook(0);
	}

	private void moveBook(int shift) {
		if (shift != this.shift) {
			this.shift = shift;
			this.rebuildWidgets();
		}
	}

	public @Nullable Popup popup() {
		return this.popup;
	}

	public boolean popupTakesInput() {
		return this.popup != null && (this.popup.modal() || this.popup.takesKeys());
	}

	public void movePopup(int x, int y) {
		if (this.popup != null) {
			this.popup.at(x, y, this.width, this.height);
		}
	}

	public void notice(Component message) {
		this.notice = message;
		this.noticeUntil = Util.getMillis() + NOTICE_MS;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		this.drawBook(graphics);
	}

	protected void drawBook(GuiGraphicsExtractor graphics) {
		int left = this.geo.left;
		int top = this.geo.top;
		if (this.geo.spread) {
			BookStyle.book(graphics, left, top, 0, 0, BookGeometry.SPREAD_WIDTH, BookGeometry.HEIGHT, this.cover);
			BookStyle.book(graphics, left, top, 0, 180, BookGeometry.SPREAD_WIDTH, BookGeometry.HEIGHT, -1);
		} else {
			BookStyle.singlePage(graphics, left, top, this.cover);
		}
		if (this.material != null && ScribeConfig.get().personalizationOn()) {
			BookStyle.corners(graphics, left, top, this.geo.width, this.geo.spread, this.material, this.corner, this.cover);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean overPopup = this.popup != null && this.popup.contains(mouseX, mouseY);
		int mx = overPopup ? -1000 : mouseX;
		int my = overPopup ? -1000 : mouseY;
		super.extractRenderState(graphics, mx, my, a);
		this.extractPages(graphics, mx, my, a);
		Component shown = this.notice != null && Util.getMillis() < this.noticeUntil ? this.notice : this.lastingNotice();
		if (shown != null) {
			graphics.nextStratum();
			int w = this.font.width(shown) + 10;
			int x = this.geo.centerX() - w / 2;
			int y = this.geo.top + 3;
			BookStyle.paper(graphics, x, y, w, 13);
			graphics.text(this.font, shown, x + 5, y + 3, BookStyle.INK, false);
		}
		if (this.popup != null) {
			graphics.nextStratum();
			this.popup.render(graphics, mouseX, mouseY);
		}
	}

	protected void extractPages(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
	}

	protected @Nullable Component lastingNotice() {
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.popup != null) {
			if (this.popup.contains(event.x(), event.y())) {
				this.popup.mouseClicked(event.x(), event.y(), event.button());
				return true;
			}
			if (this.popup.modal()) {
				this.closePopup();
				return true;
			}
			this.popup.clickedOutside();
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.popup != null) {
			if (this.popup.contains(x, y)) {
				this.popup.mouseScrolled(x, y, scrollY);
				return true;
			}
			if (this.popup.modal()) {
				return true;
			}
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.popup != null) {
			if (this.popupTakesInput()) {
				if (this.popup.keyPressed(event)) {
					return true;
				}
				if (event.isEscape()) {
					this.closePopup();
				}
				return true;
			}
			if (event.isEscape()) {
				this.closePopup();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (this.popupTakesInput()) {
			this.popup.charTyped(event);
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}
}
