package dev.remnants.client.crypt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.remnants.RemnantsMod;
import dev.remnants.entity.KeenerEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * The Keener: hidden it is only a faint shimmer ({@link #HIDDEN_ALPHA}) with no shadow and no glow; revealed (hit, a
 * Hand Bell, a spyglass) it is a translucent ghost with soul eyes until the reveal ends.
 */
public class KeenerRenderer extends MobRenderer<KeenerEntity, KeenerModel> {
	public static final ResourceLocation TEXTURE = RemnantsMod.id("textures/entity/keener.png");
	public static final ResourceLocation GLOW = RemnantsMod.id("textures/entity/keener_glow.png");
	public static final float HIDDEN_ALPHA = 0.1F, SHOWN_ALPHA = 0.78F;
	private float alpha = SHOWN_ALPHA;

	public KeenerRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, null, 0.0F);
		ModelPart root = ctx.bakeLayer(KeenerModel.LAYER);
		this.model = new KeenerModel(root) {
			@Override
			public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, int color) {
				int a = Mth.clamp((int) (FastColor.ARGB32.alpha(color) * KeenerRenderer.this.alpha), 0, 255);
				super.renderToBuffer(pose, vc, light, overlay, FastColor.ARGB32.color(a, color));
			}
		};
		this.addLayer(new GlowLayer(this));
	}

	@Override
	public void render(KeenerEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		this.alpha = e.isRevealed() ? SHOWN_ALPHA : HIDDEN_ALPHA;
		this.shadowRadius = e.isRevealed() ? 0.45F : 0.0F;
		super.render(e, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(KeenerEntity entity) {
		return TEXTURE;
	}

	/** Art 2.0 hit feel: the first two hurt ticks flash white. */
	@Override
	protected float getWhiteOverlayProgress(KeenerEntity entity, float partial) {
		return dev.remnants.client.fx.CombatFx.flash(entity, partial);
	}

	@Override
	protected float getFlipDegrees(KeenerEntity entity) {
		return 0.0F;   // a ghost does not fall over: it fades (see the death particles)
	}

	static final class GlowLayer extends RenderLayer<KeenerEntity, KeenerModel> {
		GlowLayer(RenderLayerParent<KeenerEntity, KeenerModel> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, KeenerEntity e, float limbSwing, float amount,
			float partial, float age, float headYaw, float headPitch) {
			if (!e.isRevealed()) {
				return;
			}
			this.getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), 0xF000F0, OverlayTexture.NO_OVERLAY, -1);
		}
	}
}
