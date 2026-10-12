package scribe.client.mixin;

import scribe.client.colors.WrittenBookGlint;
import com.mojang.blaze3d.platform.Transparency;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BakedQuad.MaterialInfo.class)
public abstract class BakedQuadMaterialInfoMixin {
	@Inject(method = "of", at = @At("RETURN"), cancellable = true)
	private static void scribe$writtenBookGlint(Material.Baked material, Transparency transparency, int tintIndex,
		@Nullable Direction shadeDirectionOverride, int lightEmission, CallbackInfoReturnable<BakedQuad.MaterialInfo> cir) {
		cir.setReturnValue(WrittenBookGlint.material(cir.getReturnValue()));
	}
}
