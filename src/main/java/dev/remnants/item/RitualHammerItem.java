package dev.remnants.item;

import dev.remnants.block.entity.ReflectingPoolBlockEntity;
import dev.remnants.block.entity.SoulCrucibleBlockEntity;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.multiblock.Multiblocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * The Ritual Hammer: strikes a multiblock's heart to lock the structure (or show what is missing), confirms the
 * Reflecting Pool's rite, keeps the Soul Crucible's rhythm (strike any of its blocks - the anvil too), and with
 * Shift + right-click turns the mod's blocks. It only wears down when it forges. Shift + right-click on a working
 * structure, with anything in hand, stops the craft and hands the ingredients back.
 *
 * <p>All of it runs from Fabric's UseBlockCallback, ahead of the blocks' own use (a vanilla anvil would open its menu).
 */
public class RitualHammerItem extends Item {
	public RitualHammerItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
		return repair.is(Items.IRON_INGOT);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.remnants_of_the_past.ritual_hammer.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.remnants_of_the_past.ritual_hammer.tooltip2").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("item.remnants_of_the_past.ritual_hammer.tooltip3").withStyle(ChatFormatting.DARK_GRAY));
	}

	/** UseBlockCallback, on both sides: returns non-PASS when the hammer (or a Shift-cancel) handled the click. */
	public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (player.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		boolean hammer = stack.getItem() instanceof RitualHammerItem;
		BlockPos pos = hit.getBlockPos();
		HeartBlockEntity heart = Multiblocks.findHeart(level, pos);
		if (player.isSecondaryUseActive()) {
			if (heart != null && heart.isWorking()) {
				if (level instanceof ServerLevel server) {
					heart.cancel(server, player);
				}
				return InteractionResult.sidedSuccess(level.isClientSide);
			}
			if (hammer && hand == InteractionHand.MAIN_HAND) {
				return rotate(level, player, pos);
			}
			return InteractionResult.PASS;
		}
		if (!hammer || hand != InteractionHand.MAIN_HAND || heart == null) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			player.swing(hand, true);
			if (heart instanceof ReflectingPoolBlockEntity pool) {
				pool.onHammer(server, sp);
			} else {
				heart.onHammer(server, sp, stack);   // the crucible keeps its rhythm, the belfry locks
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** Shift + right-click: turn one of the mod's blocks a quarter clockwise (not while it is locked into a structure). */
	private static InteractionResult rotate(Level level, Player player, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!dev.remnants.RemnantsMod.MOD_ID.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())
			|| !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			return InteractionResult.PASS;
		}
		if (state.hasProperty(Multiblocks.FORMED) && state.getValue(Multiblocks.FORMED)) {
			if (!level.isClientSide) {
				player.displayClientMessage(Component.translatable("message.remnants.hammer.locked").withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		if (!level.isClientSide) {
			level.setBlock(pos, state.rotate(Rotation.CLOCKWISE_90), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7F, 1.3F);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}
}
