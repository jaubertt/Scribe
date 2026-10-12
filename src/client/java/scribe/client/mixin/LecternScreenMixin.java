package scribe.client.mixin;

import scribe.EnchantedBooks;
import scribe.colors.BookColors;
import scribe.client.BookNet;
import scribe.client.colors.CoverTints;
import scribe.client.text.EnchantedPages;
import scribe.client.text.SharedPages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LecternScreen.class)
public abstract class LecternScreenMixin {
	@Unique
	private @Nullable SharedPages scribe$pages;
	@Unique
	private final SharedPages.Turns scribe$turns = new SharedPages.Turns();

	@Inject(method = "bookChanged", at = @At("RETURN"))
	private void scribe$bookChanged(CallbackInfo ci) {
		LecternScreen self = (LecternScreen) (Object) this;
		ItemStack book = self.getMenu().getBook();
		CoverTints.remember(self, BookColors.of(book));
		if (EnchantedBooks.is(book)) {
			EnchantedPages.Book pages = EnchantedPages.of(book, Minecraft.getInstance().font);
			self.setBookAccess(new BookViewScreen.BookAccess(pages.pages()));
			this.scribe$pages = pages.shared();
			this.scribe$turns.clear();
			self.setPage(pages.shared().firstPageOf(self.getMenu().getPage()));
		} else {
			this.scribe$pages = null;
		}
		if (!book.isEmpty()) {
			BookNet.openLectern(self.getMenu().containerId);
		}
	}

	@Inject(method = "pageChanged", at = @At("HEAD"), cancellable = true)
	private void scribe$pageChanged(CallbackInfo ci) {
		if (this.scribe$pages != null) {
			LecternScreen self = (LecternScreen) (Object) this;
			self.setPage(this.scribe$turns.heard(this.scribe$pages, this.scribe$page(), self.getMenu().getPage()));
			ci.cancel();
		}
	}

	@Inject(method = "pageForward", at = @At("HEAD"), cancellable = true)
	private void scribe$pageForward(CallbackInfo ci) {
		if (this.scribe$pages != null) {
			this.scribe$turnTo(this.scribe$page() + 1);
			ci.cancel();
		}
	}

	@Inject(method = "pageBack", at = @At("HEAD"), cancellable = true)
	private void scribe$pageBack(CallbackInfo ci) {
		if (this.scribe$pages != null) {
			this.scribe$turnTo(this.scribe$page() - 1);
			ci.cancel();
		}
	}

	@Inject(method = "forcePage", at = @At("HEAD"), cancellable = true)
	private void scribe$forcePage(int page, CallbackInfoReturnable<Boolean> cir) {
		if (this.scribe$pages != null) {
			cir.setReturnValue(this.scribe$turnTo(page));
		}
	}

	@Unique
	private int scribe$page() {
		return ((BookViewScreenAccessor) this).scribe$currentPage();
	}

	@Unique
	private boolean scribe$turnTo(int page) {
		SharedPages pages = this.scribe$pages;
		LecternScreen self = (LecternScreen) (Object) this;
		Minecraft minecraft = Minecraft.getInstance();
		if (pages == null || pages.pages() == 0) {
			return false;
		}
		int from = this.scribe$page();
		int target = Math.clamp(page, 0, pages.pages() - 1);
		if (target == from) {
			return false;
		}
		int request = this.scribe$turns.turn(pages, from, target, self.getMenu().getPage());
		if (request >= 0) {
			if (minecraft.gameMode != null) {
				minecraft.gameMode.handleInventoryButtonClick(self.getMenu().containerId, LecternMenu.BUTTON_PAGE_JUMP_RANGE_START + request);
			}
		} else {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
		}
		self.setPage(target);
		return true;
	}
}
