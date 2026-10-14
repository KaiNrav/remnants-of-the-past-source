package dev.remnants.client.craft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Ghost Step (the Mourner's set): the player is invisible on the server for two seconds (mobs lose them); every client
 * draws them back as a translucent, soul-tinted ghost instead of nothing.
 */
public class GhostFormLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final Map<Integer, Long> ENDS = new HashMap<>();

	public GhostFormLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	public static void start(int entityId, int ticks) {
		var level = Minecraft.getInstance().level;
		if (level != null) {
			ENDS.put(entityId, level.getGameTime() + ticks);
		}
	}

	/** Client tick: forget every ghost when leaving a world (entity ids and game times mean nothing in the next one). */
	public static void tick(Minecraft mc) {
		if (mc.level == null && !ENDS.isEmpty()) {
			ENDS.clear();
		}
	}

	private static float left(int entityId, float partial) {
		Long end = ENDS.get(entityId);
		var level = Minecraft.getInstance().level;
		if (end == null || level == null) {
			return 0;
		}
		float left = end - level.getGameTime() - partial;
		if (left <= 0) {
			ENDS.remove(entityId);
		}
		return left;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
		float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		float left = left(player.getId(), partialTick);
		if (left <= 0) {
			return;
		}
		float alpha = Mth.clamp(left / 6.0F, 0.0F, 1.0F) * (0.38F + 0.12F * Mth.sin(ageInTicks * 0.6F));
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
		this.getParentModel().renderToBuffer(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
			FastColor.ARGB32.color((int) (alpha * 255), 150, 228, 255));
	}
}
