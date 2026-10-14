package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import dev.remnants.Chronicle;
import dev.remnants.block.entity.EpitaphNicheBlockEntity;
import dev.remnants.block.entity.MemorialBlockEntity;
import dev.remnants.item.EpitaphData;
import dev.remnants.item.EpitaphItem;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.registry.ModComponents;
import dev.remnants.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * An Epitaph Niche: a soul-brick block with a recess for one tablet (the Memorial's wall is 15 of them). Right-click
 * with an Epitaph sets it in; right-click with an empty hand chisels in the newest entry of your Chronicle that is not
 * on this wall yet; Shift + right-click with an empty hand takes it out (a set-in tablet comes back, a chiselled one is
 * erased). The same epitaph twice on one wall is refused.
 */
public class EpitaphNicheBlock extends BaseEntityBlock {
	public static final MapCodec<EpitaphNicheBlock> CODEC = simpleCodec(EpitaphNicheBlock::new);
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	public EpitaphNicheBlock(Properties properties) {
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
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new EpitaphNicheBlockEntity(pos, state);
	}

	/** Whether the same epitaph is already on this wall (any niche in a 9 x 5 x 9 box round {@code pos}). */
	public static boolean onWall(Level level, BlockPos pos, EpitaphData data) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, -2, -4), pos.offset(4, 2, 4))) {
			if (!p.equals(pos) && level.getBlockEntity(p) instanceof EpitaphNicheBlockEntity n && data.equals(n.epitaph())) {
				return true;
			}
		}
		return false;
	}

	private static void changed(ServerLevel level, BlockPos pos) {
		if (Multiblocks.findHeart(level, pos) instanceof MemorialBlockEntity memorial) {
			memorial.refresh(level);
		} else {
			for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 0, 2))) {
				if (level.getBlockEntity(p) instanceof MemorialBlockEntity m) {
					m.refresh(level);
				}
			}
		}
	}

	private static void chime(ServerLevel level, BlockPos pos, Direction facing) {
		double x = pos.getX() + 0.5 + facing.getStepX() * 0.55, z = pos.getZ() + 0.5 + facing.getStepZ() * 0.55;
		level.sendParticles(ParticleTypes.SOUL, x, pos.getY() + 0.5, z, 8, 0.2, 0.2, 0.2, 0.01);
		level.playSound(null, pos, SoundEvents.CHISELED_BOOKSHELF_INSERT, SoundSource.BLOCKS, 1.0F, 0.7F);
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 0.6F);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(ModItems.EPITAPH)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!(level instanceof ServerLevel server) || !(level.getBlockEntity(pos) instanceof EpitaphNicheBlockEntity niche)) {
			return ItemInteractionResult.SUCCESS;
		}
		EpitaphData data = stack.get(ModComponents.EPITAPH);
		if (niche.epitaph() != null) {
			player.displayClientMessage(Component.translatable("message.remnants.niche.full").withStyle(ChatFormatting.GRAY), true);
			return ItemInteractionResult.CONSUME;
		}
		if (data == null) {
			return ItemInteractionResult.CONSUME;
		}
		if (onWall(level, pos, data)) {
			player.displayClientMessage(Component.translatable("message.remnants.niche.twice").withStyle(ChatFormatting.GRAY), true);
			return ItemInteractionResult.CONSUME;
		}
		niche.set(data, true);
		stack.consume(1, player);
		chime(server, pos, state.getValue(FACING));
		changed(server, pos);
		return ItemInteractionResult.CONSUME;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof EpitaphNicheBlockEntity niche)) {
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		if (player.isSecondaryUseActive()) {
			EpitaphData data = niche.epitaph();
			if (data == null) {
				return InteractionResult.PASS;
			}
			if (niche.fromItem()) {
				ItemStack out = EpitaphItem.stack(data);
				if (!player.getInventory().add(out)) {
					Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, out);
				}
			}
			niche.set(null, false);
			level.playSound(null, pos, SoundEvents.CHISELED_BOOKSHELF_PICKUP, SoundSource.BLOCKS, 1.0F, 0.7F);
			changed(server, pos);
			return InteractionResult.CONSUME;
		}
		if (niche.epitaph() != null) {
			return InteractionResult.PASS;
		}
		List<Chronicle.Entry> entries = Chronicle.entries(sp);
		for (int i = entries.size() - 1; i >= 0; i--) {
			EpitaphData data = entries.get(i).epitaph();
			if (!onWall(level, pos, data)) {
				niche.set(data, false);
				chime(server, pos, state.getValue(FACING));
				player.displayClientMessage(Component.translatable("message.remnants.niche.chiselled", data.name()).withStyle(ChatFormatting.AQUA), true);
				changed(server, pos);
				return InteractionResult.CONSUME;
			}
		}
		player.displayClientMessage(Component.translatable(entries.isEmpty() ? "message.remnants.niche.no_chronicle" : "message.remnants.niche.all_on_wall")
			.withStyle(ChatFormatting.GRAY), true);
		return InteractionResult.CONSUME;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock()) && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof EpitaphNicheBlockEntity niche
			&& niche.epitaph() != null && niche.fromItem()) {
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, EpitaphItem.stack(niche.epitaph()));
		}
		super.onRemove(state, level, pos, newState, moved);
	}
}
