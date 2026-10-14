package dev.remnants.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** A block item whose tooltip explains the block: {@code block.remnants_of_the_past.<id>.tooltip}, {@code .tooltip2}, ... */
public class LoreBlockItem extends BlockItem {
	private final int lines;

	public LoreBlockItem(Block block, Properties properties, int lines) {
		super(block, properties);
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
