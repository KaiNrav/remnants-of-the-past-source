package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A Crypt Eye: a carved stone eye set in an Ossuary wall, glowing while it watches. Only a projectile closes it - an
 * arrow, a trident, a snowball, the Bone Chakram - fists and swords glance off. When every eye near a gate is closed,
 * the gate rises ({@link OssuaryGateBlock#onEyeStruck}). Eyes are placed out of reach, so the puzzle is a ranged one.
 */
public class CryptEyeBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<CryptEyeBlock> CODEC = simpleCodec(CryptEyeBlock::new);
	public static final BooleanProperty STRUCK = BooleanProperty.create("struck");

	public CryptEyeBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STRUCK, false));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, STRUCK);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
		if (state.getValue(STRUCK) || !(level instanceof ServerLevel server)) {
			return;
		}
		BlockPos pos = hit.getBlockPos();
		level.setBlock(pos, state.setValue(STRUCK, true), Block.UPDATE_ALL);
		Direction f = state.getValue(FACING);
		double x = pos.getX() + 0.5 + f.getStepX() * 0.55, y = pos.getY() + 0.5, z = pos.getZ() + 0.5 + f.getStepZ() * 0.55;
		server.sendParticles(ParticleTypes.SOUL, x, y, z, 8, 0.15, 0.15, 0.15, 0.03);
		server.sendParticles(ParticleTypes.SMOKE, x, y, z, 6, 0.12, 0.12, 0.12, 0.01);
		level.playSound(null, pos, SoundEvents.ENDER_EYE_DEATH, SoundSource.BLOCKS, 1.0F, 0.6F);
		level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1.0F, 0.5F);
		OssuaryGateBlock.onEyeStruck(server, pos, projectile.getOwner() instanceof ServerPlayer sp ? sp : null);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(STRUCK) && random.nextInt(5) == 0) {
			Direction f = state.getValue(FACING);
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5 + f.getStepX() * 0.56 + (random.nextDouble() - 0.5) * 0.2,
				pos.getY() + 0.45 + random.nextDouble() * 0.15, pos.getZ() + 0.5 + f.getStepZ() * 0.56 + (random.nextDouble() - 0.5) * 0.2, 0, 0.005, 0);
		}
	}
}
