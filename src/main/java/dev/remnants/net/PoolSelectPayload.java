package dev.remnants.net;

import dev.remnants.RemnantsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the mouse wheel turned {@code delta} notches while the player looked at the Reflecting Basin at {@code pos}. */
public record PoolSelectPayload(BlockPos pos, int delta) implements CustomPacketPayload {
	public static final Type<PoolSelectPayload> TYPE = new Type<>(RemnantsMod.id("pool_select"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PoolSelectPayload> CODEC = StreamCodec.of(
		(buf, p) -> {
			buf.writeBlockPos(p.pos);
			buf.writeVarInt(p.delta);
		},
		buf -> new PoolSelectPayload(buf.readBlockPos(), buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
