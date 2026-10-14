package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import dev.remnants.multiblock.Multiblocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A Focus Crystal: a soul crystal on a gold collar, the top of each of the Soul Astrolabe's four obelisks. */
public class FocusCrystalBlock extends FormableBlock {
	public static final MapCodec<FocusCrystalBlock> CODEC = simpleCodec(FocusCrystalBlock::new);
	// follows models/block/focus_crystal.json
	private static final VoxelShape SHAPE = Shapes2.of(new double[] {4, 0, 4, 12, 3, 12}, new double[] {5, 3, 5, 11, 13, 11});

	public FocusCrystalBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<FocusCrystalBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(Multiblocks.FORMED) && random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
				(random.nextDouble() - 0.5) * 0.02, 0.01, (random.nextDouble() - 0.5) * 0.02);
		}
	}
}
