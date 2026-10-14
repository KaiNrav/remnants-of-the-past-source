package dev.remnants.client.archive;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.remnants.RemnantsMod;
import dev.remnants.client.anim.BoneAnimations;
import dev.remnants.entity.ArchivePageEntity;
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
 * A Page of the Archive: a folded leaf beating its halves like wings ("flap"), folded nearly shut
 * while it darts in ("dash"). Keyframed (animations/archive_page.json).
 */
public class ArchivePageModel extends EntityModel<ArchivePageEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(RemnantsMod.id("archive_page"), "main");
	private static final ResourceLocation ANIMS = RemnantsMod.id("archive_page");

	private final ModelPart mesh;
	private final ModelPart root;
	private Map<String, ModelPart> parts = Map.of();
	private int partsVersion = Integer.MIN_VALUE;

	public ArchivePageModel(ModelPart model) {
		super(RenderType::entityCutoutNoCull);
		this.mesh = model;
		this.root = model.getChild("root");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 9).addBox(-0.5F, 0.0F, -4.0F, 1.0F, 1.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(58, 18).addBox(-0.5F, -1.0F, -2.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(18, 9).addBox(-1.0F, 1.0F, -3.5F, 2.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -5.0F, 0.0F));

		PartDefinition half_right = body.addOrReplaceChild("half_right", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -0.5F, -4.0F, 7.0F, 1.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(46, 9).addBox(-4.5F, -0.55F, -1.5F, 2.0F, 0.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(26, 18).addBox(-7.0F, 0.0F, -4.5F, 7.0F, 1.0F, 1.0F, new CubeDeformation(-0.1F)), PartPose.offset(-0.5F, 0.5F, 0.0F));

		PartDefinition corner_right = half_right.addOrReplaceChild("corner_right", CubeListBuilder.create(), PartPose.offset(-5.0F, 0.0F, 3.0F));

		PartDefinition corner_r1 = corner_right.addOrReplaceChild("corner_r1", CubeListBuilder.create().texOffs(10, 18).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.5236F));

		PartDefinition half_left = body.addOrReplaceChild("half_left", CubeListBuilder.create().texOffs(30, 0).addBox(0.0F, -0.5F, -4.0F, 7.0F, 1.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(0, 18).addBox(2.5F, -0.55F, -1.5F, 2.0F, 0.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(42, 18).addBox(0.0F, 0.0F, -4.5F, 7.0F, 1.0F, 1.0F, new CubeDeformation(-0.1F)), PartPose.offset(0.5F, 0.5F, 0.0F));

		PartDefinition corner_left = half_left.addOrReplaceChild("corner_left", CubeListBuilder.create(), PartPose.offset(5.0F, 0.0F, 3.0F));

		PartDefinition corner_r2 = corner_left.addOrReplaceChild("corner_r2", CubeListBuilder.create().texOffs(18, 18).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 0.0F, 1.0F, 0.0F, 0.0F, -0.5236F));

		PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(36, 9).addBox(-0.5F, -1.0F, 0.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.0F, 4.0F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	@Override
	public void setupAnim(ArchivePageEntity e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
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
			anims.add(e.isDashing() ? "dash" : "flap", t, 1.0F, pose);
			dev.remnants.client.fx.CombatFx.watchHurt(e, 1);
			if (e.hurtTime > 0) {   // art 2.0: a hit makes it flutter
				anims.add("hurt", (e.hurtDuration - e.hurtTime + age - e.tickCount) / 20.0F, 1.0F, pose);
			}
		}
		pose.applyTo(this.parts);
		// art 4.0: scraps of its own writing tear off it as it darts (a few as it flutters), a violet spark now and then
		for (int i = dev.remnants.client.fx.MobFx.due(e, 0, age, e.deathTime > 0 ? 16.0F : e.isDashing() ? 7.0F : 0.5F); i > 0; i--) {
			dev.remnants.client.fx.MobFx.spawn(e, dev.remnants.registry.ModParticles.PAGE_SCRAP, e.position().add(0, 0.3, 0), 0.4, 0.0, 0.0, 0.0, 0.06);
		}
		for (int i = dev.remnants.client.fx.MobFx.due(e, 1, age, e.deathTime > 0 ? 0.0F : 1.5F); i > 0; i--) {
			dev.remnants.client.fx.MobFx.spawn(e, dev.remnants.registry.ModParticles.SOUL_EMBER, e.position().add(0, 0.3, 0), 0.5, 0.0, 0.02, 0.0, 0.02);
		}
	}

	@Override
	public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
		this.root.render(pose, buffer, light, overlay, color);
	}
}
