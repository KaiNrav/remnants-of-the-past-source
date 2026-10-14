package dev.remnants.registry;

import dev.remnants.RemnantsMod;
import dev.remnants.block.entity.BurialCasketBlockEntity;
import dev.remnants.block.entity.SpiritAltarBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<SpiritAltarBlockEntity> SPIRIT_ALTAR = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
		RemnantsMod.id("spirit_altar"), BlockEntityType.Builder.of(SpiritAltarBlockEntity::new, ModBlocks.SPIRIT_ALTAR).build(null));

	public static final BlockEntityType<BurialCasketBlockEntity> BURIAL_CASKET = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
		RemnantsMod.id("burial_casket"), BlockEntityType.Builder.of(BurialCasketBlockEntity::new, ModBlocks.BURIAL_CASKET).build(null));

	public static final BlockEntityType<dev.remnants.block.entity.ReflectingPoolBlockEntity> REFLECTING_POOL = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, RemnantsMod.id("reflecting_pool"),
		BlockEntityType.Builder.of(dev.remnants.block.entity.ReflectingPoolBlockEntity::new, ModBlocks.REFLECTING_BASIN).build(null));

	public static final BlockEntityType<dev.remnants.block.entity.SoulCrucibleBlockEntity> SOUL_CRUCIBLE = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, RemnantsMod.id("soul_crucible"),
		BlockEntityType.Builder.of(dev.remnants.block.entity.SoulCrucibleBlockEntity::new, ModBlocks.SOUL_CRUCIBLE).build(null));

	public static final BlockEntityType<dev.remnants.block.entity.BelfryBlockEntity> BELFRY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, RemnantsMod.id("belfry"),
		BlockEntityType.Builder.of(dev.remnants.block.entity.BelfryBlockEntity::new, ModBlocks.REST_STONE).build(null));

	public static final BlockEntityType<dev.remnants.block.entity.BellOfRestBlockEntity> BELL_OF_REST = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, RemnantsMod.id("bell_of_rest"),
		BlockEntityType.Builder.of(dev.remnants.block.entity.BellOfRestBlockEntity::new, ModBlocks.BELL_OF_REST).build(null));

	public static final BlockEntityType<dev.remnants.block.entity.MemorialBlockEntity> MEMORIAL = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, RemnantsMod.id("memorial"),
		BlockEntityType.Builder.of(dev.remnants.block.entity.MemorialBlockEntity::new, ModBlocks.MEMORIAL_PROJECTOR).build(null));

	public static final BlockEntityType<dev.remnants.block.entity.EpitaphNicheBlockEntity> EPITAPH_NICHE = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, RemnantsMod.id("epitaph_niche"),
		BlockEntityType.Builder.of(dev.remnants.block.entity.EpitaphNicheBlockEntity::new, ModBlocks.EPITAPH_NICHE).build(null));

	public static final BlockEntityType<dev.remnants.block.entity.AstrolabeBlockEntity> ASTROLABE = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, RemnantsMod.id("astrolabe"),
		BlockEntityType.Builder.of(dev.remnants.block.entity.AstrolabeBlockEntity::new, ModBlocks.SOUL_ASTROLABE).build(null));

	public static void init() {
	}

	private ModBlockEntities() {
	}
}
