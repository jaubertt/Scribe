package scribe.client.widget;

import scribe.Scribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class CornerButton extends AbstractButton {
	private final @Nullable Identifier sprite;
	private final Runnable action;

	public CornerButton(int x, int y, @Nullable String sprite, Component tooltip, Runnable action) {
		super(x - 1, y - 1, 15, 14, tooltip);
		this.sprite = sprite == null ? null : Scribe.id(sprite);
		this.action = action;
		this.setTooltip(Tooltip.create(tooltip));
	}

	@Override
	public void onPress(InputWithModifiers input) {
		this.action.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean hot = this.isHoveredOrFocused();
		int x = this.getX();
		int y = this.getY();
		if (hot) {
			graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0x60FFFFFF);
		}
		int color = hot ? 0xFFFFFFFF : 0xFFE0E0E0;
		if (this.sprite == null) {
			graphics.text(Minecraft.getInstance().font, "§l?", x + 4, y + 2, color, true);
		} else {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.sprite, x + 1, y + 1, 13, 12, 0xFF3F3F3F);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.sprite, x, y, 13, 12, color);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
