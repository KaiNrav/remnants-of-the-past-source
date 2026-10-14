package dev.remnants.crafting;

import dev.remnants.RemnantsConfig;
import dev.remnants.item.ArsenalItem;
import dev.remnants.item.SpectralForge;
import dev.remnants.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What the Soul Crucible forges. The workpiece lies on the anvil, the materials go into the pot; the forge takes the one
 * recipe the pot's contents fit exactly (nothing extra, nothing missing). Every forging also sets the item's
 * {@link dev.remnants.item.ForgeQuality forge quality} from the rhythm.
 * <ul>
 *   <li>Spectral III: a Spectral II weapon + 6 Condensed Souls + the Soul King's Heart (level III moved here from the
 *   Soul Forge, which keeps I and II).</li>
 *   <li>Awakening (IV, the Arsenal only): the Soul King's Arsenal + 6 Condensed Souls + a Heart.</li>
 *   <li>Mourner's set: an iron armour piece + Wet Shrouds, Soul Frost, a Scarab Seal + a Condensed Soul.</li>
 *   <li>Reforge: any weapon, armour piece or tool + a Condensed Soul: repaired, with a new forge quality.</li>
 *   <li>Remembrance (1.5): any weapon, armour piece or tool + a Seal of Memory + a Condensed Soul: it becomes Remembered
 *   and crosses over with you when you die.</li>
 * </ul>
 */
public final class CrucibleRecipes {
	public enum Kind {
		SPECTRAL, AWAKEN, MOURNER, REFORGE, REMEMBER
	}

	public record Need(Item item, int count) {
	}

	public record Recipe(String key, Kind kind, Predicate<ItemStack> accepts, List<Need> needs, @Nullable Item result) {
		public int need(Item item) {
			int n = 0;
			for (Need need : this.needs) {
				if (need.item() == item) {
					n += need.count();
				}
			}
			return n;
		}
	}

	private static List<Recipe> all;

	public static List<Recipe> all() {
		if (all == null) {
			List<Recipe> r = new ArrayList<>();
			Need souls6 = new Need(ModItems.CONDENSED_SOUL, 6), heart = new Need(ModItems.SOUL_KING_HEART, 1);
			r.add(new Recipe("spectral", Kind.SPECTRAL, s -> SpectralForge.isWeapon(s) && SpectralForge.level(s) == 2
				&& RemnantsConfig.get().maxSpectralLevel >= 3, List.of(souls6, heart), null));
			r.add(new Recipe("awaken", Kind.AWAKEN, s -> s.is(ModItems.SOUL_KING_ARSENAL) && !ArsenalItem.isAwakened(s), List.of(souls6, heart), null));
			r.add(mourner("mourner_hood", Items.IRON_HELMET, ModItems.MOURNER_HOOD, 2, 1, 1));
			r.add(mourner("mourner_robe", Items.IRON_CHESTPLATE, ModItems.MOURNER_ROBE, 4, 2, 1));
			r.add(mourner("mourner_leggings", Items.IRON_LEGGINGS, ModItems.MOURNER_LEGGINGS, 3, 1, 1));
			r.add(mourner("mourner_boots", Items.IRON_BOOTS, ModItems.MOURNER_BOOTS, 1, 2, 1));
			r.add(new Recipe("reforge", Kind.REFORGE, CrucibleRecipes::reforgeable, List.of(new Need(ModItems.CONDENSED_SOUL, 1)), null));
			r.add(new Recipe("remember", Kind.REMEMBER, s -> reforgeable(s) && !s.has(dev.remnants.registry.ModComponents.REMEMBERED),
				List.of(new Need(ModItems.SEAL_OF_MEMORY, 1), new Need(ModItems.CONDENSED_SOUL, 1)), null));
			all = List.copyOf(r);
		}
		return all;
	}

	private static Recipe mourner(String key, Item base, Item result, int shrouds, int frost, int scarabs) {
		return new Recipe(key, Kind.MOURNER, s -> s.is(base), List.of(new Need(ModItems.CONDENSED_SOUL, 1),
			new Need(ModItems.WET_SHROUD, shrouds), new Need(ModItems.SOUL_FROST, frost), new Need(ModItems.SCARAB_SEAL, scarabs)), result);
	}

	public static boolean reforgeable(ItemStack s) {
		return s.isDamageableItem() && (SpectralForge.isWeapon(s) || s.getItem() instanceof ArmorItem || s.has(DataComponents.TOOL)
			|| s.is(ModItems.SOUL_KING_ARSENAL));
	}

