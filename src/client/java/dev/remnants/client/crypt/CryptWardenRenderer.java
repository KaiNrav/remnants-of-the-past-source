package dev.remnants.client.crypt;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.remnants.RemnantsMod;
import dev.remnants.entity.CryptWardenEntity;
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
 * The Crypt Warden: grey stone while it stands on its plinth, the stone cracking off over the 50 awakening ticks (the
 * stone texture drawn over the armour, fading out), soul light in the visor, the gem and the runes once it is awake.
 */
public class CryptWardenRenderer extends MobRenderer<CryptWardenEntity, CryptWardenModel> {
	public static final ResourceLocation TEXTURE = RemnantsMod.id("textures/entity/crypt_warden.png");
	public static final ResourceLocation STONE = RemnantsMod.id("textures/entity/crypt_warden_stone.png");
	public static final ResourceLocation GLOW = RemnantsMod.id("textures/entity/crypt_warden_glow.png");

	public CryptWardenRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new CryptWardenModel(ctx.bakeLayer(CryptWardenModel.LAYER)), 1.0F);
		this.addLayer(new StoneLayer(this));
		this.addLayer(new GlowLayer(this));
	}

	/** It has a death of its own (to its knees, then face down): no sideways topple. */
	@Override
	protected float getFlipDegrees(CryptWardenEntity entity) {
		return 0.0F;
	}

	/** Art 2.0 hit feel: the first two hurt ticks flash white (on top of vanilla's red). */
	@Override
	protected float getWhiteOverlayProgress(CryptWardenEntity entity, float partial) {
		return dev.remnants.client.fx.CombatFx.flash(entity, partial);
	}

	@Override
	public ResourceLocation getTextureLocation(CryptWardenEntity entity) {
		return entity.isDormant() ? STONE : TEXTURE;
	}

	/** 0 = all stone .. 1 = the stone is gone. */
	static float awake(CryptWardenEntity e, float partial) {
		if (e.isDormant()) {
			return 0.0F;
		}
		if (e.getAnim() != CryptWardenEntity.ANIM_AWAKEN) {
			return 1.0F;
		}
		return Mth.clamp((e.tickCount - e.clientAnimStart + partial) / CryptWardenEntity.AWAKEN_TICKS, 0.0F, 1.0F);
	}

	static final class StoneLayer extends RenderLayer<CryptWardenEntity, CryptWardenModel> {
		StoneLayer(RenderLayerParent<CryptWardenEntity, CryptWardenModel> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, CryptWardenEntity e, float limbSwing, float amount,
			float partial, float age, float headYaw, float headPitch) {
			float a = awake(e, partial);
			if (e.isDormant() || a >= 1.0F) {
				return;
			}
			int alpha = Mth.clamp((int) ((1.0F - a) * 255.0F), 0, 255);
			this.getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(STONE)), light,
				OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(alpha, 255, 255, 255));
		}
	}

	static final class GlowLayer extends RenderLayer<CryptWardenEntity, CryptWardenModel> {
		GlowLayer(RenderLayerParent<CryptWardenEntity, CryptWardenModel> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, CryptWardenEntity e, float limbSwing, float amount,
			float partial, float age, float headYaw, float headPitch) {
			float a = awake(e, partial);
			if (a <= 0.0F) {
				return;
			}
			int k = Mth.clamp((int) (a * 255.0F), 0, 255);
			this.getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), 0xF000F0, OverlayTexture.NO_OVERLAY,
				FastColor.ARGB32.color(255, k, k, k));
		}
	}
}
