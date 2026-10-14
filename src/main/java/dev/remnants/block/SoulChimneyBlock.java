package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import dev.remnants.multiblock.Multiblocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Soul Chimney: a brick stack; the top block of a stack flares into a cap (smoke and a faint glow when formed). */
public class SoulChimneyBlock extends Block {
	public static final MapCodec<SoulChimneyBlock> CODEC = simpleCodec(SoulChimneyBlock::new);
	public static final BooleanProperty CAP = BooleanProperty.create("cap");
	private static final VoxelShape BODY = Shapes2.of(new double[] {2, 0, 2, 14, 16, 14}, new double[] {1.5, 5, 1.5, 14.5, 6.5, 14.5});
	private static final VoxelShape CAPPED = Shapes2.of(new double[] {2, 0, 2, 14, 13, 14}, new double[] {1.5, 5, 1.5, 14.5, 6.5, 14.5},
		new double[] {1, 13, 1, 15, 16, 15});

	public SoulChimneyBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(CAP, true).setValue(Multiblocks.FORMED, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CAP, Multiblocks.FORMED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return this.defaultBlockState().setValue(CAP, !ctx.getLevel().getBlockState(ctx.getClickedPos().above()).is(this));
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		return dir == Direction.UP ? state.setValue(CAP, !neighbor.is(this)) : state;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(CAP) ? CAPPED : BODY;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(CAP) && state.getValue(Multiblocks.FORMED) && random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 1.0,
				pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3, 0, 0.04, 0);
		}
	}
}
