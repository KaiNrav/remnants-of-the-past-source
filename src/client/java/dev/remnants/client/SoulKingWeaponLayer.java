package dev.remnants.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.remnants.entity.SoulKingEntity;
import dev.remnants.item.ArsenalItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The Soul King holds the very weapons he drops: the Arsenal's 3D item models, one per hand, on the
 * weapon bones of his model (whose old cube weapons are hidden), so every keyframed swing, throw and volley moves them.
 */
public class SoulKingWeaponLayer extends RenderLayer<SoulKingEntity, SoulKingModel> {
	/** Per form: where the grip sits in the item model (x, y, z in item pixels), the scale against the King's old
	 * cube weapons (same reach from the fist), and how the item's axes (tip +Y, quillons / prongs along Z, flat side
	 * along X; bow: upper limb +Y, string +Z) turn onto the bone's (tip -Z, guard along Y, prongs along X;
	 * bow: upper limb -Y, string +Z). */
	private static final float[][] GRIP = {{8.0F, 3.6F, 8.0F}, {8.0F, 7.0F, 7.0F}, {8.0F, 4.1F, 8.0F}, {8.0F, 3.4F, 8.0F}};
	private static final float[] SCALE = {1.16F, 1.15F, 1.5F, 1.0F};

	private ItemStack[] stacks;

	public SoulKingWeaponLayer(RenderLayerParent<SoulKingEntity, SoulKingModel> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, SoulKingEntity king, float limbSwing,
			float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
		Minecraft mc = Minecraft.getInstance();
		if (this.stacks == null) {
			this.stacks = new ItemStack[4];
			for (int form = 0; form < 4; form++) {
				this.stacks[form] = TwoFaceItemRenderer.unglinted(ArsenalItem.display(form));
			}
		}
		for (int form = 0; form < 4; form++) {
			BakedModel model = mc.getModelManager().getModel(TwoFaceItemRenderer.model3d(form));
			if (model == null || model == mc.getModelManager().getMissingModel()) {
				continue;
			}
			pose.pushPose();
			this.getParentModel().weaponPose(form, pose);
			if (form == ArsenalItem.BOW) {
				pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
			} else {
				pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
				if (form == ArsenalItem.TRIDENT) {
					pose.mulPose(Axis.YP.rotationDegrees(90.0F));
				}
			}
			float s = SCALE[form];
			pose.scale(s, s, s);
			float[] g = GRIP[form];
			// ItemRenderer centres the model (-0.5); shift it so the grip, not the centre, lands in the fist
			pose.translate((8.0F - g[0]) / 16.0F, (8.0F - g[1]) / 16.0F, (8.0F - g[2]) / 16.0F);
			mc.getItemRenderer().render(this.stacks[form], ItemDisplayContext.NONE, false, pose, buffers, light,
				OverlayTexture.NO_OVERLAY, model);
			pose.popPose();
		}
	}
}
