package dev.remnants.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.remnants.entity.RemnantEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Draws a Remnant with its owner's skin (wide or slim arms), translucent and softly glowing, hovering a little,
 * wearing solid copies of the owner's armor and held items.
 */
public class RemnantRenderer extends HumanoidMobRenderer<RemnantEntity, RemnantModel> {
	private final RemnantModel wide;
	private final RemnantModel slim;

	public RemnantRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new RemnantModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.4F);
		this.wide = this.model;
		this.slim = new RemnantModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.addLayer(new HumanoidArmorLayer<>(this,
			new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
			new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
			ctx.getModelManager()));
		this.addLayer(new RemnantCrownLayer(this, ctx.getModelSet()));
		this.addLayer(new ForgottenVariantLayer(this, ctx.getModelSet()));
	}

	private static PlayerSkin skinOf(RemnantEntity entity) {
		UUID owner = entity.getOwnerUuid().orElse(null);
		if (owner == null) {
			return DefaultPlayerSkin.get(entity.getUUID());
		}
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		PlayerInfo info = connection != null ? connection.getPlayerInfo(owner) : null;
		return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner);
	}

	@Override
	public void render(RemnantEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
		if (entity.isBurrowed()) {
			return; // gone under the ground (armour and held items would still show on an invisible mob)
		}
		this.model = skinOf(entity).model() == PlayerSkin.Model.SLIM ? this.slim : this.wide;
		poseStack.pushPose();
		float bob = 0.06F + 0.05F * Mth.sin((entity.tickCount + partialTick) * 0.08F);
		// kneeling while dormant: sink so the folded legs rest on the ground
		poseStack.translate(0.0F, (entity.getAnim() == RemnantEntity.ANIM_DORMANT ? bob - 0.62F : bob) + groundOffset(entity, partialTick), 0.0F);
		super.render(entity, yaw, partialTick, poseStack, buffers, light);
		poseStack.popPose();
	}

	/**
	 * How deep under the ground it is drawn: climbing out (a hand first, then head and shoulders, then a lunge; the
	 * earth hides the rest) or sinking in to burrow.
	 */
	static float groundOffset(RemnantEntity e, float partialTick) {
		if (e.isEmerging()) {
			float p = e.emergeProgress(partialTick);
			if (p < 0.35F) {
				return -1.9F + 0.7F * (p / 0.35F);
			}
			if (p < 0.7F) {
				return -1.2F + 0.6F * ((p - 0.35F) / 0.35F);
			}
			float q = 1.0F - (p - 0.7F) / 0.3F;
			return -0.6F * q * q;
		}
		int sink = e.getSinkTicks();
		return sink > 0 ? -2.0F * Mth.clamp((sink - 1 + partialTick) / RemnantEntity.SINK_TICKS, 0.0F, 1.0F) : 0.0F;
	}

	@Override
	protected float getShadowRadius(RemnantEntity entity) {
		return entity.isEmerging() || entity.getSinkTicks() > 0 ? 0.0F : super.getShadowRadius(entity);
	}

	@Override
	public ResourceLocation getTextureLocation(RemnantEntity entity) {
		return skinOf(entity).texture();
	}

	@Override
	@Nullable
	protected RenderType getRenderType(RemnantEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
		if (glowing && !bodyVisible) {
			return super.getRenderType(entity, bodyVisible, translucent, glowing);
		}
		return RenderType.entityTranslucent(this.getTextureLocation(entity));
	}

	@Override
	protected int getBlockLightLevel(RemnantEntity entity, BlockPos pos) {
		return Math.max(11, super.getBlockLightLevel(entity, pos));
	}

	/** Art 2.0 hit feel: the first two hurt ticks flash white. */
	@Override
	protected float getWhiteOverlayProgress(RemnantEntity entity, float partial) {
		return dev.remnants.client.fx.CombatFx.flash(entity, partial);
	}
}
