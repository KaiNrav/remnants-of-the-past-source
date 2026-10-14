package dev.remnants.client.fx;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The mobs' ambient VFX (art 4.0): steady emitters pinned to points on the posed model - embers off a glowing blade,
 * vapour off a veil, afterimages behind glowing eyes, flames on a crown. Called from a model's setupAnim (after the
 * pose, so the points follow every keyframe, look and cloth swing); each emitter keeps its own clock per mob, so the
 * rate is per second of game time at any frame rate, nothing is emitted while paused, and off-world copies (Codex
 * portraits) and far-away mobs emit nothing.
 */
public final class MobFx {
	private static final Map<Long, float[]> CLOCKS = new HashMap<>();
	private static final double RANGE = 48.0;

	private MobFx() {
	}

	/** How many particles emitter {@code slot} of this mob owes this frame at {@code perSecond}. */
	public static int due(LivingEntity e, int slot, float age, float perSecond) {
		Minecraft mc = Minecraft.getInstance();
		if (perSecond <= 0.0F || mc.isPaused() || !CombatFx.isLive(e)
			|| mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(e.position()) > RANGE * RANGE) {
			return 0;
		}
		long key = ((long) e.getId() << 8) | (slot & 0xFF);
		long now = System.currentTimeMillis();
		float[] c = CLOCKS.computeIfAbsent(key, k -> new float[] {age, e.getRandom().nextFloat(), 0.0F});
		float dt = Mth.clamp(age - c[0], 0.0F, 10.0F);
		c[0] = age;
		c[2] = (float) (now % 1_000_000L);
		c[1] += dt / 20.0F * perSecond;
		int n = (int) c[1];
		c[1] -= n;
		if (CLOCKS.size() > 1024) {
			float t = (float) (now % 1_000_000L);
			CLOCKS.values().removeIf(v -> Math.abs(t - v[2]) > 10_000.0F);
		}
		return Math.min(n, 12);
	}

	private static final Map<Integer, int[]> AIR = new HashMap<>();

	/** True on the first tick a mob is back on the ground after at least {@code minAir} ticks in the air (hops, leaps). */
	public static boolean landed(LivingEntity e, int minAir) {
		if (!CombatFx.isLive(e)) {
			return false;
		}
		int[] s = AIR.computeIfAbsent(e.getId(), k -> new int[] {0, e.tickCount});
		if (s[1] == e.tickCount) {
			return false;
		}
		s[1] = e.tickCount;
		if (AIR.size() > 256) {
			AIR.keySet().removeIf(id -> e.level().getEntity(id) == null);
		}
		if (!e.onGround()) {
			s[0]++;
			return false;
		}
		boolean land = s[0] >= minAir;
		s[0] = 0;
		return land;
	}

	/** A point on the posed model (see {@link ModelPoints#world}). */
	public static Vec3 at(LivingEntity e, float partial, float x, float y, float z, ModelPart... chain) {
		return ModelPoints.world(e, partial, x, y, z, chain);
	}

	public static void spawn(LivingEntity e, ParticleOptions type, Vec3 at, double vx, double vy, double vz) {
		e.level().addParticle(type, at.x, at.y, at.z, vx, vy, vz);
	}

	/** Spawns with a little random spread of position and velocity. */
	public static void spawn(LivingEntity e, ParticleOptions type, Vec3 at, double spread, double vx, double vy, double vz, double jitter) {
		RandomSource r = e.getRandom();
		e.level().addParticle(type, at.x + (r.nextDouble() - 0.5) * spread, at.y + (r.nextDouble() - 0.5) * spread,
			at.z + (r.nextDouble() - 0.5) * spread, vx + (r.nextDouble() - 0.5) * jitter, vy + (r.nextDouble() - 0.5) * jitter,
			vz + (r.nextDouble() - 0.5) * jitter);
	}
}
