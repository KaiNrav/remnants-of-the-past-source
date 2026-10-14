package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import dev.remnants.block.entity.AstrolabeBlockEntity;
import dev.remnants.item.RitualHammerItem;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * The Soul Astrolabe: a bronze armillary sphere on a stand, the heart of the Astrolabe (see {@link Multiblocks#astrolabe()}).
 * The stand is the block model; its three rings turn in the block entity's renderer. Right-click with a focus sets it on
 * the sphere, right-click with an empty hand searches, Shift + right-click takes the focus back.
 */
public class SoulAstrolabeBlock extends BaseEntityBlock {
	public static final MapCodec<SoulAstrolabeBlock> CODEC = simpleCodec(SoulAstrolabeBlock::new);
	private static final VoxelShape SHAPE = Shapes2.of(new double[] {2, 0, 2, 14, 3, 14}, new double[] {6, 3, 6, 10, 6, 10},
		new double[] {1, 6, 1, 15, 16, 15});

	public SoulAstrolabeBlock(Properties properties) {
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
		return new AstrolabeBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.ASTROLABE, HeartBlockEntity::serverTick);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (stack.getItem() instanceof RitualHammerItem || !AstrolabeBlockEntity.isFocus(stack)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof AstrolabeBlockEntity astrolabe) {
			astrolabe.insert(server, player, stack);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof AstrolabeBlockEntity astrolabe) {
			if (player.isSecondaryUseActive()) {
				astrolabe.takeFocus(server, player);
			} else {
				astrolabe.search(server, sp);
			}
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
