package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import dev.remnants.block.entity.SoulCrucibleBlockEntity;
import dev.remnants.item.RitualHammerItem;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Soul Crucible, heart of the 3x3x3 forge: an iron cradle on the hearth (the pot itself is drawn by the renderer
 * so it can tip into the trough). Right-click with a weapon, armour piece or tool to lay it on the anvil, with Condensed
 * Souls, the King's Heart or biome trophies to fill the pot; strike with the Ritual Hammer to forge (see
 * {@link SoulCrucibleBlockEntity}).
 */
public class SoulCrucibleBlock extends BaseEntityBlock {
	public static final MapCodec<SoulCrucibleBlock> CODEC = simpleCodec(SoulCrucibleBlock::new);
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape SHAPE = Shapes2.of(new double[] {1, 0, 1, 15, 1, 15}, new double[] {3, 1, 3, 13, 10.5, 13},
		new double[] {6, 1, 1, 10, 11, 15});

	public SoulCrucibleBlock(Properties properties) {
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
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SoulCrucibleBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.SOUL_CRUCIBLE, HeartBlockEntity::serverTick);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (stack.getItem() instanceof RitualHammerItem) {
			return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;   // the hammer is handled by UseBlockCallback
		}
		if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof SoulCrucibleBlockEntity crucible)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!dev.remnants.crafting.CrucibleRecipes.isWorkpiece(stack) && !dev.remnants.crafting.CrucibleRecipes.isMaterial(stack.getItem())) {
			return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;   // nothing for the forge: the item does its own thing (the Codex opens)
		}
		if (level instanceof ServerLevel server) {
			crucible.insert(server, player, hand, stack);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof SoulCrucibleBlockEntity crucible) {
			crucible.takeBack(server, player);
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
}