	public static List<Recipe> forWork(ItemStack work) {
		List<Recipe> out = new ArrayList<>();
		if (!work.isEmpty()) {
			for (Recipe r : all()) {
				if (r.accepts().test(work)) {
					out.add(r);
				}
			}
		}
		return out;
	}

	/** Some recipe puts this item into the pot. */
	public static boolean isMaterial(Item item) {
		for (Recipe r : all()) {
			if (r.need(item) > 0) {
				return true;
			}
		}
		return false;
	}

	public static boolean isWorkpiece(ItemStack stack) {
		return !forWork(stack).isEmpty();
	}

	/** The most of {@code material} any recipe for this workpiece takes (what the pot accepts). */
	public static int cap(ItemStack work, Item material) {
		int cap = 0;
		for (Recipe r : forWork(work)) {
			cap = Math.max(cap, r.need(material));
		}
		return cap;
	}

	public static Map<Item, Integer> tally(List<ItemStack> materials) {
		Map<Item, Integer> have = new LinkedHashMap<>();
		for (ItemStack s : materials) {
			if (!s.isEmpty()) {
				have.merge(s.getItem(), s.getCount(), Integer::sum);
			}
		}
		return have;
	}

	/** Nothing in the pot this recipe would not use. */
	public static boolean fits(Recipe r, Map<Item, Integer> have) {
		for (Map.Entry<Item, Integer> e : have.entrySet()) {
			if (r.need(e.getKey()) < e.getValue()) {
				return false;
			}
		}
		return true;
	}

	public static boolean satisfied(Recipe r, Map<Item, Integer> have) {
		for (Need need : r.needs()) {
			if (have.getOrDefault(need.item(), 0) < need.count()) {
				return false;
			}
		}
		return true;
	}

	/** The recipe the crucible would forge right now, or null. */
	@Nullable
	public static Recipe match(ItemStack work, List<ItemStack> materials) {
		Map<Item, Integer> have = tally(materials);
		for (Recipe r : forWork(work)) {
			if (fits(r, have) && satisfied(r, have)) {
				return r;
			}
		}
		return null;
	}

	/** The recipe the pot is filling up for (to say what is still missing), or null. */
	@Nullable
	public static Recipe closest(ItemStack work, List<ItemStack> materials) {
		Map<Item, Integer> have = tally(materials);
		Recipe best = null;
		int bestCount = -1;
		for (Recipe r : forWork(work)) {
			if (fits(r, have)) {
				int filled = 0;
				for (Need need : r.needs()) {
					filled += Math.min(need.count(), have.getOrDefault(need.item(), 0));
				}
				if (filled > bestCount) {
					best = r;
					bestCount = filled;
				}
			}
		}
		return best;
	}

	public static List<Need> lacking(Recipe r, List<ItemStack> materials) {
		Map<Item, Integer> have = tally(materials);
		List<Need> out = new ArrayList<>();
		for (Need need : r.needs()) {
			int missing = need.count() - have.getOrDefault(need.item(), 0);
			if (missing > 0) {
				out.add(new Need(need.item(), missing));
			}
		}
		return out;
	}

	/** The forged item (quality is set afterwards by the crucible). */
	public static ItemStack forge(Recipe r, ItemStack work, HolderLookup.Provider registries) {
		return switch (r.kind()) {
			case SPECTRAL -> SpectralForge.upgrade(work, registries);
			case AWAKEN -> ArsenalItem.awaken(work, registries);
			case MOURNER -> {
				ItemStack out = new ItemStack(r.result());
				out.set(DataComponents.ENCHANTMENTS, work.getEnchantments());   // the iron piece's enchantments carry over
				if (work.has(DataComponents.CUSTOM_NAME)) {
					out.set(DataComponents.CUSTOM_NAME, work.get(DataComponents.CUSTOM_NAME));
				}
				yield out;
			}
			case REFORGE -> {
				ItemStack out = work.copy();
				out.setDamageValue(0);
				yield out;
			}
			case REMEMBER -> {
				ItemStack out = work.copy();
				out.setDamageValue(0);
				out.set(dev.remnants.registry.ModComponents.REMEMBERED, net.minecraft.util.Unit.INSTANCE);
				yield out;
			}
		};
	}

	private CrucibleRecipes() {
	}
}
