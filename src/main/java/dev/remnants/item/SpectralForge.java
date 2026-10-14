package dev.remnants.item;

import dev.remnants.RemnantsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * Spectral weapons: a copy of the player's own weapon "charged with ghostly energy". Each Spectral level adds
 * +1 to every enchantment (or a fitting first enchantment), and melee weapons gain attack damage and speed.
 * The level is stored in custom data ("remnants_spectral") and capped by the config.
 */
public final class SpectralForge {
	public static final String TAG = "remnants_spectral";
	private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

	public static int level(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getInt(TAG);
	}

	public static boolean isWeapon(ItemStack stack) {
		return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem || stack.getItem() instanceof TridentItem
			|| stack.getItem() instanceof MaceItem || stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
	}

	public static boolean canUpgrade(ItemStack stack, int maxLevel) {
		return isWeapon(stack) && level(stack) < maxLevel;
	}

	/** Returns an upgraded copy (one Spectral level higher). */
	public static ItemStack upgrade(ItemStack original, HolderLookup.Provider registries) {
		ItemStack stack = original.copy();
		int lvl = level(stack) + 1;

		// +1 to every enchantment, or a fitting first one.
		ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
		if (current.isEmpty()) {
			Holder<Enchantment> first = holder(registries, firstEnchantment(stack));
			if (first != null) {
				mutable.set(first, 1);
			}
		} else {
			for (var entry : current.entrySet()) {
				mutable.set(entry.getKey(), entry.getIntValue() + 1);
			}
		}
		stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());

		// Melee weapons: + damage and + attack speed per level (replaces the previous Spectral bonus).
		if (!(stack.getItem() instanceof BowItem) && !(stack.getItem() instanceof CrossbowItem)) {
			ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
			mods = mods.withModifierAdded(Attributes.ATTACK_DAMAGE,
				new AttributeModifier(RemnantsMod.id("spectral_damage"), 1.0 * lvl, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
			mods = mods.withModifierAdded(Attributes.ATTACK_SPEED,
				new AttributeModifier(RemnantsMod.id("spectral_speed"), 0.08 * lvl, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.MAINHAND);
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, mods);
		}

		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TAG, lvl));
		Component baseName = original.has(DataComponents.CUSTOM_NAME) && level(original) == 0
			? original.getHoverName() : original.getItem().getName(original);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.remnants_of_the_past.spectral_name", baseName, roman(lvl))
			.withStyle(s -> s.withItalic(false).withColor(ChatFormatting.AQUA)));
		List<Component> lore = new ArrayList<>();
		lore.add(Component.translatable("item.remnants_of_the_past.spectral_lore").withStyle(s -> s.withItalic(true).withColor(ChatFormatting.DARK_AQUA)));
		lore.add(Component.translatable("item.remnants_of_the_past.spectral_lore2", 15 * lvl).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		stack.set(DataComponents.RARITY, lvl >= 3 ? Rarity.EPIC : Rarity.RARE);
		stack.setDamageValue(0);
		return stack;
	}

	private static ResourceKey<Enchantment> firstEnchantment(ItemStack stack) {
		if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem) {
			return Enchantments.SHARPNESS;
		} else if (stack.getItem() instanceof BowItem) {
			return Enchantments.POWER;
		} else if (stack.getItem() instanceof CrossbowItem) {
			return Enchantments.QUICK_CHARGE;
		} else if (stack.getItem() instanceof TridentItem) {
			return Enchantments.IMPALING;
		} else if (stack.getItem() instanceof MaceItem) {
			return Enchantments.DENSITY;
		}
		return Enchantments.UNBREAKING;
	}

	private static Holder<Enchantment> holder(HolderLookup.Provider registries, ResourceKey<Enchantment> key) {
		return registries.lookupOrThrow(Registries.ENCHANTMENT).get(key).orElse(null);
	}

	private static String roman(int n) {
		return n < ROMAN.length ? ROMAN[n] : Integer.toString(n);
	}

	private SpectralForge() {
	}
}
