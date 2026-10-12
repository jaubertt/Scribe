package scribe.client.screen.popup;

import scribe.client.compat.Keys;
import scribe.client.screen.BookStyle;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class MenuPopup extends Popup {
	private static final int ROW = 11;
	private static final int SHORTCUT_COLOR = 0xFF9A8B76;

	public record Item(Component label, @Nullable String shortcut, boolean enabled, @Nullable Component tooltip, Runnable action) {
		public Item(Component label, @Nullable String shortcut, boolean enabled, Runnable action) {
			this(label, shortcut, enabled, null, action);
		}
	}

	private final List<Item> items;
	private int keyboardRow = -1;

	public MenuPopup(Font font, List<Item> items) {
		super(font, width(font, items), items.size() * ROW + 8);
		this.items = items;
	}

	private static int width(Font font, List<Item> items) {
		int max = 0;
		for (Item item : items) {
			int w = font.width(item.label()) + (item.shortcut() == null ? 0 : font.width(item.shortcut()) + 12);
			max = Math.max(max, w);
		}
		return max + 10;
	}

	private int rowAt(double mouseX, double mouseY) {
		if (!this.contains(mouseX, mouseY)) {
			return -1;
		}
		int row = (int) ((mouseY - this.y - 3) / ROW);
		return row >= 0 && row < this.items.size() ? row : -1;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		BookStyle.paper(graphics, this.x, this.y, this.width, this.height);
		int underMouse = this.rowAt(mouseX, mouseY);
		int hovered = underMouse >= 0 ? underMouse : this.keyboardRow;
		for (int i = 0; i < this.items.size(); i++) {
			Item item = this.items.get(i);
			int rowY = this.y + 4 + i * ROW;
			if (i == hovered && item.enabled()) {
				graphics.fill(this.x + 1, rowY - 1, this.x + this.width - 1, rowY + 9, BookStyle.PAPER_HOVER);
			}
			graphics.text(this.font, item.label(), this.x + 5, rowY, item.enabled() ? BookStyle.INK : BookStyle.INK_SOFT, false);
			if (item.shortcut() != null) {
				graphics.text(this.font, item.shortcut(), this.x + this.width - 5 - this.font.width(item.shortcut()), rowY, SHORTCUT_COLOR, false);
			}
		}
		if (hovered >= 0 && hovered < this.items.size() && this.items.get(hovered).tooltip() != null) {
			Component tooltip = this.items.get(hovered).tooltip();
			int tx = underMouse >= 0 ? mouseX : this.x + this.width;
			int ty = underMouse >= 0 ? mouseY : this.y + 4 + hovered * ROW;
			graphics.setTooltipForNextFrame(this.font, this.font.split(tooltip, 170), tx, ty);
		}
	}

	@Override
	public void mouseClicked(double mouseX, double mouseY, int button) {
		this.run(this.rowAt(mouseX, mouseY));
	}

	private void run(int row) {
		if (row >= 0 && row < this.items.size() && this.items.get(row).enabled()) {
			this.close();
			this.items.get(row).action().run();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (Keys.isDown(event)) {
			this.keyboardRow = (this.keyboardRow + 1) % this.items.size();
			return true;
		}
		if (Keys.isUp(event)) {
			this.keyboardRow = (this.keyboardRow - 1 + this.items.size()) % this.items.size();
			return true;
		}
		if (Keys.isEnter(event)) {
			this.run(this.keyboardRow);
			return true;
		}
		return false;
	}
}
