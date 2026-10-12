package scribe.client.screen;

import scribe.BookLimits;
import scribe.SavedDetails;
import scribe.client.BookNet;
import scribe.client.memory.PenNames;
import scribe.network.Payloads;
import scribe.server.ServerBooks;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import org.jspecify.annotations.Nullable;

public interface EditTarget {
	ItemStack book();

	void save(List<String> pages);

	void sign(List<String> pages, String title, Payloads.SignDetails details);

	default boolean saveDesign(Payloads.SignDetails details) {
		return false;
	}

	default boolean saveDetails(SavedDetails details) {
		return false;
	}

	static void keepLocally(ItemStack book, SavedDetails details) {
		SavedDetails.write(book, details.taken(BookNet.subtitles(), BookNet.penNames()));
	}

	int slot();

	@Nullable InteractionHand hand();

	String playerName();

	default boolean onLectern() {
		return false;
	}

	default boolean canTakeBook() {
		return false;
	}

	default void takeBook() {
	}

	default void closed() {
	}

	record Hand(Player owner, ItemStack book, InteractionHand hand) implements EditTarget {
		@Override
		public int slot() {
			return this.hand == InteractionHand.MAIN_HAND ? this.owner.getInventory().getSelectedSlot() : BookLimits.OFFHAND_SLOT;
		}

		@Override
		public String playerName() {
			return this.owner.getGameProfile().name();
		}

		@Override
		public void save(List<String> pages) {
			ClientPacketListener connection = Minecraft.getInstance().getConnection();
			if (connection == null) {
				return;
			}
			this.book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(pages.stream().map(Filterable::passThrough).toList()));
			connection.send(new ServerboundEditBookPacket(this.slot(), pages, Optional.empty()));
		}

		@Override
		public boolean saveDesign(Payloads.SignDetails details) {
			if (!BookNet.sendSaveDesign(this.slot(), -1, details)) {
				return false;
			}
			ServerBooks.keepDesign(this.book, details);
			return true;
		}

		@Override
		public boolean saveDetails(SavedDetails details) {
			if (!BookNet.sendSaveDetails(this.slot(), -1, details)) {
				return false;
			}
			EditTarget.keepLocally(this.book, details);
			return true;
		}

		@Override
		public void sign(List<String> pages, String title, Payloads.SignDetails details) {
			ClientPacketListener connection = Minecraft.getInstance().getConnection();
			if (connection == null) {
				return;
			}
			BookNet.sendSignDetails(this.slot(), details);
			connection.send(new ServerboundEditBookPacket(this.slot(), pages, Optional.of(title)));
			if (details.hasPenName() && BookNet.penNames()) {
				PenNames.get().remember(this.owner.getUUID(), details.penName());
			}
		}
	}

	record Lectern(LecternMenu menu) implements EditTarget {
		@Override
		public ItemStack book() {
			return this.menu.getBook();
		}

		@Override
		public int slot() {
			return -1;
		}

		@Override
		public @Nullable InteractionHand hand() {
			return null;
		}

		@Override
		public String playerName() {
			Player player = Minecraft.getInstance().player;
			return player == null ? "" : player.getGameProfile().name();
		}

		@Override
		public boolean onLectern() {
			return true;
		}

		@Override
		public void save(List<String> pages) {
			BookNet.sendLecternEdit(new Payloads.LecternEdit(this.menu.containerId, pages, Optional.empty(), Payloads.SignDetails.NONE));
		}

		@Override
		public boolean saveDesign(Payloads.SignDetails details) {
			if (!BookNet.sendSaveDesign(-1, this.menu.containerId, details)) {
				return false;
			}
			ServerBooks.keepDesign(this.menu.getBook(), details);
			return true;
		}

		@Override
		public boolean saveDetails(SavedDetails details) {
			if (!BookNet.sendSaveDetails(-1, this.menu.containerId, details)) {
				return false;
			}
			EditTarget.keepLocally(this.menu.getBook(), details);
			return true;
		}

		@Override
		public void sign(List<String> pages, String title, Payloads.SignDetails details) {
			BookNet.sendLecternEdit(new Payloads.LecternEdit(this.menu.containerId, pages, Optional.of(title), details));
			Player player = Minecraft.getInstance().player;
			if (player != null && details.hasPenName() && BookNet.penNames()) {
				PenNames.get().remember(player.getUUID(), details.penName());
			}
		}

		@Override
		public boolean canTakeBook() {
			Player player = Minecraft.getInstance().player;
			return player != null && player.mayBuild();
		}

		@Override
		public void takeBook() {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.gameMode != null) {
				minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, LecternMenu.BUTTON_TAKE_BOOK);
			}
		}

		@Override
		public void closed() {
			Minecraft minecraft = Minecraft.getInstance();
			LocalPlayer player = minecraft.player;
			if (player == null || player.containerMenu != this.menu) {
				return;
			}
			ClientPacketListener connection = minecraft.getConnection();
			if (connection != null) {
				connection.send(new ServerboundContainerClosePacket(this.menu.containerId));
			}
			player.containerMenu = player.inventoryMenu;
		}
	}
}
