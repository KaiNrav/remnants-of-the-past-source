package dev.remnants.client.crypt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.remnants.RemnantsMod;
import dev.remnants.entity.GraveMimicEntity;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import static dev.remnants.entity.GraveMimicEntity.ANIM_FLINCH;

/**
 * The Grave Mimic. Asleep it is drawn with the Ossuary Urn's own block models (body + lid, cut from the same model, so
 * it is the urn to the texel - unrotated, like a placed block); its only tells are the lid lifting a texel every
 * 5..15 s and a shudder when a Hand Bell rings. Awake it is {@link GraveMimicModel}: the urn up on bone legs, a jaw
 * over rings of teeth, soul eyes glowing inside.
 */
public class GraveMimicRenderer extends MobRenderer<GraveMimicEntity, GraveMimicModel> {
	public static final ResourceLocation BODY = RemnantsMod.id("block/ossuary_urn_body");
	public static final ResourceLocation LID = RemnantsMod.id("block/ossuary_urn_lid");
	public static final ResourceLocation TEXTURE = RemnantsMod.id("textures/entity/grave_mimic.png");
	public static final ResourceLocation GLOW = RemnantsMod.id("textures/entity/grave_mimic_glow.png");

	public GraveMimicRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new GraveMimicModel(ctx.bakeLayer(GraveMimicModel.LAYER)), 0.45F);
		this.addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return RenderType.eyes(GLOW);
			}
		});
	}

	private static BakedModel model(ResourceLocation id) {
		return ((FabricBakedModelManager) Minecraft.getInstance().getModelManager()).getModel(id);
	}

	@Override
	public void render(GraveMimicEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		if (!e.isDormant()) {
			super.render(e, yaw, partial, pose, buffers, light);
			return;
		}
		float age = e.tickCount + partial;
		float lift = 0.0F;
		int period = 100 + Math.floorMod(e.getId() * 53, 200);
		if (Math.floorMod((int) age + e.getId() * 37, period) < 10) {
			lift = 1.0F / 16.0F;   // it breathes
		}
		pose.pushPose();
		if (e.getAnim() == ANIM_FLINCH) {
			float t = age - e.clientAnimStart;
			pose.translate(Mth.sin(age * 3.1F) * 0.035F * Math.max(0.0F, 1.0F - t / 20.0F), 0.0F, 0.0F);
			lift = 1.0F / 16.0F;
		}
		pose.translate(-0.5F, 0.0F, -0.5F);
		int overlay = LivingEntityRenderer.getOverlayCoords(e, 0.0F);
		VertexConsumer vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
		var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
		renderer.renderModel(pose.last(), vc, null, model(BODY), 1.0F, 1.0F, 1.0F, light, overlay);
		pose.translate(0.0F, lift, 0.0F);
		renderer.renderModel(pose.last(), vc, null, model(LID), 1.0F, 1.0F, 1.0F, light, overlay);
		pose.popPose();
	}

	/** Art 2.0 hit feel: the first two hurt ticks flash white. */
	@Override
	protected float getWhiteOverlayProgress(GraveMimicEntity entity, float partial) {
		return dev.remnants.client.fx.CombatFx.flash(entity, partial);
	}

	@Override
	protected float getFlipDegrees(GraveMimicEntity entity) {
		return 0.0F;   // its own death: the legs give way, the lid drops
	}

	@Override
	public ResourceLocation getTextureLocation(GraveMimicEntity entity) {
		return TEXTURE;
	}
}
