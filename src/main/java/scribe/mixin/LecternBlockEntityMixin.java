package scribe.mixin;

import scribe.Bookmarks;
import scribe.ClosedLecterns;
import scribe.EnchantedBooks;
import scribe.LibrarianLecterns;
import scribe.access.BookPreviews;
import scribe.access.LecternAccess;
import scribe.access.LecternMenuAccess;
import scribe.access.LecternWatch;
import scribe.colors.BookColors;
import scribe.config.ScribeConfig;
import scribe.server.BookPreview;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LecternBlockEntity.class)
public abstract class LecternBlockEntityMixin extends BlockEntity implements BookPreviews, LecternAccess {
	@Unique
	private static final String COVER_DYE_KEY = "scribe:cover_dye";
	@Unique
	private static final String BOOK_KEY = "scribe:book";
	@Unique
	private static final String PAGE_KEY = "scribe:page";
	@Unique
	private static final String PAGES_KEY = "scribe:pages";

	@Shadow
	private ItemStack book;

	@Shadow
	private int page;

	@Shadow
	private int pageCount;

	@Unique
	private int scribe$heardDye = BookColors.DEFAULT;
	@Unique
	private ItemStack scribe$preview = ItemStack.EMPTY;
	@Unique
	private int scribe$sharedPage = -1;
	@Unique
	private int scribe$sharedPages;
	@Unique
	private boolean scribe$closed;
	@Unique
	private LibrarianLecterns.@Nullable Hold scribe$librarian;

