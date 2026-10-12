package scribe.client.widget;

import scribe.Scribe;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public class SpriteButton extends AbstractButton {
	private final Identifier normal;
	private final Identifier highlighted;
	private final Identifier disabled;
	private Consumer<InputWithModifiers> action;
	private boolean selected;
	private int tint = -1;
	private boolean pageSound;
	private boolean keepsFocus = true;

	public SpriteButton(int x, int y, int width, int height, String sprite, Component tooltip, Consumer<InputWithModifiers> action) {
		super(x, y, width, height, tooltip);
		this.normal = Scribe.id(sprite);
		this.highlighted = Scribe.id(sprite + "_highlighted");
		this.disabled = Scribe.id(sprite + "_disabled");
		this.action = action;
		this.setTooltip(Tooltip.create(tooltip));
	}

	public SpriteButton(int x, int y, int width, int height, String sprite, Component tooltip, Runnable action) {
		this(x, y, width, height, sprite, tooltip, input -> action.run());
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	public SpriteButton tint(int argb) {
		this.tint = argb;
		return this;
	}

	public SpriteButton pageSound() {
		this.pageSound = true;
		return this;
	}

	public SpriteButton noFocus() {
		this.keepsFocus = false;
		return this;
	}

	public void setTooltipText(Component tooltip) {
		this.setMessage(tooltip);
		this.setTooltip(Tooltip.create(tooltip));
	}

	@Override
	public void onPress(InputWithModifiers input) {
		this.action.accept(input);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Identifier sprite = !this.active ? this.disabled : this.isHoveredOrFocused() || this.selected ? this.highlighted : this.normal;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.tint);
	}

	@Override
	public boolean shouldTakeFocusAfterInteraction() {
		return !this.pageSound && this.keepsFocus;
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
		if (this.pageSound) {
			soundManager.play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
		} else {
			soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.3F, 0.35F));
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
