package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.registry.ModBlocks;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An Ossuary Gate: an iron portcullis bar plane (one block of a wider gate). Neighbouring gate blocks form one gate
 * (a "cluster", found by flood fill). It opens two ways, the whole cluster at once: an Ossuary Key used on it (the key is
 * spent), or every Crypt Eye within {@link #EYE_RANGE} blocks of the gate struck by a projectile. Open, the bars are drawn
 * up into the lintel: every block under a column's top one is cleared, the top one shows the raised bars' foot, and the
 * clients see the whole portcullis rise ({@link #EVENT_LIFT}, drawn by client/fx/GateLift). Practically unbreakable in
 * survival (hardness 50, drops nothing), like the vanilla vault.
 */
public class OssuaryGateBlock extends Block {
	public static final MapCodec<OssuaryGateBlock> CODEC = simpleCodec(OssuaryGateBlock::new);
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	/** The bottom row of a gate, where the bars end in spikes. */
	public static final BooleanProperty TIP = BooleanProperty.create("tip");
	public static final int EYE_RANGE = 8;
	/** Block event on a column's top block when the gate opens; its parameter is the column's height in blocks. */
	public static final int EVENT_LIFT = 1;

	/** Draws a column rising on the client (set by client/fx/GateLift; null on a dedicated server). */
	public interface Lift {
		void start(BlockPos top, Direction.Axis axis, int height);
	}

	public static Lift clientLift;
	private static final int MAX_CLUSTER = 96;
	private static final VoxelShape X_CLOSED = Block.box(0, 0, 6.5, 16, 16, 9.5);
	private static final VoxelShape Z_CLOSED = Block.box(6.5, 0, 0, 9.5, 16, 16);
	private static final VoxelShape X_OPEN = Block.box(0, 13, 6.5, 16, 16, 9.5);
	private static final VoxelShape Z_OPEN = Block.box(6.5, 13, 0, 9.5, 16, 16);

	public OssuaryGateBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(OPEN, false).setValue(TIP, true));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS, OPEN, TIP);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		Direction.Axis axis = ctx.getHorizontalDirection().getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
		BlockState below = ctx.getLevel().getBlockState(ctx.getClickedPos().below());
		return this.defaultBlockState().setValue(AXIS, axis).setValue(TIP, !below.is(this));
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		if (dir == Direction.DOWN) {
			return state.setValue(TIP, !neighbor.is(this));
		}
		return state;
	}

	/** A crypt piece turned by the jigsaw turns its gates with it (without this they stood across their doorways). */
	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return switch (rotation) {
			case CLOCKWISE_90, COUNTERCLOCKWISE_90 ->
				state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
			default -> state;
		};
	}

	@Override
	protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
		if (id == EVENT_LIFT) {
			if (level.isClientSide && clientLift != null) {
				clientLift.start(pos, state.getValue(AXIS), Math.max(1, param));
			}
			return true;
		}
		return super.triggerEvent(state, level, pos, id, param);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		boolean x = state.getValue(AXIS) == Direction.Axis.X;
		return state.getValue(OPEN) ? (x ? X_OPEN : Z_OPEN) : (x ? X_CLOSED : Z_CLOSED);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return state.getValue(OPEN) ? Shapes.empty() : this.getShape(state, level, pos, ctx);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return state.getValue(OPEN);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(ModItems.OSSUARY_KEY)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (state.getValue(OPEN)) {
			return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
		}
		if (level instanceof ServerLevel server) {
			stack.consume(1, player);
			level.playSound(null, pos, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1.0F, 0.8F);
			open(server, pos);
			if (player instanceof ServerPlayer sp) {
				HeartBlockEntity.award(sp, "open_sesame");
			}
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!state.getValue(OPEN) && !level.isClientSide) {
			int eyes = eyesFor(level, cluster(level, pos)).size();
			player.displayClientMessage(Component.translatable(eyes > 0 ? "message.remnants.gate.locked_eyes" : "message.remnants.gate.locked")
				.withStyle(ChatFormatting.GRAY), true);
			level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.8F, 0.7F);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	// ---- gates and eyes ------------------------------------------------------------------------

	/** Every gate block connected to {@code start} (6-neighbourhood, at most {@link #MAX_CLUSTER}). */
	public static List<BlockPos> cluster(BlockGetter level, BlockPos start) {
		List<BlockPos> out = new ArrayList<>();
		if (!level.getBlockState(start).is(ModBlocks.OSSUARY_GATE)) {
			return out;
		}
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		todo.add(start.immutable());
		seen.add(start.immutable());
		while (!todo.isEmpty() && out.size() < MAX_CLUSTER) {
			BlockPos p = todo.poll();
			out.add(p);
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				if (seen.add(n) && level.getBlockState(n).is(ModBlocks.OSSUARY_GATE)) {
					todo.add(n);
				}
			}
		}
		return out;
	}

	/** The Crypt Eyes that guard a gate: every eye within {@link #EYE_RANGE} of any of its blocks. */
	public static List<BlockPos> eyesFor(BlockGetter level, List<BlockPos> gate) {
		if (gate.isEmpty()) {
			return new ArrayList<>();
		}
		// one pass over the gate's box grown by the range (a cube per gate block re-read the same ~5000 blocks up to 96
		// times: a lag spike on every click / struck eye); an eye found counts if it is within range of some gate block
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		for (BlockPos g : gate) {
			x0 = Math.min(x0, g.getX());
			y0 = Math.min(y0, g.getY());
			z0 = Math.min(z0, g.getZ());
			x1 = Math.max(x1, g.getX());
			y1 = Math.max(y1, g.getY());
			z1 = Math.max(z1, g.getZ());
		}
		List<BlockPos> eyes = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(x0 - EYE_RANGE, y0 - EYE_RANGE, z0 - EYE_RANGE, x1 + EYE_RANGE, y1 + EYE_RANGE, z1 + EYE_RANGE)) {
			if (!level.getBlockState(p).is(ModBlocks.CRYPT_EYE)) {
				continue;
			}
			for (BlockPos g : gate) {
				if (Math.abs(p.getX() - g.getX()) <= EYE_RANGE && Math.abs(p.getY() - g.getY()) <= EYE_RANGE
					&& Math.abs(p.getZ() - g.getZ()) <= EYE_RANGE) {
					eyes.add(p.immutable());
					break;
				}
			}
			if (eyes.size() > 32) {
				break;
			}
		}
		return eyes;
	}

	/**
	 * Opens the whole gate {@code pos} belongs to: each column's bars rise into the lintel - the blocks under its top one
	 * are cleared, the top one opens (the raised bars' foot), and nearby clients see the column rise.
	 */
	public static void open(ServerLevel level, BlockPos pos) {
		List<BlockPos> gate = cluster(level, pos);
		Set<BlockPos> in = new HashSet<>(gate);
		for (BlockPos p : gate) {
			if (in.contains(p.above())) {
				continue;   // not a column's top
			}
			int height = 1;
			while (in.contains(p.below(height))) {
				height++;
			}
			for (int i = height - 1; i >= 1; i--) {
				BlockPos b = p.below(i);
				level.setBlock(b, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				if (level.random.nextInt(3) == 0) {
					level.sendParticles(ParticleTypes.SOUL, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 3, 0.3, 0.4, 0.3, 0.01);
				}
			}
			BlockState top = level.getBlockState(p);
			level.setBlock(p, top.setValue(OPEN, true).setValue(TIP, true), Block.UPDATE_ALL);
			level.blockEvent(p, top.getBlock(), EVENT_LIFT, Math.min(255, height));
		}
		if (!gate.isEmpty()) {
			BlockPos mid = gate.get(gate.size() / 2);
			level.playSound(null, mid, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.2F, 0.5F);
			level.playSound(null, mid, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
			level.playSound(null, mid, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 1.6F);
		}
	}

	/** An eye was struck at {@code eye}: every closed gate near it whose eyes are all struck now opens. */
	public static void onEyeStruck(ServerLevel level, BlockPos eye, ServerPlayer by) {
		Set<BlockPos> done = new HashSet<>();
		for (BlockPos p : BlockPos.betweenClosed(eye.offset(-EYE_RANGE, -EYE_RANGE, -EYE_RANGE), eye.offset(EYE_RANGE, EYE_RANGE, EYE_RANGE))) {
			BlockState s = level.getBlockState(p);
			if (!s.is(ModBlocks.OSSUARY_GATE) || s.getValue(OPEN) || done.contains(p)) {
				continue;
			}
			List<BlockPos> gate = cluster(level, p.immutable());
			done.addAll(gate);
			List<BlockPos> eyes = eyesFor(level, gate);
			if (eyes.isEmpty()) {
				continue;
			}
			boolean all = true;
			for (BlockPos e : eyes) {
				BlockState es = level.getBlockState(e);
				all &= es.is(ModBlocks.CRYPT_EYE) && es.getValue(CryptEyeBlock.STRUCK);
			}
			if (all) {
				open(level, gate.get(0));
				if (by != null) {
					HeartBlockEntity.award(by, "open_sesame");
				}
			}
		}
	}
}
