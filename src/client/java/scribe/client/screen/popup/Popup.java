package scribe.client.screen.popup;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

public abstract class Popup {
	protected final Font font;
	protected int x;
	protected int y;
	protected int width;
	protected int height;
	private Runnable closer = () -> {};

	protected Popup(Font font, int width, int height) {
		this.font = font;
		this.width = width;
		this.height = height;
	}

	public final void setCloser(Runnable closer) {
		this.closer = closer;
	}

	protected final void close() {
		this.closer.run();
	}

	public Popup at(int x, int y, int screenWidth, int screenHeight) {
		this.x = Math.clamp(x, 2, Math.max(2, screenWidth - this.width - 2));
		this.y = Math.clamp(y, 2, Math.max(2, screenHeight - this.height - 2));
		return this;
	}

	public int width() {
		return this.width;
	}

	public int height() {
		return this.height;
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
	}

	public abstract void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

	public void mouseClicked(double mouseX, double mouseY, int button) {
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		return false;
	}

	public boolean keyPressed(KeyEvent event) {
		return false;
	}

	public boolean charTyped(CharacterEvent event) {
		return false;
	}

	public void onClosed() {
	}

	public boolean modal() {
		return true;
	}

	public boolean takesKeys() {
		return this.modal();
	}

	public void clickedOutside() {
	}
}
