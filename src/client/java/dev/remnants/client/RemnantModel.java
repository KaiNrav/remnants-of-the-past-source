package dev.remnants.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.remnants.client.anim.HeadLook;
import dev.remnants.entity.RemnantEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The player model drawn as a translucent ghost whose tint follows the fight (cold blue, violet in phase 2, red
 * when enraged, purple when Empowered), with boss poses for every telegraph: overhead raise, nova arms, parry
 * stance, charge, cast, roar and the stunned slump.
 */
public class RemnantModel extends PlayerModel<RemnantEntity> {
	private int tint = 0xB2AAE1FF;
	private final HeadLook.Rig look;

	public RemnantModel(ModelPart root, boolean slim) {
		super(root, slim);
		this.look = new HeadLook.Rig(this.head).eye(0.0F, -4.0F, -4.0F).limits(75.0F, 50.0F, 55.0F).speed(10.0F).glance(0.0F);
	}

	/** ARGB tint for the ghost body (also used for the crown and orbs). */
	public static int tintOf(RemnantEntity e) {
		int alpha = e.isIllusion() ? 0x90 : 0xB2;
		int rgb;
		if (e.isForgotten()) {
			// faded by the centuries: bone-grey with a cold green-blue cast (or the colour of their land); Crowned ones
			// keep a violet glow; a Frozen one goes duller once its crust is broken
			rgb = e.isEmpowered() ? 0xC8B4E6 : e.getVariant() == dev.remnants.entity.ForgottenVariant.FROZEN && !e.hasCrust() ? 0xAAC4D8
				: e.getVariant().tint;
			alpha = 0x9C;
		} else if (e.getPhase() >= 3) {
			rgb = 0xFF7890;
		} else if (e.getPhase() == 2) {
			rgb = 0xC8A0FF;
		} else if (e.isEmpowered()) {
			rgb = 0xDC96FF;
		} else {
			rgb = 0xAAE1FF;
		}
		return (alpha << 24) | rgb;
	}

	@Override
	public void prepareMobModel(RemnantEntity entity, float limbSwing, float limbSwingAmount, float partialTick) {
		this.tint = tintOf(entity);
		this.rightArmPose = HumanoidModel.ArmPose.EMPTY;
		this.leftArmPose = HumanoidModel.ArmPose.EMPTY;
		ItemStack main = entity.getMainHandItem();
		boolean aiming = entity.isAggressive() || entity.getAnim() == RemnantEntity.ANIM_AIM;
		if (main.is(Items.BOW) && aiming) {
			this.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
		} else if (main.getItem() instanceof CrossbowItem && aiming) {
			this.rightArmPose = entity.isUsingItem() ? HumanoidModel.ArmPose.CROSSBOW_CHARGE : HumanoidModel.ArmPose.CROSSBOW_HOLD;
		} else if (!main.isEmpty()) {
			this.rightArmPose = HumanoidModel.ArmPose.ITEM;
		}
		if (entity.isShieldUp()) {
			this.leftArmPose = HumanoidModel.ArmPose.BLOCK;
		} else if (!entity.getOffhandItem().isEmpty()) {
			this.leftArmPose = HumanoidModel.ArmPose.ITEM;
		}
		super.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
	}

	@Override
	public void setupAnim(RemnantEntity e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
		super.setupAnim(e, limbSwing, limbSwingAmount, age, headYaw, headPitch);
		// vanilla never resets the head's roll, and every Remnant (and the Codex portrait) shares this model: a roll left
		// by one frame (the stunned sway, a look) would carry into the next and pile up until the head lay on the shoulder
		this.head.zRot = 0.0F;
		// the head aims at the synced target from the frame's own positions (vanilla's head yaw lags the body's by a
		// different number of ticks, so a circling Remnant's head wobbled); vanilla's look stays the fallback
		if (dev.remnants.client.fx.CombatFx.isLive(e)) {
			this.head.yRot = 0.0F;
			this.head.xRot = 0.0F;
			boolean free = e.deathTime <= 0 && !e.isEmerging() && e.getSinkTicks() <= 0 && e.getAnim() != RemnantEntity.ANIM_STUNNED;
			HeadLook.apply(e, age, free ? 1.0F : 0.0F, this.look, headYaw, headPitch);
		}
		// art 2.0: the boss poses below no longer snap in on one frame - the change each makes to vanilla's walking pose
		// eases in and out on a spring per Remnant (a little overshoot reads as weight); walking itself stays crisp
		float[] base = this.snapshot();
		this.bossPose(e, age);
		float[] target = this.snapshot();
		this.smooth(e, age, base, target);
		this.hitReaction(e, age);
		this.ambient(e, age);
		this.hat.copyFrom(this.head);
		this.rightSleeve.copyFrom(this.rightArm);
		this.leftSleeve.copyFrom(this.leftArm);
		this.jacket.copyFrom(this.body);
		this.rightPants.copyFrom(this.rightLeg);
		this.leftPants.copyFrom(this.leftLeg);
	}

