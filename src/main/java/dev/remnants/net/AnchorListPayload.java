package dev.remnants.net;

import dev.remnants.RemnantsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: the player's Soul Anchor network in this dimension, to open the Anchors screen at the anchor
 * {@code from}. {@code cost} = Soul Fragments a jump costs (0 for a Wanderer), {@code cooldown} = ticks until the next.
 */
public record AnchorListPayload(BlockPos from, List<Entry> anchors, int cost, int cooldown, int max) implements CustomPacketPayload {
	public static final Type<AnchorListPayload> TYPE = new Type<>(RemnantsMod.id("anchor_list"));
	public static final StreamCodec<FriendlyByteBuf, AnchorListPayload> CODEC = CustomPacketPayload.codec(AnchorListPayload::write, AnchorListPayload::new);

	public record Entry(BlockPos pos, String name, boolean present) {
	}

	private AnchorListPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos(), readEntries(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
	}

	private static List<Entry> readEntries(FriendlyByteBuf buf) {
		int n = buf.readVarInt();
		List<Entry> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(new Entry(buf.readBlockPos(), buf.readUtf(64), buf.readBoolean()));
		}
		return out;
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeBlockPos(this.from);
		buf.writeVarInt(this.anchors.size());
		for (Entry e : this.anchors) {
			buf.writeBlockPos(e.pos());
			buf.writeUtf(e.name(), 64);
			buf.writeBoolean(e.present());
		}
		buf.writeVarInt(this.cost);
		buf.writeVarInt(this.cooldown);
		buf.writeVarInt(this.max);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
