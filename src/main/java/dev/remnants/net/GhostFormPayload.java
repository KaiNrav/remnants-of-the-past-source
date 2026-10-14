package dev.remnants.net;

import dev.remnants.RemnantsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: entity {@code entityId} (a player) is a ghost for {@code ticks} more ticks (Mourner's Ghost Step). */
public record GhostFormPayload(int entityId, int ticks) implements CustomPacketPayload {
	public static final Type<GhostFormPayload> TYPE = new Type<>(RemnantsMod.id("ghost_form"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GhostFormPayload> CODEC = StreamCodec.of(
		(buf, p) -> {
			buf.writeVarInt(p.entityId);
			buf.writeVarInt(p.ticks);
		},
		buf -> new GhostFormPayload(buf.readVarInt(), buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
