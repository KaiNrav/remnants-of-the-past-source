package dev.remnants.item;

import dev.remnants.registry.ModComponents;
import dev.remnants.registry.ModItems;
import dev.remnants.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Bone Chakram + one biome trophy (Soul Frost, Wet Shroud, Scarab Seal), anywhere in the grid: the chakram with that inlay. */
public class ChakramInlayRecipe extends CustomRecipe {
	public ChakramInlayRecipe(CraftingBookCategory category) {
		super(category);
	}

	/** The chakram and the inlay it would get, or null if the grid is anything else. */
	@Nullable
	private static Object[] find(CraftingInput input) {
		ItemStack chakram = ItemStack.EMPTY;
		ChakramInlay inlay = null;
		for (int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			ChakramInlay trophy = ChakramInlay.byTrophy(stack.getItem());
			if (stack.is(ModItems.BONE_CHAKRAM) && chakram.isEmpty()) {
				chakram = stack;
			} else if (trophy != null && inlay == null) {
				inlay = trophy;
			} else {
				return null;
			}
		}
		if (chakram.isEmpty() || inlay == null || chakram.get(ModComponents.CHAKRAM_INLAY) == inlay) {
			return null;
		}
		return new Object[]{chakram, inlay};
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return find(input) != null;
	}

	@Override
	public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
		Object[] found = find(input);
		if (found == null) {
			return ItemStack.EMPTY;
		}
		ItemStack result = ((ItemStack) found[0]).copyWithCount(1);
		result.set(ModComponents.CHAKRAM_INLAY, (ChakramInlay) found[1]);
		return result;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModRecipes.CHAKRAM_INLAY;
	}
}
