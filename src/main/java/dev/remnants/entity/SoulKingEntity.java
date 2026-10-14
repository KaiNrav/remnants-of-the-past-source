package dev.remnants.entity;

import dev.remnants.fx.CombatCues;

import dev.remnants.RemnantsConfig;
import dev.remnants.registry.ModEntities;
import dev.remnants.registry.ModItems;
import dev.remnants.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Soul King: a three-headed, four-armed giant summoned at the Spirit Offering Table.
 *
 * <ul>
 *   <li><b>Four arms</b>, one weapon each: sword sweep, mace slam, trident throw, bow volley; later phases unlock
 *   Whirlwind, Storm Grid, Arrow Rain, Soul Harvest and Cataclysm.</li>
 *   <li><b>Three heads</b> act on their own: the left wails (darkness + slow cone), the right fires a tracking soul
 *   beam, the crowned centre calls echoes of the players. Each head can be shot off (headshots deal +25 %), which
 *   silences its ability and staggers the King.</li>
 *   <li><b>Phase 2</b> raises soul crystals over the pedestals; while any stands the King takes half damage.</li>
 *   <li><b>Phase 3</b> enrages it and makes soul fire erupt under the players.</li>
 * </ul>
 */
public class SoulKingEntity extends Monster implements LookTarget {
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(SoulKingEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(SoulKingEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(SoulKingEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_HEAD_ACTION = SynchedEntityData.defineId(SoulKingEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_HEADS = SynchedEntityData.defineId(SoulKingEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_INTRO = SynchedEntityData.defineId(SoulKingEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_SHIELDED = SynchedEntityData.defineId(SoulKingEntity.class, EntityDataSerializers.BOOLEAN);

	public static final int INTRO_TICKS = 120;
	/** The King appears in the pillar of light this many ticks into the summoning (until then only the ritual is drawn). */
	public static final int EMERGE_TICK = 100;
	private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0F, 0.15F, 0.2F), 1.6F);
	private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.7F, 0.3F, 1.0F), 1.6F);

	/** Arm attacks and combos. Duration in ticks drives both the server logic and the client animation. */
	public enum Action {
		IDLE(0, 0), SWORD_SWEEP(24, 40), MACE_SLAM(32, 70), TRIDENT_THROW(20, 60), BOW_VOLLEY(28, 60),
		WHIRLWIND(70, 300), STORM_GRID(64, 360), ARROW_RAIN(60, 320), SOUL_HARVEST(90, 520), CATACLYSM(56, 260),
		ROAR(40, 0), STAGGER(50, 0);

		public final int duration;
		public final int cooldown;

		Action(int duration, int cooldown) {
			this.duration = duration;
			this.cooldown = cooldown;
		}
	}

	public static final int HEAD_LEFT = 0, HEAD_CENTER = 1, HEAD_RIGHT = 2;
	public static final int CAST_NONE = 0, CAST_WAIL = 1, CAST_CALL = 2, CAST_BEAM = 3;

	private final ServerBossEvent bossEvent = new dev.remnants.bossbar.StyledBossEvent(Component.translatable("entity.remnants_of_the_past.soul_king"),
		BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_12, dev.remnants.bossbar.BossStyle.KING);
	private final Map<Action, Integer> cooldowns = new EnumMap<>(Action.class);
	private final int[] headCooldowns = {120, 260, 80};
	private final float[] headHealth = new float[3];
	private final Set<UUID> hitThisAction = new HashSet<>();
	private final List<BlockPos> pedestals = new ArrayList<>();
	private final List<UUID> crystals = new ArrayList<>();
	private final List<Vec3> marks = new ArrayList<>();
	@Nullable
	private BlockPos altar;
	private int actionTick;
	private int actionGap = 40;
	private int headCastTick;
	private Vec3 beamAim = Vec3.ZERO;
	private Vec3 slamMarker = Vec3.ZERO;
	private int eruptionTimer = 60;
	private int unreachableTicks;
	private int transitionPending;

	/** Client: tick the current action / head cast started (for animation). */
	public int clientActionStart;
	public int clientCastStart;
	/** Client: the action that was playing before the current one, so the model can cross-fade an interruption. */
	public Action clientPrevAction = Action.IDLE;
	public int clientPrevActionStart;
	public int clientActionChanged = -100;
	private Action clientCurrentAction = Action.IDLE;
	/** Death lasts this long (the keyframed collapse), instead of vanilla's 20 ticks. */
	public static final int DEATH_TICKS = 60;

	public SoulKingEntity(EntityType<? extends SoulKingEntity> type, Level level) {
		super(type, level);
		this.xpReward = 500;
		this.setPersistenceRequired();
		this.bossEvent.setVisible(false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 600.0)
			.add(Attributes.ARMOR, 8.0)
			.add(Attributes.ARMOR_TOUGHNESS, 2.0)
			.add(Attributes.ATTACK_DAMAGE, 14.0)
			.add(Attributes.MOVEMENT_SPEED, 0.26)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 64.0)
			.add(Attributes.STEP_HEIGHT, 2.0)
			.add(Attributes.SAFE_FALL_DISTANCE, 256.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_LOOK, -1);
		builder.define(DATA_PHASE, 1);
		builder.define(DATA_ACTION, 0);
		builder.define(DATA_HEAD_ACTION, 0);
		builder.define(DATA_HEADS, 0b111);
		builder.define(DATA_INTRO, 0);
		builder.define(DATA_SHIELDED, false);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_ACTION.equals(key)) {
			this.clientPrevAction = this.clientCurrentAction;
			this.clientPrevActionStart = this.clientActionStart;
			this.clientActionChanged = this.tickCount;
			this.clientCurrentAction = this.getAction();
			this.clientActionStart = this.tickCount;
		} else if (DATA_HEAD_ACTION.equals(key)) {
			this.clientCastStart = this.tickCount;
		}
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
			p -> !((Player) p).isCreative() && !p.isSpectator()));
	}

	// ---- summoning ---------------------------------------------------------------------------------

	/** Called by the Spirit Offering Table before the King is added to the world. */
	public void beginRitual(BlockPos altarPos, List<BlockPos> pedestalPositions) {
		this.altar = altarPos;
		this.pedestals.clear();
		this.pedestals.addAll(pedestalPositions);
		this.setup();
		this.entityData.set(DATA_INTRO, INTRO_TICKS);
	}

	private void setup() {
		RemnantsConfig cfg = RemnantsConfig.get();
		AttributeInstance hp = this.getAttribute(Attributes.MAX_HEALTH);
		if (hp != null) {
			hp.setBaseValue(cfg.soulKingHealth);
		}
		this.setHealth(this.getMaxHealth());
		for (int i = 0; i < 3; i++) {
			this.headHealth[i] = this.getMaxHealth() * 0.1F;
		}
		this.setCustomName(Component.translatable("entity.remnants_of_the_past.soul_king").withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	@Override
	@Nullable
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
		if (this.entityData.get(DATA_INTRO) == 0 && this.altar == null) {
			this.setup();
			this.entityData.set(DATA_INTRO, 60);
		}
		return result;
	}

	// ---- accessors ---------------------------------------------------------------------------------

	public int getPhase() {
		return this.entityData.get(DATA_PHASE);
	}

	public Action getAction() {
		return Action.values()[Mth.clamp(this.entityData.get(DATA_ACTION), 0, Action.values().length - 1)];
	}

	public int getHeadCast() {
		return this.entityData.get(DATA_HEAD_ACTION);
	}

	public boolean hasHead(int head) {
		return (this.entityData.get(DATA_HEADS) & (1 << head)) != 0;
	}

	public int getIntro() {
		return this.entityData.get(DATA_INTRO);
	}

	public boolean isShielded() {
		return this.entityData.get(DATA_SHIELDED);
	}

	private void setAction(Action action) {
		this.entityData.set(DATA_ACTION, action.ordinal());
		this.actionTick = 0;
		this.hitThisAction.clear();
	}

	private ServerLevel server() {
		return (ServerLevel) this.level();
	}

	private float dmg(float base) {
		return base * (float) RemnantsConfig.get().soulKingDamageMultiplier * (this.getPhase() >= 3 ? 1.2F : 1.0F);
	}

	private Vec3 forward() {
		return Vec3.directionFromRotation(0, this.yBodyRot);
	}

	/** World position of a head (left / centre / right), used for headshots and head abilities. */
	public Vec3 headPos(int head) {
		Vec3 base = this.position();
		if (head == HEAD_CENTER) {
			return base.add(0, 4.55, 0);
		}
		Vec3 side = this.forward().yRot(head == HEAD_LEFT ? Mth.HALF_PI : -Mth.HALF_PI);
		return base.add(side.scale(1.1)).add(0, 4.4, 0);
	}

	private List<Player> players(Vec3 center, double radius) {
		return this.level().getEntitiesOfClass(Player.class, new AABB(center, center).inflate(radius),
			p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.position().distanceTo(center) <= radius);
	}

	private boolean hit(LivingEntity target, float amount) {
		return target.hurt(this.damageSources().mobAttack(this), this.dmg(amount));
	}

	private void push(LivingEntity target, Vec3 v) {
		target.setDeltaMovement(v);
		target.hurtMarked = true;
	}

	private void sound(SoundEvent s, float volume, float pitch) {
		this.level().playSound(null, this.getX(), this.getY() + 2, this.getZ(), s, SoundSource.HOSTILE, volume, pitch);
	}

	private void ring(Vec3 c, double r, ParticleOptions p, int n) {
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			this.server().sendParticles(p, c.x + Math.cos(a) * r, c.y + 0.15, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
	}

	private void line(Vec3 a, Vec3 b, ParticleOptions p, double step) {
		Vec3 d = b.subtract(a);
		int n = (int) Math.max(1, d.length() / step);
		for (int i = 0; i <= n; i++) {
			Vec3 q = a.add(d.scale((double) i / n));
			this.server().sendParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
		}
	}

	private void title(Component title, Component sub) {
		for (Player p : this.players(this.position(), 64)) {
			if (p instanceof ServerPlayer sp) {
				sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
				sp.connection.send(new ClientboundSetSubtitleTextPacket(sub));
				sp.connection.send(new ClientboundSetTitleTextPacket(title));
			}
		}
	}

	private void faceTarget(LivingEntity t) {
		this.faceTarget(t, 360.0F);
	}

	/** Turns at most {@code step} degrees: an instant snap every tick read as a statue jerking round in 3-tick steps. */
	private void faceTarget(LivingEntity t, float step) {
		float yaw = (float) (Mth.atan2(t.getZ() - this.getZ(), t.getX() - this.getX()) * Mth.RAD_TO_DEG) - 90.0F;
		this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, step));
		this.yBodyRot = this.getYRot();
	}

	@Override
	protected net.minecraft.world.entity.ai.control.BodyRotationControl createBodyControl() {
		return new dev.remnants.entity.ai.FaceBody(this);
	}

	// ---- damage ------------------------------------------------------------------------------------

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (this.level().isClientSide) {
			return false;
		}
		boolean unstoppable = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		if (!unstoppable && (this.getIntro() > 0 || this.getAction() == Action.ROAR)) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (attacker instanceof SoulKingEntity || attacker instanceof RemnantEntity) {
			return false;
		}
		int head = this.headHit(source);
		if (head >= 0) {
			amount *= 1.25F;
			this.headHealth[head] -= amount;
			this.server().sendParticles(ParticleTypes.CRIT, this.headPos(head).x, this.headPos(head).y, this.headPos(head).z, 8, 0.3, 0.3, 0.3, 0.2);
			if (this.headHealth[head] <= 0) {
				this.destroyHead(head);
			}
		}
		if (this.isShielded() && !unstoppable) {
			amount *= 0.5F;
			this.server().sendParticles(ModParticles.REMNANT_SOUL, this.getX(), this.getY() + 2.5, this.getZ(), 6, 0.8, 1.2, 0.8, 0.02);
		}
		if (this.getAction() == Action.STAGGER) {
			amount *= 1.5F;
		}
		return super.hurt(source, amount);
	}

	/** Which head (if any) a hit landed on: projectiles by impact point, melee by the attacker's line of sight. */
	private int headHit(DamageSource source) {
		Entity direct = source.getDirectEntity();
		Entity attacker = source.getEntity();
		if (direct == null) {
			return -1;
		}
		int best = -1;
		double bestDist = 1.25;
		for (int h = 0; h < 3; h++) {
			if (!this.hasHead(h)) {
				continue;
			}
			Vec3 p = this.headPos(h);
			double d;
			if (direct != attacker) {
				d = direct.position().distanceTo(p);
			} else if (attacker instanceof LivingEntity living) {
				Vec3 eye = living.getEyePosition();
				Vec3 look = living.getViewVector(1.0F);
				Vec3 rel = p.subtract(eye);
				d = rel.subtract(look.scale(rel.dot(look))).length();
				if (rel.dot(look) < 0) {
					continue;
				}
			} else {
				continue;
			}
			if (d < bestDist) {
				bestDist = d;
				best = h;
			}
		}
		return best;
	}

	private void destroyHead(int head) {
		this.entityData.set(DATA_HEADS, this.entityData.get(DATA_HEADS) & ~(1 << head));
		Vec3 p = this.headPos(head);
		this.server().sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 3, 0.3, 0.3, 0.3, 0);
		this.server().sendParticles(ParticleTypes.SOUL, p.x, p.y, p.z, 40, 0.4, 0.4, 0.4, 0.1);
		this.sound(SoundEvents.WITHER_BREAK_BLOCK, 2.0F, 0.6F);
		this.sound(SoundEvents.WARDEN_DEATH, 1.5F, 1.4F);
		if (this.getHeadCast() != CAST_NONE && castHead(this.getHeadCast()) == head) {
			this.entityData.set(DATA_HEAD_ACTION, CAST_NONE);
		}
		for (Player pl : this.players(this.position(), 48)) {
			pl.displayClientMessage(Component.translatable("message.remnants.king.head" + head).withStyle(ChatFormatting.GOLD), true);
		}
		if (this.getAction() != Action.ROAR) {
			CombatCues.clear(this);
			this.setAction(Action.STAGGER);
		}
	}

	private static int castHead(int cast) {
		return cast == CAST_WAIL ? HEAD_LEFT : cast == CAST_CALL ? HEAD_CENTER : HEAD_RIGHT;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return source.is(DamageTypes.FELL_OUT_OF_WORLD) && this.altar != null
			|| source.is(DamageTypes.IN_WALL) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)
			|| source.is(DamageTypes.CACTUS) || super.isInvulnerableTo(source);
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected boolean shouldDespawnInPeaceful() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected boolean canRide(Entity vehicle) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WARDEN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WITHER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WITHER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.5F;
	}

	// ---- life cycle --------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide && this.getAction() == Action.CATACLYSM) {
			// the server jumps the action clock to 40 the moment the King lands; the keyframed impact sits at 2.0 s
			int t = this.tickCount - this.clientActionStart;
			if (t > 13 && t < 39 && this.onGround()) {
				this.clientActionStart = this.tickCount - 40;
			}
		}
		if (this.level().isClientSide) {
			// a thin soul haze round him; his own fire (crown, eyes, hem, burning pauldrons) is pinned to the model in
			// SoulKingModel.ambient
			if (this.random.nextInt(4) == 0) {
				this.level().addParticle(ModParticles.REMNANT_SOUL, this.getRandomX(1.2), this.getY() + this.random.nextDouble() * 5, this.getRandomZ(1.2), 0, 0.02, 0);
			}
			if (this.getPhase() >= 3 && this.random.nextInt(3) == 0) {
				this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, this.getRandomX(1.0), this.getY() + this.random.nextDouble() * 5, this.getRandomZ(1.0), 0, 0.04, 0);
			}
		}
	}

	@Override
	protected void tickDeath() {
		this.deathTime++;
		if (this.level().isClientSide) {
			for (int i = 0; i < 3; i++) {
				this.level().addParticle(ParticleTypes.SOUL, this.getRandomX(1.4), this.getY() + this.random.nextDouble() * 5, this.getRandomZ(1.4),
					0, 0.06 + this.random.nextDouble() * 0.05, 0);
			}
			return;
		}
		if (this.deathTime == 25 || this.deathTime == 45) {
			this.sound(SoundEvents.WITHER_HURT, 2.0F, 0.4F);
		}
		if (this.deathTime >= DEATH_TICKS && !this.isRemoved()) {
			this.server().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 1.5, this.getZ(), 120, 1.5, 1.5, 1.5, 0.08);
			this.server().sendParticles(ModParticles.REMNANT_SOUL, this.getX(), this.getY() + 2.5, this.getZ(), 60, 1.2, 2.0, 1.2, 0.05);
			this.sound(SoundEvents.WITHER_DEATH, 1.5F, 0.6F);
			this.level().broadcastEntityEvent(this, (byte) 60);
			this.remove(RemovalReason.KILLED);
		}
	}

	@Override
	protected void customServerAiStep() {
		this.entityData.set(DATA_LOOK, this.getTarget() == null ? -1 : this.getTarget().getId());
		super.customServerAiStep();
		this.updateBossBar();
		int intro = this.getIntro();
		if (intro > 0) {
			this.tickIntro(intro);
			return;
		}
		this.updatePhase();
		this.tickCrystals();
		LivingEntity target = this.getTarget();
		if (target != null && !target.isAlive()) {
			this.setTarget(null);
			target = null;
		}
		this.cooldowns.replaceAll((a, cd) -> Math.max(0, cd - 1));
		for (int i = 0; i < 3; i++) {
			this.headCooldowns[i]--;
		}
		this.tickHeads(target);
		if (this.getPhase() >= 3 && target != null) {
			this.tickEruptions();
		}
		this.tickAction(target);
	}

	private void updateBossBar() {
		this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
		this.bossEvent.setVisible(this.getIntro() < INTRO_TICKS - EMERGE_TICK);
		if (this.tickCount % 10 == 0) {
			for (ServerPlayer p : this.server().players()) {
				boolean near = p.distanceToSqr(this) < 64 * 64 && !p.isSpectator();
				if (near && !this.bossEvent.getPlayers().contains(p)) {
					this.bossEvent.addPlayer(p);
				} else if (!near && this.bossEvent.getPlayers().contains(p)) {
					this.bossEvent.removePlayer(p);
				}
			}
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bossEvent.removePlayer(player);
	}

	@Override
	public void remove(RemovalReason reason) {
		this.bossEvent.removeAllPlayers();
		if (!this.level().isClientSide && reason.shouldDestroy()) {
			this.discardCrystals();
		}
		super.remove(reason);
	}

	/**
	 * The summoning (1.3, ~6 s; the client draws it in SoulKingRitual): t 0-20 the offering rises over the table, 20-60
	 * the pedestals light one by one and beam into it, 60-85 a vortex of soul shards gathers, 85-100 a pillar of light,
	 * 100-120 the King steps out of the pillar. He cannot be hurt until it is over.
	 */
	private void tickIntro(int intro) {
		this.getNavigation().stop();
		this.setDeltaMovement(Vec3.ZERO);
		ServerLevel level = this.server();
		int t = INTRO_TICKS - intro;
		Vec3 offering = this.position().add(0, 1.5, 0);
		if (t == 0) {
			this.sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 0.6F);
			this.sound(SoundEvents.BEACON_POWER_SELECT, 1.5F, 0.7F);
		}
		for (int k = 0; k < this.pedestals.size(); k++) {
			BlockPos p = this.pedestals.get(k);
			int start = 20 + k * 10;
			if (t == start) {
				level.playSound(null, p, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5F, 1.3F + k * 0.12F);
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX() + 0.5, p.getY() + 1.3, p.getZ() + 0.5, 20, 0.2, 0.3, 0.2, 0.03);
			}
			if (t >= start && t < 85 && t % 4 == 0) {
				this.line(Vec3.atCenterOf(p).add(0, 0.8, 0), offering, ModParticles.REMNANT_SOUL, 0.9);
			}
		}
		if (t == 60) {
			this.sound(SoundEvents.SOUL_ESCAPE.value(), 3.0F, 0.5F);
			this.sound(SoundEvents.WARDEN_SONIC_CHARGE, 1.2F, 1.4F);
		}
		if (t >= 60 && t < 85 && t % 2 == 0) {
			double r = 3.0 * (1.0 - (t - 60) / 25.0) + 0.3;
			this.ring(offering.add(0, -0.6, 0), r, ParticleTypes.SOUL, 10);
		}
		if (t == 85) {
			this.sound(SoundEvents.BEACON_ACTIVATE, 3.0F, 0.6F);
			this.sound(SoundEvents.TRIDENT_THUNDER.value(), 3.0F, 0.6F);
		}
		if (t >= 85 && t < 100 && t % 3 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0, this.getZ(), 6, 0.5, 2.5, 0.5, 0.02);
		}
		if (t == 100) {
			this.sound(SoundEvents.WITHER_SPAWN, 2.0F, 0.6F);
		}
		if (intro == 1) {
			this.sound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.5F);
			this.title(Component.translatable("message.remnants.king.title").withStyle(ChatFormatting.LIGHT_PURPLE),
				Component.translatable("message.remnants.king.subtitle").withStyle(ChatFormatting.GRAY));
			for (Player p : this.players(this.position(), 8)) {
				this.push(p, p.position().subtract(this.position()).multiply(1, 0, 1).normalize().scale(1.6).add(0, 0.5, 0));
			}
			this.ring(this.position(), 4, ParticleTypes.SOUL_FIRE_FLAME, 50);
			this.ring(this.position(), 7, ParticleTypes.SOUL_FIRE_FLAME, 70);
		}
		this.entityData.set(DATA_INTRO, intro - 1);
	}

	// ---- phases & crystals -------------------------------------------------------------------------

	/** Public for game tests. */
	public void updatePhase() {
		float f = this.getHealth() / this.getMaxHealth();
		int phase = f <= 0.33F ? 3 : f <= 0.66F ? 2 : 1;
		if (phase <= this.getPhase()) {
			return;
		}
		this.entityData.set(DATA_PHASE, phase);
		this.entityData.set(DATA_HEAD_ACTION, CAST_NONE);
		CombatCues.clear(this);
		this.setAction(Action.ROAR);
		this.getNavigation().stop();
		this.sound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.4F);
		this.sound(SoundEvents.WITHER_SPAWN, 1.0F, 1.2F);
		this.bossEvent.setColor(phase == 2 ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.RED);
		this.bossEvent.setDarkenScreen(phase == 3);
		this.bossEvent.setCreateWorldFog(phase == 3);
		this.title(Component.translatable("message.remnants.king.phase" + phase).withStyle(phase == 2 ? ChatFormatting.AQUA : ChatFormatting.RED),
			Component.translatable("message.remnants.king.phase" + phase + ".sub").withStyle(ChatFormatting.GRAY));
		this.transitionPending = phase;
		if (phase == 3) {
			AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.addOrReplacePermanentModifier(new AttributeModifier(dev.remnants.RemnantsMod.id("king_enrage"), 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		}
	}

	private void spawnCrystals() {
		this.discardCrystals();
		List<Vec3> spots = new ArrayList<>();
		for (BlockPos p : this.pedestals) {
			spots.add(Vec3.atBottomCenterOf(p).add(0, 3.0, 0));
		}
		if (spots.isEmpty()) {
			for (int i = 0; i < 4; i++) {
				double a = Math.PI / 2 * i;
				spots.add(this.position().add(Math.cos(a) * 8, 3.5, Math.sin(a) * 8));
			}
		}
		for (Vec3 s : spots) {
			SoulCrystalEntity c = ModEntities.SOUL_CRYSTAL.create(this.level());
			if (c != null) {
				c.moveTo(s.x, s.y, s.z);
				c.setKing(this);
				this.level().addFreshEntity(c);
				this.crystals.add(c.getUUID());
				LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(this.level());
				if (bolt != null) {
					bolt.moveTo(s.subtract(0, 3, 0));
					bolt.setVisualOnly(true);
					this.level().addFreshEntity(bolt);
				}
			}
		}
		this.entityData.set(DATA_SHIELDED, !this.crystals.isEmpty());
	}

	private void tickCrystals() {
		if (this.crystals.isEmpty()) {
			return;
		}
		this.crystals.removeIf(id -> {
			Entity e = this.server().getEntity(id);
			return e == null || !e.isAlive();
		});
		if (this.crystals.isEmpty()) {
			this.entityData.set(DATA_SHIELDED, false);
			this.sound(SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.6F);
			for (Player p : this.players(this.position(), 48)) {
				p.displayClientMessage(Component.translatable("message.remnants.king.unshielded").withStyle(ChatFormatting.AQUA), true);
			}
			return;
		}
		if (this.tickCount % 4 == 0) {
			for (UUID id : this.crystals) {
				Entity e = this.server().getEntity(id);
				if (e != null) {
					this.line(e.position(), this.position().add(0, 3, 0), ParticleTypes.SOUL_FIRE_FLAME, 0.9);
				}
			}
		}
	}

	/** Called by a crystal when a player breaks it. */
	public void onCrystalDestroyed(SoulCrystalEntity crystal) {
		this.crystals.remove(crystal.getUUID());
		this.sound(SoundEvents.WITHER_HURT, 1.5F, 0.5F);
	}

	private void discardCrystals() {
		for (UUID id : this.crystals) {
			Entity e = this.server().getEntity(id);
			if (e != null) {
				e.discard();
			}
		}
		this.crystals.clear();
		this.entityData.set(DATA_SHIELDED, false);
	}

	public int crystalCount() {
		return this.crystals.size();
	}

	// ---- heads -------------------------------------------------------------------------------------

	private void tickHeads(@Nullable LivingEntity target) {
		int cast = this.getHeadCast();
		if (cast != CAST_NONE) {
			this.tickCast(cast, target, this.headCastTick++);
			return;
		}
		if (target == null || this.getAction() == Action.ROAR || this.getAction() == Action.STAGGER) {
			return;
		}
		double d = this.distanceTo(target);
		float speed = this.getPhase() >= 3 ? 0.65F : 1.0F;
		if (this.hasHead(HEAD_RIGHT) && this.headCooldowns[HEAD_RIGHT] <= 0 && d > 5 && d < 32 && this.hasLineOfSight(target)) {
			this.startCast(CAST_BEAM, (int) (180 * speed));
			this.beamAim = target.getEyePosition();
			this.sound(SoundEvents.GUARDIAN_ATTACK, 2.0F, 0.5F);
		} else if (this.hasHead(HEAD_LEFT) && this.headCooldowns[HEAD_LEFT] <= 0 && d < 16) {
			this.startCast(CAST_WAIL, (int) (220 * speed));
			this.sound(SoundEvents.WARDEN_ROAR, 2.0F, 1.3F);
		} else if (this.hasHead(HEAD_CENTER) && this.headCooldowns[HEAD_CENTER] <= 0 && this.getPhase() >= 2 && this.echoCount() < 4) {
			this.startCast(CAST_CALL, (int) (420 * speed));
			this.sound(SoundEvents.EVOKER_PREPARE_SUMMON, 2.0F, 0.6F);
		}
	}

	/** Debug / showcase (/remnants king ...): play an action, a head cast or the intro right now. */
	public void debugAction(Action action, LivingEntity target) {
		if (action == Action.IDLE) {
			this.setAction(Action.IDLE);
		} else {
			this.startAction(action, target);
		}
	}

	public void debugCast(int cast) {
		this.startCast(cast, 0);
	}

	public void debugIntro() {
		this.entityData.set(DATA_INTRO, INTRO_TICKS);
	}

	private void startCast(int cast, int cooldown) {
		this.entityData.set(DATA_HEAD_ACTION, cast);
		this.headCastTick = 0;
		this.headCooldowns[castHead(cast)] = cooldown;
	}

	private void tickCast(int cast, @Nullable LivingEntity target, int t) {
		ServerLevel level = this.server();
		switch (cast) {
			case CAST_WAIL -> {
				Vec3 from = this.headPos(HEAD_LEFT);
				Vec3 dir = target != null ? target.getEyePosition().subtract(from).normalize() : this.forward();
				if (t < 20 && t % 2 == 0) {
					for (int i = 0; i < 6; i++) {
						Vec3 spread = dir.yRot((this.random.nextFloat() - 0.5F) * 1.2F).scale(2 + this.random.nextDouble() * 10);
						level.sendParticles(ParticleTypes.SCULK_SOUL, from.x + spread.x, from.y + spread.y, from.z + spread.z, 1, 0, 0, 0, 0);
					}
				}
				if (t == 20) {
					this.sound(SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 0.6F);
					for (Player p : this.players(from, 16)) {
						if (p.getEyePosition().subtract(from).normalize().dot(dir) > 0.6) {
							p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0), this);
							p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
							this.hit(p, 4);
						}
					}
				}
				if (t >= 30) {
					this.entityData.set(DATA_HEAD_ACTION, CAST_NONE);
				}
			}
			case CAST_BEAM -> {
				Vec3 from = this.headPos(HEAD_RIGHT);
				if (target != null) {
					// lags behind the target: keep moving and it misses
					this.beamAim = this.beamAim.lerp(target.getEyePosition().subtract(0, 0.3, 0), t < 25 ? 0.2 : 0.07);
				}
				Vec3 dir = this.beamAim.subtract(from).normalize();
				Vec3 end = from.add(dir.scale(32));
				if (t < 25) {
					if (t % 2 == 0) {
						this.line(from, this.beamAim, ModParticles.REMNANT_SOUL, 1.2);
					}
				} else {
					if (t == 25) {
						this.sound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.6F);
					}
					this.line(from, from.add(dir.scale(24)), t % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : VIOLET, 0.6);
					if (t % 4 == 0) {
						for (Player p : this.players(from, 32)) {
							Vec3 rel = p.position().add(0, 1, 0).subtract(from);
							double along = rel.dot(dir);
							if (along > 0 && rel.subtract(dir.scale(along)).length() < 0.9) {
								this.hit(p, 4);
								p.setRemainingFireTicks(40);
							}
						}
					}
				}
				if (t >= 50) {
					this.entityData.set(DATA_HEAD_ACTION, CAST_NONE);
				}
			}
			case CAST_CALL -> {
				Vec3 crown = this.headPos(HEAD_CENTER);
				level.sendParticles(ParticleTypes.SOUL, crown.x, crown.y + 0.8, crown.z, 4, 0.5, 0.3, 0.5, 0.02);
				if (t == 20) {
					int made = 0;
					for (Player p : this.players(this.position(), 32)) {
						for (int i = 0; i < 2 && made < 4; i++, made++) {
							double a = this.random.nextDouble() * Math.PI * 2;
							RemnantEntity.spawnEcho(level, this, p, p.position().add(Math.cos(a) * 4, 0.5, Math.sin(a) * 4), p);
						}
					}
					this.sound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 2.0F, 0.5F);
				}
				if (t >= 30) {
					this.entityData.set(DATA_HEAD_ACTION, CAST_NONE);
				}
			}
			default -> this.entityData.set(DATA_HEAD_ACTION, CAST_NONE);
		}
	}

	private int echoCount() {
		return this.level().getEntitiesOfClass(RemnantEntity.class, this.getBoundingBox().inflate(48), RemnantEntity::isIllusion).size();
	}

	// ---- eruptions (phase 3) -----------------------------------------------------------------------

	private void tickEruptions() {
		if (--this.eruptionTimer > 0) {
			if (this.eruptionTimer <= 24) {
				for (Vec3 m : this.marks) {
					if (this.eruptionTimer % 2 == 0) {
						this.ring(m, 1.6, this.eruptionTimer < 10 ? RED : ParticleTypes.SOUL_FIRE_FLAME, 16);
					}
				}
			}
			if (this.eruptionTimer == 24) {
				this.marks.clear();
				for (Player p : this.players(this.position(), 32)) {
					this.marks.add(p.position());
				}
			}
			return;
		}
		for (Vec3 m : this.marks) {
			for (int y = 0; y < 8; y++) {
				this.server().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, m.x, m.y + y * 0.6, m.z, 6, 0.4, 0.2, 0.4, 0.02);
			}
			this.level().playSound(null, m.x, m.y, m.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.5F, 0.5F);
			for (Player p : this.players(m, 1.7)) {
				this.hit(p, 8);
				this.push(p, new Vec3(0, 1.0, 0));
			}
		}
		this.marks.clear();
		this.eruptionTimer = 70;
	}

	// ---- arm actions -------------------------------------------------------------------------------

	private void tickAction(@Nullable LivingEntity target) {
		Action action = this.getAction();
		if (action != Action.IDLE) {
			this.runAction(action, target, this.actionTick++);
			if (this.actionTick >= action.duration && this.getAction() == action) {
				this.finishAction(action);
			}
			return;
		}
		if (this.transitionPending == 2) {
			this.transitionPending = 0;
			this.spawnCrystals();
		} else if (this.transitionPending == 3) {
			this.transitionPending = 0;
		}
		if (target == null) {
			this.getNavigation().stop();
			if (this.altar != null && this.position().distanceTo(Vec3.atCenterOf(this.altar)) > 6) {
				this.getNavigation().moveTo(this.altar.getX() + 0.5, this.altar.getY() + 1, this.altar.getZ() + 0.5, 0.8);
			}
			return;
		}
		this.getLookControl().setLookAt(target, 20, 20);
		double d = this.distanceTo(target);
		if (this.antiCheese(target, d)) {
			return;
		}
		if (d > 4.5) {
			this.getNavigation().moveTo(target, 1.0);
		} else {
			this.getNavigation().stop();
			this.faceTarget(target, 12.0F);
		}
		if (--this.actionGap > 0) {
			return;
		}
		Action pick = this.choose(target, d);
		if (pick != null) {
			this.faceTarget(target, 60.0F);
			this.startAction(pick, target);
		}
	}

	@Nullable
	private Action choose(LivingEntity target, double d) {
		boolean see = this.hasLineOfSight(target);
		int phase = this.getPhase();
		Action best = null;
		double bestScore = 0.5;
		for (Action a : Action.values()) {
			if (this.cooldowns.getOrDefault(a, 0) > 0) {
				continue;
			}
			double s = switch (a) {
				case SWORD_SWEEP -> d < 7 ? 2.0 : 0;
				case MACE_SLAM -> d < 7.5 ? 1.6 + (target.isBlocking() ? 0.8 : 0) : 0;
				case TRIDENT_THROW -> d > 5 && see ? 1.2 + (d > 12 ? 0.8 : 0) : 0;
				case BOW_VOLLEY -> d > 6 && see ? 1.3 + (d > 14 ? 0.6 : 0) : 0;
				case WHIRLWIND -> phase >= 2 && d < 10 ? 1.5 : 0;
				case STORM_GRID -> phase >= 2 ? 1.2 : 0;
				case ARROW_RAIN -> phase >= 2 && d > 6 ? 1.2 : 0;
				case SOUL_HARVEST -> phase >= 3 ? 1.6 : 0;
				case CATACLYSM -> phase >= 3 && d > 4 && d < 20 ? 1.5 : 0;
				default -> 0;
			};
			s *= 0.75 + this.random.nextDouble() * 0.5;
			if (s > bestScore) {
				bestScore = s;
				best = a;
			}
		}
		return best;
	}

	private void startAction(Action action, LivingEntity target) {
		this.setAction(action);
		this.getNavigation().stop();
		CombatCues.clear(this);
		switch (action) {
			case SWORD_SWEEP -> {
				this.sound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.5F, 0.5F);
				CombatCues.arcAttached(this, 6.5F, 151.0F, 13);
			}
			case MACE_SLAM -> {
				this.slamMarker = this.position().add(this.forward().scale(3.0));
				CombatCues.disc(this, this.slamMarker, 5.0F, 18);
			}
			case STORM_GRID -> {
				this.marks.clear();
				this.sound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.5F, 1.3F);
			}
			case ARROW_RAIN -> this.sound(SoundEvents.EVOKER_PREPARE_SUMMON, 2.0F, 0.5F);
			case SOUL_HARVEST -> {
				this.sound(SoundEvents.EVOKER_PREPARE_SUMMON, 2.0F, 0.5F);
				CombatCues.disc(this, this.position(), 6.0F, 50);
			}
			case CATACLYSM -> {
				this.slamMarker = target.position();
				CombatCues.disc(this, this.slamMarker, 6.0F, 16);
				Vec3 flat = this.slamMarker.subtract(this.position()).multiply(1, 0, 1);
				double dist = flat.length();
				this.push(this, (dist < 0.1 ? Vec3.ZERO : flat.normalize().scale(Math.min(dist * 0.08, 1.1))).add(0, 1.5, 0));
				this.sound(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 2.0F, 0.5F);
			}
			case WHIRLWIND -> {
				this.sound(SoundEvents.EVOKER_PREPARE_ATTACK, 2.0F, 0.4F);
				CombatCues.discAttached(this, 0.0F, 5.0F, 20);
			}
			default -> {
			}
		}
	}

	private void finishAction(Action action) {
		float speed = this.getPhase() >= 3 ? 0.65F : 1.0F;
		this.cooldowns.put(action, (int) (action.cooldown * speed));
		this.setAction(Action.IDLE);
		this.actionGap = this.getPhase() >= 3 ? 6 : this.getPhase() == 2 ? 10 : 16;
	}

	private void runAction(Action action, @Nullable LivingEntity target, int t) {
		ServerLevel level = this.server();
		Vec3 fwd = this.forward();
		switch (action) {
			case SWORD_SWEEP -> {
				if (t < 13 && t % 3 == 0) {
					for (int i = -5; i <= 5; i++) {
						Vec3 p = this.position().add(fwd.yRot((float) Math.toRadians(i * 15)).scale(6.0));
						level.sendParticles(t > 8 ? RED : ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + 0.2, p.z, 1, 0, 0, 0, 0);
					}
				}
				if (t == 13) {
					this.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.4F);
					for (int i = -4; i <= 4; i++) {
						Vec3 p = this.position().add(fwd.yRot((float) Math.toRadians(i * 18)).scale(3.5));
						level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y + 1.5, p.z, 1, 0, 0, 0, 0);
					}
					for (Player p : this.players(this.position(), 6.5)) {
						Vec3 to = p.position().subtract(this.position()).multiply(1, 0, 1).normalize();
						if (to.dot(fwd) > 0.25) {
							if (p.isBlocking()) {
								p.disableShield();
							}
							if (this.hit(p, 14)) {
								CombatCues.impact(this, p, p.position().add(0, p.getBbHeight() * 0.6, 0), 0.7F, 0.0F, 3);
							}
							this.push(p, to.scale(1.2).add(0, 0.35, 0));
						}
					}
				}
			}
			case MACE_SLAM -> {
				if (t < 18 && t % 2 == 0) {
					this.ring(this.slamMarker, 4.5, t > 12 ? RED : ParticleTypes.SOUL_FIRE_FLAME, 30);
				}
				if (t == 18) {
					this.slam(this.slamMarker, 5.0, 18, 0.9);
				}
				if (this.getPhase() >= 2 && t >= 18) {
					this.shockwave(this.slamMarker, (t - 18) * 0.7, 9);
				}
			}
			case TRIDENT_THROW -> {
				if (target != null) {
					this.faceTarget(target);
				}
				if (t == 11 && target != null) {
					int n = this.getPhase() >= 2 ? 3 : 1;
					for (int i = 0; i < n; i++) {
						Vec3 from = this.position().add(fwd.yRot(Mth.HALF_PI).scale(-1.9)).add(fwd.scale(0.6)).add(0, 4.4, 0);
						Vec3 aim = target.position().add(0, 1, 0).add(target.getDeltaMovement().scale(6)).subtract(from).normalize()
							.yRot((i - (n - 1) / 2.0F) * 0.22F);
						level.addFreshEntity(SpectralWeaponEntity.launched(level, this, dev.remnants.item.ArsenalItem.display(dev.remnants.item.ArsenalItem.TRIDENT),
							from, aim.scale(2.2), this.dmg(12), false));
					}
					this.sound(SoundEvents.TRIDENT_THROW.value(), 2.0F, 0.5F);
				}
			}
			case BOW_VOLLEY -> {
				if (target != null) {
					this.faceTarget(target);
				}
				if ((t == 15 || (t == 23 && this.getPhase() >= 3)) && target != null) {
					for (int i = 0; i < 5; i++) {
						this.arrow(target, -16 + 8 * i, 6.0);
					}
					this.sound(SoundEvents.ARROW_SHOOT, 2.0F, 0.5F);
				}
			}
			case WHIRLWIND -> {
				if (t < 20) {
					if (t % 2 == 0) {
						this.ring(this.position(), 5.5, t > 14 ? RED : ParticleTypes.SOUL_FIRE_FLAME, 36);
					}
					return;
				}
				if (target != null) {
					this.getNavigation().moveTo(target, 0.7);
				}
				if (t % 5 == 0) {
					this.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.6F + this.random.nextFloat() * 0.3F);
					this.ring(this.position().add(0, 1.5, 0), 4.5, ParticleTypes.SWEEP_ATTACK, 8);
				}
				if (t % 10 == 0) {
					for (Player p : this.players(this.position(), 5.0)) {
						if (this.hit(p, 8)) {
							CombatCues.impact(this, p, p.position().add(0, p.getBbHeight() * 0.6, 0), 0.3F, 0.0F, 1);
						}
						this.push(p, p.position().subtract(this.position()).multiply(1, 0, 1).normalize().scale(0.9).add(0, 0.3, 0));
					}
				}
			}
			case STORM_GRID -> {
				// three waves of five strikes around the target; each mark flashes for 18 ticks before it lands
				for (int wave = 0; wave < 3; wave++) {
					int place = wave * 14;
					int strike = place + 18;
					if (t == place && target != null) {
						Vec3 c = target.position();
						this.marks.add(c);
						for (int i = 0; i < 4; i++) {
							double a = Math.PI / 2 * i + wave * 0.6;
							this.marks.add(c.add(Math.cos(a) * 3.5, 0, Math.sin(a) * 3.5));
						}
					}
					if (t == place && this.marks.size() >= (wave + 1) * 5) {
						for (Vec3 m : this.marks.subList(wave * 5, wave * 5 + 5)) {
							CombatCues.disc(this, m, 2.0F, 18);
						}
					}
					if (t >= place && t < strike && this.marks.size() >= (wave + 1) * 5 && t % 2 == 0) {
						for (Vec3 m : this.marks.subList(wave * 5, wave * 5 + 5)) {
							this.ring(m, 1.8, ParticleTypes.ELECTRIC_SPARK, 14);
						}
					}
					if (t == strike && this.marks.size() >= (wave + 1) * 5) {
						for (Vec3 m : this.marks.subList(wave * 5, wave * 5 + 5)) {
							LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
							if (bolt != null) {
								bolt.moveTo(m);
								bolt.setVisualOnly(true);
								level.addFreshEntity(bolt);
							}
							for (Player p : this.players(m, 2.0)) {
								this.hit(p, 9);
								p.setRemainingFireTicks(60);
							}
						}
						this.sound(SoundEvents.TRIDENT_THUNDER.value(), 3.0F, 0.8F);
						CombatCues.impact(this, null, this.marks.get(wave * 5), 0.5F, 2.0F, 0);
					}
				}
			}
			case ARROW_RAIN -> {
				if (t < 24) {
					if (t % 2 == 0) {
						for (Player p : this.players(this.position(), 32)) {
							this.ring(p.position(), 4.0, t > 18 ? RED : ParticleTypes.SOUL_FIRE_FLAME, 26);
						}
					}
					if (t == 22) {
						this.marks.clear();
						for (Player p : this.players(this.position(), 32)) {
							this.marks.add(p.position());
							CombatCues.disc(this, p.position(), 4.0F, 2);
						}
					}
				} else if (t < 54) {
					for (Vec3 m : this.marks) {
						double a = this.random.nextDouble() * Math.PI * 2;
						double r = Math.sqrt(this.random.nextDouble()) * 4.0;
						PhantomArrowEntity arrow = new PhantomArrowEntity(level, this, new ItemStack(Items.ARROW), null);
						arrow.setPos(m.x + Math.cos(a) * r, m.y + 18, m.z + Math.sin(a) * r);
						arrow.shoot(0, -1, 0, 2.4F, 0.0F);
						arrow.setBaseDamage(this.dmg(2.0F));
						level.addFreshEntity(arrow);
					}
				}
			}
			case SOUL_HARVEST -> {
				Vec3 c = this.position();
				if (t < 50) {
					// the vortex: everyone within 20 blocks is dragged toward the King; run outward to escape
					if (t % 2 == 0) {
						this.ring(c, 20 - t * 0.35, ParticleTypes.SCULK_SOUL, 40);
						this.ring(c, 6.0, t > 40 ? RED : VIOLET, 30);
					}
					for (Player p : this.players(c, 20)) {
						Vec3 pull = c.subtract(p.position()).multiply(1, 0, 1).normalize().scale(0.11);
						p.setDeltaMovement(p.getDeltaMovement().add(pull));
						p.hurtMarked = true;
					}
				}
				if (t == 50) {
					this.sound(SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.5F);
					this.sound(SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 0.5F);
					level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
					CombatCues.impact(this, null, c, 1.0F, 6.0F, 3);
					for (Player p : this.players(c, 6.0)) {
						this.hit(p, 22);
						this.push(p, p.position().subtract(c).multiply(1, 0, 1).normalize().scale(1.8).add(0, 0.7, 0));
					}
					this.heal(this.getMaxHealth() * 0.015F * this.players(c, 6.0).size());
				}
			}
			case CATACLYSM -> {
				if (t % 2 == 0) {
					this.ring(this.slamMarker, 5.0, t > 8 ? RED : ParticleTypes.SOUL_FIRE_FLAME, 36);
				}
				if (t == 12) {
					Vec3 to = this.slamMarker.subtract(this.position());
					this.push(this, new Vec3(to.x * 0.25, -2.5, to.z * 0.25));
				}
				if (t > 14 && t < 40 && (this.onGround() || t == 39) && this.hitThisAction.isEmpty()) {
					this.hitThisAction.add(this.getUUID());
					this.slam(this.position(), 6.0, 20, 1.1);
					this.actionTick = 40;
				}
				if (t >= 40) {
					this.shockwave(this.position(), (t - 40) * 0.8, 12);
				}
			}
			case ROAR -> {
				this.getNavigation().stop();
				this.server().sendParticles(ModParticles.REMNANT_SOUL, this.getX(), this.getY() + 3, this.getZ(), 10, 1.0, 1.5, 1.0, 0.05);
				if (t == 10) {
					CombatCues.impact(this, null, this.position(), 0.6F, 9.0F, 0);
					for (Player p : this.players(this.position(), 9)) {
						this.push(p, p.position().subtract(this.position()).multiply(1, 0, 1).normalize().scale(1.6).add(0, 0.5, 0));
						this.hit(p, 4);
					}
					this.ring(this.position(), 5, ParticleTypes.SOUL_FIRE_FLAME, 50);
					this.ring(this.position(), 8, ParticleTypes.SOUL_FIRE_FLAME, 70);
				}
			}
			case STAGGER -> {
				this.getNavigation().stop();
				if (t % 4 == 0) {
					this.server().sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 5.6, this.getZ(), 6, 0.8, 0.2, 0.8, 0.05);
				}
			}
			default -> {
			}
		}
	}

	private void slam(Vec3 at, double radius, float damage, double up) {
		CombatCues.impact(this, null, at, 1.0F, (float) radius, 3);
		this.sound(SoundEvents.MACE_SMASH_GROUND_HEAVY, 3.0F, 0.6F);
		this.sound(SoundEvents.GENERIC_EXPLODE.value(), 1.5F, 0.8F);
		this.server().sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 5, radius * 0.4, 0.2, radius * 0.4, 0);
		this.ring(at, radius * 0.5, ParticleTypes.SOUL_FIRE_FLAME, 30);
		this.ring(at, radius, ParticleTypes.SOUL_FIRE_FLAME, 50);
		for (Player p : this.players(at, radius)) {
			double d = p.position().distanceTo(at);
			this.hit(p, damage * (float) Math.max(0.35, 1 - d / (radius + 2)));
			this.push(p, p.position().subtract(at).multiply(1, 0, 1).normalize().scale(0.6).add(0, up, 0));
		}
	}

	/** An expanding ground ring; jump over it. Each player is hit at most once per action. */
	private void shockwave(Vec3 center, double r, double max) {
		if (r <= 0 || r > max) {
			return;
		}
		this.ring(center, r, ParticleTypes.SOUL_FIRE_FLAME, (int) (r * 8) + 8);
		for (Player p : this.players(center, r + 1.2)) {
			double d = p.position().multiply(1, 0, 1).distanceTo(center.multiply(1, 0, 1));
			if (Math.abs(d - r) < 0.9 && p.onGround() && this.hitThisAction.add(p.getUUID())) {
				this.hit(p, 8);
				this.push(p, p.position().subtract(center).multiply(1, 0, 1).normalize().scale(0.8).add(0, 0.45, 0));
			}
		}
	}

	private void arrow(LivingEntity target, float yawOffset, double damage) {
		Vec3 from = this.position().add(this.forward().yRot(Mth.HALF_PI).scale(-0.9)).add(this.forward().scale(1.6)).add(0, 3.5, 0);
		PhantomArrowEntity arrow = new PhantomArrowEntity(this.level(), this, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		arrow.setPos(from);
		Vec3 aim = target.position().add(0, 1, 0).subtract(from);
		Vec3 dir = new Vec3(aim.x, 0, aim.z).yRot(yawOffset * Mth.DEG_TO_RAD);
		arrow.shoot(dir.x, aim.y + aim.horizontalDistance() * 0.12, dir.z, 2.4F, 1.0F);
		arrow.setBaseDamage(this.dmg((float) damage) / 2.4F);
		this.level().addFreshEntity(arrow);
	}

	/** Never let a player hide on a pillar or run off: step through the spirit world to them. */
	private boolean antiCheese(LivingEntity target, double d) {
		boolean stuck = d > 26 || (target.getY() - this.getY() > 4 && d < 10) || (this.getNavigation().isDone() && d > 6);
		this.unreachableTicks = stuck ? this.unreachableTicks + 1 : Math.max(0, this.unreachableTicks - 2);
		if (this.unreachableTicks < 70) {
			return false;
		}
		this.unreachableTicks = 0;
		Vec3 from = this.position();
		Vec3 dest = target.position().add(target.getLookAngle().multiply(1, 0, 1).normalize().scale(-4));
		if (this.randomTeleport(dest.x, dest.y + 1, dest.z, false)) {
			this.server().sendParticles(ParticleTypes.SOUL, from.x, from.y + 2, from.z, 40, 0.8, 1.5, 0.8, 0.05);
			this.server().sendParticles(ParticleTypes.SOUL, dest.x, dest.y + 2, dest.z, 40, 0.8, 1.5, 0.8, 0.05);
			this.sound(SoundEvents.ENDERMAN_TELEPORT, 2.0F, 0.4F);
			return true;
		}
		return false;
	}

	// ---- death -------------------------------------------------------------------------------------

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		this.dropSafe(level, new ItemStack(ModItems.SOUL_KING_ARSENAL));
		this.dropSafe(level, new ItemStack(ModItems.SOUL_KING_HEART));
		this.dropSafe(level, new ItemStack(ModItems.SOUL_FRAGMENT, 8 + this.random.nextInt(5)));
		dev.remnants.SoulResonance.onKingSlain(this);
		this.discardCrystals();
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 2, this.getZ(), 2, 1, 1, 1, 0);
		level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 2.5, this.getZ(), 120, 1.5, 2.5, 1.5, 0.08);
		this.title(Component.translatable("message.remnants.king.victory").withStyle(ChatFormatting.GOLD),
			Component.translatable("message.remnants.king.victory.sub").withStyle(ChatFormatting.GRAY));
	}

	private void dropSafe(ServerLevel level, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, this.getX(), this.getY() + 1.0, this.getZ(), stack, 0, 0.3, 0);
		item.setInvulnerable(true);
		item.setUnlimitedLifetime();
		item.setGlowingTag(true);
		level.addFreshEntity(item);
	}

	// ---- persistence -------------------------------------------------------------------------------

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("Phase", this.getPhase());
		tag.putInt("Heads", this.entityData.get(DATA_HEADS));
		tag.putInt("Intro", this.getIntro());
		for (int i = 0; i < 3; i++) {
			tag.putFloat("HeadHealth" + i, this.headHealth[i]);
		}
		if (this.altar != null) {
			tag.putIntArray("Altar", new int[]{this.altar.getX(), this.altar.getY(), this.altar.getZ()});
		}
		ListTag list = new ListTag();
		for (BlockPos p : this.pedestals) {
			CompoundTag c = new CompoundTag();
			c.putIntArray("P", new int[]{p.getX(), p.getY(), p.getZ()});
			list.add(c);
		}
		tag.put("Pedestals", list);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.entityData.set(DATA_PHASE, Math.max(1, tag.getInt("Phase")));
		this.entityData.set(DATA_HEADS, tag.contains("Heads") ? tag.getInt("Heads") : 0b111);
		this.entityData.set(DATA_INTRO, tag.getInt("Intro"));
		for (int i = 0; i < 3; i++) {
			this.headHealth[i] = tag.contains("HeadHealth" + i) ? tag.getFloat("HeadHealth" + i) : this.getMaxHealth() * 0.1F;
		}
		int[] a = tag.getIntArray("Altar");
		this.altar = a.length == 3 ? new BlockPos(a[0], a[1], a[2]) : null;
		this.pedestals.clear();
		ListTag list = tag.getList("Pedestals", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			int[] p = list.getCompound(i).getIntArray("P");
			if (p.length == 3) {
				this.pedestals.add(new BlockPos(p[0], p[1], p[2]));
			}
		}
		this.bossEvent.setName(this.getDisplayName());
		this.bossEvent.setColor(this.getPhase() == 1 ? BossEvent.BossBarColor.PURPLE : this.getPhase() == 2 ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.RED);
		if (this.getPhase() == 2) {
			// crystals are not saved: raise them again after a reload
			this.transitionPending = 2;
		}
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		super.setCustomName(name);
		this.bossEvent.setName(this.getDisplayName());
	}

	/** Test hook: skip the summoning animation. */
	public void skipIntro() {
		this.entityData.set(DATA_INTRO, 0);
	}

	/** Test hook: forces an action now. */
	public void forceAction(Action action, LivingEntity target) {
		this.startAction(action, target);
	}

	@Override
	public int lookTargetId() {
		return this.entityData.get(DATA_LOOK);
	}
}
