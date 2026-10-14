package dev.remnants.test;

import dev.remnants.RemnantTracker;
import dev.remnants.RemnantsConfig;
import dev.remnants.block.SoulForgeBlock;
import dev.remnants.block.entity.ReflectingPoolBlockEntity;
import dev.remnants.block.entity.SoulCrucibleBlockEntity;
import dev.remnants.crafting.CrucibleRecipes;
import dev.remnants.crafting.EnchantTransfer;
import dev.remnants.item.ArsenalItem;
import dev.remnants.item.ForgeQuality;
import dev.remnants.item.GhostStep;
import dev.remnants.item.SpectralForge;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.multiblock.MultiblockPattern;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.registry.ModBlocks;
import dev.remnants.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 1.3 "Craft": multiblock assembly (and the hologram), the Reflecting Pool's rules, the Soul Crucible's rhythm and
 * recipes, anti-duplication when a structure breaks mid-craft, and the Mourner's Ghost Step in its place after the totem
 * and Last Breath.
 */
public class CraftingGameTest implements FabricGameTest {

	// ---- building ----------------------------------------------------------------------------------------

	/** Stone under the test box's inside, so water stays put. */
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	private static void build(ServerLevel level, MultiblockPattern pattern, BlockPos heart, int rot) {
		for (MultiblockPattern.Part part : pattern.parts()) {
			level.setBlock(pattern.at(heart, part, rot), pattern.ghost(part, rot), 3);
		}
	}

	private static ReflectingPoolBlockEntity pool(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		BlockPos heart = helper.absolutePos(new BlockPos(3, 1, 3));
		build(level, Multiblocks.pool(), heart, 0);
		level.setBlock(heart, ModBlocks.REFLECTING_BASIN.defaultBlockState(), 3);
		ReflectingPoolBlockEntity pool = (ReflectingPoolBlockEntity) level.getBlockEntity(heart);
		helper.assertTrue(pool != null && pool.tryAssemble(level, null), "the pool should lock");
		return pool;
	}

