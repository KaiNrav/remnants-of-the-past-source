package dev.remnants.client.archive;

import dev.remnants.RemnantsMod;
import dev.remnants.SoulResonance;
import dev.remnants.client.codex.AnchorScreen;
import dev.remnants.client.codex.ClientResonance;
import dev.remnants.item.ShroudGliderItem;
import dev.remnants.item.SoulLanternItem;
import dev.remnants.net.AnchorListPayload;
import dev.remnants.net.ReplayPayload;
import dev.remnants.registry.ModBlockEntities;
import dev.remnants.registry.ModBlocks;
import dev.remnants.registry.ModComponents;
import dev.remnants.registry.ModEntities;
import dev.remnants.registry.ModItems;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;

/** 1.5 "Archive" on the client: the Archivist's fight, the Memorial, the Astrolabe, the glider, lantern, decoy and anchors. */
public final class ArchiveClient {
	public static void register() {
		EntityModelLayerRegistry.registerModelLayer(ArchivistModel.LAYER, ArchivistModel::createLayer);
		EntityModelLayerRegistry.registerModelLayer(ArchivePageModel.LAYER, ArchivePageModel::createLayer);
		EntityModelLayerRegistry.registerModelLayer(GliderLayer.LAYER, GliderLayer::createLayer);
		EntityModelLayerRegistry.registerModelLayer(CarrionCrowModel.LAYER, CarrionCrowModel::createLayer);
		EntityRendererRegistry.register(ModEntities.CARRION_CROW, CarrionCrowRenderer::new);
		EntityRendererRegistry.register(ModEntities.ARCHIVIST, ArchivistRenderer::new);
		EntityRendererRegistry.register(ModEntities.ARCHIVE_PAGE, ArchiveEntityRenderers.Page::new);
		EntityRendererRegistry.register(ModEntities.FLYING_TOME, ArchiveEntityRenderers.Tome::new);
		EntityRendererRegistry.register(ModEntities.ARCHIVE_RUNE, ArchiveEntityRenderers.Rune::new);
		EntityRendererRegistry.register(ModEntities.ARCHIVE_STAR, ArchiveEntityRenderers.Star::new);
		EntityRendererRegistry.register(ModEntities.ARCHIVE_EYE, ArchiveEntityRenderers.Eye::new);
		EntityRendererRegistry.register(ModEntities.DECOY_ECHO, DecoyEchoRenderer::new);
		EntityRendererRegistry.register(ModEntities.THROWN_DECOY, ThrownItemRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.EPITAPH_NICHE, ArchiveBlockRenderers.Niche::new);
		BlockEntityRenderers.register(ModBlockEntities.MEMORIAL, ArchiveBlockRenderers.Memorial::new);
		BlockEntityRenderers.register(ModBlockEntities.ASTROLABE, ArchiveBlockRenderers.Astrolabe::new);
		BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), ModBlocks.MEMORIAL_PROJECTOR, ModBlocks.SOUL_ASTROLABE, ModBlocks.FOCUS_CRYSTAL);

		ClientPlayNetworking.registerGlobalReceiver(AnchorListPayload.TYPE, (payload, context) -> Minecraft.getInstance().setScreen(new AnchorScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(ReplayPayload.TYPE, (payload, context) -> MemorialReplays.start(payload));
		ClientTickEvents.END_CLIENT_TICK.register(MemorialReplays::tick);

		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof PlayerRenderer player) {
				helper.register(new GliderLayer(player, context.getModelSet().bakeLayer(GliderLayer.LAYER)));
			}
		});
		ShroudGliderItem.CLIENT_BOOST = () -> {
			var r = ClientResonance.get();
			return "wanderer".equals(r.branch()) && SoulResonance.rankOf(r.points()) >= r.branchRank() ? dev.remnants.PathBranch.WANDERER_GLIDE : 1.0;
		};

		// the Soul Lantern: lit when it holds something, its flame in the colour of the prisoner's spawn egg
		ItemProperties.register(ModItems.SOUL_LANTERN_TRAP, RemnantsMod.id("filled"), (stack, level, entity, seed) -> SoulLanternItem.holds(stack) ? 1.0F : 0.0F);
		ColorProviderRegistry.ITEM.register((stack, tint) -> {
			if (tint != 1) {
				return -1;
			}
			EntityType<?> type = SoulLanternItem.heldType(stack);
			SpawnEggItem egg = type == null ? null : SpawnEggItem.byId(type);
			return egg == null ? 0xFF7FE8E2 : 0xFF000000 | egg.getColor(1);
		}, ModItems.SOUL_LANTERN_TRAP);

		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (stack.has(ModComponents.REMEMBERED) && !lines.isEmpty()) {
				lines.add(Math.min(1, lines.size()), Component.translatable("item.remnants_of_the_past.remembered").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		});
	}

	private ArchiveClient() {
	}
}
