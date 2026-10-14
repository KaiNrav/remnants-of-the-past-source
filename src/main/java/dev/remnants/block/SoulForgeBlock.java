package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import dev.remnants.RemnantsConfig;
import dev.remnants.item.SpectralForge;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Soul Forge, the "small forge" of the early game: hold a weapon and use the forge to raise its Spectral level by one
 * (the same upgrade an Empowered Remnant grants, now on purpose): +1 to every enchantment, more damage and speed for
 * melee weapons, fully repaired. Levels I and II cost 2 / 4 Condensed Souls; since 1.3 level III (6 and the Soul King's
 * Heart) is forged in the Soul Crucible, with a rhythm. Weapons already at III keep it.
 */
public class SoulForgeBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<SoulForgeBlock> CODEC = simpleCodec(SoulForgeBlock::new);
	private static final int[] COST = {2, 4, 6};
	public static final int SMALL_FORGE_MAX = 2;
	// follows models/block/soul_forge.json, authored facing north: stone hearth with fire mouths
	// on its north and south faces, anvil on top with the horn towards +X; rotated like the blockstate
	private static final double[][] BOXES = {
		{1, 0, 2, 15, 2, 14}, {2, 2, 3, 14, 9, 13}, {5, 9, 5, 11, 11, 11}, {3, 11, 4, 13, 15, 12},
		{13, 12, 5.5, 16, 15, 10.5}, {0.5, 13, 5, 3, 15, 11}};
	private static final java.util.Map<Direction, VoxelShape> SHAPES = new java.util.EnumMap<>(Direction.class);

	static {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			VoxelShape shape = Shapes.empty();
			for (double[] b : BOXES) {
				double[] r = b.clone();
				for (int k = 0; k < quarterTurns(facing); k++) {  // blockstate y+90: (x, z) -> (16 - z, x)
					r = new double[] {16 - r[5], r[1], r[0], 16 - r[2], r[4], r[3]};
				}
				shape = Shapes.or(shape, Block.box(r[0], r[1], r[2], r[3], r[4], r[5]));
			}
			SHAPES.put(facing, shape);
		}
	}

	private static int quarterTurns(Direction facing) {
		return switch (facing) {
			case EAST -> 1;
			case SOUTH -> 2;
			case WEST -> 3;
			default -> 0;
		};
	}

	public SoulForgeBlock(Properties properties) {
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
		return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getClockWise());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	/** Condensed Souls needed to reach {@code nextLevel} (1-based). */
	public static int cost(int nextLevel) {
		return COST[Math.min(nextLevel, COST.length) - 1];
	}

	public static boolean needsHeart(int nextLevel) {
		return nextLevel >= 3;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!SpectralForge.isWeapon(stack)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!(level instanceof ServerLevel server)) {
			return ItemInteractionResult.SUCCESS;
		}
		int max = RemnantsConfig.get().maxSpectralLevel;
		int next = SpectralForge.level(stack) + 1;
		if (next > max) {
			player.displayClientMessage(Component.translatable("message.remnants.forge.max").withStyle(ChatFormatting.GOLD), true);
			return ItemInteractionResult.CONSUME;
		}
		if (next > SMALL_FORGE_MAX) {   // 1.3: level III is forged in the Soul Crucible now
			player.displayClientMessage(Component.translatable("message.remnants.forge.crucible").withStyle(ChatFormatting.GOLD), true);
			return ItemInteractionResult.CONSUME;
		}
		int souls = cost(next);
		boolean heart = needsHeart(next);
		if (!player.getAbilities().instabuild) {
			Inventory inv = player.getInventory();
			if (count(inv, ModItems.CONDENSED_SOUL) < souls || heart && count(inv, ModItems.SOUL_KING_HEART) < 1) {
				player.displayClientMessage(Component.translatable(heart ? "message.remnants.forge.cost_heart" : "message.remnants.forge.cost",
					souls).withStyle(ChatFormatting.RED), true);
				server.playSound(null, pos, SoundEvents.VAULT_INSERT_ITEM_FAIL, SoundSource.BLOCKS, 0.8F, 0.8F);
				return ItemInteractionResult.CONSUME;
			}
			take(inv, ModItems.CONDENSED_SOUL, souls);
			if (heart) {
				take(inv, ModItems.SOUL_KING_HEART, 1);
			}
		}
		ItemStack upgraded = SpectralForge.upgrade(stack, level.registryAccess());
		player.setItemInHand(hand, upgraded);
		server.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 0.7F);
		server.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.5F, 0.8F);
		server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 25, 0.35, 0.2, 0.35, 0.03);
		server.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.02);
		player.displayClientMessage(Component.translatable("message.remnants.forge.done", upgraded.getHoverName()).withStyle(ChatFormatting.AQUA), true);
		return ItemInteractionResult.CONSUME;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide) {
			player.displayClientMessage(Component.translatable("message.remnants.forge.hint").withStyle(ChatFormatting.GRAY), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	private static int count(Inventory inv, Item item) {
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static void take(Inventory inv, Item item, int n) {
		for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				int k = Math.min(n, s.getCount());
				s.shrink(k);
				n -= k;
			}
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(4) == 0) {  // a flicker at one of the two fire mouths (model z = 3 / 13, x 5..11)
			double dx = (random.nextDouble() - 0.5) * 0.35, dz = random.nextBoolean() ? -0.33 : 0.33;
			for (int k = 0; k < quarterTurns(state.getValue(FACING)); k++) {
				double t = dx;
				dx = -dz;
				dz = t;
			}
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5 + dx, pos.getY() + 0.25 + random.nextDouble() * 0.1,
				pos.getZ() + 0.5 + dz, 0, 0.01, 0);
		}
	}
}
