package dev.remnants.entity;

import dev.remnants.registry.ModItems;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Biome kinds of the Forgotten Remnant: the same echo and brain, a land-worn look and one signature move
 * (see Abilities: Harpoon, FrostBreath, Burrow). Decided by the biome it rises in.
 */
public enum ForgottenVariant {
	NONE(0xB4C8BE, () -> null),
	/** Shores, rivers, the ocean's edge: hurls a chain harpoon, quick in water. Leaves a Wet Shroud. */
	DROWNED(0x8FC4B8, () -> ModItems.WET_SHROUD),
	/** Snow and ice: an ice crust (30 % less damage until fire or an axe breaks it) and a freezing breath. Leaves Soul Frost. */
	FROZEN(0xC6E4F4, () -> ModItems.SOUL_FROST),
	/** Deserts and badlands: burrows under the sand and surfaces behind you. Leaves a Scarab Seal. */
	SANDY(0xDCC9A0, () -> ModItems.SCARAB_SEAL);

	/** Ghost tint (RGB). */
	public final int tint;
	private final Supplier<Item> trophy;

	ForgottenVariant(int tint, Supplier<Item> trophy) {
		this.tint = tint;
		this.trophy = trophy;
	}

	public Item trophy() {
		return this.trophy.get();
	}

	public String key() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public static ForgottenVariant byKey(String key) {
		for (ForgottenVariant v : values()) {
			if (v.key().equals(key)) {
				return v;
			}
		}
		return NONE;
	}

	public static ForgottenVariant byId(int id) {
		return id >= 0 && id < values().length ? values()[id] : NONE;
	}

	public static ForgottenVariant forBiome(Holder<Biome> biome, BlockPos pos) {
		if (biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN)) {
			return DROWNED;
		}
		if (biome.is(ConventionalBiomeTags.IS_DESERT) || biome.is(BiomeTags.IS_BADLANDS)) {
			return SANDY;
		}
		if (biome.is(ConventionalBiomeTags.IS_SNOWY) || biome.is(ConventionalBiomeTags.IS_ICY)
			|| biome.value().getPrecipitationAt(pos) == Biome.Precipitation.SNOW) {
			return FROZEN;
		}
		return NONE;
	}
}
