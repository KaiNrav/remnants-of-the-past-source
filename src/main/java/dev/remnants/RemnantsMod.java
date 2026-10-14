package dev.remnants;

import dev.remnants.net.ResonancePayload;
import dev.remnants.registry.ModBlockEntities;
import dev.remnants.registry.ModComponents;
import dev.remnants.registry.ModRecipes;
import dev.remnants.registry.ModWorldgen;
import dev.remnants.item.SoulDowserItem;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithEnchantedBonusCondition;
import dev.remnants.registry.ModBlocks;
import dev.remnants.registry.ModEntities;
import dev.remnants.registry.ModItems;
import dev.remnants.registry.ModParticles;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemnantsMod implements ModInitializer {
	public static final String MOD_ID = "remnants_of_the_past";
	public static final Logger LOGGER = LoggerFactory.getLogger("Remnants");
	private static final ResourceLocation LATE = id("late");

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		RemnantsConfig.load();
		ModComponents.init();
		ModBlocks.init();
		ModBlockEntities.init();
		ModEntities.init();
		ModItems.init();
		ModParticles.init();
		dev.remnants.registry.ModSounds.init();
		ModRecipes.init();
		ModWorldgen.init();
		PayloadTypeRegistry.playS2C().register(ResonancePayload.TYPE, ResonancePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(dev.remnants.net.ChroniclePayload.TYPE, dev.remnants.net.ChroniclePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(dev.remnants.net.GhostFormPayload.TYPE, dev.remnants.net.GhostFormPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(dev.remnants.net.CombatCuePayload.TYPE, dev.remnants.net.CombatCuePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(dev.remnants.net.BossStylePayload.TYPE, dev.remnants.net.BossStylePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(dev.remnants.net.PoolSelectPayload.TYPE, dev.remnants.net.PoolSelectPayload.CODEC);
		// the mouse wheel over the Reflecting Basin (with the Ritual Hammer in hand) picks the enchantment
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(dev.remnants.net.PoolSelectPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(payload.pos())) < 64.0
				&& player.level().getBlockEntity(payload.pos()) instanceof dev.remnants.block.entity.ReflectingPoolBlockEntity pool) {
				pool.select(Integer.signum(payload.delta()));
			}
		});
		// 1.3: the Ritual Hammer works ahead of the blocks' own use (a vanilla anvil in a forge would open its menu)
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(dev.remnants.item.RitualHammerItem::onUseBlock);
		dev.remnants.world.Treasuries.register();
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(dev.remnants.item.GhostStep::allowDamage);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(dev.remnants.item.GhostStep::tick);
		// 1.4: the Warden's Gauntlet backstab (set up before the swing), the Hand Bell's grave lights
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register(dev.remnants.item.WardensGauntletItem::onAttack);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(dev.remnants.item.WardensGauntletItem::endTick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(dev.remnants.item.HandBellItem::tickPulses);
		initArchive();

		// Run after every other ALLOW_DEATH listener: if another mod cancels the death we never touch the inventory.
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
		ServerLivingEntityEvents.ALLOW_DEATH.register(LATE, (entity, source, amount) -> {
			if (entity instanceof ServerPlayer player) {
				try {
					// a totem in hand goes first (both skip it), then Last Breath, then the Mourner's Ghost Step
					if (SoulResonance.tryLastBreath(player, source) || dev.remnants.item.GhostStep.tryGhostStep(player, source)) {
						return false;
					}
					// 1.5: keep the last 10 s for the Memorial (unless a totem in hand is about to save them)
					if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
						|| !(player.getMainHandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)
							|| player.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING))) {
						LastRecord.capture(player, source);
					}
					DeathHandler.onPlayerDeath(player, source);
				} catch (Exception e) {
					// Never break dying: on any error vanilla drops whatever is still in the inventory.
					LOGGER.error("Remnant spawn failed for {}", player.getGameProfile().getName(), e);
				}
			}
			return true;
		});
		ServerPlayerEvents.AFTER_RESPAWN.register(DeathHandler::onRespawn);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			DeathHandler.onJoin(handler.getPlayer());
			dev.remnants.item.GhostStep.onJoin(handler.getPlayer());
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SoulDowserItem.forget(handler.getPlayer().getUUID());
			LastRecord.forget(handler.getPlayer().getUUID());
			AnchorNetwork.forget(handler.getPlayer().getUUID());
		});
		// Wither Skeletons carry a sliver of soul: the steady (if slow) Nether source of Soul Fragments.
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			if (source.isBuiltin() && key.equals(EntityType.WITHER_SKELETON.getDefaultLootTable())) {
				table.withPool(LootPool.lootPool()
					.when(LootItemKilledByPlayerCondition.killedByPlayer())
					.when(LootItemRandomChanceWithEnchantedBonusCondition.randomChanceAndLootingBoost(registries,
						(float) RemnantsConfig.get().witherSkeletonFragmentChance, 0.02F))
					.add(LootItem.lootTableItem(ModItems.SOUL_FRAGMENT)));
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> RemnantsCommand.register(dispatcher));
		// Static per-world state (game times, entities, levels) must not survive into the next world of the same JVM:
		// in singleplayer the old ServerLevel stayed reachable, and stale game times gave hours of Ghost Step or anchor cooldown.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> resetSessionState());
		LOGGER.info("Remnants of the Past loaded. The dead remember.");
	}

	private static void resetSessionState() {
		DeathHandler.reset();
		SoulAllies.reset();
		PathBranch.reset();
		LastRecord.reset();
		AnchorNetwork.reset();
		dev.remnants.item.GhostStep.reset();
		dev.remnants.item.SoulDowserItem.reset();
		dev.remnants.item.HandBellItem.reset();
		dev.remnants.item.SoulTetherItem.reset();
		dev.remnants.item.WardensGauntletItem.reset();
		dev.remnants.entity.ArchivistEntity.resetFinale();
	}

	/** 1.5 "Archive": branches, the anchor network, the last record, soul allies, the payloads that go with them. */
	private static void initArchive() {
		PayloadTypeRegistry.playS2C().register(dev.remnants.net.AnchorListPayload.TYPE, dev.remnants.net.AnchorListPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(dev.remnants.net.ReplayPayload.TYPE, dev.remnants.net.ReplayPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(dev.remnants.net.AnchorJumpPayload.TYPE, dev.remnants.net.AnchorJumpPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(dev.remnants.net.BranchPayload.TYPE, dev.remnants.net.BranchPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(dev.remnants.net.AnchorJumpPayload.TYPE,
			(payload, context) -> AnchorNetwork.request(context.player(), payload.from(), payload.to()));
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(dev.remnants.net.BranchPayload.TYPE,
			(payload, context) -> PathBranch.choose(context.player(), PathBranch.Branch.of(payload.branch())));
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register(PathBranch::onAttack);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(PathBranch::afterDamage);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SoulAllies::allowDamage);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register(SoulAllies::onLoad);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			PathBranch.endTick(server);
			SoulAllies.tick(server);
			AnchorNetwork.tick(server);
			LastRecord.tick(server);
			dev.remnants.entity.ArchivistEntity.finaleTick(server);
		});
	}
}
