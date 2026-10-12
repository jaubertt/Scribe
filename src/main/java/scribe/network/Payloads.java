package scribe.network;

import scribe.BookLimits;
import scribe.Scribe;
import scribe.binding.CoverEdit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;

public final class Payloads {
	private Payloads() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(SetBookmark.TYPE, SetBookmark.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SignDetailsPayload.TYPE, SignDetailsPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LecternEdit.TYPE, LecternEdit.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Reading.TYPE, Reading.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CoverEditPayload.TYPE, CoverEditPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SaveDesign.TYPE, SaveDesign.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SaveDetails.TYPE, SaveDetails.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CoverDesignerSave.TYPE, CoverDesignerSave.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ReadingState.TYPE, ReadingState.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ServerFeatures.TYPE, ServerFeatures.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LecternState.TYPE, LecternState.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LecternWriter.TYPE, LecternWriter.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LecternPen.TYPE, LecternPen.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PageTurn.TYPE, PageTurn.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PageTurned.TYPE, PageTurned.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LecternLeaves.TYPE, LecternLeaves.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LecternLeavesTurned.TYPE, LecternLeavesTurned.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LibrarianReadingPayload.TYPE, LibrarianReadingPayload.CODEC);
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
		return new CustomPacketPayload.Type<>(Scribe.id(name));
	}

	public static boolean hasScribe(ServerPlayer player) {
		try {
			return ServerPlayNetworking.canSend(player, ServerFeatures.TYPE);
		} catch (RuntimeException noConnection) {
			return false;
		}
	}

	public record SignDetails(String penName, List<String> subtitle, String corner, String decoration, String decorationLook,
		String spine, String titleColor, String author, String metalwork) {
		public static final int PEN_NAME_MAX = 32;
		public static final int SUBTITLE_MAX = 50;
		private static final int SUBTITLE_LINES_MAX = 8;
		public static final int DESIGN_ID_MAX = 32;
		public static final SignDetails NONE = new SignDetails("", List.of(), "", "", "", "", "", "", "");

		public static final StreamCodec<RegistryFriendlyByteBuf, SignDetails> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), SignDetails::penName,
			ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(SUBTITLE_LINES_MAX)), SignDetails::subtitle,
			ByteBufCodecs.stringUtf8(DESIGN_ID_MAX), SignDetails::corner,
			ByteBufCodecs.stringUtf8(DESIGN_ID_MAX), SignDetails::decoration,
			ByteBufCodecs.stringUtf8(DESIGN_ID_MAX), SignDetails::decorationLook,
			ByteBufCodecs.stringUtf8(DESIGN_ID_MAX), SignDetails::spine,
			ByteBufCodecs.stringUtf8(DESIGN_ID_MAX), SignDetails::titleColor,
			ByteBufCodecs.stringUtf8(DESIGN_ID_MAX), SignDetails::author,
			ByteBufCodecs.stringUtf8(DESIGN_ID_MAX), SignDetails::metalwork,
			SignDetails::new
		);

		public SignDetails {
			penName = cleanPenName(penName);
			subtitle = cleanSubtitle(subtitle);
			corner = cleanId(corner);
			decoration = cleanId(decoration);
			decorationLook = cleanId(decorationLook);
			spine = cleanId(spine);
			titleColor = cleanId(titleColor);
			author = cleanId(author);
			metalwork = cleanId(metalwork);
		}

		public SignDetails(String penName, List<String> subtitle, String corner, String decoration, String decorationLook, String spine,
			String titleColor, String author) {
			this(penName, subtitle, corner, decoration, decorationLook, spine, titleColor, author, "");
		}

		public SignDetails(String penName, List<String> subtitle, String corner, String decoration, String decorationLook, String spine,
			String titleColor) {
			this(penName, subtitle, corner, decoration, decorationLook, spine, titleColor, "");
		}

		public SignDetails(String penName, List<String> subtitle) {
			this(penName, subtitle, "", "", "", "", "");
		}

		public SignDetails(String penName, List<String> subtitle, String corner, String decoration) {
			this(penName, subtitle, corner, decoration, "", "", "");
		}

		public boolean hasPenName() {
			return !this.penName.isEmpty();
		}

		public boolean hasSubtitle() {
			return !this.subtitle.isEmpty();
		}

		public boolean hasCorner() {
			return !this.corner.isEmpty();
		}

		public boolean hasDesign() {
			return !this.corner.isEmpty() || !this.decoration.isEmpty() || !this.decorationLook.isEmpty()
				|| !this.spine.isEmpty() || !this.titleColor.isEmpty() || !this.author.isEmpty() || !this.metalwork.isEmpty();
		}

		public SignDetails withText(String penName, List<String> subtitle) {
			return new SignDetails(penName, subtitle, this.corner, this.decoration, this.decorationLook, this.spine, this.titleColor, this.author,
				this.metalwork);
		}

		public static String cleanId(String raw) {
			if (raw == null || raw.length() > DESIGN_ID_MAX) {
				return "";
			}
			for (int i = 0; i < raw.length(); i++) {
				char c = raw.charAt(i);
				if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) {
					return "";
				}
			}
			return raw;
		}

		public static String cleanPenName(String raw) {
			return limit(clean(raw), PEN_NAME_MAX);
		}

		public static String cleanTitle(String raw) {
			return limit(clean(raw), BookLimits.TITLE_CHARS);
		}

		public static String cleanSubtitleText(String raw) {
			return limit(clean(raw), SUBTITLE_MAX);
		}

		public static List<String> cleanSubtitle(List<String> raw) {
			List<String> out = new ArrayList<>();
			int used = 0;
			for (String line : raw) {
				String clean = clean(line);
				if (clean.isEmpty()) {
					continue;
				}
				int room = SUBTITLE_MAX - used - (out.isEmpty() ? 0 : 1);
				if (room <= 0 || out.size() >= SUBTITLE_LINES_MAX) {
					break;
				}
				clean = limit(clean, room);
				out.add(clean);
				used += clean.length() + (out.size() > 1 ? 1 : 0);
			}
			return List.copyOf(out);
		}

		public static int subtitleLength(List<String> lines) {
			return String.join(" ", lines).length();
		}

		private static String clean(String raw) {
			if (raw == null) {
				return "";
			}
			StringBuilder out = new StringBuilder(raw.length());
			for (int i = 0; i < raw.length(); i++) {
				char c = raw.charAt(i);
				if (c == '§') {
					i++;
				} else if (Character.isWhitespace(c)) {
					if (!out.isEmpty() && out.charAt(out.length() - 1) != ' ') {
						out.append(' ');
					}
				} else if (StringUtil.isAllowedChatCharacter(c)) {
					out.append(c);
				}
			}
			return out.toString().trim();
		}

		private static String limit(String text, int max) {
			if (text.length() <= max) {
				return text;
			}
			int end = Character.isHighSurrogate(text.charAt(max - 1)) ? max - 1 : max;
			return text.substring(0, end).trim();
		}
	}

	public record SetBookmark(int slot, int page) implements CustomPacketPayload {
		public static final Type<SetBookmark> TYPE = payloadType("set_bookmark");
		public static final StreamCodec<RegistryFriendlyByteBuf, SetBookmark> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SetBookmark::slot,
			ByteBufCodecs.VAR_INT, SetBookmark::page,
			SetBookmark::new
		);

		@Override
		public Type<SetBookmark> type() {
			return TYPE;
		}
	}

	public record SignDetailsPayload(int slot, SignDetails details) implements CustomPacketPayload {
		public static final Type<SignDetailsPayload> TYPE = payloadType("sign_details");
		public static final StreamCodec<RegistryFriendlyByteBuf, SignDetailsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SignDetailsPayload::slot,
			SignDetails.CODEC, SignDetailsPayload::details,
			SignDetailsPayload::new
		);

		@Override
		public Type<SignDetailsPayload> type() {
			return TYPE;
		}
	}

	public record LecternEdit(int containerId, List<String> pages, Optional<String> title, SignDetails details) implements CustomPacketPayload {
		public static final Type<LecternEdit> TYPE = payloadType("lectern_edit");
		public static final StreamCodec<RegistryFriendlyByteBuf, LecternEdit> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, LecternEdit::containerId,
			ByteBufCodecs.stringUtf8(BookLimits.PAGE_CHARS).apply(ByteBufCodecs.list(BookLimits.MAX_PAGES)), LecternEdit::pages,
			ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(BookLimits.TITLE_CHARS)), LecternEdit::title,
			SignDetails.CODEC, LecternEdit::details,
			LecternEdit::new
		);

		@Override
		public Type<LecternEdit> type() {
			return TYPE;
		}
	}

	public record CoverEditPayload(int slot, CoverEdit edit) implements CustomPacketPayload {
		public static final Type<CoverEditPayload> TYPE = payloadType("cover_edit");
		public static final StreamCodec<RegistryFriendlyByteBuf, CoverEditPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CoverEditPayload::slot,
			CoverEdit.CODEC, CoverEditPayload::edit,
			CoverEditPayload::new
		);

		@Override
		public Type<CoverEditPayload> type() {
			return TYPE;
		}
	}

	public record SaveDesign(int slot, int containerId, SignDetails details) implements CustomPacketPayload {
		public static final Type<SaveDesign> TYPE = payloadType("save_design");
		public static final StreamCodec<RegistryFriendlyByteBuf, SaveDesign> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SaveDesign::slot,
			ByteBufCodecs.VAR_INT, SaveDesign::containerId,
			SignDetails.CODEC, SaveDesign::details,
			SaveDesign::new
		);

		@Override
		public Type<SaveDesign> type() {
			return TYPE;
		}
	}

	public record SaveDetails(int slot, int containerId, String title, String subtitle, String penName) implements CustomPacketPayload {
		public static final Type<SaveDetails> TYPE = payloadType("save_details");
		public static final StreamCodec<RegistryFriendlyByteBuf, SaveDetails> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SaveDetails::slot,
			ByteBufCodecs.VAR_INT, SaveDetails::containerId,
			ByteBufCodecs.stringUtf8(64), SaveDetails::title,
			ByteBufCodecs.stringUtf8(64), SaveDetails::subtitle,
			ByteBufCodecs.stringUtf8(64), SaveDetails::penName,
			SaveDetails::new
		);

		public SaveDetails {
			title = SignDetails.cleanTitle(title);
			subtitle = SignDetails.cleanSubtitleText(subtitle);
			penName = SignDetails.cleanPenName(penName);
		}

		@Override
		public Type<SaveDetails> type() {
			return TYPE;
		}
	}

	public record CoverDesignerSave(int slot, String title, SignDetails details, boolean override) implements CustomPacketPayload {
		public static final Type<CoverDesignerSave> TYPE = payloadType("cover_designer");
		public static final StreamCodec<RegistryFriendlyByteBuf, CoverDesignerSave> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CoverDesignerSave::slot,
			ByteBufCodecs.stringUtf8(64), CoverDesignerSave::title,
			SignDetails.CODEC, CoverDesignerSave::details,
			ByteBufCodecs.BOOL, CoverDesignerSave::override,
			CoverDesignerSave::new
		);

		public CoverDesignerSave {
			title = SignDetails.cleanTitle(title);
		}

		public CoverDesignerSave(int slot, String title, SignDetails details) {
			this(slot, title, details, false);
		}

		@Override
		public Type<CoverDesignerSave> type() {
			return TYPE;
		}
	}

	public record Reading(boolean reading, boolean offhand) implements CustomPacketPayload {
		public static final Type<Reading> TYPE = payloadType("reading");
		public static final StreamCodec<RegistryFriendlyByteBuf, Reading> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Reading::reading,
			ByteBufCodecs.BOOL, Reading::offhand,
			Reading::new
		);

		@Override
		public Type<Reading> type() {
			return TYPE;
		}
	}

	public record ReadingState(int entityId, boolean reading, boolean offhand) implements CustomPacketPayload {
		public static final Type<ReadingState> TYPE = payloadType("reading_state");
		public static final StreamCodec<RegistryFriendlyByteBuf, ReadingState> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ReadingState::entityId,
			ByteBufCodecs.BOOL, ReadingState::reading,
			ByteBufCodecs.BOOL, ReadingState::offhand,
			ReadingState::new
		);

		@Override
		public Type<ReadingState> type() {
			return TYPE;
		}
	}

	public record PageTurn(int leaves) implements CustomPacketPayload {
		public static final int MAX_LEAVES = 3;
		public static final Type<PageTurn> TYPE = payloadType("page_turn");
		public static final StreamCodec<RegistryFriendlyByteBuf, PageTurn> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PageTurn::leaves,
			PageTurn::new
		);

		public PageTurn {
			leaves = Math.clamp(leaves, -MAX_LEAVES, MAX_LEAVES);
		}

		@Override
		public Type<PageTurn> type() {
			return TYPE;
		}
	}

	public record PageTurned(int entityId, int leaves) implements CustomPacketPayload {
		public static final Type<PageTurned> TYPE = payloadType("page_turned");
		public static final StreamCodec<RegistryFriendlyByteBuf, PageTurned> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PageTurned::entityId,
			ByteBufCodecs.VAR_INT, PageTurned::leaves,
			PageTurned::new
		);

		public PageTurned {
			leaves = Math.clamp(leaves, -PageTurn.MAX_LEAVES, PageTurn.MAX_LEAVES);
		}

		@Override
		public Type<PageTurned> type() {
			return TYPE;
		}
	}

	public record LecternLeaves(int containerId, int leaves) implements CustomPacketPayload {
		public static final Type<LecternLeaves> TYPE = payloadType("lectern_leaves");
		public static final StreamCodec<RegistryFriendlyByteBuf, LecternLeaves> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, LecternLeaves::containerId,
			ByteBufCodecs.VAR_INT, LecternLeaves::leaves,
			LecternLeaves::new
		);

		public LecternLeaves {
			leaves = Math.clamp(leaves, -PageTurn.MAX_LEAVES, PageTurn.MAX_LEAVES);
		}

		@Override
		public Type<LecternLeaves> type() {
			return TYPE;
		}
	}

	public record LecternLeavesTurned(BlockPos pos, int leaves) implements CustomPacketPayload {
		public static final Type<LecternLeavesTurned> TYPE = payloadType("lectern_leaves_turned");
		public static final StreamCodec<RegistryFriendlyByteBuf, LecternLeavesTurned> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, LecternLeavesTurned::pos,
			ByteBufCodecs.VAR_INT, LecternLeavesTurned::leaves,
			LecternLeavesTurned::new
		);

		public LecternLeavesTurned {
			leaves = Math.clamp(leaves, -PageTurn.MAX_LEAVES, PageTurn.MAX_LEAVES);
		}

		@Override
		public Type<LecternLeavesTurned> type() {
			return TYPE;
		}
	}

	public record LecternState(int containerId, boolean closed, boolean shared) implements CustomPacketPayload {
		public static final Type<LecternState> TYPE = payloadType("lectern_state");
		public static final StreamCodec<RegistryFriendlyByteBuf, LecternState> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, LecternState::containerId,
			ByteBufCodecs.BOOL, LecternState::closed,
			ByteBufCodecs.BOOL, LecternState::shared,
			LecternState::new
		);

		@Override
		public Type<LecternState> type() {
			return TYPE;
		}
	}

	public record LecternWriter(int containerId, String writer) implements CustomPacketPayload {
		public static final Type<LecternWriter> TYPE = payloadType("lectern_writer");
		public static final StreamCodec<RegistryFriendlyByteBuf, LecternWriter> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, LecternWriter::containerId,
			ByteBufCodecs.stringUtf8(64), LecternWriter::writer,
			LecternWriter::new
		);

		public boolean yours() {
			return this.writer.isEmpty();
		}

		@Override
		public Type<LecternWriter> type() {
			return TYPE;
		}
	}

	public record LecternPen(int containerId, boolean writing) implements CustomPacketPayload {
		public static final Type<LecternPen> TYPE = payloadType("lectern_pen");
		public static final StreamCodec<RegistryFriendlyByteBuf, LecternPen> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, LecternPen::containerId,
			ByteBufCodecs.BOOL, LecternPen::writing,
			LecternPen::new
		);

		@Override
		public Type<LecternPen> type() {
			return TYPE;
		}
	}

	public record ServerFeatures(int flags) implements CustomPacketPayload {
		public static final int PEN_NAMES = 1;
		public static final int SUBTITLES = 1 << 1;
		public static final int LECTERN_EDITING = 1 << 2;
		public static final int READING_POSE = 1 << 3;
		public static final int BOOK_INFO = 1 << 4;
		public static final int LECTERN_PAGES = 1 << 5;
		public static final int ENCHANTED_LECTERNS = 1 << 6;
		public static final int BOOKBINDING = 1 << 7;
		public static final int CLOSED_LECTERNS = 1 << 8;
		public static final int SIGN_FORMATTING = 1 << 9;
		public static final int PLACED_BOOKS = 1 << 10;
		public static final int CONTENTS_LINKS = 1 << 11;
		public static final int BOOK_DYEING = 1 << 12;
		public static final int TABLE_DYEING = 1 << 13;
		public static final int READING_LIBRARIANS = 1 << 14;
		public static final ServerFeatures NONE = new ServerFeatures(0);

		public static final Type<ServerFeatures> TYPE = payloadType("server_features");
		public static final StreamCodec<RegistryFriendlyByteBuf, ServerFeatures> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ServerFeatures::flags,
			ServerFeatures::new
		);

		public boolean has(int flag) {
			return (this.flags & flag) != 0;
		}

		@Override
		public Type<ServerFeatures> type() {
			return TYPE;
		}
	}
}
