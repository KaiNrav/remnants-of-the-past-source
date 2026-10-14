package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import dev.remnants.block.entity.BurialCasketBlockEntity;
import dev.remnants.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Burial Casket: a dark-wood reliquary bound in iron with a bone plaque. One rises out of every Forgotten Grave
 * that is dug up (drawn by its block entity renderer while {@link #RISING}); opening it the first time spills the
 * dead adventurer's belongings - and sometimes wakes them. Afterwards (or when crafted / placed) it is furniture
 * whose lid opens and closes.
 */
public class BurialCasketBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final MapCodec<BurialCasketBlock> CODEC = simpleCodec(BurialCasketBlock::new);
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	public static final BooleanProperty RISING = BooleanProperty.create("rising");
	public static final int RISE_TICKS = 18;
	private static final VoxelShape NORTH_SOUTH = Block.box(1, 0, 3, 15, 11, 13);
	private static final VoxelShape EAST_WEST = Block.box(3, 0, 1, 13, 11, 15);

	public BurialCasketBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(RISING, false));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OPEN, RISING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTH_SOUTH : EAST_WEST;
	}

	/** While rising the block entity renderer draws it (climbing out of the hole); otherwise the block model. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return state.getValue(RISING) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BurialCasketBlockEntity(pos, state);
	}

	/**
	 * A grave was dug up at {@code pos}: a sealed casket climbs out of the ground with a puff of the soil it lay in.
	 * {@code soil} is the grave block that was broken (for the particles).
	 */
	public static void raise(ServerLevel level, BlockPos pos, Direction facing, BlockState soil) {
		BlockState state = ModBlocks.BURIAL_CASKET.defaultBlockState().setValue(FACING, facing).setValue(RISING, true);
		level.setBlock(pos, state, Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof BurialCasketBlockEntity casket) {
			casket.seal(level.getGameTime());
		}
		level.scheduleTick(pos, state.getBlock(), RISE_TICKS);
		BlockState dust = soil.is(ModBlocks.BURIED_GRAVE) ? switch (soil.getValue(BuriedGraveBlock.SOIL)) {
			case SAND -> Blocks.SAND.defaultBlockState();
			case RED_SAND -> Blocks.RED_SAND.defaultBlockState();
			case GRAVEL -> Blocks.GRAVEL.defaultBlockState();
			default -> Blocks.DIRT.defaultBlockState();
		} : soil;
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dust), pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5,
			40, 0.4, 0.2, 0.4, 0.15);
		level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
		level.playSound(null, pos, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
		level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.2F, 0.6F);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(RISING)) {
			level.setBlock(pos, state.setValue(RISING, false), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.7F);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(RISING)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp
			&& level.getBlockEntity(pos) instanceof BurialCasketBlockEntity casket) {
			casket.use(server, sp);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** A player breaking a sealed casket disturbs the dead exactly as opening it would (loot, owner, advancement). */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp
			&& level.getBlockEntity(pos) instanceof BurialCasketBlockEntity casket) {
			casket.disturb(server, sp);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/** Blown up (or removed any other way) before it was opened, the casket still gives up what it held - nobody wakes. */
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
			&& level.getBlockEntity(pos) instanceof BurialCasketBlockEntity casket) {
			casket.spill(server, null);
		}
		super.onRemove(state, level, pos, newState, moved);
	}
}
