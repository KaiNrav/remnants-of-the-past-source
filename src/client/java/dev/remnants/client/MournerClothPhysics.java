package dev.remnants.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Damped springs for the Mourner's cloth, one set per wearer. The cape (and its lagging lower half) and the loose
 * tail are blown back by forward speed, lifted by falling and swayed by strafing / turning, and never let a leg
 * swinging back through; the tabard is pushed ahead of a leg swinging forward; the robe's skirts and the sleeve bells
 * trail the limbs; the four soul charms under the mantles hang as pendulums (they counter the arm swing and lag
 * behind it); the cowl's peaks nod. A slow breath keeps everything moving while standing. Integrated on the render
 * thread once per frame per entity (the four armour slots of one entity share it).
 */
public final class MournerClothPhysics {
	private static final Map<LivingEntity, MournerClothPhysics> STATES = new WeakHashMap<>();

	private double last = Double.NaN;
	private Vec3 lastPos;
	private float lastYaw;
	/** Game time of the last update, in ticks (drives the breathing and the glow pulse). */
	private float time;
	private final Spring cape = new Spring(0.32F, 0.46F);
	private final Spring capeLower = new Spring(0.22F, 0.30F);
	private final Spring capeSide = new Spring(0.25F, 0.40F);
	private final Spring tail = new Spring(0.20F, 0.26F);
	private final Spring tabard = new Spring(0.30F, 0.42F);
	private final Spring skirt = new Spring(0.35F, 0.50F);
	private final Spring cuff = new Spring(0.30F, 0.40F);
	private final Spring charmX = new Spring(0.26F, 0.22F);
	private final Spring charmZ = new Spring(0.26F, 0.22F);
	private final Spring ears = new Spring(0.30F, 0.36F);

	private static final class Spring {
		final float k;
		final float c;
		float a;
		float v;

		Spring(float k, float c) {
			this.k = k;
			this.c = c;
		}

		void step(float target, float h) {
			this.v += (this.k * (target - this.a) - this.c * this.v) * h;
			this.a += this.v * h;
		}
	}

	public static MournerClothPhysics of(LivingEntity entity) {
		return STATES.computeIfAbsent(entity, e -> new MournerClothPhysics());
	}

	/** Advances the springs to this frame (no-op if this entity was already updated this frame). */
	public void update(LivingEntity entity, float partialTick, HumanoidModel<LivingEntity> ctx) {
		double t = entity.tickCount + partialTick;
		Vec3 pos = entity.getPosition(partialTick);
		float yaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
		if (Double.isNaN(this.last) || t - this.last > 10 || t < this.last) {
			this.time = (float) t;
			this.last = t;
			this.lastPos = pos;
			this.lastYaw = yaw;
			return;
		}
		this.time = (float) t;
		float dt = (float) (t - this.last);
		if (dt <= 0.0F) {
			return;
		}
		Vec3 vel = pos.subtract(this.lastPos).scale(1.0 / dt);
		float yawRate = Mth.wrapDegrees(yaw - this.lastYaw) / dt;
		this.last = t;
		this.lastPos = pos;
		this.lastYaw = yaw;

		float r = yaw * Mth.DEG_TO_RAD;
		float fx = -Mth.sin(r);
		float fz = Mth.cos(r);
		float forward = (float) (vel.x * fx + vel.z * fz);
		float side = (float) (vel.x * fz - vel.z * fx);
		float fall = (float) -vel.y;
		float breath = Mth.sin((float) t * 0.075F);

		float capeT = Mth.clamp(forward * 5.0F, -0.15F, 1.1F) + Mth.clamp(fall * 2.5F, -0.15F, 0.8F) + 0.03F * breath;
		float sideT = Mth.clamp(side * 3.0F - yawRate * 0.015F, -0.35F, 0.35F);
		float tailT = Mth.clamp(forward * 6.0F, -0.1F, 1.2F) + Mth.clamp(fall * 3.0F, -0.1F, 0.9F)
			+ 0.06F * Mth.sin((float) t * 0.09F + 0.7F);
		float tabardT = Mth.clamp(forward * 3.0F, -0.2F, 0.5F) + Mth.clamp(fall * 2.0F, -0.1F, 0.5F);
		float skirtT = Mth.clamp(forward * 2.5F, -0.1F, 0.4F);
		float cuffT = Mth.clamp(forward * 2.0F, -0.1F, 0.35F) + 0.02F * breath;
		float charmXT = Mth.clamp(forward * 4.0F, -0.4F, 0.9F) + Mth.clamp(fall * 2.0F, -0.2F, 0.6F);
		float charmZT = Mth.clamp(-side * 4.0F + yawRate * 0.012F, -0.7F, 0.7F);
		float earsT = Mth.clamp(forward * 2.5F, -0.1F, 0.4F) + 0.03F * breath;
		for (float left = dt; left > 0.0F; left -= 0.5F) {   // sub-steps keep the springs stable on slow frames
			float h = Math.min(0.5F, left);
			this.cape.step(capeT, h);
			this.capeLower.step(0.45F * this.cape.a + 0.04F * Mth.sin((float) t * 0.11F + 1.0F), h);
			this.capeSide.step(sideT, h);
			this.tail.step(tailT, h);
			this.tabard.step(tabardT, h);
			this.skirt.step(skirtT, h);
			this.cuff.step(cuffT, h);
			this.charmX.step(charmXT, h);
			this.charmZ.step(charmZT, h);
			this.ears.step(earsT, h);
		}
	}

