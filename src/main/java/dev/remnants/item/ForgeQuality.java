package dev.remnants.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.remnants.RemnantsMod;
import dev.remnants.registry.ModComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;

import java.util.ArrayList;
import java.util.List;

/**
 * Forge quality ({@code remnants_of_the_past:forge_quality}): how well the Soul Crucible's five strikes landed. 3 / 4 / 5 good strikes
 * give +5 / +10 / +15 %: weapons hit harder, armour protects more, tools dig faster, and everything lasts longer; five
 * perfect strikes also sign the item ("Forged by a master: name"). The bonuses are attribute modifiers computed from the
 * item's own stats, so the Remnant echo of a player in forged gear is exactly as strong as that gear.
 */
public record ForgeQuality(int percent, String master) {
	public static final Codec<ForgeQuality> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("percent").forGetter(ForgeQuality::percent),
		Codec.STRING.optionalFieldOf("master", "").forGetter(ForgeQuality::master)
	).apply(i, ForgeQuality::new));
	public static final StreamCodec<ByteBuf, ForgeQuality> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, ForgeQuality::percent, ByteBufCodecs.STRING_UTF8, ForgeQuality::master, ForgeQuality::new);

	public static final ResourceLocation DAMAGE = RemnantsMod.id("forge_quality_damage");
	public static final ResourceLocation ARMOR = RemnantsMod.id("forge_quality_armor");
	public static final ResourceLocation MINING = RemnantsMod.id("forge_quality_mining");

	/** Quality from the strikes: a perfect strike counts 1, a good one 1/2; 3 -> +5 %, 4 -> +10 %, 5 -> +15 %. */
	public static int percentFor(int perfect, int good) {
		double score = perfect + good * 0.5;
		return score >= 5 ? 15 : score >= 4 ? 10 : score >= 3 ? 5 : 0;
	}

	public static int percent(ItemStack stack) {
		ForgeQuality q = stack.get(ModComponents.FORGE_QUALITY);
		return q == null ? 0 : q.percent();
	}

	/** Sets the quality (replacing any earlier one; 0 removes it) and re-derives its bonuses. */
	public static void apply(ItemStack stack, int percent, String master) {
		if (percent <= 0) {
			stack.remove(ModComponents.FORGE_QUALITY);
		} else {
			stack.set(ModComponents.FORGE_QUALITY, new ForgeQuality(percent, master));
		}
		refresh(stack);
	}

	/** Re-derives the bonuses from the stored quality (also after the Arsenal changes form and resets its modifiers). */
	public static void refresh(ItemStack stack) {
		double q = percent(stack) / 100.0;
		ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		if (mods.modifiers().isEmpty()) {
			// armour keeps its defence in Item#getDefaultAttributeModifiers, used only while the component is empty:
			// start from those, or the piece would lose its base protection once the quality bonus is written
			mods = stack.getItem().getDefaultAttributeModifiers();
		}
		List<ItemAttributeModifiers.Entry> kept = new ArrayList<>();
		double damage = 0, armor = 0;
		for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
			ResourceLocation id = e.modifier().id();
			if (id.equals(DAMAGE) || id.equals(ARMOR) || id.equals(MINING)) {
				continue;
			}
			kept.add(e);
			if (e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
				if (e.attribute().equals(Attributes.ATTACK_DAMAGE)) {
					damage += e.modifier().amount();
				} else if (e.attribute().equals(Attributes.ARMOR)) {
					armor += e.modifier().amount();
				}
			}
		}
		if (q > 0) {
			if (damage > 0) {   // +1: the player's own fist, as the tooltip counts it
				kept.add(new ItemAttributeModifiers.Entry(Attributes.ATTACK_DAMAGE,
					new AttributeModifier(DAMAGE, round((damage + 1.0) * q), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND));
			}
			if (armor > 0 && stack.getItem() instanceof ArmorItem piece) {
				kept.add(new ItemAttributeModifiers.Entry(Attributes.ARMOR,
					new AttributeModifier(ARMOR, round(armor * q), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.bySlot(piece.getEquipmentSlot())));
			}
			Tool tool = stack.get(DataComponents.TOOL);
			float speed = toolSpeed(tool);
			if (speed > 1.0F) {
				kept.add(new ItemAttributeModifiers.Entry(Attributes.MINING_EFFICIENCY,
					new AttributeModifier(MINING, round(speed * q), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND));
			}
		}
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(kept, mods.showInTooltip()));
		Integer max = stack.getPrototype().get(DataComponents.MAX_DAMAGE);
		if (max != null && max > 0) {
			stack.set(DataComponents.MAX_DAMAGE, (int) Math.round(max * (1.0 + q)));
		}
	}

	private static float toolSpeed(Tool tool) {
		if (tool == null) {
			return 0.0F;
		}
		float speed = tool.defaultMiningSpeed();
		for (Tool.Rule rule : tool.rules()) {
			if (rule.speed().isPresent() && rule.correctForDrops().orElse(false)) {
				speed = Math.max(speed, rule.speed().get());
			}
		}
		return speed;
	}

	private static double round(double v) {
		return Math.round(v * 100.0) / 100.0;
	}
}
