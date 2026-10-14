package dev.remnants.client.crypt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.remnants.RemnantsMod;
import dev.remnants.client.anim.BoneAnimations;
import dev.remnants.client.anim.SecondaryMotion;
import dev.remnants.client.fx.CombatFx;
import dev.remnants.client.fx.MeshFx;
import dev.remnants.client.fx.MobFx;
import dev.remnants.client.fx.ModelPoints;
import dev.remnants.entity.GraveMimicEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.remnants.entity.GraveMimicEntity.*;

/**
 * The Grave Mimic awake: the urn on four bone legs, its upper half a jaw hinged at the back over
 * rings of teeth, soul eyes inside, a forked tongue. Asleep GraveMimicRenderer draws the Ossuary Urn's block models
 * instead; the wake clip starts from the pot sitting on the ground with its legs folded.
 *
 * <p>Keyframed (animations/grave_mimic.json): idle + walk as the base, the current move's clip (whole poses) cross-faded
 * over {@link #FADE_TICKS} with a smoothstep. Art 2.0: the bite gapes then snaps on its damage tick (fitted to the
 * server's 10 ticks), a crouch before the leap, an additive hurt clip with a one-tick hit-stop, glints in the eyes and
 * splinters on the snap, the tongue's tip on a spring.
 */
public class GraveMimicModel extends EntityModel<GraveMimicEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(RemnantsMod.id("grave_mimic"), "main");
	private static final ResourceLocation ANIMS = RemnantsMod.id("grave_mimic");
	private static final float FADE_TICKS = 3.0F;

	private final ModelPart mesh;
	private final ModelPart root;
	private Map<String, ModelPart> parts = Map.of();
	private int partsVersion = Integer.MIN_VALUE;
	private final SecondaryMotion tongue = new SecondaryMotion()
		.link("tongue_tip", null, 0.22F, 0.18F, 1.6F, 2.4F, 0.03F, 1.2F, 0.0F, -0.6F, 0.9F, 0.6F);

	public GraveMimicModel(ModelPart model) {
		super(RenderType::entityCutoutNoCull);
		this.mesh = model;
		this.root = model.getChild("root");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(28, 28).addBox(-3.0F, -1.0F, -3.0F, 6.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(36, 16).addBox(-4.0F, -3.0F, -4.0F, 8.0F, 2.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-5.0F, -9.0F, -5.0F, 10.0F, 6.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(40, 0).addBox(-5.5F, -7.0F, -5.5F, 11.0F, 2.0F, 11.0F, new CubeDeformation(0.0F))
		.texOffs(98, 36).addBox(-3.5F, -10.5F, -4.0F, 7.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 42).addBox(-3.5F, -10.5F, 3.0F, 7.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 28).addBox(-4.0F, -10.5F, -3.0F, 1.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(14, 28).addBox(3.0F, -10.5F, -3.0F, 1.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(16, 42).addBox(1.0F, -10.75F, 1.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(22, 42).addBox(-3.0F, -10.75F, 1.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -6.0F, 0.0F));

		PartDefinition tongue = body.addOrReplaceChild("tongue", CubeListBuilder.create().texOffs(12, 36).addBox(-1.5F, -0.25F, -3.0F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -9.0F, 1.0F));

		PartDefinition tongue_tip = tongue.addOrReplaceChild("tongue_tip", CubeListBuilder.create().texOffs(96, 28).addBox(-1.5F, -0.5F, -5.0F, 3.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(28, 42).addBox(-1.5F, -0.5F, -7.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(34, 42).addBox(0.5F, -0.5F, -7.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.25F, -3.0F));

		PartDefinition lid = body.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 16).addBox(-4.5F, -3.0F, -9.0F, 9.0F, 3.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(40, 42).addBox(-3.0F, -0.75F, -8.75F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(112, 28).addBox(-4.25F, -0.75F, -7.0F, 1.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(0, 36).addBox(3.25F, -0.75F, -7.0F, 1.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(52, 28).addBox(-3.0F, -4.0F, -7.5F, 6.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(100, 16).addBox(-3.5F, -5.0F, -8.0F, 7.0F, 1.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(76, 28).addBox(-2.5F, -6.0F, -7.0F, 5.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(26, 36).addBox(-1.0F, -8.0F, -5.5F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(54, 42).addBox(1.0F, -2.0F, -9.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(60, 42).addBox(-3.0F, -2.0F, -9.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -9.0F, 4.5F));

		PartDefinition leg_front_right = body.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(34, 36).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-3.5F, -2.0F, -3.5F));

		PartDefinition femur_r1 = leg_front_right.addOrReplaceChild("femur_r1", CubeListBuilder.create().texOffs(68, 16).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.5F, -2.0F, -2.25F, 0.6126F, 0.0F, -0.8961F));

		PartDefinition leg_front_right_lower = leg_front_right.addOrReplaceChild("leg_front_right_lower", CubeListBuilder.create().texOffs(42, 36).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, -4.0F, -4.5F));

		PartDefinition tibia_r1 = leg_front_right_lower.addOrReplaceChild("tibia_r1", CubeListBuilder.create().texOffs(84, 0).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, 5.75F, -0.75F, 0.1292F, 0.0F, -3.0548F));

		PartDefinition leg_front_left = body.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(50, 36).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(3.5F, -2.0F, -3.5F));

		PartDefinition femur_r2 = leg_front_left.addOrReplaceChild("femur_r2", CubeListBuilder.create().texOffs(76, 16).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, -2.0F, -2.25F, 0.6126F, 0.0F, 0.8961F));

		PartDefinition leg_front_left_lower = leg_front_left.addOrReplaceChild("leg_front_left_lower", CubeListBuilder.create().texOffs(58, 36).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(5.0F, -4.0F, -4.5F));

		PartDefinition tibia_r2 = leg_front_left_lower.addOrReplaceChild("tibia_r2", CubeListBuilder.create().texOffs(88, 0).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 5.75F, -0.75F, 0.1292F, 0.0F, 3.0548F));

		PartDefinition leg_back_right = body.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(66, 36).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-3.5F, -2.0F, 3.5F));

		PartDefinition femur_r3 = leg_back_right.addOrReplaceChild("femur_r3", CubeListBuilder.create().texOffs(84, 16).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.5F, -2.0F, 2.25F, -0.6126F, 0.0F, -0.8961F));

		PartDefinition leg_back_right_lower = leg_back_right.addOrReplaceChild("leg_back_right_lower", CubeListBuilder.create().texOffs(74, 36).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, -4.0F, 4.5F));

		PartDefinition tibia_r3 = leg_back_right_lower.addOrReplaceChild("tibia_r3", CubeListBuilder.create().texOffs(92, 0).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, 5.75F, 0.75F, -0.1292F, 0.0F, -3.0548F));

		PartDefinition leg_back_left = body.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(82, 36).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(3.5F, -2.0F, 3.5F));

		PartDefinition femur_r4 = leg_back_left.addOrReplaceChild("femur_r4", CubeListBuilder.create().texOffs(92, 16).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, -2.0F, 2.25F, -0.6126F, 0.0F, 0.8961F));

		PartDefinition leg_back_left_lower = leg_back_left.addOrReplaceChild("leg_back_left_lower", CubeListBuilder.create().texOffs(90, 36).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(5.0F, -4.0F, 4.5F));

		PartDefinition tibia_r4 = leg_back_left_lower.addOrReplaceChild("tibia_r4", CubeListBuilder.create().texOffs(96, 0).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 5.75F, 0.75F, -0.1292F, 0.0F, 3.0548F));

		return LayerDefinition.create(meshdefinition, 128, 64);
	}

	static String clip(int anim) {
		return switch (anim) {
			case ANIM_WAKE -> "wake";
			case ANIM_BITE -> "bite";
			case ANIM_LEAP -> "leap";
			case ANIM_HOLD -> "hold";
			case ANIM_SPIT -> "spit";
			case ANIM_CROUCH -> "crouch";
			default -> null;
		};
	}

	@Override
	public void setupAnim(GraveMimicEntity e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
		this.root.getAllParts().forEach(ModelPart::resetPose);
		BoneAnimations anims = BoneAnimations.get(ANIMS);
		if (anims.version != this.partsVersion) {
			this.parts = anims.resolve(this.mesh);
			this.partsVersion = anims.version;
		}
		BoneAnimations.Pose pose = new BoneAnimations.Pose();
		float partial = age - e.tickCount;
		List<Object[]> events = new ArrayList<>();
		if (e.deathTime > 0) {
			float t = (e.deathTime + partial) / 20.0F;
			anims.add("death", t, 1.0F, pose);
			CombatFx.clipEvents(e, "move", anims, "death", t, (n, st) -> events.add(new Object[] {n, st}));
		} else {
			CombatFx.watchHurt(e, 1);
			float f0 = Mth.clamp((age - e.clientAnimChanged) / FADE_TICKS, 0.0F, 1.0F);
			float fade = f0 * f0 * (3.0F - 2.0F * f0);
			String cur = clip(e.getAnim());
			String prev = clip(e.clientPrevAnim);
			if (e.getAnim() == ANIM_WAKE) {
				fade = 1.0F;   // straight out of the urn: no fade from the sleeping block
				prev = null;
			}
			float wCur = cur == null ? 0.0F : fade;
			float wPrev = prev == null ? 0.0F : 1.0F - fade;
			float wBase = Math.max(0.0F, 1.0F - wCur - wPrev);
			if (wBase > 0.0F) {
				anims.add("idle", age / 20.0F, wBase, pose);
				anims.add("walk", age / 20.0F, Mth.clamp(limbSwingAmount * 3.0F, 0.0F, 1.0F) * wBase, pose);
			}
			if (prev != null) {
				anims.add(prev, seconds(e, e.clientPrevAnim, e.clientPrevAnimStart, age, anims, prev), wPrev, pose);
			}
			if (cur != null) {
				float t = seconds(e, e.getAnim(), e.clientAnimStart, age, anims, cur);
				anims.add(cur, t, wCur, pose);
				CombatFx.clipEvents(e, "move", anims, cur, t, (n, st) -> events.add(new Object[] {n, st}));
			}
			if (e.hurtTime > 0 && !e.isDormant()) {
				var src = e.getLastDamageSource();
				Vec3 at = src == null ? null : src.getSourcePosition();
				boolean front = at == null || Vec3.directionFromRotation(0, e.yBodyRot).dot(at.subtract(e.position()).multiply(1, 0, 1)) >= 0;
				anims.add(front ? "hurt_front" : "hurt_back", (e.hurtDuration - e.hurtTime + partial) / 20.0F, 1.0F, pose);
			}
		}
		pose.applyTo(this.parts);
		ModelPart body = this.root.getChild("body");
		this.tongue.apply(e, age, partial, body.xRot, body.yRot, e.deathTime > 0 ? 0.3F : 1.0F, this.parts);
		for (Object[] ev : events) {
			this.fire(e, partial, (String) ev[0], (Float) ev[1]);
		}
		this.ambient(e, age, partial, body);
		if (e.getAnim() == ANIM_WAKE && MeshFx.once(e, 0, e.clientAnimStart) && e.tickCount - e.clientAnimStart < 4) {
			MeshFx.burst(e.position().add(0, 0.6, 0), 0.9F);   // art 4.0: the urn bursts open
		}
	}

	/**
	 * Art 4.0: asleep, a soul ember slips out of the lid's seam once in a long while (the one tell for a sharp eye).
	 * Awake, soul drool drips from the jaw, its eyes leave afterimages, and every landing kicks up grave dust.
	 */
	private void ambient(GraveMimicEntity e, float age, float partial, ModelPart body) {
		if (e.deathTime > 0) {
			return;
		}
		var r = e.getRandom();
		if (e.isDormant()) {
			for (int i = MobFx.due(e, 0, age, 0.25F); i > 0; i--) {
				Vec3 p = MobFx.at(e, partial, (r.nextFloat() - 0.5F) * 8.0F, -9.0F, (r.nextFloat() - 0.5F) * 8.0F, this.root, body);
				MobFx.spawn(e, dev.remnants.registry.ModParticles.SOUL_EMBER, p, 0, 0.02, 0);
			}
			return;
		}
		for (int i = MobFx.due(e, 1, age, 1.4F); i > 0; i--) {
			MobFx.spawn(e, dev.remnants.registry.ModParticles.SOUL_DRIP, MobFx.at(e, partial, (r.nextFloat() - 0.5F) * 6.0F, -8.5F, -5.2F, this.root, body), 0, 0, 0);
		}
		for (int i = MobFx.due(e, 2, age, 16.0F); i > 0; i--) {
			float side = (i & 1) == 0 ? 2.0F : -2.0F;
			MobFx.spawn(e, dev.remnants.registry.ModParticles.EYE_TRAIL, MobFx.at(e, partial, side, -9.5F, -4.6F, this.root, body), 0, 0, 0);
		}
		if (MobFx.landed(e, 3)) {
			for (int i = 0; i < 6; i++) {
				MobFx.spawn(e, dev.remnants.registry.ModParticles.GRAVE_DUST, e.position().add(0, 0.1, 0), 0.6, 0.0, 0.02, 0.0, 0.1);
			}
		}
	}

	/** Bite, spit and the crouch are fitted to their server lengths; the rest run on their own clock. */
	private static float seconds(GraveMimicEntity e, int anim, int start, float age, BoneAnimations anims, String clip) {
		float elapsed = age - start - CombatFx.lag(e, start, age);
		int len = anim == ANIM_BITE ? 10 : anim == ANIM_SPIT ? 14 : anim == ANIM_CROUCH ? CROUCH_TICKS : anim == ANIM_WAKE ? 8 : 0;
		return len > 0 ? Mth.clamp(elapsed / len, 0.0F, 1.0F) * anims.length(clip) : elapsed / 20.0F;
	}

	/** Clip events: a glint in the eyes as it winds up, bone splinters as the jaw snaps, dust when it lands dead. */
	private void fire(GraveMimicEntity e, float partial, String name, float strength) {
		ModelPart body = this.root.getChild("body");
		switch (name) {
			case "glint_eyes" -> CombatFx.glint(ModelPoints.world(e, partial, 0.0F, -9.0F, -4.0F, this.root, body), strength * 0.7F);
			case "snap" -> {
				Vec3 m = ModelPoints.world(e, partial, 0.0F, -9.0F, -5.0F, this.root, body);
				e.level().playLocalSound(m.x, m.y, m.z, dev.remnants.registry.ModSounds.MIMIC_SNAP, net.minecraft.sounds.SoundSource.HOSTILE,
					0.5F + 0.6F * strength, 0.9F + e.getRandom().nextFloat() * 0.2F, false);
				for (int i = 0; i < 3 + Math.round(4 * strength); i++) {
					e.level().addParticle(dev.remnants.registry.ModParticles.BONE_SPARK, m.x, m.y, m.z, (e.getRandom().nextDouble() - 0.5) * 0.4, e.getRandom().nextDouble() * 0.25,
						(e.getRandom().nextDouble() - 0.5) * 0.4);
				}
			}
			case "land" -> {
				for (int i = 0; i < 8; i++) {
					e.level().addParticle(ParticleTypes.POOF, e.getX(), e.getY() + 0.1, e.getZ(), (e.getRandom().nextDouble() - 0.5) * 0.15, 0.02,
						(e.getRandom().nextDouble() - 0.5) * 0.15);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
		this.root.render(pose, buffer, light, overlay, color);
	}
}
