package dev.remnants.net;

import dev.remnants.RemnantsMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: the player's Soul Resonance, for the Codex's "Path of the Soul" page. Sent on join, when it
 * changes, and when the Codex is opened. {@code lastBreathTicks} = ticks until Last Breath is ready (0 = ready).
 * 1.5: Archivists slain, the chosen branch ("" = none), the rank branches open at, and whether a full Memorial wall adds
 * its bonus.
 */
public record ResonancePayload(int points, int laidOwn, int laidForgotten, int laidOthers, int kingsSlain, int streak,
	int remnantsOut, int lastBreathTicks, int archivistsSlain, String branch, int branchRank, boolean memorial) implements CustomPacketPayload {
	public static final ResonancePayload EMPTY = new ResonancePayload(0, 0, 0, 0, 0, 0, 0, 0, 0, "", 3, false);
	public static final Type<ResonancePayload> TYPE = new Type<>(RemnantsMod.id("resonance"));
	public static final StreamCodec<FriendlyByteBuf, ResonancePayload> CODEC = CustomPacketPayload.codec(ResonancePayload::write, ResonancePayload::new);

	private ResonancePayload(FriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
			buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(32), buf.readVarInt(), buf.readBoolean());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(this.points);
		buf.writeVarInt(this.laidOwn);
		buf.writeVarInt(this.laidForgotten);
		buf.writeVarInt(this.laidOthers);
		buf.writeVarInt(this.kingsSlain);
		buf.writeVarInt(this.streak);
		buf.writeVarInt(this.remnantsOut);
		buf.writeVarInt(this.lastBreathTicks);
		buf.writeVarInt(this.archivistsSlain);
		buf.writeUtf(this.branch, 32);
		buf.writeVarInt(this.branchRank);
		buf.writeBoolean(this.memorial);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
