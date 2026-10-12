package scribe.client.screen.popup;

import scribe.Scribe;
import scribe.client.memory.BookmarkStore;
import scribe.client.screen.BookStyle;
import scribe.client.widget.InlineField;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

public class BookmarkPopup extends Popup {
	private static final int SWATCH_STEP = 12;
	private static final int FIELD_Y = 45;
	static final Identifier TRASH = Scribe.id("popup/trash");
	static final Identifier TRASH_HIGHLIGHTED = Scribe.id("popup/trash_highlighted");
	static final int TRASH_WIDTH = 8;
	static final int TRASH_HEIGHT = 7;

	private final BookmarkStore.Marks marks;
	private final int page;
	private final InlineField name = new InlineField(BookmarkStore.MAX_NAME);
	private DyeColor color;
	private boolean exists;

	public BookmarkPopup(Font font, BookmarkStore.Marks marks, int page) {
		super(font, Math.max(106, font.width(title(page)) + 12 + TRASH_WIDTH + 6), FIELD_Y + 17);
		this.marks = marks;
		this.page = page;
		Optional<BookmarkStore.Bookmark> existing = marks.at(page);
		this.exists = existing.isPresent();
		this.color = existing.map(BookmarkStore.Bookmark::color).orElseGet(marks::nextColor);
		this.name.setValue(existing.map(BookmarkStore.Bookmark::name).orElse(""));
		this.name.setFocused(false);
		this.name.responder(value -> this.store());
	}

	private static Component title(int page) {
		return Component.translatable("scribe.bookmark.title", page + 1);
	}

	private void store() {
		this.marks.put(new BookmarkStore.Bookmark(this.page, this.color, this.name.value()));
		this.exists = true;
	}

	private int swatchX(int i) {
		return this.x + 6 + (i % 8) * SWATCH_STEP;
	}

	private int swatchY(int i) {
		return this.y + 18 + (i / 8) * SWATCH_STEP;
	}

	private boolean inField(double mouseX, double mouseY) {
		return mouseX >= this.x + 5 && mouseX < this.x + this.width - 5 && mouseY >= this.y + FIELD_Y && mouseY < this.y + FIELD_Y + 11;
	}

	private int trashX() {
		return this.x + this.width - 5 - TRASH_WIDTH;
	}

	private int trashY() {
		return this.y + 5;
	}

	private boolean inTrash(double mouseX, double mouseY) {
		return this.exists && mouseX >= this.trashX() - 2 && mouseX < this.trashX() + TRASH_WIDTH + 2
			&& mouseY >= this.trashY() - 2 && mouseY < this.trashY() + TRASH_HEIGHT + 2;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		BookStyle.paper(graphics, this.x, this.y, this.width, this.height);
		graphics.text(this.font, title(this.page), this.x + 5, this.y + 5, BookStyle.INK, false);
		if (this.exists) {
			boolean hot = this.inTrash(mouseX, mouseY);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hot ? TRASH_HIGHLIGHTED : TRASH, this.trashX(), this.trashY(), TRASH_WIDTH, TRASH_HEIGHT);
			if (hot) {
				graphics.setTooltipForNextFrame(this.font, Component.translatable("scribe.bookmark.remove"), mouseX, mouseY);
			}
		}
		for (int i = 0; i < 16; i++) {
			DyeColor dye = DyeColor.byId(i);
			int sx = this.swatchX(i);
			int sy = this.swatchY(i);
			boolean selected = this.exists && dye == this.color;
			boolean hovered = mouseX >= sx - 1 && mouseX < sx + 11 && mouseY >= sy - 1 && mouseY < sy + 11;
			graphics.fill(sx - 1, sy - 1, sx + 11, sy + 11, selected || hovered ? 0xFF2E1E10 : 0xFF9C8566);
			graphics.fill(sx, sy, sx + 10, sy + 10, BookStyle.dye(dye));
			if (selected) {
				graphics.outline(sx - 2, sy - 2, 14, 14, BookStyle.INK);
			}
			if (hovered) {
				graphics.setTooltipForNextFrame(this.font, Component.translatable("color.minecraft." + dye.getSerializedName()), mouseX, mouseY);
			}
		}
		BookStyle.field(graphics, this.x + 5, this.y + FIELD_Y, this.width - 10, 11);
		this.name.render(graphics, this.font, this.x + 8, this.y + FIELD_Y + 2, this.width - 16, BookStyle.INK,
			Component.translatable("scribe.bookmark.name_hint").getString(), BookStyle.INK_SOFT);
	}

	@Override
	public void mouseClicked(double mouseX, double mouseY, int button) {
		if (this.inTrash(mouseX, mouseY)) {
			this.marks.remove(this.page);
			this.exists = false;
			this.close();
			return;
		}
		for (int i = 0; i < 16; i++) {
			int sx = this.swatchX(i);
			int sy = this.swatchY(i);
			if (mouseX >= sx - 1 && mouseX < sx + 11 && mouseY >= sy - 1 && mouseY < sy + 11) {
				this.color = DyeColor.byId(i);
				this.store();
				return;
			}
		}
		this.name.setFocused(this.inField(mouseX, mouseY));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation()) {
			if (!this.exists) {
				this.store();
			}
			this.close();
			return true;
		}
		return this.name.keyPressed(event);
	}

	@Override
	public void onClosed() {
		this.name.setFocused(false);
		this.marks.done();
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!this.name.focused()) {
			this.name.setFocused(true);
		}
		return this.name.charTyped(event);
	}
}
