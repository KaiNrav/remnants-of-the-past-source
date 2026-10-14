package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A soul-fire brazier: light, crackle and rising soul flames for crypts, altars and the King's arena. */
public class SoulBrazierBlock extends Block {
	public static final MapCodec<SoulBrazierBlock> CODEC = simpleCodec(SoulBrazierBlock::new);
	// follows models/block/soul_brazier.json: foot, stem, bowl
	private static final VoxelShape SHAPE = Shapes.or(
		Block.box(3.5, 0, 3.5, 12.5, 2, 12.5),
		Block.box(6.5, 2, 6.5, 9.5, 7, 9.5),
		Block.box(3, 7, 3, 13, 11, 13));

	public SoulBrazierBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(dev.remnants.multiblock.Multiblocks.FORMED, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	/** A corner of a locked Reflecting Pool: its bowl lights a soul seam (1.3). */
	@Override
	protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(dev.remnants.multiblock.Multiblocks.FORMED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + 0.5, y = pos.getY() + 0.65, z = pos.getZ() + 0.5;
		for (int i = 0; i < 2; i++) {
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + (random.nextDouble() - 0.5) * 0.5, y + random.nextDouble() * 0.3,
				z + (random.nextDouble() - 0.5) * 0.5, 0, 0.03, 0);
		}
		if (random.nextInt(6) == 0) {
			level.addParticle(ParticleTypes.SOUL, x, y + 0.3, z, 0, 0.04, 0);
		}
		if (random.nextInt(24) == 0) {
			level.playLocalSound(x, y, z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.3F, 0.6F + random.nextFloat() * 0.4F, false);
		}
	}
}
