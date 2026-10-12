package scribe.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;
import scribe.client.screen.BookEditorScreen;
import scribe.ClosedLecterns;
import scribe.EnchantedBooks;
import scribe.binding.CoverEdit;
import scribe.client.command.PoseCommand;
import scribe.client.screen.LecternBookScreen;
import scribe.colors.BookDyeing;
import scribe.config.ScribeConfig;
import scribe.network.Payloads;
import scribe.server.ServerBooks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.jspecify.annotations.Nullable;

public final class BookNet {
	private static Payloads.ServerFeatures features = Payloads.ServerFeatures.NONE;
	private static Payloads.ServerFeatures hosted = Payloads.ServerFeatures.NONE;
	private static int lecternMenu = -1;
	private static boolean lecternClosed;
	private static int writerMenu = -1;
	private static String writer = "";
	private static long penSent;

	private BookNet() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(Payloads.ServerFeatures.TYPE, (payload, context) -> features = payload);
		EnchantedBooks.setClientAllows(() -> has(Payloads.ServerFeatures.ENCHANTED_LECTERNS));
		BookDyeing.setClientAllows(() -> has(Payloads.ServerFeatures.BOOK_DYEING), () -> has(Payloads.ServerFeatures.TABLE_DYEING));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.ReadingState.TYPE, (payload, context) ->
			ReadingPose.set(payload.entityId(), payload.reading(), payload.offhand()));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.LecternState.TYPE, (payload, context) -> lecternState(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.LecternWriter.TYPE, (payload, context) -> lecternWriter(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.PageTurned.TYPE, (payload, context) ->
			ReadingPose.turn(payload.entityId(), payload.leaves()));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.LecternLeavesTurned.TYPE, (payload, context) ->
			LecternMotions.leavesTurned(payload.pos(), payload.leaves()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			features = Payloads.ServerFeatures.NONE;
			lecternMenu = -1;
			writerMenu = -1;
			ReadingPose.clear();
			PoseCommand.leftWorld();
		});
	}

	private static void lecternWriter(Payloads.LecternWriter payload) {
		writerMenu = payload.containerId();
		writer = payload.writer();
		Screen screen = Minecraft.getInstance().gui.screen();
		if (!payload.yours() && screen instanceof BookEditorScreen editor) {
			editor.lecternPenTaken(payload.containerId(), payload.writer());
		} else if (screen instanceof LecternBookScreen lectern && lectern.getMenu().containerId == payload.containerId()) {
			lectern.lecternWriterChanged();
		}
	}

	public static @Nullable String lecternWriter(int containerId) {
		return containerId >= 0 && containerId == writerMenu ? writer : null;
	}

	public static void lecternTyped(int containerId) {
		long now = Util.getMillis();
		if (now - penSent >= 20_000 && canSend(Payloads.LecternPen.TYPE)) {
			penSent = now;
			ClientPlayNetworking.send(new Payloads.LecternPen(containerId, true));
		}
	}

	private static void lecternState(Payloads.LecternState state) {
		lecternMenu = state.shared() ? state.containerId() : -1;
		lecternClosed = state.closed();
		if (Minecraft.getInstance().gui.screen() instanceof LecternBookScreen screen && screen.getMenu().containerId == state.containerId()) {
			screen.lecternStateChanged();
		}
	}

	public static @Nullable Boolean lecternClosed(int containerId) {
		return containerId >= 0 && containerId == lecternMenu ? lecternClosed : null;
	}

	public static void openLectern(int containerId) {
		if (Boolean.TRUE.equals(lecternClosed(containerId))) {
			sendLecternButton(containerId, ClosedLecterns.BUTTON_OPEN);
		}
	}

	public static void lecternPageTurned(int containerId) {
		if (lecternClosed(containerId) != null) {
			lecternClosed = false;
		}
	}

	public static void closeLectern(int containerId) {
		if (lecternClosed(containerId) != null) {
			sendLecternButton(containerId, ClosedLecterns.BUTTON_CLOSE);
		}
	}

	private static void sendLecternButton(int containerId, int button) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(containerId, button);
			lecternClosed = button == ClosedLecterns.BUTTON_CLOSE;
		}
	}

	private static boolean canSend(net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> type) {
		try {
			return ClientPlayNetworking.canSend(type);
		} catch (IllegalStateException notConnected) {
			return false;
		}
	}

	public static boolean has(int feature) {
		return current().has(feature);
	}

	public static int serverFlags() {
		return current().flags();
	}

	private static Payloads.ServerFeatures current() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft != null && minecraft.hasSingleplayerServer()) {
			int flags = ServerBooks.features();
			if (hosted.flags() != flags) {
				hosted = new Payloads.ServerFeatures(flags);
			}
			return hosted;
		}
		return features;
	}

	public static boolean scribeOnServer() {
		return canSend(Payloads.SignDetailsPayload.TYPE);
	}

	public static boolean penNames() {
		return has(Payloads.ServerFeatures.PEN_NAMES) && canSend(Payloads.SignDetailsPayload.TYPE);
	}

	public static boolean subtitles() {
		return has(Payloads.ServerFeatures.SUBTITLES) && canSend(Payloads.SignDetailsPayload.TYPE);
	}

	public static boolean lecternEditing() {
		return has(Payloads.ServerFeatures.LECTERN_EDITING) && canSend(Payloads.LecternEdit.TYPE);
	}

	public static boolean closedLecterns() {
		return has(Payloads.ServerFeatures.CLOSED_LECTERNS);
	}

	public static boolean signFormatting() {
		if (Minecraft.getInstance().hasSingleplayerServer()) {
			return ScribeConfig.get().signFormattingOn();
		}
		return has(Payloads.ServerFeatures.SIGN_FORMATTING);
	}

	public static Component signFormattingOff() {
		return Component.translatable("scribe.sign_tools.needs_scribe");
	}

	public static boolean coverDesigns() {
		return has(Payloads.ServerFeatures.BOOKBINDING) && canSend(Payloads.SignDetailsPayload.TYPE);
	}

	public static boolean coverChanges() {
		return canSend(Payloads.CoverEditPayload.TYPE);
	}

	public static void sendCoverEdit(int slot, CoverEdit edit) {
		if (canSend(Payloads.CoverEditPayload.TYPE)) {
			ClientPlayNetworking.send(new Payloads.CoverEditPayload(slot, edit));
		}
	}

	public static boolean coverDesigner() {
		return canSend(Payloads.CoverDesignerSave.TYPE);
	}

	public static void sendCoverDesigner(int slot, String title, Payloads.SignDetails details, boolean override) {
		if (canSend(Payloads.CoverDesignerSave.TYPE)) {
			ClientPlayNetworking.send(new Payloads.CoverDesignerSave(slot, title, details, override));
		}
	}

	public static boolean savesDesigns() {
		return coverDesigns() && canSend(Payloads.SaveDesign.TYPE);
	}

	public static boolean sendSaveDesign(int slot, int containerId, Payloads.SignDetails details) {
		if (!savesDesigns() || !details.hasDesign()) {
			return false;
		}
		ClientPlayNetworking.send(new Payloads.SaveDesign(slot, containerId, details));
		return true;
	}

	public static boolean savesDetails() {
		return canSend(Payloads.SaveDetails.TYPE);
	}

	public static boolean sendSaveDetails(int slot, int containerId, scribe.SavedDetails details) {
		if (!savesDetails()) {
			return false;
		}
		ClientPlayNetworking.send(new Payloads.SaveDetails(slot, containerId, details.title(), details.subtitle(), details.penName()));
		return true;
	}

	public static void sendBookmark(int slot, int page) {
		if (ScribeConfig.get().sendsLastPage() && canSend(Payloads.SetBookmark.TYPE)) {
			ClientPlayNetworking.send(new Payloads.SetBookmark(slot, Math.max(0, page)));
		}
	}

	public static void sendSignDetails(int slot, Payloads.SignDetails details) {
		if ((details.hasPenName() || details.hasSubtitle() || details.hasDesign()) && canSend(Payloads.SignDetailsPayload.TYPE)) {
			ClientPlayNetworking.send(new Payloads.SignDetailsPayload(slot, details));
		}
	}

	public static void sendLecternEdit(Payloads.LecternEdit edit) {
		if (canSend(Payloads.LecternEdit.TYPE)) {
			ClientPlayNetworking.send(edit);
		}
	}

	public static void sendReading(boolean reading, InteractionHand hand) {
		PoseCommand.screenTookOver();
		tellReading(reading, hand, false);
	}

	public static void tellReading(boolean reading, InteractionHand hand, boolean posed) {
		ReadingPose.setLocal(reading, hand == InteractionHand.OFF_HAND, posed);
		if (has(Payloads.ServerFeatures.READING_POSE) && canSend(Payloads.Reading.TYPE)) {
			ClientPlayNetworking.send(new Payloads.Reading(reading, hand == InteractionHand.OFF_HAND));
		}
	}

	public static void lecternLeaves(int containerId, int leaves) {
		if (leaves != 0 && canSend(Payloads.LecternLeaves.TYPE)) {
			ClientPlayNetworking.send(new Payloads.LecternLeaves(containerId, leaves));
		}
	}

	public static void pageTurned(int leaves) {
		if (leaves == 0) {
			return;
		}
		ReadingPose.turnLocal(leaves);
		if (has(Payloads.ServerFeatures.READING_POSE) && canSend(Payloads.PageTurn.TYPE)) {
			ClientPlayNetworking.send(new Payloads.PageTurn(leaves));
		}
	}
}
