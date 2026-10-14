package dev.remnants.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.remnants.block.BurialCasketBlock;
import dev.remnants.block.entity.BurialCasketBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A dug-up casket climbs out of its grave: while the block is {@link BurialCasketBlock#RISING} the static model is off
 * and this draws it coming up from a block below (the earth around hides the part still underground), with a small
 * wobble as it breaks free. Otherwise it draws nothing.
 */
public class BurialCasketRenderer implements BlockEntityRenderer<BurialCasketBlockEntity> {
	public BurialCasketRenderer(BlockEntityRendererProvider.Context ctx) {
	}

	@Override
	public void render(BurialCasketBlockEntity casket, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		BlockState state = casket.getBlockState();
		if (!state.getValue(BurialCasketBlock.RISING) || casket.getLevel() == null) {
			return;
		}
		float t = Mth.clamp((casket.getLevel().getGameTime() - casket.riseStart() + partialTick) / BurialCasketBlock.RISE_TICKS, 0.0F, 1.0F);
		float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
		float wobble = (1.0F - t) * Mth.sin(t * 40.0F) * 3.0F;
		pose.pushPose();
		pose.translate(0.5F, eased - 1.0F, 0.5F);
		pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(wobble));
		pose.translate(-0.5F, 0.0F, -0.5F);
		Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state.setValue(BurialCasketBlock.RISING, false), pose, buffers, light,
			OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}
}
