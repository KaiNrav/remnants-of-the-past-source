package dev.remnants.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.remnants.entity.SpectralWeaponEntity;
import dev.remnants.item.ArsenalItem;
import dev.remnants.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;

/** Renders the flying ghost weapon as the real 3D item, blade first along its flight path; axes and maces tumble.
 * Copies of the Soul King's Arsenal are drawn with that form's own 3D model (the item's in-hand face). */
public class SpectralWeaponRenderer extends EntityRenderer<SpectralWeaponEntity> {
	private final ItemRenderer items;

	public SpectralWeaponRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.items = ctx.getItemRenderer();
		this.shadowRadius = 0.0F;
	}

	@Override
	public void render(SpectralWeaponEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		ItemStack stack = entity.getItem();
		if (stack.isEmpty()) {
			return;
		}
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F));
		pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
		if (stack.is(ModItems.SOUL_KING_ARSENAL)) {
			this.renderArsenal(stack, pose, buffers);
			pose.popPose();
			super.render(entity, yaw, partialTick, pose, buffers, light);
			return;
		}
		boolean tumble = entity.isLaunched() && !entity.isStuck() && (stack.getItem() instanceof AxeItem || stack.getItem() instanceof MaceItem);
		if (tumble) {
			pose.mulPose(Axis.ZP.rotationDegrees(-(entity.tickCount + partialTick) * 40.0F));
		}
		// item sprites point up-right; turn the blade onto the +X flight axis
		pose.mulPose(Axis.ZP.rotationDegrees(-45.0F));
		boolean trident = stack.is(Items.TRIDENT);
		float scale = trident ? 2.6F : 1.4F;
		pose.scale(scale, scale, scale);
		this.items.renderStatic(stack, trident ? ItemDisplayContext.GROUND : ItemDisplayContext.NONE, 0xF000F0, OverlayTexture.NO_OVERLAY,
			pose, buffers, entity.level(), entity.getId());
		pose.popPose();
		super.render(entity, yaw, partialTick, pose, buffers, light);
	}

	/** Flight runs along +X here. The model stands on +Y (tip up, grip around y 0..8 of 16): lay it tip-first along the
	 * flight, flat side facing sideways, and pull it back so only the point sinks into a wall it sticks in. */
	private void renderArsenal(ItemStack stack, PoseStack pose, MultiBufferSource buffers) {
		Minecraft mc = Minecraft.getInstance();
		BakedModel model = mc.getModelManager().getModel(TwoFaceItemRenderer.model3d(ArsenalItem.mode(stack)));
		if (model == null || model == mc.getModelManager().getMissingModel()) {
			return;
		}
		pose.mulPose(Axis.XP.rotationDegrees(90.0F));
		pose.mulPose(Axis.ZP.rotationDegrees(-90.0F));
		pose.scale(0.62F, 0.62F, 0.62F);
		pose.translate(0.0F, -0.94F, 0.0F);
		this.items.render(TwoFaceItemRenderer.unglinted(stack), ItemDisplayContext.NONE, false, pose, buffers, 0xF000F0,
			OverlayTexture.NO_OVERLAY, model);
	}

	@Override
	public ResourceLocation getTextureLocation(SpectralWeaponEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
