package dev.remnants.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.remnants.RemnantsMod;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * 3D cuboid models for the Remnant's soul crown (a ring of eight band segments with tall and short spikes and
 * soul gems) and its orbiting soul orbs.
 */
public class RemnantCrownModel extends Model {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(RemnantsMod.id("remnant_crown"), "main");

	public final ModelPart crown;
	public final ModelPart orb;

	public RemnantCrownModel(ModelPart root) {
		super(RenderType::entityTranslucentEmissive);
		this.crown = root.getChild("crown");
		this.orb = root.getChild("orb");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition crown = root.addOrReplaceChild("crown", CubeListBuilder.create(), PartPose.ZERO);
		float radius = 4.6F;
		for (int i = 0; i < 8; i++) {
			float a = i * Mth.PI / 4;
			float x = Mth.sin(a) * radius;
			float z = Mth.cos(a) * radius;
			crown.addOrReplaceChild("band" + i, CubeListBuilder.create().texOffs(0, 8).addBox(-2.0F, -1.0F, -0.5F, 4, 2, 1),
				PartPose.offsetAndRotation(x, 0, z, 0, a, 0));
			boolean tall = i % 2 == 0;
			int h = tall ? 5 : 3;
			crown.addOrReplaceChild("spike" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -h, -0.5F, 1, h, 1),
				PartPose.offsetAndRotation(x, -1.0F, z, tall ? 0 : 0.15F, a, 0));
			if (tall) {
				crown.addOrReplaceChild("gem" + i, CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -1.0F, -1.0F, 2, 2, 2),
					PartPose.offsetAndRotation(x * 1.02F, -6.5F, z * 1.02F, Mth.PI / 4, a, Mth.PI / 4));
			}
		}
		root.addOrReplaceChild("orb", CubeListBuilder.create().texOffs(16, 0).addBox(-1.5F, -1.5F, -1.5F, 3, 3, 3), PartPose.ZERO);
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
		this.crown.render(poseStack, buffer, light, overlay, color);
	}
}
