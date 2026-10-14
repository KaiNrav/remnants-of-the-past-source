package dev.remnants.client.archive;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.remnants.RemnantsMod;
import dev.remnants.item.ShroudGliderItem;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Shroud Glider's canopy over a gliding player: a torn shroud on a bone frame, two wings drooping off a ridge, two
 * struts down to the hands; its hem ripples. Drawn only while the glider is open (ShroudGliderItem.gliding).
 */
public class GliderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(RemnantsMod.id("shroud_glider"), "main");
	public static final ResourceLocation TEXTURE = RemnantsMod.id("textures/entity/shroud_glider.png");
	private final ModelPart root, wingL, wingR;

	public GliderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ModelPart root) {
		super(parent);
		this.root = root;
		this.wingL = root.getChild("wing_left");
		this.wingR = root.getChild("wing_right");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		r.addOrReplaceChild("ridge", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -1.0F, -7.0F, 16.0F, 1.0F, 14.0F)
			.texOffs(0, 30).addBox(-9.0F, -2.0F, -1.0F, 18.0F, 1.0F, 2.0F), PartPose.offset(0.0F, -26.0F, 0.0F));
		r.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(0, 15).addBox(0.0F, -1.0F, -7.0F, 12.0F, 1.0F, 14.0F),
			PartPose.offsetAndRotation(8.0F, -26.0F, 0.0F, 0.0F, 0.0F, 0.42F));
		r.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(0, 15).mirror().addBox(-12.0F, -1.0F, -7.0F, 12.0F, 1.0F, 14.0F),
			PartPose.offsetAndRotation(-8.0F, -26.0F, 0.0F, 0.0F, 0.0F, -0.42F));
		r.addOrReplaceChild("strut_left", CubeListBuilder.create().texOffs(40, 30).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 22.0F, 1.0F),
			PartPose.offsetAndRotation(6.0F, -26.0F, 0.0F, 0.0F, 0.0F, 0.1F));
		r.addOrReplaceChild("strut_right", CubeListBuilder.create().texOffs(40, 30).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 22.0F, 1.0F),
			PartPose.offsetAndRotation(-6.0F, -26.0F, 0.0F, 0.0F, 0.0F, -0.1F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float amount,
		float partial, float age, float headYaw, float headPitch) {
		if (!ShroudGliderItem.gliding(player)) {
			return;
		}
		float ripple = Mth.sin(age * 0.6F) * 0.06F;
		this.wingL.zRot = 0.42F + ripple;
		this.wingR.zRot = -0.42F - ripple;
		this.root.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
	}
}
