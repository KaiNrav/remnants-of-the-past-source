package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A weathered gravestone: mossy, chipped, sometimes leaning ({@link #TILTED}). Some Forgotten Graves have one above
 * them - the only sign of the grave you can see without a Dowser. Also a decorative block (stonecutter, mossy
 * cobblestone).
 */
public class WeatheredGravestoneBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<WeatheredGravestoneBlock> CODEC = simpleCodec(WeatheredGravestoneBlock::new);
	public static final BooleanProperty TILTED = BooleanProperty.create("tilted");
	// follows models/block/weathered_gravestone.json: a slab 10 wide, 3 thick, 14 tall
	private static final VoxelShape NORTH_SOUTH = Block.box(3, 0, 6, 13, 14, 10);
	private static final VoxelShape EAST_WEST = Block.box(6, 0, 3, 10, 14, 13);

	public WeatheredGravestoneBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TILTED, false));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, TILTED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite())
			.setValue(TILTED, ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTH_SOUTH : EAST_WEST;
	}
}
