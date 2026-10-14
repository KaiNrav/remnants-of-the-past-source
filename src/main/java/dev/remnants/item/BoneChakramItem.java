package dev.remnants.item;

import dev.remnants.entity.BoneChakramEntity;
import dev.remnants.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Bone Chakram: thrown, it flies about fifteen blocks and comes back. On the way out it cuts grass and cobwebs,
 * presses buttons, pulls levers, rings bells and hits targets; it slices through every creature in its path, and on
 * the way back it gathers up the items lying where it passes. A trophy set into it (see {@link ChakramInlay}) adds a
 * Forgotten Remnant's signature to every throw.
 */
public class BoneChakramItem extends Item {
	public BoneChakramItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide) {
			BoneChakramEntity chakram = new BoneChakramEntity(level, player, stack.copyWithCount(1), hand, player.getAbilities().instabuild);
			chakram.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 0.4F);
			level.addFreshEntity(chakram);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.5F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		player.getCooldowns().addCooldown(this, 8);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public int getEnchantmentValue() {
		return 12;
	}

	@Override
	public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
		return repair.is(Items.BONE_BLOCK) || super.isValidRepairItem(stack, repair);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.remnants_of_the_past.bone_chakram.tooltip").withStyle(ChatFormatting.GRAY));
		ChakramInlay inlay = stack.get(ModComponents.CHAKRAM_INLAY);
		if (inlay != null) {
			tooltip.add(Component.translatable("item.remnants_of_the_past.bone_chakram.inlay." + inlay.getSerializedName()).withStyle(ChatFormatting.AQUA));
		} else {
			tooltip.add(Component.translatable("item.remnants_of_the_past.bone_chakram.tooltip2").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
