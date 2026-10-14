package dev.remnants.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.remnants.RemnantsMod;
import dev.remnants.entity.RemnantEntity;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Draws the floating, slowly spinning soul crown above the head and one orbiting soul orb per phase. */
public class RemnantCrownLayer extends RenderLayer<RemnantEntity, RemnantModel> {
	private static final ResourceLocation TEXTURE = RemnantsMod.id("textures/entity/remnant_crown.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	private final RemnantCrownModel model;

	public RemnantCrownLayer(RenderLayerParent<RemnantEntity, RemnantModel> parent, EntityModelSet models) {
		super(parent);
		this.model = new RemnantCrownModel(models.bakeLayer(RemnantCrownModel.LAYER));
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, RemnantEntity entity, float limbSwing, float limbSwingAmount,
		float partialTick, float age, float headYaw, float headPitch) {
		if (entity.isForgotten() && !entity.isEmpowered()) {
			return; // only the Crowned ones among the Forgotten wear one
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
		int color = RemnantModel.tintOf(entity) | 0xFF000000;

		pose.pushPose();
		this.getParentModel().head.translateAndRotate(pose);
		pose.translate(0.0F, -0.62F + 0.03F * Mth.sin(age * 0.1F), 0.0F);
		pose.mulPose(Axis.YP.rotationDegrees(age * 2.0F));
		float s = entity.isEmpowered() ? 1.15F : 1.0F;
		pose.scale(s, s, s);
		this.model.crown.render(pose, vc, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
		pose.popPose();

		int orbs = entity.getPhase() + (entity.isEmpowered() ? 1 : 0);
		for (int i = 0; i < orbs; i++) {
			float a = age * 0.08F + i * Mth.TWO_PI / orbs;
			pose.pushPose();
			pose.translate(Mth.sin(a) * 0.8F, 0.35F + 0.15F * Mth.sin(age * 0.15F + i * 2.1F), Mth.cos(a) * 0.8F);
			pose.mulPose(Axis.YP.rotationDegrees(age * 6.0F));
			pose.mulPose(Axis.XP.rotationDegrees(age * 4.0F));
			this.model.orb.render(pose, vc, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
			pose.popPose();
		}
	}
}
