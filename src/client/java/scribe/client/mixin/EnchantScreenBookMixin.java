package scribe.client.mixin;

import scribe.client.colors.DyedBookPicture;
import scribe.client.colors.GuiBookTint;
import scribe.client.colors.JebBook;
import scribe.client.colors.TableLeather;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EnchantmentScreen.class)
public abstract class EnchantScreenBookMixin extends AbstractContainerScreen<EnchantmentMenu> {
	@Unique
	private @Nullable BlockPos scribe$tableAt;
	@Unique
	private boolean scribe$tableSought;

	protected EnchantScreenBookMixin(EnchantmentMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@WrapOperation(method = "extractBook", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;book(Lnet/minecraft/client/model/object/book/BookModel;Lnet/minecraft/resources/Identifier;FFFIIII)V"))
	private void scribe$dyedBook(GuiGraphicsExtractor graphics, BookModel model, Identifier texture, float scale, float open, float flip,
		int x0, int y0, int x1, int y1, Operation<Void> original) {
		float partialTick = this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float clock = this.minecraft.level == null ? 0.0F : this.minecraft.level.getGameTime() + partialTick;
		int leather = TableLeather.onScreen(this.title, this.scribe$table(), partialTick, clock);
		if (leather == GuiBookTint.NO_TINT) {
			original.call(graphics, model, texture, scale, open, flip, x0, y0, x1, y1);
			return;
		}
		ScreenRectangle scissor = graphics.textRenderer().defaultParameters().scissor();
		((GuiGraphicsExtractorAccess) graphics).scribe$guiRenderState()
			.addPicturesInPictureState(DyedBookPicture.of(model, texture, scale, open, flip, x0, y0, x1, y1, scissor, leather));
	}

	@Unique
	private @Nullable EnchantingTableBlockEntity scribe$table() {
		Level level = this.minecraft.level;
		if (level == null) {
			return null;
		}
		if (!this.scribe$tableSought) {
			this.scribe$tableSought = true;
			this.scribe$tableAt = JebBook.openedTable(level::getBlockEntity, this.minecraft.hitResult,
				this.minecraft.player == null ? null : this.minecraft.player.getEyePosition());
		}
		BlockPos at = this.scribe$tableAt;
		return at != null && level.getBlockEntity(at) instanceof EnchantingTableBlockEntity table ? table : null;
	}
}
