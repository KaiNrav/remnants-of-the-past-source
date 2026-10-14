package dev.remnants.net;

import dev.remnants.Chronicle;
import dev.remnants.RemnantsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Server -> client: the player's whole Chronicle (oldest first), for the Codex. Sent on join and on every new entry. */
public record ChroniclePayload(List<Chronicle.Entry> entries) implements CustomPacketPayload {
	public static final Type<ChroniclePayload> TYPE = new Type<>(RemnantsMod.id("chronicle"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ChroniclePayload> CODEC = Chronicle.Entry.STREAM_CODEC
		.apply(ByteBufCodecs.list(Chronicle.LIMIT + 16)).map(ChroniclePayload::new, ChroniclePayload::entries);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
