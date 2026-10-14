package dev.remnants.client.codex;

import dev.remnants.entity.ForgottenVariant;
import dev.remnants.entity.RemnantEntity;
import dev.remnants.entity.SoulKingEntity;
import dev.remnants.registry.ModEntities;
import dev.remnants.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Client-only display copies for the Codex's portraits: your own ghost (your skin, your current armor and weapon),
 * a Forgotten Remnant in antique gear, and the Soul King. Never added to the world; their tickCount is advanced by
 * the screen so hovering and idle animations play.
 */
final class CodexPortraits {
	RemnantEntity remnant;
	RemnantEntity forgotten;
	SoulKingEntity king;
	/** A biome Forgotten that changes land every few seconds (Drowned, Frozen, Sandy). */
	RemnantEntity lands;
	/** A mourner in the full set, Ritual Hammer in hand (1.3): a Forgotten, so no crown hides the hood. */
	RemnantEntity mourner;
	/** 1.4: the Crypt Warden (awake), a Grave Mimic with its lid open, a Keener shown as if revealed. */
	dev.remnants.entity.CryptWardenEntity warden;
	dev.remnants.entity.GraveMimicEntity mimic;
	dev.remnants.entity.KeenerEntity keener;
	/** 1.5: the Archivist, awake, its tome open. */
	dev.remnants.entity.ArchivistEntity archivist;
	/** A Carrion Crow with a stolen stack in its beak. */
	dev.remnants.entity.CarrionCrowEntity crow;
	private int tick;

	void create(Minecraft mc) {
		if (mc.level == null) {
			return;
		}
		if (this.remnant == null) {
			this.remnant = ModEntities.REMNANT.create(mc.level);
			if (this.remnant != null && mc.player != null) {
				this.remnant.setDisplayOwner(mc.player.getUUID());
				for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
					this.remnant.setItemSlot(slot, mc.player.getItemBySlot(slot).copy());
				}
				ItemStack hand = mc.player.getMainHandItem();
				this.remnant.setItemSlot(EquipmentSlot.MAINHAND, hand.isEmpty() || hand.is(ModItems.SOUL_CODEX) ? new ItemStack(Items.IRON_SWORD) : hand.copy());
			}
		}
		if (this.forgotten == null) {
			this.forgotten = ModEntities.FORGOTTEN_REMNANT.create(mc.level);
			if (this.forgotten != null) {
				this.forgotten.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
				this.forgotten.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
				this.forgotten.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
				this.forgotten.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			}
		}
		if (this.king == null) {
			this.king = ModEntities.SOUL_KING.create(mc.level);
		}
		if (this.mourner == null) {
			this.mourner = ModEntities.FORGOTTEN_REMNANT.create(mc.level);
			if (this.mourner != null) {
				this.mourner.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.MOURNER_HOOD));
				this.mourner.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.MOURNER_ROBE));
				this.mourner.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.MOURNER_LEGGINGS));
				this.mourner.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.MOURNER_BOOTS));
				this.mourner.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.RITUAL_HAMMER));
			}
		}
		if (this.warden == null) {
			this.warden = ModEntities.CRYPT_WARDEN.create(mc.level);
			if (this.warden != null) {
				this.warden.showAwake();
			}
		}
		if (this.mimic == null) {
			this.mimic = ModEntities.GRAVE_MIMIC.create(mc.level);
			if (this.mimic != null) {
				this.mimic.showAwake();
			}
		}
		if (this.keener == null) {
			this.keener = ModEntities.KEENER.create(mc.level);
			if (this.keener != null) {
				this.keener.displayRevealed = true;
			}
		}
		if (this.archivist == null) {
			this.archivist = ModEntities.ARCHIVIST.create(mc.level);
			if (this.archivist != null) {
				this.archivist.showAwake();
			}
		}
		if (this.crow == null) {
			this.crow = ModEntities.CARRION_CROW.create(mc.level);
			if (this.crow != null) {
				this.crow.showCarrying(new ItemStack(Items.DIAMOND, 3));
			}
		}
		if (this.lands == null) {
			this.lands = ModEntities.FORGOTTEN_REMNANT.create(mc.level);
			if (this.lands != null) {
				this.lands.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
				this.lands.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			}
		}
	}

	/** Every three seconds the portrait's Forgotten takes on another land (crust and all). */
	RemnantEntity variant() {
		if (this.lands != null) {
			ForgottenVariant[] kinds = {ForgottenVariant.DROWNED, ForgottenVariant.FROZEN, ForgottenVariant.SANDY};
			ForgottenVariant want = kinds[(this.tick / 60) % kinds.length];
			if (this.lands.getVariant() != want) {
				this.lands.setVariant(want);
				this.lands.applyVariantKit();
				this.lands.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(want == ForgottenVariant.DROWNED ? Items.TRIDENT : Items.IRON_SWORD));
			}
		}
		return this.lands;
	}

	void tick() {
		this.tick++;
		for (LivingEntity e : new LivingEntity[]{this.remnant, this.forgotten, this.king, this.lands, this.mourner, this.archivist, this.crow}) {
			if (e != null) {
				e.tickCount++;
			}
		}
	}
}
