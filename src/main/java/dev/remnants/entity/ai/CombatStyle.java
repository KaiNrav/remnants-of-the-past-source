package dev.remnants.entity.ai;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/** How a Remnant fights, decided by the weapon the player died holding. */
public enum CombatStyle {
	//          reach  ticks after each strike lands (a combo ends in a long pause)   ranged
	//          every melee strike is also wound up for 5-7 ticks before it lands (RemnantBrain)
	//          (reach is centre-to-centre minus half the target's width; a survival player reaches ~3.3 the same way)
	SWORD(3.1, new int[]{10, 10, 34}, false),
	AXE(3.0, new int[]{32}, false),
	TRIDENT(3.4, new int[]{22}, false),
	MACE(2.9, new int[]{34}, false),
	FISTS(2.8, new int[]{8, 8, 26}, false),
	BOW(0, new int[]{30}, true),
	CROSSBOW(0, new int[]{38}, true);

	public final double reach;
	public final int[] combo;
	public final boolean ranged;

	CombatStyle(double reach, int[] combo, boolean ranged) {
		this.reach = reach;
		this.combo = combo;
		this.ranged = ranged;
	}

	public static CombatStyle of(ItemStack weapon) {
		if (weapon.getItem() instanceof SwordItem) {
			return SWORD;
		} else if (weapon.getItem() instanceof AxeItem) {
			return AXE;
		} else if (weapon.getItem() instanceof TridentItem) {
			return TRIDENT;
		} else if (weapon.getItem() instanceof MaceItem) {
			return MACE;
		} else if (weapon.getItem() instanceof BowItem) {
			return BOW;
		} else if (weapon.getItem() instanceof CrossbowItem) {
			return CROSSBOW;
		}
		return FISTS;
	}
}
