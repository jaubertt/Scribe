package scribe.client.screen;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.DyeColor;

final class ColorDropdown {
	private static final int PAD = 4;
	private static final int COLUMNS = 4;
	private static final int SWATCH_W = 18;
	private static final int SWATCH_H = 12;
	private static final int GAP = 2;
	private static final int NONE_H = 12;
	private static final int NAME_H = 12;
	private static final int BACKGROUND = 0xF0100010;
	private static final int BORDER = 0xFF8B8B8B;

	private final Font font;
	private final Component noneLabel;
	private final Optional<DyeColor> current;
	private final Consumer<Optional<DyeColor>> pick;
	private final int x;
	private final int y;
	private final int width;
	private final int height;

	ColorDropdown(Font font, Component noneLabel, Optional<DyeColor> current, int buttonX, int buttonTop, int buttonBottom,
		int screenWidth, int screenHeight, Consumer<Optional<DyeColor>> pick) {
		this.font = font;
		this.noneLabel = noneLabel;
		this.current = current;
		this.pick = pick;
		int grid = COLUMNS * SWATCH_W + (COLUMNS - 1) * GAP;
		this.width = Math.max(grid, font.width(noneLabel) + 8) + PAD * 2;
		int rows = (DyeColor.values().length + COLUMNS - 1) / COLUMNS;
		this.height = PAD + NONE_H + GAP + rows * (SWATCH_H + GAP) + NAME_H + PAD - GAP;
		this.x = Math.clamp(buttonX, 2, Math.max(2, screenWidth - this.width - 2));
		int below = buttonBottom + 1;
		this.y = below + this.height <= screenHeight - 2 ? below : Math.max(2, buttonTop - 1 - this.height);
	}

	boolean contains(double mouseX, double mouseY) {
		return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
	}

	private int noneTop() {
		return this.y + PAD;
	}

	private int gridTop() {
		return this.noneTop() + NONE_H + GAP;
	}

	private int swatchX(int i) {
		return this.x + PAD + (i % COLUMNS) * (SWATCH_W + GAP);
	}

	private int swatchY(int i) {
		return this.gridTop() + (i / COLUMNS) * (SWATCH_H + GAP);
	}

	private boolean overNone(double mouseX, double mouseY) {
		return mouseX >= this.x + PAD && mouseX < this.x + this.width - PAD && mouseY >= this.noneTop() && mouseY < this.noneTop() + NONE_H;
	}

	private int swatchAt(double mouseX, double mouseY) {
		DyeColor[] colors = DyeColor.values();
		for (int i = 0; i < colors.length; i++) {
			int sx = this.swatchX(i);
			int sy = this.swatchY(i);
			if (mouseX >= sx && mouseX < sx + SWATCH_W && mouseY >= sy && mouseY < sy + SWATCH_H) {
				return i;
			}
		}
		return -1;
	}

	static Component colorName(Optional<DyeColor> color, Component noneLabel) {
		return color.<Component>map(dye -> Component.translatable("color.minecraft." + dye.getSerializedName()).withColor(dye.getTextColor()))
			.orElse(noneLabel);
	}

	void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BACKGROUND);
		graphics.outline(this.x, this.y, this.width, this.height, BORDER);

		boolean noneHot = this.overNone(mouseX, mouseY);
		int noneTop = this.noneTop();
		if (this.current.isEmpty() || noneHot) {
			graphics.outline(this.x + PAD - 1, noneTop - 1, this.width - PAD * 2 + 2, NONE_H + 2, this.current.isEmpty() ? 0xFFFFFFFF : 0xFFA0A0A0);
		}
		graphics.text(this.font, this.noneLabel, this.x + PAD + 3, noneTop + 2, 0xFFE0E0E0, false);

		DyeColor[] colors = DyeColor.values();
		int hot = this.swatchAt(mouseX, mouseY);
		for (int i = 0; i < colors.length; i++) {
			int sx = this.swatchX(i);
			int sy = this.swatchY(i);
			boolean picked = this.current.isPresent() && this.current.get() == colors[i];
			if (picked || i == hot) {
				graphics.outline(sx - 1, sy - 1, SWATCH_W + 2, SWATCH_H + 2, picked ? 0xFFFFFFFF : 0xFFA0A0A0);
			}
			graphics.fill(sx, sy, sx + SWATCH_W, sy + SWATCH_H, 0xFF000000);
			graphics.fill(sx + 1, sy + 1, sx + SWATCH_W - 1, sy + SWATCH_H - 1, ARGB.opaque(colors[i].getTextureDiffuseColor()));
		}

		Optional<DyeColor> shown = hot >= 0 ? Optional.of(colors[hot]) : noneHot ? Optional.empty() : this.current;
		Component name = colorName(shown, this.noneLabel);
		int nameY = this.y + this.height - PAD - NAME_H + 3;
		graphics.text(this.font, this.font.plainSubstrByWidth(name.getString(), this.width - PAD * 2), this.x + PAD, nameY,
			shown.map(DyeColor::getTextColor).map(ARGB::opaque).orElse(0xFFE0E0E0), false);
	}

	boolean mouseClicked(double mouseX, double mouseY) {
		if (this.overNone(mouseX, mouseY)) {
			this.pick.accept(Optional.empty());
			return true;
		}
		int i = this.swatchAt(mouseX, mouseY);
		if (i >= 0) {
			this.pick.accept(Optional.of(DyeColor.values()[i]));
			return true;
		}
		return false;
	}
}
