package dev.remnants.crafting;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Counting and taking items from a player's inventory. */
public final class Stacks {
	public static int count(Player player, Item item) {
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	public static void take(Player player, Item item, int n) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				int k = Math.min(n, s.getCount());
				s.shrink(k);
				n -= k;
			}
		}
		inv.setChanged();
	}

	/** Into the empty main hand, else the inventory, else on the ground at the player's feet. */
	public static void give(Player player, net.minecraft.world.InteractionHand hand, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (player.getItemInHand(hand).isEmpty()) {
			player.setItemInHand(hand, stack);
		} else if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	private Stacks() {
	}
}
