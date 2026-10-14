package dev.remnants.client.anim;

import dev.remnants.client.fx.CombatFx;
import dev.remnants.client.fx.ModelPoints;
import dev.remnants.entity.LookTarget;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Where a mob looks, worked out on the client every frame and laid over its keyframes: the head stays locked on its
 * target, smoothly, whatever the body and the clips do.
 *
 * <p>v2: a head no longer jerks aside for a moment on a cast and back. Three causes of that jerk are gone:
 * <ul>
 * <li>the look is smoothed in <b>world</b> yaw, not relative to the body: when the body snaps round (an attack's first
 * tick, a blink, a 60-degree turn) the head does not swing with it and come back - it simply stays on the target;</li>
 * <li>the keyframes' own head motion is measured every frame (where the posed chain points the head) and corrected, so a
 * wind-up that twists the torso does not drag the eyes off the target; the look no longer fades out for attack clips;</li>
 * <li>the target is sticky: if the synced target drops for a moment (a recast clears it on the server), the head keeps
 * the last one for two seconds instead of turning to look ahead.</li>
 * </ul>
 * The target's eyes are taken at the frame's partial tick from the posed model's eye, so the aim is exact at any frame
 * rate. The correction is one rotation, shared along the chain (torso, neck, head) and applied in model space about each
 * pivot: yaw about the vertical and pitch about the head's own side axis, keeping whatever tilt the keyframes give the
 * head. (Until 2026-10-14 it was the shortest arc, which twists the head about its nose when it looks up and aside at
 * once; on the vanilla player model, which never resets head.zRot, that twist piled up frame after frame until the
 * Remnant's head lay on its shoulder.)
 */
public final class HeadLook {
	private static final Map<Long, float[]> STATE = new HashMap<>();
	private static final int YAW = 0, PITCH = 1, YAW_V = 2, PITCH_V = 3, WEIGHT = 4, AGE = 5, SEEN = 6, TARGET = 7, TARGET_AGE = 8;
	private static final float STICKY = 40.0F;   // ticks a lost target is still looked at

	private HeadLook() {
	}

	/** The look applied this frame, relative to the body, in degrees (vanilla's head yaw / pitch convention). */
	public record Look(float yaw, float pitch) {
		public static final Look NONE = new Look(0.0F, 0.0F);
	}

	/**
	 * A chain of bones from the model's top part down to the head, and how much of the turn each carries. The model must
	 * pose every bone of the chain afresh each frame before {@link #apply} (resetPose, the keyframes, or vanilla's setup
	 * plus resetting what it leaves alone - HumanoidModel never resets head.zRot): the look is written into the bones'
	 * angles, and a look left over from the last frame would be turned again.
	 */
	public static final class Rig {
		final ModelPart[] chain;
		final float[] share;
		float eyeX, eyeY, eyeZ;
		float maxYaw = 70.0F, maxUp = 40.0F, maxDown = 45.0F;
		float speed = 9.0F;
		float glance = 10.0F;
		int slot;

		/** @param chain the model's top part first, the head last (nulls are skipped) */
		public Rig(ModelPart... chain) {
			this.chain = chain;
			this.share = new float[chain.length];
			this.share[chain.length - 1] = 1.0F;
		}

		/** Shares of the turn per chain bone (same order as the chain), adding up to 1. The second array is ignored
		 * (it was the pitch share; the turn is one rotation now). */
		public Rig shares(float[] yaw, float[] pitch) {
			System.arraycopy(yaw, 0, this.share, 0, this.chain.length);
			return this;
		}

		/** The eye point in the head's own Java model space, in pixels. */
		public Rig eye(float x, float y, float z) {
			this.eyeX = x;
			this.eyeY = y;
			this.eyeZ = z;
			return this;
		}

		public Rig limits(float maxYaw, float maxUp, float maxDown) {
			this.maxYaw = maxYaw;
			this.maxUp = maxUp;
			this.maxDown = maxDown;
			return this;
		}

		/** Spring rate (1/s): 6 = heavy and slow, 14 = a bird's snap. */
		public Rig speed(float speed) {
			this.speed = speed;
			return this;
		}

		/** Blocks within which an awake mob with no target glances at the local player (0 = never). */
		public Rig glance(float blocks) {
			this.glance = blocks;
			return this;
		}

		public ModelPart[] chain() {
			return this.chain;
		}

		/** Another head of the same mob (its own smoothing): 0 for the main head, 1, 2 ... for more. */
		public Rig slot(int slot) {
			this.slot = slot;
			return this;
		}
	}

	/**
	 * Locks the rig's head on the mob's look target and returns the look (relative to the body) it aims with.
	 *
	 * @param weight    0..1, eased per mob: 0 lets the keyframes have the head (death, a stun, a roar)
	 * @param headYaw   vanilla's head yaw, used only when the mob has no target and nobody to glance at
	 * @param headPitch vanilla's head pitch, same
	 */
	public static Look apply(LivingEntity e, float age, float weight, Rig rig, float headYaw, float headPitch) {
		if (!CombatFx.isLive(e)) {
			return Look.NONE;   // a Codex portrait or a replay copy: the pose as keyframed
		}
		float partial = age - e.tickCount;
		float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
		Quaternionf anim = orientation(rig);                   // how the keyframes pose the head (model space)
		Vector3f animFwd = anim.transform(new Vector3f(0.0F, 0.0F, -1.0F));
		float animYaw = (float) Math.toDegrees(Math.atan2(-animFwd.x, -animFwd.z));
		float animPitch = (float) Math.toDegrees(Math.asin(Mth.clamp(animFwd.y, -1.0F, 1.0F)));
		float[] s = STATE.computeIfAbsent(((long) e.getId() << 3) | rig.slot, k -> {
			float[] n = new float[9];
			n[YAW] = bodyYaw + animYaw;
			n[PITCH] = animPitch;
			n[WEIGHT] = weight;
			n[AGE] = age;
			n[TARGET] = -1;
			return n;
		});
		float dt = Mth.clamp(age - s[AGE], 0.0F, 4.0F) / 20.0F;
		s[AGE] = age;
		s[SEEN] = (float) (System.currentTimeMillis() % 1_000_000L);

		// where to look: the world yaw / pitch from the eye to the target's eyes
		Vec3 at = target(e, partial, rig, s, age);
		float wantYaw, wantPitch;
		if (at != null) {
			Vec3 eye = ModelPoints.world(e, partial, rig.eyeX, rig.eyeY, rig.eyeZ, rig.chain);
			double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z;
			double flat = Math.sqrt(dx * dx + dz * dz);
			wantYaw = flat < 0.05 ? s[YAW] : (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			wantPitch = flat < 0.05 && Math.abs(dy) < 0.05 ? s[PITCH] : (float) -(Mth.atan2(dy, flat) * Mth.RAD_TO_DEG);
		} else {
			wantYaw = bodyYaw + Mth.wrapDegrees(headYaw);
			wantPitch = Mth.wrapDegrees(headPitch);
		}
		// within the neck's reach of the body; a target crossing straight behind keeps the shoulder the head is on
		float rel = Mth.wrapDegrees(wantYaw - bodyYaw);
		float relNow = Mth.wrapDegrees(s[YAW] - bodyYaw);
		if (Math.abs(rel) > rig.maxYaw && Math.signum(rel) != Math.signum(relNow) && Math.abs(relNow) > rig.maxYaw * 0.5F) {
			rel = Math.copySign(rig.maxYaw, relNow);
		}
		rel = Mth.clamp(rel, -rig.maxYaw, rig.maxYaw);
		wantYaw = bodyYaw + rel;
		wantPitch = Mth.clamp(wantPitch, -rig.maxUp, rig.maxDown);
		if (dt > 0.0F) {
			spring(s, YAW, YAW_V, s[YAW] + Mth.wrapDegrees(wantYaw - s[YAW]), rig.speed, dt);
			spring(s, PITCH, PITCH_V, wantPitch, rig.speed, dt);
			s[WEIGHT] += Mth.clamp(weight - s[WEIGHT], -dt * 3.0F, dt * 3.0F);
		}
		float lookYaw = Mth.clamp(Mth.wrapDegrees(s[YAW] - bodyYaw), -rig.maxYaw, rig.maxYaw);
		float lookPitch = s[PITCH];
		// the one rotation that takes the keyframed head to the look, faded by the weight, shared along the chain
		Quaternionf turn = aim(anim, animFwd, animYaw, animPitch, lookYaw, lookPitch);
		turn(rig, new Quaternionf().slerp(turn, Mth.clamp(s[WEIGHT], 0.0F, 1.0F)));
		prune();
		float w = s[WEIGHT];
		return new Look(lookYaw * w + animYaw * (1.0F - w), lookPitch * w + animPitch * (1.0F - w));
	}

	// ---- where to look --------------------------------------------------------------------------------------------

	/** The synced target (kept for {@link #STICKY} ticks after it drops), else the local player within glance range. */
	private static Vec3 target(LivingEntity e, float partial, Rig rig, float[] s, float age) {
		Entity t = null;
		if (e instanceof LookTarget lt) {
			int id = lt.lookTargetId();
			if (id >= 0) {
				t = e.level().getEntity(id);
				if (t != null && t.isAlive()) {
					s[TARGET] = id;
					s[TARGET_AGE] = age;
				} else {
					t = null;
				}
			}
			if (t == null && s[TARGET] >= 0 && age - s[TARGET_AGE] < STICKY) {
				t = e.level().getEntity((int) s[TARGET]);
				if (t != null && !t.isAlive()) {
					t = null;
				}
			}
		}
		if (t != null) {
			return t.getEyePosition(partial);
		}
		Player me = Minecraft.getInstance().player;
		if (rig.glance > 0.0F && me != null && !me.isSpectator() && me.level() == e.level()
			&& me.distanceToSqr(e) < rig.glance * rig.glance) {
			return me.getEyePosition(partial);
		}
		return null;
	}

	/** The posed head's rotation in model space (its facing is -Z of its own space): how the keyframes pose it. */
	private static Quaternionf orientation(Rig rig) {
		Quaternionf q = new Quaternionf();
		for (ModelPart p : rig.chain) {
			if (p != null) {
				q.mul(local(p));
			}
		}
		return q;
	}

	/**
	 * The model-space rotation that takes the keyframed head ({@code anim}, facing yaw / pitch {@code animYaw},
	 * {@code animPitch}) to face the look: the head's pose is split into yaw, pitch and the tilt left over, and the yaw and
	 * pitch are swapped for the look's - so a plain head turns exactly like vanilla's (no roll) and a keyframed tilt
	 * stays. A head keyframed (almost) straight up or down has no yaw to keep its tilt against: there it blends into the
	 * shortest arc, which turns the same facing and differs only by a roll about it.
	 */
	static Quaternionf aim(Quaternionf anim, Vector3f animFwd, float animYaw, float animPitch, float lookYaw, float lookPitch) {
		Quaternionf look = new Quaternionf().rotationY(lookYaw * Mth.DEG_TO_RAD).rotateX(lookPitch * Mth.DEG_TO_RAD);
		Quaternionf arc = new Quaternionf().rotationTo(animFwd, look.transform(new Vector3f(0.0F, 0.0F, -1.0F)));
		float vertical = Mth.clamp((Math.abs(animFwd.y) - 0.9F) / 0.08F, 0.0F, 1.0F);
		if (vertical >= 1.0F) {
			return arc;
		}
		Quaternionf tilt = new Quaternionf().rotationY(animYaw * Mth.DEG_TO_RAD).rotateX(animPitch * Mth.DEG_TO_RAD).conjugate().mul(anim);
		Quaternionf turn = look.mul(tilt).mul(new Quaternionf(anim).conjugate());
		return vertical <= 0.0F ? turn : turn.slerp(arc, vertical * vertical * (3.0F - 2.0F * vertical));
	}

	// ---- motion ---------------------------------------------------------------------------------------------------

	/** Critically damped spring, exact for any step (no overshoot, no blow-up on a long frame). */
	private static void spring(float[] s, int x, int v, float target, float omega, float dt) {
		float change = s[x] - target;
		float temp = (s[v] + omega * change) * dt;
		float exp = (float) Math.exp(-omega * dt);
		s[v] = (s[v] - omega * temp) * exp;
		s[x] = target + (change + temp) * exp;
	}

	private static void prune() {
		if (STATE.size() < 96) {
			return;
		}
		float now = (float) (System.currentTimeMillis() % 1_000_000L);
		STATE.values().removeIf(s -> Math.abs(now - s[SEEN]) > 10_000.0F);
	}

	// ---- turning the bones ----------------------------------------------------------------------------------------

	private static Quaternionf local(ModelPart p) {
		return new Quaternionf().rotationZYX(p.zRot, p.yRot, p.xRot);
	}

	/**
	 * Each link adds the step from the share of the turn its ancestors carry to its own cumulative share, as a rotation in
	 * model space about its own pivot; written back into its ZYX angles.
	 */
	static void turn(Rig rig, Quaternionf total) {
		Quaternionf parent = new Quaternionf();
		Quaternionf carried = new Quaternionf();
		float c = 0.0F;
		for (int i = 0; i < rig.chain.length; i++) {
			ModelPart p = rig.chain[i];
			if (p == null) {
				continue;
			}
			if (rig.share[i] != 0.0F) {
				c += rig.share[i];
				Quaternionf upTo = new Quaternionf().slerp(total, Math.min(1.0F, c));
				rotate(p, parent, new Quaternionf(upTo).mul(new Quaternionf(carried).conjugate()));
				carried = upTo;
			}
			parent.mul(local(p));
		}
	}

	/** Rotates {@code p} by {@code extra} (model space) about its pivot: local' = parent^-1 * extra * parent * local. */
	static void rotate(ModelPart p, Quaternionf parent, Quaternionf extra) {
		Quaternionf q = new Quaternionf(parent).conjugate().mul(extra).mul(parent).mul(local(p));
		// q = Rz * Ry * Rx; JOML 1.10.5's getEulerAnglesZYX is not the inverse of rotationZYX (off by up to 0.2 rad)
		float sy = Mth.clamp(-2.0F * (q.x * q.z - q.w * q.y), -1.0F, 1.0F);
		p.xRot = (float) Math.atan2(2.0F * (q.y * q.z + q.w * q.x), 1.0F - 2.0F * (q.x * q.x + q.y * q.y));
		p.yRot = (float) Math.asin(sy);
		p.zRot = (float) Math.atan2(2.0F * (q.x * q.y + q.w * q.z), 1.0F - 2.0F * (q.y * q.y + q.z * q.z));
	}
}
