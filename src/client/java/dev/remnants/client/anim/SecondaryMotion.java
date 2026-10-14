package dev.remnants.client.anim;

import dev.remnants.client.fx.CombatFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Cloth that follows the body: damped springs on hanging bones - capes, tabards,
 * skirt flaps, plumes - added on top of the keyframes. Generalised from MournerClothPhysics.
 *
 * <p>Each link swings about X (back / forward) and Z (sideways), in radians, toward a target made of: the body's forward
 * and vertical speed, its turn rate, and the <b>lag of its parent</b> - the link keeps the parent's pitch / twist of a
 * moment ago and catches up, so a slam that pitches the torso forward leaves the cape behind and then flings it. A
 * link with a parent link also follows that link's swing ({@code follow}), slower, so two-segment cloth bends.
 * Sub-stepped at half a tick; one state per entity; nothing happens for off-world copies (Codex portraits).
 */
public final class SecondaryMotion {
	private final List<Link> links = new ArrayList<>();
	private final Map<Integer, State> states = new HashMap<>();
	private float windAmp, windRate, extraSpeed, extraTurn;

	/**
	 * @param k        spring stiffness (per tick^2); 0.15-0.4 for cloth
	 * @param c        damping (per tick); 0.25-0.5
	 * @param move     X swing per block/tick of forward speed (the cloth trails back)
	 * @param fall     X swing per block/tick of falling
	 * @param turn     Z swing per degree/tick of turning
	 * @param lag      how much of the parent's pitch / twist change the link keeps (0..1)
	 * @param follow   share of the parent link's swing added to the target (0 for a top link)
	 */
	public SecondaryMotion link(String bone, String parent, float k, float c, float move, float fall, float turn, float lag, float follow,
		float minX, float maxX, float maxZ) {
		this.links.add(new Link(bone, parent, k, c, move, fall, turn, lag, follow, minX, maxX, maxZ, new float[3], new String[1]));
		return this;
	}

	/**
	 * The last link also trails its <b>own parent bone's</b> keyframed swing (e.g. a strip of wing cloth behind the wing
	 * spar it hangs from): it keeps the bone's X / Z rotation of a moment ago ({@code gain}) and turns the bone's Y swing
	 * into an X swing ({@code gainY}, signed by side).
	 */
	public SecondaryMotion anchor(String bone, float gain, float gainY) {
		Link l = this.links.get(this.links.size() - 1);
		l.anchor[0] = bone;
		l.gains[0] = gain;
		l.gains[1] = gainY;
		return this;
	}

	/** A slow gust on every link ({@code amp} radians, {@code rate} radians per tick), each link out of phase. */
	public SecondaryMotion wind(float amp, float rate) {
		this.windAmp = amp;
		this.windRate = rate;
		return this;
	}

	/** Extra forward speed (blocks/tick) and turn rate (degrees/tick) for the next {@link #apply}, for motion the entity
	 * does not really make (a spin drawn by the renderer). */
	public void drive(float speed, float turn) {
		this.extraSpeed = speed;
		this.extraTurn = turn;
	}

