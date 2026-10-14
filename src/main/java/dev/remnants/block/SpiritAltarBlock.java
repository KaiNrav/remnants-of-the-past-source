package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import dev.remnants.block.entity.SpiritAltarBlockEntity;
import dev.remnants.entity.SoulKingEntity;
import dev.remnants.registry.ModBlocks;
import dev.remnants.registry.ModEntities;
import dev.remnants.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * The Spirit Offering Table. With four Soul Pedestals in a cross five blocks away (same height, or one block up or
 * down), offering a Condensed Soul here summons the Soul King. Right-click with an empty hand to check the circle.
 * While the circle is incomplete, the client draws the missing pedestals as glowing ghosts (SpiritAltarRenderer).
 */
public class SpiritAltarBlock extends Block implements EntityBlock {
	public static final MapCodec<SpiritAltarBlock> CODEC = simpleCodec(SpiritAltarBlock::new);
	public static final int RADIUS = 5;
	private static final VoxelShape SHAPE = Shapes.or(
		Block.box(1, 0, 1, 15, 2, 15),
		Block.box(4, 2, 4, 12, 10, 12),
		Block.box(0, 10, 0, 16, 14, 16));

	public SpiritAltarBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SpiritAltarBlockEntity(pos, state);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Pedestal positions found around {@code center}; missing ones are reported through {@code missing}. */
	public static List<BlockPos> findPedestals(Level level, BlockPos center, List<BlockPos> missing) {
		List<BlockPos> found = new ArrayList<>();
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos spot = center.relative(dir, RADIUS);
			BlockPos hit = null;
			for (int dy : new int[]{0, 1, -1}) {
				if (level.getBlockState(spot.above(dy)).is(ModBlocks.SOUL_PEDESTAL)) {
					hit = spot.above(dy);
					break;
				}
			}
			if (hit != null) {
				found.add(hit);
			} else {
				missing.add(spot);
			}
		}
		return found;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(ModItems.CONDENSED_SOUL)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!(level instanceof ServerLevel server)) {
			return ItemInteractionResult.SUCCESS;
		}
		List<BlockPos> missing = new ArrayList<>();
		List<BlockPos> pedestals = findPedestals(level, pos, missing);
		if (!missing.isEmpty()) {
			showMissing(server, player, missing);
			return ItemInteractionResult.CONSUME;
		}
		if (!level.getEntitiesOfClass(SoulKingEntity.class, new AABB(pos).inflate(64)).isEmpty()) {
			player.displayClientMessage(Component.translatable("message.remnants.altar.busy").withStyle(ChatFormatting.RED), true);
			return ItemInteractionResult.CONSUME;
		}
		SoulKingEntity king = ModEntities.SOUL_KING.create(server);
		if (king == null) {
			return ItemInteractionResult.FAIL;
		}
		stack.consume(1, player);
		king.moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, player.getYRot() + 180, 0);
		king.beginRitual(pos, pedestals);
		server.addFreshEntity(king);
		server.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 2.0F, 0.6F);
		return ItemInteractionResult.CONSUME;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			List<BlockPos> missing = new ArrayList<>();
			findPedestals(level, pos, missing);
			if (missing.isEmpty()) {
				player.displayClientMessage(Component.translatable("message.remnants.altar.ready").withStyle(ChatFormatting.AQUA), true);
			} else {
				showMissing(server, player, missing);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	private static void showMissing(ServerLevel server, Player player, List<BlockPos> missing) {
		player.displayClientMessage(Component.translatable("message.remnants.altar.missing", missing.size()).withStyle(ChatFormatting.GOLD), true);
		for (BlockPos m : missing) {
			for (int i = 0; i < 12; i++) {
				server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, m.getX() + 0.5, m.getY() + 0.2 + i * 0.12, m.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.0);
			}
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.SOUL, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 0.95,
				pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0, 0.015, 0);
		}
	}
}
