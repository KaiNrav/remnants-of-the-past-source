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
import dev.remnants.entity.KeenerEntity;
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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.remnants.entity.KeenerEntity.*;

/**
 * The Keener, built like the Soul King: a hooded skull with a hinged jaw over a pale torn shroud whose
 * hems fade into soul light, belled sleeves, bone hands, shackle chains. Drawn translucent (KeenerRenderer).
 *
 * <p>Art 2.0: idle + move are the base; the wail and the scream are fitted to their server lengths (the blast frame
 * lands on {@link KeenerEntity#SCREAM_WINDUP}), cross-faded with a smoothstep; hits play an additive hurt clip with a
 * one-tick hit-stop; clip events put glints in the eye sockets and a soul gust on the scream; the veil strips, skirt
 * panels and the hood's point swing on springs ({@link SecondaryMotion}). The head follows its target.
 */
public class KeenerModel extends EntityModel<KeenerEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(RemnantsMod.id("keener"), "main");
	private static final ResourceLocation ANIMS = RemnantsMod.id("keener");
	private static final float FADE_TICKS = 4.0F;

	private final ModelPart mesh;
	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart head;
	private final HeadLook.Rig look;
	private final ModelPart[][] veils = new ModelPart[3][], hems = new ModelPart[4][];
	private Map<String, ModelPart> parts = Map.of();
	private int partsVersion = Integer.MIN_VALUE;
	private final SecondaryMotion cloth = new SecondaryMotion()
		//    bone          parent      k     c     move  fall  turn   lag   follow minX   maxX  maxZ
		.link("veil0", null, 0.17F, 0.24F, 2.8F, 2.0F, 0.020F, 0.90F, 0.0F, -0.2F, 1.4F, 0.5F)
		.link("veil1", null, 0.15F, 0.23F, 3.0F, 2.0F, 0.020F, 0.95F, 0.0F, -0.2F, 1.45F, 0.5F)
		.link("veil2", null, 0.17F, 0.24F, 2.8F, 2.0F, 0.020F, 0.90F, 0.0F, -0.2F, 1.4F, 0.5F)
		.link("veil0_low", "veil0", 0.12F, 0.20F, 1.6F, 1.4F, 0.015F, 0.35F, 0.5F, -0.4F, 1.1F, 0.6F)
		.link("veil1_low", "veil1", 0.11F, 0.19F, 1.7F, 1.4F, 0.015F, 0.35F, 0.5F, -0.4F, 1.1F, 0.6F)
		.link("veil2_low", "veil2", 0.12F, 0.20F, 1.6F, 1.4F, 0.015F, 0.35F, 0.5F, -0.4F, 1.1F, 0.6F)
		.link("skirt_b0", null, 0.19F, 0.27F, 2.0F, 1.6F, 0.015F, 0.60F, 0.0F, -0.2F, 1.0F, 0.4F)
		.link("skirt_b1", null, 0.20F, 0.27F, 2.0F, 1.6F, 0.015F, 0.60F, 0.0F, -0.2F, 1.0F, 0.4F)
		.link("skirt_f0", null, 0.21F, 0.29F, 1.1F, 1.3F, 0.012F, 0.55F, 0.0F, -0.6F, 0.35F, 0.3F)
		.link("skirt_f1", null, 0.21F, 0.29F, 1.1F, 1.3F, 0.012F, 0.55F, 0.0F, -0.6F, 0.35F, 0.3F)
		.link("hood_point", null, 0.26F, 0.28F, 1.0F, 1.0F, 0.020F, 0.50F, 0.0F, -0.3F, 0.6F, 0.35F);

	public KeenerModel(ModelPart model) {
		super(RenderType::entityTranslucent);
		this.mesh = model;
		this.root = model.getChild("root");
		this.body = this.root.getChild("body");
		this.head = this.body.getChild("head");
		// a mourner's hunch: the head does nearly all of it, a little sway in the body
		for (int i = 0; i < 3; i++) {
			ModelPart veil = this.head.getChild("veil" + i);
			this.veils[i] = new ModelPart[] {this.root, this.body, this.head, veil, veil.getChild("veil" + i + "_low")};
		}
		String[] skirts = {"skirt_f0", "skirt_f1", "skirt_b0", "skirt_b1"};
		for (int i = 0; i < 4; i++) {
			this.hems[i] = new ModelPart[] {this.root, this.body, this.body.getChild(skirts[i])};
		}
		this.look = new HeadLook.Rig(this.root, this.body, this.head)
			.shares(new float[] {0.0F, 0.15F, 0.85F}, new float[] {0.0F, 0.1F, 0.9F})
			.eye(0.0F, -4.0F, -4.0F).limits(75.0F, 35.0F, 45.0F).speed(8.0F).glance(10.0F);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -8.0F, -3.5F, 9.0F, 22.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(48, 85).addBox(-2.5F, -2.5F, -4.0F, 5.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(98, 85).addBox(-3.0F, -1.0F, -4.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(112, 85).addBox(-3.0F, -2.75F, -4.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(92, 85).addBox(-1.0F, -2.25F, -4.25F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 63).addBox(-6.5F, -7.0F, -4.5F, 13.0F, 4.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -20.0F, 0.0F));

		PartDefinition pleat_lf_r1 = body.addOrReplaceChild("pleat_lf_r1", CubeListBuilder.create().texOffs(36, 29).addBox(-1.5F, -0.5F, -0.5F, 3.0F, 19.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0F, -1.0F, -3.8F, -0.1396F, -0.7854F, -0.1396F));

		PartDefinition pleat_lb_r1 = body.addOrReplaceChild("pleat_lb_r1", CubeListBuilder.create().texOffs(28, 29).addBox(-1.5F, -0.5F, -0.5F, 3.0F, 19.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0F, -1.0F, 3.8F, 0.1396F, 0.7854F, -0.1396F));

		PartDefinition pleat_rf_r1 = body.addOrReplaceChild("pleat_rf_r1", CubeListBuilder.create().texOffs(20, 29).addBox(-1.5F, -0.5F, -0.5F, 3.0F, 19.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0F, -1.0F, -3.8F, -0.1396F, 0.7854F, 0.1396F));

		PartDefinition pleat_rb_r1 = body.addOrReplaceChild("pleat_rb_r1", CubeListBuilder.create().texOffs(12, 29).addBox(-1.5F, -0.5F, -0.5F, 3.0F, 19.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0F, -1.0F, 3.8F, 0.1396F, -0.7854F, 0.1396F));

		PartDefinition skirt_s_m_r1 = body.addOrReplaceChild("skirt_s_m_r1", CubeListBuilder.create().texOffs(46, 0).addBox(-0.5F, 0.0F, -3.0F, 1.0F, 20.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0F, -2.0F, 0.0F, 0.0F, 0.0F, -0.1396F));

		PartDefinition skirt_s_r1 = body.addOrReplaceChild("skirt_s_r1", CubeListBuilder.create().texOffs(32, 0).addBox(-0.5F, 0.0F, -3.0F, 1.0F, 20.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0F, -2.0F, 0.0F, 0.0F, 0.0F, 0.1396F));

		PartDefinition skirt_f0 = body.addOrReplaceChild("skirt_f0", CubeListBuilder.create().texOffs(112, 0).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 19.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, 0.0F, -4.0F, -0.1396F, 0.0F, 0.0F));

		PartDefinition skirt_f1 = body.addOrReplaceChild("skirt_f1", CubeListBuilder.create().texOffs(44, 29).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 18.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.5F, 0.0F, -4.0F, -0.1396F, 0.0F, 0.0F));

		PartDefinition skirt_b0 = body.addOrReplaceChild("skirt_b0", CubeListBuilder.create().texOffs(60, 0).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 22.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, -2.0F, 4.0F, 0.1396F, 0.0F, 0.0F));

		PartDefinition skirt_b1 = body.addOrReplaceChild("skirt_b1", CubeListBuilder.create().texOffs(0, 29).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 19.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.5F, -2.0F, 4.0F, 0.1396F, 0.0F, 0.0F));

		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(92, 29).addBox(-3.5F, -9.0F, -3.5F, 7.0F, 7.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(18, 76).addBox(-2.5F, -2.0F, -4.0F, 5.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(52, 63).addBox(-5.0F, -11.0F, -5.0F, 10.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(92, 63).addBox(-5.0F, -9.0F, 4.0F, 10.0F, 11.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(72, 0).addBox(-5.0F, -9.0F, -5.0F, 1.0F, 11.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(92, 0).addBox(4.0F, -9.0F, -5.0F, 1.0F, 11.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(60, 85).addBox(-5.5F, -10.0F, -6.0F, 11.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(84, 85).addBox(-1.5F, -9.0F, -6.5F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 91).addBox(-0.5F, -8.5F, -7.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -9.0F, 0.0F));

		PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(38, 76).addBox(-2.5F, 0.5F, -4.5F, 5.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.5F, 0.5F));

		PartDefinition hood_point = head.addOrReplaceChild("hood_point", CubeListBuilder.create().texOffs(0, 76).addBox(-3.0F, 0.0F, -1.0F, 6.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -10.5F, 4.0F, 1.0821F, 0.0F, 0.0F));

		PartDefinition veil0 = head.addOrReplaceChild("veil0", CubeListBuilder.create().texOffs(76, 29).addBox(-1.5F, 0.0F, -0.5F, 3.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5F, -8.0F, 5.5F, 0.0698F, 0.0F, 0.0F));

		PartDefinition veil0_low = veil0.addOrReplaceChild("veil0_low", CubeListBuilder.create().texOffs(80, 49).addBox(-1.5F, 0.0F, -0.5F, 3.0F, 13.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 16.0F, 0.0F));

		PartDefinition veil1 = head.addOrReplaceChild("veil1", CubeListBuilder.create().texOffs(56, 29).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -8.0F, 5.5F, 0.0698F, 0.0F, 0.0F));

		PartDefinition veil1_low = veil1.addOrReplaceChild("veil1_low", CubeListBuilder.create().texOffs(66, 29).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 16.0F, 0.0F));

		PartDefinition veil2 = head.addOrReplaceChild("veil2", CubeListBuilder.create().texOffs(84, 29).addBox(-1.5F, 0.0F, -0.5F, 3.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5F, -8.0F, 5.5F, 0.0698F, 0.0F, 0.0F));

		PartDefinition veil2_low = veil2.addOrReplaceChild("veil2_low", CubeListBuilder.create().texOffs(44, 63).addBox(-1.5F, 0.0F, -0.5F, 3.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 16.0F, 0.0F));

		PartDefinition arm_right = body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(48, 49).addBox(-3.0F, -1.0F, -2.0F, 4.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 49).addBox(-4.0F, 8.0F, -3.0F, 6.0F, 8.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(0, 85).addBox(-3.0F, 14.0F, -2.5F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.2F))
		.texOffs(58, 76).addBox(-2.5F, 16.0F, -1.5F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.5F, -7.0F, 0.0F));

		PartDefinition chain1_r1 = arm_right.addOrReplaceChild("chain1_r1", CubeListBuilder.create().texOffs(36, 85).addBox(-0.5F, -2.5F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 23.5F, 3.0F, -0.1964F, 0.0F, 3.0419F));

		PartDefinition chain0_r1 = arm_right.addOrReplaceChild("chain0_r1", CubeListBuilder.create().texOffs(32, 85).addBox(-0.5F, -2.5F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 18.5F, 2.0F, -0.1964F, 0.0F, -3.0419F));

		PartDefinition finger3_r1 = arm_right.addOrReplaceChild("finger3_r1", CubeListBuilder.create().texOffs(94, 76).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.65F, 22.5F, -1.5F, 0.1633F, 0.0F, -2.9927F));

		PartDefinition finger2_r1 = arm_right.addOrReplaceChild("finger2_r1", CubeListBuilder.create().texOffs(90, 76).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.65F, 22.5F, -1.9F, 0.2911F, 0.0F, -3.0916F));

		PartDefinition finger1_r1 = arm_right.addOrReplaceChild("finger1_r1", CubeListBuilder.create().texOffs(86, 76).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.6F, 22.5F, -2.0F, 0.3216F, 0.0F, 3.1083F));

		PartDefinition finger0_r1 = arm_right.addOrReplaceChild("finger0_r1", CubeListBuilder.create().texOffs(82, 76).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 22.5F, -1.8F, 0.2584F, 0.0F, 3.009F));

		PartDefinition arm_left = body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(64, 49).addBox(-1.0F, -1.0F, -2.0F, 4.0F, 10.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(24, 49).addBox(-2.0F, 8.0F, -3.0F, 6.0F, 8.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(16, 85).addBox(-1.0F, 14.0F, -2.5F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.2F))
		.texOffs(70, 76).addBox(-0.5F, 16.0F, -1.5F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(5.5F, -7.0F, 0.0F));

		PartDefinition chain1_r2 = arm_left.addOrReplaceChild("chain1_r2", CubeListBuilder.create().texOffs(44, 85).addBox(-0.5F, -2.5F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.25F, 23.5F, 3.0F, -0.1964F, 0.0F, -3.0419F));

		PartDefinition chain0_r2 = arm_left.addOrReplaceChild("chain0_r2", CubeListBuilder.create().texOffs(40, 85).addBox(-0.5F, -2.5F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.25F, 18.5F, 2.0F, -0.1964F, 0.0F, 3.0419F));

		PartDefinition finger3_r2 = arm_left.addOrReplaceChild("finger3_r2", CubeListBuilder.create().texOffs(110, 76).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.65F, 22.5F, -1.5F, 0.1633F, 0.0F, 2.9927F));

		PartDefinition finger2_r2 = arm_left.addOrReplaceChild("finger2_r2", CubeListBuilder.create().texOffs(106, 76).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.65F, 22.5F, -1.9F, 0.2911F, 0.0F, 3.0916F));

		PartDefinition finger1_r2 = arm_left.addOrReplaceChild("finger1_r2", CubeListBuilder.create().texOffs(102, 76).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.6F, 22.5F, -2.0F, 0.3216F, 0.0F, -3.1083F));

		PartDefinition finger0_r2 = arm_left.addOrReplaceChild("finger0_r2", CubeListBuilder.create().texOffs(98, 76).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, 22.5F, -1.8F, 0.2584F, 0.0F, -3.009F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	static String clip(int anim) {
		return switch (anim) {
			case ANIM_WAIL -> "wail";
			case ANIM_SCREAM -> "scream";
			case ANIM_STUNNED -> "stunned";
			default -> null;
		};
	}

	/** Server length of a move in ticks (0 = a loop on its own clock). */
	private static int ticks(int anim) {
		return anim == ANIM_WAIL ? WAIL_TICKS : anim == ANIM_SCREAM ? SCREAM_TICKS : 0;
	}

	private static float smooth(float x) {
		return x * x * (3.0F - 2.0F * x);
	}

	private void play(BoneAnimations anims, KeenerEntity e, String clip, int anim, int start, float age, float weight, BoneAnimations.Pose pose,
		List<Object[]> events) {
		float elapsed = age - start - CombatFx.lag(e, start, age);
		int len = ticks(anim);
		float seconds = len > 0 ? Mth.clamp(elapsed / len, 0.0F, 1.0F) * anims.length(clip) : elapsed / 20.0F;
		anims.add(clip, seconds, weight, pose);
		if (events != null) {
			CombatFx.clipEvents(e, "move", anims, clip, seconds, (name, strength) -> events.add(new Object[] {name, strength}));
		}
	}

	@Override
	public void setupAnim(KeenerEntity e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
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
			calm = 0.5F;
		} else {
			CombatFx.watchHurt(e, 1);
			anims.add("idle", age / 20.0F, 1.0F, pose);
			float moving = Mth.clamp((float) e.getDeltaMovement().horizontalDistance() * 8.0F, 0.0F, 1.0F);
			anims.add("move", age / 20.0F, moving, pose);
			float fade = smooth(Mth.clamp((age - e.clientAnimChanged) / FADE_TICKS, 0.0F, 1.0F));
			String prev = clip(e.clientPrevAnim);
			String cur = clip(e.getAnim());
			if (prev != null) {
				this.play(anims, e, prev, e.clientPrevAnim, e.clientPrevAnimStart, age, 1.0F - fade, pose, null);
			}
			if (cur != null) {
				this.play(anims, e, cur, e.getAnim(), e.clientAnimStart, age, fade, pose, events);
			}
			if (e.hurtTime > 0) {
				var src = e.getLastDamageSource();
				Vec3 at = src == null ? null : src.getSourcePosition();
				boolean front = at == null || Vec3.directionFromRotation(0, e.yBodyRot).dot(at.subtract(e.position()).multiply(1, 0, 1)) >= 0;
				anims.add(front ? "hurt_front" : "hurt_back", (e.hurtDuration - e.hurtTime + partial) / 20.0F, 1.0F, pose);
			}
		}
		pose.applyTo(this.parts);
		float look = e.deathTime > 0 || e.getAnim() == ANIM_STUNNED ? 0.0F : 1.0F;
		HeadLook.apply(e, age, look, this.look, headYaw, headPitch);
		this.cloth.apply(e, age, partial, this.body.xRot + this.head.xRot * 0.5F, this.body.yRot + this.head.yRot * 0.5F, calm, this.parts);
		for (Object[] ev : events) {
			this.fire(e, partial, (String) ev[0], (Float) ev[1]);
		}
		this.ambient(e, age, partial);
		// art 4.0 mesh VFX: a pale circle under its wail, a shockwave from its scream
		if (e.isRevealed() && MeshFx.once(e, 0, e.clientAnimStart) && e.tickCount - e.clientAnimStart < 4) {
			if (e.getAnim() == ANIM_WAIL) {
				MeshFx.runeCircle(e, 2.4F, 3.0F);
			} else if (e.getAnim() == ANIM_SCREAM) {
				MeshFx.shockwave(e.position(), 4.5F);
			}
		}
	}

	/**
	 * Art 4.0: hidden, soul light drips off its hem and nothing else (the one tell, as before). Revealed, the veils
	 * smoke, its sockets leave afterimages, it weeps soul, and while it wails runes rise round it.
	 */
	private void ambient(KeenerEntity e, float age, float partial) {
		if (e.deathTime > 0) {
			return;
		}
		var r = e.getRandom();
		boolean shown = e.isRevealed();
		for (int i = MobFx.due(e, 0, age, shown ? 3.0F : 2.0F); i > 0; i--) {
			Vec3 p = MobFx.at(e, partial, (r.nextFloat() - 0.5F) * 4.0F, 18.0F, 0.0F, this.hems[r.nextInt(4)]);
			MobFx.spawn(e, dev.remnants.registry.ModParticles.SOUL_DRIP, p, 0, 0, 0);
		}
		if (!shown) {
			return;
		}
		for (int i = MobFx.due(e, 1, age, 7.0F); i > 0; i--) {
			Vec3 p = MobFx.at(e, partial, (r.nextFloat() - 0.5F) * 3.0F, 6.0F + r.nextFloat() * 8.0F, 0.0F, this.veils[r.nextInt(3)]);
			MobFx.spawn(e, dev.remnants.registry.ModParticles.SOUL_WISP, p, 0.05, 0.0, 0.012, 0.0, 0.012);
		}
		for (int i = MobFx.due(e, 2, age, 24.0F); i > 0; i--) {
			float side = (i & 1) == 0 ? 1.5F : -1.5F;
			MobFx.spawn(e, dev.remnants.registry.ModParticles.EYE_TRAIL, MobFx.at(e, partial, side, -4.5F, -3.9F, this.root, this.body, this.head), 0, 0, 0);
		}
		for (int i = MobFx.due(e, 3, age, 0.9F); i > 0; i--) {
			float side = r.nextBoolean() ? 1.5F : -1.5F;
			MobFx.spawn(e, dev.remnants.registry.ModParticles.SOUL_DRIP, MobFx.at(e, partial, side, -3.5F, -3.9F, this.root, this.body, this.head), 0, 0, 0);
		}
		if (e.isWailing()) {
			for (int i = MobFx.due(e, 4, age, 9.0F); i > 0; i--) {
				double a = r.nextDouble() * Math.PI * 2.0, d = 0.6 + r.nextDouble() * 0.6;
				Vec3 p = e.position().add(Math.cos(a) * d, 0.2 + r.nextDouble() * 1.4, Math.sin(a) * d);
				MobFx.spawn(e, dev.remnants.registry.ModParticles.RUNE, p, 0, 0.04, 0);
			}
		}
	}

	/** Clip events: a glint in the sockets as a move begins; a gust of soul out of the jaw on the scream. */
	private void fire(KeenerEntity e, float partial, String name, float strength) {
		Vec3 eyes = ModelPoints.world(e, partial, 0.0F, -4.0F, -4.0F, this.root, this.body, this.head);
		switch (name) {
			case "glint_eyes" -> CombatFx.glint(eyes, strength);
			case "scream" -> {
				Vec3 f = Vec3.directionFromRotation(0, e.yBodyRot);
				for (int i = 0; i < 12; i++) {
					double s = 0.25 + e.getRandom().nextDouble() * 0.35;
					e.level().addParticle(ParticleTypes.SOUL, eyes.x, eyes.y - 0.2, eyes.z, f.x * s + (e.getRandom().nextDouble() - 0.5) * 0.2,
						(e.getRandom().nextDouble() - 0.3) * 0.15, f.z * s + (e.getRandom().nextDouble() - 0.5) * 0.2);
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
