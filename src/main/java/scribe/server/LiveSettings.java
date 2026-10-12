package scribe.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import scribe.network.Payloads;

public final class LiveSettings {
	private LiveSettings() {
	}

	public static int changed(MinecraftServer server, int before) {
		int now = ServerBooks.features();
		int told = sendFeatures(server, now);
		int switched = before ^ now;
		if ((switched & Payloads.ServerFeatures.READING_POSE) != 0) {
			boolean on = (now & Payloads.ServerFeatures.READING_POSE) != 0;
			for (ServerPlayer reader : ServerBooks.readers(server.getPlayerList().getPlayers())) {
				ServerBooks.tellWatchers(reader, ServerBooks.readingState(reader, on));
			}
		}
		boolean lecterns = (switched & (Payloads.ServerFeatures.CLOSED_LECTERNS | Payloads.ServerFeatures.BOOK_INFO)) != 0;
		boolean shelves = (switched & Payloads.ServerFeatures.BOOK_INFO) != 0;
		if (lecterns || shelves) {
			for (ServerLevel level : server.getAllLevels()) {
				level.getChunkSource().chunkMap.forEachReadyToSendChunk(chunk -> resend(level, chunk, lecterns, shelves));
			}
		}
		return told;
	}

	public static int sendFeatures(MinecraftServer server, int flags) {
		Payloads.ServerFeatures features = new Payloads.ServerFeatures(flags);
		List<ServerPlayer> receivers = featureReceivers(server.getPlayerList().getPlayers());
		for (ServerPlayer player : receivers) {
			ServerPlayNetworking.send(player, features);
		}
		return receivers.size();
	}

	public static List<ServerPlayer> featureReceivers(Collection<ServerPlayer> players) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (Payloads.hasScribe(player)) {
				out.add(player);
			}
		}
		return out;
	}

	public static List<BlockPos> resend(ServerLevel level, LevelChunk chunk, boolean lecterns, boolean shelves) {
		List<BlockPos> sent = new ArrayList<>();
		for (BlockEntity entity : List.copyOf(chunk.getBlockEntities().values())) {
			if ((lecterns && entity instanceof LecternBlockEntity) || (shelves && entity instanceof ChiseledBookShelfBlockEntity)) {
				BlockPos pos = entity.getBlockPos();
				BlockState state = entity.getBlockState();
				level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
				sent.add(pos.immutable());
			}
		}
		return sent;
	}
}