	/**
	 * Art 4.0 on top of the soul trail (RemnantEntity.spawnTrail): from phase 2 the eyes burn and leave afterimages; the
	 * Forgotten carry their land with them - the Drowned drip, the Frozen shed frost, the Sandy shed dust.
	 */
	private void ambient(RemnantEntity e, float age) {
		if (dev.remnants.client.fx.MeshFx.changed(e, 1, e.isEmerging() ? 1 : 0) && e.isEmerging()) {
			dev.remnants.client.fx.MeshFx.pillar(e.position(), 0.6F, 3.5F, 1.2F);   // art 4.0: a soul climbing out
		}
		if (e.deathTime > 0 || e.isEmerging() || e.isIllusion()) {
			return;
		}
		if (dev.remnants.client.fx.MeshFx.changed(e, 0, e.getAnim())) {
			switch (e.getAnim()) {
				case RemnantEntity.ANIM_RAISE, RemnantEntity.ANIM_NOVA, RemnantEntity.ANIM_CAST ->
					dev.remnants.client.fx.MeshFx.runeCircle(e, 2.2F, 1.6F);
				case RemnantEntity.ANIM_ROAR -> dev.remnants.client.fx.MeshFx.shockwave(e.position(), 4.0F);
				default -> {
				}
			}
		}
		float partial = age - e.tickCount;
		if (e.getPhase() >= 2) {
			for (int i = dev.remnants.client.fx.MobFx.due(e, 0, age, e.getPhase() >= 3 ? 24.0F : 14.0F); i > 0; i--) {
				float side = (i & 1) == 0 ? 2.0F : -2.0F;
				dev.remnants.client.fx.MobFx.spawn(e, dev.remnants.registry.ModParticles.EYE_TRAIL,
					dev.remnants.client.fx.MobFx.at(e, partial, side, -4.0F, -4.3F, this.head), 0, 0, 0);
			}
		}
		if (!e.isForgotten()) {
			return;
		}
		var p = switch (e.getVariant()) {
			case DROWNED -> net.minecraft.core.particles.ParticleTypes.DRIPPING_WATER;
			case FROZEN -> net.minecraft.core.particles.ParticleTypes.SNOWFLAKE;
			case SANDY -> dev.remnants.registry.ModParticles.GRAVE_DUST;
			default -> dev.remnants.registry.ModParticles.SOUL_WISP;
		};
		for (int i = dev.remnants.client.fx.MobFx.due(e, 1, age, 2.0F); i > 0; i--) {
			var r = e.getRandom();
			dev.remnants.client.fx.MobFx.spawn(e, p, e.position().add((r.nextDouble() - 0.5) * 0.6, 0.2 + r.nextDouble() * 1.6,
				(r.nextDouble() - 0.5) * 0.6), 0, 0, 0);
		}
	}

	private ModelPart[] posed() {
		return new ModelPart[] {this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg};
	}

	private float[] snapshot() {
		ModelPart[] ps = this.posed();
		float[] v = new float[ps.length * 3];
		for (int i = 0; i < ps.length; i++) {
			v[i * 3] = ps[i].xRot;
			v[i * 3 + 1] = ps[i].yRot;
			v[i * 3 + 2] = ps[i].zRot;
		}
		return v;
	}

	/** Per Remnant: the pose offset on top of vanilla and its velocity, chasing the boss pose's offset. */
	private static final java.util.Map<Integer, float[]> SPRINGS = new java.util.HashMap<>();

