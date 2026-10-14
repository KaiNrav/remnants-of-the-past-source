package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import dev.remnants.block.entity.ReflectingPoolBlockEntity;
import dev.remnants.item.RitualHammerItem;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
 * The Reflecting Basin, heart of the Reflecting Pool: a 5x5 ring of chiseled soul bricks with soul braziers at the
 * corners, water inside, this basin in the middle. Strike it with the Ritual Hammer to lock the pool; then the item to
 * take an enchantment from floats on the water, the item to receive it rests in the basin (see
 * {@link ReflectingPoolBlockEntity}).
 */
public class ReflectingBasinBlock extends BaseEntityBlock implements net.minecraft.world.level.block.SimpleWaterloggedBlock {
	public static final MapCodec<ReflectingBasinBlock> CODEC = simpleCodec(ReflectingBasinBlock::new);
	private static final VoxelShape SHAPE = Shapes2.of(new double[] {2, 0, 2, 14, 2, 14}, new double[] {5, 2, 5, 11, 12, 11},
		new double[] {3.5, 12, 3.5, 12.5, 14, 12.5}, new double[] {1, 14, 1, 15, 18, 15});

	public static final net.minecraft.world.level.block.state.properties.BooleanProperty WATERLOGGED =
		net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;

	public ReflectingBasinBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(Multiblocks.FORMED, false).setValue(WATERLOGGED, false));
	}

	/** It stands in the pool's water: waterlogged, so the water fills its cell (no inner water faces round it). */
	@Override
	public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
		return this.defaultBlockState().setValue(WATERLOGGED,
			ctx.getLevel().getFluidState(ctx.getClickedPos()).getType() == net.minecraft.world.level.material.Fluids.WATER);
	}

	@Override
	protected net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? net.minecraft.world.level.material.Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected BlockState updateShape(BlockState state, net.minecraft.core.Direction dir, BlockState neighbor, net.minecraft.world.level.LevelAccessor level,
		BlockPos pos, BlockPos neighborPos) {
		if (state.getValue(WATERLOGGED)) {
			level.scheduleTick(pos, net.minecraft.world.level.material.Fluids.WATER, net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
		}
		return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(Multiblocks.FORMED, WATERLOGGED);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ReflectingPoolBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.REFLECTING_POOL, HeartBlockEntity::serverTick);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (stack.getItem() instanceof RitualHammerItem) {
			return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;   // the hammer is handled by UseBlockCallback
		}
		if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof ReflectingPoolBlockEntity pool)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!ReflectingPoolBlockEntity.isTarget(stack) && dev.remnants.crafting.EnchantTransfer.options(stack).isEmpty()) {
			return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;   // nothing for the pool: the item does its own thing (the Codex opens)
		}
		if (level instanceof ServerLevel server) {
			pool.insert(server, player, hand, stack);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof ReflectingPoolBlockEntity pool) {
			pool.takeBack(server, player);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
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
		if (state.getValue(Multiblocks.FORMED) && random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.SOUL, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5, pos.getY() + 1.05,
				pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
		}
	}
}
