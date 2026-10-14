package dev.remnants.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.remnants.block.SpiritAltarBlock;
import dev.remnants.block.entity.SpiritAltarBlockEntity;
import dev.remnants.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The summoning circle explains itself: while pedestals are missing, each empty spot shows a translucent, softly pulsing
 * soul-tinted Soul Pedestal (full-bright, so it reads at night and through fog), slowly bobbing. Complete circles draw
 * nothing.
 */
public class SpiritAltarRenderer implements BlockEntityRenderer<SpiritAltarBlockEntity> {
	private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};

	public SpiritAltarRenderer(BlockEntityRendererProvider.Context ctx) {
	}

	@Override
	public void render(SpiritAltarBlockEntity altar, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		Level level = altar.getLevel();
		if (level == null) {
			return;
		}
		BlockPos center = altar.getBlockPos();
		List<BlockPos> missing = new ArrayList<>();
		SpiritAltarBlock.findPedestals(level, center, missing);
		if (missing.isEmpty()) {
			return;
		}
		BlockState ghost = ModBlocks.SOUL_PEDESTAL.defaultBlockState();
		BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(ghost);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
		float time = level.getGameTime() + partialTick;
		RandomSource random = RandomSource.create(42L);
		for (int i = 0; i < missing.size(); i++) {
			BlockPos spot = missing.get(i);
			float phase = time * 0.08F + i * 1.3F;
			float alpha = 0.32F + 0.16F * Mth.sin(phase);
			pose.pushPose();
			pose.translate(spot.getX() - center.getX(), spot.getY() - center.getY() + 0.04F * Mth.sin(phase * 0.5F), spot.getZ() - center.getZ());
			PoseStack.Pose last = pose.last();
			for (Direction side : SIDES) {
				random.setSeed(42L);
				for (BakedQuad quad : model.getQuads(ghost, side, random)) {
					vc.putBulkData(last, quad, 0.55F, 0.95F, 1.0F, alpha, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
				}
			}
			pose.popPose();
		}
	}

	/** The ghosts stand five blocks from the altar, so they must not be culled with the altar's own block. */
	@Override
	public boolean shouldRenderOffScreen(SpiritAltarBlockEntity altar) {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 48;
	}
}
