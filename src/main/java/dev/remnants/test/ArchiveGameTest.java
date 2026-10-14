package dev.remnants.test;

import dev.remnants.AnchorNetwork;
import dev.remnants.Chronicle;
import dev.remnants.DeathHandler;
import dev.remnants.LastRecord;
import dev.remnants.PathBranch;
import dev.remnants.RemnantTracker;
import dev.remnants.RemnantsConfig;
import dev.remnants.RemnantsMod;
import dev.remnants.SoulAllies;
import dev.remnants.SoulResonance;
import dev.remnants.block.entity.AstrolabeBlockEntity;
import dev.remnants.block.entity.EpitaphNicheBlockEntity;
import dev.remnants.block.entity.MemorialBlockEntity;
import dev.remnants.crafting.CrucibleRecipes;
import dev.remnants.entity.ArchiveEyeEntity;
import dev.remnants.entity.ArchivePageEntity;
import dev.remnants.entity.ArchivistEntity;
import dev.remnants.entity.DecoyEchoEntity;
import dev.remnants.entity.FlyingTomeEntity;
import dev.remnants.entity.RemnantEntity;
import dev.remnants.entity.ThrownDecoyEntity;
import dev.remnants.item.ArchivistQuillItem;
import dev.remnants.item.EpitaphData;
import dev.remnants.item.EpitaphItem;
import dev.remnants.item.ShroudGliderItem;
import dev.remnants.item.SoulLanternItem;
import dev.remnants.multiblock.MultiblockPattern;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.registry.ModBlocks;
import dev.remnants.registry.ModComponents;
import dev.remnants.registry.ModEntities;
import dev.remnants.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
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

/**
 * 1.5 "Archive": the Archivist's sleep, barrier, eyes and spoils, its tomes and Pages; the Quill and the Seal of Memory;
 * the Memorial, its niches and the last record; the Soul Astrolabe; the branches of the Path; the anchor network; the
 * glider's updrafts; the Soul Lantern and its allies; the Decoy Echo; the Library's templates and jigsaw.
 */
public class ArchiveGameTest implements FabricGameTest {

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	private static void remove(GameTestHelper helper, ServerPlayer... players) {
		for (ServerPlayer p : players) {
			helper.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	private static long count(GameTestHelper helper, Vec3 c, double r, net.minecraft.world.item.Item item) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(c, c).inflate(r), e -> e.getItem().is(item))
			.stream().mapToLong(e -> e.getItem().getCount()).sum();
	}

	private static Holder<Enchantment> ench(ServerLevel level, ResourceKey<Enchantment> key) {
		return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
	}

