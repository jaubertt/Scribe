package scribe.network;

import scribe.Scribe;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public record LibrarianReadingPayload(int entityId, boolean reading, ItemStack book) implements CustomPacketPayload {
	public static final Type<LibrarianReadingPayload> TYPE = new Type<>(Scribe.id("librarian_reading"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LibrarianReadingPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, LibrarianReadingPayload::entityId,
		ByteBufCodecs.BOOL, LibrarianReadingPayload::reading,
		ItemStack.OPTIONAL_STREAM_CODEC, LibrarianReadingPayload::book,
		LibrarianReadingPayload::new
	);

	@Override
	public Type<LibrarianReadingPayload> type() {
		return TYPE;
	}
}
