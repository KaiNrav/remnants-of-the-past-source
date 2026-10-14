package dev.remnants.registry;

import dev.remnants.RemnantsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {
	/** What a thrown Bone Chakram cuts down on its way: grass, ferns, cobwebs, vines, dead bushes. */
	public static final TagKey<Block> CHAKRAM_CUTTABLE = TagKey.create(Registries.BLOCK, RemnantsMod.id("chakram_cuttable"));

	/** What the Soul Astrolabe's map focus looks for: the Ossuary and the Library. */
	public static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> ASTROLABE_TARGETS =
		TagKey.create(Registries.STRUCTURE, RemnantsMod.id("astrolabe_targets"));

	private ModTags() {
	}
}
