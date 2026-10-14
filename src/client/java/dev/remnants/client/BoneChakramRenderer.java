package dev.remnants.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.remnants.entity.BoneChakramEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The thrown chakram: its own item sprite lying flat, tilted a little along its flight, spinning fast. */
public class BoneChakramRenderer extends EntityRenderer<BoneChakramEntity> {
	private final ItemRenderer items;

	public BoneChakramRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.items = ctx.getItemRenderer();
		this.shadowRadius = 0.15F;
	}

	@Override
	public void render(BoneChakramEntity chakram, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		ItemStack stack = chakram.getItem();
		if (stack.isEmpty()) {
			return;
		}
		float age = chakram.tickCount + partialTick;
		pose.pushPose();
		pose.translate(0.0F, 0.15F, 0.0F);
		pose.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(partialTick, chakram.yRotO, chakram.getYRot())));
		pose.mulPose(Axis.XP.rotationDegrees(90.0F - 12.0F));
		pose.mulPose(Axis.ZP.rotationDegrees(age * (chakram.isReturning() ? -48.0F : 48.0F)));
		pose.scale(0.85F, 0.85F, 0.85F);
		this.items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, chakram.level(), chakram.getId());
		pose.popPose();
		super.render(chakram, yaw, partialTick, pose, buffers, light);
	}

	@Override
	protected int getBlockLightLevel(BoneChakramEntity chakram, BlockPos pos) {
		return Math.max(7, super.getBlockLightLevel(chakram, pos));
	}

	@Override
	@SuppressWarnings("deprecation")
	public ResourceLocation getTextureLocation(BoneChakramEntity chakram) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
