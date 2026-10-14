package dev.remnants.client.crypt;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.remnants.block.entity.BelfryBlockEntity;
import dev.remnants.client.craft.CraftFx;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** The Belfry's heart: the missing-block hologram and the formation wave (the 1.3 multiblock effects). */
public class BelfryRenderer implements BlockEntityRenderer<BelfryBlockEntity> {
	public BelfryRenderer(BlockEntityRendererProvider.Context ctx) {
	}

	@Override
	public void render(BelfryBlockEntity belfry, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		CraftFx.hologram(belfry, partial, pose, buffers);
		CraftFx.wave(belfry, partial, pose, buffers);
	}

	/** The tower is five blocks tall: keep drawing while the stone itself is off screen. */
	@Override
	public boolean shouldRenderOffScreen(BelfryBlockEntity belfry) {
		return true;
	}
}
