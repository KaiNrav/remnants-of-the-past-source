package dev.remnants.registry;

import dev.remnants.RemnantsMod;
import dev.remnants.block.BurialCasketBlock;
import dev.remnants.block.CruciblePartBlock;
import dev.remnants.block.FormableBlock;
import dev.remnants.block.ReflectingBasinBlock;
import dev.remnants.block.SoulChimneyBlock;
import dev.remnants.block.SoulCrucibleBlock;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.block.BuriedGraveBlock;
import dev.remnants.block.SoulAnchorBlock;
import dev.remnants.block.WeatheredGravestoneBlock;
import dev.remnants.block.SoulBrazierBlock;
import dev.remnants.block.SoulForgeBlock;
import dev.remnants.block.SoulPedestalBlock;
import dev.remnants.block.SpiritAltarBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	// ---- the soul-stone building set (crypts, the King's circle) ----------------------------------------------------
	public static final Block SOUL_BRICKS = register("soul_bricks",
		new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F)
			.requiresCorrectToolForDrops().sound(SoundType.POLISHED_DEEPSLATE)));
	public static final Block SOUL_BRICK_STAIRS = register("soul_brick_stairs",
		new StairBlock(SOUL_BRICKS.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(SOUL_BRICKS)));
	public static final Block SOUL_BRICK_SLAB = register("soul_brick_slab",
		new SlabBlock(BlockBehaviour.Properties.ofFullCopy(SOUL_BRICKS)));
	public static final Block SOUL_BRICK_WALL = register("soul_brick_wall",
		new WallBlock(BlockBehaviour.Properties.ofFullCopy(SOUL_BRICKS).forceSolidOn()));
	public static final Block CHISELED_SOUL_BRICKS = register("chiseled_soul_bricks",
		new FormableBlock(BlockBehaviour.Properties.ofFullCopy(SOUL_BRICKS).lightLevel(s -> s.getValue(Multiblocks.FORMED) ? 7 : 4)));
	public static final Block SOUL_BRAZIER = register("soul_brazier",
		new SoulBrazierBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.0F, 6.0F)
			.requiresCorrectToolForDrops().sound(SoundType.LANTERN).lightLevel(s -> 15).noOcclusion()));

	// ---- ritual and utility blocks ----------------------------------------------------------------------------------
	public static final Block SOUL_PEDESTAL = register("soul_pedestal",
		new SoulPedestalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.5F, 9.0F)
			.requiresCorrectToolForDrops().sound(SoundType.POLISHED_DEEPSLATE).lightLevel(s -> 10).noOcclusion()));

	public static final Block SPIRIT_ALTAR = register("spirit_altar",
		new SpiritAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 1200.0F)
			.requiresCorrectToolForDrops().sound(SoundType.POLISHED_DEEPSLATE).lightLevel(s -> 12).noOcclusion()));

	public static final Block SOUL_FORGE = register("soul_forge",
		new SoulForgeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(5.0F, 1200.0F)
			.requiresCorrectToolForDrops().sound(SoundType.ANVIL).lightLevel(s -> 6).noOcclusion()));

	public static final Block SOUL_ANCHOR = register("soul_anchor",
		new SoulAnchorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(25.0F, 1200.0F)
			.requiresCorrectToolForDrops().sound(SoundType.POLISHED_DEEPSLATE)
			.lightLevel(s -> s.getValue(SoulAnchorBlock.LIT) ? 12 : 3)));

	// ---- 1.2 graves ------------------------------------------------------------------------------------------------
	/** Looks and sounds like the soil around it; only a Soul Dowser hears it. */
	public static final Block BURIED_GRAVE = register("buried_grave",
		new BuriedGraveBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.6F)));
	public static final Block BURIAL_CASKET = register("burial_casket",
		new BurialCasketBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.5F)
			.sound(SoundType.WOOD).instrument(NoteBlockInstrument.BASS).noOcclusion()));
	public static final Block WEATHERED_GRAVESTONE = register("weathered_gravestone",
		new WeatheredGravestoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F, 6.0F)
			.requiresCorrectToolForDrops().sound(SoundType.STONE).noOcclusion()));

	// ---- 1.3 craft: the Reflecting Pool and the Soul Crucible ----------------------------------------------------------
	public static final Block REFLECTING_BASIN = register("reflecting_basin",
		new ReflectingBasinBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.5F, 9.0F)
			.requiresCorrectToolForDrops().sound(SoundType.POLISHED_DEEPSLATE).noOcclusion()
			.lightLevel(s -> s.getValue(Multiblocks.FORMED) ? 10 : 3)));
	public static final Block SOUL_CRUCIBLE = register("soul_crucible",
		new SoulCrucibleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 9.0F)
			.requiresCorrectToolForDrops().sound(SoundType.ANVIL).noOcclusion().lightLevel(s -> s.getValue(Multiblocks.FORMED) ? 8 : 0)));
	public static final Block SOUL_HEARTH = register("soul_hearth",
		new CruciblePartBlock.Hearth(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.5F, 9.0F)
			.requiresCorrectToolForDrops().sound(SoundType.POLISHED_DEEPSLATE).noOcclusion()
			.lightLevel(s -> s.getValue(Multiblocks.FORMED) ? 13 : 2)));
	public static final Block SOUL_BELLOWS = register("soul_bellows",
		new CruciblePartBlock.Bellows(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F)
			.sound(SoundType.WOOD).noOcclusion().ignitedByLava()));
	public static final Block SOUL_TROUGH = register("soul_trough",
		new CruciblePartBlock.Trough(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.5F, 6.0F)
			.requiresCorrectToolForDrops().sound(SoundType.POLISHED_DEEPSLATE).noOcclusion()));
	public static final Block SOUL_CHIMNEY = register("soul_chimney",
		new SoulChimneyBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.5F, 6.0F)
			.requiresCorrectToolForDrops().sound(SoundType.POLISHED_DEEPSLATE).noOcclusion()));

	// ---- 1.4 crypts: the Ossuary and the Belfry ---------------------------------------------------------------------
	public static final Block OSSUARY_URN = register("ossuary_urn",
		new dev.remnants.block.OssuaryUrnBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_PURPLE).strength(0.8F)
			.sound(SoundType.DECORATED_POT).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
	/** Practically unbreakable (like the vanilla vault) and drops nothing: Ossuary vaults open by key or by eyes. */
	public static final Block OSSUARY_GATE = register("ossuary_gate",
		new dev.remnants.block.OssuaryGateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(50.0F, 1200.0F)
			.requiresCorrectToolForDrops().sound(SoundType.CHAIN).noOcclusion().noLootTable()
			.pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
	public static final Block CRYPT_EYE = register("crypt_eye",
		new dev.remnants.block.CryptEyeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(50.0F, 1200.0F)
			.requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS).noLootTable()
			.lightLevel(s -> s.getValue(dev.remnants.block.CryptEyeBlock.STRUCK) ? 0 : 7)));
	public static final Block REST_STONE = register("rest_stone",
		new dev.remnants.block.RestStoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.5F, 9.0F)
			.requiresCorrectToolForDrops().sound(SoundType.POLISHED_DEEPSLATE).noOcclusion()
			.lightLevel(s -> s.getValue(Multiblocks.FORMED) ? 9 : 2)));
	public static final Block BELL_OF_REST = register("bell_of_rest",
		new dev.remnants.block.BellOfRestBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(5.0F)
			.requiresCorrectToolForDrops().sound(SoundType.ANVIL).noOcclusion()
			.pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY).lightLevel(s -> s.getValue(Multiblocks.FORMED) ? 6 : 0)));
	public static final Block CRACKED_BELL_OF_REST = register("cracked_bell_of_rest",
		new dev.remnants.block.CrackedBellOfRestBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(3.0F)
			.requiresCorrectToolForDrops().sound(SoundType.ANVIL).noOcclusion()
			.pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

	// ---- 1.5 archive: the Memorial and the Soul Astrolabe ------------------------------------------------------------
	public static final Block MEMORIAL_PROJECTOR = register("memorial_projector",
		new dev.remnants.block.MemorialProjectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.5F, 9.0F)
			.requiresCorrectToolForDrops().sound(SoundType.POLISHED_DEEPSLATE).noOcclusion()
			.lightLevel(s -> s.getValue(Multiblocks.FORMED) ? 10 : 3)));
	public static final Block EPITAPH_NICHE = register("epitaph_niche",
		new dev.remnants.block.EpitaphNicheBlock(BlockBehaviour.Properties.ofFullCopy(SOUL_BRICKS)
			.lightLevel(s -> s.getValue(Multiblocks.FORMED) ? 5 : 0)));
	public static final Block SOUL_ASTROLABE = register("soul_astrolabe",
		new dev.remnants.block.SoulAstrolabeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(3.5F, 9.0F)
			.requiresCorrectToolForDrops().sound(SoundType.COPPER).noOcclusion()
			.lightLevel(s -> s.getValue(Multiblocks.FORMED) ? 9 : 2)));
	public static final Block FOCUS_CRYSTAL = register("focus_crystal",
		new dev.remnants.block.FocusCrystalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(1.5F)
			.sound(SoundType.AMETHYST_CLUSTER).noOcclusion().lightLevel(s -> s.getValue(Multiblocks.FORMED) ? 12 : 5)));

	private static Block register(String name, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK, RemnantsMod.id(name), block);
	}

	public static void init() {
	}

	private ModBlocks() {
	}
}
