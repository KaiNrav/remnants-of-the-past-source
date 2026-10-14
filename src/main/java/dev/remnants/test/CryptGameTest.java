package dev.remnants.test;

import dev.remnants.RemnantsMod;
import dev.remnants.block.OssuaryGateBlock;
import dev.remnants.block.entity.BelfryBlockEntity;
import dev.remnants.entity.CryptWardenEntity;
import dev.remnants.entity.GraveMimicEntity;
import dev.remnants.entity.KeenerEntity;
import dev.remnants.entity.RemnantEntity;
import dev.remnants.entity.SoulTetherEntity;
import dev.remnants.item.HandBellItem;
import dev.remnants.multiblock.MultiblockPattern;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.registry.ModBlocks;
import dev.remnants.registry.ModEntities;
import dev.remnants.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 1.4 "Crypts": the Crypt Warden's guard and openings, the Grave Mimic's sleep, gates opened by key and by eyes, the
 * Hand Bell, the Keener's wail, the Soul Tether, the Night of Memory from the bell to the reward, and the Ossuary's
 * templates and jigsaw assembly.
 */
public class CryptGameTest implements FabricGameTest {

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	private static CryptWardenEntity warden(GameTestHelper helper, BlockPos at, float yaw, boolean awake) {
		ServerLevel level = helper.getLevel();
		BlockPos p = helper.absolutePos(at);
		CryptWardenEntity w = ModEntities.CRYPT_WARDEN.create(level);
		w.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0.0F);
		w.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.SPAWN_EGG, null);
		w.setYRot(yaw);
		w.yBodyRot = yaw;
		w.yHeadRot = yaw;
		level.addFreshEntity(w);
		if (awake) {
			w.showAwake();
		}
		return w;
	}

	private static void remove(GameTestHelper helper, ServerPlayer... players) {
		for (ServerPlayer p : players) {
			helper.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	private static long count(GameTestHelper helper, BlockPos near, double r, net.minecraft.world.item.Item item) {
		Vec3 c = Vec3.atCenterOf(helper.absolutePos(near));
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(c, c).inflate(r), e -> e.getItem().is(item))
			.stream().mapToLong(e -> e.getItem().getCount()).sum();
	}

	// ---- the Crypt Warden ----------------------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_warden", timeoutTicks = 40)
	public void wardenShieldTurnsFrontalBlowsButNotBackstabs(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		CryptWardenEntity w = warden(helper, new BlockPos(4, 1, 4), 180.0F, true);   // facing north (-z)
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Thief");
		BlockPos wp = helper.absolutePos(new BlockPos(4, 1, 4));
		float full = w.getHealth();
		player.teleportTo(level, wp.getX() + 0.5, wp.getY(), wp.getZ() - 1.5, 0, 0);   // in front
		helper.assertFalse(w.hurt(level.damageSources().playerAttack(player), 10.0F), "a blow from the front glances off the shield");
		helper.assertTrue(w.getHealth() == full, "no damage through the shield: " + w.getHealth());
		w.invulnerableTime = 0;
		player.teleportTo(level, wp.getX() + 0.5, wp.getY(), wp.getZ() + 2.5, 0, 0);   // behind
		helper.assertTrue(w.hurt(level.damageSources().playerAttack(player), 10.0F), "a backstab goes through");
		float afterBack = w.getHealth();
		helper.assertTrue(afterBack < full, "the back is open: " + afterBack);
		w.invulnerableTime = 0;
		player.teleportTo(level, wp.getX() + 0.5, wp.getY(), wp.getZ() - 1.5, 0, 0);
		w.forceAnim(CryptWardenEntity.ANIM_STUCK);   // its sword stuck after a miss
		helper.assertTrue(w.isExposed(), "stuck = exposed");
		helper.assertTrue(w.hurt(level.damageSources().playerAttack(player), 10.0F), "an exposed Warden takes a frontal blow");
		helper.assertTrue(w.getHealth() < afterBack, "front damage while exposed");
		w.discard();
		remove(helper, player);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_warden_statue", timeoutTicks = 40)
	public void sleepingWardenIsStoneAndWakesWhenStruck(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		CryptWardenEntity w = warden(helper, new BlockPos(4, 1, 4), 180.0F, false);
		helper.assertTrue(w.isDormant(), "a statue until someone comes");
		float full = w.getHealth();
		helper.assertFalse(w.hurt(level.damageSources().generic(), 50.0F), "stone takes nothing");
		helper.assertTrue(w.getHealth() == full, "full health");
		Pig pig = net.minecraft.world.entity.EntityType.PIG.create(level);
		pig.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 1, 6))));
		level.addFreshEntity(pig);
		helper.assertFalse(w.hurt(level.damageSources().mobAttack(pig), 5.0F), "the blow that wakes it does not hurt");
		helper.assertTrue(!w.isDormant() && w.getAnim() == CryptWardenEntity.ANIM_AWAKEN, "it wakes: " + w.getAnim());
		w.discard();
		pig.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_warden_miss", timeoutTicks = 80)
	public void crushingBlowThatMissesLeavesItStuck(GameTestHelper helper) {
		floor(helper);
		CryptWardenEntity w = warden(helper, new BlockPos(4, 1, 4), 180.0F, true);
		w.forceAnim(CryptWardenEntity.ANIM_SLAM_WINDUP);
		helper.runAfterDelay(CryptWardenEntity.SLAM_WINDUP + 9, () -> {
			helper.assertTrue(w.getAnim() == CryptWardenEntity.ANIM_STUCK && w.isExposed(),
				"nothing under the blade: the sword sticks in the floor (anim " + w.getAnim() + ")");
			w.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_warden_loot", timeoutTicks = 40)
	public void wardenLeavesKeyAndGauntlet(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		CryptWardenEntity w = warden(helper, new BlockPos(4, 1, 4), 180.0F, true);
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Victor");
		BlockPos wp = helper.absolutePos(new BlockPos(4, 1, 4));
		player.teleportTo(level, wp.getX() + 0.5, wp.getY(), wp.getZ() + 2.5, 0, 0);
		w.hurt(level.damageSources().playerAttack(player), 10000.0F);
		helper.assertTrue(w.isDeadOrDying(), "dead");
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(count(helper, new BlockPos(4, 1, 4), 4, ModItems.OSSUARY_KEY) == 1, "the Ossuary Key");
			helper.assertTrue(count(helper, new BlockPos(4, 1, 4), 4, ModItems.WARDENS_GAUNTLET) == 1, "the Warden's Gauntlet");
			helper.assertTrue(count(helper, new BlockPos(4, 1, 4), 4, ModItems.SOUL_FRAGMENT) >= 4, "Soul Fragments");
			remove(helper, player);
			helper.succeed();
		});
	}

	// ---- the Grave Mimic -----------------------------------------------------------------------------------

	private static GraveMimicEntity mimic(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos p = helper.absolutePos(at);
		GraveMimicEntity m = ModEntities.GRAVE_MIMIC.create(level);
		m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.0F, 0.0F);
		m.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.STRUCTURE, null);
		level.addFreshEntity(m);
		return m;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_mimic", timeoutTicks = 80)
	public void mimicSleepsUntilSomeoneComesClose(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		GraveMimicEntity m = mimic(helper, new BlockPos(6, 1, 6));
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Curious");
		BlockPos mp = helper.absolutePos(new BlockPos(6, 1, 6));
		player.teleportTo(level, mp.getX() + 0.5 - 5.0, mp.getY(), mp.getZ() + 0.5, 0, 0);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(m.isDormant(), "five blocks off it is still an urn");
			HandBellItem.ring(level, player.position(), 12, player);
			helper.assertTrue(m.isDormant() && m.getAnim() == GraveMimicEntity.ANIM_FLINCH && m.hasEffect(MobEffects.GLOWING),
				"the bell makes it flinch and glow but not wake");
			player.teleportTo(level, mp.getX() + 0.5 - 1.2, mp.getY(), mp.getZ() + 0.5, 0, 0);
		});
		helper.runAfterDelay(40, () -> {
			helper.assertFalse(m.isDormant(), "a step away and it wakes");
			helper.assertTrue(m.getTarget() == player, "and goes for whoever woke it");
			m.discard();
			remove(helper, player);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_mimic_loot", timeoutTicks = 40)
	public void mimicLeavesItsTongue(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		GraveMimicEntity m = mimic(helper, new BlockPos(4, 1, 4));
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Potter");
		m.hurt(level.damageSources().playerAttack(player), 1000.0F);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(count(helper, new BlockPos(4, 1, 4), 4, ModItems.MIMIC_TONGUE) == 1, "a Mimic Tongue");
			remove(helper, player);
			helper.succeed();
		});
	}

	// ---- gates and eyes ------------------------------------------------------------------------------------

	private static List<BlockPos> gate(GameTestHelper helper, int z) {
		List<BlockPos> out = new ArrayList<>();
		for (int x = 3; x <= 5; x++) {
			for (int y = 1; y <= 3; y++) {
				BlockPos p = helper.absolutePos(new BlockPos(x, y, z));
				helper.getLevel().setBlock(p, ModBlocks.OSSUARY_GATE.defaultBlockState().setValue(OssuaryGateBlock.TIP, y == 1), 3);
				out.add(p);
			}
		}
		return out;
	}

	/** The gate rose: each column's top block open (the raised bars' foot under the lintel), everything under it cleared. */
	private static boolean risen(GameTestHelper helper, List<BlockPos> gate) {
		for (BlockPos p : gate) {
			BlockState s = helper.getLevel().getBlockState(p);
			boolean top = !gate.contains(p.above());
			if (top ? !s.is(ModBlocks.OSSUARY_GATE) || !s.getValue(OssuaryGateBlock.OPEN) : !s.isAir()) {
				return false;
			}
		}
		return true;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_gate_key", timeoutTicks = 40)
	public void ossuaryKeyOpensTheWholeGateAndIsSpent(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		List<BlockPos> g = gate(helper, 5);
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Keyholder");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.OSSUARY_KEY));
		BlockPos at = g.get(4);
		BlockState state = level.getBlockState(at);
		state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false));
		helper.assertTrue(risen(helper, g), "every bar of the gate rose");
		helper.assertTrue(player.getMainHandItem().isEmpty(), "the key stays in the lock");
		helper.assertTrue(level.getBlockState(at).getCollisionShape(level, at).isEmpty(), "an open gate lets you through");
		remove(helper, player);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_gate_eyes", timeoutTicks = 100)
	public void strikingEveryEyeRaisesTheGate(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		List<BlockPos> g = gate(helper, 6);
		BlockPos eyeA = helper.absolutePos(new BlockPos(1, 3, 6));
		BlockPos eyeB = helper.absolutePos(new BlockPos(7, 3, 6));
		level.setBlock(eyeA, ModBlocks.CRYPT_EYE.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
		level.setBlock(eyeB, ModBlocks.CRYPT_EYE.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
		// a real arrow at the first eye
		Arrow arrow = new Arrow(level, eyeA.getX() + 0.5, eyeA.getY() + 0.5, eyeA.getZ() - 3.5, new ItemStack(Items.ARROW), null);
		arrow.shoot(0, 0.05, 1, 2.0F, 0.0F);
		level.addFreshEntity(arrow);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(level.getBlockState(eyeA).getValue(dev.remnants.block.CryptEyeBlock.STRUCK), "the arrow closed the first eye");
			helper.assertFalse(risen(helper, g), "one eye still watches: the gate stays down");
			Arrow second = new Arrow(level, eyeB.getX() + 0.5, eyeB.getY() + 0.5, eyeB.getZ() - 3.5, new ItemStack(Items.ARROW), null);
			second.shoot(0, 0.05, 1, 2.0F, 0.0F);
			level.addFreshEntity(second);
		});
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(level.getBlockState(eyeB).getValue(dev.remnants.block.CryptEyeBlock.STRUCK), "the second eye closed");
			helper.assertTrue(risen(helper, g), "every eye shut: the gate rose");
			helper.succeed();
		});
	}

	// ---- the Keener and the Hand Bell ------------------------------------------------------------------------

	private static KeenerEntity keener(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos p = helper.absolutePos(at);
		KeenerEntity k = ModEntities.KEENER.create(level);
		k.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.0F, 0.0F);
		level.addFreshEntity(k);
		return k;
	}

	private static RemnantEntity forgotten(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos p = helper.absolutePos(at);
		RemnantEntity f = ModEntities.FORGOTTEN_REMNANT.create(level);
		f.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.0F, 0.0F);
		level.addFreshEntity(f);
		return f;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_keener_wail", timeoutTicks = 80)
	public void keenerWailHealsTheForgottenUntilAHeavyBlow(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		RemnantEntity f = forgotten(helper, new BlockPos(2, 1, 2));
		f.setHealth(10.0F);
		KeenerEntity k = keener(helper, new BlockPos(5, 2, 5));
		helper.assertFalse(k.isRevealed(), "hidden until something shows it");
		k.startWail();
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(f.getHealth() > 10.0F && f.hasEffect(MobEffects.DAMAGE_BOOST), "the wail heals and strengthens: " + f.getHealth());
			helper.assertTrue(k.isWailing(), "still wailing");
			k.hurt(level.damageSources().generic(), 7.0F);
			helper.assertFalse(k.isWailing(), "a heavy blow breaks the wail");
			helper.assertTrue(k.getAnim() == KeenerEntity.ANIM_STUNNED && k.isRevealed(), "stunned and seen");
			f.discard();
			k.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_keener_bell", timeoutTicks = 40)
	public void handBellRevealsTheKeenerAndBreaksItsWail(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		KeenerEntity k = keener(helper, new BlockPos(5, 2, 5));
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Ringer");
		k.startWail();
		helper.assertTrue(k.isWailing() && !k.isRevealed(), "wailing, unseen");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HAND_BELL));
		player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
		helper.assertTrue(k.isRevealed() && !k.isWailing(), "the bell shows it and breaks the wail");
		helper.assertTrue(player.getCooldowns().isOnCooldown(ModItems.HAND_BELL), "the bell cools down");
		k.discard();
		remove(helper, player);
		helper.succeed();
	}

	// ---- the Soul Tether -----------------------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_tether_block", timeoutTicks = 40)
	public void tetherBitesABlockAndReelsItsOwner(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(6, 2, 2), Blocks.STONE);
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Climber");
		BlockPos p = helper.absolutePos(new BlockPos(1, 1, 2));
		player.teleportTo(level, p.getX() + 0.5, p.getY() - 0.62, p.getZ() + 0.5, -90.0F, 0.0F);   // eyes at the block's height, facing east
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SOUL_TETHER));
		player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
		// waits for the first pull (a fixed tick raced the hook biting on that very tick: anchored, nothing sent yet);
		// a test player never arrives, so the reel keeps pulling until the test ends
		helper.succeedWhen(() -> {
			List<SoulTetherEntity> tethers = level.getEntitiesOfClass(SoulTetherEntity.class, player.getBoundingBox().inflate(10));
			helper.assertTrue(tethers.size() == 1 && tethers.get(0).state() == SoulTetherEntity.ANCHORED, "the hook bit into the stone");
			// the pull is sent to the client as velocity (a test player has no client): check what was sent
			helper.assertTrue(tethers.get(0).lastPull.x > 0.3, "and reels its owner toward it: " + tethers.get(0).lastPull);
			helper.assertTrue(player.getMainHandItem().getDamageValue() == 1, "a shot costs one durability");
			tethers.forEach(Entity::discard);
			remove(helper, player);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_tether_remnant", timeoutTicks = 60)
	public void tetherTearsArmourOffARemnantForAWhile(GameTestHelper helper) {
		floor(helper);
		RemnantEntity f = forgotten(helper, new BlockPos(4, 1, 4));
		f.setPersistenceRequired();   // a Forgotten despawns like any monster when no player is around
		f.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		helper.assertTrue(f.stripArmor(20) == EquipmentSlot.CHEST, "the only piece it wore");
		helper.assertTrue(f.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && f.hasStrippedArmor(), "torn off");
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(f.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE) && !f.hasStrippedArmor(), "it floats back on");
			f.discard();
			helper.succeed();
		});
	}

	// ---- the Belfry and the Night of Memory ----------------------------------------------------------------

	private static BelfryBlockEntity belfry(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		BlockPos heart = helper.absolutePos(new BlockPos(3, 1, 3));
		for (MultiblockPattern.Part part : Multiblocks.belfry().parts()) {
			level.setBlock(Multiblocks.belfry().at(heart, part, 0), Multiblocks.belfry().ghost(part, 0), 3);
		}
		level.setBlock(heart, ModBlocks.REST_STONE.defaultBlockState(), 3);
		return (BelfryBlockEntity) level.getBlockEntity(heart);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_belfry", timeoutTicks = 60)
	public void belfryLocksAndOnlyRingsAtNight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BelfryBlockEntity b = belfry(helper);
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Sexton");
		ItemStack soul = new ItemStack(ModItems.CONDENSED_SOUL, 2);
		helper.assertFalse(b.tryStartNight(level, player, soul), "not locked yet");
		level.setBlock(b.getBlockPos().above(4), Blocks.AIR.defaultBlockState(), 3);
		helper.assertFalse(b.tryAssemble(level, player), "a roof slab is missing");
		level.setBlock(b.getBlockPos().above(4), Blocks.DARK_OAK_SLAB.defaultBlockState(), 3);
		helper.assertTrue(b.tryAssemble(level, player), "the belfry locks");
		long before = level.getDayTime();
		level.setDayTime(6000);
		helper.assertFalse(b.tryStartNight(level, player, soul), "noon: the dead do not answer");
		helper.assertTrue(soul.getCount() == 2, "nothing spent by day");
		level.setDayTime(before);
		remove(helper, player);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_night", timeoutTicks = 900)
	public void nightOfMemoryRunsThreeWavesToItsReward(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BelfryBlockEntity b = belfry(helper);
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Vigil");
		helper.assertTrue(b.tryAssemble(level, player), "locked");
		long before = level.getDayTime();
		level.setDayTime(14000);
		ItemStack soul = new ItemStack(ModItems.CONDENSED_SOUL);
		helper.assertTrue(b.tryStartNight(level, player, soul), "the bell calls the night");
		helper.assertTrue(soul.isEmpty(), "the soul is spent");
		helper.assertTrue(b.phase() == BelfryBlockEntity.WAVE1, "wave 1");
		boolean[] crowned = {false}, keener = {false};
		int[] seen = {0, 0, 0, 0};
		helper.onEachTick(() -> {
			int wave = BelfryBlockEntity.waveOf(b.phase());
			for (UUID id : List.copyOf(b.waveMobs())) {
				Entity e = level.getEntity(id);
				if (e == null) {
					continue;
				}
				seen[wave]++;
				crowned[0] |= wave == 2 && e instanceof RemnantEntity r && r.isEmpowered();
				keener[0] |= wave == 3 && e instanceof KeenerEntity;
				e.discard();   // laid to rest (they rise in chunks the test world does not tick)
			}
			if (b.phase() == BelfryBlockEntity.BREAK1 || b.phase() == BelfryBlockEntity.BREAK2) {
				b.jumpTo(level, b.phase() + 1);   // skip the respite
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(b.phase() == 0, "the night ends (phase " + b.phase() + ")");
			helper.assertTrue(seen[1] >= 4 && seen[2] >= 6 && seen[3] >= 9, "4, 6 and 8 + the Keener rose: " + java.util.Arrays.toString(seen));
			helper.assertTrue(crowned[0], "the second wave brought a Crowned one");
			helper.assertTrue(keener[0], "the third wave brought a Keener");
			helper.assertTrue(count(helper, new BlockPos(3, 2, 3), 4, ModItems.SOUL_FRAGMENT) >= 6, "Soul Fragments for the reward");
			helper.assertTrue(count(helper, new BlockPos(3, 2, 3), 4, ModItems.EPITAPH) == 1, "a hero's Epitaph");
			helper.assertTrue(b.lastNight() > 0, "the bell rests until tomorrow");
			level.setDayTime(before);
			remove(helper, player);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_night_dawn", timeoutTicks = 80)
	public void dawnSendsTheDeadBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BelfryBlockEntity b = belfry(helper);
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Latecomer");
		helper.assertTrue(b.tryAssemble(level, player), "locked");
		long before = level.getDayTime();
		level.setDayTime(14000);
		b.start(level, player);
		List<UUID> risen = new ArrayList<>();
		helper.runAfterDelay(30, () -> {
			risen.addAll(b.waveMobs());
			helper.assertTrue(!risen.isEmpty(), "the dead rose");
			level.setDayTime(24000L * (level.getDayTime() / 24000L + 1) + 1000);   // the next morning
		});
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(b.phase() == 0, "dawn ended the night");
			for (UUID id : risen) {
				helper.assertTrue(level.getEntity(id) == null, "the dead sank back");
			}
			level.setDayTime(before);
			remove(helper, player);
			helper.succeed();
		});
	}

	/** A crypt piece the jigsaw turns turns its gates with it: every gate's bars run along its own row, never across. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_gate_turned", timeoutTicks = 80)
	public void aTurnedPieceTurnsItsGates(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO).offset(60, 20, 420);
		net.minecraft.world.level.block.Rotation[] turns = net.minecraft.world.level.block.Rotation.values();
		List<ChunkPos> forced = new ArrayList<>();
		for (int x = (origin.getX() - 48) >> 4; x <= (origin.getX() + 60 * turns.length) >> 4; x++) {
			for (int z = (origin.getZ() - 48) >> 4; z <= (origin.getZ() + 48) >> 4; z++) {
				level.setChunkForced(x, z, true);
				forced.add(new ChunkPos(x, z));
			}
		}
		List<net.minecraft.world.level.levelgen.structure.BoundingBox> boxes = new ArrayList<>();
		helper.runAfterDelay(20, () -> {
			for (int i = 0; i < turns.length; i++) {
				BlockPos at = origin.offset(60 * i, 0, 0);
				StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(turns[i]);
				StructureTemplate t = template(level, "ossuary/vault");
				t.placeInWorld(level, at, at, settings, level.random, 2);
				boxes.add(t.getBoundingBox(settings, at));
			}
		});
		helper.runAfterDelay(30, () -> {
			for (int i = 0; i < turns.length; i++) {
				int gates = 0, across = 0;
				for (BlockPos p : BlockPos.betweenClosedStream(boxes.get(i)).map(BlockPos::immutable).toList()) {
					BlockState s = level.getBlockState(p);
					if (!s.is(ModBlocks.OSSUARY_GATE)) {
						continue;
					}
					gates++;
					for (Direction d : Direction.Plane.HORIZONTAL) {
						if (d.getAxis() != s.getValue(OssuaryGateBlock.AXIS) && level.getBlockState(p.relative(d)).is(ModBlocks.OSSUARY_GATE)) {
							across++;
						}
					}
				}
				helper.assertTrue(gates == 9 && across == 0, "vault turned " + turns[i] + ": " + gates + " gate blocks, " + across + " standing across their row");
			}
			forced.forEach(cp -> level.setChunkForced(cp.x, cp.z, false));
			helper.succeed();
		});
	}

	// ---- the Ossuary ---------------------------------------------------------------------------------------

	private static StructureTemplate template(ServerLevel level, String name) {
		return level.getStructureManager().get(RemnantsMod.id(name)).orElseThrow(() -> new AssertionError("missing template " + name));
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_templates", timeoutTicks = 120)
	public void ossuaryTemplatesPlaceGatesEyesAndTheirDwellers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO).offset(0, 20, 300);
		for (String name : new String[] {"ossuary/entrance", "ossuary/hub", "ossuary/corridor_skulls", "ossuary/corridor_burial",
			"ossuary/corner", "ossuary/junction", "ossuary/stairs_down", "ossuary/crypt_a", "ossuary/crypt_b", "ossuary/vault",
			"ossuary/warden_hall", "ossuary/dead_end", "belfry/ruin", "ossuary/bone_bridge", "ossuary/chapel", "ossuary/collapsed",
			"ossuary/entrance_desert", "ossuary/entrance_snowy", "ossuary/entrance_jungle", "belfry/graveyard", "belfry/graveyard_snowy",
			"belfry/graveyard_desert", "belfry/ruin_snowy", "belfry/ruin_desert"}) {
			helper.assertTrue(template(level, name).getSize().getX() > 0, "template " + name);
		}
		// the templates go well away from the other tests; their chunks are held loaded so the placed entities live
		List<ChunkPos> forced = new ArrayList<>();
		for (int x = origin.getX() >> 4; x <= (origin.getX() + 80) >> 4; x++) {
			for (int z = origin.getZ() >> 4; z <= (origin.getZ() + 40) >> 4; z++) {
				level.setChunkForced(x, z, true);
				forced.add(new ChunkPos(x, z));
			}
		}
		StructurePlaceSettings settings = new StructurePlaceSettings();
		BlockPos hall = origin;
		BlockPos vault = origin.offset(30, 0, 0);
		BlockPos crypt = origin.offset(50, 0, 0);
		BlockPos ruin = origin.offset(70, 0, 0);
		helper.runAfterDelay(40, () -> {
			template(level, "ossuary/warden_hall").placeInWorld(level, hall, hall, settings, level.random, 2);
			template(level, "ossuary/vault").placeInWorld(level, vault, vault, settings, level.random, 2);
			template(level, "ossuary/crypt_b").placeInWorld(level, crypt, crypt, settings, level.random, 2);
			template(level, "belfry/ruin").placeInWorld(level, ruin, ruin, settings, level.random, 2);
		});
		// sizes from the templates themselves (worldgen 3.0 made every room bigger)
		net.minecraft.core.Vec3i hs = template(level, "ossuary/warden_hall").getSize();
		net.minecraft.core.Vec3i vs = template(level, "ossuary/vault").getSize();
		net.minecraft.core.Vec3i cs = template(level, "ossuary/crypt_b").getSize();
		helper.assertTrue(hs.getX() + 5 <= 30 && vs.getX() + 5 <= 20 && cs.getX() + 1 <= 20, "the test's spacing fits the templates");
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(blocks(level, hall, hs.getX(), hs.getY(), hs.getZ(), ModBlocks.OSSUARY_GATE) == 9, "the treasury gate, 3 x 3");
			helper.assertTrue(blocks(level, hall, hs.getX(), hs.getY(), hs.getZ(), ModBlocks.CRYPT_EYE) == 0, "no eyes guard the treasury");
			List<CryptWardenEntity> wardens = level.getEntitiesOfClass(CryptWardenEntity.class, new AABB(hall).expandTowards(hs.getX(), hs.getY(), hs.getZ()));
			helper.assertTrue(wardens.size() == 1 && wardens.get(0).isDormant(), "a sleeping Warden on its plinth");
			helper.assertTrue(blocks(level, vault, vs.getX(), vs.getY(), vs.getZ(), ModBlocks.CRYPT_EYE) == 3
				&& blocks(level, vault, vs.getX(), vs.getY(), vs.getZ(), ModBlocks.OSSUARY_GATE) == 9, "the vault: three eyes and a gate");
			List<GraveMimicEntity> mimics = level.getEntitiesOfClass(GraveMimicEntity.class, new AABB(crypt).expandTowards(cs.getX(), cs.getY(), cs.getZ()));
			helper.assertTrue(mimics.size() == 2 && mimics.stream().allMatch(GraveMimicEntity::isDormant), "two sleeping mimics among the urns");
			helper.assertTrue(blocks(level, crypt, cs.getX(), cs.getY(), cs.getZ(), ModBlocks.OSSUARY_URN) == 6, "and six real urns");
			helper.assertTrue(blocks(level, ruin, 9, 7, 9, ModBlocks.CRACKED_BELL_OF_REST) == 1 && blocks(level, ruin, 9, 7, 9, ModBlocks.REST_STONE) == 1,
				"the ruined belfry: a cracked bell over the Rest Stone");
			wardens.forEach(Entity::discard);
			mimics.forEach(Entity::discard);
			forced.forEach(cp -> level.setChunkForced(cp.x, cp.z, false));
			helper.succeed();
		});
	}

	/** A vault placed the way worldgen places it (a jigsaw piece, turned, with the chunk's structure references): while its
	 * gate is shut a survival player cannot dig round the gate and TNT does not breach it; the antechamber is ordinary
	 * stone; once the gate rises the vault's walls break again. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_treasury_ward", timeoutTicks = 100)
	public void sealedTreasuryCannotBeDugRound(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO).offset(140, 20, 300);
		var stm = level.getStructureManager();
		var element = net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement
			.single(RemnantsMod.id("ossuary/vault").toString()).apply(StructureTemplatePool.Projection.RIGID);
		net.minecraft.world.level.block.Rotation rot = net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
		PoolElementStructurePiece piece = new PoolElementStructurePiece(stm, element, origin, 0, rot,
			element.getBoundingBox(stm, origin, rot), net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings.IGNORE_WATERLOGGING);
		var box = piece.getBoundingBox();
		List<ChunkPos> forced = new ArrayList<>();
		for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
			for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
				level.setChunkForced(x, z, true);
				forced.add(new ChunkPos(x, z));
			}
		}
		Structure ossuary = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(RemnantsMod.id("ossuary"));
		ChunkPos home = new ChunkPos(origin);
		StructureStart start = new StructureStart(ossuary, home, 0,
			new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(List.of(piece)));
		java.util.function.Function<BlockPos, BlockPos> at = local ->
			origin.offset(StructureTemplate.transform(local, net.minecraft.world.level.block.Mirror.NONE, rot, BlockPos.ZERO));
		helper.runAfterDelay(20, () -> {
			piece.place(level, level.structureManager(), level.getChunkSource().getGenerator(), level.random,
				net.minecraft.world.level.levelgen.structure.BoundingBox.infinite(), origin, false);
			level.getChunk(home.x, home.z).setStartForStructure(ossuary, start);
			for (ChunkPos cp : forced) {
				level.getChunk(cp.x, cp.z).addReferenceForStructure(ossuary, home.toLong());
			}
		});
		helper.runAfterDelay(30, () -> {
			ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Burglar");
			BlockPos gate = at.apply(new BlockPos(7, 2, 12));
			BlockPos beside = at.apply(new BlockPos(5, 2, 12));       // the wall right next to the gate
			BlockPos floor = at.apply(new BlockPos(7, 0, 17));        // the vault's floor, dug at from below
			BlockPos ante = at.apply(new BlockPos(0, 2, 5));          // the antechamber's outer wall: not a treasury
			helper.assertTrue(level.getBlockState(gate).is(ModBlocks.OSSUARY_GATE), "the vault's gate where the turned template put it");
			helper.assertTrue(!level.getBlockState(beside).isAir() && !level.getBlockState(floor).isAir() && !level.getBlockState(ante).isAir(),
				"solid walls to dig at");
			helper.assertTrue(!player.gameMode.destroyBlock(beside) && !level.getBlockState(beside).isAir(), "no digging round a shut gate");
			helper.assertTrue(!player.gameMode.destroyBlock(floor) && !level.getBlockState(floor).isAir(), "nor up through the vault's floor");
			helper.assertTrue(player.gameMode.destroyBlock(ante) && level.getBlockState(ante).isAir(), "the antechamber's walls break as ever");
			BlockPos blast = at.apply(new BlockPos(7, 2, 10));        // in the antechamber, two blocks before the gate
			level.explode(null, blast.getX() + 0.5, blast.getY() + 0.5, blast.getZ() + 0.5, 6.0F, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
			helper.assertTrue(!level.getBlockState(beside).isAir() && level.getBlockState(gate).is(ModBlocks.OSSUARY_GATE), "TNT does not breach it either");
			OssuaryGateBlock.open(level, gate);
			helper.assertTrue(player.gameMode.destroyBlock(beside) && level.getBlockState(beside).isAir(), "once the gate rises, the vault is plain stone");
			level.getServer().getPlayerList().remove(player);
			forced.forEach(cp -> level.setChunkForced(cp.x, cp.z, false));
			helper.succeed();
		});
	}

	private static int blocks(ServerLevel level, BlockPos from, int sx, int sy, int sz, net.minecraft.world.level.block.Block block) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(from, from.offset(sx - 1, sy - 1, sz - 1))) {
			n += level.getBlockState(p).is(block) ? 1 : 0;
		}
		return n;
	}

	/** The jigsaw assembly itself (the real structure is placed 11 under the surface, below a flat test world's floor,
	 * so this builds the same pools from a fixed height): it must reach the hub and always the Warden's hall. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "crypt_jigsaw", timeoutTicks = 100)
	public void ossuaryJigsawAlwaysReachesTheWarden(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Structure real = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(RemnantsMod.id("ossuary"));
		helper.assertTrue(real != null, "the ossuary structure is registered");
		helper.assertTrue(level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(RemnantsMod.id("ruined_belfry")) != null, "and the belfry");
		Holder<StructureTemplatePool> start = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL)
			.getHolderOrThrow(ResourceKey.create(Registries.TEMPLATE_POOL, RemnantsMod.id("ossuary/entrance")));
		JigsawStructure probe = new JigsawStructure(new Structure.StructureSettings(real.biomes(), real.spawnOverrides(), real.step(),
			real.terrainAdaptation()), start, 6, ConstantHeight.of(VerticalAnchor.absolute(-20)), false);
		ChunkGenerator gen = level.getChunkSource().getGenerator();
		int hubs = 0, wardens = 0, tries = 6, total = 0;
		for (int i = 0; i < tries; i++) {
			ChunkPos cp = new ChunkPos(400 + i * 20, 400);
			StructureStart s = probe.generate(level.registryAccess(), gen, gen.getBiomeSource(), level.getChunkSource().randomState(),
				level.getStructureManager(), level.getSeed() + i, cp, 0, level, b -> true);
			helper.assertTrue(s.isValid(), "the jigsaw assembles (try " + i + ")");
			boolean hub = false, warden = false;
			for (StructurePiece piece : s.getPieces()) {
				String el = piece instanceof PoolElementStructurePiece pe ? pe.getElement().toString() : "";
				hub |= el.contains("ossuary/hub");
				warden |= el.contains("ossuary/warden_hall");
			}
			hubs += hub ? 1 : 0;
			wardens += warden ? 1 : 0;
			total += s.getPieces().size();
		}
		helper.assertTrue(hubs == tries && wardens == tries, "every ossuary has its hub and Warden: " + hubs + " / " + wardens);
		helper.assertTrue(total >= tries * 5, "and branches out: " + total + " pieces in " + tries);
		helper.succeed();
	}
}