	/** Brightness of the emissive pass for this wearer: a slow pulse with a faint flicker, 0.7..1. */
	public float glow() {
		return 0.85F + 0.12F * Mth.sin(this.time * 0.12F) + 0.04F * Mth.sin(this.time * 0.9F + 1.3F);
	}

	private static void reset(ModelPart... parts) {
		for (ModelPart p : parts) {
			p.resetPose();
		}
	}

	/** Poses the model's cloth parts on top of their rest pose (call after copying the wearer's pose). */
	public void apply(MournerArmorModel m, HumanoidModel<LivingEntity> ctx) {
		reset(m.cape, m.capeLower, m.tail, m.tabard, m.skirtRight, m.skirtLeft, m.cuffRight, m.cuffLeft,
			m.charmR0, m.charmR1, m.charmL0, m.charmL1, m.earRight, m.earLeft);

		// the cape and the tail never let a leg swinging back pass through them
		float legBack = Math.max(0.0F, Math.max(ctx.rightLeg.xRot, ctx.leftLeg.xRot));
		float crouch = ctx.crouching ? 0.3F : 0.0F;
		m.cape.xRot += Mth.clamp(Math.max(this.cape.a, legBack * 0.75F) + crouch, -0.2F, 1.4F);
		m.cape.zRot += this.capeSide.a;
		m.capeLower.xRot += Mth.clamp(this.capeLower.a, -0.3F, 0.8F);
		m.tail.xRot += Mth.clamp(Math.max(this.tail.a, legBack * 0.8F) + crouch, -0.2F, 1.5F);
		m.tail.zRot += this.capeSide.a * 1.4F + 0.05F * Mth.sin(this.time * 0.13F);
		// the tabard is pushed ahead of a leg swinging forward (negative xRot = its point moves forward)
		float legFwd = Math.min(0.0F, Math.min(ctx.rightLeg.xRot, ctx.leftLeg.xRot));
		m.tabard.xRot += Mth.clamp(Math.min(this.tabard.a, legFwd * 0.9F), -1.3F, 0.6F) - (ctx.crouching ? 0.4F : 0.0F);
		// skirts hang straighter than the legs (counter-swing) and flare back when moving
		m.skirtRight.xRot += -ctx.rightLeg.xRot * 0.35F + this.skirt.a;
		m.skirtLeft.xRot += -ctx.leftLeg.xRot * 0.35F + this.skirt.a;
		m.cuffRight.xRot += -ctx.rightArm.xRot * 0.3F + this.cuff.a;
		m.cuffLeft.xRot += -ctx.leftArm.xRot * 0.3F + this.cuff.a;
		// charms: pendulums hanging from the mantles - counter the arm's swing, lag behind the body
		float sway = 0.05F * Mth.sin(this.time * 0.17F);
		charm(m.charmR0, ctx.rightArm, sway);
		charm(m.charmR1, ctx.rightArm, -sway);
		charm(m.charmL0, ctx.leftArm, -sway);
		charm(m.charmL1, ctx.leftArm, sway);
		// the cowl's peaks fold back a little when running and nod with the breath
		m.earRight.xRot -= this.ears.a;
		m.earLeft.xRot -= this.ears.a * 0.9F;
	}

	private void charm(ModelPart charm, ModelPart arm, float sway) {
		charm.xRot += -arm.xRot * 0.85F + this.charmX.a * 0.6F;
		charm.zRot += -arm.zRot + this.charmZ.a + sway;
	}
}