	/**
	 * Advances the springs to {@code age} and adds the swings to the parts. {@code pitch} / {@code twist} are the current
	 * total X / Y rotations (Java radians) of whatever the cloth hangs from (e.g. body + torso), read after the keyframes;
	 * {@code calm} 0..1 fades the swings out (death, statue).
	 */
	public void apply(LivingEntity e, float age, float partial, float pitch, float twist, float calm, Map<String, ModelPart> parts) {
		if (!CombatFx.isLive(e)) {
			return;
		}
		State s = this.states.computeIfAbsent(e.getId(), id -> new State(this.links.size()));
		double px = Mth.lerp(partial, e.xo, e.getX()), py = Mth.lerp(partial, e.yo, e.getY()), pz = Mth.lerp(partial, e.zo, e.getZ());
		float yaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
		if (!s.started || age < s.age || age - s.age > 10.0F) {
			s.reset(age, px, py, pz, yaw, pitch, twist);
			if (this.states.size() > 64) {
				this.states.keySet().removeIf(id -> e.level().getEntity(id) == null);
			}
		}
		float dt = Math.min(2.0F, age - s.age);
		if (dt > 1.0E-4F) {
			double vx = (px - s.px) / dt, vy = (py - s.py) / dt, vz = (pz - s.pz) / dt;
			float fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
			float forward = (float) (vx * fx + vz * fz) + this.extraSpeed;
			float turnRate = Mth.wrapDegrees(yaw - s.yaw) / dt + this.extraTurn;
			float[][] anchors = new float[this.links.size()][];
			for (int i = 0; i < this.links.size(); i++) {
				String a = this.links.get(i).anchor[0];
				ModelPart ap = a == null ? null : parts.get(a);
				anchors[i] = ap == null ? null : new float[] {ap.xRot, ap.yRot, ap.zRot};
				if (anchors[i] != null && !s.anchored[i]) {
					s.anchored[i] = true;
					System.arraycopy(anchors[i], 0, s.lagA[i], 0, 3);
				}
			}
			for (float left = dt; left > 1.0E-4F; left -= 0.5F) {
				float h = Math.min(0.5F, left);
				// the parent's lagged pitch / twist chase the real ones
				s.lagPitch += (pitch - s.lagPitch) * Math.min(1.0F, 0.35F * h);
				s.lagTwist += (twist - s.lagTwist) * Math.min(1.0F, 0.35F * h);
				for (int i = 0; i < this.links.size(); i++) {
					Link l = this.links.get(i);
					int pi = l.parentIndex(this.links);
					float tx = l.move * forward - l.fall * (float) Math.min(0.0, vy) + l.lag * (s.lagPitch - pitch);
					float tz = -l.turn * turnRate + l.lag * (s.lagTwist - twist) * 0.6F;
					if (anchors[i] != null) {
						float[] la = s.lagA[i], cur = anchors[i];
						for (int j = 0; j < 3; j++) {
							la[j] += (cur[j] - la[j]) * Math.min(1.0F, 0.3F * h);
						}
						tx += l.gains[0] * (la[0] - cur[0]) + l.gains[1] * (la[1] - cur[1]);
						tz += l.gains[0] * (la[2] - cur[2]);
					}
					if (this.windAmp > 0.0F) {
						float w = (age - left + h) * this.windRate + i * 1.7F;
						tx += this.windAmp * (Mth.sin(w) + 0.5F * Mth.sin(w * 2.3F + 1.1F));
						tz += this.windAmp * 0.6F * Mth.sin(w * 1.3F + i * 0.9F);
					}
					if (pi >= 0) {
						tx += l.follow * s.ax[pi];
						tz += l.follow * s.az[pi];
					}
					tx = Mth.clamp(tx * calm, l.minX, l.maxX);
					tz = Mth.clamp(tz * calm, -l.maxZ, l.maxZ);
					s.vx[i] += (l.k * (tx - s.ax[i]) - l.c * s.vx[i]) * h;
					s.vz[i] += (l.k * (tz - s.az[i]) - l.c * s.vz[i]) * h;
					s.ax[i] = Mth.clamp(s.ax[i] + s.vx[i] * h, l.minX - 0.2F, l.maxX + 0.2F);
					s.az[i] = Mth.clamp(s.az[i] + s.vz[i] * h, -l.maxZ - 0.2F, l.maxZ + 0.2F);
				}
			}
			this.extraSpeed = 0.0F;
			this.extraTurn = 0.0F;
			s.age = age;
			s.px = px;
			s.py = py;
			s.pz = pz;
			s.yaw = yaw;
		}
		for (int i = 0; i < this.links.size(); i++) {
			ModelPart p = parts.get(this.links.get(i).bone);
			if (p != null) {
				p.xRot += s.ax[i];
				p.zRot += s.az[i];
			}
		}
	}

	private record Link(String bone, String parent, float k, float c, float move, float fall, float turn, float lag, float follow,
		float minX, float maxX, float maxZ, float[] gains, String[] anchor) {
		int parentIndex(List<Link> all) {
			if (this.parent == null) {
				return -1;
			}
			for (int i = 0; i < all.size(); i++) {
				if (all.get(i).bone.equals(this.parent)) {
					return i;
				}
			}
			return -1;
		}
	}

	private static final class State {
		final float[] ax, az, vx, vz;
		final float[][] lagA;
		final boolean[] anchored;
		boolean started;
		float age, yaw, lagPitch, lagTwist;
		double px, py, pz;

		State(int n) {
			this.ax = new float[n];
			this.az = new float[n];
			this.vx = new float[n];
			this.vz = new float[n];
			this.lagA = new float[n][3];
			this.anchored = new boolean[n];
		}

		void reset(float age, double px, double py, double pz, float yaw, float pitch, float twist) {
			this.started = true;
			this.age = age;
			this.px = px;
			this.py = py;
			this.pz = pz;
			this.yaw = yaw;
			this.lagPitch = pitch;
			this.lagTwist = twist;
		}
	}
}
