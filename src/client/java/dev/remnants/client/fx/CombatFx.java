package dev.remnants.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.remnants.RemnantsConfig;
import dev.remnants.client.anim.BoneAnimations;
import dev.remnants.fx.CombatCues;
import dev.remnants.net.CombatCuePayload;
import dev.remnants.registry.ModParticles;
import dev.remnants.registry.ModSounds;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the art 2.0 hit feel.
 * <ul>
 * <li><b>Hit-stop</b>: an entity's keyframe clock stops for a few ticks when a blow lands, then plays a little faster
 * for {@link #CATCH_UP} ticks until it is back in step with the server ({@link #clipSeconds}).</li>
 * <li><b>Flash</b>: the first two hurt ticks of our mobs are drawn white ({@link #flash}, via getWhiteOverlayProgress).</li>
 * <li><b>Shake</b>: trauma from impacts near the camera, applied by {@code GameRendererShakeMixin}.</li>
 * <li><b>Telegraphs</b> and shock rings on the ground ({@link TelegraphRenderer}), <b>glints</b> on blades.</li>
 * <li><b>Clip events</b>: the models hand the time window of their clips to {@link #clipEvents}, which fires the clip's
 * events once each (glint, impact, step, ...).</li>
 * <li><b>Sound</b> (art 2.0 H, {@link ModSounds}): a soul swell when a telegraph appears, a swing when it runs out (the
 * strike starts), a body hit or a ground impact when a blow lands, a ting with each glint; bone chips and grave dust
 * fly from every impact.</li>
 * </ul>
 */
public final class CombatFx {
	public static final float CATCH_UP = 8.0F;
	private static final Map<Integer, List<float[]>> FREEZES = new HashMap<>();
	private static final Map<Integer, Integer> HURT_SEEN = new HashMap<>();
	private static final Map<String, Object[]> CURSORS = new HashMap<>();
	static final List<Telegraph> TELEGRAPHS = new ArrayList<>();
	static final List<float[]> SHOCKS = new ArrayList<>();   // x, y, z, radius, start, life
	private static final Set<Telegraph> SWUNG = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Map<Integer, Float> LAST_WINDUP = new HashMap<>();
	private static float trauma, traumaO;
	private static float lastNow;

	private CombatFx() {
	}

	public static void register() {
		ParticleFactoryRegistry.getInstance().register(ModParticles.GLINT, GlintParticle.Provider::new);
		ParticleFactoryRegistry.getInstance().register(ModParticles.BONE_SPARK, FxParticles.SparkProvider::new);
		ParticleFactoryRegistry.getInstance().register(ModParticles.SOUL_DUST, FxParticles.DustProvider::new);
		VfxParticles.register();
		ClientPlayNetworking.registerGlobalReceiver(CombatCuePayload.TYPE, (payload, context) -> receive(payload));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> tick(mc));
		WorldRenderEvents.AFTER_ENTITIES.register(TelegraphRenderer::render);
	}

	static float now(float partial) {
		ClientLevel level = Minecraft.getInstance().level;
		return level == null ? 0.0F : (level.getGameTime() % 1_000_000L) + partial;
	}

	// ---- cues from the server ---------------------------------------------------------------------------------------

	private static void receive(CombatCuePayload p) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		RemnantsConfig cfg = RemnantsConfig.get();
		switch (p.cue()) {
			case CombatCues.GLINT -> {
				if (cfg.telegraphs) {
					level.addParticle(ModParticles.GLINT, p.x(), p.y(), p.z(), p.strength(), 0, 0);
				}
				play(p.x(), p.y(), p.z(), ModSounds.COMBAT_GLINT, 0.35F, 1.0F);
			}
			case CombatCues.DISC, CombatCues.ARC, CombatCues.LINE -> {
				windup(level, p.entity(), p.ticks());
				if (cfg.telegraphs) {
					TELEGRAPHS.add(new Telegraph(p.entity(), p.cue(), p.x(), p.y(), p.z(), p.yaw(), p.size(), p.width(), now(0), Math.max(1, p.ticks()),
						p.flags()));
				}
			}
			case CombatCues.IMPACT -> impact(level, p);
			case CombatCues.CLEAR -> TELEGRAPHS.removeIf(t -> t.entity == p.entity());
			default -> {
			}
		}
	}

	/** A local (client-only) sound; the server plays its own on top. Pitch wobbles a little so repeats do not drone. */
	static void play(double x, double y, double z, SoundEvent sound, float volume, float pitch) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			level.playLocalSound(x, y, z, sound, SoundSource.HOSTILE, volume, pitch * (0.94F + level.random.nextFloat() * 0.12F), false);
		}
	}

	/** The soul swell of a wind-up, once per attacker per move (a volley of telegraphs swells once). Pitched by how long
	 * the wind-up is: a quick one sounds higher. */
	private static void windup(ClientLevel level, int id, int ticks) {
		Entity e = level.getEntity(id);
		float t = now(0);
		Float last = LAST_WINDUP.get(id);
		if (e == null || (last != null && t - last < 8.0F)) {
			return;
		}
		LAST_WINDUP.put(id, t);
		float big = Mth.clamp(e.getBbWidth() / 1.5F, 0.6F, 1.6F);
		play(e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), ModSounds.COMBAT_WINDUP, 0.6F + 0.3F * big,
			Mth.clamp(1.25F - ticks / 60.0F, 0.75F, 1.2F) / (0.8F + 0.2F * big));
	}

	private static void impact(ClientLevel level, CombatCuePayload p) {
		RemnantsConfig cfg = RemnantsConfig.get();
		Entity attacker = level.getEntity(p.entity());
		Entity victim = p.other() >= 0 ? level.getEntity(p.other()) : null;
		if (victim != null) {
			play(p.x(), p.y(), p.z(), ModSounds.COMBAT_HIT, 0.6F + 0.6F * p.strength(), 1.15F - 0.3F * p.strength());
		}
		if (p.size() > 0 || victim == null) {
			play(p.x(), p.y(), p.z(), ModSounds.COMBAT_IMPACT, 0.5F + 1.0F * p.strength(), 1.2F - 0.35F * p.strength());
		}
		if (cfg.hitStop && p.ticks() > 0) {
			if (attacker != null) {
				freeze(attacker, p.ticks());
			}
			if (victim != null && !(victim == Minecraft.getInstance().player)) {
				freeze(victim, p.ticks());
			}
		}
		Minecraft mc = Minecraft.getInstance();
		Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
		double d = cam.distanceTo(new Vec3(p.x(), p.y(), p.z()));
		float fall = (float) Mth.clamp(1.0 - (d - 3.0) / 16.0, 0.0, 1.0);
		float add = p.strength() * fall;
		if (victim != null && victim == mc.player) {
			add = Math.max(add, p.strength());
		}
		trauma = Math.min(1.0F, trauma + add);
		TELEGRAPHS.removeIf(t -> t.entity == p.entity() && t.end() <= now(0) + 2.0F);
		// art 4.0 mesh VFX (client/fx/MeshFx): a slam spreads a shockwave (and tears the ground open if it is big), a blow
		// that lands draws its blade's arc from the attacker and, if heavy, a white star with shards. Nothing is drawn in
		// the camera's face: when the local player is the one hit in first person, the arc stays short of them and the
		// burst at the hit point is left out (those big quads at half a block filled the screen)
		Vec3 hit = new Vec3(p.x(), p.y(), p.z());
		boolean inFace = cam.distanceTo(hit) < 2.2 || victim == mc.player && mc.options.getCameraType().isFirstPerson();
		if (p.size() > 0) {
			MeshFx.shockwave(hit, p.size() * 1.1F);
			if (p.size() >= 2.5F) {
				MeshFx.crack(hit, p.size() * 0.75F);
			}
		}
		if (attacker != null && victim != null && p.strength() >= 0.35F) {
			Vec3 from = attacker.position().add(0, attacker.getBbHeight() * 0.55, 0);
			double dx = hit.x - from.x, dz = hit.z - from.z;
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			float reach = (float) Mth.clamp(Math.sqrt(dx * dx + dz * dz) + 0.35, 1.2, 4.5);
			if (inFace) {
				reach = Math.max(0.9F, reach * 0.55F);
			}
			float roll = (attacker.tickCount % 3 - 1) * 32.0F + (level.random.nextFloat() - 0.5F) * 20.0F;
			MeshFx.slash(from, yaw, roll, reach, 100.0F + 40.0F * p.strength());
		}
		if (!inFace) {
			level.addParticle(ModParticles.SOUL_BURST, p.x(), p.y() + 0.6, p.z(), 0, 0, 0);
			if (victim != null && p.strength() >= 0.75F) {
				MeshFx.burst(hit.add(0, 0.8, 0), 0.6F + 0.5F * p.strength());
			}
		}
		int n = 4 + Math.round(10 * p.strength());
		for (int i = 0; i < n; i++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double s = 0.12 + level.random.nextDouble() * 0.25 * (0.5 + p.strength());
			level.addParticle(ModParticles.BONE_SPARK, p.x(), p.y() + 0.2, p.z(), Math.cos(a) * s, 0.15 + level.random.nextDouble() * 0.3,
				Math.sin(a) * s);
		}
		if (p.size() > 0 || victim == null) {   // a blow into the ground throws up a ring of grave dust
			int m = 6 + Math.round(8 * p.strength());
			for (int i = 0; i < m; i++) {
				double a = i * Math.PI * 2 / m + level.random.nextDouble() * 0.4;
				double s = 0.12 + 0.12 * p.strength();
				level.addParticle(ModParticles.SOUL_DUST, p.x() + Math.cos(a) * 0.5, p.y() + 0.15, p.z() + Math.sin(a) * 0.5, Math.cos(a) * s,
					0.02 + level.random.nextDouble() * 0.03, Math.sin(a) * s);
			}
		}
		for (int i = 0; i < Math.round(6 * p.strength()); i++) {
			double a = i * Math.PI * 2 / Math.max(1, Math.round(6 * p.strength()));
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, p.x() + Math.cos(a) * 0.4, p.y() + 0.1, p.z() + Math.sin(a) * 0.4,
				Math.cos(a) * 0.08, 0.02, Math.sin(a) * 0.08);
		}
	}

	private static void tick(Minecraft mc) {
		traumaO = trauma;
		trauma = Math.max(0.0F, trauma - 0.07F);
		if (mc.level == null) {
			FREEZES.clear();
			HURT_SEEN.clear();
			CURSORS.clear();
			TELEGRAPHS.clear();
			SHOCKS.clear();
			SWUNG.clear();
			LAST_WINDUP.clear();
			return;
		}
		float t = now(0);
		if (t < lastNow - 100.0F) {
			// the clock wrapped (now() runs modulo 1e6 ticks) or the world changed: every stored start time is from another
			// time base and would never expire - a telegraph or shock ring stuck on the ground for the rest of the fight
			TELEGRAPHS.clear();
			SHOCKS.clear();
			SWUNG.clear();
			LAST_WINDUP.clear();
			FREEZES.clear();
		}
		lastNow = t;
		// a sweep / lane telegraph running out is the strike starting: the weapon swings (a cone is a Keener's scream
		// or a breath, not a blade - those cues carry their own sound)
		for (Telegraph g : TELEGRAPHS) {
			if (g.shape() == CombatCues.LINE || g.shape() == CombatCues.ARC) {
				Entity e = mc.level.getEntity(g.entity());
				if (e != null && t >= g.end() - 1.0F && !SWUNG.contains(g) && !(e instanceof dev.remnants.entity.KeenerEntity)) {
					SWUNG.add(g);
					boolean heavy = e.getBbWidth() > 0.9F;
					play(e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), heavy ? ModSounds.COMBAT_SWING_HEAVY : ModSounds.COMBAT_SWING_LIGHT,
						heavy ? 1.0F : 0.7F, heavy ? 0.85F : 1.1F);
				}
			}
		}
		TELEGRAPHS.removeIf(g -> t > g.end() + 6.0F || mc.level.getEntity(g.entity) == null);
		SWUNG.retainAll(TELEGRAPHS);
		LAST_WINDUP.values().removeIf(v -> t - v > 40.0F);
		SHOCKS.removeIf(s -> t > s[4] + s[5]);
		if (mc.level.getGameTime() % 100 == 0) {
			FREEZES.values().removeIf(List::isEmpty);
			FREEZES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
			HURT_SEEN.keySet().removeIf(id -> mc.level.getEntity(id) == null);
			CURSORS.keySet().removeIf(k -> mc.level.getEntity(Integer.parseInt(k.substring(0, k.indexOf('/')))) == null);
		}
	}

	// ---- hit-stop -----------------------------------------------------------------------------------------------------

	/** Stops {@code e}'s keyframe clock for {@code ticks} ticks from now. */
	public static void freeze(Entity e, int ticks) {
		List<float[]> list = FREEZES.computeIfAbsent(e.getId(), k -> new ArrayList<>());
		list.removeIf(w -> e.tickCount > w[0] + w[1] + CATCH_UP + 1);
		list.add(new float[] {e.tickCount, ticks});
	}

	/** Ticks the clock of {@code e} lags behind at {@code age} for a clip that began at tick {@code from}. */
	public static float lag(Entity e, float from, float age) {
		List<float[]> list = FREEZES.get(e.getId());
		if (list == null) {
			return 0.0F;
		}
		float lag = 0.0F;
		for (float[] w : list) {
			float s = w[0], len = w[1];
			// a cue can arrive a tick before the move change it belongs to: the clip starting within 2 ticks after the
			// freeze still takes it (the slam that follows the wind-up), clips begun earlier do not
			if (s < from - 2.0F || age <= s) {
				continue;
			}
			if (age <= s + len) {
				lag += age - s;
			} else {
				lag += len * Math.max(0.0F, 1.0F - (age - s - len) / CATCH_UP);
			}
		}
		return Math.min(lag, Math.max(0.0F, age - from));
	}

	/** Seconds into a clip that began at tick {@code startTick}, hit-stops taken out. */
	public static float clipSeconds(Entity e, float startTick, float age) {
		return Math.max(0.0F, (age - startTick - lag(e, startTick, age)) / 20.0F);
	}

	/** Our mobs call this every frame: a fresh hurt freezes them for a tick (the blow lands on something heavy). */
	public static void watchHurt(LivingEntity e, int ticks) {
		if (e.hurtTime > 0 && e.hurtTime == e.hurtDuration && !Integer.valueOf(e.tickCount).equals(HURT_SEEN.get(e.getId()))
			&& isLive(e)) {
			HURT_SEEN.put(e.getId(), e.tickCount);
			if (RemnantsConfig.get().hitStop) {
				freeze(e, ticks);
			}
		}
	}

	/** White-flash amount for getWhiteOverlayProgress: the first 2 of the 10 hurt ticks. */
	public static float flash(LivingEntity e, float partial) {
		if (e.hurtTime <= 0 || e.deathTime > 0) {
			return 0.0F;
		}
		float t = e.hurtDuration - (e.hurtTime - partial);
		return t < 2.5F ? 0.75F * (1.0F - t / 2.5F) : 0.0F;
	}

	// ---- clip events --------------------------------------------------------------------------------------------------

	/** Fires the events of {@code clip} between the last frame's time and {@code seconds}, once each. One cursor per
	 * entity and {@code slot} ("move", "walk", ...), so a walk cycle and an attack can both fire. */
	public static void clipEvents(LivingEntity e, String slot, BoneAnimations anims, String clip, float seconds, BoneAnimations.EventSink sink) {
		if (!isLive(e)) {
			return;
		}
		String key = e.getId() + "/" + slot;
		Object[] cur = CURSORS.get(key);
		float from = -0.001F;
		if (cur != null && clip.equals(cur[0])) {
			from = (float) cur[1];
			if (seconds <= from && from - seconds < 0.5F) {
				return;   // a hit-stop or a cross-fade nudged the clock back a little: nothing fires twice
			}
			if (seconds < from) {
				from = -0.001F;   // the clip really restarted (a loop came round on another start)
			}
		}
		CURSORS.put(key, new Object[] {clip, seconds});
		if (seconds > from) {
			anims.events(clip, from, seconds, sink);
		}
	}

	/** True for an entity rendered in the world (not a Codex portrait or other off-world copy). */
	public static boolean isLive(Entity e) {
		ClientLevel level = Minecraft.getInstance().level;
		return level != null && e.level() == level && level.getEntity(e.getId()) == e;
	}

	public static void glint(Vec3 at, float size) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && RemnantsConfig.get().telegraphs) {
			level.addParticle(ModParticles.GLINT, at.x, at.y, at.z, size, 0, 0);
		}
	}

	// ---- camera shake -------------------------------------------------------------------------------------------------

	public static void applyShake(PoseStack pose, float partial) {
		float k = (float) Mth.clamp(RemnantsConfig.get().screenShake, 0.0, 1.0);
		float tr = Mth.lerp(partial, traumaO, trauma);
		if (k <= 0.0F || tr <= 0.0F) {
			return;
		}
		float amp = tr * tr * k;
		float t = now(partial);
		pose.mulPose(Axis.XP.rotationDegrees(amp * 2.2F * Mth.sin(t * 2.9F)));
		pose.mulPose(Axis.YP.rotationDegrees(amp * 1.6F * Mth.sin(t * 2.3F + 1.7F)));
		pose.mulPose(Axis.ZP.rotationDegrees(amp * 2.6F * Mth.sin(t * 3.7F + 0.6F)));
	}

	/** A ground telegraph; an ATTACHED one keeps (x, z) = 0 and y = how far ahead of the attacker it lies, and is placed
	 * at the attacker's feet along its body facing each frame ({@link #placed}). */
	record Telegraph(int entity, byte shape, float x, float y, float z, float yaw, float size, float width, float start, int ticks, int flags) {
		float end() {
			return this.start + this.ticks;
		}

		Telegraph placed(float partial) {
			if ((this.flags & CombatCuePayload.ATTACHED) == 0) {
				return this;
			}
			ClientLevel level = Minecraft.getInstance().level;
			Entity e = level == null ? null : level.getEntity(this.entity);
			if (!(e instanceof LivingEntity living)) {
				return null;
			}
			float yaw = Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot);
			double px = Mth.lerp(partial, e.xo, e.getX()), py = Mth.lerp(partial, e.yo, e.getY()), pz = Mth.lerp(partial, e.zo, e.getZ());
			Vec3 f = Vec3.directionFromRotation(0, yaw);
			return new Telegraph(this.entity, this.shape, (float) (px + f.x * this.y), (float) py, (float) (pz + f.z * this.y), yaw, this.size,
				this.width, this.start, this.ticks, 0);
		}
	}

	/** Shock rings and telegraphs, for the renderer. */
	static Iterator<Telegraph> telegraphs() {
		return TELEGRAPHS.iterator();
	}
}
