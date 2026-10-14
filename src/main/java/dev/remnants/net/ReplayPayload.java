package dev.remnants.net;

import dev.remnants.RemnantsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> client: a Memorial projector at {@code at} plays a last record. Frames are every 2 ticks, positions relative
 * to the place of death: {@code pos} x, y, z per frame, {@code rot} yaw, pitch, {@code flags} (see LastRecord),
 * {@code itemIdx} into {@code items} (what was in hand), and the killer's track (NaN where it was not seen).
 */
public record ReplayPayload(BlockPos at, UUID owner, String name, Component cause, float[] pos, float[] rot, byte[] flags,
	List<ItemStack> items, byte[] itemIdx, String killer, float[] killerPos) implements CustomPacketPayload {
	public static final Type<ReplayPayload> TYPE = new Type<>(RemnantsMod.id("replay"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ReplayPayload> CODEC = StreamCodec.of((buf, p) -> p.write(buf), ReplayPayload::read);

	public int frames() {
		return this.flags.length;
	}

	private static ReplayPayload read(RegistryFriendlyByteBuf buf) {
		BlockPos at = buf.readBlockPos();
		UUID owner = UUIDUtil.STREAM_CODEC.decode(buf);
		String name = buf.readUtf(64);
		Component cause = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
		float[] pos = floats(buf);
		float[] rot = floats(buf);
		byte[] flags = buf.readByteArray(1024);
		int n = buf.readVarInt();
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
		}
		byte[] itemIdx = buf.readByteArray(1024);
		String killer = buf.readUtf(128);
		float[] killerPos = floats(buf);
		return new ReplayPayload(at, owner, name, cause, pos, rot, flags, items, itemIdx, killer, killerPos);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeBlockPos(this.at);
		UUIDUtil.STREAM_CODEC.encode(buf, this.owner);
		buf.writeUtf(this.name, 64);
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, this.cause);
		floats(buf, this.pos);
		floats(buf, this.rot);
		buf.writeByteArray(this.flags);
		buf.writeVarInt(this.items.size());
		for (ItemStack s : this.items) {
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s);
		}
		buf.writeByteArray(this.itemIdx);
		buf.writeUtf(this.killer, 128);
		floats(buf, this.killerPos);
	}

	private static float[] floats(RegistryFriendlyByteBuf buf) {
		int n = Math.min(buf.readVarInt(), 4096);
		float[] out = new float[n];
		for (int i = 0; i < n; i++) {
			out[i] = buf.readFloat();
		}
		return out;
	}

	private static void floats(RegistryFriendlyByteBuf buf, float[] v) {
		buf.writeVarInt(v.length);
		for (float f : v) {
			buf.writeFloat(f);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
