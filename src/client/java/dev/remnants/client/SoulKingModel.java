package dev.remnants.client;

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
import dev.remnants.entity.SoulKingEntity;
import dev.remnants.entity.SoulKingEntity.Action;
import java.util.Locale;
import java.util.Map;
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
import net.minecraft.world.phys.Vec3;

/**
 * The Soul King: a floating three-skulled lich king with four skeletal arms (the outer pair thrust sideways and
 * bent at an elbow joint), hinged jaws, crown, pauldrons, torn wings and four soul weapons. The geometry was
 * modelled in Blockbench and exported with its Java codec.
 *
 * <p>Animation is keyframed (assets/remnants_of_the_past/animations/soul_king.json, editable in
 * Blockbench's Animate tab) and layered: idle + move loops, the current boss action (cross-faded when interrupted),
 * the head cast, intro and death, hurt_front / hurt_back; a thin procedural layer on top aims the heads and the bow at
 * the target. Art 2.0: the cape strips, skirt panels, tabard and every ragged wing strip are bones of their own that
 * swing on springs ({@link SecondaryMotion}: trailing the body, the torso / robe and the wing spar they hang from, a
 * slow gust always on); attack clips flash a glint on their weapon as the wind-up begins.
 */
public class SoulKingModel extends EntityModel<SoulKingEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(RemnantsMod.id("soul_king"), "main");
	private static final ResourceLocation ANIMS = RemnantsMod.id("soul_king");
	private static final float FADE_TICKS = 5.0F;

	private final ModelPart mesh;
	private final ModelPart root;
	private final ModelPart torso;
	public final ModelPart headCenter;
	/** The entity's own left / right. Blockbench's Java export mirrors X, so the bone names are swapped here. */
	public final ModelPart headLeft;
	public final ModelPart headRight;
	private final ModelPart armBow;
	private final HeadLook.Rig look;
	private final HeadLook.Rig lookLeft, lookRight;
	private final ModelPart[] crown, pauldronLeft, pauldronRight;
	private final ModelPart[][] hems;
	/** Crown spike tips in the crowned head's space: five in front, three a side, three behind. */
	private static final float[][] CROWN_TIPS = {{6.0F, -22.5F, -7.2F}, {3.0F, -25.5F, -7.4F}, {0.0F, -29.5F, -7.6F}, {-3.0F, -25.5F, -7.4F},
		{-6.0F, -22.5F, -7.2F}, {8.2F, -24.5F, 2.0F}, {8.0F, -22.5F, -2.0F}, {7.8F, -21.5F, 6.0F}, {-8.2F, -24.5F, 2.0F},
		{-8.0F, -22.5F, -2.0F}, {-7.8F, -21.5F, 6.0F}, {4.0F, -22.5F, 8.2F}, {0.0F, -24.5F, 8.4F}, {-4.0F, -22.5F, 8.2F}};
	/** Bone chains root -> weapon bone, indexed by ArsenalItem form (sword, bow, trident, mace). The weapon bones keep
	 * their animated poses but their own cubes are hidden: SoulKingWeaponLayer draws the Arsenal's 3D item models
	 * there instead. */
	private final ModelPart[][] weapons;
	private Map<String, ModelPart> parts = Map.of();
	private int partsVersion = Integer.MIN_VALUE;
	/** Cloth hanging from the torso: the back cape and the wing strips (which also trail their wing's flap). */
	private final SecondaryMotion cloth = wings(new SecondaryMotion()
		//    bone     parent k      c      move  fall  turn    lag    follow minX    maxX  maxZ
		.link("cape0", null, 0.15F, 0.24F, 2.4F, 1.6F, 0.018F, 0.90F, 0.0F, -0.12F, 1.2F, 0.45F)
		.link("cape1", null, 0.14F, 0.23F, 2.6F, 1.6F, 0.018F, 0.95F, 0.0F, -0.12F, 1.25F, 0.45F)
		.link("cape2", null, 0.15F, 0.24F, 2.4F, 1.6F, 0.018F, 0.90F, 0.0F, -0.12F, 1.2F, 0.45F))
		.wind(0.05F, 0.07F);
	/** Cloth hanging from the robe (its own bone under root): skirt panels and the tabard. */
	private final SecondaryMotion skirts = new SecondaryMotion()
		.link("skirt_b0", null, 0.19F, 0.27F, 1.8F, 1.4F, 0.015F, 0.60F, 0.0F, -0.15F, 0.9F, 0.35F)
		.link("skirt_b1", null, 0.18F, 0.27F, 1.9F, 1.4F, 0.015F, 0.60F, 0.0F, -0.15F, 0.9F, 0.35F)
		.link("skirt_b2", null, 0.19F, 0.27F, 1.8F, 1.4F, 0.015F, 0.60F, 0.0F, -0.15F, 0.9F, 0.35F)
		.link("skirt_f0", null, 0.22F, 0.30F, 1.0F, 1.1F, 0.012F, 0.55F, 0.0F, -0.45F, 0.3F, 0.3F)
		.link("skirt_f1", null, 0.21F, 0.30F, 1.0F, 1.1F, 0.012F, 0.55F, 0.0F, -0.45F, 0.3F, 0.3F)
		.link("tabard", null, 0.24F, 0.30F, 0.9F, 1.1F, 0.012F, 0.55F, 0.0F, -0.55F, 0.25F, 0.3F)
		.link("skirt_s0", null, 0.22F, 0.30F, 0.6F, 0.8F, 0.020F, 0.40F, 0.0F, -0.3F, 0.3F, 0.45F)
		.link("skirt_s1", null, 0.21F, 0.30F, 0.8F, 0.8F, 0.020F, 0.40F, 0.0F, -0.3F, 0.3F, 0.45F)
		.link("skirt_s0_m", null, 0.22F, 0.30F, 0.6F, 0.8F, 0.020F, 0.40F, 0.0F, -0.3F, 0.3F, 0.45F)
		.link("skirt_s1_m", null, 0.21F, 0.30F, 0.8F, 0.8F, 0.020F, 0.40F, 0.0F, -0.3F, 0.3F, 0.45F)
		.wind(0.035F, 0.06F);

	/** Four strips under each wing spar and four under each wing tip, longest near the body: softer further out. */
	private static SecondaryMotion wings(SecondaryMotion m) {
		for (String side : new String[] {"left", "right"}) {
			float sign = side.equals("left") ? 1.0F : -1.0F;
			for (String seg : new String[] {"wing_" + side, "wing_" + side + "_tip"}) {
				boolean tip = seg.endsWith("_tip");
				for (int i = 0; i < 4; i++) {
					float k = (tip ? 0.26F : 0.2F) + 0.015F * i;
					m.link(seg + "_s" + i, null, k, 0.22F, 1.3F, 1.0F, 0.010F, 0.45F, 0.0F, -0.7F, 0.9F, 0.5F)
						.anchor(seg, tip ? 0.9F : 0.75F, 0.35F * sign);
				}
			}
		}
		return m;
	}

	public SoulKingModel(ModelPart model) {
		super(RenderType::entityCutoutNoCull);
		this.mesh = model;
		this.root = model.getChild("root");
		this.torso = this.root.getChild("torso");
		this.headCenter = this.torso.getChild("head_center");
		this.headLeft = this.torso.getChild("head_right");
		this.headRight = this.torso.getChild("head_left");
		this.armBow = this.torso.getChild("arm_upper_left");
		// the crowned head leads and the torso gives a quarter of the turn; the side heads aim from their own eyes
		this.look = new HeadLook.Rig(this.root, this.torso, this.headCenter)
			.shares(new float[] {0.0F, 0.25F, 0.75F}, new float[] {0.0F, 0.1F, 0.9F})
			.eye(0.0F, -10.0F, -6.0F).limits(70.0F, 40.0F, 50.0F).speed(6.0F).glance(14.0F);
		// the side heads: their own rigs (the torso already carries the crowned head's turn), their own smoothing
		this.lookLeft = new HeadLook.Rig(this.root, this.torso, this.headLeft).eye(1.0F, -8.0F, -5.0F).limits(55.0F, 40.0F, 45.0F)
			.speed(8.0F).glance(14.0F).slot(1);
		this.crown = new ModelPart[] {this.root, this.torso, this.headCenter};
		this.pauldronLeft = new ModelPart[] {this.root, this.torso, this.torso.getChild("pauldron_left")};
		this.pauldronRight = new ModelPart[] {this.root, this.torso, this.torso.getChild("pauldron_right")};
		ModelPart robe = this.root.getChild("robe");
		String[] skirts = {"skirt_f0", "skirt_f1", "skirt_b0", "skirt_b1"};
		this.hems = new ModelPart[skirts.length][];
		for (int i = 0; i < skirts.length; i++) {
			ModelPart s = robe.getChild(skirts[i]);
			this.hems[i] = new ModelPart[] {this.root, robe, s, s.getChild(skirts[i] + "_r1")};
		}
		this.lookRight = new HeadLook.Rig(this.root, this.torso, this.headRight).eye(-1.0F, -8.0F, -5.0F).limits(55.0F, 40.0F, 45.0F)
			.speed(8.0F).glance(14.0F).slot(2);
		ModelPart lowerRight = this.torso.getChild("arm_lower_right").getChild("arm_lower_right_fore");
		ModelPart lowerLeft = this.torso.getChild("arm_lower_left").getChild("arm_lower_left_fore");
		ModelPart upperRight = this.torso.getChild("arm_upper_right");
		this.weapons = new ModelPart[][] {
			{this.root, this.torso, upperRight, upperRight.getChild("sword")},
			{this.root, this.torso, this.armBow, this.armBow.getChild("bow")},
			{this.root, this.torso, this.torso.getChild("arm_lower_left"), lowerLeft, lowerLeft.getChild("trident")},
			{this.root, this.torso, this.torso.getChild("arm_lower_right"), lowerRight, lowerRight.getChild("mace")},
		};
		for (ModelPart[] chain : this.weapons) {
			chain[chain.length - 1].visible = false;
		}
	}

	/** Moves the pose into the given weapon bone (origin = the grip in the fist, blade along -Z; the bow stands along
	 * Y with its upper limb at -Y and the string toward +Z). */
	public void weaponPose(int form, PoseStack pose) {
		for (ModelPart part : this.weapons[form]) {
			part.translateAndRotate(pose);
		}
	}

	public static LayerDefinition createLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition robe = root.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(154, 0).addBox(-9.0F, 0.0F, -7.0F, 18.0F, 35.0F, 14.0F, new CubeDeformation(0.0F))
		.texOffs(306, 108).addBox(-8.0F, 35.0F, -6.0F, 16.0F, 3.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -42.0F, 0.0F));

		PartDefinition pleat_rb_r1 = robe.addOrReplaceChild("pleat_rb_r1", CubeListBuilder.create().texOffs(378, 0).addBox(-2.0F, 1.0F, -0.5F, 4.0F, 34.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-9.6F, 1.0F, 7.2F, 0.1571F, -0.7854F, 0.1571F));

		PartDefinition pleat_rf_r1 = robe.addOrReplaceChild("pleat_rf_r1", CubeListBuilder.create().texOffs(388, 0).addBox(-2.0F, 1.0F, -0.5F, 4.0F, 34.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-9.6F, 1.0F, -7.2F, -0.1571F, 0.7854F, 0.1571F));

		PartDefinition pleat_lb_r1 = robe.addOrReplaceChild("pleat_lb_r1", CubeListBuilder.create().texOffs(398, 0).addBox(-2.0F, 1.0F, -0.5F, 4.0F, 34.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.6F, 1.0F, 7.2F, 0.1571F, 0.7854F, -0.1571F));

		PartDefinition pleat_lf_r1 = robe.addOrReplaceChild("pleat_lf_r1", CubeListBuilder.create().texOffs(408, 0).addBox(-2.0F, 1.0F, -0.5F, 4.0F, 34.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.6F, 1.0F, -7.2F, -0.1571F, -0.7854F, -0.1571F));

		PartDefinition tasset0_r1 = robe.addOrReplaceChild("tasset0_r1", CubeListBuilder.create().texOffs(482, 60).addBox(-0.5F, 0.0F, -6.0F, 1.0F, 12.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-10.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.2793F));

		PartDefinition tasset1_r1 = robe.addOrReplaceChild("tasset1_r1", CubeListBuilder.create().texOffs(62, 108).addBox(-0.5F, 4.0F, -5.0F, 1.0F, 10.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-10.5F, 2.0F, 0.0F, 0.0F, 0.0F, 0.384F));

		PartDefinition tasset0_m_r1 = robe.addOrReplaceChild("tasset0_m_r1", CubeListBuilder.create().texOffs(0, 87).addBox(-0.5F, 0.0F, -6.0F, 1.0F, 12.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(10.5F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2793F));

		PartDefinition tasset1_m_r1 = robe.addOrReplaceChild("tasset1_m_r1", CubeListBuilder.create().texOffs(78, 108).addBox(-0.5F, 4.0F, -5.0F, 1.0F, 10.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(10.5F, 2.0F, 0.0F, 0.0F, 0.0F, -0.384F));

		PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(368, 60).addBox(-10.0F, -3.0F, -8.0F, 20.0F, 5.0F, 16.0F, new CubeDeformation(0.3F))
		.texOffs(288, 140).addBox(-3.0F, -4.0F, -9.0F, 6.0F, 7.0F, 1.0F, new CubeDeformation(0.2F))
		.texOffs(284, 157).addBox(-1.0F, -2.0F, -10.0F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(158, 60).addBox(-9.0F, -20.0F, 1.0F, 18.0F, 17.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(206, 60).addBox(-9.0F, -20.0F, -5.0F, 3.0F, 17.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(224, 60).addBox(6.0F, -20.0F, -5.0F, 3.0F, 17.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(188, 87).addBox(-5.0F, -19.0F, -4.0F, 10.0F, 15.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(302, 140).addBox(-2.0F, -13.0F, -5.0F, 4.0F, 5.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(454, 140).addBox(-1.0F, -8.0F, -2.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(298, 108).addBox(-1.0F, -19.0F, -7.0F, 2.0F, 14.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(102, 108).addBox(-13.0F, -21.0F, -3.0F, 26.0F, 6.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(242, 60).addBox(-9.0F, -37.0F, 6.5F, 18.0F, 21.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -42.0F, 0.0F));

		PartDefinition lapel_r1 = torso.addOrReplaceChild("lapel_r1", CubeListBuilder.create().texOffs(38, 108).addBox(-4.5F, -9.0F, -0.5F, 5.0F, 17.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.5F, -11.0F, -5.5F, 0.0F, 0.1396F, 0.0F));

		PartDefinition lapel_m_r1 = torso.addOrReplaceChild("lapel_m_r1", CubeListBuilder.create().texOffs(50, 108).addBox(-0.5F, -9.0F, -0.5F, 5.0F, 17.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.5F, -11.0F, -5.5F, 0.0F, -0.1396F, 0.0F));

		PartDefinition rib0_r1 = torso.addOrReplaceChild("rib0_r1", CubeListBuilder.create().texOffs(44, 157).addBox(-6.0F, -1.0F, -1.0F, 6.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, -17.0F, -6.0F, 0.0F, 0.0F, -0.1571F));

		PartDefinition rib1_r1 = torso.addOrReplaceChild("rib1_r1", CubeListBuilder.create().texOffs(60, 157).addBox(-6.0F, -1.0F, -1.0F, 6.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, -14.0F, -6.0F, 0.0F, 0.0F, -0.1571F));

		PartDefinition rib2_r1 = torso.addOrReplaceChild("rib2_r1", CubeListBuilder.create().texOffs(108, 157).addBox(-5.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, -11.0F, -6.0F, 0.0F, 0.0F, -0.1571F));

		PartDefinition rib3_r1 = torso.addOrReplaceChild("rib3_r1", CubeListBuilder.create().texOffs(136, 157).addBox(-4.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, -8.0F, -6.0F, 0.0F, 0.0F, -0.1571F));

		PartDefinition rib0_m_r1 = torso.addOrReplaceChild("rib0_m_r1", CubeListBuilder.create().texOffs(76, 157).addBox(0.0F, -1.0F, -1.0F, 6.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, -17.0F, -6.0F, 0.0F, 0.0F, 0.1571F));

		PartDefinition rib1_m_r1 = torso.addOrReplaceChild("rib1_m_r1", CubeListBuilder.create().texOffs(92, 157).addBox(0.0F, -1.0F, -1.0F, 6.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, -14.0F, -6.0F, 0.0F, 0.0F, 0.1571F));

		PartDefinition rib2_m_r1 = torso.addOrReplaceChild("rib2_m_r1", CubeListBuilder.create().texOffs(122, 157).addBox(0.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, -11.0F, -6.0F, 0.0F, 0.0F, 0.1571F));

		PartDefinition rib3_m_r1 = torso.addOrReplaceChild("rib3_m_r1", CubeListBuilder.create().texOffs(148, 157).addBox(0.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, -8.0F, -6.0F, 0.0F, 0.0F, 0.1571F));

		PartDefinition clavicle_r1 = torso.addOrReplaceChild("clavicle_r1", CubeListBuilder.create().texOffs(0, 157).addBox(-9.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, -20.0F, -5.0F, 0.0F, 0.0F, 0.1396F));

		PartDefinition clavicle_m_r1 = torso.addOrReplaceChild("clavicle_m_r1", CubeListBuilder.create().texOffs(22, 157).addBox(0.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, -20.0F, -5.0F, 0.0F, 0.0F, -0.1396F));

		PartDefinition collar0_r1 = torso.addOrReplaceChild("collar0_r1", CubeListBuilder.create().texOffs(20, 87).addBox(-3.0F, -20.0F, -0.5F, 6.0F, 20.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(12.0F, -17.0F, 7.5F, 0.1745F, 0.0F, 0.4189F));

		PartDefinition collar1_r1 = torso.addOrReplaceChild("collar1_r1", CubeListBuilder.create().texOffs(456, 0).addBox(-3.0F, -27.0F, -0.5F, 6.0F, 27.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0F, -17.0F, 7.5F, 0.1745F, 0.0F, 0.2094F));

		PartDefinition collar2_r1 = torso.addOrReplaceChild("collar2_r1", CubeListBuilder.create().texOffs(418, 0).addBox(-3.0F, -31.0F, -0.5F, 6.0F, 31.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -17.0F, 7.5F, 0.1745F, 0.0F, 0.0F));

		PartDefinition collar3_r1 = torso.addOrReplaceChild("collar3_r1", CubeListBuilder.create().texOffs(470, 0).addBox(-3.0F, -27.0F, -0.5F, 6.0F, 27.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.0F, -17.0F, 7.5F, 0.1745F, 0.0F, -0.2094F));

		PartDefinition collar4_r1 = torso.addOrReplaceChild("collar4_r1", CubeListBuilder.create().texOffs(34, 87).addBox(-3.0F, -20.0F, -0.5F, 6.0F, 20.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-12.0F, -17.0F, 7.5F, 0.1745F, 0.0F, -0.4189F));

		PartDefinition pauldron_left = torso.addOrReplaceChild("pauldron_left", CubeListBuilder.create(), PartPose.offset(-13.0F, -19.0F, 0.0F));

		PartDefinition lame0_r1 = pauldron_left.addOrReplaceChild("lame0_r1", CubeListBuilder.create().texOffs(344, 87).addBox(-9.0F, -2.0F, -7.0F, 11.0F, 4.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2443F));

		PartDefinition lame1_r1 = pauldron_left.addOrReplaceChild("lame1_r1", CubeListBuilder.create().texOffs(174, 108).addBox(-7.0F, 0.0F, -6.0F, 8.0F, 4.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 2.0F, 0.0F, 0.0F, 0.0F, -0.4189F));

		PartDefinition lame2_r1 = pauldron_left.addOrReplaceChild("lame2_r1", CubeListBuilder.create().texOffs(48, 126).addBox(-5.0F, 0.0F, -5.0F, 6.0F, 4.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.0F, 6.0F, 0.0F, 0.0F, 0.0F, -0.5585F));

		PartDefinition gem_r1 = pauldron_left.addOrReplaceChild("gem_r1", CubeListBuilder.create().texOffs(220, 157).addBox(-5.0F, -1.0F, -8.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2443F));

		PartDefinition spike_a0_r1 = pauldron_left.addOrReplaceChild("spike_a0_r1", CubeListBuilder.create().texOffs(462, 140).addBox(-1.0F, -2.5F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-10.0F, 1.5F, -3.0F, 0.0F, 0.0F, -0.9273F));

		PartDefinition spike_a1_r1 = pauldron_left.addOrReplaceChild("spike_a1_r1", CubeListBuilder.create().texOffs(470, 140).addBox(-1.0F, -2.5F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-13.5F, -2.0F, -3.0F, 0.0F, 0.0F, -0.6435F));

		PartDefinition spike_b0_r1 = pauldron_left.addOrReplaceChild("spike_b0_r1", CubeListBuilder.create().texOffs(142, 150).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-9.5F, 3.0F, 3.0F, 0.0F, 0.0F, -0.9828F));

		PartDefinition spike_b1_r1 = pauldron_left.addOrReplaceChild("spike_b1_r1", CubeListBuilder.create().texOffs(150, 150).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-12.5F, 0.5F, 3.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition pauldron_right = torso.addOrReplaceChild("pauldron_right", CubeListBuilder.create(), PartPose.offset(13.0F, -19.0F, 0.0F));

		PartDefinition lame0_r2 = pauldron_right.addOrReplaceChild("lame0_r2", CubeListBuilder.create().texOffs(394, 87).addBox(-2.0F, -2.0F, -7.0F, 11.0F, 4.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.2443F));

		PartDefinition lame1_r2 = pauldron_right.addOrReplaceChild("lame1_r2", CubeListBuilder.create().texOffs(214, 108).addBox(-1.0F, 0.0F, -6.0F, 8.0F, 4.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.4189F));

		PartDefinition lame2_r2 = pauldron_right.addOrReplaceChild("lame2_r2", CubeListBuilder.create().texOffs(80, 126).addBox(-1.0F, 0.0F, -5.0F, 6.0F, 4.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0F, 6.0F, 0.0F, 0.0F, 0.0F, 0.5585F));

		PartDefinition gem_r2 = pauldron_right.addOrReplaceChild("gem_r2", CubeListBuilder.create().texOffs(228, 157).addBox(2.0F, -1.0F, -8.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.2443F));

		PartDefinition spike_a0_r2 = pauldron_right.addOrReplaceChild("spike_a0_r2", CubeListBuilder.create().texOffs(478, 140).addBox(-1.0F, -2.5F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(10.0F, 1.5F, -3.0F, 0.0F, 0.0F, 0.9273F));

		PartDefinition spike_a1_r2 = pauldron_right.addOrReplaceChild("spike_a1_r2", CubeListBuilder.create().texOffs(486, 140).addBox(-1.0F, -2.5F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(13.5F, -2.0F, -3.0F, 0.0F, 0.0F, 0.6435F));

		PartDefinition spike_b0_r2 = pauldron_right.addOrReplaceChild("spike_b0_r2", CubeListBuilder.create().texOffs(158, 150).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.5F, 3.0F, 3.0F, 0.0F, 0.0F, 0.9828F));

		PartDefinition spike_b1_r2 = pauldron_right.addOrReplaceChild("spike_b1_r2", CubeListBuilder.create().texOffs(166, 150).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(12.5F, 0.5F, 3.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition head_center = torso.addOrReplaceChild("head_center", CubeListBuilder.create().texOffs(128, 140).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(48, 60).addBox(-7.0F, -16.0F, -6.0F, 14.0F, 12.0F, 13.0F, new CubeDeformation(0.0F))
		.texOffs(238, 126).addBox(-5.0F, -4.0F, -6.5F, 10.0F, 3.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(238, 87).addBox(-8.0F, -17.0F, -7.0F, 16.0F, 4.0F, 15.0F, new CubeDeformation(0.0F))
		.texOffs(236, 157).addBox(-1.5F, -16.5F, -8.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(302, 157).addBox(-9.0F, -16.0F, 0.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(308, 157).addBox(8.0F, -16.0F, 0.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -20.0F, -1.0F));

		PartDefinition crown_f0_r1 = head_center.addOrReplaceChild("crown_f0_r1", CubeListBuilder.create().texOffs(290, 157).addBox(-1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0F, -17.0F, -6.5F, -0.1222F, 0.0F, 0.1257F));

		PartDefinition crown_ft0_r1 = head_center.addOrReplaceChild("crown_ft0_r1", CubeListBuilder.create().texOffs(382, 157).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0F, -17.0F, -6.5F, -0.1222F, 0.0F, 0.1257F));

		PartDefinition crown_f1_r1 = head_center.addOrReplaceChild("crown_f1_r1", CubeListBuilder.create().texOffs(0, 150).addBox(-1.0F, -6.0F, -0.5F, 2.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, -17.0F, -6.5F, -0.1222F, 0.0F, 0.0628F));

		PartDefinition crown_ft1_r1 = head_center.addOrReplaceChild("crown_ft1_r1", CubeListBuilder.create().texOffs(386, 157).addBox(-0.5F, -8.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, -17.0F, -6.5F, -0.1222F, 0.0F, 0.0628F));

		PartDefinition crown_f2_r1 = head_center.addOrReplaceChild("crown_f2_r1", CubeListBuilder.create().texOffs(318, 126).addBox(-1.0F, -10.0F, -0.5F, 2.0F, 10.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -17.0F, -6.5F, -0.1222F, 0.0F, 0.0F));

		PartDefinition crown_ft2_r1 = head_center.addOrReplaceChild("crown_ft2_r1", CubeListBuilder.create().texOffs(390, 157).addBox(-0.5F, -12.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -17.0F, -6.5F, -0.1222F, 0.0F, 0.0F));

		PartDefinition crown_f3_r1 = head_center.addOrReplaceChild("crown_f3_r1", CubeListBuilder.create().texOffs(6, 150).addBox(-1.0F, -6.0F, -0.5F, 2.0F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, -17.0F, -6.5F, -0.1222F, 0.0F, -0.0628F));

		PartDefinition crown_ft3_r1 = head_center.addOrReplaceChild("crown_ft3_r1", CubeListBuilder.create().texOffs(394, 157).addBox(-0.5F, -8.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, -17.0F, -6.5F, -0.1222F, 0.0F, -0.0628F));

		PartDefinition crown_f4_r1 = head_center.addOrReplaceChild("crown_f4_r1", CubeListBuilder.create().texOffs(296, 157).addBox(-1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.0F, -17.0F, -6.5F, -0.1222F, 0.0F, -0.1257F));

		PartDefinition crown_ft4_r1 = head_center.addOrReplaceChild("crown_ft4_r1", CubeListBuilder.create().texOffs(398, 157).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.0F, -17.0F, -6.5F, -0.1222F, 0.0F, -0.1257F));

		PartDefinition crown_s0_r1 = head_center.addOrReplaceChild("crown_s0_r1", CubeListBuilder.create().texOffs(206, 150).addBox(-0.5F, -4.0F, -1.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.5F, -17.0F, -2.0F, 0.0F, 0.0F, -0.1571F));

		PartDefinition crown_st0_r1 = head_center.addOrReplaceChild("crown_st0_r1", CubeListBuilder.create().texOffs(402, 157).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.5F, -17.0F, -2.0F, 0.0F, 0.0F, -0.1571F));

		PartDefinition crown_s1_r1 = head_center.addOrReplaceChild("crown_s1_r1", CubeListBuilder.create().texOffs(370, 140).addBox(-0.5F, -6.0F, -1.0F, 1.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.5F, -17.0F, 2.0F, 0.0F, 0.0F, -0.1571F));

		PartDefinition crown_st1_r1 = head_center.addOrReplaceChild("crown_st1_r1", CubeListBuilder.create().texOffs(406, 157).addBox(-0.5F, -8.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.5F, -17.0F, 2.0F, 0.0F, 0.0F, -0.1571F));

		PartDefinition crown_s2_r1 = head_center.addOrReplaceChild("crown_s2_r1", CubeListBuilder.create().texOffs(384, 150).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.5F, -17.0F, 6.0F, 0.0F, 0.0F, -0.1571F));

		PartDefinition crown_st2_r1 = head_center.addOrReplaceChild("crown_st2_r1", CubeListBuilder.create().texOffs(410, 157).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.5F, -17.0F, 6.0F, 0.0F, 0.0F, -0.1571F));

		PartDefinition crown_s0_m_r1 = head_center.addOrReplaceChild("crown_s0_m_r1", CubeListBuilder.create().texOffs(212, 150).addBox(-0.5F, -4.0F, -1.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.5F, -17.0F, -2.0F, 0.0F, 0.0F, 0.1571F));

		PartDefinition crown_st0_m_r1 = head_center.addOrReplaceChild("crown_st0_m_r1", CubeListBuilder.create().texOffs(414, 157).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.5F, -17.0F, -2.0F, 0.0F, 0.0F, 0.1571F));

		PartDefinition crown_s1_m_r1 = head_center.addOrReplaceChild("crown_s1_m_r1", CubeListBuilder.create().texOffs(376, 140).addBox(-0.5F, -6.0F, -1.0F, 1.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.5F, -17.0F, 2.0F, 0.0F, 0.0F, 0.1571F));

		PartDefinition crown_st1_m_r1 = head_center.addOrReplaceChild("crown_st1_m_r1", CubeListBuilder.create().texOffs(418, 157).addBox(-0.5F, -8.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.5F, -17.0F, 2.0F, 0.0F, 0.0F, 0.1571F));

		PartDefinition crown_s2_m_r1 = head_center.addOrReplaceChild("crown_s2_m_r1", CubeListBuilder.create().texOffs(390, 150).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.5F, -17.0F, 6.0F, 0.0F, 0.0F, 0.1571F));

		PartDefinition crown_st2_m_r1 = head_center.addOrReplaceChild("crown_st2_m_r1", CubeListBuilder.create().texOffs(422, 157).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.5F, -17.0F, 6.0F, 0.0F, 0.0F, 0.1571F));

		PartDefinition crown_b0_r1 = head_center.addOrReplaceChild("crown_b0_r1", CubeListBuilder.create().texOffs(218, 150).addBox(-1.0F, -5.0F, -0.5F, 2.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0F, -17.0F, 7.5F, 0.1222F, 0.0F, 0.0F));

		PartDefinition crown_b1_r1 = head_center.addOrReplaceChild("crown_b1_r1", CubeListBuilder.create().texOffs(382, 140).addBox(-1.0F, -7.0F, -0.5F, 2.0F, 7.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -17.0F, 7.5F, 0.1222F, 0.0F, 0.0F));

		PartDefinition crown_b2_r1 = head_center.addOrReplaceChild("crown_b2_r1", CubeListBuilder.create().texOffs(224, 150).addBox(-1.0F, -5.0F, -0.5F, 2.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.0F, -17.0F, 7.5F, 0.1222F, 0.0F, 0.0F));

		PartDefinition jaw_center = head_center.addOrReplaceChild("jaw_center", CubeListBuilder.create().texOffs(332, 126).addBox(-5.0F, 0.0F, -7.0F, 10.0F, 3.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.0F, 0.5F));

		PartDefinition head_left = torso.addOrReplaceChild("head_left", CubeListBuilder.create().texOffs(330, 140).addBox(-2.0F, -2.0F, -1.0F, 3.0F, 5.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(444, 87).addBox(-6.0F, -12.0F, -5.0F, 10.0F, 9.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(72, 140).addBox(-5.0F, -3.0F, -5.5F, 8.0F, 3.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(458, 108).addBox(-7.0F, -14.0F, -6.0F, 12.0F, 2.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(392, 108).addBox(-7.0F, -12.0F, 4.0F, 12.0F, 13.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(280, 60).addBox(-7.0F, -12.0F, -6.0F, 1.0F, 12.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(302, 60).addBox(4.0F, -12.0F, -6.0F, 1.0F, 12.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(330, 157).addBox(-2.5F, -14.5F, -7.0F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-15.0F, -21.0F, 1.0F, 0.0F, 0.2443F, -0.2269F));

		PartDefinition hood_peak_r1 = head_left.addOrReplaceChild("hood_peak_r1", CubeListBuilder.create().texOffs(366, 126).addBox(-4.0F, -2.0F, -3.0F, 8.0F, 3.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, -14.0F, 3.0F, 0.384F, 0.0F, 0.0F));

		PartDefinition jaw_left = head_left.addOrReplaceChild("jaw_left", CubeListBuilder.create().texOffs(388, 140).addBox(-4.0F, 0.0F, -5.0F, 8.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.0F, 0.0F, -0.5F));

		PartDefinition head_right = torso.addOrReplaceChild("head_right", CubeListBuilder.create().texOffs(342, 140).addBox(-1.0F, -2.0F, -1.0F, 3.0F, 5.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 108).addBox(-4.0F, -12.0F, -5.0F, 10.0F, 9.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(100, 140).addBox(-3.0F, -3.0F, -5.5F, 8.0F, 3.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(0, 126).addBox(-5.0F, -14.0F, -6.0F, 12.0F, 2.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(420, 108).addBox(-5.0F, -12.0F, 4.0F, 12.0F, 13.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(324, 60).addBox(6.0F, -12.0F, -6.0F, 1.0F, 12.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(346, 60).addBox(-5.0F, -12.0F, -6.0F, 1.0F, 12.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(338, 157).addBox(-0.5F, -14.5F, -7.0F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(15.0F, -21.0F, 1.0F, 0.0F, -0.2443F, 0.2269F));

		PartDefinition hood_peak_r2 = head_right.addOrReplaceChild("hood_peak_r2", CubeListBuilder.create().texOffs(396, 126).addBox(-4.0F, -2.0F, -3.0F, 8.0F, 3.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, -14.0F, 3.0F, 0.384F, 0.0F, 0.0F));

		PartDefinition jaw_right = head_right.addOrReplaceChild("jaw_right", CubeListBuilder.create().texOffs(414, 140).addBox(-4.0F, 0.0F, -5.0F, 8.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(1.0F, 0.0F, -0.5F));

		PartDefinition arm_upper_left = torso.addOrReplaceChild("arm_upper_left", CubeListBuilder.create().texOffs(124, 87).addBox(-4.0F, -1.0F, -4.0F, 8.0F, 12.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(48, 87).addBox(-5.0F, 10.0F, -5.0F, 10.0F, 11.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(254, 108).addBox(-1.0F, 18.0F, -2.0F, 3.0F, 13.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(198, 126).addBox(-2.0F, 23.0F, -3.0F, 5.0F, 7.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(426, 126).addBox(-2.0F, 32.0F, -3.0F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(160, 157).addBox(-2.0F, 34.0F, -4.0F, 5.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(494, 140).addBox(3.0F, 32.0F, -3.0F, 1.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-12.0F, -16.0F, 0.0F, 0.0F, 0.0F, 0.2618F));

		PartDefinition arm_upper_right = torso.addOrReplaceChild("arm_upper_right", CubeListBuilder.create().texOffs(156, 87).addBox(-4.0F, -1.0F, -4.0F, 8.0F, 12.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(86, 87).addBox(-5.0F, 10.0F, -5.0F, 10.0F, 11.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(266, 108).addBox(-2.0F, 18.0F, -2.0F, 3.0F, 13.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(218, 126).addBox(-3.0F, 23.0F, -3.0F, 5.0F, 7.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(446, 126).addBox(-3.0F, 32.0F, -3.0F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(172, 157).addBox(-3.0F, 34.0F, -4.0F, 5.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(502, 140).addBox(-4.0F, 32.0F, -3.0F, 1.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(12.0F, -17.0F, 0.0F, 0.0F, 0.0F, -0.2618F));

		PartDefinition arm_lower_left = torso.addOrReplaceChild("arm_lower_left", CubeListBuilder.create().texOffs(160, 140).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-9.5F, -6.0F, 5.0F));

		PartDefinition humerus_r1 = arm_lower_left.addOrReplaceChild("humerus_r1", CubeListBuilder.create().texOffs(102, 60).addBox(-1.5F, -10.5F, -1.5F, 3.0F, 21.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-10.25F, -1.0F, 1.5F, -0.1446F, 0.0F, -1.4735F));

		PartDefinition armband_r1 = arm_lower_left.addOrReplaceChild("armband_r1", CubeListBuilder.create().texOffs(176, 140).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-11.275F, -1.1F, 1.65F, -0.1446F, 0.0F, -1.4735F));

		PartDefinition rag_r1 = arm_lower_left.addOrReplaceChild("rag_r1", CubeListBuilder.create().texOffs(140, 126).addBox(-3.0F, 0.0F, -0.5F, 6.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-10.5F, -1.0F, 2.5F, 0.1047F, 0.1745F, 0.0698F));

		PartDefinition arm_lower_left_fore = arm_lower_left.addOrReplaceChild("arm_lower_left_fore", CubeListBuilder.create().texOffs(192, 140).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(466, 126).addBox(-6.0F, 12.0F, -14.0F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(184, 157).addBox(-6.0F, 13.5F, -15.0F, 5.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(182, 150).addBox(-1.0F, 12.5F, -14.0F, 1.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-20.5F, -2.0F, 3.0F));

		PartDefinition elbow_spike0_r1 = arm_lower_left_fore.addOrReplaceChild("elbow_spike0_r1", CubeListBuilder.create().texOffs(174, 150).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.0F, -2.0F, 2.75F, -0.7238F, 0.0F, -0.7854F));

		PartDefinition elbow_spike1_r1 = arm_lower_left_fore.addOrReplaceChild("elbow_spike1_r1", CubeListBuilder.create().texOffs(368, 150).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5F, -4.5F, 4.5F, -0.3063F, 0.0F, -0.3218F));

		PartDefinition forearm_r1 = arm_lower_left_fore.addOrReplaceChild("forearm_r1", CubeListBuilder.create().texOffs(300, 87).addBox(-1.5F, -8.0F, -1.5F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, 6.0F, -5.0F, 0.6799F, 0.0F, -2.8966F));

		PartDefinition bracer_r1 = arm_lower_left_fore.addOrReplaceChild("bracer_r1", CubeListBuilder.create().texOffs(20, 140).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.83F, 7.32F, -6.1F, 0.6799F, 0.0F, -2.8966F));

		PartDefinition arm_lower_right = torso.addOrReplaceChild("arm_lower_right", CubeListBuilder.create().texOffs(208, 140).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(9.5F, -6.0F, 5.0F));

		PartDefinition humerus_r2 = arm_lower_right.addOrReplaceChild("humerus_r2", CubeListBuilder.create().texOffs(114, 60).addBox(-1.5F, -10.5F, -1.5F, 3.0F, 21.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(10.25F, -1.0F, 1.5F, -0.1446F, 0.0F, 1.4735F));

		PartDefinition armband_r2 = arm_lower_right.addOrReplaceChild("armband_r2", CubeListBuilder.create().texOffs(224, 140).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(11.275F, -1.1F, 1.65F, -0.1446F, 0.0F, 1.4735F));

		PartDefinition rag_r2 = arm_lower_right.addOrReplaceChild("rag_r2", CubeListBuilder.create().texOffs(154, 126).addBox(-3.0F, 0.0F, -0.5F, 6.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(10.5F, -1.0F, 2.5F, 0.1047F, -0.1745F, -0.0698F));

		PartDefinition arm_lower_right_fore = arm_lower_right.addOrReplaceChild("arm_lower_right_fore", CubeListBuilder.create().texOffs(240, 140).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(486, 126).addBox(1.0F, 12.0F, -14.0F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(196, 157).addBox(1.0F, 13.5F, -15.0F, 5.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(198, 150).addBox(0.0F, 12.5F, -14.0F, 1.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(20.5F, -2.0F, 3.0F));

		PartDefinition elbow_spike0_r2 = arm_lower_right_fore.addOrReplaceChild("elbow_spike0_r2", CubeListBuilder.create().texOffs(190, 150).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, -2.0F, 2.75F, -0.7238F, 0.0F, 0.7854F));

		PartDefinition elbow_spike1_r2 = arm_lower_right_fore.addOrReplaceChild("elbow_spike1_r2", CubeListBuilder.create().texOffs(376, 150).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5F, -4.5F, 4.5F, -0.3063F, 0.0F, 0.3218F));

		PartDefinition forearm_r2 = arm_lower_right_fore.addOrReplaceChild("forearm_r2", CubeListBuilder.create().texOffs(312, 87).addBox(-1.5F, -8.0F, -1.5F, 3.0F, 16.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, 6.0F, -5.0F, 0.6799F, 0.0F, 2.8966F));

		PartDefinition bracer_r2 = arm_lower_right_fore.addOrReplaceChild("bracer_r2", CubeListBuilder.create().texOffs(36, 140).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.83F, 7.32F, -6.1F, 0.6799F, 0.0F, 2.8966F));

		PartDefinition wing_left = torso.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(12, 150).addBox(-20.0F, -2.0F, 0.0F, 20.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(256, 140).addBox(-23.0F, -2.0F, -0.5F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.0F, -15.0F, 7.0F, 0.0F, 0.4363F, 0.3491F));

		PartDefinition finger0_r1 = wing_left.addOrReplaceChild("finger0_r1", CubeListBuilder.create().texOffs(150, 60).addBox(-0.5F, -11.5F, -0.5F, 1.0F, 23.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.5F, 12.5F, 2.5F, 0.0F, 0.0F, 3.0981F));

		PartDefinition finger1_r1 = wing_left.addOrReplaceChild("finger1_r1", CubeListBuilder.create().texOffs(94, 108).addBox(-0.5F, -8.0F, -0.5F, 1.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-16.5F, 9.0F, 2.5F, 0.0F, 0.0F, -3.0792F));

		PartDefinition wing_left_tip = wing_left.addOrReplaceChild("wing_left_tip", CubeListBuilder.create().texOffs(428, 150).addBox(-17.0F, -1.0F, -1.0F, 17.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-21.0F, 0.0F, 1.5F, 0.0F, 0.2443F, 0.3142F));

		PartDefinition claw0_r1 = wing_left_tip.addOrReplaceChild("claw0_r1", CubeListBuilder.create().texOffs(396, 150).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-18.5F, -1.0F, 0.0F, 0.0F, 0.0F, -0.9828F));

		PartDefinition claw1_r1 = wing_left_tip.addOrReplaceChild("claw1_r1", CubeListBuilder.create().texOffs(314, 157).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-20.5F, -3.5F, 0.0F, 0.0F, 0.0F, -0.3218F));

		PartDefinition finger0_r2 = wing_left_tip.addOrReplaceChild("finger0_r2", CubeListBuilder.create().texOffs(324, 126).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-8.75F, 6.0F, 1.0F, 0.0F, 0.0F, -2.9927F));

		PartDefinition wing_right = torso.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(58, 150).addBox(0.0F, -2.0F, 0.0F, 20.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(272, 140).addBox(19.0F, -2.0F, -0.5F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0F, -15.0F, 7.0F, 0.0F, -0.4363F, -0.3491F));

		PartDefinition finger0_r3 = wing_right.addOrReplaceChild("finger0_r3", CubeListBuilder.create().texOffs(154, 60).addBox(-0.5F, -11.5F, -0.5F, 1.0F, 23.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.5F, 12.5F, 2.5F, 0.0F, 0.0F, -3.0981F));

		PartDefinition finger1_r2 = wing_right.addOrReplaceChild("finger1_r2", CubeListBuilder.create().texOffs(98, 108).addBox(-0.5F, -8.0F, -0.5F, 1.0F, 16.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(16.5F, 9.0F, 2.5F, 0.0F, 0.0F, 3.0792F));

		PartDefinition wing_right_tip = wing_right.addOrReplaceChild("wing_right_tip", CubeListBuilder.create().texOffs(466, 150).addBox(0.0F, -1.0F, -1.0F, 17.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(21.0F, 0.0F, 1.5F, 0.0F, -0.2443F, -0.3142F));

		PartDefinition claw0_r2 = wing_right_tip.addOrReplaceChild("claw0_r2", CubeListBuilder.create().texOffs(400, 150).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(18.5F, -1.0F, 0.0F, 0.0F, 0.0F, 0.9828F));

		PartDefinition claw1_r2 = wing_right_tip.addOrReplaceChild("claw1_r2", CubeListBuilder.create().texOffs(318, 157).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(20.5F, -3.5F, 0.0F, 0.0F, 0.0F, 0.3218F));

		PartDefinition finger0_r4 = wing_right_tip.addOrReplaceChild("finger0_r4", CubeListBuilder.create().texOffs(328, 126).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(8.75F, 6.0F, 1.0F, 0.0F, 0.0F, 2.9927F));

		PartDefinition sword = arm_upper_right.addOrReplaceChild("sword", CubeListBuilder.create().texOffs(308, 150).addBox(-1.5F, -1.5F, 4.0F, 3.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(442, 157).addBox(-0.5F, -0.5F, 6.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 140).addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(188, 126).addBox(-1.5F, -5.5F, -6.0F, 3.0F, 11.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(318, 150).addBox(-1.0F, -7.5F, -7.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(328, 150).addBox(-1.0F, 5.5F, -7.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(208, 157).addBox(-2.0F, -1.0F, -6.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.1F))
		.texOffs(0, 60).addBox(-1.0F, -2.5F, -28.0F, 2.0F, 5.0F, 22.0F, new CubeDeformation(0.0F))
		.texOffs(132, 150).addBox(-1.0F, -1.5F, -31.0F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(346, 157).addBox(-0.5F, -0.5F, -33.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, 34.5F, -0.5F, 0.0F, -0.384F, 0.0F));

		PartDefinition bow = arm_upper_left.addOrReplaceChild("bow", CubeListBuilder.create().texOffs(448, 108).addBox(-1.0F, -6.0F, -1.5F, 2.0F, 12.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(316, 140).addBox(-1.5F, -2.0F, -2.0F, 3.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(104, 150).addBox(-1.5F, -7.0F, -2.0F, 3.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(354, 140).addBox(-1.0F, -19.0F, -5.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(284, 150).addBox(-1.5F, -14.0F, -5.5F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(118, 150).addBox(-1.5F, 5.0F, -2.0F, 3.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(296, 150).addBox(-1.5F, 12.0F, -5.5F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(352, 157).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 37.5F, -1.5F, 1.504F, 0.0019F, 0.0561F));

		PartDefinition limb_u0_r1 = bow.addOrReplaceChild("limb_u0_r1", CubeListBuilder.create().texOffs(144, 140).addBox(-1.0F, -3.5F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -10.0F, -2.5F, 0.4636F, 0.0F, 0.0F));

		PartDefinition tip_u0_r1 = bow.addOrReplaceChild("tip_u0_r1", CubeListBuilder.create().texOffs(230, 150).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -20.5F, -3.0F, -0.588F, 0.0F, 0.0F));

		PartDefinition tip_u1_r1 = bow.addOrReplaceChild("tip_u1_r1", CubeListBuilder.create().texOffs(236, 150).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -23.0F, -0.5F, -0.9828F, 0.0F, 0.0F));

		PartDefinition limb_d0_r1 = bow.addOrReplaceChild("limb_d0_r1", CubeListBuilder.create().texOffs(152, 140).addBox(-1.0F, -3.5F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 10.0F, -2.5F, 0.4636F, 0.0F, -3.1416F));

		PartDefinition limb_d1_r1 = bow.addOrReplaceChild("limb_d1_r1", CubeListBuilder.create().texOffs(362, 140).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 16.0F, -4.0F, 0.0F, 0.0F, -3.1416F));

		PartDefinition tip_d0_r1 = bow.addOrReplaceChild("tip_d0_r1", CubeListBuilder.create().texOffs(242, 150).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 20.5F, -3.0F, -0.588F, 0.0F, -3.1416F));

		PartDefinition tip_d1_r1 = bow.addOrReplaceChild("tip_d1_r1", CubeListBuilder.create().texOffs(248, 150).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 23.0F, -0.5F, -0.9828F, 0.0F, -3.1416F));

		PartDefinition string_r1 = bow.addOrReplaceChild("string_r1", CubeListBuilder.create().texOffs(218, 0).addBox(-0.5F, -23.5F, -0.5F, 1.0F, 47.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 1.0F, 0.0F, 0.0F, -3.1416F));

		PartDefinition mace = arm_lower_right_fore.addOrReplaceChild("mace", CubeListBuilder.create().texOffs(338, 150).addBox(-1.5F, -1.5F, 4.0F, 3.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(440, 60).addBox(-1.0F, -1.0F, -15.0F, 2.0F, 2.0F, 19.0F, new CubeDeformation(0.0F))
		.texOffs(348, 150).addBox(-1.5F, -1.5F, -16.0F, 3.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(112, 126).addBox(-3.5F, -3.5F, -23.5F, 7.0F, 7.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(260, 157).addBox(-1.0F, -5.75F, -21.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(434, 157).addBox(-0.5F, -7.75F, -20.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(358, 150).addBox(-1.0F, -1.0F, -26.5F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(446, 157).addBox(-0.5F, -0.5F, -27.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.25F, 15.5F, -11.5F, -0.2084F, -0.1009F, -0.0471F));

		PartDefinition spike0_r1 = mace.addOrReplaceChild("spike0_r1", CubeListBuilder.create().texOffs(244, 157).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.75F, 0.0F, -20.0F, 0.0F, 0.0F, -1.5708F));

		PartDefinition spike0t_r1 = mace.addOrReplaceChild("spike0t_r1", CubeListBuilder.create().texOffs(426, 157).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.75F, 0.0F, -20.0F, 0.0F, 0.0F, -1.5708F));

		PartDefinition spike1_r1 = mace.addOrReplaceChild("spike1_r1", CubeListBuilder.create().texOffs(252, 157).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.75F, 0.0F, -20.0F, 0.0F, 0.0F, 1.5708F));

		PartDefinition spike1t_r1 = mace.addOrReplaceChild("spike1t_r1", CubeListBuilder.create().texOffs(430, 157).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.75F, 0.0F, -20.0F, 0.0F, 0.0F, 1.5708F));

		PartDefinition spike3_r1 = mace.addOrReplaceChild("spike3_r1", CubeListBuilder.create().texOffs(268, 157).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 4.75F, -20.0F, 0.0F, 0.0F, -3.1416F));

		PartDefinition spike3t_r1 = mace.addOrReplaceChild("spike3t_r1", CubeListBuilder.create().texOffs(438, 157).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 6.75F, -20.0F, 0.0F, 0.0F, -3.1416F));

		PartDefinition spike_d0_r1 = mace.addOrReplaceChild("spike_d0_r1", CubeListBuilder.create().texOffs(404, 150).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.85F, -3.85F, -20.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition spike_d1_r1 = mace.addOrReplaceChild("spike_d1_r1", CubeListBuilder.create().texOffs(408, 150).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.85F, 3.85F, -20.0F, 0.0F, 0.0F, -2.3562F));

		PartDefinition spike_d2_r1 = mace.addOrReplaceChild("spike_d2_r1", CubeListBuilder.create().texOffs(412, 150).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.85F, -3.85F, -20.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition spike_d3_r1 = mace.addOrReplaceChild("spike_d3_r1", CubeListBuilder.create().texOffs(416, 150).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.85F, 3.85F, -20.0F, 0.0F, 0.0F, 2.3562F));

		PartDefinition trident = arm_lower_left_fore.addOrReplaceChild("trident", CubeListBuilder.create().texOffs(54, 0).addBox(-1.0F, -1.0F, -30.0F, 2.0F, 2.0F, 48.0F, new CubeDeformation(0.0F))
		.texOffs(358, 157).addBox(-1.0F, -1.0F, 16.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.4F))
		.texOffs(364, 157).addBox(-1.0F, -1.0F, -4.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.4F))
		.texOffs(370, 157).addBox(-1.0F, -1.0F, 3.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.4F))
		.texOffs(376, 157).addBox(-1.0F, -1.0F, -29.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.4F))
		.texOffs(254, 150).addBox(-6.0F, -1.0F, -33.0F, 12.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(440, 140).addBox(-1.5F, -1.5F, -34.0F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.1F))
		.texOffs(362, 108).addBox(-1.0F, -1.0F, -47.0F, 2.0F, 2.0F, 13.0F, new CubeDeformation(0.0F))
		.texOffs(276, 157).addBox(-0.5F, -0.5F, -50.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(274, 126).addBox(-6.0F, -1.0F, -42.0F, 2.0F, 2.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(296, 126).addBox(4.0F, -1.0F, -42.0F, 2.0F, 2.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5F, 15.5F, -11.5F, 0.2112F, 0.1723F, 1.782F));

		PartDefinition prong_at_r1 = trident.addOrReplaceChild("prong_at_r1", CubeListBuilder.create().texOffs(420, 150).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.25F, 0.0F, -44.0F, 1.212F, 0.0F, 1.5708F));

		PartDefinition barb_a_r1 = trident.addOrReplaceChild("barb_a_r1", CubeListBuilder.create().texOffs(322, 157).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.25F, 0.0F, -38.5F, -1.1071F, 0.0F, -1.5708F));

		PartDefinition prong_bt_r1 = trident.addOrReplaceChild("prong_bt_r1", CubeListBuilder.create().texOffs(424, 150).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.25F, 0.0F, -44.0F, 1.212F, 0.0F, -1.5708F));

		PartDefinition barb_b_r1 = trident.addOrReplaceChild("barb_b_r1", CubeListBuilder.create().texOffs(326, 157).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.25F, 0.0F, -38.5F, -1.1071F, 0.0F, 1.5708F));

		PartDefinition cape0 = torso.addOrReplaceChild("cape0", CubeListBuilder.create(), PartPose.offset(8.0F, -19.0F, 8.5F));

		PartDefinition cape0_r1 = cape0.addOrReplaceChild("cape0_r1", CubeListBuilder.create().texOffs(18, 0).addBox(-4.0F, 0.0F, -0.5F, 8.0F, 56.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.1222F, 0.0F, 0.0F));

		PartDefinition cape1 = torso.addOrReplaceChild("cape1", CubeListBuilder.create(), PartPose.offset(0.0F, -19.0F, 8.5F));

		PartDefinition cape1_r1 = cape1.addOrReplaceChild("cape1_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, 0.0F, -0.5F, 8.0F, 59.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.1571F, 0.0F, 0.0F));

		PartDefinition cape2 = torso.addOrReplaceChild("cape2", CubeListBuilder.create(), PartPose.offset(-8.0F, -19.0F, 8.5F));

		PartDefinition cape2_r1 = cape2.addOrReplaceChild("cape2_r1", CubeListBuilder.create().texOffs(36, 0).addBox(-4.0F, 0.0F, -0.5F, 8.0F, 55.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.1222F, 0.0F, 0.0F));

		PartDefinition skirt_f0 = robe.addOrReplaceChild("skirt_f0", CubeListBuilder.create(), PartPose.offset(7.0F, 0.0F, -7.5F));

		PartDefinition skirt_f0_r1 = skirt_f0.addOrReplaceChild("skirt_f0_r1", CubeListBuilder.create().texOffs(318, 0).addBox(-3.0F, 0.0F, -0.5F, 6.0F, 38.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.1571F, 0.0F, 0.0F));

		PartDefinition skirt_f1 = robe.addOrReplaceChild("skirt_f1", CubeListBuilder.create(), PartPose.offset(-7.0F, 0.0F, -7.5F));

		PartDefinition skirt_f1_r1 = skirt_f1.addOrReplaceChild("skirt_f1_r1", CubeListBuilder.create().texOffs(348, 0).addBox(-3.0F, 0.0F, -0.5F, 6.0F, 37.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.1571F, 0.0F, 0.0F));

		PartDefinition skirt_b0 = robe.addOrReplaceChild("skirt_b0", CubeListBuilder.create(), PartPose.offset(6.5F, 0.0F, 7.5F));

		PartDefinition skirt_b0_r1 = skirt_b0.addOrReplaceChild("skirt_b0_r1", CubeListBuilder.create().texOffs(362, 0).addBox(-3.5F, 0.0F, -0.5F, 7.0F, 36.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.1571F, 0.0F, 0.0F));

		PartDefinition skirt_b1 = robe.addOrReplaceChild("skirt_b1", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 7.5F));

		PartDefinition skirt_b1_r1 = skirt_b1.addOrReplaceChild("skirt_b1_r1", CubeListBuilder.create().texOffs(304, 0).addBox(-3.0F, 0.0F, -0.5F, 6.0F, 39.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.1571F, 0.0F, 0.0F));

		PartDefinition skirt_b2 = robe.addOrReplaceChild("skirt_b2", CubeListBuilder.create(), PartPose.offset(-6.5F, 0.0F, 7.5F));

		PartDefinition skirt_b2_r1 = skirt_b2.addOrReplaceChild("skirt_b2_r1", CubeListBuilder.create().texOffs(332, 0).addBox(-3.5F, 0.0F, -0.5F, 7.0F, 37.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.1571F, 0.0F, 0.0F));

		PartDefinition tabard = robe.addOrReplaceChild("tabard", CubeListBuilder.create(), PartPose.offset(0.0F, 1.0F, -8.5F));

		PartDefinition tabard_r1 = tabard.addOrReplaceChild("tabard_r1", CubeListBuilder.create().texOffs(286, 0).addBox(-4.0F, 0.0F, -0.5F, 8.0F, 40.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.1047F, 0.0F, 0.0F));

		PartDefinition skirt_s0 = robe.addOrReplaceChild("skirt_s0", CubeListBuilder.create(), PartPose.offset(-9.5F, 0.0F, -3.5F));

		PartDefinition skirt_s0_r1 = skirt_s0.addOrReplaceChild("skirt_s0_r1", CubeListBuilder.create().texOffs(222, 0).addBox(-0.5F, 0.0F, -3.5F, 1.0F, 38.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.1571F));

		PartDefinition skirt_s1 = robe.addOrReplaceChild("skirt_s1", CubeListBuilder.create(), PartPose.offset(-9.5F, 0.0F, 3.5F));

		PartDefinition skirt_s1_r1 = skirt_s1.addOrReplaceChild("skirt_s1_r1", CubeListBuilder.create().texOffs(254, 0).addBox(-0.5F, 0.0F, -3.5F, 1.0F, 36.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.1571F));

		PartDefinition skirt_s0_m = robe.addOrReplaceChild("skirt_s0_m", CubeListBuilder.create(), PartPose.offset(9.5F, 0.0F, -3.5F));

		PartDefinition skirt_s0_m_r1 = skirt_s0_m.addOrReplaceChild("skirt_s0_m_r1", CubeListBuilder.create().texOffs(238, 0).addBox(-0.5F, 0.0F, -3.5F, 1.0F, 38.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.1571F));

		PartDefinition skirt_s1_m = robe.addOrReplaceChild("skirt_s1_m", CubeListBuilder.create(), PartPose.offset(9.5F, 0.0F, 3.5F));

		PartDefinition skirt_s1_m_r1 = skirt_s1_m.addOrReplaceChild("skirt_s1_m_r1", CubeListBuilder.create().texOffs(270, 0).addBox(-0.5F, 0.0F, -3.5F, 1.0F, 36.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.1571F));

		PartDefinition wing_left_s0 = wing_left.addOrReplaceChild("wing_left_s0", CubeListBuilder.create(), PartPose.offset(-3.5F, 0.0F, 1.5F));

		PartDefinition strip0_r1 = wing_left_s0.addOrReplaceChild("strip0_r1", CubeListBuilder.create().texOffs(432, 0).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 30.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0184F, 0.0F, -0.0366F));

		PartDefinition wing_left_s1 = wing_left.addOrReplaceChild("wing_left_s1", CubeListBuilder.create(), PartPose.offset(-8.5F, 0.0F, 1.5F));

		PartDefinition strip1_r1 = wing_left_s1.addOrReplaceChild("strip1_r1", CubeListBuilder.create().texOffs(484, 0).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 27.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.0158F, 0.0F, -0.0448F));

		PartDefinition wing_left_s2 = wing_left.addOrReplaceChild("wing_left_s2", CubeListBuilder.create(), PartPose.offset(-13.5F, 0.0F, 1.5F));

		PartDefinition strip2_r1 = wing_left_s2.addOrReplaceChild("strip2_r1", CubeListBuilder.create().texOffs(126, 60).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 23.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.0038F, 0.0F, -0.0141F));

		PartDefinition wing_left_s3 = wing_left.addOrReplaceChild("wing_left_s3", CubeListBuilder.create(), PartPose.offset(-18.0F, 0.0F, 1.5F));

		PartDefinition strip3_r1 = wing_left_s3.addOrReplaceChild("strip3_r1", CubeListBuilder.create().texOffs(218, 87).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 19.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0463F, 0.0F, 0.0008F));

		PartDefinition wing_left_tip_s0 = wing_left_tip.addOrReplaceChild("wing_left_tip_s0", CubeListBuilder.create(), PartPose.offset(-2.0F, 0.0F, 0.0F));

		PartDefinition strip0_r2 = wing_left_tip_s0.addOrReplaceChild("strip0_r2", CubeListBuilder.create().texOffs(324, 87).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 18.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0484F, 0.0F, -0.0069F));

		PartDefinition wing_left_tip_s1 = wing_left_tip.addOrReplaceChild("wing_left_tip_s1", CubeListBuilder.create(), PartPose.offset(-6.0F, 0.0F, 0.0F));

		PartDefinition strip1_r2 = wing_left_tip_s1.addOrReplaceChild("strip1_r2", CubeListBuilder.create().texOffs(278, 108).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 15.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.045F, 0.0F, -0.0429F));

		PartDefinition wing_left_tip_s2 = wing_left_tip.addOrReplaceChild("wing_left_tip_s2", CubeListBuilder.create(), PartPose.offset(-10.0F, 0.0F, 0.0F));

		PartDefinition strip2_r2 = wing_left_tip_s2.addOrReplaceChild("strip2_r2", CubeListBuilder.create().texOffs(168, 126).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0079F, 0.0F, 0.0342F));

		PartDefinition wing_left_tip_s3 = wing_left_tip.addOrReplaceChild("wing_left_tip_s3", CubeListBuilder.create(), PartPose.offset(-14.0F, 0.0F, 0.0F));

		PartDefinition strip3_r2 = wing_left_tip_s3.addOrReplaceChild("strip3_r2", CubeListBuilder.create().texOffs(52, 140).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 9.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0394F, 0.0F, -0.029F));

		PartDefinition wing_right_s0 = wing_right.addOrReplaceChild("wing_right_s0", CubeListBuilder.create(), PartPose.offset(3.5F, 0.0F, 1.5F));

		PartDefinition strip0_r3 = wing_right_s0.addOrReplaceChild("strip0_r3", CubeListBuilder.create().texOffs(444, 0).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 30.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0184F, 0.0F, 0.0366F));

		PartDefinition wing_right_s1 = wing_right.addOrReplaceChild("wing_right_s1", CubeListBuilder.create(), PartPose.offset(8.5F, 0.0F, 1.5F));

		PartDefinition strip1_r3 = wing_right_s1.addOrReplaceChild("strip1_r3", CubeListBuilder.create().texOffs(496, 0).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 27.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.0158F, 0.0F, 0.0448F));

		PartDefinition wing_right_s2 = wing_right.addOrReplaceChild("wing_right_s2", CubeListBuilder.create(), PartPose.offset(13.5F, 0.0F, 1.5F));

		PartDefinition strip2_r3 = wing_right_s2.addOrReplaceChild("strip2_r3", CubeListBuilder.create().texOffs(138, 60).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 23.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.0038F, 0.0F, 0.0141F));

		PartDefinition wing_right_s3 = wing_right.addOrReplaceChild("wing_right_s3", CubeListBuilder.create(), PartPose.offset(18.0F, 0.0F, 1.5F));

		PartDefinition strip3_r3 = wing_right_s3.addOrReplaceChild("strip3_r3", CubeListBuilder.create().texOffs(228, 87).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 19.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0463F, 0.0F, -0.0008F));

		PartDefinition wing_right_tip_s0 = wing_right_tip.addOrReplaceChild("wing_right_tip_s0", CubeListBuilder.create(), PartPose.offset(2.0F, 0.0F, 0.0F));

		PartDefinition strip0_r4 = wing_right_tip_s0.addOrReplaceChild("strip0_r4", CubeListBuilder.create().texOffs(334, 87).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 18.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0484F, 0.0F, 0.0069F));

		PartDefinition wing_right_tip_s1 = wing_right_tip.addOrReplaceChild("wing_right_tip_s1", CubeListBuilder.create(), PartPose.offset(6.0F, 0.0F, 0.0F));

		PartDefinition strip1_r4 = wing_right_tip_s1.addOrReplaceChild("strip1_r4", CubeListBuilder.create().texOffs(288, 108).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 15.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.045F, 0.0F, 0.0429F));

		PartDefinition wing_right_tip_s2 = wing_right_tip.addOrReplaceChild("wing_right_tip_s2", CubeListBuilder.create(), PartPose.offset(10.0F, 0.0F, 0.0F));

		PartDefinition strip2_r4 = wing_right_tip_s2.addOrReplaceChild("strip2_r4", CubeListBuilder.create().texOffs(178, 126).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 12.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0079F, 0.0F, -0.0342F));

		PartDefinition wing_right_tip_s3 = wing_right_tip.addOrReplaceChild("wing_right_tip_s3", CubeListBuilder.create(), PartPose.offset(14.0F, 0.0F, 0.0F));

		PartDefinition strip3_r4 = wing_right_tip_s3.addOrReplaceChild("strip3_r4", CubeListBuilder.create().texOffs(62, 140).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 9.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0394F, 0.0F, 0.029F));

		return LayerDefinition.create(meshdefinition, 512, 256);
	}

	private static String clip(Action action) {
		return action.name().toLowerCase(Locale.ROOT);
	}

	private static String castClip(int cast) {
		return switch (cast) {
			case SoulKingEntity.CAST_WAIL -> "cast_wail";
			case SoulKingEntity.CAST_BEAM -> "cast_beam";
			case SoulKingEntity.CAST_CALL -> "cast_call";
			default -> "";
		};
	}

	@Override
	public void setupAnim(SoulKingEntity e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
		this.root.getAllParts().forEach(ModelPart::resetPose);
		BoneAnimations anims = BoneAnimations.get(ANIMS);
		if (anims.version != this.partsVersion) {
			this.parts = anims.resolve(this.mesh);
			this.partsVersion = anims.version;
		}
		float partial = age - e.tickCount;
		BoneAnimations.Pose pose = new BoneAnimations.Pose();
		boolean dying = e.deathTime > 0;
		int intro = e.getIntro();

		// base loops: hover / breathe, and a forward lean + trailing robe while gliding
		float base = dying ? 0.0F : intro > 0 ? 0.25F : 1.0F;
		anims.add("idle", age / 20.0F, base, pose);
		anims.add("move", age / 20.0F, Mth.clamp(limbSwingAmount * 2.5F, 0.0F, 1.0F) * base, pose);

		Action action = e.getAction();
		float actionWeight = 0.0F;
		if (dying) {
			anims.add("death", (e.deathTime + partial) / 20.0F, 1.0F, pose);
		} else if (intro > 0) {
			anims.add("intro", (SoulKingEntity.INTRO_TICKS - intro + partial) / 20.0F, 1.0F, pose);
		} else {
			// the action that was interrupted fades out while the new one starts
			float since = age - e.clientActionChanged;
			if (since < FADE_TICKS && e.clientPrevAction != Action.IDLE) {
				anims.add(clip(e.clientPrevAction), (age - e.clientPrevActionStart) / 20.0F, 1.0F - since / FADE_TICKS, pose);
			}
			if (action != Action.IDLE) {
				actionWeight = Mth.clamp(since / FADE_TICKS, 0.0F, 1.0F);
				anims.add(clip(action), (age - e.clientActionStart) / 20.0F, actionWeight, pose);
			}
			int cast = e.getHeadCast();
			if (cast != SoulKingEntity.CAST_NONE) {
				anims.add(castClip(cast), (age - e.clientCastStart) / 20.0F, 1.0F, pose);
			}
		}
		if (!dying && intro <= 0) {
			CombatFx.watchHurt(e, 1);
			if (e.hurtTime > 0) {
				anims.add(hitFromFront(e) ? "hurt_front" : "hurt_back", (e.hurtDuration - e.hurtTime + partial) / 20.0F, 1.0F, pose);
			}
		}
		pose.applyTo(this.parts);
		String fired = !dying && intro <= 0 && action != Action.IDLE ? clip(action) : null;

		// procedural layer: the crowned head tracks its target; the beam head aims; the bow follows the aim pitch
		float lookWeight = dying || intro > 0 || action == Action.STAGGER || action == Action.WHIRLWIND ? 0.0F
			: action == Action.ROAR ? 0.4F : 1.0F;
		HeadLook.Look look = HeadLook.apply(e, age, lookWeight, this.look, headYaw, headPitch);
		if (!dying && intro <= 0) {
			int cast = e.getHeadCast();
			this.lookRight.limits(cast == SoulKingEntity.CAST_BEAM ? 85.0F : 55.0F, 40.0F, 45.0F);
			this.lookLeft.limits(cast == SoulKingEntity.CAST_WAIL ? 85.0F : 55.0F, 40.0F, 45.0F);
			HeadLook.apply(e, age, lookWeight, this.lookRight, headYaw, headPitch);
			HeadLook.apply(e, age, lookWeight, this.lookLeft, headYaw, headPitch);
			if (action == Action.BOW_VOLLEY) {
				this.armBow.xRot += look.pitch() * Mth.DEG_TO_RAD * actionWeight;
				this.armBow.yRot += look.yaw() * Mth.DEG_TO_RAD * 0.5F * actionWeight;
			}
		}
		this.headLeft.visible = e.hasHead(SoulKingEntity.HEAD_LEFT);
		this.headCenter.visible = e.hasHead(SoulKingEntity.HEAD_CENTER);
		this.headRight.visible = e.hasHead(SoulKingEntity.HEAD_RIGHT);

		// cloth: calm while it dies; a Whirlwind spin (drawn by the renderer) flings everything outward
		float calm = dying ? Mth.clamp(1.0F - e.deathTime / 40.0F, 0.0F, 1.0F) : intro > 0 ? 0.4F : 1.0F;
		if (action == Action.WHIRLWIND && age - e.clientActionStart > 20) {
			this.cloth.drive(0.5F, 36.0F);
			this.skirts.drive(0.5F, 36.0F);
		}
		this.cloth.apply(e, age, partial, this.root.xRot + this.torso.xRot, this.root.yRot + this.torso.yRot, calm, this.parts);
		ModelPart robe = this.parts.get("robe");
		float robeX = robe == null ? 0.0F : robe.xRot, robeY = robe == null ? 0.0F : robe.yRot;
		this.skirts.apply(e, age, partial, this.root.xRot + robeX, this.root.yRot + robeY, calm, this.parts);
		if (fired != null) {
			CombatFx.clipEvents(e, "action", anims, fired, (age - e.clientActionStart) / 20.0F, (name, strength) -> this.fire(e, partial, name, strength));
		}
		if (!dying) {
			this.ambient(e, age, partial);
			this.castFx(e, action);
		}
	}

	/**
	 * Art 4.0, the King alive: soul fire licks the crown's spikes, every head's eyes leave afterimages, vapour curls off
	 * the hem, embers rise off the robe; in the third phase his pauldrons burn.
	 */
	/** Art 4.0 mesh VFX on a move's first frame: magic circles under the big casts, a vortex round the whirlwind, a
	 * shockwave from the roar, a small circle under a head's own cast. */
	private void castFx(SoulKingEntity e, Action action) {
		if (action != Action.IDLE && MeshFx.once(e, 0, e.clientActionStart) && e.tickCount - e.clientActionStart < 4) {
			float secs = action.duration / 20.0F;
			switch (action) {
				case STORM_GRID, ARROW_RAIN, SOUL_HARVEST, CATACLYSM -> MeshFx.runeCircle(e, 4.5F, secs);
				case WHIRLWIND -> MeshFx.vortex(e, 2.4F, 5.5F, secs);
				case ROAR -> MeshFx.shockwave(e.position(), 7.0F);
				default -> {
				}
			}
		}
		if (e.getHeadCast() != SoulKingEntity.CAST_NONE && MeshFx.once(e, 1, e.clientCastStart) && e.tickCount - e.clientCastStart < 4) {
			MeshFx.runeCircle(e, 2.6F, 1.6F);
		}
	}

	private void ambient(SoulKingEntity e, float age, float partial) {
		var r = e.getRandom();
		int phase = e.getPhase();
		if (e.hasHead(SoulKingEntity.HEAD_CENTER)) {
			for (int i = MobFx.due(e, 0, age, 12.0F + 4.0F * phase); i > 0; i--) {
				float[] t = CROWN_TIPS[r.nextInt(CROWN_TIPS.length)];
				MobFx.spawn(e, dev.remnants.registry.ModParticles.SOUL_FLAME, MobFx.at(e, partial, t[0], t[1], t[2], this.crown), 0.05, 0.0, 0.03, 0.0, 0.01);
			}
			for (int i = MobFx.due(e, 1, age, 22.0F); i > 0; i--) {
				float side = (i & 1) == 0 ? 3.0F : -3.0F;
				MobFx.spawn(e, dev.remnants.registry.ModParticles.EYE_TRAIL, MobFx.at(e, partial, side, -10.0F, -6.6F, this.crown), 0, 0, 0);
			}
		}
		if (e.hasHead(SoulKingEntity.HEAD_RIGHT)) {
			for (int i = MobFx.due(e, 2, age, 14.0F); i > 0; i--) {
				MobFx.spawn(e, dev.remnants.registry.ModParticles.EYE_TRAIL, MobFx.at(e, partial, (i & 1) == 0 ? -3.0F : 1.0F, -8.0F, -5.3F, this.lookRight.chain()), 0, 0, 0);
			}
		}
		if (e.hasHead(SoulKingEntity.HEAD_LEFT)) {
			for (int i = MobFx.due(e, 3, age, 14.0F); i > 0; i--) {
				MobFx.spawn(e, dev.remnants.registry.ModParticles.EYE_TRAIL, MobFx.at(e, partial, (i & 1) == 0 ? 3.0F : -1.0F, -8.0F, -5.3F, this.lookLeft.chain()), 0, 0, 0);
			}
		}
		for (int i = MobFx.due(e, 4, age, 6.0F); i > 0; i--) {
			Vec3 p = MobFx.at(e, partial, (r.nextFloat() - 0.5F) * 6.0F, 33.0F + r.nextFloat() * 4.0F, 0.0F, this.hems[r.nextInt(this.hems.length)]);
			MobFx.spawn(e, dev.remnants.registry.ModParticles.SOUL_WISP, p, 0.1, 0.0, 0.015, 0.0, 0.02);
		}
		for (int i = MobFx.due(e, 5, age, 4.0F); i > 0; i--) {
			Vec3 p = e.position().add((r.nextDouble() - 0.5) * 1.6, 0.5 + r.nextDouble() * 3.5, (r.nextDouble() - 0.5) * 1.6);
			MobFx.spawn(e, dev.remnants.registry.ModParticles.SOUL_EMBER, p, 0, 0.03, 0);
		}
		if (phase >= 3) {
			for (int i = MobFx.due(e, 6, age, 20.0F); i > 0; i--) {
				Vec3 p = MobFx.at(e, partial, (r.nextFloat() - 0.5F) * 8.0F, -3.0F, (r.nextFloat() - 0.5F) * 6.0F, (i & 1) == 0 ? this.pauldronLeft : this.pauldronRight);
				MobFx.spawn(e, dev.remnants.registry.ModParticles.SOUL_FLAME, p, 0.1, 0.0, 0.04, 0.0, 0.02);
			}
		}
	}

	private static boolean hitFromFront(SoulKingEntity e) {
		var src = e.getLastDamageSource();
		Vec3 at = src == null ? null : src.getSourcePosition();
		return at == null || Vec3.directionFromRotation(0, e.yBodyRot).dot(at.subtract(e.position()).multiply(1, 0, 1)) >= 0;
	}

	/** Clip events: a glint on a weapon (at its blade / head) as the wind-up begins. */
	private void fire(SoulKingEntity e, float partial, String name, float strength) {
		int form = switch (name) {
			case "glint_sword" -> 0;
			case "glint_bow" -> 1;
			case "glint_trident" -> 2;
			case "glint_mace" -> 3;
			default -> -1;
		};
		if (form >= 0) {
			float along = form == 0 ? -22.0F : form == 1 ? 0.0F : form == 2 ? -38.0F : -20.0F;
			CombatFx.glint(ModelPoints.world(e, partial, 0.0F, 0.0F, along, this.weapons[form]), strength * 1.6F);
		}
	}

	@Override
	public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
		this.root.render(pose, buffer, light, overlay, color);
	}
}
