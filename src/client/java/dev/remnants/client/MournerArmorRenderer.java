package dev.remnants.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.remnants.RemnantsMod;
import dev.remnants.registry.ModItems;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the Mourner's set with its 3D model (MournerArmorModel) instead of the flat vanilla armour layers, on every
 * humanoid armour layer (players, Remnants, armour stands): the wearer's pose, one slot's pieces, the cloth swung by
 * MournerClothPhysics, then the soul accents (eyes, gems, sockets, the ring and its shards) again in an emissive pass that glows in the
 * dark and pulses slowly.
 */
public final class MournerArmorRenderer implements ArmorRenderer {
	private static final ResourceLocation TEXTURE = RemnantsMod.id("textures/models/armor/mourner_3d.png");
	private static final ResourceLocation GLOW = RemnantsMod.id("textures/models/armor/mourner_3d_glow.png");
	private MournerArmorModel model;

	public static void register() {
		ArmorRenderer.register(new MournerArmorRenderer(),
			ModItems.MOURNER_HOOD, ModItems.MOURNER_ROBE, ModItems.MOURNER_LEGGINGS, ModItems.MOURNER_BOOTS);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, ItemStack stack, LivingEntity entity, EquipmentSlot slot,
		int light, HumanoidModel<LivingEntity> contextModel) {
		if (this.model == null) {
			this.model = new MournerArmorModel(Minecraft.getInstance().getEntityModels().bakeLayer(MournerArmorModel.LAYER));
		}
		contextModel.copyPropertiesTo(this.model);
		MournerClothPhysics cloth = MournerClothPhysics.of(entity);
		cloth.update(entity, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true), contextModel);
		cloth.apply(this.model, contextModel);
		this.model.show(slot);
		ArmorRenderer.renderPart(pose, buffers, light, stack, this.model, TEXTURE);
		int g = Math.round(255.0F * cloth.glow());
		this.model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT,
			OverlayTexture.NO_OVERLAY, 0xFF000000 | (g << 16) | (g << 8) | g);
	}
}
