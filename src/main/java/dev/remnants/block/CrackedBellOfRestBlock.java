package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import dev.remnants.registry.ModBlocks;
import dev.remnants.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Cracked Bell of Rest, hanging in a Ruined Belfry: a split gold-and-bone bell. Right-click it with a Condensed Soul
 * (and {@link #GOLD} gold ingots in the inventory) to recast it into a Bell of Rest - the first step towards a Night of
 * Memory.
 */
public class CrackedBellOfRestBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<CrackedBellOfRestBlock> CODEC = simpleCodec(CrackedBellOfRestBlock::new);
	public static final int GOLD = 2;

	public CrackedBellOfRestBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return BellOfRestBlock.SHAPE;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(ModItems.CONDENSED_SOUL)) {
			if (!level.isClientSide && stack.isEmpty()) {
				player.displayClientMessage(Component.translatable("message.remnants.belfry.cracked", GOLD).withStyle(ChatFormatting.GRAY), true);
			}
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level instanceof ServerLevel server) {
			if (!player.hasInfiniteMaterials() && player.getInventory().countItem(Items.GOLD_INGOT) < GOLD) {
				player.displayClientMessage(Component.translatable("message.remnants.belfry.cracked", GOLD).withStyle(ChatFormatting.GOLD), true);
				return ItemInteractionResult.SUCCESS;
			}
			if (!player.hasInfiniteMaterials()) {
				int left = GOLD;
				for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
					ItemStack s = player.getInventory().getItem(i);
					if (s.is(Items.GOLD_INGOT)) {
						int take = Math.min(left, s.getCount());
						s.shrink(take);
						left -= take;
					}
				}
			}
			stack.consume(1, player);
			level.setBlock(pos, ModBlocks.BELL_OF_REST.defaultBlockState().setValue(BellOfRestBlock.FACING, state.getValue(FACING)), Block.UPDATE_ALL);
			server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 24, 0.3, 0.3, 0.3, 0.02);
			server.sendParticles(ParticleTypes.WAX_OFF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 16, 0.3, 0.3, 0.3, 0.5);
			level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 1.2F);
			BellOfRestBlock.ring(level, pos, Direction.NORTH, dev.remnants.block.entity.BellOfRestBlockEntity.RING, 0.8F);
			player.displayClientMessage(Component.translatable("message.remnants.belfry.recast").withStyle(ChatFormatting.AQUA), true);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}
}
