package dev.remnants;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * config/remnants_of_the_past.json. Unknown keys are ignored and missing keys fall back to defaults, and the file is
 * rewritten after every load so new options appear automatically. A broken file never crashes the game:
 * it is logged and the defaults are used.
 */
public final class RemnantsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("remnants_of_the_past.json");

	/** Bumped when default balance changes; older files get the new defaults for values left at the old ones. */
	public int configVersion = CURRENT_VERSION;
	/** Master switch. */
	public boolean enabled = true;
	/**
	 * RESPECT: with the keepInventory gamerule on, nothing happens (vanilla behaviour).
	 * BYPASS: a Remnant always takes your items, even with keepInventory on ("Minecraft but..." challenge mode).
	 */
	public String keepInventoryMode = "RESPECT";
	public boolean spawnForCreativePlayers = false;

	/** Health of the echo of a player who died with nothing; it grows with their gear up to baseHealth (enchanted netherite). */
	public double minHealth = 20.0;
	public double baseHealth = 100.0;
	public double healthMultiplier = 1.0;
	/** Fist damage; a share of the copied weapon's damage is added on top (see weaponDamageShare). */
	public double baseDamage = 2.0;
	/** How much of the weapon's own damage (and its Sharpness / Smite bonus) the ghostly copy deals. */
	public double weaponDamageShare = 0.4;
	public double damageMultiplier = 1.0;
	public double rangedDamageMultiplier = 1.0;
	/** How much of the copied armor's protection the ghost keeps (0..1). Toughness and Protection enchantments never carry over. */
	public double ghostArmorFactor = 0.35;
	/** Each consecutive death without reclaiming adds this fraction to health; damage grows half as fast. */
	public double scalingPerDeath = 0.15;
	public int maxScalingTier = 4;
	/** A new Remnant kneels where you fell until a player comes within wakeRadius blocks (or hits it): you choose when the fight starts. */
	public boolean dormantUntilApproached = true;
	public int wakeRadius = 9;
	/** Poise: hits wear it down; when it breaks the Remnant is staggered (stunned, takes +50 % damage). */
	public boolean poise = true;
	/** The Remnant also holds your experience (all of it, instead of the vanilla 7 points per level, max 100). */
	public boolean remnantKeepsExperience = true;
	/** Share of your experience points the Remnant holds and gives back (0..1). */
	public double experienceKept = 1.0;
	/** Soul Resonance: laying Remnants to rest earns points; ranks unlock permanent perks (see the Codex). */
	public boolean soulResonance = true;
	public boolean giveCodexOnFirstJoin = true;

	/** Ticks until an unclaimed Remnant fades away. -1 = never (default). */
	public long despawnTicks = -1;
	/** When a Remnant fades away, drop its items on the spot instead of deleting them. */
	public boolean dropItemsOnDespawn = true;

	/** Remnants hunt their owner first; with this on they will also fight other players. */
	public boolean attackOtherPlayers = true;
	public boolean giveSpectralCompassOnRespawn = true;
	/** Client-side visual. */
	public boolean particleTrail = true;
	public String nameFormat = "[Ghost] %s";
	public boolean showTierInName = true;
	public boolean announceSpawn = true;

	/** Chance (0..1) that the Remnant seizes your weapon and charges it: +60 % HP, but defeating it upgrades the weapon. */
	public double empoweredChance = 0.10;
	/** Highest Spectral level a weapon can reach (each level: +1 to every enchantment, + damage and speed). */
	public int maxSpectralLevel = 3;
	public boolean bossBar = true;
	/** Mirror Echoes: one-hit decoy clones in phases 2 and 3. */
	public boolean illusions = true;
	/** Scales every ability cooldown (lower = more aggressive boss). */
	public double abilityCooldownMultiplier = 1.0;

	/** Chance a defeated Remnant drops Soul Fragments (Empowered ones always drop 3-5 and their crown). */
	public double soulFragmentChance = 0.6;
	/** Forgotten Remnants: natural echoes of long-dead adventurers, the renewable source of Soul Fragments. */
	public boolean forgottenRemnants = true;
	/** Spawn weight among overworld night monsters (zombie = 95, skeleton = 100) and in the Soul Sand Valley. */
	public int forgottenSpawnWeightOverworld = 5;
	public int forgottenSpawnWeightSoulValley = 12;
	public double forgottenHealth = 30.0;
	public int forgottenFragmentsMin = 1;
	public int forgottenFragmentsMax = 3;
	/** Chance a Forgotten Remnant is Crowned: double health, drops a Remnant Crown and 2 more fragments. */
	public double forgottenCrownedChance = 0.05;
	/** Wither Skeletons killed by a player drop a Soul Fragment with this chance (+0.02 per Looting level). */
	public double witherSkeletonFragmentChance = 0.08;
	/** How far (blocks, same dimension) a death may be from your Soul Anchor for the Remnant to rise there. 0 = any. */
	public int soulAnchorRange = 0;

	/** Forgotten Remnants take the look and signature move of the land they rise in (Drowned, Frozen, Sandy). */
	public boolean forgottenVariants = true;
	/** Forgotten Remnants climb out of the ground when they spawn (invulnerable for those two seconds). */
	public boolean emergeAnimation = true;
	/** One chunk in this many gets a Forgotten Grave (0 = none). Only affects newly generated chunks. */
	public int forgottenGraveRarity = 12;
	/** Chance the grave's owner climbs out when a Burial Casket is first opened. */
	public double graveOwnerChance = 0.2;
	/** Chance an opened casket also holds the epitaph of whoever lay there. */
	public double graveEpitaphChance = 0.3;
	/** Every death of yours leaves an Epitaph (written into your Chronicle in the Codex). */
	public boolean epitaphs = true;
	/** The 1.2.0 way: instead of the Chronicle, your Remnant carries the Epitaph as an item and gives it back with the gear. */
	public boolean epitaphItemsForOwnDeaths = false;
	/** Bone Chakram damage per hit (Sharpness and Smite add to it as usual). */
	public double chakramDamage = 5.0;

	/** Reflecting Pool (1.3): experience levels and Soul Fragments per enchantment level moved; treasure ones cost this much more. */
	public int poolLevelsPerEnchantLevel = 3;
	public int poolFragmentsPerEnchantLevel = 1;
	public double poolTreasureMultiplier = 2.0;
	/** Soul Crucible rhythm: a strike within this many ticks of the beat is perfect / good; network lag forgiven up to forgeLagTicks. */
	public int forgePerfectWindow = 3;
	public int forgeGoodWindow = 6;
	public int forgeLagTicks = 4;
	/** Mourner's set: Ghost Step (a killing blow becomes this many ticks of ghost form instead), and its cooldown. */
	public int ghostStepTicks = 40;
	public int ghostStepCooldown = 12000;

	/** Crypt Warden (1.4): health; it only takes damage from behind or while it recovers from a miss. */
	public double wardenHealth = 160.0;
	public double wardenDamageMultiplier = 1.0;
	/** Warden's Gauntlet in the off hand: melee damage bonus against a target that is not facing you. */
	public double gauntletBackstabBonus = 0.4;
	/** Hand Bell: radius (blocks) and cooldown (ticks). */
	public int handBellRadius = 12;
	public int handBellCooldown = 400;
	/** Soul Tether: range (blocks) and cooldown (ticks). */
	public int tetherRange = 24;
	public int tetherCooldown = 60;
	/** Night of Memory: wave sizes (+ this many per extra player), radius the dead rise in, cooldown per bell (ticks). */
	public int[] nightWaveSizes = {4, 6, 8};
	public int nightExtraPerPlayer = 2;
	public int nightRadius = 24;
	public long nightCooldown = 24000L;
	public int nightFragmentsMin = 6;
	public int nightFragmentsMax = 10;
	public double nightCrownChance = 0.1;
	public int nightResonance = 4;

	/** The Archivist (1.5): health and damage; Path of the Soul points for everyone in the fight. */
	public double archivistHealth = 400.0;
	public double archivistDamageMultiplier = 1.0;
	public int archivistResonance = 12;
	/** Slow the world for about a second when the Archivist falls (singleplayer only). */
	public boolean bossFinaleSlowMotion = true;
	/** Archivist's Quill: ticks between two copies (one game day). */
	public long quillCooldown = 24000L;
	/** The last 10 s before every death are recorded for the Memorial's projector. */
	public boolean lastRecord = true;
	/** Memorial: Path of the Soul bonus for whoever locked a full wall (0.1 = +10 %). */
	public double memorialResonanceBonus = 0.1;
	/** Soul Astrolabe: how long the beam holds (ticks) and how far the map focus searches (chunks). */
	public int astrolabeBeamTicks = 1200;
	public int astrolabeSearchChunks = 100;
	/** Soul Anchor network: anchors per player, teleport cost (Soul Fragments) and cooldown (ticks). */
	public int anchorNetworkSize = 16;
	public int anchorTeleportCost = 1;
	public int anchorTeleportCooldown = 200;
	/** Path of the Soul: the rank at which a branch (Reaper / Keeper / Wanderer) is chosen. */
	public int branchRank = 3;
	/** Soul Lantern: the health share a mob must be down to; how long a released ally fights (ticks). */
	public double lanternCatchHealth = 0.25;
	public int lanternAllyTicks = 1200;
	/** Decoy Echo: lifetime (ticks), health, how far it draws monsters. */
	public int decoyTicks = 160;
	public double decoyHealth = 20.0;
	public double decoyRadius = 16.0;
	/** Carrion Crows: a Remnant left this long (world ticks) draws a flock; one stack is carried off every interval, one
	 * per crow at most. */
	public boolean carrionCrows = true;
	public long crowDelayTicks = 12000L;
	public long crowIntervalTicks = 4800L;
	public int crowFlockMin = 3;
	public int crowFlockMax = 5;
	/** Shroud Glider: rising air over fires this many blocks below still lifts. */
	public int gliderUpdraftHeight = 20;

	/** Soul King health (it also has 3 breakable heads and crystal shields in phase 2). */
	public double soulKingHealth = 300.0;
	public double soulKingDamageMultiplier = 1.0;

	/** Client (art 2.0 hit feel): camera shake from heavy blows near you, 0 = off .. 1 = full. */
	public double screenShake = 1.0;
	/** Client: attacker and victim freeze for a tick or three when a blow lands (hit-stop). */
	public boolean hitStop = true;
	/** Client: wind-up glints and ground telegraphs (where and when a boss's strike will land). */
	public boolean telegraphs = true;

	private static final int CURRENT_VERSION = 3;
	private static RemnantsConfig instance = new RemnantsConfig();

	public static RemnantsConfig get() {
		return instance;
	}

	public boolean bypassKeepInventory() {
		return "BYPASS".equalsIgnoreCase(keepInventoryMode);
	}

	public static void load() {
		RemnantsConfig loaded = null;
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				JsonObject json = GSON.fromJson(reader, JsonObject.class);
				loaded = GSON.fromJson(json, RemnantsConfig.class);
				int version = json.has("configVersion") ? json.get("configVersion").getAsInt() : 1;
				if (loaded != null && version < 2) {
					loaded.migrateFrom1();
				}
				if (loaded != null && version < 3) {
					loaded.migrateFrom2();
				}
			} catch (Exception e) {
				RemnantsMod.LOGGER.error("Could not read {}, using defaults", PATH, e);
			}
			if (loaded == null) {
				// keep the player's file (one typo must not wipe every tuned value): the defaults are written beside it
				try {
					Path broken = PATH.resolveSibling("remnants_of_the_past.json.broken");
					Files.copy(PATH, broken, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					RemnantsMod.LOGGER.error("The unreadable config was kept as {}", broken);
				} catch (IOException e) {
					RemnantsMod.LOGGER.error("Could not back up {}", PATH, e);
				}
			}
		}
		instance = loaded != null ? loaded : new RemnantsConfig();
		instance.configVersion = CURRENT_VERSION;
		instance.save();
	}

	/** 1.0 balance -> 1.1: values still at the old defaults move to the new ones; anything the player tuned is kept. */
	private void migrateFrom1() {
		RemnantsConfig fresh = new RemnantsConfig();
		if (this.baseHealth == 80.0) {
			this.baseHealth = fresh.baseHealth;
		}
		if (this.scalingPerDeath == 0.25) {
			this.scalingPerDeath = fresh.scalingPerDeath;
		}
		if (this.maxScalingTier == 8) {
			this.maxScalingTier = fresh.maxScalingTier;
		}
		RemnantsMod.LOGGER.info("Remnants config updated to the 1.1 balance");
	}

	/** 1.1.0 -> 1.1.1: the Soul King was far too tanky for what he drops; halve his health unless it was tuned. */
	private void migrateFrom2() {
		if (this.soulKingHealth == 600.0) {
			this.soulKingHealth = new RemnantsConfig().soulKingHealth;
			RemnantsMod.LOGGER.info("Remnants config: Soul King health 600 -> {}", this.soulKingHealth);
		}
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			RemnantsMod.LOGGER.error("Could not write {}", PATH, e);
		}
	}
}
