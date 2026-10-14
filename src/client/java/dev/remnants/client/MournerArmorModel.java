package dev.remnants.client;

import dev.remnants.RemnantsMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Mourner's set as 3D armour: a biped whose bones carry no cubes; every piece is a child part tagged with the
 * slot that shows it (a two-peaked cowl with a gold frame round a bone mask and a soul diamond; a robe with a
 * ribcage, a skull buckle and a pointed tabard, a gold-edged cape and a loose tail, mantles with a skull / bone
 * plates and soul charms, belled cuffs, A-line skirts round the legs; dark leggings with bone knee caps; boots with
 * gold rings, shin plates and bone toe caps).
 * Texture {@code textures/models/armor/mourner_3d.png} (+ {@code _glow} for the emissive pass), 1 texel per unit.
 */
public class MournerArmorModel extends HumanoidModel<LivingEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(RemnantsMod.id("mourner_armor"), "main");

	/** name, path from the root, slot. */
	private static final String[][] PIECES = {
		{"hood", "head/hood", "HEAD"},
		{"brow_gem", "head/brow_gem", "HEAD"},
		{"ear_r", "head/ear_r", "HEAD"},
		{"ear_tip_r", "head/ear_r/ear_tip_r", "HEAD"},
		{"ear_l", "head/ear_l", "HEAD"},
		{"ear_tip_l", "head/ear_l/ear_tip_l", "HEAD"},
		{"robe", "body/robe", "CHEST"},
		{"collar_r", "body/collar_r", "CHEST"},
		{"rib0_r", "body/rib0_r", "CHEST"},
		{"ribwrap0_r", "body/rib0_r/ribwrap0_r", "CHEST"},
		{"rib1_r", "body/rib1_r", "CHEST"},
		{"ribwrap1_r", "body/rib1_r/ribwrap1_r", "CHEST"},
		{"rib2_r", "body/rib2_r", "CHEST"},
		{"ribwrap2_r", "body/rib2_r/ribwrap2_r", "CHEST"},
		{"collar_l", "body/collar_l", "CHEST"},
		{"rib0_l", "body/rib0_l", "CHEST"},
		{"ribwrap0_l", "body/rib0_l/ribwrap0_l", "CHEST"},
		{"rib1_l", "body/rib1_l", "CHEST"},
		{"ribwrap1_l", "body/rib1_l/ribwrap1_l", "CHEST"},
		{"rib2_l", "body/rib2_l", "CHEST"},
		{"ribwrap2_l", "body/rib2_l/ribwrap2_l", "CHEST"},
		{"throat_gem", "body/throat_gem", "CHEST"},
		{"tabard", "body/tabard", "CHEST"},
		{"tail", "body/tail", "CHEST"},
		{"cape", "body/cape", "CHEST"},
		{"cape_lower", "body/cape/cape_lower", "CHEST"},
		{"sleeve_r", "right_arm/sleeve_r", "CHEST"},
		{"cuff_r", "right_arm/cuff_r", "CHEST"},
		{"charm0_r", "right_arm/charm0_r", "CHEST"},
		{"charm1_r", "right_arm/charm1_r", "CHEST"},
		{"sleeve_l", "left_arm/sleeve_l", "CHEST"},
		{"cuff_l", "left_arm/cuff_l", "CHEST"},
		{"charm0_l", "left_arm/charm0_l", "CHEST"},
		{"charm1_l", "left_arm/charm1_l", "CHEST"},
		{"pauldron_skull", "right_arm/pauldron_skull", "CHEST"},
		{"pauldron_lames", "left_arm/pauldron_lames", "CHEST"},
		{"hips", "body/hips", "LEGS"},
		{"leg_r", "right_leg/leg_r", "LEGS"},
		{"skirt_r", "right_leg/skirt_r", "CHEST"},
		{"boot_r", "right_leg/boot_r", "FEET"},
		{"leg_l", "left_leg/leg_l", "LEGS"},
		{"skirt_l", "left_leg/skirt_l", "CHEST"},
		{"boot_l", "left_leg/boot_l", "FEET"}
	};

	private final Map<EquipmentSlot, List<ModelPart>> pieces = new EnumMap<>(EquipmentSlot.class);
	/** The cloth parts MournerClothPhysics swings (reset to their rest pose before every frame). */
	public final ModelPart cape;
	public final ModelPart capeLower;
	public final ModelPart tail;
	public final ModelPart tabard;
	public final ModelPart earRight;
	public final ModelPart earLeft;
	public final ModelPart charmR0;
	public final ModelPart charmR1;
	public final ModelPart charmL0;
	public final ModelPart charmL1;
	public final ModelPart skirtRight;
	public final ModelPart skirtLeft;
	public final ModelPart cuffRight;
	public final ModelPart cuffLeft;

	public MournerArmorModel(ModelPart root) {
		super(root);
		this.cape = find(root, "body/cape");
		this.capeLower = find(root, "body/cape/cape_lower");
		this.tail = find(root, "body/tail");
		this.tabard = find(root, "body/tabard");
		this.earRight = find(root, "head/ear_r");
		this.earLeft = find(root, "head/ear_l");
		this.charmR0 = find(root, "right_arm/charm0_r");
		this.charmR1 = find(root, "right_arm/charm1_r");
		this.charmL0 = find(root, "left_arm/charm0_l");
		this.charmL1 = find(root, "left_arm/charm1_l");
		this.skirtRight = find(root, "right_leg/skirt_r");
		this.skirtLeft = find(root, "left_leg/skirt_l");
		this.cuffRight = find(root, "right_arm/cuff_r");
		this.cuffLeft = find(root, "left_arm/cuff_l");
		for (String[] piece : PIECES) {
			ModelPart p = find(root, piece[1]);
			this.pieces.computeIfAbsent(EquipmentSlot.valueOf(piece[2]), s -> new ArrayList<>()).add(p);
		}
	}

	private static ModelPart find(ModelPart root, String path) {
		ModelPart p = root;
		for (String step : path.split("/")) {
			p = p.getChild(step);
		}
		return p;
	}

	/** Shows only the pieces of one slot (every bone stays visible: they carry no cubes). */
	public void show(EquipmentSlot slot) {
		this.setAllVisible(true);
		this.pieces.forEach((s, parts) -> parts.forEach(p -> p.visible = s == slot));
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
		PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
		PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
		PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
		head.addOrReplaceChild("hood", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(1.25F))
			.texOffs(62, 59).addBox(-4.0F, -7.75F, -4.75F, 8.0F, 8.0F, 1.0F, new CubeDeformation(0.1F))
			.texOffs(65, 78).addBox(-5.5F, -10.0F, -7.0F, 11.0F, 2.0F, 2.0F)
			.texOffs(67, 47).addBox(-5.75F, -8.25F, -6.75F, 2.0F, 9.0F, 2.0F)
			.texOffs(76, 47).addBox(3.75F, -8.25F, -6.75F, 2.0F, 9.0F, 2.0F)
			.texOffs(59, 17).addBox(-5.5F, -6.5F, -5.5F, 11.0F, 1.0F, 11.0F),
			PartPose.ZERO);
		head.addOrReplaceChild("brow_gem", CubeListBuilder.create()
			.texOffs(13, 86).addBox(-1.5F, -1.5F, -0.5F, 3.0F, 3.0F, 1.0F)
			.texOffs(105, 86).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, -9.0F, -7.2F, 0.0F, 0.0F, 0.7854F));
		PartDefinition p_ear_r = head.addOrReplaceChild("ear_r", CubeListBuilder.create()
			.texOffs(28, 78).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 3.0F, 3.0F),
			PartPose.offsetAndRotation(-2.75F, -9.0F, 1.0F, -0.15F, 0.0F, -0.18F));
		p_ear_r.addOrReplaceChild("ear_tip_r", CubeListBuilder.create()
			.texOffs(22, 86).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F),
			PartPose.offsetAndRotation(0.0F, -2.75F, 0.25F, -0.3F, 0.0F, -0.2F));
		PartDefinition p_ear_l = head.addOrReplaceChild("ear_l", CubeListBuilder.create()
			.texOffs(41, 78).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 3.0F, 3.0F),
			PartPose.offsetAndRotation(2.75F, -9.0F, 1.0F, -0.15F, 0.0F, 0.18F));
		p_ear_l.addOrReplaceChild("ear_tip_l", CubeListBuilder.create()
			.texOffs(31, 86).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F),
			PartPose.offsetAndRotation(0.0F, -2.75F, 0.25F, -0.3F, 0.0F, 0.2F));
		body.addOrReplaceChild("robe", CubeListBuilder.create()
			.texOffs(33, 0).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(1.0F))
			.texOffs(21, 78).addBox(-1.0F, 1.5F, -4.25F, 2.0F, 6.0F, 1.0F)
			.texOffs(0, 69).addBox(-5.5F, 9.25F, -3.5F, 11.0F, 1.0F, 7.0F)
			.texOffs(54, 78).addBox(-1.5F, 8.25F, -5.25F, 3.0F, 3.0F, 2.0F)
			.texOffs(0, 59).addBox(-5.5F, -2.25F, -3.5F, 11.0F, 2.0F, 7.0F, new CubeDeformation(0.2F))
			.texOffs(112, 86).addBox(2.25F, 9.75F, -4.75F, 2.0F, 2.0F, 1.0F),
			PartPose.ZERO);
		body.addOrReplaceChild("collar_r", CubeListBuilder.create()
			.texOffs(14, 91).addBox(-4.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F),
			PartPose.offsetAndRotation(-0.75F, 1.5F, -3.75F, 0.0F, 0.0F, 0.5F));
		PartDefinition p_rib0_r = body.addOrReplaceChild("rib0_r", CubeListBuilder.create()
			.texOffs(36, 91).addBox(-3.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F),
			PartPose.offsetAndRotation(-0.9F, 2.75F, -3.6F, 0.0F, 0.3F, -0.12F));
		p_rib0_r.addOrReplaceChild("ribwrap0_r", CubeListBuilder.create()
			.texOffs(72, 91).addBox(-2.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F),
			PartPose.offsetAndRotation(-3.0F, 0.0F, 0.0F, 0.0F, 0.9F, -0.15F));
		PartDefinition p_rib1_r = body.addOrReplaceChild("rib1_r", CubeListBuilder.create()
			.texOffs(45, 91).addBox(-3.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F),
			PartPose.offsetAndRotation(-0.9F, 4.75F, -3.6F, 0.0F, 0.3F, -0.12F));
		p_rib1_r.addOrReplaceChild("ribwrap1_r", CubeListBuilder.create()
			.texOffs(79, 91).addBox(-2.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F),
			PartPose.offsetAndRotation(-3.0F, 0.0F, 0.0F, 0.0F, 0.9F, -0.15F));
		PartDefinition p_rib2_r = body.addOrReplaceChild("rib2_r", CubeListBuilder.create()
			.texOffs(86, 91).addBox(-2.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F),
			PartPose.offsetAndRotation(-0.9F, 6.75F, -3.6F, 0.0F, 0.3F, -0.12F));
		p_rib2_r.addOrReplaceChild("ribwrap2_r", CubeListBuilder.create()
			.texOffs(93, 91).addBox(-2.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F),
			PartPose.offsetAndRotation(-2.0F, 0.0F, 0.0F, 0.0F, 0.9F, -0.15F));
		body.addOrReplaceChild("collar_l", CubeListBuilder.create()
			.texOffs(25, 91).addBox(0.0F, -0.5F, -0.5F, 4.0F, 1.0F, 1.0F),
			PartPose.offsetAndRotation(0.75F, 1.5F, -3.75F, 0.0F, 0.0F, -0.5F));
		PartDefinition p_rib0_l = body.addOrReplaceChild("rib0_l", CubeListBuilder.create()
			.texOffs(54, 91).addBox(0.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F),
			PartPose.offsetAndRotation(0.9F, 2.75F, -3.6F, 0.0F, -0.3F, 0.12F));
		p_rib0_l.addOrReplaceChild("ribwrap0_l", CubeListBuilder.create()
			.texOffs(100, 91).addBox(0.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F),
			PartPose.offsetAndRotation(3.0F, 0.0F, 0.0F, 0.0F, -0.9F, 0.15F));
		PartDefinition p_rib1_l = body.addOrReplaceChild("rib1_l", CubeListBuilder.create()
			.texOffs(63, 91).addBox(0.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F),
			PartPose.offsetAndRotation(0.9F, 4.75F, -3.6F, 0.0F, -0.3F, 0.12F));
		p_rib1_l.addOrReplaceChild("ribwrap1_l", CubeListBuilder.create()
			.texOffs(107, 91).addBox(0.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F),
			PartPose.offsetAndRotation(3.0F, 0.0F, 0.0F, 0.0F, -0.9F, 0.15F));
		PartDefinition p_rib2_l = body.addOrReplaceChild("rib2_l", CubeListBuilder.create()
			.texOffs(114, 91).addBox(0.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F),
			PartPose.offsetAndRotation(0.9F, 6.75F, -3.6F, 0.0F, -0.3F, 0.12F));
		p_rib2_l.addOrReplaceChild("ribwrap2_l", CubeListBuilder.create()
			.texOffs(121, 91).addBox(0.0F, -0.5F, -0.5F, 2.0F, 1.0F, 1.0F),
			PartPose.offsetAndRotation(2.0F, 0.0F, 0.0F, 0.0F, -0.9F, 0.15F));
		body.addOrReplaceChild("throat_gem", CubeListBuilder.create()
			.texOffs(40, 86).addBox(-1.5F, -1.5F, -0.5F, 3.0F, 3.0F, 1.0F)
			.texOffs(119, 86).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, 2.25F, -4.6F, 0.0F, 0.0F, 0.7854F));
		body.addOrReplaceChild("tabard", CubeListBuilder.create()
			.texOffs(85, 47).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 9.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, 10.25F, -4.2F, 0.0F, 0.0F, 0.0F));
		body.addOrReplaceChild("tail", CubeListBuilder.create()
			.texOffs(0, 17).addBox(-1.5F, 0.0F, -0.5F, 3.0F, 15.0F, 1.0F),
			PartPose.offsetAndRotation(-5.2F, 0.5F, 3.9F, 0.12F, 0.0F, 0.06F));
		PartDefinition p_cape = body.addOrReplaceChild("cape", CubeListBuilder.create()
			.texOffs(25, 47).addBox(-4.5F, 0.0F, 0.0F, 9.0F, 10.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, 0.25F, 3.5F, 0.1F, 0.0F, 0.0F));
		p_cape.addOrReplaceChild("cape_lower", CubeListBuilder.create()
			.texOffs(46, 47).addBox(-4.5F, 0.0F, 0.0F, 9.0F, 10.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		rightArm.addOrReplaceChild("sleeve_r", CubeListBuilder.create()
			.texOffs(58, 0).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.75F))
			.texOffs(0, 34).addBox(-4.5F, -3.5F, -3.5F, 7.0F, 5.0F, 7.0F),
			PartPose.ZERO);
		rightArm.addOrReplaceChild("cuff_r", CubeListBuilder.create()
			.texOffs(58, 34).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 3.0F, 8.0F),
			PartPose.offsetAndRotation(-1.0F, 8.5F, 0.0F, 0.0F, 0.0F, 0.0F));
		rightArm.addOrReplaceChild("charm0_r", CubeListBuilder.create()
			.texOffs(67, 86).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F)
			.texOffs(0, 95).addBox(-0.5F, 3.0F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.2F)),
			PartPose.offsetAndRotation(-4.5F, 1.25F, -3.15F, 0.0F, 0.0F, 0.0F));
		rightArm.addOrReplaceChild("charm1_r", CubeListBuilder.create()
			.texOffs(72, 86).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F)
			.texOffs(5, 95).addBox(-0.5F, 3.0F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.2F)),
			PartPose.offsetAndRotation(-4.5F, 1.25F, 3.15F, 0.0F, 0.0F, 0.0F));
		leftArm.addOrReplaceChild("sleeve_l", CubeListBuilder.create()
			.texOffs(75, 0).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.75F))
			.texOffs(29, 34).addBox(-2.5F, -3.5F, -3.5F, 7.0F, 5.0F, 7.0F),
			PartPose.ZERO);
		leftArm.addOrReplaceChild("cuff_l", CubeListBuilder.create()
			.texOffs(91, 34).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 3.0F, 8.0F),
			PartPose.offsetAndRotation(1.0F, 8.5F, 0.0F, 0.0F, 0.0F, 0.0F));
		leftArm.addOrReplaceChild("charm0_l", CubeListBuilder.create()
			.texOffs(77, 86).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F)
			.texOffs(10, 95).addBox(-0.5F, 3.0F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.2F)),
			PartPose.offsetAndRotation(4.5F, 1.25F, -3.15F, 0.0F, 0.0F, 0.0F));
		leftArm.addOrReplaceChild("charm1_l", CubeListBuilder.create()
			.texOffs(82, 86).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F)
			.texOffs(15, 95).addBox(-0.5F, 3.0F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.2F)),
			PartPose.offsetAndRotation(4.5F, 1.25F, 3.15F, 0.0F, 0.0F, 0.0F));
		rightArm.addOrReplaceChild("pauldron_skull", CubeListBuilder.create()
			.texOffs(0, 47).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 5.0F, 6.0F)
			.texOffs(92, 78).addBox(-2.5F, -0.25F, -3.5F, 5.0F, 1.0F, 3.0F),
			PartPose.offsetAndRotation(-2.0F, -3.25F, 0.0F, 0.0F, 0.35F, -0.12F));
		leftArm.addOrReplaceChild("pauldron_lames", CubeListBuilder.create()
			.texOffs(37, 69).addBox(-3.0F, -1.0F, -3.75F, 6.0F, 1.0F, 7.0F)
			.texOffs(0, 78).addBox(-2.0F, -1.75F, -3.25F, 4.0F, 1.0F, 6.0F),
			PartPose.offsetAndRotation(1.25F, -3.5F, 0.0F, 0.0F, 0.0F, 0.12F));
		body.addOrReplaceChild("hips", CubeListBuilder.create()
			.texOffs(37, 59).addBox(-4.0F, 7.0F, -2.0F, 8.0F, 5.0F, 4.0F, new CubeDeformation(0.7F)),
			PartPose.ZERO);
		rightLeg.addOrReplaceChild("leg_r", CubeListBuilder.create()
			.texOffs(92, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.5F))
			.texOffs(87, 86).addBox(-1.5F, 3.5F, -3.1F, 3.0F, 2.0F, 1.0F),
			PartPose.ZERO);
		rightLeg.addOrReplaceChild("skirt_r", CubeListBuilder.create()
			.texOffs(9, 17).addBox(-3.0F, -0.5F, -3.0F, 6.0F, 7.0F, 6.0F, new CubeDeformation(0.25F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.1F));
		rightLeg.addOrReplaceChild("boot_r", CubeListBuilder.create()
			.texOffs(81, 59).addBox(-2.0F, 7.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(1.0F))
			.texOffs(64, 69).addBox(-3.0F, 6.25F, -3.0F, 6.0F, 1.0F, 6.0F, new CubeDeformation(0.5F))
			.texOffs(49, 86).addBox(-1.5F, 8.25F, -4.25F, 3.0F, 3.0F, 1.0F)
			.texOffs(109, 78).addBox(-2.0F, 10.25F, -4.75F, 4.0F, 2.0F, 2.0F)
			.texOffs(0, 91).addBox(-0.5F, 10.0F, 2.75F, 1.0F, 1.0F, 2.0F),
			PartPose.ZERO);
		leftLeg.addOrReplaceChild("leg_l", CubeListBuilder.create()
			.texOffs(109, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.6F))
			.texOffs(96, 86).addBox(-1.5F, 3.5F, -3.1F, 3.0F, 2.0F, 1.0F),
			PartPose.ZERO);
		leftLeg.addOrReplaceChild("skirt_l", CubeListBuilder.create()
			.texOffs(34, 17).addBox(-3.0F, -0.5F, -3.0F, 6.0F, 7.0F, 6.0F, new CubeDeformation(0.35F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.1F));
		leftLeg.addOrReplaceChild("boot_l", CubeListBuilder.create()
			.texOffs(98, 59).addBox(-2.0F, 7.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(1.1F))
			.texOffs(89, 69).addBox(-3.0F, 6.25F, -3.0F, 6.0F, 1.0F, 6.0F, new CubeDeformation(0.6F))
			.texOffs(58, 86).addBox(-1.5F, 8.25F, -4.25F, 3.0F, 3.0F, 1.0F)
			.texOffs(0, 86).addBox(-2.0F, 10.25F, -4.75F, 4.0F, 2.0F, 2.0F)
			.texOffs(7, 91).addBox(-0.5F, 10.0F, 2.75F, 1.0F, 1.0F, 2.0F),
			PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, 128);
	}
}
