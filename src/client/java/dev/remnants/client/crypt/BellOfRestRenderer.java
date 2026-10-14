package dev.remnants.client.crypt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.remnants.RemnantsMod;
import dev.remnants.block.entity.BellOfRestBlockEntity;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Bell of Rest's body swinging under its yoke, the vanilla bell's swing curve (a full ring), a third of it for
 * the hum between the waves of a Night of Memory; during a night the bell also sways a little on its own.
 */
public class BellOfRestRenderer implements BlockEntityRenderer<BellOfRestBlockEntity> {
	public static final ResourceLocation BODY = RemnantsMod.id("block/bell_of_rest_body");
	private static final float PIVOT_Y = 13.5F / 16.0F;

	public BellOfRestRenderer(BlockEntityRendererProvider.Context ctx) {
	}

	@Override
	public void render(BellOfRestBlockEntity bell, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		float xRot = 0.0F, zRot = 0.0F;
		if (bell.shaking) {
			float t = bell.ticks + partial;
			float s = Mth.sin(t / Mth.PI) / (4.0F + t / 3.0F) * (bell.strength == BellOfRestBlockEntity.HUM ? 0.35F : 1.0F);
			switch (bell.clickDirection) {
				case NORTH -> xRot = -s;
				case SOUTH -> xRot = s;
				case EAST -> zRot = -s;
				case WEST -> zRot = s;
				default -> {
				}
			}
		}
		if (bell.night && bell.getLevel() != null) {
			xRot += Mth.sin((bell.getLevel().getGameTime() + partial) * 0.05F) * 0.03F;
		}
		pose.pushPose();
		pose.translate(0.5F, PIVOT_Y, 0.5F);
		pose.mulPose(Axis.XP.rotation(xRot));
		pose.mulPose(Axis.ZP.rotation(zRot));
		pose.translate(-0.5F, -PIVOT_Y, -0.5F);
		BakedModel model = ((FabricBakedModelManager) Minecraft.getInstance().getModelManager()).getModel(BODY);
		Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
			null, model, 1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}
}
