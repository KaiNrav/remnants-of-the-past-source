package dev.remnants.fx;

import dev.remnants.net.CombatCuePayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server side of the combat cues (client: dev.remnants.client.fx.CombatFx): what a strike is about to do and when, and
 * that it landed. Readability rules: a glint and a ground telegraph at the START of every
 * dodgeable wind-up (the telegraph fills up exactly on the strike tick), an impact with hit-stop / shake / shock ring on
 * the tick damage is dealt. Sent only to players tracking the attacker (and the attacker if a player); a no-op in
 * game tests (mock players cannot receive custom payloads).
 */
public final class CombatCues {
	public static final byte GLINT = 0, DISC = 1, ARC = 2, LINE = 3, IMPACT = 4, CLEAR = 5;

	private CombatCues() {
	}

	/** A flash of soul light at {@code at} (a blade, a visor) when a wind-up begins. */
	public static void glint(Entity e, Vec3 at, float size) {
		send(e, new CombatCuePayload(e.getId(), -1, GLINT, (float) at.x, (float) at.y, (float) at.z, 0, 0, 0, size, 0, 0));
	}

	/** A disc on the ground (centre, radius) that fills in {@code ticks}: where a slam will land. */
	public static void disc(Entity e, Vec3 centre, float radius, int ticks) {
		send(e, new CombatCuePayload(e.getId(), -1, DISC, (float) centre.x, (float) centre.y, (float) centre.z, 0, radius, 360, 1, ticks, 0));
	}

	/** A sector round {@code centre} facing {@code yaw}, {@code arc} degrees wide: a sweep. */
	public static void arc(Entity e, Vec3 centre, float yaw, float radius, float arc, int ticks) {
		send(e, new CombatCuePayload(e.getId(), -1, ARC, (float) centre.x, (float) centre.y, (float) centre.z, yaw, radius, arc, 1, ticks, 0));
	}

	/** A lane from {@code from} along {@code yaw}: a charge. */
	public static void line(Entity e, Vec3 from, float yaw, float length, float width, int ticks) {
		send(e, new CombatCuePayload(e.getId(), -1, LINE, (float) from.x, (float) from.y, (float) from.z, yaw, length, width, 1, ticks, 0));
	}

	/** A lane that stays at the attacker's feet along its facing while it turns (offset {@code ahead} blocks): a charge
	 * still aiming. */
	public static void lineAttached(Entity e, float ahead, float length, float width, int ticks) {
		send(e, new CombatCuePayload(e.getId(), -1, LINE, 0, ahead, 0, 0, length, width, 1, ticks, CombatCuePayload.ATTACHED));
	}

	/** A sector at the attacker's feet along its facing, following it while it turns: a cone it is still aiming. */
	public static void arcAttached(Entity e, float radius, float arc, int ticks) {
		send(e, new CombatCuePayload(e.getId(), -1, ARC, 0, 0, 0, 0, radius, arc, 1, ticks, CombatCuePayload.ATTACHED));
	}

	/** A disc {@code ahead} blocks in front of the attacker that follows its facing: a slam still aiming. */
	public static void discAttached(Entity e, float ahead, float radius, int ticks) {
		send(e, new CombatCuePayload(e.getId(), -1, DISC, 0, ahead, 0, 0, radius, 360, 1, ticks, CombatCuePayload.ATTACHED));
	}

	/**
	 * The strike landed (or hit the floor): attacker and victim freeze for {@code hitStop} ticks, nearby cameras shake by
	 * {@code strength} (0..1), a shock ring of {@code ring} blocks spreads from {@code at}.
	 */
	public static void impact(Entity attacker, @Nullable Entity victim, Vec3 at, float strength, float ring, int hitStop) {
		send(attacker, new CombatCuePayload(attacker.getId(), victim == null ? -1 : victim.getId(), IMPACT, (float) at.x, (float) at.y,
			(float) at.z, 0, ring, 0, strength, hitStop, 0));
	}

	/** Drops the attacker's telegraphs (the move was interrupted). */
	public static void clear(Entity e) {
		send(e, new CombatCuePayload(e.getId(), -1, CLEAR, 0, 0, 0, 0, 0, 0, 0, 0, 0));
	}

	private static void send(Entity e, CombatCuePayload payload) {
		if (e.level().isClientSide) {
			return;
		}
		for (ServerPlayer p : PlayerLookup.tracking(e)) {
			if (ServerPlayNetworking.canSend(p, CombatCuePayload.TYPE)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
		if (e instanceof ServerPlayer self && ServerPlayNetworking.canSend(self, CombatCuePayload.TYPE)) {
			ServerPlayNetworking.send(self, payload);
		}
	}
}