	private static SoulCrucibleBlockEntity crucible(GameTestHelper helper, BlockPos at, int rot) {
		ServerLevel level = helper.getLevel();
		BlockPos heart = helper.absolutePos(at);
		build(level, Multiblocks.crucible(), heart, rot);
		level.setBlock(heart, ModBlocks.SOUL_CRUCIBLE.defaultBlockState()
			.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, MultiblockPattern.rotate(Direction.NORTH, rot)), 3);
		return (SoulCrucibleBlockEntity) level.getBlockEntity(heart);
	}

	private static Holder<Enchantment> ench(ServerLevel level, net.minecraft.resources.ResourceKey<Enchantment> key) {
		Registry<Enchantment> reg = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		return reg.getHolderOrThrow(key);
	}

	private static ItemStack enchanted(ServerLevel level, net.minecraft.world.item.Item item, Object... pairs) {
		ItemStack stack = new ItemStack(item);
		ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		for (int i = 0; i < pairs.length; i += 2) {
			@SuppressWarnings("unchecked")
			net.minecraft.resources.ResourceKey<Enchantment> key = (net.minecraft.resources.ResourceKey<Enchantment>) pairs[i];
			m.set(ench(level, key), (Integer) pairs[i + 1]);
		}
		EnchantmentHelper.setEnchantments(stack, m.toImmutable());
		return stack;
	}

	private static int level(ItemStack stack, Holder<Enchantment> e) {
		return EnchantTransfer.enchantmentsOf(stack).getLevel(e);
	}

	// ---- assembly ----------------------------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "craft_assembly", timeoutTicks = 60)
	public void poolNeedsEveryBlockAndBreaksWhenOneGoes(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		BlockPos heart = helper.absolutePos(new BlockPos(3, 1, 3));
		build(level, Multiblocks.pool(), heart, 0);
		BlockPos corner = heart.offset(2, 0, -2);
		level.setBlock(corner, Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(heart, ModBlocks.REFLECTING_BASIN.defaultBlockState(), 3);
		ReflectingPoolBlockEntity pool = (ReflectingPoolBlockEntity) level.getBlockEntity(heart);
		helper.assertTrue(!pool.tryAssemble(level, null), "a brazier is missing: no lock");
		helper.assertTrue(!pool.isFormed() && pool.hologramAt() == level.getGameTime(), "the hologram shows what is missing");
		helper.assertTrue(Multiblocks.pool().missing(level, heart, 0).equals(List.of(corner)), "exactly the corner is missing");
		level.setBlock(corner, ModBlocks.SOUL_BRAZIER.defaultBlockState(), 3);
		helper.assertTrue(pool.tryAssemble(level, null) && pool.isFormed(), "complete: it locks");
		helper.assertTrue(level.getBlockState(corner).getValue(Multiblocks.FORMED) && level.getBlockState(heart).getValue(Multiblocks.FORMED),
			"locked blocks are formed");
		BlockPos brick = heart.offset(0, 0, 2);
		helper.assertTrue(level.getBlockState(brick).getValue(Multiblocks.FORMED), "the ring is formed");
		level.setBlock(brick, Blocks.AIR.defaultBlockState(), 3);
		helper.succeedWhen(() -> {
			helper.assertTrue(!pool.isFormed(), "a broken ring unlocks the pool");
			helper.assertTrue(!level.getBlockState(corner).getValue(Multiblocks.FORMED), "and its blocks go dark");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "craft_rotations")
	public void crucibleLocksInEveryRotation(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int rot = 0; rot < 4; rot++) {
			SoulCrucibleBlockEntity crucible = crucible(helper, new BlockPos(1 + (rot % 2) * 4, 2, 1 + (rot / 2) * 4), rot);
			helper.assertTrue(crucible.tryAssemble(level, null), "rotation " + rot + " locks");
			helper.assertTrue(crucible.rotation() == rot, "rotation " + rot + " recognised, got " + crucible.rotation());
			BlockState bellows = level.getBlockState(crucible.partPos(SoulCrucibleBlockEntity.BELLOWS));
			helper.assertTrue(bellows.getValue(Multiblocks.FORMED), "the bellows are part of it");
		}
		// and without its anvil it will not lock
		SoulCrucibleBlockEntity lone = crucible(helper, new BlockPos(1, 2, 1), 0);
		level.setBlock(lone.partPos(SoulCrucibleBlockEntity.ANVIL), Blocks.AIR.defaultBlockState(), 3);
		lone.disassemble(level);
		helper.assertTrue(!lone.tryAssemble(level, null), "no anvil, no forge");
		helper.succeed();
	}

	// ---- the Reflecting Pool ------------------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "craft_pool")
	public void poolMovesTheChosenEnchantment(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ReflectingPoolBlockEntity pool = pool(helper);
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Reflector");
		player.setExperienceLevels(20);
		player.getInventory().add(new ItemStack(ModItems.SOUL_FRAGMENT, 10));
		Holder<Enchantment> sharp = ench(level, Enchantments.SHARPNESS), unbreak = ench(level, Enchantments.UNBREAKING);
		pool.load(enchanted(level, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 3, Enchantments.UNBREAKING, 2), new ItemStack(Items.IRON_SWORD));
		helper.assertTrue(pool.options().size() == 2 && pool.selectedEnchantment().equals(sharp), "sharpness first (sorted by id)");
		pool.select(1);
		helper.assertTrue(pool.selectedEnchantment().equals(unbreak), "the wheel picks the next one");
		pool.select(-1);
		pool.onHammer(level, player);
		helper.assertTrue(pool.phase() == ReflectingPoolBlockEntity.RISE, "the rite begins");
		helper.assertTrue(player.experienceLevel == 20, "nothing is paid before the last phase");
		pool.finishNow(level);
		helper.assertTrue(level(pool.target(), sharp) == 3, "Sharpness III moved to the iron sword");
		helper.assertTrue(level(pool.source(), sharp) == 0 && level(pool.source(), unbreak) == 2, "the source keeps the rest");
		helper.assertTrue(player.experienceLevel == 11, "3 levels x III = 9 levels paid, has " + player.experienceLevel);
		helper.assertTrue(player.getInventory().countItem(ModItems.SOUL_FRAGMENT) == 7, "3 fragments paid");
		level.getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "craft_pool_rules")
	public void poolRulesCompatibilityMaximaCursesBooks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Holder<Enchantment> sharp = ench(level, Enchantments.SHARPNESS), smite = ench(level, Enchantments.SMITE),
			vanishing = ench(level, Enchantments.VANISHING_CURSE), unbreak = ench(level, Enchantments.UNBREAKING),
			mending = ench(level, Enchantments.MENDING);
		// incompatible: Smite onto a Sharpness sword, or onto a pickaxe
		ItemStack smiteBook = enchanted(level, Items.ENCHANTED_BOOK, Enchantments.SMITE, 4);
		helper.assertTrue(EnchantTransfer.check(enchanted(level, Items.IRON_SWORD, Enchantments.SMITE, 4),
			enchanted(level, Items.IRON_SWORD, Enchantments.SHARPNESS, 1), smite) == EnchantTransfer.Result.INCOMPATIBLE, "Smite vs Sharpness");
		helper.assertTrue(EnchantTransfer.check(enchanted(level, Items.IRON_SWORD, Enchantments.SMITE, 4), new ItemStack(Items.IRON_PICKAXE), smite)
			== EnchantTransfer.Result.INCOMPATIBLE, "Smite does not go on a pickaxe");
		// maxima: a Spectral sword's Sharpness VI arrives as V; V onto V gains nothing
		ItemStack six = enchanted(level, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 6);
		helper.assertTrue(EnchantTransfer.resultLevel(six, new ItemStack(Items.IRON_SWORD), sharp) == 5, "capped at V");
		helper.assertTrue(EnchantTransfer.check(enchanted(level, Items.IRON_SWORD, Enchantments.SHARPNESS, 5),
			enchanted(level, Items.IRON_SWORD, Enchantments.SHARPNESS, 5), sharp) == EnchantTransfer.Result.NOTHING_TO_GAIN, "V + V stays V");
		helper.assertTrue(EnchantTransfer.resultLevel(enchanted(level, Items.IRON_SWORD, Enchantments.SHARPNESS, 3),
			enchanted(level, Items.IRON_SWORD, Enchantments.SHARPNESS, 3), sharp) == 4, "III + III = IV, like the anvil");
		// curses never move
		ItemStack cursed = enchanted(level, Items.IRON_SWORD, Enchantments.SHARPNESS, 2, Enchantments.VANISHING_CURSE, 1);
		helper.assertTrue(EnchantTransfer.options(cursed).equals(List.of(sharp)), "a curse is not on the list");
		helper.assertTrue(EnchantTransfer.check(cursed, new ItemStack(Items.BOOK), vanishing) == EnchantTransfer.Result.CURSE, "refused");
		ItemStack[] moved = EnchantTransfer.apply(cursed, new ItemStack(Items.IRON_SWORD), sharp);
		helper.assertTrue(level(moved[0], vanishing) == 1 && level(moved[1], vanishing) == 0, "the curse stays behind");
		// a book splits into books; the last enchantment leaves a plain book
		ItemStack book = enchanted(level, Items.ENCHANTED_BOOK, Enchantments.SHARPNESS, 2, Enchantments.UNBREAKING, 1);
		helper.assertTrue(EnchantTransfer.check(book, new ItemStack(Items.BOOK), sharp) == EnchantTransfer.Result.OK, "book to book");
		ItemStack[] first = EnchantTransfer.apply(book, new ItemStack(Items.BOOK), sharp);
		helper.assertTrue(first[1].is(Items.ENCHANTED_BOOK) && level(first[1], sharp) == 2 && level(first[1], unbreak) == 0, "a new book with Sharpness II");
		helper.assertTrue(first[0].is(Items.ENCHANTED_BOOK) && level(first[0], unbreak) == 1 && level(first[0], sharp) == 0, "the old one keeps Unbreaking");
		ItemStack[] second = EnchantTransfer.apply(first[0], new ItemStack(Items.BOOK), unbreak);
		helper.assertTrue(second[0].is(Items.BOOK), "emptied, it is a plain book again");
		// treasure costs double
		helper.assertTrue(EnchantTransfer.levelCost(mending, 1) == 6 && EnchantTransfer.fragmentCost(mending, 1) == 2
			&& EnchantTransfer.levelCost(sharp, 3) == 9 && EnchantTransfer.fragmentCost(sharp, 3) == 3, "3 levels + 1 fragment per level, treasure x2");
		helper.assertTrue(!smiteBook.isEmpty(), "book built");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "craft_pool_dupe", timeoutTicks = 100)
	public void breakingThePoolMidRiteGivesBackExactlyTheItems(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ReflectingPoolBlockEntity pool = pool(helper);
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Breaker");
		player.setExperienceLevels(30);
		player.getInventory().add(new ItemStack(ModItems.SOUL_FRAGMENT, 5));
		pool.load(enchanted(level, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 3), new ItemStack(Items.IRON_SWORD));
		// a chunk unloading mid-rite: the saved heart comes back idle, both items still inside
		pool.onHammer(level, player);
		CompoundTag saved = pool.saveWithFullMetadata(level.registryAccess());
		BlockEntity reloaded = BlockEntity.loadStatic(pool.getBlockPos(), pool.getBlockState(), saved, level.registryAccess());
		helper.assertTrue(reloaded instanceof ReflectingPoolBlockEntity r && r.phase() == 0 && !r.source().isEmpty() && !r.target().isEmpty(),
			"reloaded idle with the items inside");
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(pool.phase() == ReflectingPoolBlockEntity.RUNES, "mid-rite, phase " + pool.phase());
			level.setBlock(pool.getBlockPos().offset(-2, 0, 0), Blocks.AIR.defaultBlockState(), 3);
		});
		helper.runAfterDelay(35, () -> {
			List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pool.getBlockPos()).inflate(4));
			helper.assertTrue(drops.size() == 2, "exactly the two items fall out, got " + drops.size());
			Holder<Enchantment> sharp = ench(level, Enchantments.SHARPNESS);
			boolean source = drops.stream().anyMatch(e -> e.getItem().is(Items.DIAMOND_SWORD) && level(e.getItem(), sharp) == 3);
			boolean target = drops.stream().anyMatch(e -> e.getItem().is(Items.IRON_SWORD) && level(e.getItem(), sharp) == 0);
			helper.assertTrue(source && target, "untouched: the sword keeps Sharpness III, the target has none");
			helper.assertTrue(pool.source().isEmpty() && pool.target().isEmpty() && !pool.isFormed(), "the pool is empty and unlocked");
			helper.assertTrue(player.experienceLevel == 30 && player.getInventory().countItem(ModItems.SOUL_FRAGMENT) == 5, "nothing was paid");
			drops.forEach(ItemEntity::discard);
			level.getServer().getPlayerList().remove(player);
			helper.succeed();
		});
	}

	// ---- the Soul Crucible --------------------------------------------------------------------------------

	private static ItemStack forge(GameTestHelper helper, SoulCrucibleBlockEntity crucible, ServerPlayer smith, ItemStack work,
		List<ItemStack> materials, int perfect, int good) {
		ServerLevel level = helper.getLevel();
		crucible.load(work, materials);
		helper.assertTrue(crucible.start(level, smith), "forging starts");
		crucible.jumpTo(level, SoulCrucibleBlockEntity.FORGE);
		ItemStack hammer = new ItemStack(ModItems.RITUAL_HAMMER);
		for (int k = 0; k < perfect + good; k++) {
			long when = crucible.beatTarget(k) + (k < perfect ? 0 : 5);
			int verdict = crucible.strikeAt(level, smith, hammer, when);
			helper.assertTrue(verdict == (k < perfect ? SoulCrucibleBlockEntity.PERFECT : SoulCrucibleBlockEntity.GOOD), "beat " + k + " verdict " + verdict);
		}
		crucible.finishNow(level);
		helper.assertTrue(crucible.finished() && crucible.materials().isEmpty(), "forged, the pot used up");
		ItemStack out = crucible.work();
		crucible.load(ItemStack.EMPTY, List.of());
		return out;
	}

	private static double modifier(ItemStack stack, net.minecraft.resources.ResourceLocation id) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY)
			.modifiers().stream().filter(e -> e.modifier().id().equals(id)).mapToDouble(e -> e.modifier().amount()).sum();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "craft_rhythm")
	public void crucibleQualityFollowsTheStrikes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		SoulCrucibleBlockEntity crucible = crucible(helper, new BlockPos(3, 2, 3), 0);
		helper.assertTrue(crucible.tryAssemble(level, null), "locks");
		ServerPlayer smith = RemnantsGameTest.survivalPlayer(helper, "Smith");
		List<ItemStack> soul = List.of(new ItemStack(ModItems.CONDENSED_SOUL));
		int[][] cases = {{3, 0, 5}, {4, 0, 10}, {5, 0, 15}, {2, 2, 5}, {2, 0, 0}};
		for (int[] c : cases) {
			ItemStack sword = forge(helper, crucible, smith, new ItemStack(Items.IRON_SWORD), soul, c[0], c[1]);
			helper.assertTrue(ForgeQuality.percent(sword) == c[2], c[0] + " perfect + " + c[1] + " good -> +" + c[2] + "%, got " + ForgeQuality.percent(sword));
			double bonus = modifier(sword, ForgeQuality.DAMAGE);
			helper.assertTrue(Math.abs(bonus - 6.0 * c[2] / 100.0) < 0.011, "iron sword (6 damage) +" + c[2] + "% = " + bonus);
			helper.assertTrue(sword.getMaxDamage() == Math.round(250 * (1 + c[2] / 100.0)), "durability " + sword.getMaxDamage());
			ForgeQuality q = sword.get(dev.remnants.registry.ModComponents.FORGE_QUALITY);
			helper.assertTrue(c[2] == 15 ? q != null && q.master().equals("Smith") : q == null || q.master().isEmpty(), "only five perfect sign it");
		}
		// armour gets protection, keeping its own defence
		ItemStack chest = forge(helper, crucible, smith, new ItemStack(Items.IRON_CHESTPLATE), soul, 5, 0);
		double armor = chest.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY)
			.modifiers().stream().filter(e -> e.attribute().equals(Attributes.ARMOR)).mapToDouble(e -> e.modifier().amount()).sum();
		helper.assertTrue(Math.abs(armor - 6.9) < 0.011, "iron chestplate 6 + 15% = 6.9 armour, got " + armor);
		// tools dig faster
		ItemStack pick = forge(helper, crucible, smith, new ItemStack(Items.IRON_PICKAXE), soul, 4, 0);
		helper.assertTrue(Math.abs(modifier(pick, ForgeQuality.MINING) - 0.6) < 0.011, "iron pickaxe speed 6 + 10%: " + modifier(pick, ForgeQuality.MINING));
		level.getServer().getPlayerList().remove(smith);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "craft_levels")
	public void levelThreeMovedAndLevelFourOnlyForTheArsenal(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		SoulCrucibleBlockEntity crucible = crucible(helper, new BlockPos(3, 2, 3), 0);
		helper.assertTrue(crucible.tryAssemble(level, null), "locks");
		ServerPlayer smith = RemnantsGameTest.survivalPlayer(helper, "Smith2");
		ItemStack two = SpectralForge.upgrade(SpectralForge.upgrade(new ItemStack(Items.DIAMOND_SWORD), level.registryAccess()), level.registryAccess());
		List<ItemStack> kingly = List.of(new ItemStack(ModItems.CONDENSED_SOUL, 6), new ItemStack(ModItems.SOUL_KING_HEART));
		// the small forge stops at II now
		BlockPos forgePos = helper.absolutePos(new BlockPos(6, 1, 6));
		level.setBlockAndUpdate(forgePos, ModBlocks.SOUL_FORGE.defaultBlockState());
		smith.setItemInHand(InteractionHand.MAIN_HAND, two.copy());
		smith.getInventory().add(new ItemStack(ModItems.CONDENSED_SOUL, 6));
		smith.getInventory().add(new ItemStack(ModItems.SOUL_KING_HEART));
		level.getBlockState(forgePos).useItemOn(smith.getMainHandItem(), level, smith, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(forgePos), Direction.UP, forgePos, false));
		helper.assertTrue(SpectralForge.level(smith.getMainHandItem()) == 2 && smith.getInventory().countItem(ModItems.CONDENSED_SOUL) == 6,
			"the Soul Forge no longer forges III");
		helper.assertTrue(SoulForgeBlock.SMALL_FORGE_MAX == 2, "small forge: I and II");
		// III in the Crucible
		ItemStack three = forge(helper, crucible, smith, two.copy(), kingly, 3, 0);
		helper.assertTrue(SpectralForge.level(three) == 3, "Spectral III from the Crucible");
		// IV: the Arsenal only
		helper.assertTrue(CrucibleRecipes.match(three, kingly) == null, "no IV for a common weapon");
		ItemStack arsenal = new ItemStack(ModItems.SOUL_KING_ARSENAL);
		double before = damage(arsenal);
		ItemStack awakened = forge(helper, crucible, smith, arsenal, kingly, 0, 0);
		helper.assertTrue(ArsenalItem.isAwakened(awakened) && SpectralForge.level(awakened) == 4, "the Arsenal awakens (IV)");
		helper.assertTrue(Math.abs(damage(awakened) - before - ArsenalItem.AWAKENED_DAMAGE) < 0.01, "+2 damage, got " + (damage(awakened) - before));
		ArsenalItem.setMode(awakened, ArsenalItem.MACE);
		ArsenalItem.setMode(awakened, ArsenalItem.SWORD);
		helper.assertTrue(Math.abs(damage(awakened) - before - ArsenalItem.AWAKENED_DAMAGE) < 0.01, "the bonus survives changing form");
		helper.assertTrue(CrucibleRecipes.match(awakened, kingly) == null, "only once");
		level.getServer().getPlayerList().remove(smith);
		helper.succeed();
	}

	private static double damage(ItemStack stack) {
		return stack.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream().filter(e -> e.attribute().equals(Attributes.ATTACK_DAMAGE))
			.mapToDouble(e -> e.modifier().amount()).sum();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "craft_forge_dupe", timeoutTicks = 60)
	public void breakingTheForgeMidCraftDropsExactlyTheIngredients(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		SoulCrucibleBlockEntity crucible = crucible(helper, new BlockPos(3, 2, 3), 0);
		helper.assertTrue(crucible.tryAssemble(level, null), "locks");
		ServerPlayer smith = RemnantsGameTest.survivalPlayer(helper, "Smith3");
		ItemStack sword = enchanted(level, Items.IRON_SWORD, Enchantments.SHARPNESS, 2);
		sword.setDamageValue(40);
		crucible.load(sword.copy(), List.of(new ItemStack(ModItems.CONDENSED_SOUL)));
		helper.assertTrue(crucible.start(level, smith), "starts");
		crucible.jumpTo(level, SoulCrucibleBlockEntity.FORGE);
		crucible.strikeAt(level, smith, new ItemStack(ModItems.RITUAL_HAMMER), crucible.beatTarget(0));
		BlockPos anvil = crucible.partPos(SoulCrucibleBlockEntity.ANVIL);
		level.setBlock(anvil, Blocks.AIR.defaultBlockState(), 3);
		helper.runAfterDelay(5, () -> {
			List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(crucible.getBlockPos()).inflate(4));
			helper.assertTrue(drops.size() == 2, "the sword and the soul, got " + drops.size());
			helper.assertTrue(drops.stream().anyMatch(e -> ItemStack.matches(e.getItem(), sword)), "the sword exactly as it went in (worn, no quality)");
			helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(ModItems.CONDENSED_SOUL) && e.getItem().getCount() == 1), "the soul, unspent");
			helper.assertTrue(!crucible.isFormed() && !crucible.isWorking() && crucible.work().isEmpty(), "unlocked and empty");
			drops.forEach(ItemEntity::discard);
			level.getServer().getPlayerList().remove(smith);
			helper.succeed();
		});
	}

	// ---- the Mourner's set ------------------------------------------------------------------------------

	private static void mourner(ServerPlayer player) {
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.MOURNER_HOOD));
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.MOURNER_ROBE));
		player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.MOURNER_LEGGINGS));
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.MOURNER_BOOTS));
	}

	/** A new player is immune to damage for 60 ticks after joining (ServerPlayer#spawnInvulnerableTime): wait it out. */
	static final int SPAWN_GRACE = 70;

	@GameTest(template = EMPTY_STRUCTURE, batch = "craft_ghost", timeoutTicks = 220)
	public void ghostStepSavesOnceThenCoolsDown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Mourner");
		mourner(player);
		player.getInventory().add(new ItemStack(Items.EMERALD));
		helper.runAfterDelay(SPAWN_GRACE, () -> {
			player.hurt(level.damageSources().generic(), 1000F);
			helper.assertTrue(player.isAlive() && player.getHealth() == GhostStep.HEALTH_AFTER,
				"Ghost Step: alive on two hearts, health " + player.getHealth());
			helper.assertTrue(GhostStep.isGhost(player) && player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY), "a ghost now");
			helper.assertTrue(GhostStep.cooldownLeft(player) > 0, "cooling down");
			player.invulnerableTime = 0;
			player.hurt(level.damageSources().generic(), 1000F);
			helper.assertTrue(player.isAlive() && player.getHealth() == GhostStep.HEALTH_AFTER, "a ghost takes nothing");
		});
		helper.runAfterDelay(SPAWN_GRACE + RemnantsConfig.get().ghostStepTicks + 5, () -> {
			helper.assertTrue(!GhostStep.isGhost(player), "the ghost form has ended");
			player.invulnerableTime = 0;
			player.hurt(level.damageSources().generic(), 1000F);
			helper.assertTrue(player.isDeadOrDying(), "on cooldown the set does not save again");
			RemnantsGameTest.remnantsOf(helper, player.getUUID()).forEach(net.minecraft.world.entity.Entity::discard);
			level.getServer().getPlayerList().remove(player);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "craft_ghost_order", timeoutTicks = 160)
	public void totemThenLastBreathThenTheSet(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer totem = RemnantsGameTest.survivalPlayer(helper, "MournerTotem");
		mourner(totem);
		totem.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		ServerPlayer breath = RemnantsGameTest.survivalPlayer(helper, "MournerBreath");
		mourner(breath);
		breath.getInventory().add(new ItemStack(Items.EMERALD));
		RemnantTracker.get(level.getServer()).addResonance(breath.getUUID(), 50, "");
		helper.runAfterDelay(SPAWN_GRACE, () -> {
			// a totem in hand goes first
			totem.hurt(level.damageSources().generic(), 1000F);
			helper.assertTrue(totem.isAlive() && totem.getOffhandItem().isEmpty(), "the totem saved them");
			helper.assertTrue(GhostStep.cooldownLeft(totem) == 0 && !GhostStep.isGhost(totem), "the set was not used");
			// then Last Breath; a second killing blow in the same tick is not caught by the set
			breath.hurt(level.damageSources().generic(), 1000F);
			helper.assertTrue(breath.isAlive() && breath.getHealth() == 6.0F && GhostStep.cooldownLeft(breath) == 0,
				"Last Breath first, the set untouched, health " + breath.getHealth());
			breath.invulnerableTime = 0;
			breath.hurt(level.damageSources().generic(), 1000F);
			helper.assertTrue(breath.isDeadOrDying(), "a second killing blow in the same tick is not caught by the set");
			helper.assertTrue(GhostStep.cooldownLeft(breath) == 0, "never in the same tick as Last Breath");
			RemnantsGameTest.remnantsOf(helper, breath.getUUID()).forEach(net.minecraft.world.entity.Entity::discard);
			level.getServer().getPlayerList().remove(totem);
			level.getServer().getPlayerList().remove(breath);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "craft_passthrough")
	public void unrelatedItemsPassThroughToTheirOwnUse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ReflectingPoolBlockEntity pool = pool(helper);
		ServerPlayer player = RemnantsGameTest.survivalPlayer(helper, "Reader");
		BlockHitResult poolHit = new BlockHitResult(Vec3.atCenterOf(pool.getBlockPos()), Direction.UP, pool.getBlockPos(), false);
		ItemStack codex = new ItemStack(ModItems.SOUL_CODEX);
		helper.assertTrue(level.getBlockState(pool.getBlockPos()).useItemOn(codex, level, player, InteractionHand.MAIN_HAND, poolHit)
			== net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION, "the Codex is not swallowed by the basin");
		helper.assertTrue(pool.source().isEmpty() && pool.target().isEmpty() && codex.getCount() == 1, "and nothing went in");
		ItemStack sword = enchanted(level, Items.IRON_SWORD, Enchantments.SHARPNESS, 2);
		helper.assertTrue(level.getBlockState(pool.getBlockPos()).useItemOn(sword, level, player, InteractionHand.MAIN_HAND, poolHit)
			!= net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION && !pool.source().isEmpty(), "an enchanted item does go in");
		SoulCrucibleBlockEntity crucible = crucible(helper, new BlockPos(1, 2, 1), 0);
		helper.assertTrue(crucible.tryAssemble(level, null), "locks");
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(crucible.getBlockPos()), Direction.UP, crucible.getBlockPos(), false);
		helper.assertTrue(level.getBlockState(crucible.getBlockPos()).useItemOn(codex, level, player, InteractionHand.MAIN_HAND, hit)
			== net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION, "nor by the crucible");
		ItemStack soul = new ItemStack(ModItems.CONDENSED_SOUL);
		helper.assertTrue(level.getBlockState(crucible.getBlockPos()).useItemOn(soul, level, player, InteractionHand.MAIN_HAND, hit)
			!= net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION, "a Condensed Soul is handled by the forge");
		level.getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "craft_echo")
	public void forgedArmourCountsInTheEcho(GameTestHelper helper) {
		ItemStack plain = new ItemStack(Items.IRON_CHESTPLATE);
		ItemStack forged = plain.copy();
		ForgeQuality.apply(forged, 15, "");
		dev.remnants.entity.RemnantEntity.EquipmentSnapshot a = new dev.remnants.entity.RemnantEntity.EquipmentSnapshot(ItemStack.EMPTY, plain,
			ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
		dev.remnants.entity.RemnantEntity.EquipmentSnapshot b = new dev.remnants.entity.RemnantEntity.EquipmentSnapshot(ItemStack.EMPTY, forged,
			ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
		helper.assertTrue(dev.remnants.entity.RemnantEntity.gearScore(b) > dev.remnants.entity.RemnantEntity.gearScore(a),
			"a forged piece makes a stronger echo");
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		ItemStack forgedSword = sword.copy();
		ForgeQuality.apply(forgedSword, 10, "");
		helper.assertTrue(dev.remnants.entity.RemnantEntity.gearScore(new dev.remnants.entity.RemnantEntity.EquipmentSnapshot(ItemStack.EMPTY,
			ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, forgedSword, ItemStack.EMPTY))
			> dev.remnants.entity.RemnantEntity.gearScore(new dev.remnants.entity.RemnantEntity.EquipmentSnapshot(ItemStack.EMPTY, ItemStack.EMPTY,
			ItemStack.EMPTY, ItemStack.EMPTY, sword, ItemStack.EMPTY)), "and a forged weapon too");
		helper.succeed();
	}
}
