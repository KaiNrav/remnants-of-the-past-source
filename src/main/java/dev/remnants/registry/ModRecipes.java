package dev.remnants.registry;

import dev.remnants.RemnantsMod;
import dev.remnants.item.ChakramInlayRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

public final class ModRecipes {
	/** data/remnants_of_the_past/recipe/chakram_inlay.json: {"type": "remnants_of_the_past:chakram_inlay"}. */
	public static final RecipeSerializer<ChakramInlayRecipe> CHAKRAM_INLAY = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
		RemnantsMod.id("chakram_inlay"), new SimpleCraftingRecipeSerializer<>(ChakramInlayRecipe::new));

	public static void init() {
	}

	private ModRecipes() {
	}
}
