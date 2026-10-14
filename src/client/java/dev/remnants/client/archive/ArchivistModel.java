package dev.remnants.client.archive;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.remnants.RemnantsMod;
import dev.remnants.client.anim.BoneAnimations;
import dev.remnants.client.anim.HeadLook;
import dev.remnants.client.anim.SecondaryMotion;
import dev.remnants.client.fx.CombatFx;
import dev.remnants.client.fx.MeshFx;
import dev.remnants.client.fx.MobFx;
import dev.remnants.client.fx.ModelPoints;
import dev.remnants.entity.ArchivistEntity;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.remnants.entity.ArchivistEntity.*;

/**
 * The Archivist, built like the Soul King: a hooded skull with soul-glass spectacles and a crown of
 * wax candles, a fanned collar, layered robes, a great open tome in the left hand, a quill in the right, three open books
 * on orbit beating their covers.
 *
 * <p>Art 2.0: "read" while it sleeps at its lectern, else idle + move as the base; each move is
 * fitted to its real length on the server (the strike frame lands on the tick the tome flies / the ring goes off),
 * cross-faded with a smoothstep; hits play an additive hurt clip with a one-tick hit-stop ({@link CombatFx}); clip
 * events put glints on the spectacles, the quill hand and the tome, and a ring of pages on a burst; the robe panels,
 * cape and tabard swing on springs ({@link SecondaryMotion}). The head follows its target.
 */
