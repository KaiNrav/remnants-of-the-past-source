package dev.remnants.client.archive;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.remnants.RemnantsMod;
import dev.remnants.client.anim.BoneAnimations;
import dev.remnants.client.anim.HeadLook;
import dev.remnants.client.fx.MobFx;
import dev.remnants.entity.CarrionCrowEntity;
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

import java.util.Map;

/**
 * A Carrion Crow: flies, glides, dives and perches; keyframed (animations/carrion_crow.json). Perched,
 * it cocks its head after whoever comes near (HeadLook, a bird's quick snap).
 */
public class CarrionCrowModel extends EntityModel<CarrionCrowEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(RemnantsMod.id("carrion_crow"), "main");
	private static final ResourceLocation ANIMS = RemnantsMod.id("carrion_crow");

	private final ModelPart mesh;
	private final ModelPart root;
	private final HeadLook.Rig look;
	private final ModelPart[] head;
	private Map<String, ModelPart> parts = Map.of();
	private int partsVersion = Integer.MIN_VALUE;

	public CarrionCrowModel(ModelPart model) {
		super(RenderType::entityCutoutNoCull);
		this.mesh = model;
		this.root = model.getChild("root");
		ModelPart body = this.root.getChild("body");
		this.head = new ModelPart[] {this.root, body, body.getChild("head")};
		this.look = new HeadLook.Rig(this.root, body, body.getChild("head")).eye(0.0F, -1.5F, -2.5F).limits(100.0F, 40.0F, 50.0F)
			.speed(16.0F).glance(8.0F);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -3.0F, -3.0F, 5.0F, 5.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(0, 19).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(34, 33).addBox(-1.5F, -0.5F, -3.5F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(14, 42).addBox(-2.0F, 0.0F, -4.0F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(24, 42).addBox(-2.0F, -1.5F, -4.0F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(34, 42).addBox(-0.5F, -0.5F, -3.75F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.05F)), PartPose.offset(0.0F, -6.0F, 0.0F));

		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(24, 0).addBox(-2.0F, -3.0F, -3.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(38, 42).addBox(-2.75F, -1.5F, -2.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(42, 42).addBox(1.75F, -1.5F, -2.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(16, 26).addBox(-1.0F, -1.5F, -7.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(12, 33).addBox(-0.5F, 0.5F, -6.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(46, 42).addBox(-0.5F, -0.5F, -8.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(28, 26).addBox(-2.5F, -1.5F, 0.0F, 5.0F, 3.0F, 2.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, -3.0F, -3.0F));

		PartDefinition crest_r1 = head.addOrReplaceChild("crest_r1", CubeListBuilder.create().texOffs(20, 33).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, 0.9599F, 0.0F, 0.0F));

		PartDefinition wing_right = body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(0, 12).addBox(-7.0F, -0.5F, -2.0F, 7.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.5F, -2.5F, 0.0F));

		PartDefinition arm_r1 = wing_right.addOrReplaceChild("arm_r1", CubeListBuilder.create().texOffs(40, 0).addBox(-0.5F, -3.5F, -0.5F, 1.0F, 7.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.25F, 0.0F, -0.5F, -0.1526F, 0.0F, -1.5708F));

		PartDefinition wing_right_tip = wing_right.addOrReplaceChild("wing_right_tip", CubeListBuilder.create().texOffs(42, 26).addBox(-2.0F, -0.5F, -1.5F, 2.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-7.0F, 0.0F, 0.0F));

		PartDefinition primary3_r1 = wing_right_tip.addOrReplaceChild("primary3_r1", CubeListBuilder.create().texOffs(50, 40).addBox(-5.0F, 0.0F, -1.0F, 5.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 0.0F, 3.0F, 0.0F, -0.3142F, 0.0F));

		PartDefinition primary2_r1 = wing_right_tip.addOrReplaceChild("primary2_r1", CubeListBuilder.create().texOffs(18, 40).addBox(-6.0F, 0.0F, -1.0F, 6.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 0.0F, 1.5F, 0.0F, -0.1396F, 0.0F));

		PartDefinition primary1_r1 = wing_right_tip.addOrReplaceChild("primary1_r1", CubeListBuilder.create().texOffs(18, 38).addBox(-7.0F, 0.0F, -1.0F, 7.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 0.0F, 0.0F, 0.0F, 0.0349F, 0.0F));

		PartDefinition primary0_r1 = wing_right_tip.addOrReplaceChild("primary0_r1", CubeListBuilder.create().texOffs(0, 38).addBox(-7.0F, 0.0F, -1.0F, 7.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 0.0F, -1.5F, 0.0F, 0.2094F, 0.0F));

		PartDefinition wing_left = body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(26, 12).addBox(0.0F, -0.5F, -2.0F, 7.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(2.5F, -2.5F, 0.0F));

		PartDefinition arm_r2 = wing_left.addOrReplaceChild("arm_r2", CubeListBuilder.create().texOffs(44, 0).addBox(-0.5F, -3.5F, -0.5F, 1.0F, 7.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.25F, 0.0F, -0.5F, -0.1526F, 0.0F, 1.5708F));

		PartDefinition wing_left_tip = wing_left.addOrReplaceChild("wing_left_tip", CubeListBuilder.create().texOffs(0, 33).addBox(0.0F, -0.5F, -1.5F, 2.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(7.0F, 0.0F, 0.0F));

		PartDefinition primary3_r2 = wing_left_tip.addOrReplaceChild("primary3_r2", CubeListBuilder.create().texOffs(0, 42).addBox(0.0F, 0.0F, -1.0F, 5.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 0.0F, 3.0F, 0.0F, 0.3142F, 0.0F));

		PartDefinition primary2_r2 = wing_left_tip.addOrReplaceChild("primary2_r2", CubeListBuilder.create().texOffs(34, 40).addBox(0.0F, 0.0F, -1.0F, 6.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 0.0F, 1.5F, 0.0F, 0.1396F, 0.0F));

		PartDefinition primary1_r2 = wing_left_tip.addOrReplaceChild("primary1_r2", CubeListBuilder.create().texOffs(0, 40).addBox(0.0F, 0.0F, -1.0F, 7.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 0.0F, 0.0F, 0.0F, -0.0349F, 0.0F));

		PartDefinition primary0_r2 = wing_left_tip.addOrReplaceChild("primary0_r2", CubeListBuilder.create().texOffs(36, 38).addBox(0.0F, 0.0F, -1.0F, 7.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 0.0F, -1.5F, 0.0F, -0.2094F, 0.0F));

		PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(36, 19).addBox(-1.0F, -0.5F, 0.0F, 2.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.5F, 4.0F));

		PartDefinition feather2_r1 = tail.addOrReplaceChild("feather2_r1", CubeListBuilder.create().texOffs(0, 26).addBox(-1.0F, -0.5F, 0.0F, 2.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.3491F, 0.0F));

		PartDefinition feather0_r1 = tail.addOrReplaceChild("feather0_r1", CubeListBuilder.create().texOffs(20, 19).addBox(-1.0F, -0.5F, 0.0F, 2.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.3491F, 0.0F));

		PartDefinition leg_right = body.addOrReplaceChild("leg_right", CubeListBuilder.create(), PartPose.offset(-1.25F, 2.0F, 0.5F));

		PartDefinition toe_b_r1 = leg_right.addOrReplaceChild("toe_b_r1", CubeListBuilder.create().texOffs(46, 33).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.5F, 0.75F, -0.9828F, 0.0F, -3.1416F));

		PartDefinition toe_f_r1 = leg_right.addOrReplaceChild("toe_f_r1", CubeListBuilder.create().texOffs(42, 33).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.5F, -1.0F, 1.1071F, 0.0F, -3.1416F));

		PartDefinition shin_r1 = leg_right.addOrReplaceChild("shin_r1", CubeListBuilder.create().texOffs(26, 33).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, -3.1416F));

		PartDefinition leg_left = body.addOrReplaceChild("leg_left", CubeListBuilder.create(), PartPose.offset(1.25F, 2.0F, 0.5F));

		PartDefinition toe_b_r2 = leg_left.addOrReplaceChild("toe_b_r2", CubeListBuilder.create().texOffs(54, 33).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.5F, 0.75F, -0.9828F, 0.0F, 3.1416F));

		PartDefinition toe_f_r2 = leg_left.addOrReplaceChild("toe_f_r2", CubeListBuilder.create().texOffs(50, 33).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.5F, -1.0F, 1.1071F, 0.0F, 3.1416F));

		PartDefinition shin_r2 = leg_left.addOrReplaceChild("shin_r2", CubeListBuilder.create().texOffs(30, 33).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 3.1416F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	@Override
	public void setupAnim(CarrionCrowEntity e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
		this.root.getAllParts().forEach(ModelPart::resetPose);
		BoneAnimations anims = BoneAnimations.get(ANIMS);
		if (anims.version != this.partsVersion) {
			this.parts = anims.resolve(this.mesh);
			this.partsVersion = anims.version;
		}
		BoneAnimations.Pose pose = new BoneAnimations.Pose();
		float t = (age + e.getId() * 3) / 20.0F;
		if (e.deathTime > 0) {
			anims.add("death", (e.deathTime + age - e.tickCount) / 20.0F, 1.0F, pose);
		} else {
			String clip = switch (e.state()) {
				case CarrionCrowEntity.PERCH -> "perch";
				case CarrionCrowEntity.SNATCH -> "dive";
				default -> e.getDeltaMovement().lengthSqr() < 0.04 && e.state() == CarrionCrowEntity.CIRCLE ? "glide" : "fly";
			};
			anims.add(clip, t, 1.0F, pose);
			// art 2.0: a hit knocks it about (additive flinch) and freezes it for a tick
			dev.remnants.client.fx.CombatFx.watchHurt(e, 1);
			if (e.hurtTime > 0) {
				var src = e.getLastDamageSource();
				net.minecraft.world.phys.Vec3 at = src == null ? null : src.getSourcePosition();
				boolean front = at == null || net.minecraft.world.phys.Vec3.directionFromRotation(0, e.yBodyRot)
					.dot(at.subtract(e.position()).multiply(1, 0, 1)) >= 0;
				anims.add(front ? "hurt_front" : "hurt_back", (e.hurtDuration - e.hurtTime + age - e.tickCount) / 20.0F, 1.0F, pose);
			}
		}
		pose.applyTo(this.parts);
		HeadLook.apply(e, age, e.deathTime <= 0 && e.state() == CarrionCrowEntity.PERCH ? 1.0F : 0.0F, this.look, headYaw, headPitch);
		this.ambient(e, age);
	}

	/** Art 4.0: a feather now and then in flight, a flurry of them when struck, soul-lit eyes streaking through a dive. */
	private void ambient(CarrionCrowEntity e, float age) {
		float partial = age - e.tickCount;
		boolean flying = e.state() != CarrionCrowEntity.PERCH;
		for (int i = MobFx.due(e, 0, age, e.hurtTime > 0 || e.deathTime > 0 ? 14.0F : flying ? 0.35F : 0.05F); i > 0; i--) {
			MobFx.spawn(e, dev.remnants.registry.ModParticles.FEATHER, e.position().add(0, 0.35, 0), 0.4, 0.0, 0.0, 0.0, 0.08);
		}
		if (e.deathTime <= 0 && e.state() == CarrionCrowEntity.SNATCH) {
			for (int i = MobFx.due(e, 1, age, 30.0F); i > 0; i--) {
				float side = (i & 1) == 0 ? 2.25F : -2.25F;
				MobFx.spawn(e, dev.remnants.registry.ModParticles.EYE_TRAIL, MobFx.at(e, partial, side, -1.0F, -1.5F, this.head), 0, 0, 0);
			}
		}
	}

	@Override
	public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
		this.root.render(pose, buffer, light, overlay, color);
	}
}
