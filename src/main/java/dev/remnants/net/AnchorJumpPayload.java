package dev.remnants.net;

import dev.remnants.RemnantsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: from the Anchors screen at {@code from}, go to the anchor at {@code to} (same dimension). */
public record AnchorJumpPayload(BlockPos from, BlockPos to) implements CustomPacketPayload {
	public static final Type<AnchorJumpPayload> TYPE = new Type<>(RemnantsMod.id("anchor_jump"));
	public static final StreamCodec<FriendlyByteBuf, AnchorJumpPayload> CODEC = CustomPacketPayload.codec(AnchorJumpPayload::write, AnchorJumpPayload::new);

	private AnchorJumpPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos(), buf.readBlockPos());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeBlockPos(this.from);
		buf.writeBlockPos(this.to);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
