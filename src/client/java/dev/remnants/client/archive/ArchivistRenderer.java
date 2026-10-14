package dev.remnants.client.archive;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.remnants.RemnantsMod;
import dev.remnants.client.craft.CraftFx;
import dev.remnants.entity.ArchivistEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Archivist: its model, the soul light of its spectacles, candles and runes (a glow layer), and in phase 2 the
 * barrier - four arcs of turning runes round it, one fewer for every eye closed.
 */
public class ArchivistRenderer extends MobRenderer<ArchivistEntity, ArchivistModel> {
	public static final ResourceLocation TEXTURE = RemnantsMod.id("textures/entity/archivist.png");
	public static final ResourceLocation GLOW = RemnantsMod.id("textures/entity/archivist_glow.png");

	public ArchivistRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new ArchivistModel(ctx.bakeLayer(ArchivistModel.LAYER)), 0.9F);
		this.addLayer(new GlowLayer(this));
	}

	@Override
	public ResourceLocation getTextureLocation(ArchivistEntity entity) {
		return TEXTURE;
	}

	/** Art 2.0 hit feel: the first two hurt ticks flash white. */
	@Override
	protected float getWhiteOverlayProgress(ArchivistEntity entity, float partial) {
		return dev.remnants.client.fx.CombatFx.flash(entity, partial);
	}

	@Override
	protected float getFlipDegrees(ArchivistEntity entity) {
		return 0.0F;   // it rises and comes apart (see its death clip) instead of falling over
	}

	@Override
	public void render(ArchivistEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		super.render(e, yaw, partial, pose, buffers, light);
		int arcs = e.phase() == 2 ? e.barrier() : 0;
		if (arcs <= 0 || e.isStunned() || e.deathTime > 0) {
			return;
		}
		float t = e.tickCount + partial;
		VertexConsumer vc = buffers.getBuffer(CraftFx.glow(CraftFx.RUNES));
		for (int arc = 0; arc < arcs; arc++) {
			for (int k = 0; k < 6; k++) {
				double a = Math.toRadians(arc * 90.0 + 8.0 + k * 14.8 + t * 1.6);
				float r = 1.9F;
				float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r;
				float y = 1.4F + 0.35F * Mth.sin(t * 0.08F + arc * 1.7F + k * 0.6F);
				int rune = (arc * 3 + k) % CraftFx.RUNE_COUNT;
				int alpha = (int) (170 + 60 * Mth.sin(t * 0.2F + k));
				CraftFx.billboard(vc, pose, x, y, z, 0.55F, rune / 8.0F, 0.0F, (rune + 1) / 8.0F, 1.0F,
					alpha << 24 | 0x8FF5EC, LightTexture.FULL_BRIGHT);
			}
		}
	}

	static final class GlowLayer extends RenderLayer<ArchivistEntity, ArchivistModel> {
		GlowLayer(RenderLayerParent<ArchivistEntity, ArchivistModel> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, ArchivistEntity e, float limbSwing, float amount,
			float partial, float age, float headYaw, float headPitch) {
			this.getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), 0xF000F0, OverlayTexture.NO_OVERLAY, -1);
		}
	}
}
