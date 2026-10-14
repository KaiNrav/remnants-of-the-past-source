package dev.remnants.registry;

import dev.remnants.RemnantsMod;
import dev.remnants.entity.BoneChakramEntity;
import dev.remnants.entity.PhantomArrowEntity;
import dev.remnants.entity.RemnantEntity;
import dev.remnants.entity.SoulCrystalEntity;
import dev.remnants.entity.SoulKingEntity;
import dev.remnants.entity.SpectralWeaponEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import dev.remnants.RemnantsConfig;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ModEntities {
	public static final EntityType<RemnantEntity> REMNANT = Registry.register(BuiltInRegistries.ENTITY_TYPE, RemnantsMod.id("remnant"),
		EntityType.Builder.of(RemnantEntity::new, MobCategory.MONSTER)
			.sized(0.6F, 1.8F)
			.eyeHeight(1.62F)
			.fireImmune()
			.clientTrackingRange(10)
			.build("remnant"));

	/** Same creature, natural kind: echoes of long-dead adventurers (see RemnantEntity#isForgotten). */
	public static final EntityType<RemnantEntity> FORGOTTEN_REMNANT = Registry.register(BuiltInRegistries.ENTITY_TYPE, RemnantsMod.id("forgotten_remnant"),
		EntityType.Builder.of(RemnantEntity::new, MobCategory.MONSTER)
			.sized(0.6F, 1.8F)
			.eyeHeight(1.62F)
			.fireImmune()
			.clientTrackingRange(8)
			.build("forgotten_remnant"));

	public static final EntityType<PhantomArrowEntity> PHANTOM_ARROW = Registry.register(BuiltInRegistries.ENTITY_TYPE, RemnantsMod.id("phantom_arrow"),
		EntityType.Builder.<PhantomArrowEntity>of(PhantomArrowEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.5F)
			.eyeHeight(0.13F)
			.clientTrackingRange(4)
			.updateInterval(20)
			.build("phantom_arrow"));

	public static final EntityType<SpectralWeaponEntity> SPECTRAL_WEAPON = Registry.register(BuiltInRegistries.ENTITY_TYPE, RemnantsMod.id("spectral_weapon"),
		EntityType.Builder.<SpectralWeaponEntity>of(SpectralWeaponEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.5F)
			.clientTrackingRange(6)
			.updateInterval(1)
			.build("spectral_weapon"));

	public static final EntityType<SoulKingEntity> SOUL_KING = Registry.register(BuiltInRegistries.ENTITY_TYPE, RemnantsMod.id("soul_king"),
		EntityType.Builder.of(SoulKingEntity::new, MobCategory.MONSTER)
			.sized(1.8F, 5.4F)
			.eyeHeight(4.5F)
			.fireImmune()
			.clientTrackingRange(16)
			.build("soul_king"));

	public static final EntityType<SoulCrystalEntity> SOUL_CRYSTAL = Registry.register(BuiltInRegistries.ENTITY_TYPE, RemnantsMod.id("soul_crystal"),
		EntityType.Builder.<SoulCrystalEntity>of(SoulCrystalEntity::new, MobCategory.MISC)
			.sized(1.2F, 1.2F)
			.fireImmune()
			.clientTrackingRange(10)
			.updateInterval(20)
			.build("soul_crystal"));

	public static final EntityType<BoneChakramEntity> BONE_CHAKRAM = Registry.register(BuiltInRegistries.ENTITY_TYPE, RemnantsMod.id("bone_chakram"),
		EntityType.Builder.<BoneChakramEntity>of(BoneChakramEntity::new, MobCategory.MISC)
			.sized(0.6F, 0.3F)
			.clientTrackingRange(6)
			.updateInterval(1)
			.build("bone_chakram"));

	// ---- 1.4 crypts
	public static final EntityType<dev.remnants.entity.CryptWardenEntity> CRYPT_WARDEN = Registry.register(BuiltInRegistries.ENTITY_TYPE,
		RemnantsMod.id("crypt_warden"), EntityType.Builder.of(dev.remnants.entity.CryptWardenEntity::new, MobCategory.MONSTER)
			.sized(1.4F, 3.3F)
			.eyeHeight(2.9F)
			.fireImmune()
			.clientTrackingRange(10)
			.build("crypt_warden"));

	public static final EntityType<dev.remnants.entity.GraveMimicEntity> GRAVE_MIMIC = Registry.register(BuiltInRegistries.ENTITY_TYPE,
		RemnantsMod.id("grave_mimic"), EntityType.Builder.of(dev.remnants.entity.GraveMimicEntity::new, MobCategory.MONSTER)
			.sized(0.7F, 0.9F)
			.eyeHeight(0.6F)
			.clientTrackingRange(8)
			.build("grave_mimic"));

	public static final EntityType<dev.remnants.entity.KeenerEntity> KEENER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
		RemnantsMod.id("keener"), EntityType.Builder.of(dev.remnants.entity.KeenerEntity::new, MobCategory.MONSTER)
			.sized(0.8F, 2.5F)
			.eyeHeight(2.1F)
			.fireImmune()
			.clientTrackingRange(10)
			.build("keener"));

	public static final EntityType<dev.remnants.entity.SoulTetherEntity> SOUL_TETHER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
		RemnantsMod.id("soul_tether"), EntityType.Builder.<dev.remnants.entity.SoulTetherEntity>of(dev.remnants.entity.SoulTetherEntity::new, MobCategory.MISC)
			.sized(0.3F, 0.3F)
			.clientTrackingRange(8)
			.updateInterval(1)
			.build("soul_tether"));

	public static final EntityType<dev.remnants.entity.MimicSpitEntity> MIMIC_SPIT = Registry.register(BuiltInRegistries.ENTITY_TYPE,
		RemnantsMod.id("mimic_spit"), EntityType.Builder.<dev.remnants.entity.MimicSpitEntity>of(dev.remnants.entity.MimicSpitEntity::new, MobCategory.MISC)
			.sized(0.3F, 0.3F)
			.clientTrackingRange(4)
			.updateInterval(10)
			.build("mimic_spit"));

	// ---- 1.5 archive
	public static final EntityType<dev.remnants.entity.DecoyEchoEntity> DECOY_ECHO = Registry.register(BuiltInRegistries.ENTITY_TYPE,
		RemnantsMod.id("decoy_echo"), EntityType.Builder.of(dev.remnants.entity.DecoyEchoEntity::new, MobCategory.MISC)
			.sized(0.6F, 1.8F).clientTrackingRange(10).build("decoy_echo"));

	public static final EntityType<dev.remnants.entity.ThrownDecoyEntity> THROWN_DECOY = Registry.register(BuiltInRegistries.ENTITY_TYPE,
		RemnantsMod.id("thrown_decoy"), EntityType.Builder.<dev.remnants.entity.ThrownDecoyEntity>of(dev.remnants.entity.ThrownDecoyEntity::new, MobCategory.MISC)
			.sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build("thrown_decoy"));

	public static final EntityType<dev.remnants.entity.ArchivistEntity> ARCHIVIST = Registry.register(BuiltInRegistries.ENTITY_TYPE,
		RemnantsMod.id("archivist"), EntityType.Builder.of(dev.remnants.entity.ArchivistEntity::new, MobCategory.MONSTER)
			.sized(1.2F, 3.4F).fireImmune().clientTrackingRange(10).build("archivist"));

	public static final EntityType<dev.remnants.entity.ArchivePageEntity> ARCHIVE_PAGE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
		RemnantsMod.id("archive_page"), EntityType.Builder.of(dev.remnants.entity.ArchivePageEntity::new, MobCategory.MONSTER)
			.sized(0.6F, 0.5F).clientTrackingRange(8).build("archive_page"));

	public static final EntityType<dev.remnants.entity.FlyingTomeEntity> FLYING_TOME = Registry.register(BuiltInRegistries.ENTITY_TYPE,
		RemnantsMod.id("flying_tome"), EntityType.Builder.<dev.remnants.entity.FlyingTomeEntity>of(dev.remnants.entity.FlyingTomeEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(1).build("flying_tome"));

	public static final EntityType<dev.remnants.entity.ArchiveRuneEntity> ARCHIVE_RUNE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
		RemnantsMod.id("archive_rune"), EntityType.Builder.<dev.remnants.entity.ArchiveRuneEntity>of(dev.remnants.entity.ArchiveRuneEntity::new, MobCategory.MISC)
			.sized(0.35F, 0.35F).clientTrackingRange(8).updateInterval(2).build("archive_rune"));

	public static final EntityType<dev.remnants.entity.ArchiveStarEntity> ARCHIVE_STAR = Registry.register(BuiltInRegistries.ENTITY_TYPE,
		RemnantsMod.id("archive_star"), EntityType.Builder.<dev.remnants.entity.ArchiveStarEntity>of(dev.remnants.entity.ArchiveStarEntity::new, MobCategory.MISC)
			.sized(1.0F, 0.2F).clientTrackingRange(8).updateInterval(20).build("archive_star"));

	public static final EntityType<dev.remnants.entity.ArchiveEyeEntity> ARCHIVE_EYE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
		RemnantsMod.id("archive_eye"), EntityType.Builder.of(dev.remnants.entity.ArchiveEyeEntity::new, MobCategory.MISC)
			.sized(0.9F, 0.9F).fireImmune().clientTrackingRange(10).build("archive_eye"));

	public static final EntityType<dev.remnants.entity.CarrionCrowEntity> CARRION_CROW = Registry.register(BuiltInRegistries.ENTITY_TYPE,
		RemnantsMod.id("carrion_crow"), EntityType.Builder.of(dev.remnants.entity.CarrionCrowEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.6F).clientTrackingRange(10).build("carrion_crow"));

	public static void init() {
		FabricDefaultAttributeRegistry.register(DECOY_ECHO, dev.remnants.entity.DecoyEchoEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ARCHIVIST, dev.remnants.entity.ArchivistEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ARCHIVE_PAGE, dev.remnants.entity.ArchivePageEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(CARRION_CROW, dev.remnants.entity.CarrionCrowEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ARCHIVE_EYE, dev.remnants.entity.ArchiveEyeEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(REMNANT, RemnantEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(FORGOTTEN_REMNANT, RemnantEntity.createAttributes());
		SpawnPlacements.register(FORGOTTEN_REMNANT, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			Monster::checkMonsterSpawnRules);
		RemnantsConfig cfg = RemnantsConfig.get();
		if (cfg.forgottenRemnants) {
			if (cfg.forgottenSpawnWeightOverworld > 0) {
				BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld().and(BiomeSelectors.excludeByKey(Biomes.MUSHROOM_FIELDS, Biomes.DEEP_DARK)),
					MobCategory.MONSTER, FORGOTTEN_REMNANT, cfg.forgottenSpawnWeightOverworld, 1, 1);
			}
			if (cfg.forgottenSpawnWeightSoulValley > 0) {
				BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.SOUL_SAND_VALLEY),
					MobCategory.MONSTER, FORGOTTEN_REMNANT, cfg.forgottenSpawnWeightSoulValley, 1, 2);
			}
		}
		FabricDefaultAttributeRegistry.register(SOUL_KING, SoulKingEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(CRYPT_WARDEN, dev.remnants.entity.CryptWardenEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(GRAVE_MIMIC, dev.remnants.entity.GraveMimicEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(KEENER, dev.remnants.entity.KeenerEntity.createAttributes());
	}

	private ModEntities() {
	}
}
