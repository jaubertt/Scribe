package scribe.client.mixin;

import scribe.colors.ColoredBlockEntity;
import scribe.config.ScribeConfig;
import scribe.client.colors.GuiBookTint;
import scribe.client.colors.JebBook;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
public abstract class EnchantmentScreenMixin extends AbstractContainerScreen<EnchantmentMenu> {
	@Unique
	private @Nullable BlockPos scribe$table;
	@Unique
	private boolean scribe$lookedUp;

	protected EnchantmentScreenMixin(EnchantmentMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@WrapOperation(method = "extractBook", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;book(Lnet/minecraft/client/model/object/book/BookModel;Lnet/minecraft/resources/Identifier;FFFIIII)V"))
	private void scribe$tintBook(GuiGraphicsExtractor graphics, BookModel model, Identifier texture, float scale, float open, float flip,
		int x0, int y0, int x1, int y1, Operation<Void> original) {
		float partialTick = this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float clock = this.minecraft.level == null ? 0.0F : this.minecraft.level.getGameTime() + partialTick;
		int tableColor = ((ColoredBlockEntity) (Object) this.menu).scribe$getColor(0);
		GuiBookTint.pending = ScribeConfig.get().tableLookShown() ? JebBook.screenColor(this.title, this.scribe$table(), partialTick, clock, tableColor) : -1;
		try {
			original.call(graphics, model, texture, scale, open, flip, x0, y0, x1, y1);
		} finally {
			GuiBookTint.pending = -1;
		}
	}

	@Unique
	private @Nullable EnchantingTableBlockEntity scribe$table() {
		Level level = this.minecraft.level;
		if (level == null) {
			return null;
		}
		if (!this.scribe$lookedUp) {
			this.scribe$lookedUp = true;
			this.scribe$table = JebBook.openedTable(level::getBlockEntity, this.minecraft.hitResult,
				this.minecraft.player == null ? null : this.minecraft.player.getEyePosition());
		}
		return this.scribe$table != null && level.getBlockEntity(this.scribe$table) instanceof EnchantingTableBlockEntity table ? table : null;
	}
}
