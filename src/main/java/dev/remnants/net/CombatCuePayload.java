package dev.remnants.net;

import dev.remnants.RemnantsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a cosmetic combat cue - a wind-up glint, a ground
 * telegraph that fills until the strike lands, an impact (hit-stop of attacker and victim, camera shake, a shock ring),
 * or the end of an entity's telegraphs. Damage stays server-side; these only make it readable.
 *
 * @param entity   the attacker
 * @param other    the victim of an impact, or -1
 * @param cue      one of {@link dev.remnants.fx.CombatCues} GLINT / DISC / ARC / LINE / IMPACT / CLEAR
 * @param x        position (glint, telegraph centre or line start, impact point)
 * @param yaw      facing of an arc or line (degrees, Minecraft yaw)
 * @param size     radius of a disc / arc, length of a line, radius of an impact's shock ring
 * @param width    arc: full angle in degrees; line: width in blocks
 * @param strength impact: 0..1 (shake, ring, sparks); glint: size factor
 * @param ticks    telegraph: ticks until the strike; impact: hit-stop ticks
 * @param flags    telegraph: {@link #ATTACHED} = drawn at the attacker's feet along its current facing every frame
 *                 (the move still turns to track its target)
 */
public record CombatCuePayload(int entity, int other, byte cue, float x, float y, float z, float yaw, float size, float width,
	float strength, int ticks, int flags) implements CustomPacketPayload {
	public static final int ATTACHED = 1;
	public static final Type<CombatCuePayload> TYPE = new Type<>(RemnantsMod.id("combat_cue"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CombatCuePayload> CODEC = StreamCodec.of(
		(buf, p) -> {
			buf.writeVarInt(p.entity);
			buf.writeVarInt(p.other + 1);
			buf.writeByte(p.cue);
			buf.writeFloat(p.x);
			buf.writeFloat(p.y);
			buf.writeFloat(p.z);
			buf.writeFloat(p.yaw);
			buf.writeFloat(p.size);
			buf.writeFloat(p.width);
			buf.writeFloat(p.strength);
			buf.writeVarInt(p.ticks);
			buf.writeVarInt(p.flags);
		},
		buf -> new CombatCuePayload(buf.readVarInt(), buf.readVarInt() - 1, buf.readByte(), buf.readFloat(), buf.readFloat(),
			buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
