package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import dev.remnants.RemnantTracker;
import dev.remnants.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Soul Anchor: feed it a Condensed Soul and your next Remnant rises here instead of where you fell (in lava, over
 * the void, deep in a dungeon). Each soul is one charge, up to three; a charge is spent per death. Binding to a new
 * anchor moves your charges there. Lit while anyone has charges bound to it.
 *
 * <p>1.5: every anchor you touch joins your network ({@link dev.remnants.AnchorNetwork}); a renamed anchor (anvil, or a
 * Name Tag on the block) is listed by its name. Shift + right-click with an empty hand opens the Anchors screen.
 */
public class SoulAnchorBlock extends Block {
	public static final MapCodec<SoulAnchorBlock> CODEC = simpleCodec(SoulAnchorBlock::new);
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public SoulAnchorBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (placer instanceof net.minecraft.server.level.ServerPlayer sp) {
			Component name = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
			dev.remnants.AnchorNetwork.attune(sp, pos, name == null ? null : name.getString());
		}
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (stack.is(net.minecraft.world.item.Items.NAME_TAG) && stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
			if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
				dev.remnants.AnchorNetwork.attune(sp, pos, stack.getHoverName().getString());
			}
			return ItemInteractionResult.sidedSuccess(level.isClientSide);
		}
		if (!stack.is(ModItems.CONDENSED_SOUL)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!(level instanceof ServerLevel server)) {
			return ItemInteractionResult.SUCCESS;
		}
		int charges = RemnantTracker.get(server.getServer()).chargeAnchor(player.getUUID(), level.dimension(), pos);
		if (charges < 0) {
			player.displayClientMessage(Component.translatable("message.remnants.anchor.full", RemnantTracker.MAX_ANCHOR_CHARGES)
				.withStyle(ChatFormatting.GOLD), true);
			return ItemInteractionResult.CONSUME;
		}
		stack.consume(1, player);
		if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
			dev.remnants.AnchorNetwork.attune(sp, pos, null);
		}
		level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
		server.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 0.7F);
		server.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
		player.displayClientMessage(Component.translatable("message.remnants.anchor.bound", charges, RemnantTracker.MAX_ANCHOR_CHARGES)
			.withStyle(ChatFormatting.AQUA), true);
		return ItemInteractionResult.CONSUME;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof net.minecraft.server.level.ServerPlayer sp) {
			RemnantTracker tracker = RemnantTracker.get(server.getServer());
			boolean known = tracker.isLinked(player.getUUID(), level.dimension(), pos);
			if (!dev.remnants.AnchorNetwork.attune(sp, pos, null)) {
				return InteractionResult.CONSUME;
			}
			if (player.isSecondaryUseActive()) {
				dev.remnants.AnchorNetwork.open(sp, pos);
				return InteractionResult.CONSUME;
			}
			if (!known) {
				return InteractionResult.CONSUME;   // "attuned" stays on screen
			}
			boolean here = tracker.anchor(player.getUUID()).map(a -> a.dimension() == level.dimension() && a.pos().equals(pos)).orElse(false);
			player.displayClientMessage(here
				? Component.translatable("message.remnants.anchor.status", tracker.anchorCharges(player.getUUID()), RemnantTracker.MAX_ANCHOR_CHARGES)
					.withStyle(ChatFormatting.AQUA)
				: Component.translatable("message.remnants.anchor.hint").withStyle(ChatFormatting.GRAY), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(LIT) && random.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.SOUL, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.05,
				pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.03, 0);
		}
	}
}
