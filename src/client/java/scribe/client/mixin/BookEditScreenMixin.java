package scribe.client.mixin;

import scribe.colors.BookColors;
import scribe.client.colors.ColoredBookScreen;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin {
	@Shadow
	@Final
	private ItemStack book;

	@Shadow
	@Final
	private BookSignScreen signScreen;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void scribe$init(Player owner, ItemStack book, InteractionHand hand, WritableBookContent content, CallbackInfo ci) {
		((ColoredBookScreen) this.signScreen).scribe$setColor(BookColors.of(book));
	}

	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void scribe$coloredCover(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
		int width, int height, int textureWidth, int textureHeight, Operation<Void> original) {
		if (ColoredBookScreen.dyed()) {
			ColoredBookScreen.drawCover(graphics, pipeline, x, y, u, v, width, height, textureWidth, textureHeight, BookColors.of(this.book));
		} else {
			original.call(graphics, pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
		}
	}
}
