package dev.remnants;

import dev.remnants.entity.RemnantEntity;
import dev.remnants.registry.ModEntities;
import dev.remnants.block.SoulAnchorBlock;
import dev.remnants.registry.ModBlocks;
import dev.remnants.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Hooked to Fabric's ALLOW_DEATH (late phase, so any mod that cancels the death runs first). At that moment
 * vanilla has not dropped anything yet: we move the inventory (and the experience) into a new Remnant, and vanilla
 * then finds nothing to drop. Soul-bound items (the Codex, and what Resonance perks keep) wait for the respawn.
 */
public final class DeathHandler {
	public static void onPlayerDeath(ServerPlayer player, DamageSource source) {
		RemnantsConfig cfg = RemnantsConfig.get();
		if (!cfg.enabled || player.isSpectator() || (player.isCreative() && !cfg.spawnForCreativePlayers)) {
			return;
		}
		ServerLevel level = player.serverLevel();
		if (level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) && !cfg.bypassKeepInventory()) {
			return;
		}
		// ALLOW_DEATH fires before the totem check: a totem in hand will save the player, so keep out of it.
		if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
			&& (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING))) {
			return;
		}

		RemnantTracker tracker = RemnantTracker.get(level.getServer());
		Inventory inventory = player.getInventory();
		BlockPos deathPos = player.blockPosition();
		int levelsAtDeath = player.experienceLevel;

		// A Phylactery holds the whole inventory until respawn: no Remnant this time, one of its three uses is spent.
		int phylactery = slotOf(inventory, ModItems.PHYLACTERY);
		if (phylactery >= 0) {
			keepWithPhylactery(player, inventory, phylactery, tracker);
			writeEpitaph(player, source, deathPos, levelsAtDeath, 0, null);
			return;
		}
		// A Soul Ward burns away and calms the next Remnant: no streak scaling, less health, never Empowered.
		int ward = slotOf(inventory, ModItems.SOUL_WARD);
		boolean weakened = ward >= 0;
		if (weakened) {
			inventory.getItem(ward).shrink(1);
		}

		RemnantEntity.EquipmentSnapshot look = RemnantEntity.EquipmentSnapshot.of(player);
		bindSoulbound(player, inventory, tracker);
		List<ItemStack> items = takeInventory(inventory);
		int xp = cfg.remnantKeepsExperience ? (int) (totalExperience(player) * Math.max(0.0, Math.min(1.0, cfg.experienceKept))) : 0;
		int levels = player.experienceLevel;
		// nothing worth a Remnant: an empty inventory and a handful of experience (vanilla drops that as usual)
		if (items.isEmpty() && levels < 5) {
			writeEpitaph(player, source, deathPos, levelsAtDeath, 0, null);
			return;
		}
		// A charged Soul Anchor in this dimension calls the Remnant to it instead of the place of death.
		BlockPos anchorPos = anchorFor(player, level, tracker);

		RemnantEntity remnant;
		BlockPos pos;
		int tier;
		try {
			remnant = ModEntities.REMNANT.create(level);
			if (remnant == null) {
				giveBack(player, items);
				return;
			}
			pos = anchorPos != null ? anchorPos.above() : safeSpawnPos(level, player.blockPosition());
			remnant.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), 0.0F);
			tier = tracker.spawned(player.getUUID(), remnant.getUUID(), level, pos);
			if (weakened) {
				tier = 0;
			}
			remnant.rise(player, items, look, tier, !weakened && FORCE_EMPOWER.remove(player.getUUID()), weakened);
			remnant.setLastWords(source.getLocalizedDeathMessage(player));
			writeEpitaph(player, source, deathPos, levelsAtDeath, remnant.getEchoRank(), remnant);
			remnant.setStoredXp(xp);
			if (!level.addFreshEntity(remnant)) {
				RemnantTracker.get(level.getServer()).forget(player.getUUID(), remnant.getUUID());
				giveBack(player, items);
				return;
			}
			if (xp > 0) {
				// the Remnant holds it now: vanilla must not drop its own 7-per-level share as well
				player.experienceLevel = 0;
				player.experienceProgress = 0.0F;
				player.totalExperience = 0;
			}
		} catch (RuntimeException e) {
			// Put everything back so vanilla drops it as usual.
			giveBack(player, items);
			throw e;
		}

		level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.5F, 1.8F);
		level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 2.0F, 0.5F);
		if (anchorPos != null) {
			int left = tracker.useAnchorCharge(player.getUUID());
			BlockState anchor = level.getBlockState(anchorPos);
			if (anchor.is(ModBlocks.SOUL_ANCHOR) && !tracker.anchorInUse(level.dimension(), anchorPos)) {
				level.setBlock(anchorPos, anchor.setValue(SoulAnchorBlock.LIT, false), Block.UPDATE_ALL);
			}
			player.sendSystemMessage(Component.translatable("message.remnants.spawned.anchor", pos.getX(), pos.getY(), pos.getZ(), left)
				.withStyle(ChatFormatting.DARK_AQUA));
		} else if (cfg.announceSpawn) {
			player.sendSystemMessage(Component.translatable("message.remnants.spawned", pos.getX(), pos.getY(), pos.getZ(), tier + 1)
				.withStyle(ChatFormatting.DARK_AQUA));
		}
		if (xp > 0 && levels > 0) {
			player.sendSystemMessage(Component.translatable("message.remnants.spawned.xp", levels).withStyle(ChatFormatting.GREEN));
		}
		if (cfg.dormantUntilApproached) {
			player.sendSystemMessage(Component.translatable("message.remnants.spawned.dormant").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		if (weakened) {
			player.sendSystemMessage(Component.translatable("message.remnants.ward").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		if (remnant.isEmpowered()) {
			player.sendSystemMessage(Component.translatable("message.remnants.empowered", remnant.getMainHandItem().getHoverName())
				.withStyle(ChatFormatting.LIGHT_PURPLE));
			// the title is shown after respawn, once the death screen is gone
			PENDING_EMPOWERED_TITLE.add(player.getUUID());
		}
		RemnantsMod.LOGGER.info("Remnant of {} (tier {}) rose at {} in {} holding {} stacks",
			player.getGameProfile().getName(), tier, pos.toShortString(), level.dimension().location(), items.size());
	}

	/**
	 * Every death leaves an epitaph: written straight into the player's Chronicle, or (epitaphItemsForOwnDeaths, the 1.2.0
	 * way) carried by the Remnant as an Epitaph item and given back with the gear. {@code rank} 0 = no Remnant rose.
	 */
	private static void writeEpitaph(ServerPlayer player, DamageSource source, BlockPos pos, int levels, int rank, @Nullable RemnantEntity remnant) {
		RemnantsConfig cfg = RemnantsConfig.get();
		if (!cfg.epitaphs) {
			return;
		}
		dev.remnants.item.EpitaphData data = dev.remnants.item.EpitaphItem.death(player, source, pos, levels, rank);
		if (cfg.epitaphItemsForOwnDeaths) {
			if (remnant != null) {
				remnant.setEpitaph(dev.remnants.item.EpitaphItem.stack(data));
			}
		} else {
			Chronicle.record(player, data, Chronicle.DEATH);
		}
	}

	/** Moves every item out of the inventory. Curse of Vanishing items vanish (vanilla rule); compasses are re-given on respawn. */
	private static List<ItemStack> takeInventory(Inventory inventory) {
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.removeItemNoUpdate(i);
			if (stack.isEmpty() || stack.is(ModItems.SPECTRAL_COMPASS) || stack.is(ModItems.SOUL_CODEX)
				|| EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				continue;
			}
			items.add(stack);
		}
		return items;
	}

	/**
	 * Items that cross over with you instead of rising in the Remnant: the Soul Codex and Remembered items (sealed with a
	 * Seal of Memory, 1.5) always; the item in your main
	 * hand with Faithful Blade (Resonance III); your worn armor with Undying Memory (Resonance V). Stored in the
	 * tracker (they survive a server restart) and put back where they were on respawn.
	 */
	private static void bindSoulbound(ServerPlayer player, Inventory inventory, RemnantTracker tracker) {
		boolean blade = SoulResonance.has(player, SoulResonance.Perk.FAITHFUL_BLADE);
		boolean memory = SoulResonance.has(player, SoulResonance.Perk.UNDYING_MEMORY);
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty() || EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				continue;
			}
			boolean armorSlot = i >= Inventory.INVENTORY_SIZE && i < Inventory.INVENTORY_SIZE + 4;
			if (stack.is(ModItems.SOUL_CODEX) || stack.has(dev.remnants.registry.ModComponents.REMEMBERED)
				|| (blade && i == inventory.selected) || (memory && armorSlot)) {
				CompoundTag entry = new CompoundTag();
				entry.putInt("Slot", i);
				entry.put("Item", stack.save(player.registryAccess()));
				tracker.addSoulbound(player.getUUID(), entry);
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
	}

	private static void restoreSoulbound(ServerPlayer player, RemnantTracker tracker) {
		ListTag list = tracker.takeSoulbound(player.getUUID());
		if (list == null) {
			return;
		}
		Inventory inventory = player.getInventory();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompound(i);
			ItemStack stack = ItemStack.parse(player.registryAccess(), entry.getCompound("Item")).orElse(ItemStack.EMPTY);
			if (stack.isEmpty()) {
				continue;
			}
			int slot = entry.getInt("Slot");
			if (slot >= 0 && slot < inventory.getContainerSize() && inventory.getItem(slot).isEmpty()) {
				inventory.setItem(slot, stack);
			} else {
				inventory.placeItemBackInInventory(stack);
			}
		}
	}

	private static int xpForLevel(int level) {
		return level >= 30 ? 112 + (level - 30) * 9 : level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
	}

	/** All the experience points a player holds right now (levels + progress), as vanilla counts them. */
	public static int totalExperience(ServerPlayer player) {
		long points = 0;
		for (int l = 0; l < player.experienceLevel; l++) {
			points += xpForLevel(l);
		}
		points += Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
		return (int) Math.min(Integer.MAX_VALUE, points);
	}

	/**
	 * Adds experience points exactly (vanilla's giveExperiencePoints drifts on large amounts: 550 points can come
	 * back as 19 levels and 99.99 %). Recomputes level and progress from the new total.
	 */
	public static void giveExactExperience(ServerPlayer player, int points) {
		long total = (long) totalExperience(player) + points;
		int level = 0;
		while (level < 21863 && total >= xpForLevel(level)) {
			total -= xpForLevel(level);
			level++;
		}
		player.setExperienceLevels(level);
		player.setExperiencePoints((int) total);
		player.totalExperience += points;
	}

	private static int slotOf(Inventory inventory, Item item) {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(item)) {
				return i;
			}
		}
		return -1;
	}

	/** Cracks the Phylactery (it breaks on its third use) and stores the inventory until respawn (vanishing-cursed
	 * items still vanish, as they would have). */
	private static void keepWithPhylactery(ServerPlayer player, Inventory inventory, int slot, RemnantTracker tracker) {
		ItemStack phylactery = inventory.getItem(slot);
		int used = phylactery.getDamageValue() + 1;
		int left = phylactery.getMaxDamage() - used;
		if (left <= 0) {
			inventory.setItem(slot, ItemStack.EMPTY);
		} else {
			phylactery.setDamageValue(used);
		}
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (EnchantmentHelper.has(inventory.getItem(i), EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
		ListTag saved = new ListTag();
		inventory.save(saved);
		tracker.keepInventory(player.getUUID(), saved);
		inventory.clearContent();
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.blockPosition(), left <= 0 ? SoundEvents.TOTEM_USE : SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
			SoundSource.PLAYERS, 1.0F, 0.8F);
		level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.6, 0.4, 0.03);
		player.sendSystemMessage(Component.translatable(left <= 0 ? "message.remnants.phylactery.broke" : "message.remnants.phylactery.kept", left)
			.withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	/** The player's charged Soul Anchor if it is usable for a death here, else null (a vanished anchor is unbound). */
	@Nullable
	private static BlockPos anchorFor(ServerPlayer player, ServerLevel level, RemnantTracker tracker) {
		RemnantTracker.Location anchor = tracker.anchor(player.getUUID()).orElse(null);
		if (anchor == null || anchor.dimension() != level.dimension()) {
			return null;
		}
		if (!level.getBlockState(anchor.pos()).is(ModBlocks.SOUL_ANCHOR)) {
			tracker.clearAnchor(player.getUUID());
			player.sendSystemMessage(Component.translatable("message.remnants.anchor.lost").withStyle(ChatFormatting.GRAY));
			return null;
		}
		int range = RemnantsConfig.get().soulAnchorRange;
		if (range > 0 && anchor.pos().distSqr(player.blockPosition()) > (double) range * range) {
			return null;
		}
		return anchor.pos();
	}

	private static void giveBack(ServerPlayer player, List<ItemStack> items) {
		for (ItemStack stack : items) {
			player.getInventory().placeItemBackInInventory(stack);
		}
	}

	private static BlockPos safeSpawnPos(ServerLevel level, BlockPos death) {
		if (death.getY() > level.getMinBuildHeight()) {
			return death.getY() < level.getMaxBuildHeight() ? death : death.atY(level.getMaxBuildHeight() - 2);
		}
		// Fell into the void: rise on the surface above the death column instead, or at world spawn.
		BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, death);
		return surface.getY() > level.getMinBuildHeight() ? surface : level.getSharedSpawnPos();
	}

	private static final Set<UUID> PENDING_EMPOWERED_TITLE = new HashSet<>();
	/** Players whose next Remnant is guaranteed Empowered (/remnants empower). */
	public static final Set<UUID> FORCE_EMPOWER = new HashSet<>();

	/** Gives a Spectral Compass on respawn while the player has Remnants out there; returns soul-bound items. */
	public static void onRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (!alive) {
			restoreSoulbound(newPlayer, RemnantTracker.get(newPlayer.server));
		}
		if (!alive && PENDING_EMPOWERED_TITLE.remove(newPlayer.getUUID())) {
			newPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
			newPlayer.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.remnants.empowered.sub").withStyle(ChatFormatting.GRAY)));
			newPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.remnants.empowered.title").withStyle(ChatFormatting.LIGHT_PURPLE)));
			newPlayer.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6F, 1.4F);
		}
		if (!alive) {
			ListTag kept = RemnantTracker.get(newPlayer.server).takeKeptInventory(newPlayer.getUUID());
			if (kept != null) {
				newPlayer.getInventory().load(kept);
				newPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
				newPlayer.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.remnants.phylactery.sub").withStyle(ChatFormatting.GRAY)));
				newPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.remnants.phylactery.title").withStyle(ChatFormatting.LIGHT_PURPLE)));
			}
		}
		if (alive || !RemnantsConfig.get().giveSpectralCompassOnRespawn) {
			return;
		}
		RemnantTracker.get(newPlayer.server).prune(newPlayer.server, newPlayer.getUUID());
		if (RemnantTracker.get(newPlayer.server).any(newPlayer.getUUID()).isPresent()
			&& !newPlayer.getInventory().contains(new ItemStack(ModItems.SPECTRAL_COMPASS))) {
			newPlayer.getInventory().add(new ItemStack(ModItems.SPECTRAL_COMPASS));
		}
		SoulResonance.onRespawn(newPlayer);
	}

	/** First join: hand out the Soul Codex (once per world); every join: sync Soul Resonance for the Codex. */
	public static void onJoin(ServerPlayer player) {
		RemnantTracker tracker = RemnantTracker.get(player.server);
		if (RemnantsConfig.get().giveCodexOnFirstJoin && tracker.claimCodex(player.getUUID())
			&& !player.getInventory().contains(new ItemStack(ModItems.SOUL_CODEX))) {
			player.getInventory().add(new ItemStack(ModItems.SOUL_CODEX));
		}
		SoulResonance.sync(player);
		Chronicle.sync(player);
	}

	/** Server stopped (singleplayer: back to the title screen): nothing of this world may leak into the next one. */
	public static void reset() {
		PENDING_EMPOWERED_TITLE.clear();
		FORCE_EMPOWER.clear();
	}

	private DeathHandler() {
	}
}
