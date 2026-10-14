package dev.remnants.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.remnants.RemnantsMod;
import dev.remnants.entity.ForgottenVariant;
import dev.remnants.entity.RemnantEntity;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Kelp, ice or sand-cloth on a biome Forgotten Remnant, following its head, body and arms (ForgottenVariantModel). */
public class ForgottenVariantLayer extends RenderLayer<RemnantEntity, RemnantModel> {
	private static final ResourceLocation TEXTURE = RemnantsMod.id("textures/entity/forgotten_variants.png");
	private final ForgottenVariantModel model;

	public ForgottenVariantLayer(RenderLayerParent<RemnantEntity, RemnantModel> parent, EntityModelSet models) {
		super(parent);
		this.model = new ForgottenVariantModel(models.bakeLayer(ForgottenVariantModel.LAYER));
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, RemnantEntity entity, float limbSwing, float limbSwingAmount,
		float partialTick, float age, float headYaw, float headPitch) {
		ForgottenVariant variant = entity.getVariant();
		if (variant == ForgottenVariant.NONE || entity.isInvisible()) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
		int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
		int color = 0xE6FFFFFF; // the details are a little more solid than the ghost wearing them
		RemnantModel body = this.getParentModel();
		switch (variant) {
			case DROWNED -> {
				this.on(body.head, this.model.drownedHead, pose, vc, light, overlay, color);
				this.on(body.body, this.model.drownedBody, pose, vc, light, overlay, color);
				this.on(body.rightArm, this.model.drownedArm, pose, vc, light, overlay, color);
			}
			case FROZEN -> {
				if (entity.hasCrust()) {
					this.on(body.head, this.model.frozenHead, pose, vc, light, overlay, 0xD0FFFFFF);
					this.on(body.rightArm, this.model.frozenRightArm, pose, vc, light, overlay, 0xD0FFFFFF);
					this.on(body.leftArm, this.model.frozenLeftArm, pose, vc, light, overlay, 0xD0FFFFFF);
				}
			}
			case SANDY -> {
				this.on(body.head, this.model.sandyHead, pose, vc, light, overlay, color);
				this.on(body.body, this.model.sandyBody, pose, vc, light, overlay, color);
			}
			default -> {
			}
		}
	}

	private void on(ModelPart bone, ModelPart piece, PoseStack pose, VertexConsumer vc, int light, int overlay, int color) {
		pose.pushPose();
		bone.translateAndRotate(pose);
		piece.render(pose, vc, light, overlay, color);
		pose.popPose();
	}
}
