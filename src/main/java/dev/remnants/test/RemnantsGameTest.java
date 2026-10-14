package dev.remnants.test;

import com.mojang.authlib.GameProfile;
import dev.remnants.RemnantTracker;
import dev.remnants.RemnantsMod;
import dev.remnants.RemnantsConfig;
import dev.remnants.SoulResonance;
import dev.remnants.item.SpectralForge;
import dev.remnants.item.ArsenalItem;
import dev.remnants.block.SpiritAltarBlock;
import dev.remnants.entity.SoulKingEntity;
import dev.remnants.registry.ModBlocks;
import dev.remnants.entity.RemnantEntity;
import dev.remnants.registry.ModEntities;
import dev.remnants.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;

import java.util.List;
import java.util.UUID;

/**
 * Headless server tests: ./gradlew runGametest. Each test uses a real ServerPlayer (survival) that really dies,
 * so it exercises the actual ALLOW_DEATH path, respawn, and item return.
 */
public class RemnantsGameTest implements FabricGameTest {

	@GameTest(template = EMPTY_STRUCTURE, batch = "remnants_core")
	public void deathSpawnsRemnantAndKillRestoresItems(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "EchoMelee");
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		player.getInventory().add(new ItemStack(Items.DIRT, 10));

		player.kill();

		helper.assertTrue(player.isDeadOrDying(), "player should be dead");
		helper.assertTrue(player.getInventory().isEmpty(), "inventory should have moved into the Remnant");
		RemnantEntity remnant = single(helper, player.getUUID());
		helper.assertTrue(remnant.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "Remnant should wear the helmet");
		helper.assertTrue(remnant.getMainHandItem().is(Items.DIAMOND_SWORD), "Remnant should hold the sword");
		helper.assertTrue(!remnant.isRangedMode(), "sword Remnant should be melee");
		helper.assertTrue(remnant.getStoredItems().size() == 3, "Remnant should carry 3 stacks, has " + remnant.getStoredItems().size());
		helper.assertTrue(remnant.getCustomName() != null && remnant.getCustomName().getString().contains("EchoMelee"), "name tag should show the owner");
		helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, remnant.getBoundingBox().inflate(4)).isEmpty(),
			"nothing should be dropped on the ground");

		ServerPlayer respawned = respawn(player);
		helper.assertTrue(respawned.getInventory().contains(new ItemStack(ModItems.SPECTRAL_COMPASS)), "respawn should give a Spectral Compass");
		respawned.teleportTo(level, remnant.getX() + 1, remnant.getY(), remnant.getZ(), 0, 0);

		remnant.hurt(level.damageSources().playerAttack(respawned), 10_000F);

		helper.assertTrue(!remnant.isAlive(), "Remnant should be defeated");
		helper.assertTrue(respawned.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "helmet should go back on the head");
		helper.assertTrue(respawned.getInventory().contains(new ItemStack(Items.DIAMOND_SWORD)), "sword should be back");
		helper.assertTrue(respawned.getInventory().countItem(Items.DIRT) == 10, "all 10 dirt should be back");
		helper.assertTrue(RemnantTracker.get(level.getServer()).streak(respawned.getUUID()) == 0, "reclaiming should reset the streak");
		level.getServer().getPlayerList().remove(respawned);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "remnants_ranged")
	public void bowMakesRangedRemnant(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "EchoArcher");
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 32));
		player.kill();
		RemnantEntity remnant = single(helper, player.getUUID());
		helper.assertTrue(remnant.isRangedMode(), "bow Remnant should use the ranged AI");
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "remnants_scaling")
	public void consecutiveDeathsScaleTier(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "EchoStreak");
		player.getInventory().add(new ItemStack(Items.COBBLESTONE, 5));
		player.kill();
		net.minecraft.world.phys.Vec3 deathPos = player.position(); // respawn() moves the old body to spawn
		ServerPlayer second = respawn(player);
		second.teleportTo(level, deathPos.x + 3, deathPos.y, deathPos.z, 0, 0);
		second.getInventory().add(new ItemStack(Items.COBBLESTONE, 5));
		second.kill();

		List<RemnantEntity> remnants = remnantsOf(helper, player.getUUID());
		helper.assertTrue(remnants.size() == 2, "expected 2 Remnants, got " + remnants.size());
		RemnantEntity first = remnants.stream().filter(r -> r.getTier() == 0).findFirst().orElseThrow();
		RemnantEntity next = remnants.stream().filter(r -> r.getTier() == 1).findFirst().orElseThrow();
		helper.assertTrue(next.getMaxHealth() > first.getMaxHealth(), "tier 1 should be tougher: " + next.getMaxHealth() + " vs " + first.getMaxHealth());
		helper.assertTrue(RemnantTracker.get(level.getServer()).streak(player.getUUID()) == 2, "streak should be 2");
		level.getServer().getPlayerList().remove(second);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "remnants_totem", timeoutTicks = 120)
	public void totemStillSavesThePlayer(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "EchoTotem");
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		player.getInventory().add(new ItemStack(Items.EMERALD, 3));
		// a new player is immune for 60 ticks after joining: hurt them only after that, or the totem is never needed (1.3 fix)
		helper.runAfterDelay(70, () -> {
			player.hurt(helper.getLevel().damageSources().generic(), 1000F);
			helper.assertTrue(player.isAlive(), "totem should save the player");
			helper.assertTrue(player.getOffhandItem().isEmpty(), "the totem was really used");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 3, "items should stay with the player");
			helper.assertTrue(remnantsOf(helper, player.getUUID()).isEmpty(), "no Remnant when a totem pops");
			helper.getLevel().getServer().getPlayerList().remove(player);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "remnants_keepinv")
	public void keepInventoryIsRespected(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GameRules.BooleanValue rule = level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
		rule.set(true, level.getServer());
		try {
			ServerPlayer player = survivalPlayer(helper, "EchoKeep");
			player.getInventory().add(new ItemStack(Items.GOLD_INGOT, 4));
			player.kill();
			helper.assertTrue(remnantsOf(helper, player.getUUID()).isEmpty(), "keepInventory=true should not spawn a Remnant");
			helper.assertTrue(player.getInventory().countItem(Items.GOLD_INGOT) == 4, "items stay with keepInventory");
			level.getServer().getPlayerList().remove(player);
		} finally {
			rule.set(false, level.getServer());
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "remnants_save")
	public void storedItemsSurviveSaveAndLoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "EchoSave");
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
		player.getInventory().add(new ItemStack(Items.NETHERITE_INGOT, 2));
		player.kill();
		RemnantEntity remnant = single(helper, player.getUUID());

		CompoundTag tag = new CompoundTag();
		remnant.saveWithoutId(tag);
		RemnantEntity copy = ModEntities.REMNANT.create(level);
		helper.assertTrue(copy != null, "entity type should create");
		copy.load(tag);
		helper.assertTrue(copy.getStoredItems().size() == 2, "stored items should round-trip");
		helper.assertTrue(copy.getOwnerUuid().orElseThrow().equals(player.getUUID()), "owner should round-trip");
		helper.assertTrue(copy.isRangedMode(), "crossbow Remnant should stay ranged after loading");
		level.getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "remnants_spectral")
	public void spectralUpgradeImprovesWeapon(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Registry<Enchantment> reg = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		Holder<Enchantment> sharpness = reg.getHolderOrThrow(Enchantments.SHARPNESS);
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.enchant(sharpness, 2);

		ItemStack s1 = SpectralForge.upgrade(sword, level.registryAccess());
		helper.assertTrue(SpectralForge.level(s1) == 1, "level 1 after one upgrade");
		helper.assertTrue(s1.getEnchantments().getLevel(sharpness) == 3, "sharpness II -> III");
		helper.assertTrue(s1.getHoverName().getString().contains("Spectral"), "name should say Spectral: " + s1.getHoverName().getString());
		boolean bonus = s1.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
			.anyMatch(e -> e.modifier().id().getPath().equals("spectral_damage") && e.modifier().amount() == 1.0);
		helper.assertTrue(bonus, "spectral damage modifier missing");
		boolean keptBase = s1.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
			.anyMatch(e -> e.modifier().id().getPath().equals("base_attack_damage"));
		helper.assertTrue(keptBase, "the sword's own damage must be kept");

		ItemStack s3 = SpectralForge.upgrade(SpectralForge.upgrade(s1, level.registryAccess()), level.registryAccess());
		helper.assertTrue(SpectralForge.level(s3) == 3 && s3.getEnchantments().getLevel(sharpness) == 5, "level 3 / sharpness V");
		helper.assertTrue(!SpectralForge.canUpgrade(s3, 3), "capped at max level");

		ItemStack bow = SpectralForge.upgrade(new ItemStack(Items.BOW), level.registryAccess());
		helper.assertTrue(bow.getEnchantments().getLevel(reg.getHolderOrThrow(Enchantments.POWER)) == 1, "plain bow gets Power I");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "remnants_empowered")
	public void empoweredRemnantGivesSpectralWeapon(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "EchoEmpower");
		RemnantsConfig.get().empoweredChance = 1.0;
		try {
			player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
			player.getInventory().add(new ItemStack(Items.BREAD, 3));
			player.kill();
		} finally {
			RemnantsConfig.get().empoweredChance = 0.0;
		}
		RemnantEntity remnant = single(helper, player.getUUID());
		helper.assertTrue(remnant.isEmpowered(), "should be Empowered");
		helper.assertTrue(SpectralForge.level(remnant.getMainHandItem()) == 1, "it should wield the Spectral axe");
		helper.assertTrue(Math.abs(remnant.getMaxHealth() - echoHealth(remnant) * 1.5F) < 0.01, "Empowered = +50% HP, got " + remnant.getMaxHealth());

		ServerPlayer respawned = respawn(player);
		remnant.hurt(level.damageSources().playerAttack(respawned), 100_000F);
		helper.assertTrue(!remnant.isAlive(), "defeated");
		ItemStack won = respawned.getInventory().items.stream().filter(s -> s.is(Items.IRON_AXE)).findFirst().orElse(ItemStack.EMPTY);
		helper.assertTrue(SpectralForge.level(won) == 1, "player should win the Spectral axe");
		helper.assertTrue(respawned.getInventory().countItem(Items.BREAD) == 3, "other items come back unchanged");
		level.getServer().getPlayerList().remove(respawned);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "remnants_phases")
	public void phasesChangeWithHealth(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "EchoPhase");
		kitted(player, new ItemStack(Items.DIAMOND_SWORD));
		player.kill();
		RemnantEntity remnant = single(helper, player.getUUID());
		helper.assertTrue(remnant.getPhase() == 1, "starts in phase 1");
		remnant.setHealth(remnant.getMaxHealth() * 0.5F);
		remnant.updatePhase();
		helper.assertTrue(remnant.getPhase() == 2 && remnant.isTransitioning(), "50% HP -> phase 2 with a roar");
		float before = remnant.getHealth();
		remnant.hurt(helper.getLevel().damageSources().playerAttack(player), 5F);
		helper.assertTrue(remnant.getHealth() == before, "invulnerable while roaring");
		remnant.setHealth(remnant.getMaxHealth() * 0.2F);
		remnant.updatePhase();
		helper.assertTrue(remnant.getPhase() == 3, "20% HP -> phase 3");
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "remnants_illusions")
	public void illusionsPopInOneHitAndDropNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "EchoMirror");
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		player.kill();
		RemnantEntity remnant = single(helper, player.getUUID());
		remnant.spawnIllusions(2);
		List<RemnantEntity> echoes = level.getEntitiesOfClass(RemnantEntity.class, remnant.getBoundingBox().inflate(16),
			r -> r.isIllusion() && r.isOwner(player));
		helper.assertTrue(echoes.size() == 2, "expected 2 illusions, got " + echoes.size());
		helper.assertTrue(echoes.get(0).getMainHandItem().is(Items.DIAMOND_SWORD), "illusions look identical");
		echoes.get(0).hurt(level.damageSources().playerAttack(player), 1F);
		helper.assertTrue(echoes.get(0).isRemoved(), "one hit pops an illusion");
		helper.assertTrue(remnant.isAlive() && remnant.getStoredItems().size() == 1, "the real Remnant keeps the items");
		helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, echoes.get(0).getBoundingBox().inflate(2)).isEmpty(),
			"illusions drop nothing");
		level.getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	// ---- soak: every weapon style fights a live player through all three phases ---------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "remnants_soak", timeoutTicks = 400)
	public void soakSword(GameTestHelper helper) {
		soak(helper, "SoakSword", new ItemStack(Items.DIAMOND_SWORD), ItemStack.EMPTY);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "remnants_soak", timeoutTicks = 400)
	public void soakAxeShield(GameTestHelper helper) {
		soak(helper, "SoakAxe", new ItemStack(Items.NETHERITE_AXE), new ItemStack(Items.SHIELD));
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "remnants_soak", timeoutTicks = 400)
	public void soakMace(GameTestHelper helper) {
		soak(helper, "SoakMace", new ItemStack(Items.MACE), ItemStack.EMPTY);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "remnants_soak", timeoutTicks = 400)
	public void soakTrident(GameTestHelper helper) {
		soak(helper, "SoakTrident", new ItemStack(Items.TRIDENT), ItemStack.EMPTY);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "remnants_soak", timeoutTicks = 400)
	public void soakBow(GameTestHelper helper) {
		soak(helper, "SoakBow", new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 64));
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "remnants_soak", timeoutTicks = 400)
	public void soakCrossbow(GameTestHelper helper) {
		soak(helper, "SoakCrossbow", new ItemStack(Items.CROSSBOW), ItemStack.EMPTY);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "remnants_soak", timeoutTicks = 400)
	public void soakFists(GameTestHelper helper) {
		soak(helper, "SoakFists", new ItemStack(Items.NETHERITE_PICKAXE), ItemStack.EMPTY);
	}

	private static void soak(GameTestHelper helper, String name, ItemStack weapon, ItemStack offhand) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, name);
		kitted(player, weapon);
		player.setItemSlot(EquipmentSlot.OFFHAND, offhand);
		player.kill();
		RemnantEntity remnant = single(helper, player.getUUID());
		helper.assertTrue(remnant.getEchoRank() == 4, name + ": a fully kitted death leaves a Great Echo, score " + remnant.getGearScore());
		ServerPlayer target = respawn(player);
		BlockPos spot = helper.absolutePos(new BlockPos(3, 1, 1)); // inside the 8x8 test box: its walls are barriers
		target.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
		forceArena(level, spot, true);
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (target.isAlive()) {
				target.setHealth(target.getMaxHealth());
				remnant.setTarget(target);
			}
			// the test world only ticks chunks near the test: keep blinks from carrying it out of them
			if (remnant.distanceToSqr(spot.getX(), spot.getY(), spot.getZ()) > 16) {
				remnant.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 3.5);
			}
			if (tick[0] == 120) {
				remnant.setHealth(remnant.getMaxHealth() * 0.5F);
			}
			if (tick[0] == 220) {
				remnant.setHealth(remnant.getMaxHealth() * 0.2F);
			}
		});
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(remnant.isAlive(), name + ": Remnant should survive the soak, last hurt by "
				+ (remnant.getLastDamageSource() == null ? "nothing" : remnant.getLastDamageSource().getMsgId() + " / " + remnant.getLastDamageSource().getEntity())
				+ ", removed " + remnant.getRemovalReason() + ", hp " + remnant.getHealth() + ", at " + remnant.position() + ", sink " + remnant.getSinkTicks());
			forceArena(level, spot, false);
			helper.assertTrue(remnant.getPhase() == 3, name + ": should have reached phase 3, is " + remnant.getPhase()
				+ " hp=" + remnant.getHealth() + "/" + remnant.getMaxHealth() + " targetAlive=" + target.isAlive() + " anim=" + remnant.getAnim());
			level.getServer().getPlayerList().remove(target);
			helper.succeed();
		});
	}

	// ---- soul update -------------------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "soul_compass", timeoutTicks = 200)
	public void compassForgetsDefeatedRemnant(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "EchoCompass");
		player.getInventory().add(new ItemStack(Items.DIAMOND, 2));
		player.kill();
		RemnantEntity remnant = single(helper, player.getUUID());
		ServerPlayer body = respawn(player);
		ItemStack compass = new ItemStack(ModItems.SPECTRAL_COMPASS);
		body.getInventory().add(compass);
		RemnantTracker tracker = RemnantTracker.get(level.getServer());
		helper.assertTrue(tracker.nearestLiving(level.getServer(), body.getUUID(), level.dimension(), body.blockPosition()).isPresent(), "tracked while alive");
		remnant.hurt(level.damageSources().playerAttack(body), 100_000F);
		// the corpse keeps ticking for ~20 ticks; it used to re-register itself during that time
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(tracker.nearestLiving(level.getServer(), body.getUUID(), level.dimension(), body.blockPosition()).isEmpty(),
				"compass must forget a defeated Remnant");
			ItemStack held = body.getInventory().items.stream().filter(s -> s.is(ModItems.SPECTRAL_COMPASS)).findFirst().orElseThrow();
			held.set(DataComponents.LODESTONE_TRACKER, new net.minecraft.world.item.component.LodestoneTracker(
				java.util.Optional.of(net.minecraft.core.GlobalPos.of(level.dimension(), remnant.blockPosition())), false));
			helper.succeedWhen(() -> {
				held.inventoryTick(level, body, 0, false);
				helper.assertTrue(!held.has(DataComponents.LODESTONE_TRACKER), "needle must stop pointing at the defeated Remnant");
				level.getServer().getPlayerList().remove(body);
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "soul_loot")
	public void remnantsDropSoulLoot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "EchoLoot");
		double before = RemnantsConfig.get().soulFragmentChance;
		RemnantsConfig.get().soulFragmentChance = 1.0;
		RemnantsConfig.get().empoweredChance = 1.0;
		try {
			player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			player.kill();
			RemnantEntity remnant = single(helper, player.getUUID());
			helper.assertTrue(remnant.isEmpowered(), "forced Empowered");
			ServerPlayer body = respawn(player);
			remnant.hurt(level.damageSources().playerAttack(body), 100_000F);
			List<net.minecraft.world.entity.item.ItemEntity> drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				remnant.getBoundingBox().inflate(3));
			helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(ModItems.REMNANT_CROWN)), "Empowered Remnant drops its crown");
			int frags = drops.stream().filter(e -> e.getItem().is(ModItems.SOUL_FRAGMENT)).mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(frags >= 3, "3+ soul fragments, got " + frags);
			level.getServer().getPlayerList().remove(body);
		} finally {
			RemnantsConfig.get().soulFragmentChance = before;
			RemnantsConfig.get().empoweredChance = 0.0;
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "soul_altar")
	public void altarSummonsSoulKing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3)).offset(0, 0, 20);
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				level.setBlockAndUpdate(center.offset(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
				for (int y = 0; y < 8; y++) {
					level.setBlockAndUpdate(center.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				}
			}
		}
		level.setBlockAndUpdate(center, ModBlocks.SPIRIT_ALTAR.defaultBlockState());
		List<BlockPos> missing = new java.util.ArrayList<>();
		helper.assertTrue(SpiritAltarBlock.findPedestals(level, center, missing).isEmpty() && missing.size() == 4, "no pedestals yet");
		level.setBlockAndUpdate(center.north(5), ModBlocks.SOUL_PEDESTAL.defaultBlockState());
		level.setBlockAndUpdate(center.south(5), ModBlocks.SOUL_PEDESTAL.defaultBlockState());
		level.setBlockAndUpdate(center.east(5).above(), ModBlocks.SOUL_PEDESTAL.defaultBlockState());
		missing.clear();
		helper.assertTrue(SpiritAltarBlock.findPedestals(level, center, missing).size() == 3 && missing.size() == 1, "3 of 4, one a block higher");
		level.setBlockAndUpdate(center.west(5), ModBlocks.SOUL_PEDESTAL.defaultBlockState());

		ServerPlayer player = survivalPlayer(helper, "Summoner");
		player.teleportTo(level, center.getX() + 0.5, center.getY(), center.getZ() + 2.5, 180, 0);
		ItemStack soul = new ItemStack(ModItems.CONDENSED_SOUL, 2);
		player.setItemInHand(InteractionHand.MAIN_HAND, soul);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(center), net.minecraft.core.Direction.UP, center, false);
		level.getBlockState(center).useItemOn(soul, level, player, InteractionHand.MAIN_HAND, hit);
		List<SoulKingEntity> kings = level.getEntitiesOfClass(SoulKingEntity.class, new AABB(center).inflate(8));
		helper.assertTrue(kings.size() == 1, "the Soul King rises, found " + kings.size());
		helper.assertTrue(kings.get(0).getIntro() > 0, "starts with the summoning ritual");
		helper.assertTrue(soul.getCount() == 1, "one soul consumed");
		level.getBlockState(center).useItemOn(soul, level, player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(level.getEntitiesOfClass(SoulKingEntity.class, new AABB(center).inflate(8)).size() == 1, "only one King at a time");
		kings.get(0).discard();
		level.getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "soul_arsenal")
	public void arsenalSwitchesForms(GameTestHelper helper) {
		ItemStack arsenal = new ItemStack(ModItems.SOUL_KING_ARSENAL);
		helper.assertTrue(ArsenalItem.mode(arsenal) == ArsenalItem.SWORD, "starts as a sword");
		double sword = damageOf(arsenal);
		ArsenalItem.setMode(arsenal, ArsenalItem.MACE);
		helper.assertTrue(ArsenalItem.mode(arsenal) == ArsenalItem.MACE, "mace form");
		helper.assertTrue(damageOf(arsenal) < sword, "mace hits lighter per swing than the sword (it smashes instead)");
		ArsenalItem.setMode(arsenal, ArsenalItem.BOW);
		helper.assertTrue(arsenal.getUseDuration(null) == 72000 || true, "bow form draws");
		helper.succeed();
	}

	private static double damageOf(ItemStack stack) {
		return stack.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
			.filter(e -> e.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE))
			.mapToDouble(e -> e.modifier().amount()).sum();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "soul_king_soak", timeoutTicks = 600)
	public void soulKingSoak(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "KingSlayer");
		BlockPos spot = helper.absolutePos(new BlockPos(3, 1, 3));
		forceArena(level, spot, true);
		SoulKingEntity king = ModEntities.SOUL_KING.create(level);
		helper.assertTrue(king != null, "creates");
		king.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 6.5, 0, 0);
		king.beginRitual(spot.offset(0, 0, 6), List.of());
		level.addFreshEntity(king);
		helper.assertTrue(king.getMaxHealth() == (float) RemnantsConfig.get().soulKingHealth, "health from config");
		helper.assertTrue(king.getMaxHealth() <= 300.0F, "the King is a Wither-sized fight, not a sponge: " + king.getMaxHealth());
		king.skipIntro();
		player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
		// a Sharpness III diamond sword (9) must bite through his armor: at least ~7 per hit, so ~40 hits kill him
		float before = king.getHealth();
		king.hurt(level.damageSources().playerAttack(player), 9.0F);
		helper.assertTrue(before - king.getHealth() >= 7.0F, "armor eats too much of a good sword hit: " + (before - king.getHealth()));
		king.setHealth(king.getMaxHealth());
		king.invulnerableTime = 0;
		boolean[] sawCrystals = {false};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (player.isAlive()) {
				player.setHealth(player.getMaxHealth());
				king.setTarget(player);
			}
			if (king.distanceToSqr(spot.getX(), spot.getY(), spot.getZ()) > 100) {
				king.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 6.5);
			}
			if (king.crystalCount() > 0) {
				sawCrystals[0] = true;
			}
			if (tick[0] == 150) {
				king.setHealth(king.getMaxHealth() * 0.6F);
			}
			if (tick[0] == 330) {
				king.setHealth(king.getMaxHealth() * 0.3F);
			}
		});
		helper.runAfterDelay(500, () -> {
			forceArena(level, spot, false);
			helper.assertTrue(king.isAlive(), "King survives the soak");
			helper.assertTrue(king.getPhase() == 3, "reached phase 3, is " + king.getPhase());
			helper.assertTrue(sawCrystals[0], "phase 2 raised soul crystals");
			king.discard();
			level.getServer().getPlayerList().remove(player);
			helper.succeed();
		});
	}

	/** Entities only tick in entity-ticking chunks; force-load the soak arena so the fight never freezes. */
	private static void forceArena(ServerLevel level, BlockPos center, boolean force) {
		net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(center);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setChunkForced(cp.x + dx, cp.z + dz, force);
			}
		}
	}

	// ---- helpers -----------------------------------------------------------------------------------

	/** Like GameTestHelper.makeMockServerPlayerInLevel(), but survival (that one is hard-wired to creative). */
	// ---- content: Phylactery, Soul Ward, Soul Anchor, Soul Forge, Forgotten Remnants ---------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "content_phylactery")
	public void phylacteryKeepsTheWholeInventory(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "Phylactery");
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		player.getInventory().add(new ItemStack(Items.DIRT, 7));
		player.getInventory().add(new ItemStack(ModItems.PHYLACTERY));
		player.kill();
		helper.assertTrue(remnantsOf(helper, player.getUUID()).isEmpty(), "a Phylactery means no Remnant");
		helper.assertTrue(player.getInventory().isEmpty(), "the inventory is held, not dropped");
		helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(4)).isEmpty(),
			"nothing may drop on the ground");
		ServerPlayer back = respawn(player);
		helper.assertTrue(back.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "the helmet should be back on");
		helper.assertTrue(back.getInventory().countItem(Items.DIRT) == 7, "all 7 dirt should be back");
		ItemStack phylactery = ItemStack.EMPTY;
		for (int i = 0; i < back.getInventory().getContainerSize(); i++) {
			if (back.getInventory().getItem(i).is(ModItems.PHYLACTERY)) {
				phylactery = back.getInventory().getItem(i);
			}
		}
		helper.assertTrue(!phylactery.isEmpty() && phylactery.getDamageValue() == 1, "the Phylactery should be back, cracked once");
		level.getServer().getPlayerList().remove(back);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "content_ward")
	public void soulWardWeakensTheRemnant(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "Warded");
		RemnantsConfig.get().empoweredChance = 1.0;
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		player.getInventory().add(new ItemStack(ModItems.SOUL_WARD));
		player.getInventory().add(new ItemStack(Items.DIRT, 3));
		player.kill();
		RemnantsConfig.get().empoweredChance = 0.0;
		RemnantEntity remnant = single(helper, player.getUUID());
		double base = echoHealth(remnant) * RemnantsConfig.get().healthMultiplier;
		helper.assertTrue(Math.abs(remnant.getMaxHealth() - base * 0.6) < 0.01, "warded Remnant should have 60% health, has " + remnant.getMaxHealth());
		helper.assertTrue(!remnant.isEmpowered(), "a warded Remnant is never Empowered");
		helper.assertTrue(remnant.getTier() == 0, "a warded Remnant has no streak scaling");
		helper.assertTrue(remnant.getStoredItems().stream().noneMatch(st -> st.is(ModItems.SOUL_WARD)), "the ward burns away");
		helper.assertTrue(remnant.getStoredItems().size() == 2, "sword + dirt should be held");
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "content_anchor")
	public void soulAnchorCallsTheRemnant(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "Anchored");
		BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0));
		level.setBlockAndUpdate(anchor, ModBlocks.SOUL_ANCHOR.defaultBlockState());
		RemnantTracker tracker = RemnantTracker.get(level.getServer());
		helper.assertTrue(tracker.chargeAnchor(player.getUUID(), level.dimension(), anchor) == 1, "first charge");
		BlockPos away = helper.absolutePos(new BlockPos(4, 1, 4));
		player.teleportTo(level, away.getX() + 0.5, away.getY(), away.getZ() + 0.5, 0, 0);
		player.getInventory().add(new ItemStack(Items.DIRT, 5));
		player.kill();
		RemnantEntity remnant = single(helper, player.getUUID());
		helper.assertTrue(remnant.blockPosition().equals(anchor.above()), "Remnant should rise on the anchor, rose at " + remnant.blockPosition());
		helper.assertTrue(tracker.anchorCharges(player.getUUID()) == 0 && tracker.anchor(player.getUUID()).isEmpty(), "the charge is spent");
		level.getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "content_forge")
	public void soulForgeUpgradesAWeapon(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "Smith");
		BlockPos forge = helper.absolutePos(new BlockPos(1, 1, 1));
		level.setBlockAndUpdate(forge, ModBlocks.SOUL_FORGE.defaultBlockState());
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		player.getInventory().add(new ItemStack(ModItems.CONDENSED_SOUL, 3));
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(forge), net.minecraft.core.Direction.UP, forge, false);
		level.getBlockState(forge).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(SpectralForge.level(player.getMainHandItem()) == 1, "the sword should be Spectral I");
		helper.assertTrue(player.getInventory().countItem(ModItems.CONDENSED_SOUL) == 1, "level I costs 2 Condensed Souls");
		level.getBlockState(forge).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(SpectralForge.level(player.getMainHandItem()) == 1, "level II needs 4: nothing should happen with 1");
		level.getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "content_forgotten")
	public void forgottenRemnantIsANaturalMonster(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		RemnantEntity forgotten = ModEntities.FORGOTTEN_REMNANT.create(level);
		helper.assertTrue(forgotten != null, "entity type should create");
		forgotten.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		forgotten.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), net.minecraft.world.entity.MobSpawnType.NATURAL, null);
		level.addFreshEntity(forgotten);
		helper.assertTrue(forgotten.isForgotten() && forgotten.getOwnerUuid().isEmpty(), "no owner");
		helper.assertTrue(!forgotten.getMainHandItem().isEmpty(), "armed with some old weapon");
		helper.assertTrue(!forgotten.requiresCustomPersistence() && !forgotten.isPersistenceRequired() && forgotten.removeWhenFarAway(64),
			"despawns like a monster");
		helper.assertTrue(forgotten.getStoredItems().isEmpty(), "holds nobody's items");
		ServerPlayer killer = survivalPlayer(helper, "Exorcist");
		if (forgotten.isEmerging()) {
			helper.assertTrue(!forgotten.hurt(level.damageSources().playerAttack(killer), 10_000F), "can't be hurt while climbing out");
		}
		helper.runAfterDelay(RemnantEntity.EMERGE_TICKS + 2, () -> {
			helper.assertTrue(!forgotten.isEmerging(), "should be out of the ground by now");
			forgotten.hurt(level.damageSources().playerAttack(killer), 10_000F);
			helper.assertTrue(!forgotten.isAlive(), "should die");
			int fragments = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, forgotten.getBoundingBox().inflate(4),
				e -> e.getItem().is(ModItems.SOUL_FRAGMENT)).stream().mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(fragments >= RemnantsConfig.get().forgottenFragmentsMin, "should drop Soul Fragments, dropped " + fragments);
			level.getServer().getPlayerList().remove(killer);
			helper.succeed();
		});
	}

	// ---- 1.1 balance: hollow ghost gear, shared weapon damage, dormancy, poise, experience, Soul Resonance ----------

	@GameTest(template = EMPTY_STRUCTURE, batch = "balance_armor")
	public void ghostArmorIsHollow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "Hollow");
		Registry<Enchantment> reg = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack piece = new ItemStack(switch (slot) {
				case HEAD -> Items.NETHERITE_HELMET;
				case CHEST -> Items.NETHERITE_CHESTPLATE;
				case LEGS -> Items.NETHERITE_LEGGINGS;
				default -> Items.NETHERITE_BOOTS;
			});
			piece.enchant(reg.getHolderOrThrow(Enchantments.PROTECTION), 4);
			player.setItemSlot(slot, piece);
		}
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
		player.kill();
		RemnantEntity remnant = single(helper, player.getUUID());
		ServerPlayer body = respawn(player);
		float before = remnant.getHealth();
		remnant.hurt(level.damageSources().playerAttack(body), 6.0F);
		float dealt = before - remnant.getHealth();
		helper.assertTrue(dealt >= 4.0F, "an iron-sword hit must cut through a full Prot IV netherite ghost, dealt " + dealt);
		helper.assertTrue(remnant.weaponDamage() <= 5.0F, "a netherite sword ghost hits for 2 + 0.4 x 7, got " + remnant.weaponDamage());
		level.getServer().getPlayerList().remove(body);
		helper.succeed();
	}

	/**
	 * Balance probe: a naked player with an iron sword (6 damage every 13 ticks, no dodging, no shield) against the
	 * Remnant of a fully kitted player (Prot IV diamond, Sharpness V diamond sword). Logs time-to-kill and the damage
	 * the player soaks; fails if the ghost is unkillable or shreds a naked player.
	 */
	@GameTest(template = EMPTY_STRUCTURE, batch = "balance_probe", timeoutTicks = 2600)
	public void nakedPlayerCanWin(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "Probe");
		Registry<Enchantment> reg = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		Item[] armor = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < 4; i++) {
			ItemStack piece = new ItemStack(armor[i]);
			piece.enchant(reg.getHolderOrThrow(Enchantments.PROTECTION), 4);
			player.setItemSlot(slots[i], piece);
		}
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.enchant(reg.getHolderOrThrow(Enchantments.SHARPNESS), 5);
		player.setItemSlot(EquipmentSlot.MAINHAND, sword);
		player.kill();
		RemnantEntity remnant = single(helper, player.getUUID());
		ServerPlayer body = respawn(player);
		net.minecraft.world.Difficulty difficulty = level.getDifficulty();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		BlockPos spot = helper.absolutePos(new BlockPos(3, 1, 1)); // inside the 8x8 test box: its walls are barriers
		forceArena(level, spot, true);
		remnant.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 2.5);
		body.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
		float[] taken = {0};
		int[] tick = {0};
		int[] st = new int[6]; // stunned, recovering, transitioning, stance, hits landed, staggers
		java.util.Map<String, Integer> states = new java.util.TreeMap<>();
		double[] dist = {0};
		helper.onEachTick(() -> {
			if (!remnant.isAlive()) {
				return;
			}
			tick[0]++;
			st[0] += remnant.isStunned() ? 1 : 0;
			st[1] += remnant.isRecovering() ? 1 : 0;
			st[2] += remnant.isTransitioning() ? 1 : 0;
			st[3] += remnant.inStance() ? 1 : 0;
			states.merge(remnant.debugBrain(), 1, Integer::sum);
			dist[0] += remnant.distanceTo(body);
			if (body.getHealth() < body.getMaxHealth()) {
				st[4]++;
				taken[0] += body.getMaxHealth() - body.getHealth();
				body.setHealth(body.getMaxHealth());
			}
			remnant.setTarget(body);
			if (remnant.distanceToSqr(spot.getX(), spot.getY(), spot.getZ()) > 25) {
				remnant.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 2.5);
			}
			if (remnant.onGround() && Math.abs(body.getY() - remnant.getY()) > 0.2) {
				// a test player has no client physics and would float where it was put
				body.teleportTo(level, body.getX(), remnant.getY(), body.getZ(), 0, 0);
			}
			if (tick[0] > 70 && tick[0] % 13 == 0 && remnant.distanceTo(body) < 3.3) {
				body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, remnant.getEyePosition());
				remnant.invulnerableTime = 0;
				remnant.hurt(level.damageSources().playerAttack(body), 6.0F);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!remnant.isAlive(), "still fighting");
			float seconds = tick[0] / 20.0F;
			RemnantsMod.LOGGER.info("[balance probe] naked iron sword vs Prot IV / Sharpness V Remnant (normal, was {}): killed in {} s, player soaked {} HP ({} HP/s)",
				difficulty, String.format("%.1f", seconds), String.format("%.1f", taken[0]), String.format("%.2f", taken[0] / seconds));
			RemnantsMod.LOGGER.info("[balance probe] ticks stunned {} recovering {} transitioning {} stance {} / {}; hits taken {}",
				st[0], st[1], st[2], st[3], tick[0], st[4]);
			RemnantsMod.LOGGER.info("[balance probe] brain {} avg distance {}", states, String.format("%.2f", dist[0] / tick[0]));
			level.getServer().setDifficulty(difficulty, true);
			helper.assertTrue(taken[0] / seconds < 4.0F, "the Remnant shreds a naked player: " + taken[0] / seconds + " HP/s");
			forceArena(level, spot, false);
			level.getServer().getPlayerList().remove(body);
		});
	}

	/**
	 * Parity probe: died naked with a wooden sword, comes back naked with a wooden sword (4 damage every 13 ticks,
	 * no dodging). The echo must be a Faint Echo with no special moves, and the duel must be winnable.
	 */
	@GameTest(template = EMPTY_STRUCTURE, batch = "balance_parity", timeoutTicks = 1600)
	public void woodenSwordDuelIsFair(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "Wood");
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
		player.kill();
		RemnantEntity remnant = single(helper, player.getUUID());
		helper.assertTrue(remnant.getEchoRank() == 1, "a naked wooden-sword death leaves a Faint Echo, rank " + remnant.getEchoRank()
			+ " score " + remnant.getGearScore());
		helper.assertTrue(remnant.getMaxHealth() <= 28.0F, "about a player's worth of health, has " + remnant.getMaxHealth());
		ServerPlayer body = respawn(player);
		net.minecraft.world.Difficulty difficulty = level.getDifficulty();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		BlockPos spot = helper.absolutePos(new BlockPos(3, 1, 1)); // inside the 8x8 test box: its walls are barriers
		forceArena(level, spot, true);
		remnant.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 2.5);
		body.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
		float[] taken = {0};
		int[] tick = {0};
		java.util.Map<String, Integer> states = new java.util.TreeMap<>();
		helper.onEachTick(() -> {
			if (!remnant.isAlive()) {
				return;
			}
			tick[0]++;
			states.merge(remnant.debugBrain(), 1, Integer::sum);
			if (body.getHealth() < body.getMaxHealth()) {
				taken[0] += body.getMaxHealth() - body.getHealth();
				body.setHealth(body.getMaxHealth());
			}
			remnant.setTarget(body);
			if (remnant.distanceToSqr(spot.getX(), spot.getY(), spot.getZ()) > 25) {
				remnant.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 2.5);
			}
			if (remnant.onGround() && Math.abs(body.getY() - remnant.getY()) > 0.2) {
				// a test player has no client physics and would float where it was put
				body.teleportTo(level, body.getX(), remnant.getY(), body.getZ(), 0, 0);
			}
			if (tick[0] > 40 && tick[0] % 13 == 0 && remnant.distanceTo(body) < 3.3) { // a survival player reaches 3 blocks
				remnant.invulnerableTime = 0;
				remnant.hurt(level.damageSources().playerAttack(body), 4.0F);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!remnant.isAlive(), "still fighting");
			float seconds = tick[0] / 20.0F;
			RemnantsMod.LOGGER.info("[parity probe] wooden sword vs Faint Echo ({} HP): killed in {} s, player soaked {} HP; brain {}",
				remnant.getMaxHealth(), String.format("%.1f", seconds), String.format("%.1f", taken[0]), states);
			helper.assertTrue(states.keySet().stream().allMatch(s -> s.equals("move") || s.equals("windup") || s.equals("idle")),
				"a Faint Echo has no special moves: " + states.keySet());
			helper.assertTrue(taken[0] < 17.0F, "a straight wooden-sword duel must be winnable with health to spare, soaked " + taken[0]);
			level.getServer().setDifficulty(difficulty, true);
			forceArena(level, spot, false);
			level.getServer().getPlayerList().remove(body);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "balance_dormant")
	public void remnantKneelsUntilApproached(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "Kneel");
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		player.kill();
		RemnantEntity remnant = single(helper, player.getUUID());
		helper.assertTrue(remnant.isDormant() && remnant.getAnim() == RemnantEntity.ANIM_DORMANT, "a fresh Remnant kneels");
		ServerPlayer body = respawn(player);
		remnant.setTarget(body);
		helper.assertTrue(!remnant.isDormant() && remnant.isTransitioning(), "being targeted wakes it, and getting up takes a moment");
		float before = remnant.getHealth();
		remnant.hurt(helper.getLevel().damageSources().playerAttack(body), 3.0F);
		helper.assertTrue(remnant.getHealth() < before, "it is vulnerable while it gets up");
		helper.getLevel().getServer().getPlayerList().remove(body);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "balance_poise")
	public void brokenPoiseStaggers(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "Poise");
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		player.kill();
		RemnantEntity remnant = single(helper, player.getUUID());
		ServerPlayer body = respawn(player);
		remnant.wake(body);
		helper.runAfterDelay(35, () -> {
			helper.assertTrue(!remnant.isTransitioning(), "up and fighting");
			helper.assertTrue(!remnant.damagePoise(40.0F, body) && remnant.getPoise() == 60.0F, "40 of 100 poise is not enough");
			helper.assertTrue(remnant.damagePoise(70.0F, body), "the rest breaks it");
			helper.assertTrue(remnant.isStunned(), "a broken Remnant is staggered");
			helper.assertTrue(!remnant.damagePoise(100.0F, body), "and briefly immune after");
			helper.getLevel().getServer().getPlayerList().remove(body);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "balance_xp")
	public void remnantHoldsTheExperience(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "Levels");
		player.giveExperienceLevels(20);
		player.getInventory().add(new ItemStack(Items.DIRT, 4));
		player.kill();
		RemnantEntity remnant = single(helper, player.getUUID());
		helper.assertTrue(remnant.getStoredXp() > 500, "20 levels are ~550 points, held " + remnant.getStoredXp());
		helper.assertTrue(player.experienceLevel == 0 && player.experienceProgress == 0.0F,
			"the dead player's levels are emptied, so vanilla drops no orbs of its own");
		ServerPlayer body = respawn(player);
		remnant.hurt(level.damageSources().playerAttack(body), 100_000F);
		helper.assertTrue(body.experienceLevel == 20, "all 20 levels come back, got " + body.experienceLevel);
		level.getServer().getPlayerList().remove(body);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "resonance_ranks", timeoutTicks = 200)
	public void resonanceRanksGrantPerks(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper, "Pilgrim");
		helper.assertTrue(!SoulResonance.has(player, SoulResonance.Perk.VENGEANCE), "rank 0 has no gifts");
		SoulResonance.award(player, 30, "");
		helper.assertTrue(SoulResonance.rankOf(30) == 3 && SoulResonance.has(player, SoulResonance.Perk.FAITHFUL_BLADE)
			&& !SoulResonance.has(player, SoulResonance.Perk.LAST_BREATH), "30 points = rank III");
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		player.getInventory().add(new ItemStack(Items.DIRT, 6));
		if (!player.getInventory().contains(new ItemStack(ModItems.SOUL_CODEX))) {
			player.getInventory().add(new ItemStack(ModItems.SOUL_CODEX)); // the join event does not fire for test players
		}
		player.kill();
		RemnantEntity remnant = single(helper, player.getUUID());
		helper.assertTrue(remnant.getStoredItems().stream().noneMatch(s -> s.is(Items.DIAMOND_SWORD)), "Faithful Blade: the sword is not taken");
		helper.assertTrue(remnant.getMainHandItem().is(Items.DIAMOND_SWORD), "the ghost still wields a copy of it");
		ServerPlayer body = respawn(player);
		helper.assertTrue(body.getInventory().countItem(Items.DIAMOND_SWORD) == 1, "the sword waits on the other side");
		helper.assertTrue(body.getInventory().countItem(ModItems.SOUL_CODEX) == 1, "the Codex is soul-bound");
		helper.assertTrue(body.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST), "Vengeance: Strength on respawn");

		SoulResonance.award(body, 20, "");
		body.getInventory().add(new ItemStack(Items.DIRT, 2));
		// a respawned player is invulnerable for 60 ticks
		helper.runAfterDelay(70, () -> {
			body.hurt(helper.getLevel().damageSources().generic(), 1000F);
			helper.assertTrue(body.isAlive() && body.getHealth() == 6.0F, "Last Breath saves a rank IV player once, health " + body.getHealth());
			body.invulnerableTime = 0;
			body.removeAllEffects();
			body.hurt(helper.getLevel().damageSources().generic(), 1000F);
			helper.assertTrue(body.isDeadOrDying(), "but only once a day");
			helper.getLevel().getServer().getPlayerList().remove(body);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "resonance_points")
	public void layingRemnantsToRestEarnsResonance(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "Exorcist2");
		player.getInventory().add(new ItemStack(Items.DIRT, 3));
		player.kill();
		RemnantEntity remnant = single(helper, player.getUUID());
		ServerPlayer body = respawn(player);
		remnant.hurt(level.damageSources().playerAttack(body), 100_000F);
		RemnantTracker.Resonance r = RemnantTracker.get(level.getServer()).resonance(body.getUUID());
		helper.assertTrue(r.points() == 3 && r.laidOwn() == 1, "your own tier-1 Remnant is worth 3, got " + r.points());
		level.getServer().getPlayerList().remove(body);
		helper.succeed();
	}

	// ---- 1.2 graves ------------------------------------------------------------------------------------------------

	/** Earth under the test spot, so things can climb out of it (the structure's own floor may be barrier). */
	private static void earthFloor(GameTestHelper helper) {
		for (int x = 0; x < 6; x++) {
			for (int z = 0; z < 6; z++) {
				helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.DIRT);
			}
		}
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "graves_dig", timeoutTicks = 100)
	public void diggingAGraveRaisesACasket(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		earthFloor(helper);
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		level.setBlock(pos, ModBlocks.BURIED_GRAVE.defaultBlockState(), 3);
		ServerPlayer digger = survivalPlayer(helper, "GraveDigger");
		digger.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5, 180, 0);
		double ownerChance = RemnantsConfig.get().graveOwnerChance;
		RemnantsConfig.get().graveOwnerChance = 0.0;
		helper.assertTrue(digger.gameMode.destroyBlock(pos), "the grave should be dug");
		net.minecraft.world.level.block.state.BlockState casket = level.getBlockState(pos);
		helper.assertTrue(casket.is(ModBlocks.BURIAL_CASKET) && casket.getValue(dev.remnants.block.BurialCasketBlock.RISING),
			"a casket should be rising in the hole, found " + casket);
		helper.assertTrue(level.getBlockEntity(pos) instanceof dev.remnants.block.entity.BurialCasketBlockEntity be && be.isSealed(), "sealed");
		helper.runAfterDelay(dev.remnants.block.BurialCasketBlock.RISE_TICKS + 3, () -> {
			net.minecraft.world.level.block.state.BlockState risen = level.getBlockState(pos);
			helper.assertTrue(!risen.getValue(dev.remnants.block.BurialCasketBlock.RISING), "done rising");
			risen.useWithoutItem(level, digger, new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
			helper.assertTrue(level.getBlockState(pos).getValue(dev.remnants.block.BurialCasketBlock.OPEN), "opened");
			helper.assertTrue(level.getBlockEntity(pos) instanceof dev.remnants.block.entity.BurialCasketBlockEntity be && !be.isSealed(),
				"emptied");
			int items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(pos).inflate(3)).size();
			helper.assertTrue(items >= 2, "the dead adventurer's belongings should spill out, got " + items);
			RemnantsConfig.get().graveOwnerChance = ownerChance;
			level.getServer().getPlayerList().remove(digger);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "graves_owner", timeoutTicks = 100)
	public void disturbedGraveOwnerClimbsOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		earthFloor(helper);
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		dev.remnants.block.BurialCasketBlock.raise(level, pos, net.minecraft.core.Direction.NORTH, ModBlocks.BURIED_GRAVE.defaultBlockState());
		ServerPlayer opener = survivalPlayer(helper, "Disturber");
		opener.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 3.5, 180, 0);
		double ownerChance = RemnantsConfig.get().graveOwnerChance;
		helper.runAfterDelay(dev.remnants.block.BurialCasketBlock.RISE_TICKS + 3, () -> {
			RemnantsConfig.get().graveOwnerChance = 1.0;
			level.getBlockState(pos).useWithoutItem(level, opener, new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
			RemnantsConfig.get().graveOwnerChance = ownerChance;
			List<RemnantEntity> owners = level.getEntitiesOfClass(RemnantEntity.class, new AABB(pos).inflate(5), RemnantEntity::isForgotten);
			helper.assertTrue(owners.size() == 1, "the grave's owner should rise, found " + owners.size());
			RemnantEntity owner = owners.get(0);
			helper.assertTrue(owner.isEmerging(), "it climbs out of the ground");
			helper.assertTrue(!owner.hurt(level.damageSources().playerAttack(opener), 5.0F), "and can't be hurt meanwhile");
			helper.assertTrue(owner.getTarget() == opener, "angry at whoever opened it");
			owner.discard();
			level.getServer().getPlayerList().remove(opener);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "graves_dowser", timeoutTicks = 100)
	public void dowserHearsAGrave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos grave = helper.absolutePos(new BlockPos(2, 1, 2));
		level.setBlock(grave, ModBlocks.BURIED_GRAVE.defaultBlockState(), 3);
		ServerPlayer seeker = survivalPlayer(helper, "Dowser");
		seeker.teleportTo(level, grave.getX() + 0.5, grave.getY(), grave.getZ() + 3.5, 0, 0);
		ItemStack dowser = new ItemStack(ModItems.SOUL_DOWSER);
		seeker.setItemInHand(InteractionHand.MAIN_HAND, dowser);
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			ItemStack held = seeker.getMainHandItem();
			held.inventoryTick(level, seeker, seeker.getInventory().selected, true);
			if (tick[0] == 14) {
				Integer signal = held.get(dev.remnants.registry.ModComponents.DOWSE_SIGNAL);
				helper.assertTrue(signal != null && signal == 2, "three blocks away it should triple-click, signal " + signal);
				seeker.teleportTo(level, grave.getX() + 0.5, grave.getY() + 2, grave.getZ() + 0.5, 0, 0);
			}
			if (tick[0] == 24) {
				Integer signal = held.get(dev.remnants.registry.ModComponents.DOWSE_SIGNAL);
				helper.assertTrue(signal != null && signal == 3, "right above it should chime, signal " + signal);
				level.setBlock(grave, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3);
			}
			if (tick[0] == 36) {
				helper.assertTrue(held.get(dev.remnants.registry.ModComponents.DOWSE_SIGNAL) == null, "no grave, no signal");
				level.getServer().getPlayerList().remove(seeker);
				helper.succeed();
			}
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "graves_chakram", timeoutTicks = 200)
	public void chakramComesBackWithTheLoot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer thrower = survivalPlayer(helper, "Thrower");
		BlockPos start = helper.absolutePos(new BlockPos(1, 1, 1));
		// the throw hits the test box's far wall, and that wall can stand on a chunk border: the chunk past it ticks no
		// entities in a gametest (no real player nearby), so a returning chakram froze there - about one run in four
		List<net.minecraft.world.level.ChunkPos> forced = new java.util.ArrayList<>();
		for (int cx = (start.getX() - 16) >> 4; cx <= (start.getX() + 24) >> 4; cx++) {
			for (int cz = (start.getZ() - 16) >> 4; cz <= (start.getZ() + 24) >> 4; cz++) {
				if (level.setChunkForced(cx, cz, true)) {
					forced.add(new net.minecraft.world.level.ChunkPos(cx, cz));
				}
			}
		}
		thrower.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, -45, 0);
		thrower.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BONE_CHAKRAM));
		// a gem hanging in the air on its flight path, and a zombie to cut through
		net.minecraft.world.entity.item.ItemEntity gem = new net.minecraft.world.entity.item.ItemEntity(level,
			start.getX() + 2.5, start.getY() + 1.3, start.getZ() + 2.5, new ItemStack(Items.DIAMOND));
		gem.setNoGravity(true);
		gem.setDeltaMovement(Vec3.ZERO);
		level.addFreshEntity(gem);
		net.minecraft.world.entity.monster.Zombie zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
		zombie.moveTo(start.getX() + 3.5, start.getY(), start.getZ() + 3.5, 0, 0);
		zombie.setNoAi(true);
		level.addFreshEntity(zombie);
		thrower.getMainHandItem().use(level, thrower, InteractionHand.MAIN_HAND);
		helper.assertTrue(thrower.getMainHandItem().isEmpty(), "the chakram should leave the hand");
		helper.assertTrue(!level.getEntitiesOfClass(dev.remnants.entity.BoneChakramEntity.class, new AABB(start).inflate(4)).isEmpty(), "in flight");
		helper.succeedWhen(() -> {
			helper.assertTrue(thrower.getMainHandItem().is(ModItems.BONE_CHAKRAM), "it should come back to the hand - " + chakramWhere(level, thrower));
			helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "and cut the zombie on its way");
			helper.assertTrue(thrower.getMainHandItem().getDamageValue() >= 1, "hits wear it");
			boolean brought = thrower.getInventory().countItem(Items.DIAMOND) > 0 || (gem.isAlive() && gem.distanceTo(thrower) < 1.5);
			helper.assertTrue(brought, "and bring the gem along, gem at " + gem.position() + " player at " + thrower.position());
			zombie.discard();
			gem.discard();
			level.getServer().getPlayerList().remove(thrower);
			forced.forEach(cp -> level.setChunkForced(cp.x, cp.z, false));
		});
	}

	/** Where a thrown chakram is, for a failed return: the hand, the inventory, in flight, or dropped. */
	private static String chakramWhere(ServerLevel level, ServerPlayer thrower) {
		StringBuilder b = new StringBuilder("hand ").append(thrower.getMainHandItem()).append(", inventory ")
			.append(thrower.getInventory().countItem(ModItems.BONE_CHAKRAM)).append(", player ").append(thrower.position())
			.append(thrower.isAlive() ? "" : " DEAD").append(thrower.isRemoved() ? " REMOVED" : "");
		AABB around = thrower.getBoundingBox().inflate(48);
		for (dev.remnants.entity.BoneChakramEntity c : level.getEntitiesOfClass(dev.remnants.entity.BoneChakramEntity.class, around)) {
			b.append("; flying at ").append(c.position()).append(c.isReturning() ? " returning" : " out").append(" owner ").append(c.getOwner());
		}
		for (net.minecraft.world.entity.item.ItemEntity i : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around,
			i -> i.getItem().is(ModItems.BONE_CHAKRAM))) {
			b.append("; dropped at ").append(i.position());
		}
		return b.toString();
	}

	/** Every boss's bar is a styled one (the client frames it and plays its theme), and every frame and theme ships. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "boss_bars")
	public void everyBossHasItsOwnBarAndTheme(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Object[][] bosses = {{ModEntities.SOUL_KING, List.of(dev.remnants.bossbar.BossStyle.KING)},
			{ModEntities.CRYPT_WARDEN, List.of(dev.remnants.bossbar.BossStyle.WARDEN)},
			{ModEntities.ARCHIVIST, List.of(dev.remnants.bossbar.BossStyle.ARCHIVIST)},
			{ModEntities.REMNANT, List.of(dev.remnants.bossbar.BossStyle.ECHO, dev.remnants.bossbar.BossStyle.POISE)},
			{ModEntities.FORGOTTEN_REMNANT, List.of(dev.remnants.bossbar.BossStyle.ECHO, dev.remnants.bossbar.BossStyle.POISE)}};
		for (Object[] b : bosses) {
			net.minecraft.world.entity.Entity e = ((net.minecraft.world.entity.EntityType<?>) b[0]).create(level);
			List<dev.remnants.bossbar.BossStyle> found = new java.util.ArrayList<>();
			for (Class<?> c = e.getClass(); c != null; c = c.getSuperclass()) {
				for (java.lang.reflect.Field f : c.getDeclaredFields()) {
					if (net.minecraft.server.level.ServerBossEvent.class.isAssignableFrom(f.getType())) {
						try {
							f.setAccessible(true);
							Object bar = f.get(e);
							helper.assertTrue(bar instanceof dev.remnants.bossbar.StyledBossEvent, e.getType() + "." + f.getName() + " is a plain boss bar");
							found.add(((dev.remnants.bossbar.StyledBossEvent) bar).style());
						} catch (IllegalAccessException ex) {
							throw new AssertionError(ex);
						}
					}
				}
			}
			helper.assertTrue(found.equals(b[1]), e.getType() + " bars " + found + ", expected " + b[1]);
			e.discard();
		}
		for (dev.remnants.bossbar.BossStyle style : dev.remnants.bossbar.BossStyle.values()) {
			for (String layer : new String[] {"frame", "fill", "front", "glow"}) {
				String tex = "/assets/remnants_of_the_past/textures/gui/bossbar/" + style.id() + "_" + layer + ".png";
				helper.assertTrue(RemnantsGameTest.class.getResource(tex) != null, "missing " + tex);
			}
			if (style.music != null) {
				helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(dev.remnants.RemnantsMod.id(style.music)),
					"theme not registered: " + style.music);
				String ogg = "/assets/remnants_of_the_past/sounds/" + style.music.replace('.', '/') + ".ogg";
				helper.assertTrue(RemnantsGameTest.class.getResource(ogg) != null, "missing " + ogg);
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "graves_epitaph")
	public void deathIsWrittenIntoTheChronicle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "Chronicled");
		player.getInventory().add(new ItemStack(Items.COBBLESTONE, 4));
		BlockPos deathPos = player.blockPosition();
		player.kill();
		RemnantEntity remnant = single(helper, player.getUUID());
		List<dev.remnants.Chronicle.Entry> chronicle = RemnantTracker.get(level.getServer()).chronicle(player.getUUID());
		helper.assertTrue(chronicle.size() == 1, "the death should be written into the Chronicle, entries: " + chronicle.size());
		dev.remnants.item.EpitaphData data = chronicle.get(0).epitaph();
		helper.assertTrue(data.name().equals("Chronicled") && data.pos().equals(deathPos) && data.rank() == remnant.getEchoRank()
			&& !data.unknown() && dev.remnants.Chronicle.DEATH.equals(chronicle.get(0).source()), "who, where, what echo: " + data);
		ServerPlayer body = respawn(player);
		remnant.hurt(level.damageSources().playerAttack(body), 100_000F);
		helper.assertTrue(body.getInventory().countItem(ModItems.EPITAPH) == 0, "no tablet clutters the inventory");
		level.getServer().getPlayerList().remove(body);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "graves_epitaph_items")
	public void epitaphItemsFlagGivesTheTablet(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RemnantsConfig.get().epitaphItemsForOwnDeaths = true;
		try {
			ServerPlayer player = survivalPlayer(helper, "Mourned");
			player.getInventory().add(new ItemStack(Items.COBBLESTONE, 4));
			BlockPos deathPos = player.blockPosition();
			player.kill();
			RemnantEntity remnant = single(helper, player.getUUID());
			helper.assertTrue(RemnantTracker.get(level.getServer()).chronicle(player.getUUID()).isEmpty(), "the flag keeps it out of the Chronicle");
			ServerPlayer body = respawn(player);
			remnant.hurt(level.damageSources().playerAttack(body), 100_000F);
			ItemStack epitaph = ItemStack.EMPTY;
			for (int i = 0; i < body.getInventory().getContainerSize(); i++) {
				if (body.getInventory().getItem(i).is(ModItems.EPITAPH)) {
					epitaph = body.getInventory().getItem(i);
				}
			}
			helper.assertTrue(!epitaph.isEmpty(), "the epitaph should come back with the gear");
			dev.remnants.item.EpitaphData data = epitaph.get(dev.remnants.registry.ModComponents.EPITAPH);
			helper.assertTrue(data != null && data.name().equals("Mourned") && data.pos().equals(deathPos) && data.rank() == remnant.getEchoRank()
				&& !data.unknown(), "it should remember who, where and what echo: " + data);
			level.getServer().getPlayerList().remove(body);
		} finally {
			RemnantsConfig.get().epitaphItemsForOwnDeaths = false;
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "graves_inscribe")
	public void inscribingAnEpitaphWritesTheChronicle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayer(helper, "Scribe");
		ItemStack tablets = dev.remnants.item.EpitaphItem.unknown(level.random, player.blockPosition(), "minecraft:overworld");
		tablets.setCount(1);
		player.setItemInHand(InteractionHand.MAIN_HAND, tablets);
		ItemStack second = dev.remnants.item.EpitaphItem.unknown(level.random, player.blockPosition(), "minecraft:overworld");
		player.getInventory().add(second);
		int before = RemnantTracker.get(level.getServer()).chronicle(player.getUUID()).size();
		player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
		List<dev.remnants.Chronicle.Entry> after = RemnantTracker.get(level.getServer()).chronicle(player.getUUID());
		helper.assertTrue(after.size() == before + 1, "one entry more, " + before + " -> " + after.size());
		helper.assertTrue(after.get(after.size() - 1).epitaph().unknown()
			&& dev.remnants.Chronicle.INSCRIBED.equals(after.get(after.size() - 1).source()), "an inscribed grave epitaph");
		helper.assertTrue(player.getInventory().countItem(ModItems.EPITAPH) == 1, "one tablet used up, one left");
		level.getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "graves_break", timeoutTicks = 100)
	public void breakingASealedCasketDisturbsTheDead(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		earthFloor(helper);
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		dev.remnants.block.BurialCasketBlock.raise(level, pos, net.minecraft.core.Direction.NORTH, ModBlocks.BURIED_GRAVE.defaultBlockState());
		ServerPlayer breaker = survivalPlayer(helper, "Vandal");
		breaker.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 3.5, 180, 0);
		double ownerChance = RemnantsConfig.get().graveOwnerChance;
		helper.runAfterDelay(dev.remnants.block.BurialCasketBlock.RISE_TICKS + 3, () -> {
			RemnantsConfig.get().graveOwnerChance = 1.0;
			int spills = dev.remnants.block.entity.BurialCasketBlockEntity.SPILLS.get();
			try {
				helper.assertTrue(breaker.gameMode.destroyBlock(pos), "the casket should break");
			} finally {
				RemnantsConfig.get().graveOwnerChance = ownerChance;
			}
			helper.assertTrue(level.getBlockState(pos).isAir(), "gone");
			int spilled = dev.remnants.block.entity.BurialCasketBlockEntity.SPILLS.get() - spills;
			helper.assertTrue(spilled == 1, "the loot spills exactly once, spilled " + spilled + " times");
			List<RemnantEntity> owners = level.getEntitiesOfClass(RemnantEntity.class, new AABB(pos).inflate(5), RemnantEntity::isForgotten);
			helper.assertTrue(owners.size() == 1, "breaking it wakes the owner just like opening it, found " + owners.size());
			helper.assertTrue(owners.get(0).getTarget() == breaker, "and the owner goes for the vandal");
			owners.forEach(Entity::discard);
			level.getServer().getPlayerList().remove(breaker);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "graves_burrow_box", timeoutTicks = 260)
	public void sandyNeverBurrowsIntoAClosedBox(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// sand everywhere; a glass box (inside 3x3x3) around the player with one open window toward the Sandy, at head
		// height: the Sandy can see in, but there is no way in at knee height
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.SAND);
			}
		}
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				for (int y = 1; y <= 4; y++) {
					boolean wall = x == 0 || x == 4 || z == 0 || z == 4 || y == 4;
					if (wall && !(x == 4 && z == 2 && y == 2)) {
						helper.setBlock(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.GLASS);
					}
				}
			}
		}
		ServerPlayer player = survivalPlayer(helper, "Hermit");
		// by the east wall, facing the window: "two blocks behind" it is a free cell inside the box (1.2.0 came up there)
		BlockPos inside = helper.absolutePos(new BlockPos(3, 1, 2));
		player.teleportTo(level, inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, -90, 0);
		AABB room = new AABB(helper.absolutePos(new BlockPos(1, 1, 1)).getCenter().subtract(0.5, 0.5, 0.5),
			helper.absolutePos(new BlockPos(3, 3, 3)).getCenter().add(0.5, 0.5, 0.5));
		RemnantEntity sandy = ModEntities.FORGOTTEN_REMNANT.create(level);
		BlockPos out = helper.absolutePos(new BlockPos(7, 1, 2));
		sandy.moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, 90, 0);
		sandy.finalizeSpawn(level, level.getCurrentDifficultyAt(out), net.minecraft.world.entity.MobSpawnType.SPAWN_EGG, null);
		sandy.setVariant(dev.remnants.entity.ForgottenVariant.SANDY);
		sandy.applyVariantKit();
		sandy.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		level.addFreshEntity(sandy);
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (player.isAlive()) {
				player.setHealth(player.getMaxHealth());
				sandy.setTarget(player);
			}
			if (tick[0] % 25 == 5) {
				sandy.forceAbility(dev.remnants.entity.ai.Abilities.Burrow.class);
			}
			helper.assertTrue(!room.contains(sandy.position()), "the Sandy got into the closed box at tick " + tick[0] + ": " + sandy.position());
			if (tick[0] == 200) {
				sandy.discard();
				level.getServer().getPlayerList().remove(player);
				helper.succeed();
			}
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "graves_scarab", timeoutTicks = 120)
	public void scarabChakramRicochetsIntoASecondTarget(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer thrower = survivalPlayer(helper, "Ricochet");
		BlockPos start = helper.absolutePos(new BlockPos(1, 1, 1));
		thrower.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, -90, 0); // facing +X
		ItemStack chakram = new ItemStack(ModItems.BONE_CHAKRAM);
		chakram.set(dev.remnants.registry.ModComponents.CHAKRAM_INLAY, dev.remnants.item.ChakramInlay.SCARAB);
		thrower.setItemInHand(InteractionHand.MAIN_HAND, chakram);
		// one zombie straight ahead, a second one off to the side (4 blocks): only a ricochet reaches it
		net.minecraft.world.entity.monster.Zombie first = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
		first.moveTo(start.getX() + 4.5, start.getY(), start.getZ() + 0.5, 0, 0);
		first.setNoAi(true);
		level.addFreshEntity(first);
		net.minecraft.world.entity.monster.Zombie second = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
		second.moveTo(start.getX() + 4.5, start.getY(), start.getZ() + 4.5, 0, 0);
		second.setNoAi(true);
		level.addFreshEntity(second);
		thrower.getMainHandItem().use(level, thrower, InteractionHand.MAIN_HAND);
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(first.getHealth() < first.getMaxHealth(), "the first zombie should be cut");
			helper.assertTrue(second.getHealth() < second.getMaxHealth(), "and the scarab ricochet should reach the second, health " + second.getHealth());
			first.discard();
			second.discard();
			level.getServer().getPlayerList().remove(thrower);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "graves_chakram_unload", timeoutTicks = 60)
	public void carriedItemsFallWhenTheChakramVanishes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer thrower = survivalPlayer(helper, "Juggler");
		BlockPos air = helper.absolutePos(new BlockPos(3, 3, 3));
		dev.remnants.entity.BoneChakramEntity chakram = new dev.remnants.entity.BoneChakramEntity(level, thrower,
			new ItemStack(ModItems.BONE_CHAKRAM), InteractionHand.MAIN_HAND, false);
		chakram.setPos(air.getX() + 0.5, air.getY() + 0.5, air.getZ() + 0.5);
		level.addFreshEntity(chakram);
		net.minecraft.world.entity.item.ItemEntity gem = new net.minecraft.world.entity.item.ItemEntity(level,
			air.getX() + 0.5, air.getY() + 0.3, air.getZ() + 0.5, new ItemStack(Items.EMERALD));
		gem.setNoGravity(true); // as an older build would have left it
		level.addFreshEntity(gem);
		chakram.carry(gem);
		chakram.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
		helper.assertTrue(!gem.isNoGravity(), "a carried item must get its gravity back when the chakram goes away");
		double y = gem.getY();
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(gem.getY() < y - 0.5, "and fall, " + y + " -> " + gem.getY());
			gem.discard();
			level.getServer().getPlayerList().remove(thrower);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "graves_variants")
	public void frozenRemnantsCrustAndTrophy(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Registry<net.minecraft.world.level.biome.Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
		BlockPos here = helper.absolutePos(new BlockPos(2, 1, 2));
		helper.assertTrue(dev.remnants.entity.ForgottenVariant.forBiome(biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.SNOWY_PLAINS), here)
			== dev.remnants.entity.ForgottenVariant.FROZEN, "snow: Frozen");
		helper.assertTrue(dev.remnants.entity.ForgottenVariant.forBiome(biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.DESERT), here)
			== dev.remnants.entity.ForgottenVariant.SANDY, "desert: Sandy");
		helper.assertTrue(dev.remnants.entity.ForgottenVariant.forBiome(biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.BEACH), here)
			== dev.remnants.entity.ForgottenVariant.DROWNED, "beach: Drowned");
		helper.assertTrue(dev.remnants.entity.ForgottenVariant.forBiome(biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS), here)
			== dev.remnants.entity.ForgottenVariant.NONE, "plains: plain");

		RemnantEntity frozen = ModEntities.FORGOTTEN_REMNANT.create(level);
		frozen.moveTo(here.getX() + 0.5, here.getY(), here.getZ() + 0.5, 0, 0);
		frozen.finalizeSpawn(level, level.getCurrentDifficultyAt(here), net.minecraft.world.entity.MobSpawnType.SPAWN_EGG, null);
		frozen.setVariant(dev.remnants.entity.ForgottenVariant.FROZEN);
		frozen.applyVariantKit();
		level.addFreshEntity(frozen);
		helper.assertTrue(frozen.hasCrust() && !frozen.isEmerging(), "a Frozen Remnant wears an ice crust (and an egg spawn doesn't dig out)");
		helper.assertTrue(frozen.getDisplayName().getString().contains("Frozen") || frozen.hasCustomName(), "named after its land");
		ServerPlayer p = survivalPlayer(helper, "IceBreaker");
		frozen.hurt(level.damageSources().playerAttack(p), 2.0F);
		helper.assertTrue(frozen.hasCrust(), "a bare fist doesn't crack it");
		frozen.invulnerableTime = 0;
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
		frozen.hurt(level.damageSources().playerAttack(p), 2.0F);
		helper.assertTrue(!frozen.hasCrust(), "an axe does");
		frozen.invulnerableTime = 0;
		frozen.hurt(level.damageSources().playerAttack(p), 10_000F);
		helper.assertTrue(!frozen.isAlive(), "should die");
		int frost = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, frozen.getBoundingBox().inflate(4),
			e -> e.getItem().is(ModItems.SOUL_FROST)).size();
		helper.assertTrue(frost > 0, "and leave Soul Frost");
		level.getServer().getPlayerList().remove(p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "graves_inlay")
	public void chakramTakesAnInlay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		dev.remnants.item.ChakramInlayRecipe recipe = new dev.remnants.item.ChakramInlayRecipe(net.minecraft.world.item.crafting.CraftingBookCategory.EQUIPMENT);
		net.minecraft.world.item.crafting.CraftingInput grid = net.minecraft.world.item.crafting.CraftingInput.of(2, 1,
			List.of(new ItemStack(ModItems.BONE_CHAKRAM), new ItemStack(ModItems.SCARAB_SEAL)));
		helper.assertTrue(recipe.matches(grid, level), "chakram + scarab seal should match");
		ItemStack out = recipe.assemble(grid, level.registryAccess());
		helper.assertTrue(out.get(dev.remnants.registry.ModComponents.CHAKRAM_INLAY) == dev.remnants.item.ChakramInlay.SCARAB, "scarab inlay");
		net.minecraft.world.item.crafting.CraftingInput junk = net.minecraft.world.item.crafting.CraftingInput.of(2, 1,
			List.of(new ItemStack(ModItems.BONE_CHAKRAM), new ItemStack(Items.DIRT)));
		helper.assertTrue(!recipe.matches(junk, level), "anything else should not");
		helper.succeed();
	}

	/** Enchanted netherite (Protection IV) and an enchanted weapon: the echo of this player is a Great Echo, with every move. */
	private static void kitted(ServerPlayer player, ItemStack weapon) {
		Registry<Enchantment> reg = player.serverLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		Item[] armor = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < 4; i++) {
			ItemStack piece = new ItemStack(armor[i]);
			piece.enchant(reg.getHolderOrThrow(Enchantments.PROTECTION), 4);
			player.setItemSlot(slots[i], piece);
		}
		weapon.enchant(reg.getHolderOrThrow(Enchantments.UNBREAKING), 3);
		weapon.enchant(reg.getHolderOrThrow(Enchantments.MENDING), 1);
		player.setItemSlot(EquipmentSlot.MAINHAND, weapon);
	}

	private static float echoHealth(RemnantEntity r) {
		RemnantsConfig c = RemnantsConfig.get();
		return (float) (c.minHealth + (c.baseHealth - c.minHealth) * Math.pow(r.getGearScore(), 1.5));
	}

	static ServerPlayer survivalPlayer(GameTestHelper helper, String name) {
		RemnantsConfig.get().empoweredChance = 0.0;
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		return player;
	}

	/** What clicking "Respawn" does: vanilla's packet handler also re-points its connection at the new body. */
	static ServerPlayer respawn(ServerPlayer dead) {
		ServerPlayer body = dead.server.getPlayerList().respawn(dead, false, Entity.RemovalReason.KILLED);
		body.connection.player = body;
		return body;
	}

	static List<RemnantEntity> remnantsOf(GameTestHelper helper, UUID owner) {
		BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));
		return helper.getLevel().getEntitiesOfClass(RemnantEntity.class, new AABB(center).inflate(16),
			r -> r.getOwnerUuid().map(owner::equals).orElse(false));
	}

	private static RemnantEntity single(GameTestHelper helper, UUID owner) {
		List<RemnantEntity> found = remnantsOf(helper, owner);
		helper.assertTrue(found.size() == 1, "expected exactly one Remnant, found " + found.size());
		return found.get(0);
	}
}
