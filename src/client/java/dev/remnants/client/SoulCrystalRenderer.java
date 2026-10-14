package dev.remnants.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.remnants.RemnantsMod;
import dev.remnants.entity.SoulCrystalEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A bobbing, tumbling glowing soul cube with a smaller counter-rotating core. */
public class SoulCrystalRenderer extends EntityRenderer<SoulCrystalEntity> {
	private static final ResourceLocation TEXTURE = RemnantsMod.id("textures/entity/remnant_crown.png");
	private final RemnantCrownModel model;

	public SoulCrystalRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.model = new RemnantCrownModel(ctx.bakeLayer(RemnantCrownModel.LAYER));
		this.shadowRadius = 0.4F;
	}

	@Override
	public void render(SoulCrystalEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		float age = entity.tickCount + partialTick;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
		pose.pushPose();
		pose.translate(0.0F, 0.6F + Mth.sin(age * 0.1F) * 0.15F, 0.0F);
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(age * 3.0F));
		pose.mulPose(Axis.XP.rotationDegrees(45.0F));
		pose.mulPose(Axis.ZP.rotationDegrees(45.0F));
		pose.scale(4.0F, 4.0F, 4.0F);
		this.model.orb.render(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, 0xB070E8FF);
		pose.popPose();
		pose.mulPose(Axis.YP.rotationDegrees(-age * 5.0F));
		pose.mulPose(Axis.XP.rotationDegrees(age * 4.0F));
		pose.scale(2.0F, 2.0F, 2.0F);
		this.model.orb.render(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		pose.popPose();
		super.render(entity, yaw, partialTick, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(SoulCrystalEntity entity) {
		return TEXTURE;
	}
}
