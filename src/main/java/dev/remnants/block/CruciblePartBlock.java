package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import dev.remnants.multiblock.Multiblocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * A block of the Soul Crucible (hearth, bellows, trough): faces a direction (the structure turns it the right way when
 * it locks, and the Ritual Hammer turns it by hand) and lights its soul seam while {@code formed}. Shapes follow the
 * block models, authored facing north.
 */
public abstract class CruciblePartBlock extends HorizontalDirectionalBlock {
	private final Map<Direction, VoxelShape> shapes;

	protected CruciblePartBlock(Properties properties, Map<Direction, VoxelShape> shapes) {
		super(properties);
		this.shapes = shapes;
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(Multiblocks.FORMED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, Multiblocks.FORMED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.shapes.get(state.getValue(FACING));
	}

	/** Soul Hearth: the firebox under the crucible; soul fire burns in its mouth once the forge is locked. */
	public static class Hearth extends CruciblePartBlock {
		public static final MapCodec<Hearth> CODEC = simpleCodec(Hearth::new);

		public Hearth(Properties properties) {
			super(properties, Shapes2.horizontal(new double[] {0, 0, 0, 16, 16, 16}));
		}

		@Override
		protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
			return CODEC;
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (!state.getValue(Multiblocks.FORMED)) {
				return;
			}
			Direction front = state.getValue(FACING);
			double x = pos.getX() + 0.5 + front.getStepX() * 0.45, z = pos.getZ() + 0.5 + front.getStepZ() * 0.45;
			if (random.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 0.25,
					z + (random.nextDouble() - 0.5) * 0.3, 0, 0.01, 0);
			}
			if (random.nextInt(30) == 0) {
				level.playLocalSound(x, pos.getY() + 0.3, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.6F, 0.7F, false);
			}
		}
	}

	/** Soul Bellows: nozzle forward, into the hearth; the pumping board is drawn by the crucible while it works. */
	public static class Bellows extends CruciblePartBlock {
		public static final MapCodec<Bellows> CODEC = simpleCodec(Bellows::new);

		public Bellows(Properties properties) {
			super(properties, Shapes2.horizontal(new double[] {1, 0, 2, 15, 11, 14}, new double[] {6.5, 6.5, 0, 9.5, 8.5, 2}));
		}

		@Override
		protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
			return CODEC;
		}
	}

	/** Soul Trough: takes the pour from the crucible's lip and spills it forward, onto the anvil. */
	public static class Trough extends CruciblePartBlock {
		public static final MapCodec<Trough> CODEC = simpleCodec(Trough::new);

		public Trough(Properties properties) {
			super(properties, Shapes2.horizontal(new double[] {1, 0, 1, 15, 2, 15}, new double[] {3, 2, 3, 13, 5, 13},
				new double[] {0, 5, 5, 11, 8, 11}, new double[] {5, 5, 0, 11, 8, 5}));
		}

		@Override
		protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
			return CODEC;
		}
	}
}
