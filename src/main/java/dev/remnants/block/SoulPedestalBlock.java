package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A soul pedestal: four of them in a cross around the Spirit Offering Table form the Soul King's summoning circle. */
public class SoulPedestalBlock extends Block {
	public static final MapCodec<SoulPedestalBlock> CODEC = simpleCodec(SoulPedestalBlock::new);
	// follows models/block/soul_pedestal.json: plinth, step, column, top slab, crowned skull
	private static final VoxelShape SHAPE = Shapes.or(
		Block.box(1, 0, 1, 15, 2, 15),
		Block.box(2, 2, 2, 14, 3, 14),
		Block.box(3, 3, 3, 13, 13, 13),
		Block.box(2, 13, 2, 14, 15, 14),
		Block.box(5, 15, 5, 11, 21, 11));

	public SoulPedestalBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 1.42,
				pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
		}
	}
}
