package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import dev.remnants.block.entity.MemorialBlockEntity;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Memorial Projector: the heart of the Memorial (see {@link Multiblocks#memorial()}), a low soul-stone pedestal with
 * a lens of soul glass. Face it toward where you stand, the wall goes behind it. Strike it with the Ritual Hammer to
 * lock the Memorial; right-click with an empty hand to play your last record.
 */
public class MemorialProjectorBlock extends BaseEntityBlock {
	public static final MapCodec<MemorialProjectorBlock> CODEC = simpleCodec(MemorialProjectorBlock::new);
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	// follows models/block/memorial_projector.json
	private static final VoxelShape SHAPE = Shapes2.of(new double[] {1, 0, 1, 15, 3, 15}, new double[] {3, 3, 3, 13, 10, 13},
		new double[] {2, 10, 2, 14, 12, 14});

	public MemorialProjectorBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(Multiblocks.FORMED, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	/** Turned with the structure piece it stands in (BaseEntityBlock does not do it for FACING). */
	@Override
	protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
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
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MemorialBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.MEMORIAL, HeartBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof MemorialBlockEntity memorial) {
			memorial.play(server, sp);
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
			level.addParticle(ParticleTypes.SOUL, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.8,
				pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
		}
	}
}
