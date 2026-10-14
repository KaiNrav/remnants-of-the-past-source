package dev.remnants.item;

import dev.remnants.RemnantsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.function.ToIntFunction;

/** A piece of the Mourner's set (bone, violet shroud cloth, thin gold): the tooltip explains Ghost Step and its cooldown. */
public class MournerArmorItem extends ArmorItem {
	/** Client hook (set by RemnantsClient): ticks left on this item's cooldown for the local player. */
	public static ToIntFunction<Item> clientCooldown = item -> 0;

	public MournerArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.remnants_of_the_past.mourner.set").withStyle(ChatFormatting.DARK_AQUA));
		tooltip.add(Component.translatable("item.remnants_of_the_past.mourner.set.desc", RemnantsConfig.get().ghostStepTicks / 20)
			.withStyle(ChatFormatting.GRAY));
		int left = clientCooldown.applyAsInt(this);
		if (left > 0) {
			int s = (left + 19) / 20;
			tooltip.add(Component.translatable("item.remnants_of_the_past.mourner.cooldown", String.format("%d:%02d", s / 60, s % 60))
				.withStyle(ChatFormatting.DARK_GRAY));
		} else {
			tooltip.add(Component.translatable("item.remnants_of_the_past.mourner.ready", RemnantsConfig.get().ghostStepCooldown / 1200)
				.withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
