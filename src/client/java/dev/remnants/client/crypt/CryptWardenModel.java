package dev.remnants.client.crypt;

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
import dev.remnants.entity.CryptWardenEntity;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.remnants.entity.CryptWardenEntity.*;

/**
 * The Crypt Warden, built like the Soul King: great
 * helm with a soul-lit visor, circlet and horns; a breastplate broken open over a soul heart; pauldrons of three lames;
 * mail, tassets, a tabard of runes, a torn cape of three two-segment strips; a greatsword and a tower shield.
 *
 * <p>Art 2.0: every move of {@link CryptWardenEntity} plays its clip fitted to the
 * move's real length (wind-ups reach their loaded pose on the strike tick whether enraged or in a combo), cross-faded
 * over {@link #FADE_TICKS} with a smoothstep; the "breath" layer never stops; hits on it play an additive hurt clip and a
 * one-tick hit-stop ({@link CombatFx}); clip events put glints on the visor and blade, sparks at the sword tip and dust
 * under the feet; the cape, tabard and skirt flaps swing on springs ({@link SecondaryMotion}).
 */
public class CryptWardenModel extends EntityModel<CryptWardenEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(RemnantsMod.id("crypt_warden"), "main");
	private static final ResourceLocation ANIMS = RemnantsMod.id("crypt_warden");
	private static final float FADE_TICKS = 4.0F;

	private final ModelPart mesh;
	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart torso;
	private final ModelPart head;
	private final ModelPart armRight;
	private final ModelPart forearmRight;
	private final ModelPart sword;
	private final ModelPart legRight, shinRight, legLeft, shinLeft;
	private final HeadLook.Rig look;
	private final ModelPart[] pauldronRight, pauldronLeft, shield;
	private Map<String, ModelPart> parts = Map.of();
	private int partsVersion = Integer.MIN_VALUE;
	private final SecondaryMotion cloth = new SecondaryMotion()
		//    bone          parent        k     c     move  fall  turn   lag   follow minX   maxX  maxZ
		.link("cape0", null, 0.22F, 0.30F, 2.2F, 1.6F, 0.020F, 0.85F, 0.0F, -0.15F, 1.25F, 0.45F)
		.link("cape1", null, 0.20F, 0.28F, 2.4F, 1.6F, 0.020F, 0.90F, 0.0F, -0.15F, 1.30F, 0.45F)
		.link("cape2", null, 0.23F, 0.30F, 2.1F, 1.6F, 0.020F, 0.85F, 0.0F, -0.15F, 1.25F, 0.45F)
		.link("cape0_low", "cape0", 0.16F, 0.24F, 1.2F, 1.0F, 0.015F, 0.30F, 0.45F, -0.35F, 0.9F, 0.5F)
		.link("cape1_low", "cape1", 0.15F, 0.22F, 1.3F, 1.0F, 0.015F, 0.30F, 0.45F, -0.35F, 0.9F, 0.5F)
		.link("cape2_low", "cape2", 0.16F, 0.24F, 1.2F, 1.0F, 0.015F, 0.30F, 0.45F, -0.35F, 0.9F, 0.5F)
		.link("tabard", null, 0.25F, 0.32F, 0.8F, 1.2F, 0.012F, 0.55F, 0.0F, -0.9F, 0.18F, 0.3F);

	public CryptWardenModel(ModelPart model) {
		super(RenderType::entityCutoutNoCull);
		this.mesh = model;
		this.root = model.getChild("root");
		this.body = this.root.getChild("body");
		this.torso = this.body.getChild("torso");
		this.head = this.torso.getChild("head");
		this.armRight = this.torso.getChild("arm_right");
		this.forearmRight = this.armRight.getChild("forearm_right");
		this.sword = this.forearmRight.getChild("sword");
		this.legRight = this.root.getChild("leg_right");
		this.shinRight = this.legRight.getChild("shin_right");
		this.legLeft = this.root.getChild("leg_left");
		this.shinLeft = this.legLeft.getChild("shin_left");
		// a heavy knight: the torso takes a third of the turn, the helm the rest; slow, deliberate
		ModelPart armLeft = this.torso.getChild("arm_left");
		this.pauldronRight = new ModelPart[] {this.root, this.body, this.torso, this.armRight, this.armRight.getChild("pauldron_right")};
		this.pauldronLeft = new ModelPart[] {this.root, this.body, this.torso, armLeft, armLeft.getChild("pauldron_left")};
		ModelPart forearmLeft = armLeft.getChild("forearm_left");
		this.shield = new ModelPart[] {this.root, this.body, this.torso, armLeft, forearmLeft, forearmLeft.getChild("shield")};
		this.look = new HeadLook.Rig(this.root, this.body, this.torso, this.head)
			.shares(new float[] {0.0F, 0.0F, 0.3F, 0.7F}, new float[] {0.0F, 0.0F, 0.15F, 0.85F})
			.eye(0.0F, -5.5F, -5.0F).limits(65.0F, 35.0F, 40.0F).speed(7.0F).glance(12.0F);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition leg_right = root.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(50, 49).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 10.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(26, 93).addBox(-3.0F, 0.5F, -3.5F, 6.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-4.5F, -22.0F, 0.0F));

		PartDefinition shin_right = leg_right.addOrReplaceChild("shin_right", CubeListBuilder.create().texOffs(0, 105).addBox(-3.5F, -1.5F, -3.5F, 7.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(226, 111).addBox(-1.0F, -1.0F, -4.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(146, 66).addBox(-2.5F, 1.5F, -2.5F, 5.0F, 8.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(54, 80).addBox(-3.0F, 9.5F, -5.0F, 6.0F, 3.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(80, 111).addBox(-2.0F, 10.0F, -5.5F, 4.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 9.5F, -1.0F));

		PartDefinition knee_spike_r1 = shin_right.addOrReplaceChild("knee_spike_r1", CubeListBuilder.create().texOffs(6, 111).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.5F, -4.0F, 0.4363F, 0.0F, 0.0F));

		PartDefinition leg_left = root.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(72, 49).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 10.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(46, 93).addBox(-3.0F, 0.5F, -3.5F, 6.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(4.5F, -22.0F, 0.0F));

		PartDefinition shin_left = leg_left.addOrReplaceChild("shin_left", CubeListBuilder.create().texOffs(20, 105).addBox(-3.5F, -1.5F, -3.5F, 7.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(232, 111).addBox(-1.0F, -1.0F, -4.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(168, 66).addBox(-2.5F, 1.5F, -2.5F, 5.0F, 8.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(84, 80).addBox(-3.0F, 9.5F, -5.0F, 6.0F, 3.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(90, 111).addBox(-2.0F, 10.0F, -5.5F, 4.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 9.5F, -1.0F));

		PartDefinition knee_spike_r2 = shin_left.addOrReplaceChild("knee_spike_r2", CubeListBuilder.create().texOffs(10, 111).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.5F, -4.0F, 0.4363F, 0.0F, 0.0F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(76, 29).addBox(-8.25F, -1.0F, -4.0F, 1.0F, 10.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(94, 29).addBox(7.25F, -1.0F, -4.0F, 1.0F, 10.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(198, 49).addBox(-8.5F, -3.0F, -5.5F, 17.0F, 3.0F, 11.0F, new CubeDeformation(0.25F))
		.texOffs(152, 105).addBox(-2.5F, -3.5F, -6.5F, 5.0F, 4.0F, 1.0F, new CubeDeformation(0.1F))
		.texOffs(116, 111).addBox(-1.0F, -2.5F, -7.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -22.0F, 0.0F));

		PartDefinition tasset_r1 = body.addOrReplaceChild("tasset_r1", CubeListBuilder.create().texOffs(212, 29).addBox(-0.5F, 0.0F, -4.0F, 1.0F, 9.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-8.0F, -2.0F, -1.0F, 0.0F, 0.0F, 0.0873F));

		PartDefinition tasset_m_r1 = body.addOrReplaceChild("tasset_m_r1", CubeListBuilder.create().texOffs(230, 29).addBox(-0.5F, 0.0F, -4.0F, 1.0F, 9.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(8.0F, -2.0F, -1.0F, 0.0F, 0.0F, -0.0873F));

		PartDefinition skirt_front = body.addOrReplaceChild("skirt_front", CubeListBuilder.create().texOffs(114, 80).addBox(-7.0F, 0.0F, -0.5F, 14.0F, 11.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.0F, -5.0F));

		PartDefinition skirt_back = body.addOrReplaceChild("skirt_back", CubeListBuilder.create().texOffs(144, 80).addBox(-7.0F, 0.0F, -0.5F, 14.0F, 11.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.0F, 5.0F));

		PartDefinition tabard = body.addOrReplaceChild("tabard", CubeListBuilder.create().texOffs(54, 29).addBox(-3.0F, 0.0F, -0.5F, 6.0F, 18.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.0F, -6.5F));

		PartDefinition torso = body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(100, 0).addBox(-7.0F, -14.0F, -4.5F, 14.0F, 14.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(22, 111).addBox(-6.75F, -2.5F, -5.0F, 14.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(22, 80).addBox(-7.5F, -13.0F, -5.5F, 6.0F, 11.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(38, 80).addBox(1.5F, -13.0F, -5.5F, 6.0F, 11.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(206, 93).addBox(-8.0F, -15.0F, -6.0F, 16.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(66, 93).addBox(-2.0F, -12.5F, -5.0F, 4.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(196, 111).addBox(-2.0F, -4.5F, -6.0F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(206, 111).addBox(-2.0F, -7.5F, -6.0F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(216, 111).addBox(-2.0F, -10.5F, -6.0F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 111).addBox(-1.0F, -7.0F, -5.5F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(114, 66).addBox(-7.5F, -13.5F, 4.0F, 15.0F, 13.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 66).addBox(-6.0F, -17.0F, -5.5F, 12.0F, 3.0F, 11.0F, new CubeDeformation(0.0F))
		.texOffs(134, 49).addBox(-11.0F, -16.0F, -3.5F, 22.0F, 5.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(100, 111).addBox(-7.5F, -16.5F, -6.5F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(250, 111).addBox(-6.5F, -16.0F, -7.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(108, 111).addBox(4.5F, -16.5F, -6.5F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 115).addBox(5.5F, -16.0F, -7.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -3.0F, 0.0F));

		PartDefinition cape0 = torso.addOrReplaceChild("cape0", CubeListBuilder.create().texOffs(14, 49).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.5F, -15.0F, 6.0F, 0.1047F, 0.0F, 0.0F));

		PartDefinition cape0_low = cape0.addOrReplaceChild("cape0_low", CubeListBuilder.create().texOffs(112, 29).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 17.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 16.0F, 0.0F));

		PartDefinition cape1 = torso.addOrReplaceChild("cape1", CubeListBuilder.create().texOffs(0, 49).addBox(-3.0F, 0.0F, -0.5F, 6.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -15.0F, 6.0F, 0.1396F, 0.0F, 0.0F));

		PartDefinition cape1_low = cape1.addOrReplaceChild("cape1_low", CubeListBuilder.create().texOffs(40, 29).addBox(-3.0F, 0.0F, -0.5F, 6.0F, 19.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 16.0F, 0.0F));

		PartDefinition cape2 = torso.addOrReplaceChild("cape2", CubeListBuilder.create().texOffs(26, 49).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.5F, -15.0F, 6.0F, 0.1047F, 0.0F, 0.0F));

		PartDefinition cape2_low = cape2.addOrReplaceChild("cape2_low", CubeListBuilder.create().texOffs(38, 49).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 16.0F, 0.0F));

		PartDefinition head = torso.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 29).addBox(-5.0F, -10.0F, -5.0F, 10.0F, 10.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(190, 66).addBox(-5.5F, -9.0F, -5.5F, 11.0F, 2.0F, 11.0F, new CubeDeformation(0.0F))
		.texOffs(238, 111).addBox(-1.0F, -8.5F, -6.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -16.0F, 0.0F));

		PartDefinition crest0_r1 = head.addOrReplaceChild("crest0_r1", CubeListBuilder.create().texOffs(172, 105).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, -9.0F, -5.0F, -0.1396F, 0.0F, 0.1047F));

		PartDefinition crest1_r1 = head.addOrReplaceChild("crest1_r1", CubeListBuilder.create().texOffs(202, 93).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -9.0F, -5.0F, -0.1396F, 0.0F, 0.0F));

		PartDefinition crest2_r1 = head.addOrReplaceChild("crest2_r1", CubeListBuilder.create().texOffs(176, 105).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, -9.0F, -5.0F, -0.1396F, 0.0F, -0.1047F));

		PartDefinition horn0_r1 = head.addOrReplaceChild("horn0_r1", CubeListBuilder.create().texOffs(162, 93).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.25F, -6.0F, 0.5F, -0.2432F, 0.0F, -1.0516F));

		PartDefinition horn1_r1 = head.addOrReplaceChild("horn1_r1", CubeListBuilder.create().texOffs(186, 93).addBox(-1.0F, -2.5F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-9.25F, -9.0F, 1.75F, -0.3079F, 0.0F, -0.5586F));

		PartDefinition horn2_r1 = head.addOrReplaceChild("horn2_r1", CubeListBuilder.create().texOffs(180, 105).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-10.5F, -13.0F, 3.5F, -0.4636F, 0.0F, 0.0F));

		PartDefinition horn0_m_r1 = head.addOrReplaceChild("horn0_m_r1", CubeListBuilder.create().texOffs(174, 93).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.25F, -6.0F, 0.5F, -0.2432F, 0.0F, 1.0516F));

		PartDefinition horn1_m_r1 = head.addOrReplaceChild("horn1_m_r1", CubeListBuilder.create().texOffs(194, 93).addBox(-1.0F, -2.5F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.25F, -9.0F, 1.75F, -0.3079F, 0.0F, 0.5586F));

		PartDefinition horn2_m_r1 = head.addOrReplaceChild("horn2_m_r1", CubeListBuilder.create().texOffs(184, 105).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(10.5F, -13.0F, 3.5F, -0.4636F, 0.0F, 0.0F));

		PartDefinition eye_trail_r1 = head.addOrReplaceChild("eye_trail_r1", CubeListBuilder.create().texOffs(4, 115).addBox(-5.0F, -0.5F, 0.0F, 5.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0F, -6.0F, -4.5F, 0.0F, 0.3491F, 0.1047F));

		PartDefinition eye_trail_m_r1 = head.addOrReplaceChild("eye_trail_m_r1", CubeListBuilder.create().texOffs(14, 115).addBox(0.0F, -0.5F, 0.0F, 5.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0F, -6.0F, -4.5F, 0.0F, -0.3491F, -0.1047F));

		PartDefinition arm_right = torso.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(94, 49).addBox(-3.0F, 1.0F, -2.5F, 5.0F, 11.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(-11.0F, -13.0F, 0.0F));

		PartDefinition forearm_right = arm_right.addOrReplaceChild("forearm_right", CubeListBuilder.create().texOffs(76, 93).addBox(-3.0F, -1.5F, -3.0F, 6.0F, 3.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(234, 66).addBox(-2.5F, 1.5F, -3.0F, 5.0F, 7.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(174, 80).addBox(-3.0F, 8.5F, -3.5F, 6.0F, 5.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(52, 111).addBox(-3.0F, 11.5F, -4.5F, 6.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, 13.5F, 0.0F));

		PartDefinition sword = forearm_right.addOrReplaceChild("sword", CubeListBuilder.create().texOffs(232, 105).addBox(-1.0F, -1.0F, 3.5F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(130, 93).addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(124, 93).addBox(-1.0F, -4.0F, -5.0F, 2.0F, 8.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(244, 111).addBox(-0.75F, -0.5F, -5.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-0.5F, -2.0F, -30.0F, 1.0F, 4.0F, 25.0F, new CubeDeformation(0.0F))
		.texOffs(146, 0).addBox(0.55F, -0.5F, -27.0F, 0.0F, 1.0F, 20.0F, new CubeDeformation(0.0F))
		.texOffs(186, 0).addBox(-0.55F, -0.5F, -27.0F, 0.0F, 1.0F, 20.0F, new CubeDeformation(0.0F))
		.texOffs(164, 105).addBox(-0.5F, -1.0F, -33.0F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 11.0F, 0.0F));

		PartDefinition quillon_r0_r1 = sword.addOrReplaceChild("quillon_r0_r1", CubeListBuilder.create().texOffs(128, 111).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.75F, -4.75F, -4.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition quillon_r1_r1 = sword.addOrReplaceChild("quillon_r1_r1", CubeListBuilder.create().texOffs(132, 111).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, -5.75F, -4.0F, 0.0F, 0.0F, 1.3258F));

		PartDefinition quillon_l0_r1 = sword.addOrReplaceChild("quillon_l0_r1", CubeListBuilder.create().texOffs(136, 111).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.75F, 4.75F, -4.0F, 0.0F, 0.0F, 2.3562F));

		PartDefinition quillon_l1_r1 = sword.addOrReplaceChild("quillon_l1_r1", CubeListBuilder.create().texOffs(140, 111).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, 5.75F, -4.0F, 0.0F, 0.0F, 1.8158F));

		PartDefinition pauldron_right = arm_right.addOrReplaceChild("pauldron_right", CubeListBuilder.create(), PartPose.offset(-1.0F, -2.0F, 0.0F));

		PartDefinition pgem_r1 = pauldron_right.addOrReplaceChild("pgem_r1", CubeListBuilder.create().texOffs(124, 29).addBox(-7.0F, -2.0F, -6.0F, 10.0F, 5.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(216, 105).addBox(-3.0F, -1.0F, -7.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 1.0F, 0.0F, 0.0F, 0.0F, -0.2443F));

		PartDefinition lame1_r1 = pauldron_right.addOrReplaceChild("lame1_r1", CubeListBuilder.create().texOffs(46, 66).addBox(-5.0F, 0.0F, -5.0F, 7.0F, 4.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 4.0F, 0.0F, 0.0F, 0.0F, -0.4189F));

		PartDefinition lame2_r1 = pauldron_right.addOrReplaceChild("lame2_r1", CubeListBuilder.create().texOffs(200, 80).addBox(-3.0F, 0.0F, -4.0F, 5.0F, 4.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.0F, 8.0F, 0.0F, 0.0F, 0.0F, -0.5585F));

		PartDefinition pspike_a0_r1 = pauldron_right.addOrReplaceChild("pspike_a0_r1", CubeListBuilder.create().texOffs(40, 105).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.5F, -1.5F, -3.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition pspike_a1_r1 = pauldron_right.addOrReplaceChild("pspike_a1_r1", CubeListBuilder.create().texOffs(188, 105).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.75F, -5.0F, -3.0F, 0.0F, 0.0F, -0.3588F));

		PartDefinition pspike_b0_r1 = pauldron_right.addOrReplaceChild("pspike_b0_r1", CubeListBuilder.create().texOffs(48, 105).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.0F, -0.5F, 3.0F, 0.0F, 0.0F, -0.588F));

		PartDefinition pspike_b1_r1 = pauldron_right.addOrReplaceChild("pspike_b1_r1", CubeListBuilder.create().texOffs(14, 111).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.5F, -3.5F, 3.0F, 0.0F, 0.0F, -0.3218F));

		PartDefinition arm_left = torso.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(114, 49).addBox(-2.0F, 1.0F, -2.5F, 5.0F, 11.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(11.0F, -13.0F, 0.0F));

		PartDefinition forearm_left = arm_left.addOrReplaceChild("forearm_left", CubeListBuilder.create().texOffs(100, 93).addBox(-3.0F, -1.5F, -3.0F, 6.0F, 3.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(0, 80).addBox(-2.5F, 1.5F, -3.0F, 5.0F, 7.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(226, 80).addBox(-3.0F, 8.5F, -3.5F, 6.0F, 5.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(66, 111).addBox(-3.0F, 11.5F, -4.5F, 6.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 13.5F, 0.0F));

		PartDefinition shield = forearm_left.addOrReplaceChild("shield", CubeListBuilder.create().texOffs(52, 0).addBox(-7.5F, -14.0F, -5.0F, 16.0F, 26.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(72, 105).addBox(-8.0F, -15.0F, -5.5F, 17.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(112, 105).addBox(-8.0F, 11.0F, -5.5F, 17.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(88, 0).addBox(8.5F, -14.0F, -5.0F, 1.0F, 26.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(94, 0).addBox(-8.5F, -14.0F, -5.0F, 1.0F, 26.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(146, 93).addBox(-2.5F, -4.0F, -7.0F, 6.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(204, 105).addBox(-1.5F, 2.0F, -7.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(122, 111).addBox(-0.5F, -10.0F, -6.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(144, 111).addBox(-5.5F, -11.0F, -6.0F, 12.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(170, 111).addBox(-5.5F, 8.0F, -6.0F, 12.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(68, 29).addBox(5.5F, -10.0F, -6.0F, 1.0F, 18.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(72, 29).addBox(-5.5F, -10.0F, -6.0F, 1.0F, 18.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(240, 105).addBox(8.0F, -17.0F, -5.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(248, 105).addBox(-9.0F, -17.0F, -5.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 6.5F, -3.0F));

		PartDefinition boss_horn_l_r1 = shield.addOrReplaceChild("boss_horn_l_r1", CubeListBuilder.create().texOffs(196, 105).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.5F, -4.5F, -6.0F, 0.0F, 0.0F, 0.588F));

		PartDefinition boss_horn_r_r1 = shield.addOrReplaceChild("boss_horn_r_r1", CubeListBuilder.create().texOffs(200, 105).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5F, -4.5F, -6.0F, 0.0F, 0.0F, -0.588F));

		PartDefinition pauldron_left = arm_left.addOrReplaceChild("pauldron_left", CubeListBuilder.create(), PartPose.offset(1.0F, -2.0F, 0.0F));

		PartDefinition pgem_r2 = pauldron_left.addOrReplaceChild("pgem_r2", CubeListBuilder.create().texOffs(168, 29).addBox(-3.0F, -2.0F, -6.0F, 10.0F, 5.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(224, 105).addBox(0.0F, -1.0F, -7.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.2443F));

		PartDefinition lame1_r2 = pauldron_left.addOrReplaceChild("lame1_r2", CubeListBuilder.create().texOffs(80, 66).addBox(-2.0F, 0.0F, -5.0F, 7.0F, 4.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 4.0F, 0.0F, 0.0F, 0.0F, 0.4189F));

		PartDefinition lame2_r2 = pauldron_left.addOrReplaceChild("lame2_r2", CubeListBuilder.create().texOffs(0, 93).addBox(-2.0F, 0.0F, -4.0F, 5.0F, 4.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.5585F));

		PartDefinition pspike_a0_r2 = pauldron_left.addOrReplaceChild("pspike_a0_r2", CubeListBuilder.create().texOffs(56, 105).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.5F, -1.5F, -3.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition pspike_a1_r2 = pauldron_left.addOrReplaceChild("pspike_a1_r2", CubeListBuilder.create().texOffs(192, 105).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.75F, -5.0F, -3.0F, 0.0F, 0.0F, 0.3588F));

		PartDefinition pspike_b0_r2 = pauldron_left.addOrReplaceChild("pspike_b0_r2", CubeListBuilder.create().texOffs(64, 105).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0F, -0.5F, 3.0F, 0.0F, 0.0F, 0.588F));

		PartDefinition pspike_b1_r2 = pauldron_left.addOrReplaceChild("pspike_b1_r2", CubeListBuilder.create().texOffs(18, 111).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.5F, -3.5F, 3.0F, 0.0F, 0.0F, 0.3218F));

		return LayerDefinition.create(meshdefinition, 256, 128);
	}

	/** The clip of a move; recoveries differ by what they recover from. */
	static String clip(int anim, int recoverFrom) {
		return switch (anim) {
			case ANIM_AWAKEN -> "awaken";
			case ANIM_SLAM_WINDUP -> "slam_windup";
			case ANIM_SLAM -> "slam";
			case ANIM_STUCK -> "stuck";
			case ANIM_SWEEP_WINDUP -> "sweep_windup";
			case ANIM_SWEEP -> "sweep";
			case ANIM_STAGGER -> "stagger";
			case ANIM_CHARGE_WINDUP -> "charge_windup";
			case ANIM_CHARGE -> "charge";
			case ANIM_STUNNED -> "stunned";
			case ANIM_RECOVER -> recoverFrom == ANIM_SWEEP ? "recover_sweep" : recoverFrom == ANIM_CHARGE ? "recover_charge" : "recover";
			default -> null;
		};
	}

	/** Loops run on their own clock; every other move is fitted to its real length. */
	private static boolean looping(int anim) {
		return anim == ANIM_STUCK || anim == ANIM_CHARGE || anim == ANIM_STUNNED;
	}

	private static float smooth(float x) {
		return x * x * (3.0F - 2.0F * x);
	}

	private void play(BoneAnimations anims, CryptWardenEntity e, String clip, int anim, int start, int length, float age, float weight,
		BoneAnimations.Pose pose, List<Object[]> events) {
		// wind-ups are fitted to the server's length and never stop: the hit-stop belongs to the strike that follows
		boolean windup = anim == ANIM_SLAM_WINDUP || anim == ANIM_SWEEP_WINDUP || anim == ANIM_CHARGE_WINDUP;
		float elapsed = age - start - (windup ? 0.0F : CombatFx.lag(e, start, age));
		float seconds = !looping(anim) && length > 0 ? Mth.clamp(elapsed / length, 0.0F, 1.0F) * anims.length(clip) : elapsed / 20.0F;
		anims.add(clip, seconds, weight, pose);
		if (events != null) {
			CombatFx.clipEvents(e, "move", anims, clip, seconds, (name, strength) -> events.add(new Object[] {name, strength}));
		}
	}

	@Override
	public void setupAnim(CryptWardenEntity e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
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
			float t = (e.deathTime + partial) / 20.0F;
			anims.add("death", t, 1.0F, pose);
			CombatFx.clipEvents(e, "move", anims, "death", t, (name, strength) -> events.add(new Object[] {name, strength}));
			calm = Mth.clamp(1.0F - t * 2.0F, 0.0F, 1.0F);
		} else if (e.isDormant()) {
			anims.add("statue", 0.0F, 1.0F, pose);
			calm = 0.0F;
		} else {
			CombatFx.watchHurt(e, 1);
			float fade = smooth(Mth.clamp((age - e.clientAnimChanged) / FADE_TICKS, 0.0F, 1.0F));
			String cur = clip(e.getAnim(), e.clientRecoverFrom);
			String prev = clip(e.clientPrevAnim, e.clientPrevRecoverFrom);
			float wCur = cur == null ? 0.0F : fade;
			float wPrev = prev == null ? 0.0F : 1.0F - fade;
			float wBase = Math.max(0.0F, 1.0F - wCur - wPrev);
			anims.add("breath", age / 20.0F, 1.0F, pose);
			if (wBase > 0.0F) {
				float walk = Mth.clamp(limbSwingAmount * 2.5F, 0.0F, 1.0F);
				float turn = Mth.clamp(Math.abs(Mth.wrapDegrees(e.yBodyRot - e.yBodyRotO)) / TURN, 0.0F, 1.0F) * (1.0F - walk);
				anims.add("idle", age / 20.0F, wBase * (1.0F - walk - turn), pose);
				anims.add("walk", limbSwing / 12.0F, walk * wBase, pose);
				anims.add("turn", age / 20.0F, turn * wBase, pose);
				if (walk > 0.5F) {
					CombatFx.clipEvents(e, "walk", anims, "walk", limbSwing / 12.0F, (name, strength) -> events.add(new Object[] {name, strength}));
				}
			}
			if (prev != null) {
				this.play(anims, e, prev, e.clientPrevAnim, e.clientPrevAnimStart, e.clientPrevAnimLength, age, wPrev, pose, null);
			}
			if (cur != null) {
				this.play(anims, e, cur, e.getAnim(), e.clientAnimStart, e.clientAnimLength, age, wCur, pose, events);
			}
			if (e.hurtTime > 0) {
				anims.add(hitFromFront(e) ? "hurt_front" : "hurt_back", (e.hurtDuration - e.hurtTime + partial) / 20.0F, 1.0F, pose);
			}
		}
		pose.applyTo(this.parts);
		// the mail skirt's flaps ride on the legs; the cloth swings on springs
		ModelPart front = this.parts.get("skirt_front"), back = this.parts.get("skirt_back");
		if (front != null && back != null) {
			front.xRot += Math.min(0.0F, Math.min(this.legRight.xRot, this.legLeft.xRot)) * 0.7F;
			back.xRot += Math.max(0.0F, Math.max(this.legRight.xRot, this.legLeft.xRot)) * 0.7F;
		}
		HeadLook.apply(e, age, e.deathTime > 0 || e.isDormant() ? 0.0F : lookWeight(e.getAnim()), this.look, headYaw, headPitch);
		this.cloth.apply(e, age, partial, this.body.xRot + this.torso.xRot, this.body.yRot + this.torso.yRot, calm, this.parts);
		for (Object[] ev : events) {
			this.fire(e, partial, (String) ev[0], (Float) ev[1]);
		}
		this.ambient(e, age, partial);
		// art 4.0 mesh VFX: the statue cracks open round its feet as it wakes
		if (e.getAnim() == ANIM_AWAKEN && MeshFx.once(e, 0, e.clientAnimStart) && e.tickCount - e.clientAnimStart < 4) {
			MeshFx.crack(e.position(), 2.8F);
			MeshFx.pillar(e.position(), 1.0F, 4.0F, 1.6F);
		}
	}

	/**
	 * Art 4.0, the knight alive: the visor's eyes leave afterimages as the helm turns, the glowing blade edge sheds
	 * embers and a drop gathers at its tip, a rune now and then lifts off the shield's
	 * skull; enraged, soul fire burns on both pauldrons. Fades in with the stone cracking off.
	 */
	private void ambient(CryptWardenEntity e, float age, float partial) {
		if (e.deathTime > 0 || e.isDormant()) {
			return;
		}
		float awake = CryptWardenRenderer.awake(e, partial);
		var r = e.getRandom();
		for (int i = MobFx.due(e, 0, age, 36.0F * awake); i > 0; i--) {
			float side = (i & 1) == 0 ? 1.0F : -1.0F;
			MobFx.spawn(e, dev.remnants.registry.ModParticles.EYE_TRAIL, MobFx.at(e, partial, side * 3.0F, -6.0F, -5.4F, this.root, this.body, this.torso, this.head), 0, 0, 0);
		}
		for (int i = MobFx.due(e, 1, age, 4.0F * awake); i > 0; i--) {
			Vec3 p = MobFx.at(e, partial, 0.0F, 0.0F, -8.0F - r.nextFloat() * 22.0F, this.root, this.body, this.torso, this.armRight, this.forearmRight, this.sword);
			MobFx.spawn(e, dev.remnants.registry.ModParticles.SOUL_EMBER, p, 0.06, 0.0, 0.025, 0.0, 0.02);
		}
		for (int i = MobFx.due(e, 2, age, 0.8F * awake); i > 0; i--) {
			MobFx.spawn(e, dev.remnants.registry.ModParticles.SOUL_DRIP, MobFx.at(e, partial, 0.0F, 0.0F, -30.0F, this.root, this.body, this.torso, this.armRight, this.forearmRight, this.sword), 0, 0, 0);
		}
		for (int i = MobFx.due(e, 4, age, 0.7F * awake); i > 0; i--) {
			MobFx.spawn(e, dev.remnants.registry.ModParticles.RUNE, MobFx.at(e, partial, 0.5F, -2.0F, -6.0F, this.shield), 0.1, 0.0, 0.03, 0.0, 0.01);
		}
		if (e.isEnraged()) {
			for (int i = MobFx.due(e, 5, age, 14.0F); i > 0; i--) {
				Vec3 p = MobFx.at(e, partial, 0.0F, -3.0F, 0.0F, (i & 1) == 0 ? this.pauldronRight : this.pauldronLeft);
				MobFx.spawn(e, dev.remnants.registry.ModParticles.SOUL_FLAME, p, 0.25, 0.0, 0.035, 0.0, 0.02);
			}
		}
	}

	/** How much the helm follows its target per move: fully, except while it wakes, is stuck or dazed. */
	private static float lookWeight(int anim) {
		return switch (anim) {
			// HeadLook v2 holds the eyes on the target through the keyframes' own motion: no fading out for a strike
			case ANIM_NONE, ANIM_SLAM_WINDUP, ANIM_SWEEP_WINDUP, ANIM_CHARGE_WINDUP, ANIM_RECOVER, ANIM_SLAM, ANIM_SWEEP, ANIM_CHARGE -> 1.0F;
			default -> 0.0F;   // awakening, stuck, staggered, stunned: the clip has the head
		};
	}

	private static boolean hitFromFront(CryptWardenEntity e) {
		var src = e.getLastDamageSource();
		Vec3 at = src == null ? null : src.getSourcePosition();
		return at == null || e.angleTo(at) <= 90.0F;
	}

	/** Clip events: glints on the visor / blade, sparks at the sword tip, dust under a foot or where the body lands. */
	private void fire(CryptWardenEntity e, float partial, String name, float strength) {
		Level level = e.level();
		switch (name) {
			case "glint_visor" -> CombatFx.glint(ModelPoints.world(e, partial, 0.0F, -5.5F, -5.8F, this.root, this.body, this.torso, this.head), strength);
			case "glint_blade" -> CombatFx.glint(ModelPoints.world(e, partial, 0.0F, 0.0F, -22.0F, this.root, this.body, this.torso, this.armRight,
				this.forearmRight, this.sword), strength);
			case "impact_blade" -> {
				Vec3 tip = ModelPoints.world(e, partial, 0.0F, 0.0F, -30.0F, this.root, this.body, this.torso, this.armRight, this.forearmRight, this.sword);
				for (int i = 0; i < 4 + Math.round(6 * strength); i++) {
					level.addParticle(dev.remnants.registry.ModParticles.BONE_SPARK, tip.x, tip.y, tip.z, (e.getRandom().nextDouble() - 0.5) * 0.6, e.getRandom().nextDouble() * 0.4,
						(e.getRandom().nextDouble() - 0.5) * 0.6);
				}
			}
			case "step" -> {
				boolean right = this.legRight.xRot <= this.legLeft.xRot;   // the leg further forward has just landed
				Vec3 foot = right ? ModelPoints.world(e, partial, 0.0F, 12.0F, -1.0F, this.root, this.legRight, this.shinRight)
					: ModelPoints.world(e, partial, 0.0F, 12.0F, -1.0F, this.root, this.legLeft, this.shinLeft);
				dust(level, foot, 2 + Math.round(4 * strength), 0.08 * strength);
				for (int i = 0; i < 1 + Math.round(2 * strength); i++) {
					MobFx.spawn(e, dev.remnants.registry.ModParticles.GRAVE_DUST, foot, 0.3, 0.0, 0.02, 0.0, 0.06);
				}
			}
			case "impact_knees", "impact_body" -> dust(level, e.position(), 6 + Math.round(10 * strength), 0.15 * strength);
			default -> {
			}
		}
	}

	private static void dust(Level level, Vec3 at, int n, double speed) {
		BlockState below = level.getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
		if (below.isAir()) {
			return;
		}
		BlockParticleOption p = new BlockParticleOption(ParticleTypes.BLOCK, below);
		for (int i = 0; i < n; i++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			level.addParticle(p, at.x, at.y + 0.05, at.z, Math.cos(a) * speed, 0.05 + level.random.nextDouble() * 0.1, Math.sin(a) * speed);
		}
	}

	@Override
	public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
		this.root.render(pose, buffer, light, overlay, color);
	}
}
