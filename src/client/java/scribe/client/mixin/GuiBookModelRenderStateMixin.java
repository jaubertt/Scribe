package scribe.client.mixin;

import scribe.client.colors.ColoredRenderState;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.state.gui.pip.GuiBookModelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiBookModelRenderState.class)
public abstract class GuiBookModelRenderStateMixin implements ColoredRenderState {
	@Unique
	private int scribe$color = -1;

	@Override
	public void scribe$setColor(int color) {
		this.scribe$color = color;
	}

	@Override
	public int scribe$getColor() {
		return this.scribe$color;
	}

	@ModifyReturnValue(method = "equals", at = @At("RETURN"))
	private boolean scribe$sameColor(boolean original, @Local(argsOnly = true) Object other) {
		return original && other instanceof ColoredRenderState colored && colored.scribe$getColor() == this.scribe$color;
	}
}
