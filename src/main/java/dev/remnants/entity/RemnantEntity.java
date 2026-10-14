package dev.remnants.entity;

import dev.remnants.RemnantTracker;
import dev.remnants.RemnantsConfig;
import dev.remnants.RemnantsMod;
import dev.remnants.SoulResonance;
import dev.remnants.entity.ai.CombatStyle;
import dev.remnants.entity.ai.RemnantBrain;
import dev.remnants.item.SpectralForge;
import dev.remnants.registry.ModEntities;
import dev.remnants.registry.ModItems;
import dev.remnants.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Remnant: a hostile echo of a dead player and a three-phase boss. It wears copies of the player's gear,
 * keeps the real items in {@link #storedItems} and gives them back when defeated. Its fighting is driven by
 * {@link RemnantBrain}; this class holds the state the brain and the renderer share (phase, animation pose,
 * stance, shield, stun, poise), the boss bars, illusions and the Empowered / Spectral weapon mechanic.
 *
 * <p>Balance (1.1): the player comes back with nothing, so the ghost must be beatable with a fresh kit. Its copied
 * armor is hollow ({@link RemnantsConfig#ghostArmorFactor}), its weapon deals only a share of the real one's damage,
 * every strike is wound up before it lands, big moves are followed by a recovery window, and a poise bar rewards
 * pressure (and perfect shield parries) with a stagger. It kneels where you fell until you come close.
 */
public class RemnantEntity extends Monster implements RangedAttackMob, LookTarget {
	private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_EMPOWERED = SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_ILLUSION = SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_SHIELD_UP = SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> DATA_POISE = SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.FLOAT);
	// 1.2: biome kinds of the Forgotten, the Frozen one's ice crust, climbing out of the ground and burrowing into it
	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_CRUST = SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_EMERGE = SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_EMERGE_TOTAL = SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SINK = SynchedEntityData.defineId(RemnantEntity.class, EntityDataSerializers.INT);

	private static final ResourceLocation TIER_DAMAGE = RemnantsMod.id("tier_damage");
	private static final ResourceLocation TIER_SPEED = RemnantsMod.id("tier_speed");
	private static final ResourceLocation ENRAGE_SPEED = RemnantsMod.id("enrage_speed");
	private static final ResourceLocation TIDE_SPEED = RemnantsMod.id("tide_speed");
	/** Ticks a Forgotten Remnant takes to dig itself out of the ground (hand, head, then a lunge). */
	public static final int EMERGE_TICKS = 40;
	/** Ticks a burrowing Remnant takes to sink out of sight. */
	public static final int SINK_TICKS = 12;
	private static final int LINGER_RADIUS = 12;
	private static final int ILLUSION_LIFE = 400;

	/** Animation poses, synced to the client model. */
	public static final int ANIM_NONE = 0, ANIM_RAISE = 1, ANIM_NOVA = 2, ANIM_STANCE = 3, ANIM_CHARGE = 4, ANIM_STUNNED = 5, ANIM_CAST = 6, ANIM_AIM = 7, ANIM_ROAR = 8,
		ANIM_DORMANT = 9, ANIM_WINDUP = 10, ANIM_RECOVER = 11;
	public static final float MAX_POISE = 100.0F;
	/** Ticks a shield may have been raised for and still count as a perfect parry (vanilla blocks from tick 5). */
	public static final int PARRY_WINDOW = 12;

	private final List<ItemStack> storedItems = new ArrayList<>();
	private final ServerBossEvent bossEvent = new dev.remnants.bossbar.StyledBossEvent(Component.literal("Remnant"), BossEvent.BossBarColor.BLUE,
		BossEvent.BossBarOverlay.NOTCHED_10, dev.remnants.bossbar.BossStyle.ECHO);
	private final ServerBossEvent poiseEvent = new dev.remnants.bossbar.StyledBossEvent(Component.translatable("bossbar.remnants.poise"),
		BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_6, dev.remnants.bossbar.BossStyle.POISE);
	private RemnantBrain brain;
	private String ownerName = "";
	private long lifeTicks;
	/** Carrion Crows (1.5): world time the owner was last near (0 = not yet set), and stacks the crows have taken. */
	private long abandonedSince;
	private int crowSteals;
	@Nullable
	private BlockPos anchor;
	private boolean itemsReleased;
	/** Death handled once (items, resonance, tracker), whichever of the two death-loot hooks gets there first. */
	private boolean laidToRest;

	// combat state used by the brain
	private int transitionTicks;
	private int stanceTicks;
	private int stunTicks;
	private int shieldDisabledTicks;
	private int outOfCombatTicks;
	private int recoverTicks;
	private int poiseIdleTicks;
	private int poiseImmuneTicks;
	private int awakenTicks;
	private boolean dormant;
	/** Experience points taken from the owner at death; given back to whoever lays the Remnant to rest. */
	private int storedXp;
	/** How the owner died (their death message), shown when the Remnant wakes. */
	@Nullable
	private Component lastWords;
	/** How strong its owner was when they died (0 = naked, fists .. 1 = enchanted netherite), and the echo rank from it. */
	private float gearScore = 1.0F;
	private int echoRank = 4;
	@Nullable
	private UUID masterId;
	/** The epitaph of the death that raised this Remnant; left for whoever lays it to rest. */
	private ItemStack epitaph = ItemStack.EMPTY;
	/** Whether the variant's kit (crust, gear) was handed out; false after an override from a spawn egg / NBT. */
	private boolean variantKit;
	/** 1.4: the armour piece a Soul Tether tore off, and how long until it floats back on. */
	@Nullable
	private EquipmentSlot strippedSlot;
	private ItemStack strippedItem = ItemStack.EMPTY;
	private int strippedTicks;

	public RemnantEntity(EntityType<? extends RemnantEntity> type, Level level) {
		super(type, level);
		this.xpReward = 50;
		if (type != ModEntities.FORGOTTEN_REMNANT) {
			this.setPersistenceRequired(); // a player's Remnant waits forever; the Forgotten despawn like monsters
		}
		this.bossEvent.setVisible(false);
		this.poiseEvent.setVisible(false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 100.0)
			.add(Attributes.MOVEMENT_SPEED, 0.27)
			.add(Attributes.ATTACK_DAMAGE, 2.0)
			.add(Attributes.FOLLOW_RANGE, 48.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
			.add(Attributes.STEP_HEIGHT, 1.0)
			.add(Attributes.SAFE_FALL_DISTANCE, 64.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_LOOK, -1);
		builder.define(DATA_OWNER, Optional.empty());
		builder.define(DATA_TIER, 0);
		builder.define(DATA_PHASE, 1);
		builder.define(DATA_ANIM, ANIM_NONE);
		builder.define(DATA_EMPOWERED, false);
		builder.define(DATA_ILLUSION, false);
		builder.define(DATA_SHIELD_UP, false);
		builder.define(DATA_POISE, MAX_POISE);
		builder.define(DATA_VARIANT, 0);
		builder.define(DATA_CRUST, false);
		builder.define(DATA_EMERGE, 0);
		builder.define(DATA_EMERGE_TOTAL, EMERGE_TICKS);
		builder.define(DATA_SINK, 0);
	}

	@Override
	protected void registerGoals() {
		this.brain = new RemnantBrain(this);
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(0, new DormantGoal());
		this.goalSelector.addGoal(1, this.brain);
		this.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, RemnantEntity.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
			player -> !this.dormant && this.isOwner(player)));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
			player -> !this.dormant && (RemnantsConfig.get().attackOtherPlayers || this.isIllusion() || this.isForgotten())));
	}

	/** While dormant the Remnant kneels: this goal holds MOVE / LOOK / JUMP so strolling and looking around stay off. */
	private final class DormantGoal extends Goal {
		DormantGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return RemnantEntity.this.dormant || RemnantEntity.this.isEmerging();
		}

		@Override
		public void tick() {
			RemnantEntity.this.getNavigation().stop();
		}
	}

	// ---- setup from a dead player ------------------------------------------------------------------

	/** Called once, server side, right after the owner died. {@code items} are the real items (moved, not copied). */
	public void rise(ServerPlayer owner, List<ItemStack> items, EquipmentSnapshot look, int tier, boolean forceEmpower) {
		this.rise(owner, items, look, tier, forceEmpower, false);
	}

	/** {@code weakened}: a Soul Ward burned for this death - never Empowered, 40 % less health. */
	public void rise(ServerPlayer owner, List<ItemStack> items, EquipmentSnapshot look, int tier, boolean forceEmpower, boolean weakened) {
		this.entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
		this.entityData.set(DATA_TIER, tier);
		this.ownerName = owner.getGameProfile().getName();
		this.storedItems.clear();
		this.storedItems.addAll(items);
		this.anchor = this.blockPosition();
		this.restrictTo(this.anchor, LINGER_RADIUS);

		ItemStack mainHand = look.mainHand();
		RemnantsConfig cfg = RemnantsConfig.get();
		if (!weakened && !items.isEmpty() && SpectralForge.canUpgrade(mainHand, cfg.maxSpectralLevel)
			&& (forceEmpower || this.random.nextDouble() < cfg.empoweredChance)) {
			mainHand = this.empower(mainHand);
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() == EquipmentSlot.Type.ANIMAL_ARMOR) {
				continue;
			}
			this.setItemSlot(slot, slot == EquipmentSlot.MAINHAND ? mainHand : look.get(slot));
			this.setDropChance(slot, 0.0F);
		}

		this.gearScore = gearScore(look);
		this.echoRank = Math.min(4, rankFor(this.gearScore) + (this.isEmpowered() ? 1 : 0));
		this.applyTierStats();
		this.dormant = cfg.dormantUntilApproached;
		this.setAnim(this.dormant ? ANIM_DORMANT : ANIM_NONE);
		if (weakened) {
			AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
			if (health != null) {
				health.setBaseValue(health.getBaseValue() * 0.6);
				this.setHealth(this.getMaxHealth());
			}
		}
		this.refreshName();
		this.yHeadRot = owner.getYHeadRot();
		this.setYRot(owner.getYRot());
	}

	/**
	 * The Remnant seizes the weapon and charges it with ghostly energy: the stored original is replaced by a Spectral
	 * copy one level higher, which the player wins by defeating it.
	 */
	private ItemStack empower(ItemStack shown) {
		ItemStack upgraded = SpectralForge.upgrade(shown, this.registryAccess());
		for (int i = 0; i < this.storedItems.size(); i++) {
			if (ItemStack.matches(this.storedItems.get(i), shown)) {
				this.storedItems.set(i, upgraded.copy());
				this.entityData.set(DATA_EMPOWERED, true);
				return upgraded.copy();
			}
		}
		return shown;
	}

	/** Copies of what the player looked like at death; only used for display, never dropped. */
	public record EquipmentSnapshot(ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet, ItemStack mainHand, ItemStack offHand) {
		public static EquipmentSnapshot of(LivingEntity e) {
			return new EquipmentSnapshot(
				e.getItemBySlot(EquipmentSlot.HEAD).copy(), e.getItemBySlot(EquipmentSlot.CHEST).copy(),
				e.getItemBySlot(EquipmentSlot.LEGS).copy(), e.getItemBySlot(EquipmentSlot.FEET).copy(),
				e.getMainHandItem().copy(), e.getOffhandItem().copy());
		}

		ItemStack get(EquipmentSlot slot) {
			return switch (slot) {
				case HEAD -> head;
				case CHEST -> chest;
				case LEGS -> legs;
				case FEET -> feet;
				case MAINHAND -> mainHand;
				case OFFHAND -> offHand;
				default -> ItemStack.EMPTY;
			};
		}
	}

	/**
	 * How strong the dead player was: worn armor (and its toughness), the weapon's damage, and enchantment levels.
	 * 0 = naked with fists, ~0.15 = naked with a wooden sword, ~0.6 = iron kit, ~0.8 = diamond, 1 = enchanted netherite.
	 */
	public static float gearScore(EquipmentSnapshot look) {
		double armor = 0;
		double toughness = 0;
		int enchants = 0;
		for (ItemStack piece : new ItemStack[]{look.head(), look.chest(), look.legs(), look.feet()}) {
			if (piece.getItem() instanceof ArmorItem armorItem) {
				// forge quality (1.3) adds to the defence; a forged weapon's bonus is already in its attack modifiers below
				armor += armorItem.getDefense() * (1.0 + dev.remnants.item.ForgeQuality.percent(piece) / 100.0);
				toughness += armorItem.getToughness();
			}
			enchants += piece.getEnchantments().entrySet().stream().mapToInt(en -> en.getIntValue()).sum();
		}
		ItemStack weapon = look.mainHand();
		double damage = 1.0;
		var modifiers = weapon.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
		if (modifiers != null) {
			for (var entry : modifiers.modifiers()) {
				if (entry.attribute().equals(Attributes.ATTACK_DAMAGE) && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
					damage += entry.modifier().amount();
				}
			}
		}
		if (weapon.getItem() instanceof ProjectileWeaponItem) {
			damage = Math.max(damage, 6.0); // a bow kills like an iron sword
		}
		enchants += weapon.getEnchantments().entrySet().stream().mapToInt(en -> en.getIntValue()).sum();
		double score = 0.45 * Math.min(1.0, (armor + toughness * 0.5) / 20.0)
			+ 0.40 * Math.min(1.0, Math.max(0.0, damage - 1.0) / 7.0)
			+ 0.15 * Math.min(1.0, enchants / 20.0);
		return (float) Math.max(0.0, Math.min(1.0, score));
	}

	/** Faint (1) below 0.3, Echo (2) below 0.55, Strong (3) below 0.8, Great (4) above. */
	public static int rankFor(float gearScore) {
		return gearScore < 0.3F ? 1 : gearScore < 0.55F ? 2 : gearScore < 0.8F ? 3 : 4;
	}

	public int getEchoRank() {
		return this.echoRank;
	}

	public float getGearScore() {
		return this.gearScore;
	}

	private void applyTierStats() {
		RemnantsConfig cfg = RemnantsConfig.get();
		double empowered = this.isEmpowered() ? 1.5 : 1.0;

		// an echo is as strong as its owner was: ~a player's health with little gear (wooden sword: ~26), 60 in iron,
		// ~76 in diamond, baseHealth in enchanted netherite
		double hp = cfg.minHealth + (cfg.baseHealth - cfg.minHealth) * Math.pow(this.gearScore, 1.5);
		AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(Math.max(1.0, hp * cfg.healthMultiplier * this.tierScale() * empowered));
		}
		AttributeInstance damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) {
			// streak and Empowered scaling live in weaponDamage(); the attribute only carries fist + weapon
			damage.setBaseValue(Math.max(0.0, cfg.baseDamage));
			damage.removeModifier(TIER_DAMAGE);
		}
		AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.addOrReplacePermanentModifier(new AttributeModifier(TIER_SPEED, Math.min(0.08, 0.02 * this.getTier()),
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		this.setHealth(this.getMaxHealth());
		this.xpReward = (this.isEmpowered() ? 100 : 40) + 10 * this.getTier();
	}

	private int scaledTier() {
		return Math.min(this.getTier(), Math.max(0, RemnantsConfig.get().maxScalingTier));
	}

	/** Health multiplier from the death streak. */
	private double tierScale() {
		return 1.0 + RemnantsConfig.get().scalingPerDeath * this.scaledTier();
	}

	/** Damage multiplier: the streak counts half as much for damage as for health, so a naked comeback stays possible. */
	public double damageScale() {
		RemnantsConfig cfg = RemnantsConfig.get();
		return cfg.damageMultiplier * (1.0 + cfg.scalingPerDeath * 0.5 * this.scaledTier()) * (this.isEmpowered() ? 1.15 : 1.0);
	}

	private void refreshName() {
		RemnantsConfig cfg = RemnantsConfig.get();
		String who = this.ownerName.isEmpty() ? "???" : this.ownerName;
		String label;
		try {
			label = String.format(cfg.nameFormat, who);
		} catch (Exception e) {
			label = "[Ghost] " + who;
		}
		Component name = Component.literal((this.isEmpowered() ? "✦ " : "") + label)
			.withStyle(this.isEmpowered() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA, ChatFormatting.ITALIC);
		if (cfg.showTierInName && this.getTier() > 0) {
			name = name.copy().append(Component.literal(" " + roman(this.getTier() + 1)).withStyle(ChatFormatting.DARK_AQUA));
		}
		name = name.copy().append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
			.append(Component.translatable("entity.remnants_of_the_past.echo_rank." + this.echoRank).withStyle(ChatFormatting.GRAY));
		this.setCustomName(name);
		this.setCustomNameVisible(true);
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		super.setCustomName(name);
		this.bossEvent.setName(this.getDisplayName());
	}

	private static String roman(int n) {
		String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n < r.length ? r[n] : Integer.toString(n);
	}

	// ---- accessors ---------------------------------------------------------------------------------

	/** A Forgotten Remnant: the natural kind, echo of some long-dead adventurer rather than of a player. No owner, no
	 * items held for anyone, no boss bar or phases; it despawns like any monster and drops Soul Fragments. */
	public boolean isForgotten() {
		return this.getType() == ModEntities.FORGOTTEN_REMNANT;
	}

	public Optional<UUID> getOwnerUuid() {
		return this.entityData.get(DATA_OWNER);
	}

	public int getTier() {
		return this.entityData.get(DATA_TIER);
	}

	public int getPhase() {
		return this.entityData.get(DATA_PHASE);
	}

	public int getAnim() {
		return this.entityData.get(DATA_ANIM);
	}

	public void setAnim(int anim) {
		this.entityData.set(DATA_ANIM, anim);
	}

	public boolean isEmpowered() {
		return this.entityData.get(DATA_EMPOWERED);
	}

	public boolean isIllusion() {
		return this.entityData.get(DATA_ILLUSION);
	}

	public boolean isShieldUp() {
		return this.entityData.get(DATA_SHIELD_UP);
	}

	public void setShieldUp(boolean up) {
		this.entityData.set(DATA_SHIELD_UP, up && this.canRaiseShield());
	}

	public boolean canRaiseShield() {
		return this.getOffhandItem().is(Items.SHIELD) && this.shieldDisabledTicks <= 0 && this.stunTicks <= 0;
	}

	public String getOwnerName() {
		return this.ownerName;
	}

	public long abandonedSince() {
		return this.abandonedSince;
	}

	public void setAbandonedSince(long gameTime) {
		this.abandonedSince = gameTime;
	}

	public int crowSteals() {
		return this.crowSteals;
	}

	public void addCrowSteal() {
		this.crowSteals++;
	}

	/** What a crow may take: supplies, never gear (anything with durability), never the epitaph. */
	private static boolean crowLoot(ItemStack s) {
		return !s.isEmpty() && !s.isDamageableItem();
	}

	public boolean hasCrowLoot() {
		return !this.itemsReleased && this.storedItems.stream().anyMatch(RemnantEntity::crowLoot);
	}

	/** A Carrion Crow snatches one stack of supplies (a random one); empty if there is nothing it would take. */
	public ItemStack takeForCrow(net.minecraft.util.RandomSource random) {
		List<Integer> picks = new ArrayList<>();
		for (int i = 0; i < this.storedItems.size(); i++) {
			if (crowLoot(this.storedItems.get(i))) {
				picks.add(i);
			}
		}
		if (picks.isEmpty() || this.itemsReleased) {
			return ItemStack.EMPTY;
		}
		return this.storedItems.remove((int) picks.get(random.nextInt(picks.size())));
	}

	public List<ItemStack> getStoredItems() {
		return this.storedItems;
	}

	public boolean isOwner(LivingEntity entity) {
		return this.getOwnerUuid().map(entity.getUUID()::equals).orElse(false);
	}

	public boolean isRangedMode() {
		return this.getMainHandItem().getItem() instanceof ProjectileWeaponItem;
	}

	/** Starts one of its moves right now, ignoring cooldowns and scores (game tests, debugging). */
	public void forceAbility(Class<? extends dev.remnants.entity.ai.Ability> type) {
		if (this.brain != null) {
			this.brain.force(type);
		}
	}

	public String debugBrain() {
		return this.brain == null ? "none" : this.brain.debugState();
	}

	public CombatStyle getStyle() {
		return CombatStyle.of(this.getMainHandItem());
	}

	/** Roaring into a new phase (invulnerable) or getting up from its kneel (vulnerable): the brain holds still. */
	public boolean isTransitioning() {
		return this.transitionTicks > 0 || this.awakenTicks > 0 || this.dormant || this.isEmerging();
	}

	public boolean isStunned() {
		return this.stunTicks > 0;
	}

	public void stun(int ticks) {
		this.stunTicks = Math.max(this.stunTicks, ticks);
		this.setShieldUp(false);
	}

	public boolean inStance() {
		return this.stanceTicks > 0;
	}

	public void setStance(int ticks) {
		this.stanceTicks = ticks;
		this.setGlowingTag(ticks > 0);
	}

	/**
	 * One basic hit: fist damage plus a share of the copied weapon's own damage (a netherite sword: 2 + 0.4 x 7 = 4.8),
	 * times the streak / Empowered scaling. Abilities are built on this number too.
	 */
	public float weaponDamage() {
		RemnantsConfig cfg = RemnantsConfig.get();
		AttributeInstance attr = this.getAttribute(Attributes.ATTACK_DAMAGE);
		double base = attr != null ? attr.getBaseValue() : cfg.baseDamage;
		double weapon = Math.max(0.0, this.getAttributeValue(Attributes.ATTACK_DAMAGE) - base);
		return (float) ((base + weapon * cfg.weaponDamageShare) * this.damageScale());
	}

	/** Damage multiplier for abilities: illusions hit softly, enraged Remnants a little harder. */
	public float abilityScale() {
		return (this.isIllusion() ? 0.25F : 1.0F) * (this.getPhase() >= 3 ? 1.1F : 1.0F);
	}

	/** Soul Bond (Soul Resonance rank II): your own Remnants hit you 15 % softer. */
	public float againstTarget(LivingEntity target, float amount) {
		if (target instanceof ServerPlayer player && this.isOwner(player) && SoulResonance.has(player, SoulResonance.Perk.SOUL_BOND)) {
			return amount * 0.85F;
		}
		return amount;
	}

	// ---- recovery, poise, dormancy ------------------------------------------------------------------

	/** After a big move the Remnant stands catching its breath: no attacks, no movement. Free hits. */
	public void recover(int ticks) {
		if (ticks > 0 && !this.isIllusion()) {
			this.recoverTicks = Math.max(this.recoverTicks, ticks);
			this.setAnim(ANIM_RECOVER);
		}
	}

	public boolean isRecovering() {
		return this.recoverTicks > 0;
	}

	public float getPoise() {
		return this.entityData.get(DATA_POISE);
	}

	/** Wears the poise bar down; at zero the Remnant is staggered. Returns true if this broke it. */
	public boolean damagePoise(float amount, @Nullable LivingEntity by) {
		if (!RemnantsConfig.get().poise || this.isIllusion() || this.isForgotten() || this.poiseImmuneTicks > 0 || this.isTransitioning()) {
			return false;
		}
		this.poiseIdleTicks = 0;
		float left = this.getPoise() - amount;
		if (left > 0) {
			this.entityData.set(DATA_POISE, left);
			return false;
		}
		this.entityData.set(DATA_POISE, 0.0F);
		this.stagger(by);
		return true;
	}

	private void stagger(@Nullable LivingEntity by) {
		if (this.brain != null) {
			this.brain.interrupt();
		}
		this.recoverTicks = 0;
		this.stun(50);
		this.poiseImmuneTicks = 50 + 60;
		this.sound(SoundEvents.SHIELD_BREAK, 1.6F, 0.6F);
		this.sound(SoundEvents.WARDEN_SONIC_BOOM, 0.5F, 1.8F);
		ServerLevel level = this.serverLevel();
		level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.4, this.getZ(), 30, 0.4, 0.5, 0.4, 0.08);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, this.getX(), this.getY() + 1.4, this.getZ(), 25, 0.5, 0.6, 0.5, 0.3);
		level.sendParticles(ModParticles.TWINKLE, this.getX(), this.getY() + 2.2, this.getZ(), 6, 0.4, 0.1, 0.4, 0.02);
		level.sendParticles(ModParticles.SOUL_BURST, this.getX(), this.getY() + 1.2, this.getZ(), 1, 0, 0, 0, 0);
		if (by instanceof ServerPlayer sp) {
			sp.displayClientMessage(Component.translatable("message.remnants.staggered").withStyle(ChatFormatting.GOLD), true);
		}
	}

	public boolean isDormant() {
		return this.dormant;
	}

	/** Rises from its kneel: a short, vulnerable getting-up (no attacks), with the owner's last words as a title. */
	public void wake(@Nullable LivingEntity by) {
		if (!this.dormant) {
			return;
		}
		this.dormant = false;
		this.awakenTicks = 30;
		this.setAnim(ANIM_ROAR);
		this.sound(SoundEvents.SOUL_ESCAPE.value(), 2.0F, 0.5F);
		this.sound(SoundEvents.WARDEN_EMERGE, 0.7F, 1.4F);
		this.serverLevel().sendParticles(ModParticles.REMNANT_SOUL, this.getX(), this.getY() + 1, this.getZ(), 40, 0.5, 0.9, 0.5, 0.06);
		this.serverLevel().sendParticles(ModParticles.SKULL_WISP, this.getX(), this.getY() + 2.6, this.getZ(), 1, 0, 0, 0, 0);
		this.serverLevel().sendParticles(ModParticles.SOUL_BURST, this.getX(), this.getY() + 1.1, this.getZ(), 1, 0, 0, 0, 0);
		this.serverLevel().sendParticles(ModParticles.SHOCK_RING, this.getX(), this.getY() + 0.2, this.getZ(), 1, 0, 0, 0, 0);
		Component sub = this.lastWords != null ? this.lastWords.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
			: Component.translatable("message.remnants.awaken.sub").withStyle(ChatFormatting.GRAY);
		this.title(this.getDisplayName().copy(), sub, 24);
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		if (target != null && this.dormant && !this.level().isClientSide) {
			this.wake(target);
		}
		super.setTarget(target);
	}

	public void setStoredXp(int xp) {
		this.storedXp = Math.max(0, xp);
	}

	public int getStoredXp() {
		return this.storedXp;
	}

	public void setLastWords(@Nullable Component words) {
		this.lastWords = words;
	}

	/** Client-side display copies (the Codex's portrait): wear this player's skin. */
	public void setDisplayOwner(UUID owner) {
		this.entityData.set(DATA_OWNER, Optional.of(owner));
	}

	// ---- 1.2: biome variants, the ice crust, climbing out of the ground, burrowing, epitaphs ----------

	public ForgottenVariant getVariant() {
		return ForgottenVariant.byId(this.entityData.get(DATA_VARIANT));
	}

	/** Sets the kind; {@link #applyVariantKit} hands out what comes with it. Also used by the Codex's display copies. */
	public void setVariant(ForgottenVariant variant) {
		this.entityData.set(DATA_VARIANT, variant.ordinal());
	}

	/** What a variant brings: the Frozen one's ice crust, the Drowned one's occasional trident. */
	public void applyVariantKit() {
		this.variantKit = true;
		ForgottenVariant v = this.getVariant();
		this.entityData.set(DATA_CRUST, v == ForgottenVariant.FROZEN);
		if (v == ForgottenVariant.DROWNED && !this.level().isClientSide && this.random.nextFloat() < 0.3F) {
			this.setItemSlot(EquipmentSlot.MAINHAND, this.worn(new ItemStack(Items.TRIDENT)));
		}
	}

	/** The Forgotten are named after their land ("Drowned Remnant"); a Crowned one keeps its custom name. */
	@Override
	protected Component getTypeName() {
		ForgottenVariant v = this.getVariant();
		return this.isForgotten() && v != ForgottenVariant.NONE
			? Component.translatable("entity.remnants_of_the_past.forgotten_remnant." + v.key()) : super.getTypeName();
	}

	public boolean hasCrust() {
		return this.entityData.get(DATA_CRUST);
	}

	/** Fire, explosions, an axe or a Fire Aspect blade crack the Frozen Remnant's crust; anything else glances off it. */
	private boolean breaksIce(DamageSource source, boolean melee) {
		if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
			|| (source.getDirectEntity() != null && source.getDirectEntity().isOnFire())) {
			return true;
		}
		if (melee && source.getEntity() instanceof LivingEntity living) {
			ItemStack weapon = living.getMainHandItem();
			return weapon.getItem() instanceof AxeItem || EnchantmentHelper.getItemEnchantmentLevel(
				this.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.FIRE_ASPECT), weapon) > 0;
		}
		return false;
	}

	private void shatterCrust() {
		this.entityData.set(DATA_CRUST, false);
		this.sound(SoundEvents.GLASS_BREAK, 1.2F, 0.7F);
		this.sound(SoundEvents.PLAYER_HURT_FREEZE, 1.0F, 0.8F);
		this.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState()),
			this.getX(), this.getY() + 1.1, this.getZ(), 40, 0.35, 0.6, 0.35, 0.1);
		this.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 1.1, this.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
	}

	public int getEmergeTicks() {
		return this.entityData.get(DATA_EMERGE);
	}

	public boolean isEmerging() {
		return this.getEmergeTicks() > 0;
	}

	/** 0 = still under the ground .. 1 = fully out (client animation). */
	public float emergeProgress(float partialTick) {
		int total = Math.max(1, this.entityData.get(DATA_EMERGE_TOTAL));
		return Mth.clamp(1.0F - (this.getEmergeTicks() - partialTick) / total, 0.0F, 1.0F);
	}

	/**
	 * Climbs out of the ground over {@code ticks}: a hand breaks the surface, then the head, then a lunge. Invulnerable
	 * and still meanwhile (like the Warden), so it can't be beaten before it is out.
	 */
	public void emerge(int ticks) {
		this.entityData.set(DATA_EMERGE_TOTAL, ticks);
		this.entityData.set(DATA_EMERGE, ticks);
		this.getNavigation().stop();
		if (!this.level().isClientSide) {
			this.sound(SoundEvents.ROOTED_DIRT_BREAK, 1.0F, 0.7F);
			this.groundBurst(10);
		}
	}

	private void tickEmerge() {
		int left = this.getEmergeTicks() - 1;
		this.entityData.set(DATA_EMERGE, left);
		this.getNavigation().stop();
		int total = this.entityData.get(DATA_EMERGE_TOTAL);
		if (left % 4 == 0) {
			this.groundBurst(4);
		}
		if (left == total / 3) {
			// the lunge out of the hole
			this.sound(SoundEvents.SOUL_ESCAPE.value(), 1.5F, 0.6F);
			this.sound(SoundEvents.ROOTED_DIRT_BREAK, 1.2F, 0.5F);
			this.groundBurst(16);
			this.serverLevel().sendParticles(ModParticles.REMNANT_SOUL, this.getX(), this.getY() + 0.8, this.getZ(), 12, 0.3, 0.5, 0.3, 0.03);
			this.serverLevel().sendParticles(ModParticles.SOUL_SMOKE, this.getX(), this.getY() + 0.4, this.getZ(), 4, 0.4, 0.1, 0.4, 0.01);
		}
	}

	/** Bits of whatever it is standing in fly up around it. */
	private void groundBurst(int count) {
		BlockState ground = this.level().getBlockState(this.blockPosition().below());
		if (ground.isAir()) {
			return;
		}
		this.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), this.getX(), this.getY() + 0.1, this.getZ(),
			count, 0.35, 0.05, 0.35, 0.12);
	}

	/** Ticks spent sinking into the ground (Burrow); 0 = above ground. Hidden and untouchable once fully under. */
	public int getSinkTicks() {
		return this.entityData.get(DATA_SINK);
	}

	public void setSinkTicks(int ticks) {
		this.entityData.set(DATA_SINK, ticks);
		this.setInvisible(ticks >= SINK_TICKS);
		if (ticks > 0 && ticks <= SINK_TICKS && ticks % 3 == 0) {
			this.groundBurst(6);
		}
	}

	public boolean isBurrowed() {
		return this.getSinkTicks() >= SINK_TICKS;
	}

	/** Whether it can climb out of / dig into what it stands on (not water, air or bedrock-hard stuff). */
	public boolean standsOnDiggable() {
		BlockPos below = this.blockPosition().below();
		BlockState ground = this.level().getBlockState(below);
		return ground.isFaceSturdy(this.level(), below, Direction.UP) && ground.getFluidState().isEmpty()
			&& ground.getDestroySpeed(this.level(), below) >= 0 && ground.getDestroySpeed(this.level(), below) < 5.0F;
	}

	public void setEpitaph(ItemStack stack) {
		this.epitaph = stack;
	}

	/** Drowned Remnants walk the river bed instead of bobbing to the surface. */
	@Override
	public double getFluidJumpThreshold() {
		return this.getVariant() == ForgottenVariant.DROWNED ? 64.0 : super.getFluidJumpThreshold();
	}

	// ---- combat helpers used by abilities ------------------------------------------------------------

	public ServerLevel serverLevel() {
		return (ServerLevel) this.level();
	}

	/** Players (survival/adventure) within {@code radius} of {@code center}. */
	public List<Player> playersNear(Vec3 center, double radius) {
		return this.level().getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(center, center).inflate(radius),
			p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.position().distanceTo(center) <= radius);
	}

	/** Damages a target with an ability; a little lifesteal while enraged. */
	public boolean abilityHit(LivingEntity target, float amount) {
		boolean hit = target.hurt(this.damageSources().mobAttack(this), this.againstTarget(target, amount * this.abilityScale()));
		if (hit && this.getPhase() >= 3 && !this.isIllusion()) {
			this.heal(amount * 0.1F);
		}
		return hit;
	}

	public void push(LivingEntity target, Vec3 velocity) {
		target.setDeltaMovement(velocity);
		target.hurtMarked = true;
		target.hasImpulse = true;
	}

	public void ring(Vec3 center, double radius, ParticleOptions particle, int points) {
		ServerLevel level = this.serverLevel();
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points;
			level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y + 0.1, center.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
		}
	}

	public void line(Vec3 from, Vec3 to, ParticleOptions particle, double step) {
		Vec3 d = to.subtract(from);
		int n = (int) Math.max(1, d.length() / step);
		for (int i = 0; i <= n; i++) {
			Vec3 p = from.add(d.scale((double) i / n));
			this.serverLevel().sendParticles(particle, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
	}

	public void sound(SoundEvent sound, float volume, float pitch) {
		this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
	}

	public void title(Component title, Component subtitle, double radius) {
		for (Player p : this.playersNear(this.position(), radius)) {
			if (p instanceof ServerPlayer sp) {
				sp.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
				sp.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
				sp.connection.send(new ClientboundSetTitleTextPacket(title));
			}
		}
	}

	// ---- melee & ranged ----------------------------------------------------------------------------

	/**
	 * A basic weapon hit (the brain calls it when a wound-up strike lands). Like vanilla's mob hit, but the weapon's
	 * enchantment bonus is shared the same way as its damage; Fire Aspect and knockback still apply.
	 */
	@Override
	public boolean doHurtTarget(Entity target) {
		RemnantsConfig cfg = RemnantsConfig.get();
		DamageSource source = this.damageSources().mobAttack(this);
		float damage = this.weaponDamage();
		if (this.level() instanceof ServerLevel level) {
			float enchanted = EnchantmentHelper.modifyDamage(level, this.getWeaponItem(), target, source, damage);
			damage += (enchanted - damage) * (float) cfg.weaponDamageShare;
		}
		if (this.isIllusion()) {
			damage *= 0.25F;
		}
		if (target instanceof LivingEntity living) {
			damage = this.againstTarget(living, damage);
		}
		boolean hit = target.hurt(source, damage);
		if (hit) {
			float knockback = this.getKnockback(target, source);
			if (knockback > 0.0F && target instanceof LivingEntity living) {
				living.knockback(knockback * 0.5F, Mth.sin(this.getYRot() * Mth.DEG_TO_RAD), -Mth.cos(this.getYRot() * Mth.DEG_TO_RAD));
			}
			if (this.level() instanceof ServerLevel level && !this.isIllusion()) {
				EnchantmentHelper.doPostAttackEffects(level, target, source);
			}
			this.setLastHurtMob(target);
			if (this.getPhase() >= 3 && !this.isIllusion()) {
				this.heal(damage * 0.1F);
			}
		}
		return hit;
	}

	@Override
	public void performRangedAttack(LivingEntity target, float power) {
		this.shootArrow(target, power, 0.0F, 1.0F, false);
	}

	/** Fires one phantom arrow at {@code target}, {@code yawOffset} degrees off-centre. */
	/** Turns body, head and view to the target at once (ranged attacks are never fired facing away). */
	/**
	 * Turns toward the target by at most {@code step} degrees a tick. Strafing alone keeps the old yaw (vanilla strafes
	 * relative to it), so a Remnant circling its target drifted side-on or back-on to it, head clamped 75 degrees away.
	 */
	public void turnToward(LivingEntity target, float step) {
		float yaw = (float) (Mth.atan2(target.getZ() - this.getZ(), target.getX() - this.getX()) * Mth.RAD_TO_DEG) - 90.0F;
		this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, step));
		this.yBodyRot = this.getYRot();
	}

	public void faceTarget(LivingEntity target) {
		double dx = target.getX() - this.getX();
		double dz = target.getZ() - this.getZ();
		double dy = target.getEyeY() - this.getEyeY();
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
		this.setXRot((float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
	}

	public PhantomArrowEntity shootArrow(LivingEntity target, float power, float yawOffset, float damageMul, boolean explosive) {
		this.faceTarget(target);
		ItemStack weapon = this.getMainHandItem();
		ItemStack firedFrom = weapon.getItem() instanceof ProjectileWeaponItem ? weapon.copyWithCount(1) : new ItemStack(Items.BOW);
		PhantomArrowEntity arrow = new PhantomArrowEntity(this.level(), this, new ItemStack(Items.ARROW), firedFrom);
		arrow.setBaseDamageFromMob(power);
		RemnantsConfig cfg = RemnantsConfig.get();
		arrow.setBaseDamage(arrow.getBaseDamage() * cfg.rangedDamageMultiplier * this.damageScale() * damageMul * this.abilityScale() * 0.85);
		arrow.setExplosive(explosive);

		double dx = target.getX() - this.getX();
		double dy = target.getY(1.0 / 3.0) - arrow.getY();
		double dz = target.getZ() - this.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		Vec3 dir = new Vec3(dx, 0, dz).yRot(yawOffset * Mth.DEG_TO_RAD);
		boolean crossbow = weapon.getItem() instanceof CrossbowItem;
		float velocity = crossbow ? 2.4F : 1.8F;
		float inaccuracy = Math.max(0.5F, 6 - this.level().getDifficulty().getId() * 2 - this.getPhase());
		arrow.shoot(dir.x, dy + flat * (crossbow ? 0.1 : 0.17), dir.z, velocity, inaccuracy);
		this.sound(crossbow ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.SKELETON_SHOOT, 1.0F, 0.6F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
		this.level().addFreshEntity(arrow);
		return arrow;
	}

	// ---- damage handling ---------------------------------------------------------------------------

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (this.level().isClientSide) {
			return false;
		}
		boolean unstoppable = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		Entity attacker = source.getEntity();
		if (this.isIllusion() && attacker instanceof Player) {
			// One hit pops an echo.
			this.serverLevel().sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1, this.getZ(), 25, 0.3, 0.6, 0.3, 0.04);
			this.sound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 1.6F);
			this.discard();
			return true;
		}
		if (this.transitionTicks > 0 && !unstoppable) {
			return false;
		}
		if ((this.isEmerging() || this.getSinkTicks() > 0) && !unstoppable) {
			return false; // still (or again) under the ground
		}
		if (this.dormant && attacker instanceof LivingEntity living) {
			this.wake(living);
		}
		boolean melee = attacker instanceof LivingEntity && source.getDirectEntity() == attacker;
		if (this.stanceTicks > 0 && melee && !unstoppable) {
			this.brain.counterAttack((LivingEntity) attacker);
			return false;
		}
		if (this.isShieldUp() && !unstoppable && !source.is(DamageTypeTags.BYPASSES_SHIELD) && this.isFrontal(source)) {
			if (melee && ((LivingEntity) attacker).getMainHandItem().getItem() instanceof AxeItem) {
				this.shieldDisabledTicks = 100;
				this.stun(20);
				this.sound(SoundEvents.SHIELD_BREAK, 1.0F, 0.8F);
			} else {
				this.sound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
				if (melee) {
					((LivingEntity) attacker).knockback(0.5, this.getX() - attacker.getX(), this.getZ() - attacker.getZ());
				}
				return false;
			}
		}
		if (this.hasCrust() && !unstoppable) {
			if (this.breaksIce(source, melee)) {
				this.shatterCrust();
			} else {
				amount *= 0.7F;
				this.sound(SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 1.6F);
			}
		}
		if (this.stunTicks > 0) {
			amount *= 1.5F;
		}
		if (attacker instanceof LivingEntity living) {
			int spectral = SpectralForge.level(living.getMainHandItem());
			amount *= 1.0F + 0.15F * spectral;
		}
		if (attacker instanceof ServerPlayer player && this.isOwner(player) && SoulResonance.has(player, SoulResonance.Perk.SOUL_BOND)) {
			amount *= 1.2F;
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt && attacker instanceof LivingEntity living && this.brain != null) {
			this.brain.onHurtBy(living, source, melee);
			this.outOfCombatTicks = 0;
			this.damagePoise(this.poiseDamage(living, amount, melee), living);
		}
		return hurt;
	}

	/**
	 * How much a hit wears down poise: heavier hits more, falling (critical) blows and axes extra, arrows a little.
	 * About six solid sword hits in a row break it; it recovers if you back off.
	 */
	private float poiseDamage(LivingEntity attacker, float amount, boolean melee) {
		if (!melee) {
			return 6.0F + amount * 0.5F;
		}
		float p = 6.0F + amount * 1.5F;
		if (attacker.fallDistance > 0.0F && !attacker.onGround() && !attacker.isInWater()) {
			p *= 1.4F;
		}
		if (attacker.getMainHandItem().getItem() instanceof AxeItem) {
			p *= 1.3F;
		}
		return p;
	}

	/**
	 * Ghost gear is hollow: it shows your armor but keeps only a share of its protection, no toughness and none of
	 * its Protection enchantments, and it never wears out. A fresh iron sword can cut through a netherite ghost.
	 */
	@Override
	protected float getDamageAfterArmorAbsorb(DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			return amount;
		}
		float armor = (float) (this.getArmorValue() * RemnantsConfig.get().ghostArmorFactor);
		return CombatRules.getDamageAfterAbsorb(this, amount, source, armor, 0.0F);
	}

	@Override
	protected float getDamageAfterMagicAbsorb(DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypeTags.BYPASSES_RESISTANCE)
			|| !this.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
			return amount;
		}
		int level = this.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1;
		return Math.max(0.0F, amount * (25 - level * 5) / 25.0F);
	}

	/** Hyper armor: while winding up a strike or performing a move it barely budges, so trading hits can't stun-lock it. */
	@Override
	public void knockback(double strength, double x, double z) {
		if (this.brain != null && this.brain.isCommitted() && !this.isStunned()) {
			strength *= 0.15;
		}
		super.knockback(strength, x, z);
	}

	private boolean isFrontal(DamageSource source) {
		Vec3 from = source.getSourcePosition();
		if (from == null) {
			return false;
		}
		Vec3 to = from.subtract(this.position()).multiply(1, 0, 1).normalize();
		Vec3 facing = Vec3.directionFromRotation(0, this.yBodyRot);
		return to.dot(facing) > 0.15;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return source.is(DamageTypes.FELL_OUT_OF_WORLD)
			|| source.is(DamageTypes.IN_WALL)
			|| source.is(DamageTypes.CACTUS)
			|| source.is(DamageTypes.LIGHTNING_BOLT)
			|| source.is(DamageTypeTags.IS_DROWNING)
			|| source.is(DamageTypeTags.IS_FALL)
			|| (source.getEntity() instanceof RemnantEntity)
			|| (source.getEntity() instanceof SoulKingEntity)
			|| super.isInvulnerableTo(source);
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected boolean shouldDespawnInPeaceful() {
		return this.isForgotten();
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return this.isForgotten();
	}

	@Override
	public boolean requiresCustomPersistence() {
		return !this.isIllusion() && !this.isForgotten();
	}

	@Override
	public boolean shouldBeSaved() {
		return !this.isIllusion() && super.shouldBeSaved();
	}

	@Override
	protected void onBelowWorld() {
		// Never lose items to the void: float back up to where we rose (or to the surface above us).
		BlockPos target = this.anchor != null && this.anchor.getY() > this.level().getMinBuildHeight() ? this.anchor
			: this.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.blockPosition());
		if (target.getY() <= this.level().getMinBuildHeight() && this.level() instanceof ServerLevel server) {
			target = server.getSharedSpawnPos();
		}
		this.teleportTo(target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5);
		this.setDeltaMovement(Vec3.ZERO);
		this.anchor = target;
		this.restrictTo(target, LINGER_RADIUS);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SOUL_ESCAPE.value();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.VEX_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ALLAY_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.6F + this.random.nextFloat() * 0.2F;
	}

	// ---- life cycle --------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide) {
			if (RemnantsConfig.get().particleTrail) {
				this.spawnTrail();
			}
			return;
		}
		this.lifeTicks++;
		if (this.strippedTicks > 0 && --this.strippedTicks == 0) {
			this.restoreStripped();
		}
		if (this.isEmerging()) {
			this.tickEmerge();
		}
		if (this.hasCrust() && (this.isOnFire() || this.isInLava())) {
			this.shatterCrust();
		}
		if (this.getVariant() == ForgottenVariant.DROWNED && this.tickCount % 10 == 0) {
			// at home in the water: half again as fast when wading or swimming
			AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				if (this.isInWater()) {
					speed.addOrReplacePermanentModifier(new AttributeModifier(TIDE_SPEED, 0.6, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
				} else {
					speed.removeModifier(TIDE_SPEED);
				}
			}
		}
		if (this.isIllusion()) {
			if (this.lifeTicks > ILLUSION_LIFE || this.master().map(m -> !m.isAlive()).orElse(true)) {
				this.serverLevel().sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1, this.getZ(), 15, 0.3, 0.6, 0.3, 0.02);
				this.discard();
			}
			return;
		}
		long despawn = RemnantsConfig.get().despawnTicks;
		if (despawn > 0 && this.lifeTicks >= despawn) {
			this.fadeAway();
			return;
		}
		if (this.tickCount % 40 == 0) {
			dev.remnants.CarrionCrows.tick(this);
		}
		if (this.dormant && this.tickCount % 10 == 0) {
			int r = RemnantsConfig.get().wakeRadius;
			Player near = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), r,
				p -> !p.isSpectator() && !((Player) p).isCreative() && this.hasLineOfSight(p));
			if (near != null) {
				this.wake(near);
				this.setTarget(near);
			} else if (this.random.nextInt(4) == 0) {
				this.serverLevel().sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 0.4, this.getZ(), 1, 0.3, 0.1, 0.3, 0.01);
			}
		}
		if (this.tickCount % 40 == 0 && this.isAlive()) {
			this.getOwnerUuid().ifPresent(owner -> RemnantTracker.get(this.serverLevel().getServer()).update(owner, this.getUUID(), this.serverLevel(), this.blockPosition(), this.getTier()));
		}
	}

	@Override
	protected void customServerAiStep() {
		this.entityData.set(DATA_LOOK, this.getTarget() == null ? -1 : this.getTarget().getId());
		super.customServerAiStep();
		if (this.stanceTicks > 0 && --this.stanceTicks == 0) {
			this.setGlowingTag(false);
		}
		if (this.stunTicks > 0) {
			this.stunTicks--;
			this.getNavigation().stop();
			if (this.tickCount % 4 == 0) {
				this.serverLevel().sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 2.1, this.getZ(), 3, 0.3, 0.1, 0.3, 0.05);
			}
		}
		if (this.shieldDisabledTicks > 0) {
			this.shieldDisabledTicks--;
		}
		if (this.recoverTicks > 0) {
			this.getNavigation().stop();
			if (--this.recoverTicks == 0 && this.getAnim() == ANIM_RECOVER) {
				this.setAnim(ANIM_NONE);
			} else if (this.tickCount % 5 == 0) {
				this.serverLevel().sendParticles(ParticleTypes.SOUL, this.getX(), this.getEyeY() + 0.2, this.getZ(), 1, 0.15, 0.05, 0.15, 0.01);
			}
		}
		if (this.awakenTicks > 0) {
			this.getNavigation().stop();
			if (--this.awakenTicks == 0 && this.getAnim() == ANIM_ROAR) {
				this.setAnim(ANIM_NONE);
			}
		}
		if (this.isIllusion() || this.isForgotten()) {
			return;
		}
		this.updatePhase();
		if (this.transitionTicks > 0) {
			this.tickTransition();
		}
		this.tickPoise();

		// Out of combat it heals, but never past the phase it reached: what you broke stays broken, so you can
		// back off, eat and come back without starting over, but can't cheese it down across many visits either.
		if (this.getTarget() == null) {
			float cap = this.getMaxHealth() * (this.getPhase() >= 3 ? 0.25F : this.getPhase() == 2 ? 0.6F : 1.0F);
			if (++this.outOfCombatTicks > 300 && this.tickCount % 20 == 0 && this.getHealth() < cap) {
				this.setHealth(Math.min(cap, this.getHealth() + this.getMaxHealth() * 0.03F));
			}
		} else {
			this.outOfCombatTicks = 0;
		}
		this.updateBossBar();
	}

	private void tickPoise() {
		if (this.poiseImmuneTicks > 0) {
			this.poiseImmuneTicks--;
			if (this.stunTicks <= 0) {
				// staggered and back up: the bar refills while it is immune
				this.entityData.set(DATA_POISE, Math.min(MAX_POISE, this.getPoise() + MAX_POISE / 40.0F));
			}
			return;
		}
		if (++this.poiseIdleTicks > 50 && this.getPoise() < MAX_POISE) {
			this.entityData.set(DATA_POISE, Math.min(MAX_POISE, this.getPoise() + 0.6F));
		}
	}

	private void updateBossBar() {
		RemnantsConfig cfg = RemnantsConfig.get();
		if (!cfg.bossBar || this.dormant) {
			this.bossEvent.removeAllPlayers();
			this.poiseEvent.removeAllPlayers();
			return;
		}
		this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
		this.bossEvent.setVisible(true);
		boolean showPoise = cfg.poise && this.getTarget() != null;
		this.poiseEvent.setProgress(Mth.clamp(this.getPoise() / MAX_POISE, 0.0F, 1.0F));
		this.poiseEvent.setColor(this.poiseImmuneTicks > 0 ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.WHITE);
		this.poiseEvent.setVisible(showPoise);
		if (this.tickCount % 10 == 0) {
			for (ServerPlayer p : this.serverLevel().players()) {
				boolean near = p.distanceToSqr(this) < 48 * 48 && !p.isSpectator();
				if (near && !this.bossEvent.getPlayers().contains(p)) {
					this.bossEvent.addPlayer(p);
					this.poiseEvent.addPlayer(p);
				} else if (!near && this.bossEvent.getPlayers().contains(p)) {
					this.bossEvent.removePlayer(p);
					this.poiseEvent.removePlayer(p);
				}
			}
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bossEvent.removePlayer(player);
		this.poiseEvent.removePlayer(player);
	}

	@Override
	public void remove(RemovalReason reason) {
		this.bossEvent.removeAllPlayers();
		this.poiseEvent.removeAllPlayers();
		super.remove(reason);
	}

	// ---- phases ------------------------------------------------------------------------------------

	/** Recomputes the phase from health; entering a new phase starts a roar transition. Public for game tests. */
	public void updatePhase() {
		float frac = this.getHealth() / this.getMaxHealth();
		int phase = frac <= 0.25F ? 3 : frac <= 0.6F ? 2 : 1;
		// weak echoes have fewer phases: Faint none, Echo only Wrath
		phase = Math.min(phase, this.echoRank <= 1 ? 1 : this.echoRank == 2 ? 2 : 3);
		if (phase > this.getPhase()) {
			this.entityData.set(DATA_PHASE, phase);
			this.transitionTicks = 30;
			this.setAnim(ANIM_ROAR);
			this.setStance(0);
			this.setShieldUp(false);
			this.stunTicks = 0;
			this.recoverTicks = 0;
			if (this.brain != null) {
				this.brain.interrupt();
			}
			this.getNavigation().stop();
			this.sound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.6F);
			this.sound(SoundEvents.WITHER_SPAWN, 0.6F, 1.6F);
			this.bossEvent.setColor(phase == 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED);
			this.bossEvent.setDarkenScreen(phase == 3);
			this.title(Component.translatable("message.remnants.phase" + phase).withStyle(phase == 2 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.RED),
				Component.translatable("message.remnants.phase" + phase + ".sub").withStyle(ChatFormatting.GRAY), 40);
			if (phase == 3) {
				AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
				if (speed != null) {
					speed.addOrReplacePermanentModifier(new AttributeModifier(ENRAGE_SPEED, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
				}
			}
		}
	}

	private void tickTransition() {
		this.transitionTicks--;
		this.getNavigation().stop();
		ServerLevel level = this.serverLevel();
		level.sendParticles(ModParticles.REMNANT_SOUL, this.getX(), this.getY() + 1, this.getZ(), 6, 0.5, 0.9, 0.5, 0.05);
		if (this.transitionTicks == 20) {
			// shockwave: everyone close is thrown back
			for (Player p : this.playersNear(this.position(), 7)) {
				Vec3 away = p.position().subtract(this.position()).multiply(1, 0, 1).normalize();
				this.push(p, away.scale(1.4).add(0, 0.5, 0));
				this.abilityHit(p, 3.0F);
			}
			this.ring(this.position(), 3.5, ParticleTypes.SOUL_FIRE_FLAME, 40);
			this.ring(this.position(), 6.0, ParticleTypes.SOUL_FIRE_FLAME, 60);
			level.sendParticles(ParticleTypes.SONIC_BOOM, this.getX(), this.getY() + 1, this.getZ(), 1, 0, 0, 0, 0);
			level.sendParticles(ModParticles.SHOCK_RING, this.getX(), this.getY() + 0.2, this.getZ(), 1, 0, 0, 0, 0);
			level.sendParticles(ModParticles.SOUL_EXPLOSION, this.getX(), this.getY() + 1.2, this.getZ(), 1, 0, 0, 0, 0);
			for (int i = 0; i < 12; i++) {
				double a = Math.PI * 2 * i / 12;
				level.sendParticles(ModParticles.RUNE, this.getX() + Math.cos(a) * 3.0, this.getY() + 0.3, this.getZ() + Math.sin(a) * 3.0,
					1, 0, 0, 0, 0);
			}
		}
		if (this.transitionTicks == 0) {
			this.setAnim(ANIM_NONE);
			if (RemnantsConfig.get().illusions && this.echoRank >= 3) {
				this.spawnIllusions(this.echoRank >= 4 ? 2 : 1);
			}
		}
	}

	/** Mirror Echoes: one-hit decoys that look exactly like the Remnant; the Remnant blinks into the circle too. */
	public void spawnIllusions(int count) {
		LivingEntity target = this.getTarget();
		Vec3 center = target != null ? target.position() : this.position();
		ServerLevel level = this.serverLevel();
		int slots = count + 1;
		int me = this.random.nextInt(slots);
		double offset = this.random.nextDouble() * Math.PI * 2;
		for (int i = 0; i < slots; i++) {
			double a = offset + Math.PI * 2 * i / slots;
			Vec3 pos = center.add(Math.cos(a) * 6, 0, Math.sin(a) * 6);
			if (i == me) {
				Vec3 from = this.position();
				if (this.randomTeleport(pos.x, pos.y + 1, pos.z, false)) {
					level.sendParticles(ParticleTypes.SOUL, from.x, from.y + 1, from.z, 20, 0.3, 0.6, 0.3, 0.05);
				}
				continue;
			}
			RemnantEntity echo = ModEntities.REMNANT.create(level);
			if (echo == null) {
				continue;
			}
			echo.entityData.set(DATA_ILLUSION, true);
			echo.entityData.set(DATA_OWNER, this.getOwnerUuid());
			echo.entityData.set(DATA_PHASE, this.getPhase());
			echo.entityData.set(DATA_EMPOWERED, this.isEmpowered());
			echo.masterId = this.getUUID();
			echo.ownerName = this.ownerName;
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (slot.getType() != EquipmentSlot.Type.ANIMAL_ARMOR) {
					echo.setItemSlot(slot, this.getItemBySlot(slot).copy());
					echo.setDropChance(slot, 0.0F);
				}
			}
			echo.setCustomName(this.getCustomName());
			echo.setCustomNameVisible(true);
			AttributeInstance hp = echo.getAttribute(Attributes.MAX_HEALTH);
			if (hp != null) {
				hp.setBaseValue(1.0);
			}
			echo.setHealth(1.0F);
			echo.moveTo(pos.x, pos.y, pos.z, this.random.nextFloat() * 360, 0);
			boolean placed = false;
			for (double r : new double[]{6, 4, 2}) {
				Vec3 tryPos = center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
				if (echo.randomTeleport(tryPos.x, tryPos.y + 1, tryPos.z, false)) {
					placed = true;
					break;
				}
			}
			if (!placed) {
				// no room around the target: rise right next to the Remnant instead of silently vanishing
				echo.moveTo(this.getX() + Math.cos(a), this.getY(), this.getZ() + Math.sin(a), this.random.nextFloat() * 360, 0);
			}
			echo.setTarget(target);
			level.addFreshEntity(echo);
			level.sendParticles(ParticleTypes.SOUL, echo.getX(), echo.getY() + 1, echo.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
		}
		this.sound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 2.0F, 0.7F);
	}

	private Optional<LivingEntity> master() {
		if (this.masterId == null) {
			return Optional.empty();
		}
		Entity e = this.serverLevel().getEntity(this.masterId);
		return e instanceof LivingEntity l ? Optional.of(l) : Optional.empty();
	}

	/**
	 * A one-hit echo of {@code template} (its look and gear) fighting for {@code master}. Used by the Soul King's
	 * Call of Echoes to send copies of a player at that player.
	 */
	public static RemnantEntity spawnEcho(ServerLevel level, LivingEntity master, LivingEntity template, Vec3 pos, @Nullable LivingEntity target) {
		RemnantEntity echo = ModEntities.REMNANT.create(level);
		if (echo == null) {
			return null;
		}
		echo.entityData.set(DATA_ILLUSION, true);
		echo.entityData.set(DATA_PHASE, 2);
		if (template instanceof Player p) {
			echo.entityData.set(DATA_OWNER, Optional.of(p.getUUID()));
			echo.ownerName = p.getGameProfile().getName();
		}
		echo.masterId = master.getUUID();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() != EquipmentSlot.Type.ANIMAL_ARMOR) {
				echo.setItemSlot(slot, template.getItemBySlot(slot).copy());
				echo.setDropChance(slot, 0.0F);
			}
		}
		echo.setCustomName(Component.literal("[Echo] " + echo.ownerName).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
		echo.setCustomNameVisible(true);
		AttributeInstance hp = echo.getAttribute(Attributes.MAX_HEALTH);
		if (hp != null) {
			hp.setBaseValue(1.0);
		}
		echo.setHealth(1.0F);
		echo.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360, 0);
		echo.setTarget(target);
		level.addFreshEntity(echo);
		level.sendParticles(ParticleTypes.SOUL, pos.x, pos.y + 1, pos.z, 20, 0.3, 0.6, 0.3, 0.05);
		return echo;
	}

	public long illusionCount() {
		return this.level().getEntitiesOfClass(RemnantEntity.class, this.getBoundingBox().inflate(32),
			r -> r.isIllusion() && this.getUUID().equals(r.masterId)).size();
	}

	private void spawnTrail() {
		boolean moving = this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;
		if (moving || this.random.nextInt(4) == 0) {
			int n = moving ? 2 : 1;
			for (int i = 0; i < n; i++) {
				this.level().addParticle(ModParticles.REMNANT_SOUL,
					this.getRandomX(0.6), this.getY() + this.random.nextDouble() * 1.6, this.getRandomZ(0.6),
					-this.getDeltaMovement().x * 0.5, 0.0, -this.getDeltaMovement().z * 0.5);
			}
		}
		if (this.random.nextInt(10) == 0) {
			this.level().addParticle(ParticleTypes.SOUL, this.getRandomX(0.4), this.getY() + 0.1, this.getRandomZ(0.4), 0.0, 0.02, 0.0);
		}
		if (this.isEmpowered() && this.random.nextInt(3) == 0) {
			Vec3 hand = this.position().add(Vec3.directionFromRotation(0, this.yBodyRot + 40).scale(0.45)).add(0, 1.0, 0);
			this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, hand.x, hand.y, hand.z, 0, 0.02, 0);
		}
		if (this.getPhase() >= 3 && this.random.nextInt(2) == 0) {
			this.level().addParticle(ParticleTypes.SCULK_SOUL, this.getRandomX(0.8), this.getY() + this.random.nextDouble() * 2, this.getRandomZ(0.8), 0, 0.03, 0);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		this.layToRest(level, source, true);
	}

	/**
	 * With the doMobLoot gamerule off vanilla skips dropCustomDeathLoot, but the owner's inventory is not loot: it must
	 * come back (and the tracker must hear of it) either way. Only the Soul Fragments / crown / trophies are left out then.
	 */
	@Override
	protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
		super.dropAllDeathLoot(level, source);
		this.layToRest(level, source, false);
	}

	private void layToRest(ServerLevel level, DamageSource source, boolean loot) {
		if (this.isIllusion() || this.laidToRest) {
			return;
		}
		this.laidToRest = true;
		ServerPlayer reclaimer = null;
		if (source.getEntity() instanceof ServerPlayer player && this.isOwner(player) && player.isAlive()) {
			reclaimer = player;
		}
		this.releaseItems(level, reclaimer);
		if (loot) {
			this.dropSoulLoot(level);
		}
		if (source.getEntity() instanceof ServerPlayer killer) {
			SoulResonance.onRemnantLaidToRest(killer, this);
		}
		this.getOwnerUuid().ifPresent(owner -> RemnantTracker.get(level.getServer()).reclaimed(owner, this.getUUID()));
		level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.0, this.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
		level.sendParticles(ModParticles.REMNANT_SOUL, this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.5, 0.8, 0.5, 0.02);
		level.sendParticles(ModParticles.SOUL_EXPLOSION, this.getX(), this.getY() + 1.0, this.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ModParticles.SKULL_WISP, this.getX(), this.getY() + 2.2, this.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ModParticles.RUNE, this.getX(), this.getY() + 0.8, this.getZ(), 8, 0.6, 0.6, 0.6, 0.02);
		level.sendParticles(ModParticles.SOUL_FLAME, this.getX(), this.getY() + 0.2, this.getZ(), 10, 0.5, 0.1, 0.5, 0.01);
		level.playSound(null, this.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 2.0F, 0.6F);
		if (this.isForgotten()) {
			return;
		}
		if (this.isEmpowered()) {
			this.title(Component.translatable("message.remnants.victory.empowered").withStyle(ChatFormatting.LIGHT_PURPLE),
				Component.translatable("message.remnants.victory.empowered.sub").withStyle(ChatFormatting.GRAY), 32);
		} else {
			this.title(Component.translatable("message.remnants.victory").withStyle(ChatFormatting.AQUA), Component.empty(), 32);
		}
	}

	/** Soul Fragments (chance; more for higher tiers), and an Empowered Remnant always leaves its crown. */
	private void dropSoulLoot(ServerLevel level) {
		RemnantsConfig cfg = RemnantsConfig.get();
		int fragments = 0;
		if (this.isForgotten()) {
			fragments = cfg.forgottenFragmentsMin + this.random.nextInt(Math.max(1, cfg.forgottenFragmentsMax - cfg.forgottenFragmentsMin + 1));
			if (this.isEmpowered()) {
				fragments += 2;
				this.dropProtected(level, new ItemStack(ModItems.REMNANT_CROWN));
			}
		} else if (this.isEmpowered()) {
			fragments = 3 + this.random.nextInt(3);
			this.dropProtected(level, new ItemStack(ModItems.REMNANT_CROWN));
		} else if (this.random.nextDouble() < cfg.soulFragmentChance) {
			fragments = 1 + this.random.nextInt(2) + this.getTier() / 2;
		}
		if (fragments > 0) {
			this.dropProtected(level, new ItemStack(ModItems.SOUL_FRAGMENT, fragments));
		}
		ForgottenVariant variant = this.getVariant();
		if (this.isForgotten() && variant != ForgottenVariant.NONE) {
			this.spawnAtLocation(new ItemStack(variant.trophy(), 1 + (this.random.nextFloat() < 0.3F ? 1 : 0)));
		}
	}

	private void fadeAway() {
		if (this.level() instanceof ServerLevel level) {
			if (RemnantsConfig.get().dropItemsOnDespawn) {
				this.releaseItems(level, null);
			}
			this.storedXp = 0;
			this.getOwnerUuid().ifPresent(owner -> RemnantTracker.get(level.getServer()).forget(owner, this.getUUID()));
			level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.0, this.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
		}
		this.discard();
	}

	/**
	 * Gives the items back. The owner gets them straight into their inventory (armor goes back on if the slot is
	 * free); otherwise they drop as protected items that never despawn and ignore fire, lava and explosions.
	 */
	private void releaseItems(ServerLevel level, @Nullable ServerPlayer owner) {
		if (this.itemsReleased) {
			return;
		}
		this.itemsReleased = true;
		int returned = 0;
		int stacks = 0;
		for (ItemStack stack : this.storedItems) {
			if (stack.isEmpty()) {
				continue;
			}
			stacks++;
			returned += stack.getCount();
			if (owner != null) {
				if (stack.getItem() instanceof ArmorItem armor && owner.getItemBySlot(armor.getEquipmentSlot()).isEmpty()) {
					owner.setItemSlot(armor.getEquipmentSlot(), stack);
				} else if (!owner.getInventory().add(stack) || !stack.isEmpty()) {
					this.dropProtected(level, stack);
				}
			} else {
				this.dropProtected(level, stack);
			}
		}
		this.storedItems.clear();
		if (!this.epitaph.isEmpty()) {
			if (owner == null || !owner.getInventory().add(this.epitaph)) {
				this.dropProtected(level, this.epitaph);
			}
			this.epitaph = ItemStack.EMPTY;
		}
		int xp = this.storedXp;
		this.storedXp = 0;
		if (xp > 0) {
			if (owner != null) {
				int before = owner.experienceLevel;
				dev.remnants.DeathHandler.giveExactExperience(owner, xp);
				owner.sendSystemMessage(Component.translatable("message.remnants.reclaimed_xp", owner.experienceLevel - before)
					.withStyle(ChatFormatting.GREEN));
			} else {
				ExperienceOrb.award(level, this.position().add(0, 0.5, 0), xp);
			}
		}
		RemnantsMod.LOGGER.info("Remnant of {} released {} stacks ({} items) and {} xp {}", this.ownerName, stacks, returned, xp,
			owner != null ? "into the owner's inventory" : "as protected drops");
		if (owner != null) {
			owner.displayClientMessage(Component.translatable("message.remnants.reclaimed", stacks, returned).withStyle(ChatFormatting.AQUA), true);
			level.playSound(null, owner.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.4F);
		}
	}

	private void dropProtected(ServerLevel level, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, this.getX(), this.getY() + 0.5, this.getZ(), stack.copy(),
			(this.random.nextDouble() - 0.5) * 0.2, 0.25, (this.random.nextDouble() - 0.5) * 0.2);
		item.setInvulnerable(true);
		item.setUnlimitedLifetime();
		item.setPickUpDelay(10);
		level.addFreshEntity(item);
		stack.setCount(0);
	}

	@Override
	@Nullable
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
		if (this.isForgotten()) {
			RemnantsConfig cfg = RemnantsConfig.get();
			this.setVariant(cfg.forgottenVariants ? ForgottenVariant.forBiome(level.getBiome(this.blockPosition()), this.blockPosition())
				: ForgottenVariant.NONE);
			this.equipForgotten(difficulty);
			this.applyVariantKit();
			boolean rises = spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.EVENT || spawnType == MobSpawnType.REINFORCEMENT
				|| spawnType == MobSpawnType.SPAWNER;
			if (cfg.emergeAnimation && rises && this.standsOnDiggable()) {
				this.emerge(EMERGE_TICKS);
			}
			return result;
		}
		// Spawn-egg / command Remnants have no owner: give them a name and a home so they behave sensibly.
		if (this.getOwnerUuid().isEmpty() && !this.isIllusion()) {
			this.anchor = this.blockPosition();
			this.restrictTo(this.anchor, LINGER_RADIUS);
			this.applyTierStats();
			this.setCustomName(Component.translatable("entity.remnants_of_the_past.remnant").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
		}
		return result;
	}

	// ---- Forgotten Remnants ------------------------------------------------------------------------

	private static final Item[][] FORGOTTEN_ARMOR = {
		{Items.CHAINMAIL_HELMET, Items.IRON_HELMET, Items.GOLDEN_HELMET, Items.LEATHER_HELMET},
		{Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.LEATHER_CHESTPLATE},
		{Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS, Items.GOLDEN_LEGGINGS, Items.LEATHER_LEGGINGS},
		{Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS, Items.GOLDEN_BOOTS, Items.LEATHER_BOOTS},
	};
	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final Item[] FORGOTTEN_WEAPONS = {Items.IRON_SWORD, Items.IRON_SWORD, Items.STONE_SWORD, Items.IRON_AXE,
		Items.GOLDEN_SWORD, Items.BOW, Items.BOW, Items.CROSSBOW};

	/** Worn-out gear of some adventurer from long ago: mostly chainmail and iron, a weapon, all of it battered. A few
	 * are Crowned (stronger; they keep a Remnant Crown). Gear drops like a zombie's: rarely, and damaged. */
	private void equipForgotten(DifficultyInstance difficulty) {
		RemnantsConfig cfg = RemnantsConfig.get();
		float armorChance = 0.35F + 0.25F * difficulty.getSpecialMultiplier();
		int set = this.random.nextFloat() < 0.45F ? 0 : this.random.nextFloat() < 0.5F ? 1 : this.random.nextFloat() < 0.6F ? 2 : 3;
		for (int i = 0; i < ARMOR_SLOTS.length; i++) {
			if (this.random.nextFloat() < armorChance) {
				int pick = this.random.nextFloat() < 0.75F ? set : this.random.nextInt(4);
				this.setItemSlot(ARMOR_SLOTS[i], this.worn(new ItemStack(FORGOTTEN_ARMOR[i][pick])));
			}
		}
		this.setItemSlot(EquipmentSlot.MAINHAND, this.worn(new ItemStack(FORGOTTEN_WEAPONS[this.random.nextInt(FORGOTTEN_WEAPONS.length)])));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() != EquipmentSlot.Type.ANIMAL_ARMOR) {
				this.setDropChance(slot, 0.06F);
			}
		}
		if (this.random.nextDouble() < cfg.forgottenCrownedChance) {
			this.entityData.set(DATA_EMPOWERED, true);
			this.setCustomName(Component.translatable("entity.remnants_of_the_past.forgotten_remnant.crowned")
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
		}
		this.gearScore = gearScore(EquipmentSnapshot.of(this)) * 0.8F;
		this.echoRank = Math.min(3, rankFor(this.gearScore) + (this.isEmpowered() ? 1 : 0));
		AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(cfg.forgottenHealth * (this.isEmpowered() ? 2.0 : 1.0));
		}
		this.setHealth(this.getMaxHealth());
		this.xpReward = this.isEmpowered() ? 60 : 15;
	}

	/** Makes this Forgotten a Crowned one (the Night of Memory's second wave always brings one). */
	public void crown() {
		if (this.isEmpowered()) {
			return;
		}
		this.entityData.set(DATA_EMPOWERED, true);
		this.setCustomName(Component.translatable("entity.remnants_of_the_past.forgotten_remnant.crowned").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
		this.echoRank = Math.min(3, this.echoRank + 1);
		AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(RemnantsConfig.get().forgottenHealth * 2.0);
		}
		this.setHealth(this.getMaxHealth());
		this.xpReward = 60;
	}

	/**
	 * A Soul Tether tore a piece of armour off (1.4): one worn piece (a random one) is gone for {@code ticks}, then floats
	 * back. The worn gear is a hollow copy (or the Forgotten's own battered kit) - nothing drops. Returns the slot, or null.
	 */
	@Nullable
	public EquipmentSlot stripArmor(int ticks) {
		if (this.strippedSlot != null) {
			return null;
		}
		List<EquipmentSlot> worn = new ArrayList<>();
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			if (!this.getItemBySlot(slot).isEmpty()) {
				worn.add(slot);
			}
		}
		if (worn.isEmpty()) {
			return null;
		}
		EquipmentSlot slot = worn.get(this.random.nextInt(worn.size()));
		this.strippedSlot = slot;
		this.strippedItem = this.getItemBySlot(slot).copy();
		this.strippedTicks = ticks;
		this.setItemSlot(slot, ItemStack.EMPTY);
		this.sound(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.2F, 0.6F);
		return slot;
	}

	public boolean hasStrippedArmor() {
		return this.strippedSlot != null;
	}

	private void restoreStripped() {
		if (this.strippedSlot != null) {
			if (this.getItemBySlot(this.strippedSlot).isEmpty()) {
				this.setItemSlot(this.strippedSlot, this.strippedItem);
			}
			this.sound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8F, 0.8F);
			this.serverLevel().sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.0, this.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
		}
		this.strippedSlot = null;
		this.strippedItem = ItemStack.EMPTY;
		this.strippedTicks = 0;
	}

	private ItemStack worn(ItemStack stack) {
		if (stack.isDamageableItem()) {
			stack.setDamageValue((int) (stack.getMaxDamage() * (0.35F + 0.5F * this.random.nextFloat())));
		}
		return stack;
	}

	// ---- persistence -------------------------------------------------------------------------------

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		this.getOwnerUuid().ifPresent(uuid -> tag.putUUID("Owner", uuid));
		tag.putString("OwnerName", this.ownerName);
		tag.putInt("Tier", this.getTier());
		tag.putInt("Phase", this.getPhase());
		tag.putBoolean("Empowered", this.isEmpowered());
		tag.putLong("LifeTicks", this.lifeTicks);
		tag.putLong("AbandonedSince", this.abandonedSince);
		tag.putInt("CrowSteals", this.crowSteals);
		tag.putBoolean("Dormant", this.dormant);
		tag.putFloat("GearScore", this.gearScore);
		tag.putInt("EchoRank", this.echoRank);
		tag.putInt("StoredXp", this.storedXp);
		tag.putFloat("Poise", this.getPoise());
		tag.putString("Variant", this.getVariant().key());
		tag.putBoolean("VariantKit", this.variantKit);
		tag.putBoolean("Crust", this.hasCrust());
		if (this.strippedSlot != null && !this.strippedItem.isEmpty()) {
			tag.putString("StrippedSlot", this.strippedSlot.getName());
			tag.put("StrippedItem", this.strippedItem.save(this.registryAccess()));
			tag.putInt("StrippedTicks", this.strippedTicks);
		}
		if (!this.epitaph.isEmpty()) {
			tag.put("Epitaph", this.epitaph.save(this.registryAccess()));
		}
		if (this.lastWords != null) {
			tag.putString("LastWords", Component.Serializer.toJson(this.lastWords, this.registryAccess()));
		}
		if (this.anchor != null) {
			tag.putIntArray("Anchor", new int[]{this.anchor.getX(), this.anchor.getY(), this.anchor.getZ()});
		}
		ListTag items = new ListTag();
		for (ItemStack stack : this.storedItems) {
			if (!stack.isEmpty()) {
				items.add(stack.save(this.registryAccess()));
			}
		}
		tag.put("StoredItems", items);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		if (this.isForgotten() && !tag.contains("CustomName")) {
			tag.putBoolean("PersistenceRequired", false); // 1.0-1.1 marked every Forgotten persistent: they never despawned
		}
		super.readAdditionalSaveData(tag);
		if (tag.hasUUID("Owner")) {
			this.entityData.set(DATA_OWNER, Optional.of(tag.getUUID("Owner")));
		}
		this.ownerName = tag.getString("OwnerName");
		this.entityData.set(DATA_TIER, tag.getInt("Tier"));
		this.entityData.set(DATA_PHASE, Math.max(1, tag.getInt("Phase")));
		this.entityData.set(DATA_EMPOWERED, tag.getBoolean("Empowered"));
		this.lifeTicks = tag.getLong("LifeTicks");
		this.abandonedSince = tag.getLong("AbandonedSince");
		this.crowSteals = tag.getInt("CrowSteals");
		this.dormant = tag.getBoolean("Dormant");
		if (tag.contains("EchoRank")) {
			this.gearScore = tag.getFloat("GearScore");
			this.echoRank = Math.max(1, Math.min(4, tag.getInt("EchoRank")));
		} else {
			// a 1.0 Remnant: judge it by the gear it wears
			this.gearScore = gearScore(EquipmentSnapshot.of(this));
			this.echoRank = Math.min(4, rankFor(this.gearScore) + (this.isEmpowered() ? 1 : 0));
		}
		this.storedXp = tag.getInt("StoredXp");
		this.entityData.set(DATA_CRUST, tag.getBoolean("Crust"));
		if (tag.contains("StrippedSlot")) {
			this.strippedSlot = EquipmentSlot.byName(tag.getString("StrippedSlot"));
			this.strippedItem = ItemStack.parse(this.registryAccess(), tag.getCompound("StrippedItem")).orElse(ItemStack.EMPTY);
			this.strippedTicks = Math.max(1, tag.getInt("StrippedTicks"));
		}
		if (tag.contains("Variant")) {
			this.setVariant(ForgottenVariant.byKey(tag.getString("Variant")));
			// a spawn egg / summon that names a variant without its kit ({Variant:"frozen"}) still gets the crust
			if (!tag.getBoolean("VariantKit")) {
				this.applyVariantKit();
			}
		}
		this.epitaph = tag.contains("Epitaph") ? ItemStack.parse(this.registryAccess(), tag.getCompound("Epitaph")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
		this.entityData.set(DATA_POISE, tag.contains("Poise") ? Mth.clamp(tag.getFloat("Poise"), 0.0F, MAX_POISE) : MAX_POISE);
		this.lastWords = tag.contains("LastWords") ? Component.Serializer.fromJson(tag.getString("LastWords"), this.registryAccess()) : null;
		if (this.dormant) {
			this.setAnim(ANIM_DORMANT);
		}
		// 1.0 Remnants carried their streak scaling as an attribute modifier; it now lives in damageScale()
		AttributeInstance damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) {
			damage.removeModifier(TIER_DAMAGE);
		}
		int[] a = tag.getIntArray("Anchor");
		if (a.length == 3) {
			this.anchor = new BlockPos(a[0], a[1], a[2]);
			this.restrictTo(this.anchor, LINGER_RADIUS);
		}
		this.storedItems.clear();
		ListTag items = tag.getList("StoredItems", Tag.TAG_COMPOUND);
		for (int i = 0; i < items.size(); i++) {
			ItemStack.parse(this.registryAccess(), items.getCompound(i)).ifPresent(this.storedItems::add);
		}
		this.bossEvent.setName(this.getDisplayName());
		this.bossEvent.setColor(this.getPhase() == 1 ? BossEvent.BossBarColor.BLUE : this.getPhase() == 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED);
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof RemnantEntity || super.isAlliedTo(other);
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	/** Under the ground (burrowing) there is nothing to hit or bump into. */
	@Override
	public boolean isPickable() {
		return !this.isBurrowed() && super.isPickable();
	}

	@Override
	public boolean isPushable() {
		return this.getSinkTicks() == 0 && !this.isEmerging() && super.isPushable();
	}

	@Override
	protected boolean canRide(Entity vehicle) {
		return false;
	}

	@Override
	public int lookTargetId() {
		return this.entityData.get(DATA_LOOK);
	}
}
