package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import dev.remnants.block.entity.BelfryBlockEntity;
import dev.remnants.block.entity.BellOfRestBlockEntity;
import dev.remnants.item.RitualHammerItem;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.registry.ModBlockEntities;
import dev.remnants.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Bell of Rest: gold and bone, hung from the belfry's roof (part of the Belfry, whose heart is the Rest Stone
 * below). The block model is the yoke; the bell itself swings in its block entity's renderer. At night, rung with a
 * Condensed Soul, it calls the Night of Memory ({@link BelfryBlockEntity}). Rung by hand or struck by a projectile it
 * only swings.
 */
public class BellOfRestBlock extends BaseEntityBlock {
	public static final MapCodec<BellOfRestBlock> CODEC = simpleCodec(BellOfRestBlock::new);
	public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	// follows models/block/bell_of_rest.json + the bell body
	static final VoxelShape SHAPE = Shapes2.of(new double[] {3, 2, 3, 13, 12, 13}, new double[] {5, 12, 5, 11, 16, 11});

	public BellOfRestBlock(Properties properties) {
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
		return new BellOfRestBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, ModBlockEntities.BELL_OF_REST, BellOfRestBlockEntity::tick);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (stack.getItem() instanceof RitualHammerItem) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!stack.is(ModItems.CONDENSED_SOUL)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level instanceof ServerLevel server) {
			if (Multiblocks.findHeart(level, pos) instanceof BelfryBlockEntity belfry) {
				belfry.tryStartNight(server, player, stack);
			} else {
				player.displayClientMessage(Component.translatable("message.remnants.belfry.unformed").withStyle(ChatFormatting.GOLD), true);
			}
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide) {
			ring(level, pos, hit.getDirection(), BellOfRestBlockEntity.RING, 0.7F);
			if (Multiblocks.findHeart(level, pos) instanceof BelfryBlockEntity belfry && !belfry.isWorking()) {
				player.displayClientMessage(Component.translatable("message.remnants.belfry.hint").withStyle(ChatFormatting.GRAY), true);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
		if (!level.isClientSide) {
			ring(level, hit.getBlockPos(), hit.getDirection(), BellOfRestBlockEntity.RING, 0.6F);
		}
	}

	/** Swing the bell at {@code pos} (server), with its two-note toll. */
	public static void ring(Level level, BlockPos pos, Direction from, int strength, float volume) {
		if (level.getBlockEntity(pos) instanceof BellOfRestBlockEntity bell) {
			bell.swing(from.getAxis().isHorizontal() ? from : Direction.NORTH, strength);
			level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, volume * (strength == BellOfRestBlockEntity.HUM ? 0.35F : 1.6F),
				strength == BellOfRestBlockEntity.HUM ? 0.5F : 0.6F);
			if (strength == BellOfRestBlockEntity.RING) {
				level.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, volume, 0.7F);
			}
		}
	}
}
