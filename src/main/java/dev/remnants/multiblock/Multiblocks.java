package dev.remnants.multiblock;

import dev.remnants.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The 1.3 multiblocks. Blocks of a locked structure carry {@link #FORMED} (their soul seam lights up); the heart's
 * block entity holds the pattern, its rotation and the craft.
 *
 * <pre>
 * Reflecting Pool (5x5, one layer)        Soul Crucible (3x3x3, front = north = top row)
 *   B C C C B                               y-1: cauldron  .      anvil     y0: .        .        .
 *   C w w w C    B brazier, C chiseled            bellows  hearth bricks        .        CRUCIBLE trough
 *   C w H w C    bricks, w water source,          bricks   bricks bricks        bricks   chimney  bricks
 *   C w w w C    H Reflecting Basin         y+1: chimney cap above the chimney
 *   B C C C B
 * </pre>
 */
public final class Multiblocks {
	public static final BooleanProperty FORMED = BooleanProperty.create("formed");
	/** Farthest any part stands from its heart, sideways and up / down, for finding the heart of a clicked block. */
	public static final int REACH = 2, REACH_Y = 4;

	private static MultiblockPattern pool;
	private static MultiblockPattern crucible;
	private static MultiblockPattern belfry;
	private static MultiblockPattern memorial;
	private static MultiblockPattern astrolabe;

	public static MultiblockPattern pool() {
		if (pool == null) {
			List<MultiblockPattern.Part> parts = new ArrayList<>();
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					BlockPos o = new BlockPos(x, 0, z);
					if (Math.abs(x) == 2 && Math.abs(z) == 2) {
						parts.add(part(o, s -> s.is(ModBlocks.SOUL_BRAZIER), ModBlocks.SOUL_BRAZIER.defaultBlockState(), null));
					} else if (Math.abs(x) == 2 || Math.abs(z) == 2) {
						parts.add(part(o, s -> s.is(ModBlocks.CHISELED_SOUL_BRICKS), ModBlocks.CHISELED_SOUL_BRICKS.defaultBlockState(), null));
					} else if (x != 0 || z != 0) {
						parts.add(part(o, Multiblocks::isWaterSource, Blocks.WATER.defaultBlockState(), null));
					}
				}
			}
			pool = new MultiblockPattern(parts, true);
		}
		return pool;
	}

	public static MultiblockPattern crucible() {
		if (crucible == null) {
			Predicate<BlockState> bricks = s -> s.is(ModBlocks.SOUL_BRICKS) || s.is(ModBlocks.CHISELED_SOUL_BRICKS);
			BlockState brick = ModBlocks.SOUL_BRICKS.defaultBlockState();
			List<MultiblockPattern.Part> parts = new ArrayList<>();
			// ground layer: the quench cauldron and the anvil in front, bellows - hearth - bricks, a brick back wall
			parts.add(part(new BlockPos(-1, -1, -1), s -> s.is(Blocks.WATER_CAULDRON),
				Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), null));
			parts.add(part(new BlockPos(1, -1, -1), s -> s.is(BlockTags.ANVIL), Blocks.ANVIL.defaultBlockState(), null));
			parts.add(part(new BlockPos(-1, -1, 0), s -> s.is(ModBlocks.SOUL_BELLOWS),
				ModBlocks.SOUL_BELLOWS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), Direction.EAST));
			parts.add(part(new BlockPos(0, -1, 0), s -> s.is(ModBlocks.SOUL_HEARTH), ModBlocks.SOUL_HEARTH.defaultBlockState(), Direction.NORTH));
			parts.add(part(new BlockPos(1, -1, 0), bricks, brick, null));
			for (int x = -1; x <= 1; x++) {
				parts.add(part(new BlockPos(x, -1, 1), bricks, brick, null));
			}
			// the crucible's own layer: the trough east of it (spout over the anvil), chimney and bricks behind
			parts.add(part(new BlockPos(1, 0, 0), s -> s.is(ModBlocks.SOUL_TROUGH), ModBlocks.SOUL_TROUGH.defaultBlockState(), Direction.NORTH));
			parts.add(part(new BlockPos(-1, 0, 1), bricks, brick, null));
			parts.add(part(new BlockPos(1, 0, 1), bricks, brick, null));
			parts.add(part(new BlockPos(0, 0, 1), s -> s.is(ModBlocks.SOUL_CHIMNEY),
				ModBlocks.SOUL_CHIMNEY.defaultBlockState().setValue(dev.remnants.block.SoulChimneyBlock.CAP, false), null));
			parts.add(part(new BlockPos(0, 1, 1), s -> s.is(ModBlocks.SOUL_CHIMNEY),
				ModBlocks.SOUL_CHIMNEY.defaultBlockState().setValue(dev.remnants.block.SoulChimneyBlock.CAP, true), null));
			crucible = new MultiblockPattern(parts, false);
		}
		return crucible;
	}

	/**
	 * The Belfry (1.4), 3 x 3 x 5 round its heart, the Rest Stone, at the bottom centre:
	 * <pre>
	 * y0: brick   brazier brick     y1..y3: a post at each corner    y3: the Bell of Rest in the middle
	 *     brazier STONE   brazier                                    y4: a roof of 9 wooden slabs
	 *     brick   brazier brick
	 * </pre>
	 * Posts may be wooden fences, logs or soul brick walls, the roof any wooden slab (the "wooden frame"); the shape is
	 * the same from every side.
	 */
	public static MultiblockPattern belfry() {
		if (belfry == null) {
			Predicate<BlockState> bricks = s -> s.is(ModBlocks.SOUL_BRICKS) || s.is(ModBlocks.CHISELED_SOUL_BRICKS);
			List<MultiblockPattern.Part> parts = new ArrayList<>();
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					boolean corner = x != 0 && z != 0;
					if (corner) {
						parts.add(part(new BlockPos(x, 0, z), bricks, ModBlocks.SOUL_BRICKS.defaultBlockState(), null));
						for (int y = 1; y <= 3; y++) {
							parts.add(part(new BlockPos(x, y, z), Multiblocks::isPost, Blocks.DARK_OAK_FENCE.defaultBlockState(), null));
						}
					} else if (x != 0 || z != 0) {
						parts.add(part(new BlockPos(x, 0, z), s -> s.is(ModBlocks.SOUL_BRAZIER), ModBlocks.SOUL_BRAZIER.defaultBlockState(), null));
					}
					parts.add(part(new BlockPos(x, 4, z), s -> s.is(BlockTags.WOODEN_SLABS), Blocks.DARK_OAK_SLAB.defaultBlockState(), null));
				}
			}
			parts.add(part(new BlockPos(0, 3, 0), s -> s.is(ModBlocks.BELL_OF_REST), ModBlocks.BELL_OF_REST.defaultBlockState(), null));
			belfry = new MultiblockPattern(parts, true);
		}
		return belfry;
	}

	/**
	 * The Memorial (1.5): the Memorial Projector (heart) and, two blocks behind it, a wall 5 wide and 3 high of Epitaph
	 * Niches facing it (15 tablets). Authored with the wall south of the projector (the front, north, is where you stand).
	 * <pre>
	 * z+2:  N N N N N   (y 0..2)
	 * z+1:  . . . . .
	 * z 0:  . . P . .
	 * </pre>
	 */
	public static MultiblockPattern memorial() {
		if (memorial == null) {
			List<MultiblockPattern.Part> parts = new ArrayList<>();
			BlockState niche = ModBlocks.EPITAPH_NICHE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
			for (int y = 0; y <= 2; y++) {
				for (int x = -2; x <= 2; x++) {
					parts.add(part(new BlockPos(x, y, 2), s -> s.is(ModBlocks.EPITAPH_NICHE), niche, Direction.NORTH));
				}
			}
			memorial = new MultiblockPattern(parts, false);
		}
		return memorial;
	}

	/**
	 * The Soul Astrolabe (1.5), 3 x 3 x 3 round the sphere (heart) standing on a column of chiseled soul bricks; four
	 * obelisks at the corners: soul bricks, a soul brick wall, a Focus Crystal on top. The same from every side.
	 * <pre>
	 * y-1: bricks . bricks    y0: wall   .      wall    y+1: crystal . crystal
	 *      .   chiseled .         .   ASTROLABE  .            .     .     .
	 *      bricks . bricks        wall   .      wall          crystal . crystal
	 * </pre>
	 */
	public static MultiblockPattern astrolabe() {
		if (astrolabe == null) {
			Predicate<BlockState> bricks = s -> s.is(ModBlocks.SOUL_BRICKS) || s.is(ModBlocks.CHISELED_SOUL_BRICKS);
			List<MultiblockPattern.Part> parts = new ArrayList<>();
			parts.add(part(new BlockPos(0, -1, 0), s -> s.is(ModBlocks.CHISELED_SOUL_BRICKS), ModBlocks.CHISELED_SOUL_BRICKS.defaultBlockState(), null));
			for (int x = -1; x <= 1; x += 2) {
				for (int z = -1; z <= 1; z += 2) {
					parts.add(part(new BlockPos(x, -1, z), bricks, ModBlocks.SOUL_BRICKS.defaultBlockState(), null));
					parts.add(part(new BlockPos(x, 0, z), s -> s.is(ModBlocks.SOUL_BRICK_WALL), ModBlocks.SOUL_BRICK_WALL.defaultBlockState(), null));
					parts.add(part(new BlockPos(x, 1, z), s -> s.is(ModBlocks.FOCUS_CRYSTAL), ModBlocks.FOCUS_CRYSTAL.defaultBlockState(), null));
				}
			}
			astrolabe = new MultiblockPattern(parts, true);
		}
		return astrolabe;
	}

	private static MultiblockPattern.Part part(BlockPos offset, Predicate<BlockState> test, BlockState ghost, @Nullable Direction facing) {
		return new MultiblockPattern.Part(offset, test, ghost, facing);
	}

	/** A belfry post: a wooden fence, a log or a soul brick wall. */
	public static boolean isPost(BlockState s) {
		return s.is(BlockTags.WOODEN_FENCES) || s.is(BlockTags.LOGS) || s.is(ModBlocks.SOUL_BRICK_WALL);
	}

	public static boolean isWaterSource(BlockState s) {
		return s.is(Blocks.WATER) && s.getFluidState().is(Fluids.WATER) && s.getFluidState().isSource();
	}

	/** The formed heart whose structure includes {@code pos} (the heart itself counts), or null. */
	@Nullable
	public static HeartBlockEntity findHeart(Level level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof HeartBlockEntity heart) {
			return heart;
		}
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-REACH, -REACH_Y, -REACH), pos.offset(REACH, REACH_Y, REACH))) {
			BlockEntity be = level.getBlockEntity(p);
			if (be instanceof HeartBlockEntity heart && heart.isFormed() && heart.pattern().contains(heart.getBlockPos(), heart.rotation(), pos)) {
				return heart;
			}
		}
		return null;
	}

	private Multiblocks() {
	}
}