	private void smooth(RemnantEntity e, float age, float[] base, float[] target) {
		if (!dev.remnants.client.fx.CombatFx.isLive(e)) {
			return;   // a Codex portrait or a decoy copy: show the pose as it is
		}
		int n = base.length;
		float[] st = SPRINGS.computeIfAbsent(e.getId(), id -> {
			float[] f = new float[n * 2 + 1];
			for (int i = 0; i < n; i++) {
				f[i] = target[i] - base[i];
			}
			f[n * 2] = age;
			return f;
		});
		if (SPRINGS.size() > 128) {
			SPRINGS.keySet().removeIf(id -> e.level().getEntity(id) == null);
		}
		float dt = Mth.clamp(age - st[n * 2], 0.0F, 2.0F);
		st[n * 2] = age;
		for (float left = dt; left > 1.0E-4F; left -= 0.5F) {
			float h = Math.min(0.5F, left);
			for (int i = 0; i < n; i++) {
				float goal = Mth.wrapDegrees((target[i] - base[i]) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
				st[n + i] += (0.55F * (goal - st[i]) - 0.95F * st[n + i]) * h;   // stiff, a touch under-damped
				st[i] += st[n + i] * h;
			}
		}
		ModelPart[] ps = this.posed();
		for (int i = 0; i < ps.length; i++) {
			ps[i].xRot = base[i * 3] + st[i * 3];
			ps[i].yRot = base[i * 3 + 1] + st[i * 3 + 1];
			ps[i].zRot = base[i * 3 + 2] + st[i * 3 + 2];
		}
	}

	/** A one-tick hit-stop, then the body rocks away from the blow and the head snaps after it. */
	private void hitReaction(RemnantEntity e, float age) {
		dev.remnants.client.fx.CombatFx.watchHurt(e, 1);
		if (e.hurtTime <= 0 || e.deathTime > 0) {
			return;
		}
		float t = (e.hurtDuration - e.hurtTime) + (age - e.tickCount);
		float k = t < 1.5F ? t / 1.5F : Math.max(0.0F, 1.0F - (t - 1.5F) / 6.0F);
		var src = e.getLastDamageSource();
		net.minecraft.world.phys.Vec3 at = src == null ? null : src.getSourcePosition();
		float dir = at == null || net.minecraft.world.phys.Vec3.directionFromRotation(0, e.yBodyRot)
			.dot(at.subtract(e.position()).multiply(1, 0, 1)) >= 0 ? -1.0F : 1.0F;
		this.body.xRot += 0.18F * k * dir;
		this.head.xRot += 0.3F * k * dir;
		this.rightArm.xRot += 0.25F * k * dir;
		this.leftArm.xRot += 0.25F * k * dir;
	}

	/** The pose of the current boss move, written over vanilla's (smoothed afterwards). */
	private void bossPose(RemnantEntity e, float age) {
		if (e.isEmerging() || e.getSinkTicks() > 0) {
			this.groundPose(e, age);
			return;
		}
		switch (e.getAnim()) {
			case RemnantEntity.ANIM_RAISE -> {
				this.rightArm.xRot = -2.9F;
				this.rightArm.zRot = 0.1F;
				this.leftArm.xRot = -2.6F;
				this.leftArm.zRot = -0.1F;
			}
			case RemnantEntity.ANIM_NOVA -> {
				this.rightArm.xRot = -0.2F;
				this.leftArm.xRot = -0.2F;
				this.rightArm.zRot = 2.3F + Mth.sin(age * 0.6F) * 0.1F;
				this.leftArm.zRot = -2.3F - Mth.sin(age * 0.6F) * 0.1F;
				this.head.xRot = -0.4F;
			}
			case RemnantEntity.ANIM_STANCE -> {
				this.rightArm.xRot = -1.2F;
				this.rightArm.yRot = -0.7F;
				this.leftArm.xRot = -1.2F;
				this.leftArm.yRot = 0.7F;
			}
			case RemnantEntity.ANIM_CHARGE -> {
				this.rightArm.xRot = 1.1F;
				this.leftArm.xRot = 1.1F;
				this.head.xRot = 0.3F;
			}
			case RemnantEntity.ANIM_STUNNED -> {
				this.head.xRot = 0.7F;
				this.head.zRot = Mth.sin(age * 0.3F) * 0.2F;
				this.rightArm.xRot = 0.2F;
				this.rightArm.zRot = 0.15F;
				this.leftArm.xRot = 0.2F;
				this.leftArm.zRot = -0.15F;
			}
			case RemnantEntity.ANIM_CAST -> {
				this.rightArm.xRot = -1.7F + this.head.xRot;
				this.rightArm.yRot = this.head.yRot;
				this.leftArm.zRot = -0.5F;
			}
			case RemnantEntity.ANIM_DORMANT -> {
				// kneeling where its owner fell, head bowed, hands on the ground (the renderer lowers the body)
				this.head.xRot = 0.85F + Mth.sin(age * 0.05F) * 0.04F;
				this.rightArm.xRot = -0.55F;
				this.rightArm.zRot = 0.12F;
				this.leftArm.xRot = -0.55F;
				this.leftArm.zRot = -0.12F;
				this.rightLeg.xRot = -1.45F;
				this.rightLeg.yRot = 0.2F;
				this.leftLeg.xRot = -1.45F;
				this.leftLeg.yRot = -0.2F;
				this.rightPants.copyFrom(this.rightLeg);
				this.leftPants.copyFrom(this.leftLeg);
			}
			case RemnantEntity.ANIM_WINDUP -> {
				// weapon drawn back over the shoulder: the strike lands in a moment
				this.rightArm.xRot = -2.5F + Mth.sin(age * 2.6F) * 0.04F;
				this.rightArm.yRot = -0.35F;
				this.rightArm.zRot = 0.25F;
				this.leftArm.xRot = -0.6F;
				this.leftArm.yRot = 0.5F;
				this.body.yRot = -0.25F;
				this.jacket.copyFrom(this.body);
			}
			case RemnantEntity.ANIM_RECOVER -> {
				// catching its breath: hunched, arms slack, shoulders heaving
				float breath = Mth.sin(age * 0.35F) * 0.08F;
				this.head.xRot = 0.55F + breath;
				this.rightArm.xRot = 0.35F + breath;
				this.rightArm.zRot = 0.2F;
				this.leftArm.xRot = 0.35F + breath;
				this.leftArm.zRot = -0.2F;
			}
			case RemnantEntity.ANIM_ROAR -> {
				this.head.xRot = -0.6F;
				this.rightArm.xRot = -0.3F;
				this.leftArm.xRot = -0.3F;
				this.rightArm.zRot = 1.2F;
				this.leftArm.zRot = -1.2F;
			}
			default -> {
			}
		}
	}

	/** Climbing out of the ground: one hand clawing up first, then both arms dragging it out, then a lunge. Diving in to burrow: arms up. */
	private void groundPose(RemnantEntity e, float age) {
		float p = e.isEmerging() ? e.emergeProgress(age - e.tickCount) : -1.0F;
		if (p < 0.0F) {
			this.rightArm.xRot = -2.9F;
			this.leftArm.xRot = -2.9F;
			this.rightArm.zRot = 0.2F;
			this.leftArm.zRot = -0.2F;
			this.head.xRot = 0.4F;
		} else if (p < 0.35F) {
			this.rightArm.xRot = -2.9F + Mth.sin(age * 0.9F) * 0.15F;
			this.rightArm.zRot = 0.15F;
			this.leftArm.xRot = -0.3F;
			this.head.xRot = -0.5F;
		} else if (p < 0.7F) {
			this.rightArm.xRot = -2.3F + Mth.sin(age * 0.8F) * 0.3F;
			this.leftArm.xRot = -2.3F - Mth.sin(age * 0.8F) * 0.3F;
			this.rightArm.zRot = 0.3F;
			this.leftArm.zRot = -0.3F;
			this.head.xRot = -0.35F;
		} else {
			this.rightArm.xRot = -1.5F;
			this.leftArm.xRot = -1.5F;
			this.head.xRot = 0.1F;
		}
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
		super.renderToBuffer(poseStack, buffer, light, overlay, FastColor.ARGB32.multiply(color, this.tint));
	}
}