public class ArchivistModel extends EntityModel<ArchivistEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(RemnantsMod.id("archivist"), "main");
	private static final ResourceLocation ANIMS = RemnantsMod.id("archivist");
	private static final float FADE_TICKS = 4.0F;

	private final ModelPart mesh;
	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart armRight, armLeft, tome;
	private final HeadLook.Rig look;
	private final ModelPart quill;
	private Map<String, ModelPart> parts = Map.of();
	private int partsVersion = Integer.MIN_VALUE;
	private final SecondaryMotion cloth = new SecondaryMotion()
		//    bone          parent      k     c     move  fall  turn   lag   follow minX   maxX  maxZ
		.link("cape0", null, 0.18F, 0.26F, 2.6F, 1.8F, 0.020F, 0.90F, 0.0F, -0.15F, 1.3F, 0.5F)
		.link("cape1", null, 0.16F, 0.25F, 2.8F, 1.8F, 0.020F, 0.95F, 0.0F, -0.15F, 1.35F, 0.5F)
		.link("cape2", null, 0.18F, 0.26F, 2.6F, 1.8F, 0.020F, 0.90F, 0.0F, -0.15F, 1.3F, 0.5F)
		.link("cape0_low", "cape0", 0.13F, 0.22F, 1.4F, 1.2F, 0.015F, 0.35F, 0.5F, -0.4F, 1.0F, 0.55F)
		.link("cape1_low", "cape1", 0.12F, 0.21F, 1.5F, 1.2F, 0.015F, 0.35F, 0.5F, -0.4F, 1.0F, 0.55F)
		.link("cape2_low", "cape2", 0.13F, 0.22F, 1.4F, 1.2F, 0.015F, 0.35F, 0.5F, -0.4F, 1.0F, 0.55F)
		.link("skirt_b0", null, 0.20F, 0.28F, 1.8F, 1.5F, 0.015F, 0.60F, 0.0F, -0.2F, 0.9F, 0.35F)
		.link("skirt_b1", null, 0.21F, 0.28F, 1.8F, 1.5F, 0.015F, 0.60F, 0.0F, -0.2F, 0.9F, 0.35F)
		.link("skirt_f0", null, 0.22F, 0.30F, 1.0F, 1.2F, 0.012F, 0.55F, 0.0F, -0.5F, 0.35F, 0.3F)
		.link("skirt_f1", null, 0.20F, 0.30F, 1.1F, 1.2F, 0.012F, 0.55F, 0.0F, -0.5F, 0.35F, 0.3F)
		.link("skirt_f2", null, 0.22F, 0.30F, 1.0F, 1.2F, 0.012F, 0.55F, 0.0F, -0.5F, 0.35F, 0.3F)
		.link("tabard", null, 0.24F, 0.30F, 0.9F, 1.2F, 0.012F, 0.55F, 0.0F, -0.6F, 0.25F, 0.3F)
		.link("hood_point", null, 0.30F, 0.30F, 0.8F, 1.0F, 0.020F, 0.50F, 0.0F, -0.3F, 0.5F, 0.3F);

	public ArchivistModel(ModelPart model) {
		super(RenderType::entityCutoutNoCull);
		this.mesh = model;
		this.root = model.getChild("root");
		this.body = this.root.getChild("body");
		this.head = this.body.getChild("head");
		this.armRight = this.body.getChild("arm_right");
		this.armLeft = this.body.getChild("arm_left");
		this.tome = this.armLeft.getChild("tome");
		this.quill = this.armRight.getChild("quill");
		// a scholar peering over spectacles: the head does it, the robe barely follows
		this.look = new HeadLook.Rig(this.root, this.body, this.head)
			.shares(new float[] {0.0F, 0.1F, 0.9F}, new float[] {0.0F, 0.05F, 0.95F})
			.eye(0.0F, -5.5F, -3.5F).limits(70.0F, 35.0F, 45.0F).speed(8.0F).glance(10.0F);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-6.5F, -2.0F, -4.5F, 13.0F, 26.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(180, 55).addBox(-5.5F, 24.0F, -3.5F, 11.0F, 5.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(78, 55).addBox(-7.0F, 4.0F, -5.0F, 14.0F, 3.0F, 10.0F, new CubeDeformation(0.25F))
		.texOffs(224, 82).addBox(-2.0F, 3.5F, -6.0F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.1F))
		.texOffs(16, 96).addBox(-1.0F, 4.5F, -6.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(40, 91).addBox(-6.0F, 7.0F, -5.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(192, 82).addBox(-7.5F, 11.0F, -6.5F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(118, 91).addBox(-7.0F, 11.5F, -7.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(40, 96).addBox(-6.0F, 12.0F, -7.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(44, 91).addBox(5.0F, 7.0F, -5.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(204, 82).addBox(3.5F, 11.0F, -6.5F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(126, 91).addBox(4.0F, 11.5F, -7.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(44, 96).addBox(5.0F, 12.0F, -7.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(194, 0).addBox(-7.0F, -12.0F, -5.0F, 14.0F, 11.0F, 10.0F, new CubeDeformation(-0.25F))
		.texOffs(104, 35).addBox(-10.0F, -12.0F, -5.5F, 20.0F, 5.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(108, 91).addBox(-2.0F, -11.0F, -6.5F, 4.0F, 3.0F, 1.0F, new CubeDeformation(0.1F))
		.texOffs(22, 96).addBox(-1.0F, -10.5F, -7.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -30.0F, 0.0F));

		PartDefinition collar_rim_r1 = body.addOrReplaceChild("collar_rim_r1", CubeListBuilder.create().texOffs(60, 96).addBox(-6.5F, -10.0F, 1.0F, 13.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(216, 55).addBox(-6.0F, -10.0F, -0.5F, 12.0F, 11.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -11.0F, 3.5F, 0.2094F, 0.0F, 0.0F));

		PartDefinition collar_wing_m_r1 = body.addOrReplaceChild("collar_wing_m_r1", CubeListBuilder.create().texOffs(66, 55).addBox(-0.5F, -8.0F, -2.5F, 1.0F, 9.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.5F, -11.0F, 1.5F, 0.0F, -0.1745F, -0.2443F));

		PartDefinition collar_wing_r1 = body.addOrReplaceChild("collar_wing_r1", CubeListBuilder.create().texOffs(54, 55).addBox(-0.5F, -8.0F, -2.5F, 1.0F, 9.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.5F, -11.0F, 1.5F, 0.0F, 0.1745F, 0.2443F));

		PartDefinition lapel_m_r1 = body.addOrReplaceChild("lapel_m_r1", CubeListBuilder.create().texOffs(234, 70).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 10.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, -11.0F, -5.0F, 0.0F, 0.0F, -0.1047F));

		PartDefinition lapel_r1 = body.addOrReplaceChild("lapel_r1", CubeListBuilder.create().texOffs(222, 70).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 10.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, -11.0F, -5.0F, 0.0F, 0.0F, 0.1047F));

		PartDefinition scroll_cap_r1 = body.addOrReplaceChild("scroll_cap_r1", CubeListBuilder.create().texOffs(134, 91).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.25F))
		.texOffs(126, 55).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.5F, 7.0F, -1.0F, 0.0F, 0.0F, -0.1396F));

		PartDefinition pleat_lf_r1 = body.addOrReplaceChild("pleat_lf_r1", CubeListBuilder.create().texOffs(172, 0).addBox(-1.5F, 0.0F, -0.5F, 3.0F, 22.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.5F, 6.0F, -5.0F, -0.1571F, -0.7854F, -0.1571F));

		PartDefinition pleat_lb_r1 = body.addOrReplaceChild("pleat_lb_r1", CubeListBuilder.create().texOffs(164, 0).addBox(-1.5F, 0.0F, -0.5F, 3.0F, 22.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.5F, 6.0F, 5.0F, 0.1571F, 0.7854F, -0.1571F));

		PartDefinition pleat_rf_r1 = body.addOrReplaceChild("pleat_rf_r1", CubeListBuilder.create().texOffs(156, 0).addBox(-1.5F, 0.0F, -0.5F, 3.0F, 22.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.5F, 6.0F, -5.0F, -0.1571F, 0.7854F, 0.1571F));

		PartDefinition pleat_rb_r1 = body.addOrReplaceChild("pleat_rb_r1", CubeListBuilder.create().texOffs(148, 0).addBox(-1.5F, 0.0F, -0.5F, 3.0F, 22.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.5F, 6.0F, 5.0F, 0.1571F, -0.7854F, 0.1571F));

		PartDefinition skirt_s_m_r1 = body.addOrReplaceChild("skirt_s_m_r1", CubeListBuilder.create().texOffs(62, 0).addBox(-0.5F, 0.0F, -4.0F, 1.0F, 24.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, 4.0F, 0.0F, 0.0F, 0.0F, -0.1571F));

		PartDefinition skirt_s_r1 = body.addOrReplaceChild("skirt_s_r1", CubeListBuilder.create().texOffs(44, 0).addBox(-0.5F, 0.0F, -4.0F, 1.0F, 24.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.1571F));

		PartDefinition skirt_f0 = body.addOrReplaceChild("skirt_f0", CubeListBuilder.create().texOffs(136, 0).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 22.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.5F, 6.0F, -5.5F, -0.1571F, 0.0F, 0.0F));

		PartDefinition skirt_f1 = body.addOrReplaceChild("skirt_f1", CubeListBuilder.create().texOffs(184, 0).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 21.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 6.0F, -5.5F, -0.1571F, 0.0F, 0.0F));

		PartDefinition skirt_f2 = body.addOrReplaceChild("skirt_f2", CubeListBuilder.create().texOffs(124, 0).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 23.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.5F, 6.0F, -5.5F, -0.1571F, 0.0F, 0.0F));

		PartDefinition skirt_b0 = body.addOrReplaceChild("skirt_b0", CubeListBuilder.create().texOffs(80, 0).addBox(-3.0F, 0.0F, -0.5F, 6.0F, 25.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0F, 4.0F, 5.5F, 0.1571F, 0.0F, 0.0F));

		PartDefinition skirt_b1 = body.addOrReplaceChild("skirt_b1", CubeListBuilder.create().texOffs(94, 0).addBox(-4.0F, 0.0F, -0.5F, 8.0F, 24.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 4.0F, 5.5F, 0.1571F, 0.0F, 0.0F));

		PartDefinition tabard = body.addOrReplaceChild("tabard", CubeListBuilder.create().texOffs(112, 0).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 24.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.0F, -6.25F));

		PartDefinition cape0 = body.addOrReplaceChild("cape0", CubeListBuilder.create().texOffs(68, 35).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 19.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.5F, -11.0F, 6.5F, 0.0873F, 0.0F, 0.0F));

		PartDefinition cape0_low = cape0.addOrReplaceChild("cape0_low", CubeListBuilder.create().texOffs(92, 35).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 17.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 19.0F, 0.0F));

		PartDefinition cape1 = body.addOrReplaceChild("cape1", CubeListBuilder.create().texOffs(40, 35).addBox(-3.0F, 0.0F, -0.5F, 6.0F, 19.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -11.0F, 6.5F, 0.1222F, 0.0F, 0.0F));

		PartDefinition cape1_low = cape1.addOrReplaceChild("cape1_low", CubeListBuilder.create().texOffs(54, 35).addBox(-3.0F, 0.0F, -0.5F, 6.0F, 19.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 19.0F, 0.0F));

		PartDefinition cape2 = body.addOrReplaceChild("cape2", CubeListBuilder.create().texOffs(80, 35).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 19.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.5F, -11.0F, 6.5F, 0.0873F, 0.0F, 0.0F));

		PartDefinition cape2_low = cape2.addOrReplaceChild("cape2_low", CubeListBuilder.create().texOffs(168, 35).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 19.0F, 0.0F));

		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(26, 55).addBox(-3.5F, -9.0F, -3.5F, 7.0F, 7.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(120, 82).addBox(-2.5F, -2.0F, -4.0F, 5.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(142, 91).addBox(-3.0F, -6.0F, -4.25F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(28, 96).addBox(-2.5F, -5.5F, -4.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(150, 91).addBox(0.0F, -6.0F, -4.25F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(34, 96).addBox(0.5F, -5.5F, -4.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(88, 96).addBox(-0.5F, -5.5F, -4.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(140, 55).addBox(-5.0F, -11.0F, -5.0F, 10.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(0, 70).addBox(-5.0F, -9.0F, 4.0F, 10.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 35).addBox(-5.0F, -9.0F, -5.0F, 1.0F, 11.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(20, 35).addBox(4.0F, -9.0F, -5.0F, 1.0F, 11.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(210, 91).addBox(-5.5F, -10.0F, -6.0F, 11.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(22, 70).addBox(-5.0F, -10.5F, -5.0F, 10.0F, 1.0F, 10.0F, new CubeDeformation(0.3F))
		.texOffs(234, 82).addBox(2.0F, -14.0F, -3.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(48, 96).addBox(2.0F, -17.0F, -2.0F, 2.0F, 3.0F, 0.0F, new CubeDeformation(0.0F))
		.texOffs(48, 91).addBox(3.0F, -17.0F, -3.0F, 0.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(92, 96).addBox(3.5F, -13.0F, -3.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.1F))
		.texOffs(184, 82).addBox(-1.0F, -16.0F, -4.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(52, 96).addBox(-1.0F, -19.0F, -3.0F, 2.0F, 3.0F, 0.0F, new CubeDeformation(0.0F))
		.texOffs(52, 91).addBox(0.0F, -19.0F, -4.0F, 0.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(96, 96).addBox(0.5F, -15.0F, -4.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.1F))
		.texOffs(216, 82).addBox(-4.0F, -15.0F, -3.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(56, 96).addBox(-4.0F, -18.0F, -2.0F, 2.0F, 3.0F, 0.0F, new CubeDeformation(0.0F))
		.texOffs(56, 91).addBox(-3.0F, -18.0F, -3.0F, 0.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(100, 96).addBox(-2.5F, -14.0F, -3.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.1F)), PartPose.offset(0.0F, -13.0F, 0.0F));

		PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(140, 82).addBox(-2.5F, 0.5F, -4.5F, 5.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.5F, 0.5F));

		PartDefinition hood_point = head.addOrReplaceChild("hood_point", CubeListBuilder.create().texOffs(52, 82).addBox(-3.0F, 0.0F, -0.5F, 6.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -10.0F, 4.0F, 0.6632F, 0.0F, 0.0F));

		PartDefinition arm_right = body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(186, 35).addBox(-2.0F, -1.0F, -2.5F, 4.0F, 11.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(222, 35).addBox(-3.0F, 9.0F, -3.5F, 6.0F, 8.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(0, 82).addBox(-3.0F, 9.0F, -3.5F, 6.0F, 2.0F, 7.0F, new CubeDeformation(0.3F))
		.texOffs(160, 82).addBox(-1.5F, 17.0F, -1.5F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-8.5F, -10.0F, 0.0F));

		PartDefinition finger3_r1 = arm_right.addOrReplaceChild("finger3_r1", CubeListBuilder.create().texOffs(72, 91).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, 22.5F, -1.4F, 0.1937F, 0.0F, -2.9442F));

		PartDefinition finger2_r1 = arm_right.addOrReplaceChild("finger2_r1", CubeListBuilder.create().texOffs(68, 91).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.45F, 22.5F, -1.8F, 0.3795F, 0.0F, -3.0667F));

		PartDefinition finger1_r1 = arm_right.addOrReplaceChild("finger1_r1", CubeListBuilder.create().texOffs(64, 91).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.6F, 22.5F, -1.9F, 0.4224F, 0.0F, 3.0916F));

		PartDefinition finger0_r1 = arm_right.addOrReplaceChild("finger0_r1", CubeListBuilder.create().texOffs(60, 91).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.6F, 22.5F, -1.7F, 0.3332F, 0.0F, 2.9927F));

		PartDefinition quill = arm_right.addOrReplaceChild("quill", CubeListBuilder.create().texOffs(206, 91).addBox(-0.5F, 2.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(104, 96).addBox(-0.5F, 5.0F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(180, 0).addBox(-0.5F, -20.0F, -0.5F, 1.0F, 22.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 20.0F, -1.5F));

		PartDefinition tuft_r1 = quill.addOrReplaceChild("tuft_r1", CubeListBuilder.create().texOffs(92, 91).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -20.0F, 0.0F, 0.0F, 0.0F, -0.2443F));

		PartDefinition vane_in_r1 = quill.addOrReplaceChild("vane_in_r1", CubeListBuilder.create().texOffs(134, 55).addBox(0.0F, -12.0F, -0.5F, 2.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, -7.0F, 0.0F, 0.0F, 0.0F, 0.1396F));

		PartDefinition vane_out_r1 = quill.addOrReplaceChild("vane_out_r1", CubeListBuilder.create().texOffs(180, 35).addBox(-2.0F, -16.0F, -0.5F, 2.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, -5.0F, 0.0F, 0.0F, 0.0F, -0.1047F));

		PartDefinition arm_left = body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(204, 35).addBox(-2.0F, -1.0F, -2.5F, 4.0F, 11.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(0, 55).addBox(-3.0F, 9.0F, -3.5F, 6.0F, 8.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(26, 82).addBox(-3.0F, 9.0F, -3.5F, 6.0F, 2.0F, 7.0F, new CubeDeformation(0.3F))
		.texOffs(172, 82).addBox(-1.5F, 17.0F, -1.5F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(8.5F, -10.0F, 0.0F));

		PartDefinition finger3_r2 = arm_left.addOrReplaceChild("finger3_r2", CubeListBuilder.create().texOffs(88, 91).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, 22.5F, -1.4F, 0.1937F, 0.0F, 2.9442F));

		PartDefinition finger2_r2 = arm_left.addOrReplaceChild("finger2_r2", CubeListBuilder.create().texOffs(84, 91).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.45F, 22.5F, -1.8F, 0.3795F, 0.0F, 3.0667F));

		PartDefinition finger1_r2 = arm_left.addOrReplaceChild("finger1_r2", CubeListBuilder.create().texOffs(80, 91).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.6F, 22.5F, -1.9F, 0.4224F, 0.0F, -3.0916F));

		PartDefinition finger0_r2 = arm_left.addOrReplaceChild("finger0_r2", CubeListBuilder.create().texOffs(76, 91).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.6F, 22.5F, -1.7F, 0.3332F, 0.0F, -2.9927F));

		PartDefinition tome = arm_left.addOrReplaceChild("tome", CubeListBuilder.create().texOffs(198, 70).addBox(-1.0F, 0.0F, -9.0F, 2.0F, 1.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(68, 82).addBox(-0.5F, 1.0F, -2.0F, 1.0F, 7.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 16.5F, 2.0F));

		PartDefinition corner3_r1 = tome.addOrReplaceChild("corner3_r1", CubeListBuilder.create().texOffs(8, 96).addBox(-8.5F, -0.5F, 3.5F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.25F))
		.texOffs(0, 96).addBox(-8.5F, -0.5F, -5.5F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.25F))
		.texOffs(96, 82).addBox(-6.0F, -2.55F, -3.5F, 5.0F, 0.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(166, 70).addBox(-7.0F, -2.5F, -4.5F, 7.0F, 2.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(98, 70).addBox(-8.0F, -0.5F, -5.0F, 8.0F, 1.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 0.5F, -4.0F, 0.0F, 0.0F, 0.2443F));

		PartDefinition corner1_r1 = tome.addOrReplaceChild("corner1_r1", CubeListBuilder.create().texOffs(242, 91).addBox(6.5F, -0.5F, 3.5F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.25F))
		.texOffs(234, 91).addBox(6.5F, -0.5F, -5.5F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.25F))
		.texOffs(72, 82).addBox(1.0F, -2.55F, -3.5F, 5.0F, 0.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(134, 70).addBox(0.0F, -2.5F, -4.5F, 7.0F, 2.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(62, 70).addBox(0.0F, -0.5F, -5.0F, 8.0F, 1.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 0.5F, -4.0F, 0.0F, 0.0F, -0.2443F));

		PartDefinition orbit = body.addOrReplaceChild("orbit", CubeListBuilder.create(), PartPose.offset(0.0F, -14.0F, 0.0F));

		PartDefinition orb0 = orbit.addOrReplaceChild("orb0", CubeListBuilder.create().texOffs(96, 91).addBox(-0.5F, -2.0F, 0.0F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-12.0F, 0.0F, 0.0F, 0.0F, 1.5708F, 0.0F));

		PartDefinition orb0_r = orb0.addOrReplaceChild("orb0_r", CubeListBuilder.create().texOffs(242, 82).addBox(-3.5F, -2.0F, -0.5F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(158, 91).addBox(-3.5F, -1.5F, -1.5F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(108, 96).addBox(-3.0F, -1.0F, -1.55F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, 0.0F, -0.4887F, 0.0F));

		PartDefinition orb0_l = orb0.addOrReplaceChild("orb0_l", CubeListBuilder.create().texOffs(0, 91).addBox(0.5F, -2.0F, -0.5F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(166, 91).addBox(0.5F, -1.5F, -1.5F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(112, 96).addBox(1.0F, -1.0F, -1.55F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, 0.0F, 0.4887F, 0.0F));

		PartDefinition orb1 = orbit.addOrReplaceChild("orb1", CubeListBuilder.create().texOffs(100, 91).addBox(-0.5F, -2.0F, 0.0F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0F, -3.0F, 10.5F, 0.0F, -2.6225F, 0.0F));

		PartDefinition orb1_r = orb1.addOrReplaceChild("orb1_r", CubeListBuilder.create().texOffs(8, 91).addBox(-3.5F, -2.0F, -0.5F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(174, 91).addBox(-3.5F, -1.5F, -1.5F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(116, 96).addBox(-3.0F, -1.0F, -1.55F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, 0.0F, -0.4887F, 0.0F));

		PartDefinition orb1_l = orb1.addOrReplaceChild("orb1_l", CubeListBuilder.create().texOffs(16, 91).addBox(0.5F, -2.0F, -0.5F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(182, 91).addBox(0.5F, -1.5F, -1.5F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(120, 96).addBox(1.0F, -1.0F, -1.55F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, 0.0F, 0.4887F, 0.0F));

		PartDefinition orb2 = orbit.addOrReplaceChild("orb2", CubeListBuilder.create().texOffs(104, 91).addBox(-0.5F, -2.0F, 0.0F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0F, 3.0F, -10.5F, 0.0F, -0.5191F, 0.0F));

		PartDefinition orb2_r = orb2.addOrReplaceChild("orb2_r", CubeListBuilder.create().texOffs(24, 91).addBox(-3.5F, -2.0F, -0.5F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(190, 91).addBox(-3.5F, -1.5F, -1.5F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(124, 96).addBox(-3.0F, -1.0F, -1.55F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, 0.0F, -0.4887F, 0.0F));

		PartDefinition orb2_l = orb2.addOrReplaceChild("orb2_l", CubeListBuilder.create().texOffs(32, 91).addBox(0.5F, -2.0F, -0.5F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(198, 91).addBox(0.5F, -1.5F, -1.5F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(128, 96).addBox(1.0F, -1.0F, -1.55F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.5F, 0.0F, 0.4887F, 0.0F));

		return LayerDefinition.create(meshdefinition, 256, 128);
	}

	static String clip(int anim) {
		return switch (anim) {
			case ANIM_AWAKEN -> "awaken";
			case ANIM_THROW -> "throw";
			case ANIM_SUMMON -> "summon";
			case ANIM_BURST -> "burst";
			case ANIM_BLINK -> "blink";
			case ANIM_RISE -> "rise";
			case ANIM_STUNNED -> "stunned";
			case ANIM_SHATTER -> "shatter";
			case ANIM_SPIRAL -> "spiral";
			case ANIM_STARS -> "stars";
			default -> null;
		};
	}

	/** Moves fitted to the server's length (their effect lands on a fixed tick); loops and the long sag are not. */
	private static boolean fitted(int anim) {
		return anim != ANIM_STUNNED && anim != ANIM_SPIRAL && anim != ANIM_SHATTER;
	}

	private static float smooth(float x) {
		return x * x * (3.0F - 2.0F * x);
	}

	private void play(BoneAnimations anims, ArchivistEntity e, String clip, int anim, int start, int length, float age, float weight,
		BoneAnimations.Pose pose, List<Object[]> events) {
		float elapsed = age - start - CombatFx.lag(e, start, age);
		float seconds = fitted(anim) && length > 0 ? Mth.clamp(elapsed / length, 0.0F, 1.0F) * anims.length(clip) : elapsed / 20.0F;
		anims.add(clip, seconds, weight, pose);
		if (events != null) {
			CombatFx.clipEvents(e, "move", anims, clip, seconds, (name, strength) -> events.add(new Object[] {name, strength}));
		}
	}

	@Override
	public void setupAnim(ArchivistEntity e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
		this.root.getAllParts().forEach(ModelPart::resetPose);
		BoneAnimations anims = BoneAnimations.get(ANIMS);
		if (anims.version != this.partsVersion) {
			this.parts = anims.resolve(this.mesh);
			this.partsVersion = anims.version;
		}
		float partial = age - e.tickCount;
		BoneAnimations.Pose pose = new BoneAnimations.Pose();
		List<Object[]> events = new ArrayList<>();
		float calm = 1.0F;
		if (e.deathTime > 0) {
			anims.add("death", (e.deathTime + partial) / 20.0F, 1.0F, pose);
		} else if (e.isDormant()) {
			anims.add("read", age / 20.0F, 1.0F, pose);
			calm = 0.4F;
		} else {
			CombatFx.watchHurt(e, 1);
			anims.add("idle", age / 20.0F, 1.0F, pose);
			float moving = Mth.clamp((float) e.getDeltaMovement().horizontalDistance() * 6.0F, 0.0F, 1.0F);
			anims.add("move", age / 20.0F, moving, pose);
			float fade = smooth(Mth.clamp((age - e.clientAnimChanged) / FADE_TICKS, 0.0F, 1.0F));
			String prev = clip(e.clientPrevAnim);
			String cur = clip(e.getAnim());
			if (prev != null) {
				this.play(anims, e, prev, e.clientPrevAnim, e.clientPrevAnimStart, e.clientPrevAnimLength, age, 1.0F - fade, pose, null);
			}
			if (cur != null) {
				this.play(anims, e, cur, e.getAnim(), e.clientAnimStart, e.clientAnimLength, age, fade, pose, events);
			}
			if (e.hurtTime > 0 && !e.isShielded()) {
				var src = e.getLastDamageSource();
				Vec3 at = src == null ? null : src.getSourcePosition();
				boolean front = at == null || Vec3.directionFromRotation(0, e.yBodyRot).dot(at.subtract(e.position()).multiply(1, 0, 1)) >= 0;
				anims.add(front ? "hurt_front" : "hurt_back", (e.hurtDuration - e.hurtTime + partial) / 20.0F, 1.0F, pose);
			}
		}
		pose.applyTo(this.parts);
		float look = e.deathTime > 0 || e.isDormant() || e.isStunned() ? 0.0F : 1.0F;
		HeadLook.apply(e, age, look, this.look, headYaw, headPitch);
		this.cloth.apply(e, age, partial, this.body.xRot, this.body.yRot, calm, this.parts);
		for (Object[] ev : events) {
			this.fire(e, partial, (String) ev[0], (Float) ev[1]);
		}
		this.ambient(e, age, partial);
		// art 4.0 mesh VFX on a move's first frame: circles under its summons and star-falls, a pillar where it blinks
		if (MeshFx.once(e, 0, e.clientAnimStart) && e.tickCount - e.clientAnimStart < 4) {
			switch (e.getAnim()) {
				case ANIM_SUMMON, ANIM_STARS, ANIM_SPIRAL -> MeshFx.runeCircle(e, 3.2F, Math.max(1.0F, e.clientAnimLength / 20.0F));
				case ANIM_BLINK -> MeshFx.pillar(e.position(), 0.7F, 3.5F, 0.8F);
				case ANIM_BURST -> MeshFx.shockwave(e.position(), 5.0F);
				default -> {
				}
			}
		}
	}

	/**
	 * Art 4.0: asleep over its tome, a rune drifts up now and then. Awake, the spectacles leave afterimages, the quill's
	 * nib spits ink-light, runes rise off the open tome, scraps of written pages tumble round it (more in phase 2), and
	 * near the end violet sparks crackle off it.
	 */
	private void ambient(ArchivistEntity e, float age, float partial) {
		if (e.deathTime > 0) {
			return;
		}
		var r = e.getRandom();
		Vec3 book = MobFx.at(e, partial, 0.0F, -1.0F, -4.0F, this.root, this.body, this.armLeft, this.tome);
		if (e.isDormant()) {
			for (int i = MobFx.due(e, 0, age, 0.6F); i > 0; i--) {
				MobFx.spawn(e, dev.remnants.registry.ModParticles.RUNE, book, 0.2, 0.0, 0.03, 0.0, 0.01);
			}
			return;
		}
		for (int i = MobFx.due(e, 1, age, 20.0F); i > 0; i--) {
			float side = (i & 1) == 0 ? 1.5F : -1.5F;
			MobFx.spawn(e, dev.remnants.registry.ModParticles.EYE_TRAIL, MobFx.at(e, partial, side, -4.5F, -4.7F, this.root, this.body, this.head), 0, 0, 0);
		}
		for (int i = MobFx.due(e, 2, age, 5.0F); i > 0; i--) {
			MobFx.spawn(e, dev.remnants.registry.ModParticles.SOUL_EMBER, MobFx.at(e, partial, 0.0F, 5.0F, 0.0F, this.root, this.body, this.armRight, this.quill), 0.05, 0.0, 0.02, 0.0, 0.03);
		}
		for (int i = MobFx.due(e, 3, age, 1.6F); i > 0; i--) {
			MobFx.spawn(e, dev.remnants.registry.ModParticles.RUNE, book, 0.3, 0.0, 0.035, 0.0, 0.015);
		}
		for (int i = MobFx.due(e, 4, age, e.phase() >= 2 ? 3.5F : 1.2F); i > 0; i--) {
			double a = r.nextDouble() * Math.PI * 2.0, d = 0.5 + r.nextDouble() * 0.9;
			Vec3 p = e.position().add(Math.cos(a) * d, 2.2 + r.nextDouble() * 0.8, Math.sin(a) * d);
			MobFx.spawn(e, dev.remnants.registry.ModParticles.PAGE_SCRAP, p, 0, -0.02, 0);
		}
		if (e.getHealth() < e.getMaxHealth() * ArchivistEntity.FRENZY_AT) {
			for (int i = MobFx.due(e, 5, age, 12.0F); i > 0; i--) {
				Vec3 p = e.position().add((r.nextDouble() - 0.5) * 1.2, 0.3 + r.nextDouble() * 2.4, (r.nextDouble() - 0.5) * 1.2);
				MobFx.spawn(e, dev.remnants.registry.ModParticles.CURSE_EMBER, p, 0, 0.03, 0);
			}
		}
	}

	/** Clip events: glints on the spectacles / quill hand / tome; a ring of torn pages when it bursts. */
	private void fire(ArchivistEntity e, float partial, String name, float strength) {
		Level level = e.level();
		switch (name) {
			case "glint_eyes" -> CombatFx.glint(ModelPoints.world(e, partial, 0.0F, -4.5F, -5.0F, this.root, this.body, this.head), strength);
			case "glint_hand" -> CombatFx.glint(ModelPoints.world(e, partial, 0.0F, 19.0F, -1.0F, this.root, this.body, this.armRight), strength);
			case "glint_tome" -> CombatFx.glint(ModelPoints.world(e, partial, 0.0F, -2.0F, -7.0F, this.root, this.body, this.armLeft, this.tome),
				strength);
			case "burst" -> {
				Vec3 c = e.position().add(0, 1.4, 0);
				int n = 10 + Math.round(14 * strength);
				for (int i = 0; i < n; i++) {
					double a = i * Math.PI * 2 / n;
					level.addParticle(ParticleTypes.ENCHANT, c.x + Math.cos(a) * 0.8, c.y, c.z + Math.sin(a) * 0.8, Math.cos(a) * 1.2, 0.1,
						Math.sin(a) * 1.2);
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
