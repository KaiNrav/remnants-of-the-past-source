package dev.remnants.client.archive;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.remnants.entity.DecoyEchoEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A ghostly copy of a player - the Decoy Echo, and the Memorial's replaying figure: the player model in their own skin,
 * translucent and tinted toward soul light, holding what they held. A decoy fades as its time runs out; a replay ghost
 * takes its alpha from the replay.
 */
public class DecoyEchoRenderer extends LivingEntityRenderer<DecoyEchoEntity, PlayerModel<DecoyEchoEntity>> {
	private float alpha = 0.7F;

	public DecoyEchoRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, null, 0.0F);
		this.model = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false) {
			@Override
			public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, int color) {
				int a = Mth.clamp((int) (FastColor.ARGB32.alpha(color) * DecoyEchoRenderer.this.alpha), 0, 255);
				super.renderToBuffer(pose, vc, light, overlay, FastColor.ARGB32.color(a, 0xB8F4F0));
			}
		};
		this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
	}

	@Override
	public void render(DecoyEchoEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		this.alpha = e.replay ? e.replayAlpha * 0.75F : 0.7F * (1.0F - 0.6F * Math.max(0, e.lifeFraction(partial) - 0.7F) / 0.3F);
		this.shadowRadius = e.replay ? 0.0F : 0.4F;
		if (this.alpha > 0.02F) {
			super.render(e, yaw, partial, pose, buffers, light);
		}
	}

	@Nullable
	@Override
	protected RenderType getRenderType(DecoyEchoEntity e, boolean visible, boolean translucent, boolean glowing) {
		return RenderType.entityTranslucent(this.getTextureLocation(e));
	}

	@Override
	public ResourceLocation getTextureLocation(DecoyEchoEntity e) {
		UUID owner = e.owner().orElse(null);
		if (owner == null) {
			return DefaultPlayerSkin.getDefaultTexture();
		}
		var connection = Minecraft.getInstance().getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner);
		return info != null ? info.getSkin().texture() : DefaultPlayerSkin.get(owner).texture();
	}

	@Override
	protected boolean shouldShowName(DecoyEchoEntity e) {
		return false;
	}

	@Override
	protected float getFlipDegrees(DecoyEchoEntity e) {
		return 0.0F;
	}
}
