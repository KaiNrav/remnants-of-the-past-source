package dev.remnants.crafting;

import dev.remnants.RemnantsConfig;
import net.minecraft.core.Holder;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Reflecting Pool's rules: one enchantment moves from a source (an item or an enchanted book) to a target (an item,
 * a plain book or an enchanted book). Vanilla compatibility and maximum levels apply, curses never move, and a book
 * given to a book splits one enchanted book into several. Equal levels combine like the anvil (V + V = VI, capped at
 * the maximum). Cost: {@code poolLevelsPerEnchantLevel} experience levels and {@code poolFragmentsPerEnchantLevel} Soul
 * Fragments per level moved, x {@code poolTreasureMultiplier} for treasure enchantments (Mending, Frost Walker...).
 */
public final class EnchantTransfer {
	public enum Result {
		OK, NO_ENCHANT, CURSE, INCOMPATIBLE, NOTHING_TO_GAIN;

		public String key() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Enchantments (stored ones for books) on a stack. */
	public static ItemEnchantments enchantmentsOf(ItemStack stack) {
		return EnchantmentHelper.getEnchantmentsForCrafting(stack);
	}

	/** What the source can give, curses left out, in a stable order (the same on the client and the server). */
	public static List<Holder<Enchantment>> options(ItemStack source) {
		List<Holder<Enchantment>> out = new ArrayList<>();
		for (Holder<Enchantment> e : enchantmentsOf(source).keySet()) {
			if (!e.is(EnchantmentTags.CURSE)) {
				out.add(e);
			}
		}
		out.sort(Comparator.comparing(e -> e.unwrapKey().map(k -> k.location().toString()).orElse("")));
		return out;
	}

	/** The level that moves: the source's, capped at the enchantment's maximum (Spectral items go past it). */
	public static int movedLevel(ItemStack source, Holder<Enchantment> e) {
		return Math.min(enchantmentsOf(source).getLevel(e), e.value().getMaxLevel());
	}

	/** The target's level afterwards (anvil rule), capped at the maximum. */
	public static int resultLevel(ItemStack source, ItemStack target, Holder<Enchantment> e) {
		int moved = movedLevel(source, e);
		int had = enchantmentsOf(target).getLevel(e);
		int level = had == 0 ? moved : had == moved ? moved + 1 : Math.max(had, moved);
		return Math.min(level, e.value().getMaxLevel());
	}

	public static Result check(ItemStack source, ItemStack target, Holder<Enchantment> e) {
		if (enchantmentsOf(source).getLevel(e) <= 0) {
			return Result.NO_ENCHANT;
		}
		if (e.is(EnchantmentTags.CURSE)) {
			return Result.CURSE;
		}
		if (target.isEmpty()) {
			return Result.INCOMPATIBLE;
		}
		if (target.is(Items.BOOK)) {
			return Result.OK;
		}
		ItemEnchantments existing = enchantmentsOf(target);
		if (!target.is(Items.ENCHANTED_BOOK) && !e.value().canEnchant(target)) {
			return Result.INCOMPATIBLE;
		}
		List<Holder<Enchantment>> others = new ArrayList<>(existing.keySet());
		others.remove(e);
		if (!EnchantmentHelper.isEnchantmentCompatible(others, e)) {
			return Result.INCOMPATIBLE;
		}
		return resultLevel(source, target, e) > existing.getLevel(e) ? Result.OK : Result.NOTHING_TO_GAIN;
	}

	public static boolean isTreasure(Holder<Enchantment> e) {
		return e.is(EnchantmentTags.TREASURE);
	}

	public static int levelCost(Holder<Enchantment> e, int level) {
		RemnantsConfig cfg = RemnantsConfig.get();
		return (int) Math.ceil(cfg.poolLevelsPerEnchantLevel * level * (isTreasure(e) ? cfg.poolTreasureMultiplier : 1.0));
	}

	public static int fragmentCost(Holder<Enchantment> e, int level) {
		RemnantsConfig cfg = RemnantsConfig.get();
		return (int) Math.ceil(cfg.poolFragmentsPerEnchantLevel * level * (isTreasure(e) ? cfg.poolTreasureMultiplier : 1.0));
	}

	/** Performs a checked transfer: {new source, new target}. The inputs are not modified. */
	public static ItemStack[] apply(ItemStack source, ItemStack target, Holder<Enchantment> e) {
		int level = resultLevel(source, target, e);
		ItemStack from = source.copy();
		EnchantmentHelper.updateEnchantments(from, m -> m.removeIf(h -> h.equals(e)));
		if (from.is(Items.ENCHANTED_BOOK) && enchantmentsOf(from).isEmpty()) {
			from = new ItemStack(Items.BOOK, from.getCount());   // the last enchantment taken off a book leaves a plain book
		}
		ItemStack to = target.is(Items.BOOK) ? target.transmuteCopy(Items.ENCHANTED_BOOK, 1) : target.copy();
		EnchantmentHelper.updateEnchantments(to, m -> m.set(e, level));
		return new ItemStack[] {from, to};
	}

	private EnchantTransfer() {
	}
}
