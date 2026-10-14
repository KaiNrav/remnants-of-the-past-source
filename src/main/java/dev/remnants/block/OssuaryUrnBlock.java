package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An Ossuary Urn: a glazed burial urn with a bone band and a gold lid. Break it and it spills what it held
 * (loot table {@code remnants_of_the_past:blocks/ossuary_urn}: bones, nuggets, now and then a Soul Fragment). Some of the urns in an
 * Ossuary are not urns at all - see {@link dev.remnants.entity.GraveMimicEntity}, which is drawn from this block's models.
 */
public class OssuaryUrnBlock extends Block {
	public static final MapCodec<OssuaryUrnBlock> CODEC = simpleCodec(OssuaryUrnBlock::new);
	// follows models/block/ossuary_urn.json
	private static final VoxelShape SHAPE = Shapes2.of(new double[] {5, 0, 5, 11, 1, 11}, new double[] {4, 1, 4, 12, 3, 12},
		new double[] {3, 3, 3, 13, 10, 13}, new double[] {4, 10, 4, 12, 12, 12}, new double[] {4.5, 12, 4.5, 11.5, 14, 11.5},
		new double[] {7, 14, 7, 9, 15.5, 9});

	public OssuaryUrnBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}
}
