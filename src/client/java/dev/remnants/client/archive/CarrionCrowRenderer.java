package dev.remnants.client.archive;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.remnants.RemnantsMod;
import dev.remnants.entity.CarrionCrowEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** A Carrion Crow: its model, soul-lit eye sockets, and the stolen stack hanging from its beak. */
public class CarrionCrowRenderer extends MobRenderer<CarrionCrowEntity, CarrionCrowModel> {
	public static final ResourceLocation TEXTURE = RemnantsMod.id("textures/entity/carrion_crow.png");
	public static final ResourceLocation GLOW = RemnantsMod.id("textures/entity/carrion_crow_glow.png");

	public CarrionCrowRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new CarrionCrowModel(ctx.bakeLayer(CarrionCrowModel.LAYER)), 0.3F);
		this.addLayer(new RenderLayer<>(this) {
			@Override
			public void render(PoseStack pose, MultiBufferSource buffers, int light, CarrionCrowEntity e, float limbSwing, float amount,
				float partial, float age, float headYaw, float headPitch) {
				this.getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), 0xF000F0, OverlayTexture.NO_OVERLAY, -1);
			}
		});
	}

	@Override
	public void render(CarrionCrowEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		super.render(e, yaw, partial, pose, buffers, light);
		ItemStack held = e.held();
		if (held.isEmpty()) {
			return;
		}
		float body = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-body));
		pose.translate(0.0, e.state() == CarrionCrowEntity.PERCH ? 0.85 : 0.75, -0.62);
		pose.mulPose(Axis.XP.rotationDegrees(90.0F));
		pose.scale(0.42F, 0.42F, 0.42F);
		Minecraft.getInstance().getItemRenderer().renderStatic(held, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers,
			e.level(), e.getId());
		pose.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(CarrionCrowEntity entity) {
		return TEXTURE;
	}

	/** Art 2.0 hit feel: the first two hurt ticks flash white. */
	@Override
	protected float getWhiteOverlayProgress(dev.remnants.entity.CarrionCrowEntity entity, float partial) {
		return dev.remnants.client.fx.CombatFx.flash(entity, partial);
	}
}
