package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import dev.remnants.block.entity.BelfryBlockEntity;
import dev.remnants.item.RitualHammerItem;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.registry.ModBlockEntities;
import dev.remnants.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Rest Stone: the heart of the Belfry (3 x 3 x 5, see {@link Multiblocks#belfry()}). Strike it with the Ritual
 * Hammer to lock the tower; then, at night, a Condensed Soul on it (or on the bell) calls the Night of Memory.
 */
public class RestStoneBlock extends BaseEntityBlock {
	public static final MapCodec<RestStoneBlock> CODEC = simpleCodec(RestStoneBlock::new);
	// follows models/block/rest_stone.json
	private static final VoxelShape SHAPE = Shapes2.of(new double[] {0, 0, 0, 16, 2, 16}, new double[] {1, 2, 1, 15, 7, 15},
		new double[] {0.5, 7, 0.5, 15.5, 9, 15.5});

	public RestStoneBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(Multiblocks.FORMED, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(Multiblocks.FORMED);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BelfryBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.BELFRY, HeartBlockEntity::serverTick);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (stack.getItem() instanceof RitualHammerItem || !stack.is(ModItems.CONDENSED_SOUL)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof BelfryBlockEntity belfry) {
			belfry.tryStartNight(server, player, stack);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock()) && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof HeartBlockEntity heart) {
			heart.onHeartRemoved(server);
		}
		super.onRemove(state, level, pos, newState, moved);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(Multiblocks.FORMED) && random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.SOUL, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.6,
				pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.015, 0);
		}
	}
}
