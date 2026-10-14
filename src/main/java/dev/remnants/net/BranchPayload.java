package dev.remnants.net;

import dev.remnants.RemnantsMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the player picked a Path of the Soul branch in the Codex ("reaper", "keeper", "wanderer"). */
public record BranchPayload(String branch) implements CustomPacketPayload {
	public static final Type<BranchPayload> TYPE = new Type<>(RemnantsMod.id("branch"));
	public static final StreamCodec<FriendlyByteBuf, BranchPayload> CODEC = CustomPacketPayload.codec(BranchPayload::write, BranchPayload::new);

	private BranchPayload(FriendlyByteBuf buf) {
		this(buf.readUtf(32));
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeUtf(this.branch, 32);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
