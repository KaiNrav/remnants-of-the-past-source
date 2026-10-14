package dev.remnants.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.remnants.RemnantsMod;
import dev.remnants.entity.SoulKingEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the Soul King (the model is built at twice player scale, so it renders 1:1), rising out of the altar during
 * the ritual and spinning in Whirlwind. Eyes, gems, blades and the bow string glow in the dark.
 */
public class SoulKingRenderer extends MobRenderer<SoulKingEntity, SoulKingModel> {
	private static final ResourceLocation TEXTURE = RemnantsMod.id("textures/entity/soul_king.png");
	private static final ResourceLocation GLOW = RemnantsMod.id("textures/entity/soul_king_glow.png");

	public SoulKingRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new SoulKingModel(ctx.bakeLayer(SoulKingModel.LAYER)), 1.8F);
		this.addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return RenderType.eyes(GLOW);
			}
		});
		this.addLayer(new SoulKingWeaponLayer(this));
	}

	@Override
	public ResourceLocation getTextureLocation(SoulKingEntity entity) {
		return TEXTURE;
	}

	/** The King is self-lit, all light and shade is painted into his texture,
	 * so he reads the same in a crypt at night as in daylight. */
	@Override
	protected int getBlockLightLevel(SoulKingEntity entity, net.minecraft.core.BlockPos pos) {
		return 15;
	}

	@Override
	public void render(SoulKingEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		int intro = entity.getIntro();
		if (intro > 0) {
			// 1.3: the summoning is a ritual of its own (SoulKingRitual); the King only steps out of the pillar of light at its end
			float t = SoulKingEntity.INTRO_TICKS - intro + partialTick;
			SoulKingRitual.render(entity, t, partialTick, pose, buffers);
			float scale = SoulKingRitual.kingScale(t);
			if (scale <= 0.0F) {
				pose.popPose();
				return;
			}
			pose.scale(scale, scale, scale);
		}
		super.render(entity, yaw, partialTick, pose, buffers, light);
		pose.popPose();
	}

	/** Art 2.0 hit feel: the first two hurt ticks flash white (on top of vanilla's red). */
	@Override
	protected float getWhiteOverlayProgress(SoulKingEntity entity, float partial) {
		return dev.remnants.client.fx.CombatFx.flash(entity, partial);
	}

	@Override
	protected float getShadowRadius(SoulKingEntity entity) {
		int intro = entity.getIntro();
		return intro > 0 && SoulKingEntity.INTRO_TICKS - intro < SoulKingEntity.EMERGE_TICK ? 0.0F : super.getShadowRadius(entity);
	}

	@Override
	public boolean shouldRender(SoulKingEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
		return entity.getIntro() > 0 || super.shouldRender(entity, frustum, x, y, z);   // the ritual reaches out to the pedestals
	}

	@Override
	protected void setupRotations(SoulKingEntity entity, PoseStack pose, float bob, float bodyYaw, float partialTick, float scale) {
		if (entity.getAction() == SoulKingEntity.Action.WHIRLWIND && entity.tickCount - entity.clientActionStart > 20) {
			bodyYaw += (entity.tickCount + partialTick) * 36.0F;
		}
		super.setupRotations(entity, pose, bob, bodyYaw, partialTick, scale);
	}

	@Override
	protected float getFlipDegrees(SoulKingEntity entity) {
		return 0.0F;
	}
}