	private static ArchivistEntity archivist(GameTestHelper helper, BlockPos at, boolean awake) {
		ServerLevel level = helper.getLevel();
		BlockPos p = helper.absolutePos(at);
		ArchivistEntity a = ModEntities.ARCHIVIST.create(level);
		a.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 180.0F, 0.0F);
		a.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.SPAWN_EGG, null);
		level.addFreshEntity(a);
		if (awake) {
			a.showAwake();
		}
		return a;
	}

	// ---- the Archivist ---------------------------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_sleep", timeoutTicks = 40)
	public void archivistReadsUntilDisturbed(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		ArchivistEntity a = archivist(helper, new BlockPos(4, 1, 4), false);
		helper.assertTrue(a.isDormant(), "it reads at first");
		helper.assertTrue(a.getMaxHealth() == (float) RemnantsConfig.get().archivistHealth, "health from the config: " + a.getMaxHealth());
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Reader");
		player.teleportTo(level, a.getX() + 30, a.getY(), a.getZ(), 0, 0);
		helper.assertFalse(a.hurt(level.damageSources().playerAttack(player), 20.0F), "a reading Archivist takes nothing");
		helper.assertTrue(a.getHealth() == a.getMaxHealth(), "whole");
		helper.assertFalse(a.isDormant(), "but the blow wakes it");
		a.discard();
		remove(helper, player);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_eyes", timeoutTicks = 240)
	public void barrierHoldsUntilEveryEyeIsShut(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		ArchivistEntity a = archivist(helper, new BlockPos(4, 1, 4), true);
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Archer");
		player.teleportTo(level, a.getX() + 40, a.getY(), a.getZ(), 0, 0);   // out of its sight: nothing to hunt
		a.forcePhase2();
		helper.assertTrue(a.phase() == 2 && a.barrier() == 4 && a.isShielded(), "phase 2: the barrier is up");
		helper.assertFalse(a.hurt(level.damageSources().playerAttack(player), 30.0F), "nothing gets through the barrier");
		helper.runAfterDelay(30, () -> {
			List<ArchiveEyeEntity> eyes = a.eyes();
			helper.assertTrue(eyes.size() == 4 && eyes.stream().allMatch(ArchiveEyeEntity::isOpen), "four open eyes: " + eyes.size());
			ArchiveEyeEntity first = eyes.get(0);
			helper.assertFalse(first.hurt(level.damageSources().playerAttack(player), 5.0F), "a blade does not shut an eye");
			Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
			helper.assertTrue(first.hurt(level.damageSources().arrow(arrow, player), 2.0F), "an arrow does");
			helper.assertFalse(first.isOpen(), "the eye is shut");
			helper.assertTrue(a.barrier() == 3 && a.isStunned(), "an arc broke and the Archivist reels");
			a.invulnerableTime = 0;
			float before = a.getHealth();
			helper.assertTrue(a.hurt(level.damageSources().playerAttack(player), 20.0F), "stunned, it can be hurt");
			helper.assertTrue(a.getHealth() < before, "and it is: " + a.getHealth());
			for (int i = 1; i < eyes.size(); i++) {
				Arrow next = new Arrow(level, player, new ItemStack(Items.ARROW), null);
				eyes.get(i).hurt(level.damageSources().arrow(next, player), 2.0F);
			}
			helper.assertTrue(a.barrier() == 0 && a.getAnim() == ArchivistEntity.ANIM_SHATTER, "the fourth eye shatters the barrier");
			a.invulnerableTime = 0;
			a.hurt(level.damageSources().playerAttack(player), 10000.0F);
			helper.assertTrue(a.isAlive() && a.getHealth() >= a.getMaxHealth() * ArchivistEntity.FLOOR - 0.01F,
				"no dying in phase 2: " + a.getHealth());
		});
		helper.runAfterDelay(30 + ArchivistEntity.SHATTER + 10, () -> {
			helper.assertTrue(a.phase() == 3, "after the shatter: phase 3, the constellation");
			helper.assertTrue(a.eyes().isEmpty(), "the eyes are gone");
			a.discard();
			remove(helper, player);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_leash", timeoutTicks = 420)
	public void archivistDoesNotResetWhileItsFoeIsClose(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		ArchivistEntity a = archivist(helper, new BlockPos(2, 1, 4), true);
		for (int z = 0; z < 8; z++) {   // a wall between them: out of sight
			for (int y = 1; y < 6; y++) {
				helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
			}
		}
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Hidden");
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 1000, 4));
		BlockPos pp = helper.absolutePos(new BlockPos(7, 1, 4));
		player.teleportTo(level, pp.getX() + 0.5, pp.getY(), pp.getZ() + 0.5, 0, 0);
		a.hurt(level.damageSources().generic(), 50.0F);
		float hurt = a.getHealth();
		a.setTarget(null);
		helper.runAfterDelay(ArchivistEntity.LOST_TICKS + 60, () -> {
			helper.assertFalse(a.isDormant(), "it did not go back to reading");
			helper.assertTrue(a.getHealth() <= hurt, "nor heal: " + a.getHealth());
			helper.assertTrue(a.getTarget() == player, "it took its foe up again");
			a.discard();
			remove(helper, player);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_spoils", timeoutTicks = 60)
	public void archivistLeavesItsQuillAndSeal(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		ArchivistEntity a = archivist(helper, new BlockPos(4, 1, 4), true);
		Vec3 at = a.position();
		a.hurt(level.damageSources().genericKill(), 100000.0F);
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(!a.isAlive(), "it fell");
			helper.assertTrue(count(helper, at, 4, ModItems.ARCHIVIST_QUILL) == 1, "the Quill");
			helper.assertTrue(count(helper, at, 4, ModItems.SEAL_OF_MEMORY) == 1, "the Seal of Memory");
			helper.assertTrue(count(helper, at, 4, ModItems.SOUL_FRAGMENT) >= 8, "and souls");
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(5))) {
				e.discard();
			}
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_tome", timeoutTicks = 120)
	public void flyingTomeTurnsAndComesBack(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		ArchivistEntity a = archivist(helper, new BlockPos(1, 1, 1), true);
		a.setNoAi(true);
		for (int x = 0; x < 8; x++) {   // a wall to turn it early, well inside the test's chunks
			for (int y = 1; y < 6; y++) {
				helper.setBlock(new BlockPos(x, y, 6), Blocks.STONE);
			}
		}
		FlyingTomeEntity tome = FlyingTomeEntity.throwAt(level, a, a.position().add(0, 2.3, 0), new Vec3(0.3, 0, 1));
		helper.runAfterDelay(30, () -> helper.assertTrue(tome.isReturning() || tome.isRemoved(), "it turned back"));
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(tome.isRemoved(), "and its thrower caught it");
			a.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_page", timeoutTicks = 20)
	public void pagesBurnTwiceAsFast(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		ArchivePageEntity page = ModEntities.ARCHIVE_PAGE.create(level);
		BlockPos p = helper.absolutePos(new BlockPos(4, 2, 4));
		page.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
		level.addFreshEntity(page);
		page.hurt(level.damageSources().inFire(), 2.0F);
		helper.assertTrue(page.getHealth() == 4.0F, "fire took twice: " + page.getHealth());
		page.discard();
		helper.succeed();
	}

	// ---- the Quill and the Seal ----------------------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_quill", timeoutTicks = 20)
	public void quillCopiesEnchantmentsOncePerDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Scribe");
		ItemStack quill = new ItemStack(ModItems.ARCHIVIST_QUILL);
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.enchant(ench(level, Enchantments.SHARPNESS), 4);
		sword.enchant(ench(level, Enchantments.VANISHING_CURSE), 1);
		helper.assertTrue(ArchivistQuillItem.write(player, quill, sword) == ArchivistQuillItem.Result.NO_BOOK, "no book, no writing");
		player.getInventory().add(new ItemStack(Items.BOOK, 2));
		helper.assertTrue(ArchivistQuillItem.write(player, quill, sword) == ArchivistQuillItem.Result.WRITTEN, "written");
		helper.assertTrue(sword.getEnchantments().size() == 2, "the sword keeps its own");
		ItemStack book = ItemStack.EMPTY;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(Items.ENCHANTED_BOOK)) {
				book = player.getInventory().getItem(i);
			}
		}
		var stored = book.get(DataComponents.STORED_ENCHANTMENTS);
		helper.assertTrue(stored != null && stored.size() == 1 && stored.getLevel(ench(level, Enchantments.SHARPNESS)) == 4,
			"the book holds Sharpness IV and no curse");
		helper.assertTrue(player.getInventory().countItem(Items.BOOK) == 1, "one blank book used");
		helper.assertTrue(ArchivistQuillItem.write(player, quill, sword) == ArchivistQuillItem.Result.DRY, "once a day");
		quill.set(ModComponents.QUILL_WRITTEN, level.getGameTime() - RemnantsConfig.get().quillCooldown - 1);
		helper.assertTrue(ArchivistQuillItem.write(player, quill, book.copy()) == ArchivistQuillItem.Result.BOOK_COPY, "never a book");
		quill.setDamageValue(quill.getMaxDamage());
		helper.assertTrue(ArchivistQuillItem.write(player, quill, sword) == ArchivistQuillItem.Result.BLUNT, "a blunt quill does not write");
		helper.assertTrue(!quill.isEmpty() && quill.getItem() == ModItems.ARCHIVIST_QUILL, "but it is still a quill");
		helper.assertTrue(ModItems.ARCHIVIST_QUILL.isValidRepairItem(quill, new ItemStack(ModItems.CONDENSED_SOUL)), "sharpened with a Condensed Soul");
		remove(helper, player);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_seal", timeoutTicks = 20)
	public void sealOfMemoryKeepsAnItemThroughDeath(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		CrucibleRecipes.Recipe r = CrucibleRecipes.match(sword, List.of(new ItemStack(ModItems.SEAL_OF_MEMORY), new ItemStack(ModItems.CONDENSED_SOUL)));
		helper.assertTrue(r != null && r.kind() == CrucibleRecipes.Kind.REMEMBER, "the crucible knows the Remembrance");
		ItemStack remembered = CrucibleRecipes.forge(r, sword, level.registryAccess());
		helper.assertTrue(remembered.has(ModComponents.REMEMBERED), "the sword is Remembered");
		helper.assertTrue(CrucibleRecipes.match(remembered, List.of(new ItemStack(ModItems.SEAL_OF_MEMORY), new ItemStack(ModItems.CONDENSED_SOUL))) == null,
			"no second seal");
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Keeper");
		player.getInventory().setItem(3, remembered);
		player.getInventory().setItem(4, new ItemStack(Items.DIRT, 10));
		DeathHandler.onPlayerDeath(player, level.damageSources().generic());
		helper.assertTrue(player.getInventory().getItem(3).isEmpty(), "taken off the body");
		var kept = RemnantTracker.get(level.getServer()).takeSoulbound(player.getUUID());
		boolean found = false;
		for (int i = 0; kept != null && i < kept.size(); i++) {
			found |= ItemStack.parse(level.registryAccess(), kept.getCompound(i).getCompound("Item")).map(s -> s.has(ModComponents.REMEMBERED)).orElse(false);
		}
		helper.assertTrue(found, "the Remembered sword waits for the respawn instead of rising in the Remnant");
		for (RemnantEntity e : level.getEntitiesOfClass(RemnantEntity.class, new AABB(player.blockPosition()).inflate(8))) {
			e.discard();
		}
		remove(helper, player);
		helper.succeed();
	}

	// ---- the Memorial and the last record ----------------------------------------------------------------------------

	private static MemorialBlockEntity memorial(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		BlockPos heart = helper.absolutePos(new BlockPos(3, 1, 2));
		MultiblockPattern pattern = Multiblocks.memorial();
		for (MultiblockPattern.Part part : pattern.parts()) {
			level.setBlock(pattern.at(heart, part, 0), pattern.ghost(part, 0), 3);
		}
		level.setBlock(heart, ModBlocks.MEMORIAL_PROJECTOR.defaultBlockState(), 3);
		return (MemorialBlockEntity) level.getBlockEntity(heart);
	}

	private static EpitaphData epitaph(String name, int day) {
		return new EpitaphData(name, Component.literal("fell to a test #" + day), "minecraft:overworld", new BlockPos(day, 64, 0), 3, 1, day, false);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_memorial", timeoutTicks = 40)
	public void fullMemorialWallAddsTenPercent(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MemorialBlockEntity m = memorial(helper);
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Mourner");
		helper.assertTrue(m.tryAssemble(level, player), "the Memorial locks");
		List<BlockPos> niches = new ArrayList<>();
		for (MultiblockPattern.Part part : m.pattern().parts()) {
			niches.add(m.pattern().at(m.getBlockPos(), part, m.rotation()));
		}
		helper.assertTrue(level.getBlockState(niches.get(0)).getValue(dev.remnants.block.EpitaphNicheBlock.FACING) == Direction.NORTH,
			"the niches face the projector");
		// a tablet set in by hand, the same one refused next to it
		ItemStack tablet = EpitaphItem.stack(epitaph("Ada", 1));
		BlockHitResult hit0 = new BlockHitResult(Vec3.atCenterOf(niches.get(0)), Direction.NORTH, niches.get(0), false);
		player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
		level.getBlockState(niches.get(0)).useItemOn(tablet, level, player, InteractionHand.MAIN_HAND, hit0);
		helper.assertTrue(tablet.isEmpty(), "the tablet went into the niche");
		helper.assertTrue(dev.remnants.block.EpitaphNicheBlock.onWall(level, niches.get(1), epitaph("Ada", 1)), "and the same epitaph would be twice");
		// chiselled from the Chronicle with an empty hand
		Chronicle.record(player, epitaph("Mourner", 7), Chronicle.DEATH);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		BlockHitResult hit1 = new BlockHitResult(Vec3.atCenterOf(niches.get(1)), Direction.NORTH, niches.get(1), false);
		level.getBlockState(niches.get(1)).useWithoutItem(level, player, hit1);
		EpitaphNicheBlockEntity second = (EpitaphNicheBlockEntity) level.getBlockEntity(niches.get(1));
		helper.assertTrue(second.epitaph() != null && second.epitaph().name().equals("Mourner") && !second.fromItem(), "chiselled from the Chronicle");
		helper.assertFalse(RemnantTracker.get(level.getServer()).hasFullMemorial(player.getUUID()), "two of fifteen: no bonus yet");
		for (int i = 2; i < niches.size(); i++) {
			((EpitaphNicheBlockEntity) level.getBlockEntity(niches.get(i))).set(epitaph("Name" + i, 10 + i), true);
		}
		m.refresh(level);
		helper.assertTrue(m.isFull() && RemnantTracker.get(level.getServer()).hasFullMemorial(player.getUUID()), "fifteen: the wall is full");
		int before = RemnantTracker.get(level.getServer()).resonance(player.getUUID()).points();
		SoulResonance.award(player, 10, "");
		int after = RemnantTracker.get(level.getServer()).resonance(player.getUUID()).points();
		helper.assertTrue(after - before == 11, "+10 % on the Path: " + (after - before));
		level.setBlock(niches.get(5), Blocks.AIR.defaultBlockState(), 3);
		helper.runAfterDelay(10, () -> {
			helper.assertFalse(RemnantTracker.get(level.getServer()).hasFullMemorial(player.getUUID()), "a broken wall gives nothing");
			remove(helper, player);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_record", timeoutTicks = 80)
	public void lastRecordRemembersTheKiller(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MemorialBlockEntity m = memorial(helper);
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Victim");
		m.tryAssemble(level, player);
		Zombie zombie = EntityType.ZOMBIE.create(level);
		BlockPos zp = helper.absolutePos(new BlockPos(6, 1, 6));
		zombie.moveTo(zp.getX() + 0.5, zp.getY(), zp.getZ() + 0.5);
		zombie.setNoAi(true);
		level.addFreshEntity(zombie);
		helper.assertFalse(LastRecord.has(player), "no record before a death");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_AXE));
		helper.runAfterDelay(30, () -> {
			LastRecord.capture(player, level.damageSources().mobAttack(zombie));
			var tag = RemnantTracker.get(level.getServer()).lastRecord(player.getUUID());
			helper.assertTrue(tag != null, "the death was recorded");
			helper.assertTrue(tag.getByteArray("Flags").length >= 10, "with ~1.5 s of frames already: " + tag.getByteArray("Flags").length);
			helper.assertTrue(tag.getString("Killer").equals("minecraft:zombie"), "and the killer: " + tag.getString("Killer"));
			float kx = Float.intBitsToFloat(tag.getIntArray("KillerPos")[tag.getIntArray("KillerPos").length - 3]);
			helper.assertTrue(!Float.isNaN(kx), "the zombie's track was seen");
			m.play(level, player);
			helper.assertTrue(m.isWorking(), "the projector plays it");
			zombie.discard();
			remove(helper, player);
			helper.succeed();
		});
	}

	// ---- the Astrolabe ----------------------------------------------------------------------------------------------

	private static AstrolabeBlockEntity astrolabe(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		BlockPos heart = helper.absolutePos(new BlockPos(2, 2, 2));
		MultiblockPattern pattern = Multiblocks.astrolabe();
		for (MultiblockPattern.Part part : pattern.parts()) {
			level.setBlock(pattern.at(heart, part, 0), pattern.ghost(part, 0), 3);
		}
		level.setBlock(heart, ModBlocks.SOUL_ASTROLABE.defaultBlockState(), 3);
		return (AstrolabeBlockEntity) level.getBlockEntity(heart);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_astrolabe", timeoutTicks = 80)
	public void astrolabePointsAtYourRemnant(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AstrolabeBlockEntity a = astrolabe(helper);
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Seeker");
		helper.assertTrue(a.tryAssemble(level, player), "the astrolabe locks");
		RemnantEntity remnant = ModEntities.REMNANT.create(level);
		BlockPos rp = helper.absolutePos(new BlockPos(7, 1, 7));
		remnant.moveTo(rp.getX() + 0.5, rp.getY(), rp.getZ() + 0.5);
		level.addFreshEntity(remnant);
		RemnantTracker.get(level.getServer()).spawned(player.getUUID(), remnant.getUUID(), level, rp);
		ItemStack compass = new ItemStack(ModItems.SPECTRAL_COMPASS);
		helper.assertTrue(a.insert(level, player, compass), "the compass rests on the sphere");
		helper.assertFalse(a.search(level, player), "no Soul Fragment, no search");
		player.getInventory().add(new ItemStack(ModItems.SOUL_FRAGMENT, 2));
		helper.assertTrue(a.search(level, player), "the rings turn");
		helper.assertTrue(player.getInventory().countItem(ModItems.SOUL_FRAGMENT) == 1, "one fragment spent");
		helper.assertTrue(a.target() != null && a.target().distanceTo(Vec3.atCenterOf(rp)) < 1.0 && a.kind().equals(AstrolabeBlockEntity.REMNANT),
			"aimed at the Remnant");
		helper.runAfterDelay(AstrolabeBlockEntity.SPIN + 5, () -> {
			helper.assertTrue(a.phase() == 2, "the rings locked: the beam holds");
			helper.assertTrue(a.takeFocus(level, player) && a.phase() == 0, "taking the focus puts the beam out");
			// an Epitaph points at its place of death
			EpitaphData d = new EpitaphData("Old", Component.literal("x"), level.dimension().location().toString(), rp.offset(40, 0, 0), 0, 0, 1, false);
			a.insert(level, player, EpitaphItem.stack(d));
			helper.assertTrue(a.search(level, player) && a.target().distanceTo(Vec3.atCenterOf(rp.offset(40, 0, 0))) < 1.0, "the epitaph's place");
			remnant.discard();
			RemnantTracker.get(level.getServer()).forget(player.getUUID(), remnant.getUUID());
			remove(helper, player);
			helper.succeed();
		});
	}

	// ---- the branches -------------------------------------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_branches", timeoutTicks = 20)
	public void branchesOpenAtRankThreeAndCostAHeartToChange(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Pilgrim");
		helper.assertTrue(PathBranch.choose(player, PathBranch.Branch.REAPER) == PathBranch.Result.RANK, "rank 0: no branch yet");
		RemnantTracker.get(level.getServer()).addResonance(player.getUUID(), 30, "");
		helper.assertTrue(PathBranch.choose(player, PathBranch.Branch.REAPER) == PathBranch.Result.CHOSEN, "rank III: Reaper, free");
		helper.assertTrue(PathBranch.of(player) == PathBranch.Branch.REAPER, "stored");
		// the Reaper's swing against the undead
		Zombie zombie = EntityType.ZOMBIE.create(level);
		zombie.moveTo(player.getX() + 1, player.getY(), player.getZ());
		level.addFreshEntity(zombie);
		double base = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		PathBranch.onAttack(player, level, InteractionHand.MAIN_HAND, zombie, null);
		double armed = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		helper.assertTrue(Math.abs(armed - base * 1.15) < 1.0E-4, "+15 % against the undead: " + base + " -> " + armed);
		PathBranch.endTick(level.getServer());
		helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_DAMAGE) - base) < 1.0E-4, "for one swing only");
		helper.assertTrue(PathBranch.choose(player, PathBranch.Branch.WANDERER) == PathBranch.Result.NEEDS_HEART, "changing takes a Heart");
		player.getInventory().add(new ItemStack(ModItems.SOUL_KING_HEART));
		helper.assertTrue(PathBranch.choose(player, PathBranch.Branch.WANDERER) == PathBranch.Result.CHANGED, "with a Heart: Wanderer");
		helper.assertTrue(player.getInventory().countItem(ModItems.SOUL_KING_HEART) == 0, "the Heart burnt away");
		helper.assertTrue(PathBranch.tetherRange(player) == PathBranch.WANDERER_TETHER_RANGE, "the Wanderer's tether reaches 32");
		helper.assertTrue(AnchorNetwork.cost(player) == 0, "and the anchors carry it for free");
		zombie.discard();
		remove(helper, player);
		helper.succeed();
	}

	// ---- the anchor network -------------------------------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_anchors", timeoutTicks = 80)
	public void anchorNetworkCarriesYouForASoulFragment(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos b = helper.absolutePos(new BlockPos(6, 1, 6));
		BlockPos c = helper.absolutePos(new BlockPos(6, 1, 1));
		for (BlockPos p : new BlockPos[] {a, b, c}) {
			level.setBlock(p, ModBlocks.SOUL_ANCHOR.defaultBlockState(), 3);
		}
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Traveller");
		helper.assertTrue(AnchorNetwork.attune(player, a, null) && AnchorNetwork.attune(player, b, "Home") && AnchorNetwork.attune(player, c, null),
			"three anchors attuned");
		helper.assertTrue(RemnantTracker.get(level.getServer()).anchors(player.getUUID()).get(1).name().equals("Home"), "one of them named");
		player.teleportTo(level, a.getX() + 0.5, a.getY() + 1, a.getZ() + 1.5, 0, 0);
		helper.assertFalse(AnchorNetwork.request(player, a, b), "no Soul Fragment, no jump");
		player.getInventory().add(new ItemStack(ModItems.SOUL_FRAGMENT, 1));
		helper.assertTrue(AnchorNetwork.request(player, a, b), "the jump begins");
		helper.assertTrue(player.getInventory().countItem(ModItems.SOUL_FRAGMENT) == 0, "a fragment spent");
		helper.runAfterDelay(AnchorNetwork.DISSOLVE + 5, () -> {
			helper.assertTrue(player.blockPosition().distManhattan(b.above()) <= 2, "gathered again at Home: " + player.blockPosition());
			level.setBlock(c, Blocks.AIR.defaultBlockState(), 3);
			RemnantsConfig.get().anchorTeleportCooldown = 0;
			player.getInventory().add(new ItemStack(ModItems.SOUL_FRAGMENT, 1));
			helper.assertFalse(AnchorNetwork.request(player, b, c), "a broken anchor cannot be reached");
			helper.assertFalse(RemnantTracker.get(level.getServer()).isLinked(player.getUUID(), level.dimension(), c), "and it left the network");
			RemnantsConfig.get().anchorTeleportCooldown = 200;
			remove(helper, player);
			helper.succeed();
		});
	}

	// ---- the glider, the lantern, the decoy ----------------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_glider", timeoutTicks = 20)
	public void warmAirRisesOverFires(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		BlockPos fire = helper.absolutePos(new BlockPos(2, 1, 2));
		level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 3);
		helper.assertTrue(ShroudGliderItem.updraft(level, fire.above(4)), "rising air over a lit campfire: " + level.getBlockState(fire)
			+ " reach " + RemnantsConfig.get().gliderUpdraftHeight + " heat " + ShroudGliderItem.isHeat(level.getBlockState(fire)));
		level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), 3);
		helper.assertFalse(ShroudGliderItem.updraft(level, fire.above(4)), "none over a dead one");
		BlockPos brazier = helper.absolutePos(new BlockPos(5, 1, 5));
		level.setBlock(brazier, ModBlocks.SOUL_BRAZIER.defaultBlockState(), 3);
		helper.assertTrue(ShroudGliderItem.updraft(level, brazier.above(5)), "over a soul brazier");
		level.setBlock(brazier.above(3), Blocks.STONE.defaultBlockState(), 3);
		helper.assertFalse(ShroudGliderItem.updraft(level, brazier.above(5)), "a roof stops it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_lantern", timeoutTicks = 40)
	public void soulLanternCatchesTheWeakAndFreesAnAlly(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Warden");
		ItemStack lantern = new ItemStack(ModItems.SOUL_LANTERN_TRAP);
		Zombie zombie = EntityType.ZOMBIE.create(level);
		BlockPos zp = helper.absolutePos(new BlockPos(4, 1, 4));
		zombie.moveTo(zp.getX() + 0.5, zp.getY(), zp.getZ() + 0.5);
		level.addFreshEntity(zombie);
		helper.assertTrue(SoulLanternItem.capture(player, lantern, zombie) == SoulLanternItem.Catch.HEALTHY, "too healthy to catch");
		zombie.setHealth(4.0F);
		helper.assertTrue(SoulLanternItem.capture(player, lantern, zombie) == SoulLanternItem.Catch.CAUGHT, "worn down: caught");
		helper.assertTrue(zombie.isRemoved() && SoulLanternItem.holds(lantern) && SoulLanternItem.heldType(lantern) == EntityType.ZOMBIE, "inside the lantern");
		RemnantEntity remnant = ModEntities.REMNANT.create(level);
		remnant.moveTo(zp.getX() + 0.5, zp.getY(), zp.getZ() + 2.5);
		remnant.setHealth(1.0F);
		helper.assertTrue(SoulLanternItem.canCatch(new ItemStack(ModItems.SOUL_LANTERN_TRAP), remnant) == SoulLanternItem.Catch.IMMUNE, "never a Remnant");
		var ally = SoulLanternItem.release(player, lantern, Vec3.atBottomCenterOf(zp));
		helper.assertTrue(ally != null && SoulAllies.isAlly(ally) && !SoulLanternItem.holds(lantern), "set free as an ally");
		helper.assertTrue(ally.getHealth() == ally.getMaxHealth(), "whole again");
		helper.assertFalse(player.hurt(level.damageSources().mobAttack(ally), 5.0F), "an ally never hurts a player");
		SoulAllies.dissolve(ally);
		helper.assertTrue(ally.isRemoved() && !SoulAllies.isAlly(ally), "it fades without a trace");
		remove(helper, player);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_decoy", timeoutTicks = 220)
	public void decoyDrawsMonstersAndEchoes(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Trickster");
		Zombie zombie = EntityType.ZOMBIE.create(level);
		BlockPos zp = helper.absolutePos(new BlockPos(6, 1, 6));
		zombie.moveTo(zp.getX() + 0.5, zp.getY(), zp.getZ() + 0.5);
		level.addFreshEntity(zombie);
		zombie.setTarget(player);
		RemnantEntity echo = ModEntities.FORGOTTEN_REMNANT.create(level);
		echo.moveTo(zp.getX() - 2.5, zp.getY(), zp.getZ() + 0.5);
		level.addFreshEntity(echo);
		echo.setTarget(player);
		DecoyEchoEntity decoy = ThrownDecoyEntity.spawn(level, player, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 2))));
		helper.assertTrue(zombie.getTarget() == decoy, "the zombie turns on the decoy");
		helper.assertTrue(echo.getTarget() == decoy, "and so does the Remnant");
		helper.assertTrue(decoy.getMainHandItem().isEmpty() == player.getMainHandItem().isEmpty(), "it holds what the thrower held");
		helper.runAfterDelay(RemnantsConfig.get().decoyTicks + 10, () -> {
			helper.assertTrue(decoy.isRemoved(), "its time ran out");
			zombie.discard();
			echo.discard();
			remove(helper, player);
			helper.succeed();
		});
	}

	// ---- the Library ----------------------------------------------------------------------------------------------------

	private static StructureTemplate template(ServerLevel level, String name) {
		return level.getStructureManager().get(RemnantsMod.id(name)).orElseThrow(() -> new AssertionError("missing template " + name));
	}

	private static int blocks(ServerLevel level, BlockPos from, int sx, int sy, int sz, net.minecraft.world.level.block.Block block) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(from, from.offset(sx - 1, sy - 1, sz - 1))) {
			n += level.getBlockState(p).is(block) ? 1 : 0;
		}
		return n;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_library", timeoutTicks = 120)
	public void libraryHallHoldsTheArchivistAndItsTreasury(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO).offset(0, 20, -300);
		for (String name : new String[] {"ossuary/entrance_library", "ossuary/hub_library", "ossuary/library_hall"}) {
			helper.assertTrue(template(level, name).getSize().getX() > 0, "template " + name);
		}
		List<ChunkPos> forced = new ArrayList<>();
		for (int x = origin.getX() >> 4; x <= (origin.getX() + 24) >> 4; x++) {
			for (int z = origin.getZ() >> 4; z <= (origin.getZ() + 30) >> 4; z++) {
				level.setChunkForced(x, z, true);
				forced.add(new ChunkPos(x, z));
			}
		}
		helper.runAfterDelay(40, () -> template(level, "ossuary/library_hall").placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.random, 2));
		helper.runAfterDelay(60, () -> {
			List<ArchivistEntity> found = level.getEntitiesOfClass(ArchivistEntity.class, new AABB(origin).expandTowards(23, 12, 28));
			helper.assertTrue(found.size() == 1 && found.get(0).isDormant(), "the Archivist reads at its lectern");
			helper.assertTrue(blocks(level, origin, 23, 12, 28, ModBlocks.OSSUARY_GATE) == 9, "the treasury gate, 3 x 3");
			helper.assertTrue(blocks(level, origin, 23, 12, 28, Blocks.BOOKSHELF) > 200, "shelves to the vault");
			helper.assertTrue(blocks(level, origin, 23, 12, 28, Blocks.LECTERN) >= 1, "and its lectern");
			found.forEach(e -> e.hurt(level.damageSources().genericKill(), 100000.0F));
		});
		helper.runAfterDelay(70, () -> {
			BlockPos gate = null;
			for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(22, 11, 27))) {
				if (level.getBlockState(p).is(ModBlocks.OSSUARY_GATE)) {
					gate = p.immutable();
					break;
				}
			}
			helper.assertTrue(gate != null && level.getBlockState(gate).getValue(dev.remnants.block.OssuaryGateBlock.OPEN), "its fall raised the treasury gate");
			for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(origin).expandTowards(23, 12, 28), e -> !(e instanceof ServerPlayer))) {
				e.discard();
			}
			forced.forEach(cp -> level.setChunkForced(cp.x, cp.z, false));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_jigsaw", timeoutTicks = 100)
	public void libraryJigsawAlwaysReachesTheLibrary(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Structure real = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(RemnantsMod.id("ossuary_library"));
		helper.assertTrue(real != null, "the library structure is registered");
		Holder<StructureTemplatePool> start = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL)
			.getHolderOrThrow(ResourceKey.create(Registries.TEMPLATE_POOL, RemnantsMod.id("ossuary/entrance_library")));
		JigsawStructure probe = new JigsawStructure(new Structure.StructureSettings(real.biomes(), real.spawnOverrides(), real.step(),
			real.terrainAdaptation()), start, 6, ConstantHeight.of(VerticalAnchor.absolute(-20)), false);
		ChunkGenerator gen = level.getChunkSource().getGenerator();
		int libraries = 0, wardens = 0, tries = 5;
		for (int i = 0; i < tries; i++) {
			ChunkPos cp = new ChunkPos(-400 - i * 20, 400);
			StructureStart s = probe.generate(level.registryAccess(), gen, gen.getBiomeSource(), level.getChunkSource().randomState(),
				level.getStructureManager(), level.getSeed() + 31 * i, cp, 0, level, b -> true);
			helper.assertTrue(s.isValid(), "the jigsaw assembles (try " + i + ")");
			for (StructurePiece piece : s.getPieces()) {
				String el = piece instanceof PoolElementStructurePiece pe ? pe.getElement().toString() : "";
				libraries += el.contains("ossuary/library_hall") ? 1 : 0;
				wardens += el.contains("ossuary/warden_hall") ? 1 : 0;
			}
		}
		helper.assertTrue(libraries == tries, "every library site has its Library: " + libraries);
		helper.assertTrue(wardens == 0, "and no Warden's hall");
		helper.succeed();
	}

	// ---- Carrion Crows ----------------------------------------------------------------------------------------------------

	/** A waiting Remnant of {@code owner} holding a sword (gear), dirt and diamonds (supplies). */
	private static RemnantEntity waitingRemnant(GameTestHelper helper, ServerPlayer owner) {
		ServerLevel level = helper.getLevel();
		RemnantEntity r = ModEntities.REMNANT.create(level);
		BlockPos p = helper.absolutePos(new BlockPos(4, 1, 4));
		r.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		r.rise(owner, new ArrayList<>(List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.DIRT, 32), new ItemStack(Items.DIAMOND, 3),
			new ItemStack(Items.BREAD, 5))), RemnantEntity.EquipmentSnapshot.of(owner), 0, false);
		level.addFreshEntity(r);
		return r;
	}

	private static List<dev.remnants.entity.CarrionCrowEntity> crows(GameTestHelper helper, RemnantEntity r) {
		return dev.remnants.CarrionCrows.flock(helper.getLevel(), r);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_crows", timeoutTicks = 200)
	public void crowsGatherOverAnAbandonedRemnantAndSnatchSupplies(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		dev.remnants.CarrionCrows.ignoreSky = true;
		ServerPlayer owner = RemnantsGameTest.survivalPlayer(helper, "Absent");
		RemnantEntity r = waitingRemnant(helper, owner);
		owner.teleportTo(level, r.getX() + 200, r.getY(), r.getZ(), 0, 0);
		dev.remnants.CarrionCrows.tick(r);
		helper.assertTrue(crows(helper, r).isEmpty(), "a fresh Remnant draws no crows");
		r.setAbandonedSince(level.getGameTime() - RemnantsConfig.get().crowDelayTicks - 1);
		dev.remnants.CarrionCrows.tick(r);
		List<dev.remnants.entity.CarrionCrowEntity> flock = crows(helper, r);
		helper.assertTrue(flock.size() >= RemnantsConfig.get().crowFlockMin && flock.size() <= RemnantsConfig.get().crowFlockMax,
			"ten minutes alone: a flock gathers (" + flock.size() + ")");
		helper.assertTrue(flock.stream().noneMatch(dev.remnants.entity.CarrionCrowEntity::isCarrying) && r.crowSteals() == 0,
			"they circle a while before the first dive");
		flock.forEach(c -> c.tickCount = 300);
		dev.remnants.CarrionCrows.tick(r);
		helper.assertTrue(r.crowSteals() == 1 && flock.stream().anyMatch(c -> c.state() == dev.remnants.entity.CarrionCrowEntity.SNATCH),
			"one crow dives");
		// the test box is walled and roofed with barriers: bring the diving crow inside it
		flock.stream().filter(c -> c.state() == dev.remnants.entity.CarrionCrowEntity.SNATCH)
			.forEach(c -> c.moveTo(r.getX() + 1.5, r.getY() + 2.5, r.getZ() + 1.5));
		helper.runAfterDelay(120, () -> {
			List<dev.remnants.entity.CarrionCrowEntity> now = crows(helper, r);
			helper.assertTrue(now.stream().filter(dev.remnants.entity.CarrionCrowEntity::isCarrying).count() == 1, "one crow holds a stack");
			ItemStack held = now.stream().filter(dev.remnants.entity.CarrionCrowEntity::isCarrying).findFirst().get().held();
			helper.assertTrue(!held.isDamageableItem(), "supplies, never gear: " + held);
			helper.assertTrue(r.getStoredItems().size() == 3 && r.getStoredItems().stream().anyMatch(s -> s.is(Items.IRON_SWORD)),
				"the Remnant lost one stack and kept its sword");
			now.forEach(Entity::discard);
			r.discard();
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(r.blockPosition()).inflate(40))) {
				e.discard();
			}
			dev.remnants.CarrionCrows.ignoreSky = false;
			remove(helper, owner);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_crows_backlog", timeoutTicks = 40)
	public void lateReturnFindsCrowsOnTheTreesAndShootingOneGivesItBack(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		dev.remnants.CarrionCrows.ignoreSky = true;
		ServerPlayer owner = RemnantsGameTest.survivalPlayer(helper, "Late");
		RemnantEntity r = waitingRemnant(helper, owner);
		owner.teleportTo(level, r.getX() + 6, r.getY(), r.getZ(), 0, 0);   // back, but long after
		r.setAbandonedSince(level.getGameTime() - RemnantsConfig.get().crowDelayTicks - 2 * RemnantsConfig.get().crowIntervalTicks - 1);
		dev.remnants.CarrionCrows.tick(r);
		List<dev.remnants.entity.CarrionCrowEntity> flock = crows(helper, r);
		helper.assertTrue(flock.size() == 3 && flock.stream().allMatch(c -> c.isCarrying() && c.state() == dev.remnants.entity.CarrionCrowEntity.PERCH),
			"three snatches fell due while nobody was here: three crows sit with loot (" + flock.size() + ")");
		helper.assertTrue(r.getStoredItems().size() == 1 && r.getStoredItems().get(0).is(Items.IRON_SWORD), "only the gear is left");
		helper.assertTrue(r.abandonedSince() == level.getGameTime(), "the owner is here: the clock starts over");
		dev.remnants.entity.CarrionCrowEntity first = flock.get(0);
		ItemStack held = first.held().copy();
		Vec3 at = first.position();
		first.hurt(level.damageSources().playerAttack(owner), 100.0F);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(count(helper, at, 3, held.getItem()) >= held.getCount(), "shot down: the stack falls");
			flock.forEach(Entity::discard);
			r.discard();
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(at)).inflate(40))) {
				e.discard();
			}
			dev.remnants.CarrionCrows.ignoreSky = false;
			remove(helper, owner);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "archive_crows_off", timeoutTicks = 20)
	public void noCrowsWhenTurnedOffOrForTheForgotten(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		dev.remnants.CarrionCrows.ignoreSky = true;
		ServerPlayer owner = RemnantsGameTest.survivalPlayer(helper, "Calm");
		RemnantEntity r = waitingRemnant(helper, owner);
		owner.teleportTo(level, r.getX() + 200, r.getY(), r.getZ(), 0, 0);
		r.setAbandonedSince(level.getGameTime() - RemnantsConfig.get().crowDelayTicks - 1);
		RemnantsConfig.get().carrionCrows = false;
		dev.remnants.CarrionCrows.tick(r);
		RemnantsConfig.get().carrionCrows = true;
		helper.assertTrue(crows(helper, r).isEmpty(), "carrionCrows off: no flock");
		RemnantEntity forgotten = ModEntities.FORGOTTEN_REMNANT.create(level);
		forgotten.moveTo(r.getX() + 2, r.getY(), r.getZ());
		level.addFreshEntity(forgotten);
		forgotten.setAbandonedSince(1);
		dev.remnants.CarrionCrows.tick(forgotten);
		helper.assertTrue(crows(helper, forgotten).isEmpty(), "the Forgotten hold nothing for anyone: no crows");
		r.discard();
		forgotten.discard();
		dev.remnants.CarrionCrows.ignoreSky = false;
		remove(helper, owner);
		helper.succeed();
	}
}