	protected LecternBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		Packet<ClientGamePacketListener> packet = super.getUpdatePacket();
		return packet != null ? packet : ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		ClosedLecterns.share(tag, this.scribe$closed, !this.book.isEmpty());
		tag.putInt(COVER_DYE_KEY, BookColors.of(this.book));
		if (ScribeConfig.get().bookInfoOn() && !this.book.isEmpty()) {
			tag.store(BOOK_KEY, ItemStack.OPTIONAL_CODEC, registries.createSerializationContext(NbtOps.INSTANCE), BookPreview.of(this.book));
			tag.putInt(PAGE_KEY, this.page);
			tag.putInt(PAGES_KEY, this.pageCount);
		}
		return tag;
	}

	@Inject(method = "saveAdditional", at = @At("RETURN"))
	private void scribe$save(ValueOutput output, CallbackInfo ci) {
		ClosedLecterns.save(output, LibrarianLecterns.savedClosed(this.scribe$closed, this.scribe$librarian != null), !this.book.isEmpty());
	}

	@Inject(method = "loadAdditional", at = @At("RETURN"))
	private void scribe$load(ValueInput input, CallbackInfo ci) {
		boolean wasClosed = this.scribe$closed;
		int oldPage = this.scribe$sharedPage;
		this.scribe$closed = ClosedLecterns.load(input);
		this.scribe$librarian = null;
		if (this.level != null && this.level.isClientSide()) {
			this.scribe$heardDye = input.getIntOr(COVER_DYE_KEY, BookColors.DEFAULT);
			this.scribe$preview = input.read(BOOK_KEY, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
			this.scribe$sharedPage = input.getIntOr(PAGE_KEY, -1);
			this.scribe$sharedPages = input.getIntOr(PAGES_KEY, 0);
			LecternWatch.arrived((LecternBlockEntity) (Object) this, wasClosed, this.scribe$closed, oldPage, this.scribe$sharedPage);
		}
	}

	@Inject(method = "setBook(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"))
	private void scribe$newBookLiesClosed(ItemStack newBook, Player player, CallbackInfo ci) {
		this.scribe$librarian = null;
		if (newBook.isEmpty()) {
			this.scribe$closed = false;
		} else if (this.book.isEmpty()) {
			this.scribe$closed = ClosedLecterns.startsClosed();
		}
	}

	@Inject(method = "setBook(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)V", at = @At("TAIL"))
	private void scribe$setBook(ItemStack newBook, Player player, CallbackInfo ci) {
		if (!ScribeConfig.get().lecternPagesOn() || !Bookmarks.isBook(this.book) || this.pageCount <= 0) {
			return;
		}
		int saved = Bookmarks.get(this.book);
		if (saved > 0) {
			this.page = Math.clamp(saved, 0, this.pageCount - 1);
		}
	}

	@Inject(method = "onBookItemRemove", at = @At("HEAD"))
	private void scribe$bookRemoved(CallbackInfo ci) {
		this.scribe$closed = false;
		this.scribe$librarian = null;
	}

	@Inject(method = "setPage", at = @At("TAIL"))
	private void scribe$pageTurned(int requestedPage, CallbackInfo ci) {
		if (ScribeConfig.get().lecternPagesOn() && Bookmarks.isBook(this.book)) {
			Bookmarks.set(this.book, this.page);
		}
		this.scribe$tellPlayers();
	}

	@ModifyReturnValue(method = "hasBook", at = @At("RETURN"))
	private boolean scribe$enchantedBookCounts(boolean hasBook) {
		return hasBook || EnchantedBooks.is(this.book);
	}

	@ModifyReturnValue(method = "getPageCount", at = @At("RETURN"))
	private static int scribe$enchantedBookPages(int pages, @Local(argsOnly = true) ItemStack book) {
		return EnchantedBooks.is(book) ? EnchantedBooks.pageCount(book) : pages;
	}

	@Inject(method = "createMenu", at = @At("RETURN"))
	private void scribe$rememberLectern(int containerId, net.minecraft.world.entity.player.Inventory inventory, Player player, CallbackInfoReturnable<AbstractContainerMenu> cir) {
		if (cir.getReturnValue() instanceof LecternMenuAccess menu) {
			LecternBlockEntity self = (LecternBlockEntity) (Object) this;
			menu.scribe$setLectern(self);
			if (player instanceof ServerPlayer server && this.level != null && !this.level.isClientSide()) {
				ClosedLecterns.menuOpened(self, server, containerId, ClosedLecterns.hasScribe(server));
				scribe.LecternLocks.menuOpened(self, server, containerId);
			}
		}
	}

	@Override
	public void scribe$bookEdited() {
		this.pageCount = BookPreview.pageCount(this.book);
		this.page = this.pageCount <= 0 ? 0 : Math.clamp(this.page, 0, this.pageCount - 1);
		this.setChanged();
		this.scribe$tellPlayers();
	}

	@Override
	public int scribe$coverDye() {
		return this.level != null && this.level.isClientSide() ? this.scribe$heardDye : BookColors.of(this.book);
	}

	@Override
	public boolean scribe$isClosed() {
		return this.scribe$closed;
	}

	@Override
	public void scribe$setClosed(boolean closed) {
		this.scribe$closed = closed;
		this.scribe$librarian = null;
		this.setChanged();
		this.scribe$tellPlayers();
	}

	@Override
	public LibrarianLecterns.@Nullable Hold scribe$librarian() {
		return this.scribe$librarian;
	}

	@Override
	public void scribe$setLibrarian(LibrarianLecterns.@Nullable Hold hold) {
		this.scribe$librarian = hold;
	}

	@Unique
	private void scribe$tellPlayers() {
		if (this.level != null && !this.level.isClientSide()) {
			BlockState state = this.getBlockState();
			this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public ItemStack scribe$preview(int slot) {
		if (this.level != null && this.level.isClientSide()) {
			return this.scribe$preview;
		}
		return BookPreview.of(this.book);
	}

	@Override
	public int scribe$page() {
		return this.level != null && this.level.isClientSide() ? this.scribe$sharedPage : this.page;
	}

	@Override
	public int scribe$pageCount() {
		return this.level != null && this.level.isClientSide() ? this.scribe$sharedPages : this.pageCount;
	}
}
