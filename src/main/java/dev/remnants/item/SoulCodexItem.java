package dev.remnants.item;

import dev.remnants.SoulResonance;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Soul Codex: the mod's guide book. Right-click opens the illustrated Codex screen (client); the server answers
 * with a fresh Soul Resonance sync so the "Path of the Soul" page is current. It is soul-bound: it stays with you
 * through death.
 */
public class SoulCodexItem extends Item {
	/** Set by the client entrypoint; a no-op on dedicated servers. */
	public static Runnable openScreen = () -> {
	};

	public SoulCodexItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide) {
			openScreen.run();
		} else if (player instanceof ServerPlayer sp) {
			SoulResonance.sync(sp);
			dev.remnants.Chronicle.sync(sp);
		}
		player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.8F);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.remnants_of_the_past.soul_codex.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.remnants_of_the_past.soul_codex.tooltip2").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
	}
}
