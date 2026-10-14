package dev.remnants.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.remnants.RemnantsMod;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;

/**
 * The few pieces that tell the biome Forgotten apart ("a recolour plus two to four details"), each drawn in the space
 * of the player-model part it hangs on (see ForgottenVariantLayer). Texture: textures/entity/forgotten_variants.png:
 * kelp strands top-left, ice top-right, sand-coloured cloth and a gold scarab below.
 *
 * <ul>
 *   <li>Drowned: kelp hanging from both sides of the head, down the chest and off the right arm.</li>
 *   <li>Frozen: an ice cap with icicles over the brow and ice shards on both shoulders (gone once the crust breaks).</li>
 *   <li>Sandy: a head wrap with a trailing tail, a sash and a gold scarab on the chest.</li>
 * </ul>
 */
public class ForgottenVariantModel extends Model {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(RemnantsMod.id("forgotten_variants"), "main");

	public final ModelPart drownedHead, drownedBody, drownedArm;
	public final ModelPart frozenHead, frozenRightArm, frozenLeftArm;
	public final ModelPart sandyHead, sandyBody;

	public ForgottenVariantModel(ModelPart root) {
		super(RenderType::entityTranslucent);
		this.drownedHead = root.getChild("drowned_head");
		this.drownedBody = root.getChild("drowned_body");
		this.drownedArm = root.getChild("drowned_arm");
		this.frozenHead = root.getChild("frozen_head");
		this.frozenRightArm = root.getChild("frozen_right_arm");
		this.frozenLeftArm = root.getChild("frozen_left_arm");
		this.sandyHead = root.getChild("sandy_head");
		this.sandyBody = root.getChild("sandy_body");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// kelp strands are flat cards (zero depth), the texture's transparent texels give them ragged edges
		root.addOrReplaceChild("drowned_head", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-4.4F, -8.6F, -2.0F, 0, 12, 4)
			.texOffs(8, 0).addBox(4.4F, -8.6F, -2.5F, 0, 12, 4)
			.texOffs(16, 0).addBox(-2.0F, -8.6F, 4.4F, 4, 9, 0), PartPose.ZERO);
		root.addOrReplaceChild("drowned_body", CubeListBuilder.create()
			.texOffs(24, 0).addBox(-3.5F, -0.4F, -2.3F, 3, 15, 0), PartPose.ZERO);
		root.addOrReplaceChild("drowned_arm", CubeListBuilder.create()
			.texOffs(0, 16).addBox(-3.3F, -2.2F, -2.0F, 0, 12, 4), PartPose.ZERO);

		root.addOrReplaceChild("frozen_head", CubeListBuilder.create()
			.texOffs(32, 0).addBox(-4.0F, -9.4F, -4.0F, 8, 2, 8, new CubeDeformation(0.35F))
			.texOffs(32, 10).addBox(-3.0F, -7.6F, -4.7F, 1, 2, 1)
			.texOffs(36, 10).addBox(0.0F, -7.6F, -4.7F, 1, 3, 1)
			.texOffs(40, 10).addBox(2.0F, -7.6F, -4.7F, 1, 2, 1), PartPose.ZERO);
		root.addOrReplaceChild("frozen_right_arm", CubeListBuilder.create()
			.texOffs(32, 14).addBox(-3.5F, -2.8F, -2.5F, 5, 3, 5, new CubeDeformation(0.1F))
			.texOffs(52, 14).addBox(-2.5F, -5.5F, -0.5F, 1, 3, 1), PartPose.ZERO);
		root.addOrReplaceChild("frozen_left_arm", CubeListBuilder.create()
			.texOffs(32, 22).addBox(-1.5F, -2.8F, -2.5F, 5, 3, 5, new CubeDeformation(0.1F))
			.texOffs(56, 14).addBox(1.5F, -4.8F, 0.0F, 1, 2, 1), PartPose.ZERO);

		root.addOrReplaceChild("sandy_head", CubeListBuilder.create()
			.texOffs(0, 32).addBox(-4.0F, -8.5F, -4.0F, 8, 4, 8, new CubeDeformation(0.45F))
			.texOffs(0, 44).addBox(-1.5F, -6.0F, 4.6F, 3, 9, 0), PartPose.ZERO);
		root.addOrReplaceChild("sandy_body", CubeListBuilder.create()
			.texOffs(0, 54).addBox(-4.0F, 9.0F, -2.0F, 8, 2, 4, new CubeDeformation(0.4F))
			.texOffs(32, 32).addBox(-1.0F, 2.5F, -2.9F, 2, 2, 1), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
	}
}
