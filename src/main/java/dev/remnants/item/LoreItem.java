package dev.remnants.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** An item whose tooltip explains it: lines {@code <description id>.tooltip}, {@code .tooltip2}, ... (grey). */
public class LoreItem extends Item {
	private final int lines;

	public LoreItem(Properties properties, int lines) {
		super(properties);
		this.lines = lines;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		for (int i = 1; i <= this.lines; i++) {
			tooltip.add(Component.translatable(this.getDescriptionId() + ".tooltip" + (i == 1 ? "" : Integer.toString(i)))
				.withStyle(i == 1 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
		}
	}
}
