package dev.remnants.net;

import dev.remnants.RemnantsMod;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the boss bar {@code bar} is drawn (and scored) in {@link dev.remnants.bossbar.BossStyle} {@code style}. */
public record BossStylePayload(UUID bar, int style) implements CustomPacketPayload {
	public static final Type<BossStylePayload> TYPE = new Type<>(RemnantsMod.id("boss_style"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BossStylePayload> CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC, BossStylePayload::bar, ByteBufCodecs.VAR_INT, BossStylePayload::style, BossStylePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
