package dev.remnants.registry;

import dev.remnants.RemnantsMod;
import dev.remnants.world.ForgottenGraveFeature;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Forgotten Graves: the feature, where it generates, and the point of interest the Soul Dowser listens for. */
public final class ModWorldgen {
	public static final Feature<NoneFeatureConfiguration> FORGOTTEN_GRAVE = Registry.register(BuiltInRegistries.FEATURE,
		RemnantsMod.id("forgotten_grave"), new ForgottenGraveFeature(NoneFeatureConfiguration.CODEC));
	/** data/remnants_of_the_past/worldgen/placed_feature/forgotten_grave.json. */
	public static final ResourceKey<PlacedFeature> FORGOTTEN_GRAVE_PLACED = ResourceKey.create(Registries.PLACED_FEATURE,
		RemnantsMod.id("forgotten_grave"));
	public static final ResourceKey<PoiType> GRAVE_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, RemnantsMod.id("buried_grave"));

	public static void init() {
		PointOfInterestHelper.register(GRAVE_POI.location(), 0, 1, ModBlocks.BURIED_GRAVE);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld()
				.and(BiomeSelectors.excludeByKey(Biomes.MUSHROOM_FIELDS, Biomes.DEEP_DARK))
				.and(BiomeSelectors.tag(BiomeTags.IS_OCEAN).negate())
				.and(BiomeSelectors.tag(BiomeTags.IS_RIVER).negate()),
			GenerationStep.Decoration.SURFACE_STRUCTURES, FORGOTTEN_GRAVE_PLACED);
	}

	private ModWorldgen() {
	}
}
