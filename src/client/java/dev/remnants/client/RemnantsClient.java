package dev.remnants.client;

import dev.remnants.RemnantsMod;
import dev.remnants.client.codex.ClientResonance;
import dev.remnants.client.codex.CodexScreen;
import dev.remnants.item.SoulCodexItem;
import dev.remnants.net.ResonancePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import dev.remnants.entity.PhantomArrowEntity;
import dev.remnants.item.ArsenalItem;
import dev.remnants.item.ChakramInlay;
import dev.remnants.item.EpitaphItem;
import dev.remnants.registry.ModComponents;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import dev.remnants.registry.ModBlockEntities;
import dev.remnants.registry.ModBlocks;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import dev.remnants.registry.ModEntities;
import dev.remnants.registry.ModItems;
import dev.remnants.registry.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import dev.remnants.client.anim.BoneAnimations;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public class RemnantsClient implements ClientModInitializer {
	private static final int[] DOWSER_FRAMES = {0, 1, 0, 2};
	private static final ResourceLocation PHANTOM_ARROW_TEXTURE = RemnantsMod.id("textures/entity/phantom_arrow.png");

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.REMNANT, RemnantRenderer::new);
		EntityRendererRegistry.register(ModEntities.FORGOTTEN_REMNANT, RemnantRenderer::new);
		EntityRendererRegistry.register(ModEntities.PHANTOM_ARROW, PhantomArrowRenderer::new);
		EntityRendererRegistry.register(ModEntities.SPECTRAL_WEAPON, SpectralWeaponRenderer::new);
		EntityRendererRegistry.register(ModEntities.BONE_CHAKRAM, BoneChakramRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(ForgottenVariantModel.LAYER, ForgottenVariantModel::createLayer);
		EntityModelLayerRegistry.registerModelLayer(RemnantCrownModel.LAYER, RemnantCrownModel::createLayer);
		EntityModelLayerRegistry.registerModelLayer(MournerArmorModel.LAYER, MournerArmorModel::createLayer);
		MournerArmorRenderer.register();
		ParticleFactoryRegistry.getInstance().register(ModParticles.REMNANT_SOUL, RemnantSoulParticle.Provider::new);
		EntityRendererRegistry.register(ModEntities.SOUL_KING, SoulKingRenderer::new);
		EntityRendererRegistry.register(ModEntities.SOUL_CRYSTAL, SoulCrystalRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(SoulKingModel.LAYER, SoulKingModel::createLayer);
		BoneAnimations.register();
		dev.remnants.client.fx.CombatFx.register();
		dev.remnants.client.fx.MeshFx.register();
		dev.remnants.client.fx.GateLift.register();
		dev.remnants.client.bossbar.BossBars.register();
		dev.remnants.client.bossbar.BossMusic.register();
		ClientPlayNetworking.registerGlobalReceiver(ResonancePayload.TYPE, (payload, context) -> ClientResonance.set(payload));
		ClientPlayNetworking.registerGlobalReceiver(dev.remnants.net.ChroniclePayload.TYPE,
			(payload, context) -> dev.remnants.client.codex.ClientChronicle.set(payload));
		SoulCodexItem.openScreen = CodexScreen::open;
		TwoFaceItemRenderer.register();
		BlockEntityRenderers.register(ModBlockEntities.SPIRIT_ALTAR, SpiritAltarRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.BURIAL_CASKET, BurialCasketRenderer::new);
		// casket hinges / gravestone moss are cut-out texels too
		BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), ModBlocks.BURIAL_CASKET, ModBlocks.WEATHERED_GRAVESTONE);
		TooltipComponentCallback.EVENT.register(data -> data instanceof EpitaphItem.Tablet tablet ? new EpitaphTooltipComponent(tablet.data()) : null);
		// candle flames, the altar cloth's torn hem, the pedestal crown's teeth and the brazier fire are cut-out texels
		BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), ModBlocks.SOUL_BRAZIER, ModBlocks.SOUL_PEDESTAL,
			ModBlocks.SPIRIT_ALTAR, ModBlocks.SOUL_FORGE);
		registerCraft();
		registerCrypts();
		dev.remnants.client.archive.ArchiveClient.register();

		// Spectral Compass: the vanilla compass needle, aimed by the lodestone-tracker component the server keeps fresh.
		ItemProperties.register(ModItems.SPECTRAL_COMPASS, ResourceLocation.withDefaultNamespace("angle"),
			new CompassItemPropertyFunction((level, stack, entity) -> {
				LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
				return tracker == null ? null : tracker.target().orElse(null);
			}));
		// Soul Dowser: the rod trembles (left / right, faster when near) and points down over a grave.
		ItemProperties.register(ModItems.SOUL_DOWSER, RemnantsMod.id("signal"), (stack, level, entity, seed) -> {
			Integer signal = stack.get(ModComponents.DOWSE_SIGNAL);
			if (signal == null || signal == 0) {
				return 0.0F;
			}
			if (signal >= 3) {
				return 0.75F;
			}
			net.minecraft.client.multiplayer.ClientLevel world = net.minecraft.client.Minecraft.getInstance().level;
			long t = world == null ? 0 : world.getGameTime();
			int frame = signal == 2 ? (int) ((t / 3) % 2) + 1 : DOWSER_FRAMES[(int) ((t / 6) % 4)];
			return frame * 0.25F;
		});
		// Bone Chakram: the inlaid trophy shows in the hub
		ItemProperties.register(ModItems.BONE_CHAKRAM, RemnantsMod.id("inlay"), (stack, level, entity, seed) -> {
			ChakramInlay inlay = stack.get(ModComponents.CHAKRAM_INLAY);
			return inlay == null ? 0.0F : (inlay.ordinal() + 1) * 0.25F;
		});
		// Arsenal: one 3D model per form.
		ItemProperties.register(ModItems.SOUL_KING_ARSENAL, RemnantsMod.id("form"), (stack, level, entity, seed) -> ArsenalItem.mode(stack) / 4.0F);
		ItemProperties.register(ModItems.SOUL_KING_ARSENAL, ResourceLocation.withDefaultNamespace("pulling"),
			(stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
	}

	/** 1.3 "Craft": the multiblocks' renderers and extra models, Ghost Step, the pool's mouse wheel, forge-quality tooltips. */
	private static void registerCraft() {
		BlockEntityRenderers.register(ModBlockEntities.REFLECTING_POOL, dev.remnants.client.craft.ReflectingPoolRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.SOUL_CRUCIBLE, dev.remnants.client.craft.SoulCrucibleRenderer::new);
		net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(ctx -> ctx.addModels(
			dev.remnants.client.craft.SoulCrucibleRenderer.POT, dev.remnants.client.craft.SoulCrucibleRenderer.POT_FORMED,
			dev.remnants.client.craft.SoulCrucibleRenderer.BELLOWS_TOP));
		// fire planes in the hearth, cut-out texels everywhere else
		BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), ModBlocks.REFLECTING_BASIN, ModBlocks.SOUL_CRUCIBLE, ModBlocks.SOUL_HEARTH,
			ModBlocks.SOUL_BELLOWS, ModBlocks.SOUL_TROUGH, ModBlocks.SOUL_CHIMNEY);
		ClientPlayNetworking.registerGlobalReceiver(dev.remnants.net.GhostFormPayload.TYPE,
			(payload, context) -> dev.remnants.client.craft.GhostFormLayer.start(payload.entityId(), payload.ticks()));
		net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer player) {
				helper.register(new dev.remnants.client.craft.GhostFormLayer(player));
			}
		});
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(dev.remnants.client.craft.PoolWheel::onStartTick);
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(dev.remnants.client.craft.GhostFormLayer::tick);
		dev.remnants.item.MournerArmorItem.clientCooldown = item -> {
			var player = net.minecraft.client.Minecraft.getInstance().player;
			return player == null ? 0 : Math.round(player.getCooldowns().getCooldownPercent(item, 0.0F) * dev.remnants.RemnantsConfig.get().ghostStepCooldown);
		};
		net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			dev.remnants.item.ForgeQuality quality = stack.get(ModComponents.FORGE_QUALITY);
			if (quality == null || lines.isEmpty()) {
				return;
			}
			int at = Math.min(1, lines.size());
			lines.add(at, net.minecraft.network.chat.Component.translatable("item.remnants_of_the_past.forge_quality", quality.percent())
				.withStyle(quality.percent() >= 15 ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.AQUA));
			if (!quality.master().isEmpty()) {
				lines.add(at + 1, net.minecraft.network.chat.Component.translatable("item.remnants_of_the_past.forge_quality.master", quality.master())
					.withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.ITALIC));
			}
		});
	}

	/** 1.4 "Crypts": the Warden, the Keener, the Grave Mimic, the Soul Tether, the Belfry and its bell. */
	private static void registerCrypts() {
		EntityModelLayerRegistry.registerModelLayer(dev.remnants.client.crypt.CryptWardenModel.LAYER, dev.remnants.client.crypt.CryptWardenModel::createLayer);
		EntityModelLayerRegistry.registerModelLayer(dev.remnants.client.crypt.KeenerModel.LAYER, dev.remnants.client.crypt.KeenerModel::createLayer);
		EntityModelLayerRegistry.registerModelLayer(dev.remnants.client.crypt.GraveMimicModel.LAYER, dev.remnants.client.crypt.GraveMimicModel::createLayer);
		EntityRendererRegistry.register(ModEntities.CRYPT_WARDEN, dev.remnants.client.crypt.CryptWardenRenderer::new);
		EntityRendererRegistry.register(ModEntities.KEENER, dev.remnants.client.crypt.KeenerRenderer::new);
		EntityRendererRegistry.register(ModEntities.GRAVE_MIMIC, dev.remnants.client.crypt.GraveMimicRenderer::new);
		EntityRendererRegistry.register(ModEntities.SOUL_TETHER, dev.remnants.client.crypt.SoulTetherRenderer::new);
		EntityRendererRegistry.register(ModEntities.MIMIC_SPIT, net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.BELFRY, dev.remnants.client.crypt.BelfryRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.BELL_OF_REST, dev.remnants.client.crypt.BellOfRestRenderer::new);
		net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(ctx -> ctx.addModels(
			dev.remnants.client.crypt.BellOfRestRenderer.BODY, dev.remnants.client.crypt.GraveMimicRenderer.BODY,
			dev.remnants.client.crypt.GraveMimicRenderer.LID));
		// the gate's bars, the urn's knob and the bells are cut-out texels
		BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), ModBlocks.OSSUARY_GATE, ModBlocks.OSSUARY_URN, ModBlocks.BELL_OF_REST,
			ModBlocks.CRACKED_BELL_OF_REST, ModBlocks.REST_STONE);
	}

	/** Vanilla arrow geometry, drawn translucent and full-bright so the phantom arrow glows like a soul flame. */
	static class PhantomArrowRenderer extends ArrowRenderer<PhantomArrowEntity> {
		PhantomArrowRenderer(EntityRendererProvider.Context ctx) {
			super(ctx);
		}

		@Override
		public ResourceLocation getTextureLocation(PhantomArrowEntity entity) {
			return PHANTOM_ARROW_TEXTURE;
		}

		@Override
		protected int getBlockLightLevel(PhantomArrowEntity entity, BlockPos pos) {
			return 15;
		}

		@Override
		public void render(PhantomArrowEntity arrow, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
			if (arrow.tickCount < 5 && this.entityRenderDispatcher.camera.getPosition().distanceToSqr(arrow.position()) < 4.0) {
				return; // just spawned at the shooter's eyes: would fill the whole screen for a frame
			}
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, arrow.yRotO, arrow.getYRot()) - 90.0F));
			pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, arrow.xRotO, arrow.getXRot())));
			float shake = arrow.shakeTime - partialTick;
			if (shake > 0.0F) {
				pose.mulPose(Axis.ZP.rotationDegrees(-Mth.sin(shake * 3.0F) * shake));
			}
			pose.mulPose(Axis.XP.rotationDegrees(45.0F));
			pose.scale(0.05625F, 0.05625F, 0.05625F);
			pose.translate(-4.0F, 0.0F, 0.0F);
			VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(PHANTOM_ARROW_TEXTURE));
			int full = LightTexture.FULL_BRIGHT;
			PoseStack.Pose last = pose.last();
			this.vertex(last, vc, -7, -2, -2, 0.0F, 0.15625F, -1, 0, 0, full);
			this.vertex(last, vc, -7, -2, 2, 0.15625F, 0.15625F, -1, 0, 0, full);
			this.vertex(last, vc, -7, 2, 2, 0.15625F, 0.3125F, -1, 0, 0, full);
			this.vertex(last, vc, -7, 2, -2, 0.0F, 0.3125F, -1, 0, 0, full);
			this.vertex(last, vc, -7, 2, -2, 0.0F, 0.15625F, 1, 0, 0, full);
			this.vertex(last, vc, -7, 2, 2, 0.15625F, 0.15625F, 1, 0, 0, full);
			this.vertex(last, vc, -7, -2, 2, 0.15625F, 0.3125F, 1, 0, 0, full);
			this.vertex(last, vc, -7, -2, -2, 0.0F, 0.3125F, 1, 0, 0, full);
			for (int i = 0; i < 4; i++) {
				pose.mulPose(Axis.XP.rotationDegrees(90.0F));
				this.vertex(last, vc, -8, -2, 0, 0.0F, 0.0F, 0, 1, 0, full);
				this.vertex(last, vc, 8, -2, 0, 0.5F, 0.0F, 0, 1, 0, full);
				this.vertex(last, vc, 8, 2, 0, 0.5F, 0.15625F, 0, 1, 0, full);
				this.vertex(last, vc, -8, 2, 0, 0.0F, 0.15625F, 0, 1, 0, full);
			}
			pose.popPose();
		}
	}
}
