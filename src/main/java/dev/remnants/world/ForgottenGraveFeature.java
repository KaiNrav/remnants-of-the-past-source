package dev.remnants.world;

import com.mojang.serialization.Codec;
import dev.remnants.RemnantsConfig;
import dev.remnants.block.BuriedGraveBlock;
import dev.remnants.block.WeatheredGravestoneBlock;
import dev.remnants.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A Forgotten Grave: a Buried Grave block two below the surface, in the same soil as around it, so nothing gives it
 * away. About a third have a weathered gravestone above (leaning half the time), sometimes with old candles and a
 * tuft of grass. One chunk in {@link RemnantsConfig#forgottenGraveRarity} gets one; some of them lie in a family plot.
 */
public class ForgottenGraveFeature extends Feature<NoneFeatureConfiguration> {
	/** Share of graves that lie in a family plot (2.0): a row of two to four, headstones alike. */
	private static final float FAMILY_CHANCE = 0.12F;

	public ForgottenGraveFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
		int rarity = RemnantsConfig.get().forgottenGraveRarity;
		RandomSource random = ctx.random();
		if (rarity <= 0 || random.nextInt(rarity) != 0) {
			return false;
		}
		WorldGenLevel level = ctx.level();
		BlockPos top = ctx.origin();
		boolean family = random.nextFloat() < FAMILY_CHANCE;
		Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		if (!grave(level, top, random, family, facing)) {
			return false;
		}
		if (family) {
			// a family plot: a row of up to three more beside it, two blocks apart, their headstones all facing alike
			Direction along = facing.getClockWise();
			int more = 1 + random.nextInt(3);
			for (int i = 1; i <= more; i++) {
				BlockPos column = top.relative(along, 2 * i);
				BlockPos next = new BlockPos(column.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, column.getX(), column.getZ()),
					column.getZ());
				if (Math.abs(next.getY() - top.getY()) > 1 || !grave(level, next, random, true, facing)) {
					break;
				}
			}
		}
		return true;
	}

	/** A Buried Grave under the ground block below {@code top} (the first air above it), with a headstone when
	 * {@code stone} (else about a third of the time). False when the ground there will not hold one. */
	private static boolean grave(WorldGenLevel level, BlockPos top, RandomSource random, boolean stone, Direction facing) {
		BlockState above = level.getBlockState(top);
		if (!above.isAir() && !above.canBeReplaced() || !above.getFluidState().isEmpty()) {
			return false;
		}
		BlockState surface = level.getBlockState(top.below());
		if (BuriedGraveBlock.Soil.matching(surface) == null && !surface.is(Blocks.GRASS_BLOCK)) {
			return false;
		}
		BlockPos gravePos = top.below(2);
		BuriedGraveBlock.Soil soil = BuriedGraveBlock.Soil.matching(level.getBlockState(gravePos));
		if (soil == null || !level.getBlockState(top.below(3)).isSolid()) {
			return false;
		}
		level.setBlock(gravePos, ModBlocks.BURIED_GRAVE.defaultBlockState().setValue(BuriedGraveBlock.SOIL, soil), Block.UPDATE_CLIENTS);

		if (stone || random.nextFloat() < 0.35F) {
			level.setBlock(top, ModBlocks.WEATHERED_GRAVESTONE.defaultBlockState()
				.setValue(WeatheredGravestoneBlock.FACING, facing)
				.setValue(WeatheredGravestoneBlock.TILTED, random.nextBoolean()), Block.UPDATE_CLIENTS);
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos p = top.relative(side);
				BlockState floor = level.getBlockState(p.below());
				if (!level.getBlockState(p).isAir() || !floor.isFaceSturdy(level, p.below(), Direction.UP)) {
					continue;
				}
				float roll = random.nextFloat();
				if (roll < 0.18F) {
					level.setBlock(p, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + random.nextInt(3)), Block.UPDATE_CLIENTS);
				} else if (roll < 0.55F && floor.is(BlockTags.DIRT)) {
					level.setBlock(p, Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else if (roll < 0.4F && floor.is(BlockTags.SAND)) {
					level.setBlock(p, Blocks.DEAD_BUSH.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		return true;
	}
}
