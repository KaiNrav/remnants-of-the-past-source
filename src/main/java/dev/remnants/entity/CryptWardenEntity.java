package dev.remnants.entity;

import dev.remnants.RemnantsConfig;
import dev.remnants.fx.CombatCues;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Crypt Warden, mini-boss of the Ossuary: a plate-armoured knight that stands as a stone statue on its plinth
 * until a player comes within 10 blocks in its sight (or strikes it), then shakes off the stone over 50 ticks.
 *
 * <p>Its tower shield turns every blow from the front (within {@link #GUARD_ANGLE} of where it faces): no damage,
 * sparks and a clang. Damage goes through only from <b>behind</b> (x1.25) or while it is <b>exposed</b> after a miss -
 * the greatsword stuck in the floor after a Crushing Blow that hit nothing, a stagger after a Sweep that hit nothing,
 * stunned after its Shield Charge ran into a wall. It turns slowly ({@link #TURN} degrees a tick, faster when enraged
 * below half health), so circling it works. Moves: Crushing Blow (wind-up 22, a disc in front, 12), Sweep (wind-up 14,
 * a 200-degree arc, 8 and a shove), Shield Charge (6..14 blocks off; 10 and a big shove, or a wall and a 70-tick stun).
 * Enraged: shorter wind-ups and a Crushing Blow that lands goes straight into a Sweep. With nobody to fight for 10 s
 * it walks back to its plinth and turns to stone again, healed.
 */
public class CryptWardenEntity extends Monster implements LookTarget {
	private static final EntityDataAccessor<Boolean> DATA_DORMANT = SynchedEntityData.defineId(CryptWardenEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(CryptWardenEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(CryptWardenEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_ENRAGED = SynchedEntityData.defineId(CryptWardenEntity.class, EntityDataSerializers.BOOLEAN);
	/** How many ticks the current move lasts (wind-ups vary: enraged x0.75, combo x0.7): the client fits its clip to it. */
	private static final EntityDataAccessor<Integer> DATA_ANIM_LENGTH = SynchedEntityData.defineId(CryptWardenEntity.class, EntityDataSerializers.INT);
	public static final int ANIM_NONE = 0, ANIM_AWAKEN = 1, ANIM_SLAM_WINDUP = 2, ANIM_SLAM = 3, ANIM_STUCK = 4, ANIM_SWEEP_WINDUP = 5,
		ANIM_SWEEP = 6, ANIM_STAGGER = 7, ANIM_CHARGE_WINDUP = 8, ANIM_CHARGE = 9, ANIM_STUNNED = 10, ANIM_RECOVER = 11;
	public static final int AWAKEN_TICKS = 50, SLAM_WINDUP = 22, SWEEP_WINDUP = 14, CHARGE_WINDUP = 16, CHARGE_MAX = 30;
	public static final int STUCK_TICKS = 50, STAGGER_TICKS = 20, STUNNED_TICKS = 70;
	public static final float TURN = 3.5F, TURN_ENRAGED = 5.0F, GUARD_ANGLE = 110.0F;
	public static final double SLAM_REACH = 2.2, SLAM_RADIUS = 2.2, SWEEP_RADIUS = 3.4, SWEEP_HALF_ARC = 100.0;

	private final ServerBossEvent bossEvent = new dev.remnants.bossbar.StyledBossEvent(Component.translatable("entity.remnants_of_the_past.crypt_warden"),
		BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10, dev.remnants.bossbar.BossStyle.WARDEN);
	private int animTicks, animLength;
	private int slamCooldown, sweepCooldown, chargeCooldown = 60;
	private int lostTicks;
	private boolean struck;
	@Nullable
	private BlockPos home;
	private float homeYaw;
	private final Set<UUID> hinted = new HashSet<>();
	/** Client: when the current animation began, and the one before it (the models cross-fade between them). */
	public int clientAnimStart, clientAnimChanged, clientPrevAnimStart;
	public int clientAnim, clientPrevAnim;
	/** Client: real length of the current / previous move in ticks, and what each recovery recovers from. */
	public int clientAnimLength, clientPrevAnimLength, clientRecoverFrom, clientPrevRecoverFrom;

	public CryptWardenEntity(EntityType<? extends CryptWardenEntity> type, Level level) {
		super(type, level);
		this.xpReward = 80;
		this.setPersistenceRequired();
		this.moveControl = new SlowTurnMoveControl(this);
		this.lookControl = new LookControl(this) {
			@Override
			public void tick() {
				// the head never turns on its own: what it faces is where its shield is
			}
		};
		this.bossEvent.setVisible(false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 160.0)
			.add(Attributes.ARMOR, 10.0)
			.add(Attributes.ARMOR_TOUGHNESS, 4.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.MOVEMENT_SPEED, 0.23)
			.add(Attributes.ATTACK_DAMAGE, 12.0)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_LOOK, -1);
		builder.define(DATA_DORMANT, true);
		builder.define(DATA_ANIM, ANIM_NONE);
		builder.define(DATA_ENRAGED, false);
		builder.define(DATA_ANIM_LENGTH, 0);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_ANIM.equals(key)) {
			this.clientPrevAnim = this.clientAnim;
			this.clientPrevAnimStart = this.clientAnimStart;
			this.clientPrevAnimLength = this.clientAnimLength;
			this.clientPrevRecoverFrom = this.clientRecoverFrom;
			int next = this.entityData.get(DATA_ANIM);
			this.clientRecoverFrom = next == ANIM_RECOVER ? this.clientAnim : ANIM_NONE;
			this.clientAnim = next;
			this.clientAnimStart = this.tickCount;
			this.clientAnimChanged = this.tickCount;
			this.clientAnimLength = this.entityData.get(DATA_ANIM_LENGTH);
		} else if (DATA_ANIM_LENGTH.equals(key)) {
			this.clientAnimLength = this.entityData.get(DATA_ANIM_LENGTH);
		}
	}

	@Override
	protected BodyRotationControl createBodyControl() {
		return new BodyRotationControl(this) {
			@Override
			public void clientTick() {
				CryptWardenEntity.this.yBodyRot = CryptWardenEntity.this.getYRot();
				CryptWardenEntity.this.yHeadRot = CryptWardenEntity.this.getYRot();
			}
		};
	}

	@Override
	protected void registerGoals() {
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, p -> !this.isDormant()));
	}

	// ---- state ---------------------------------------------------------------------------------

	public boolean isDormant() {
		return this.entityData.get(DATA_DORMANT);
	}

	public int getAnim() {
		return this.entityData.get(DATA_ANIM);
	}

	public boolean isEnraged() {
		return this.entityData.get(DATA_ENRAGED);
	}

	private void setAnim(int anim, int length) {
		this.entityData.set(DATA_ANIM_LENGTH, length);   // before DATA_ANIM: the client reads it when the move changes
		this.entityData.set(DATA_ANIM, anim);
		this.animTicks = 0;
		this.animLength = length;
		this.telegraph(anim, length);
	}

	/** Art 2.0 hit feel: where and when each wind-up will strike, on the ground, filling up until the damage tick. */
	private void telegraph(int anim, int length) {
		switch (anim) {
			case ANIM_SLAM_WINDUP -> CombatCues.discAttached(this, (float) SLAM_REACH, (float) SLAM_RADIUS, length);
			case ANIM_SWEEP_WINDUP -> CombatCues.arc(this, this.position(), this.getYRot(), (float) SWEEP_RADIUS, (float) (SWEEP_HALF_ARC * 2), length);
			case ANIM_CHARGE_WINDUP -> CombatCues.lineAttached(this, 0.6F, 12.0F, 1.8F, length);
			case ANIM_NONE, ANIM_STUNNED -> CombatCues.clear(this);
			default -> {
			}
		}
	}

	/** After a miss (or a wall): the only time its front can be hurt. */
	public boolean isExposed() {
		int a = this.getAnim();
		return a == ANIM_STUCK || a == ANIM_STAGGER || a == ANIM_STUNNED;
	}

	public float turnRate() {
		return this.isEnraged() ? TURN_ENRAGED : TURN;
	}

	private float windup(int base) {
		return this.isEnraged() ? base * 0.75F : base;
	}

	private float damage(float base) {
		return (float) (base * RemnantsConfig.get().wardenDamageMultiplier);
	}

	/** Test hook: make it start the given move right now. */
	public void forceAnim(int anim) {
		switch (anim) {
			case ANIM_SLAM_WINDUP -> this.setAnim(ANIM_SLAM_WINDUP, Math.round(this.windup(SLAM_WINDUP)));
			case ANIM_SWEEP_WINDUP -> this.setAnim(ANIM_SWEEP_WINDUP, Math.round(this.windup(SWEEP_WINDUP)));
			case ANIM_CHARGE_WINDUP -> this.setAnim(ANIM_CHARGE_WINDUP, Math.round(this.windup(CHARGE_WINDUP)));
			case ANIM_STUCK -> this.setAnim(ANIM_STUCK, STUCK_TICKS);
			default -> this.setAnim(anim, 20);
		}
	}

	// ---- waking --------------------------------------------------------------------------------

	public void awaken(@Nullable LivingEntity by) {
		if (!this.isDormant()) {
			return;
		}
		this.entityData.set(DATA_DORMANT, false);
		this.setAnim(ANIM_AWAKEN, AWAKEN_TICKS);
		this.playSound(SoundEvents.IRON_GOLEM_REPAIR, 2.0F, 0.4F);
		this.playSound(SoundEvents.STONE_BREAK, 2.0F, 0.5F);
		if (by != null && !(by instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			this.setTarget(by);
		}
	}

	/** Client display copies (the Codex portrait): armour, not stone. */
	public void showAwake() {
		this.entityData.set(DATA_DORMANT, false);
	}

	/** Back on the plinth: stone again, healed. */
	public void petrify() {
		CombatCues.clear(this);
		this.entityData.set(DATA_DORMANT, true);
		this.setAnim(ANIM_NONE, 0);
		this.setTarget(null);
		this.setHealth(this.getMaxHealth());
		this.entityData.set(DATA_ENRAGED, false);
		this.getNavigation().stop();
		this.setYRot(this.homeYaw);
		this.yBodyRot = this.homeYaw;
		this.yHeadRot = this.homeYaw;
		this.bossEvent.removeAllPlayers();
		this.hinted.clear();
		this.playSound(SoundEvents.STONE_PLACE, 1.5F, 0.5F);
	}

	// ---- the fight -----------------------------------------------------------------------------

	@Override
	protected void customServerAiStep() {
		this.entityData.set(DATA_LOOK, this.getTarget() == null ? -1 : this.getTarget().getId());
		super.customServerAiStep();
		ServerLevel level = (ServerLevel) this.level();
		if (this.home == null) {
			this.home = this.blockPosition();
			this.homeYaw = this.getYRot();
		}
		this.animTicks++;
		if (this.isDormant()) {
			this.getNavigation().stop();
			this.setDeltaMovement(0, Math.min(0, this.getDeltaMovement().y), 0);
			if (this.tickCount % 10 == 0) {
				Player near = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), 10.0,
					p -> !p.isSpectator() && !((Player) p).isCreative() && this.hasLineOfSight(p));
				if (near != null) {
					this.awaken(near);
				}
			}
			return;
		}
		this.updateBossBar(level);
		if (this.slamCooldown > 0) {
			this.slamCooldown--;
		}
		if (this.sweepCooldown > 0) {
			this.sweepCooldown--;
		}
		if (this.chargeCooldown > 0) {
			this.chargeCooldown--;
		}
		if (!this.isEnraged() && this.getHealth() < this.getMaxHealth() * 0.5F) {
			this.entityData.set(DATA_ENRAGED, true);
			this.playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.6F);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 2.4, this.getZ(), 20, 0.4, 0.3, 0.4, 0.03);
		}
		LivingEntity target = this.getTarget();
		if (target != null && (!target.isAlive() || (target instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			this.setTarget(null);
			target = null;
		}
		int anim = this.getAnim();
		switch (anim) {
			case ANIM_AWAKEN -> {
				this.getNavigation().stop();
				if (this.animTicks % 4 == 0) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState()),
						this.getX(), this.getY() + 1.5, this.getZ(), 12, 0.4, 0.9, 0.4, 0.1);
				}
				if (this.animTicks >= this.animLength) {
					this.setAnim(ANIM_NONE, 0);
					this.playSound(SoundEvents.IRON_GOLEM_HURT, 2.0F, 0.4F);
				}
				return;
			}
			case ANIM_SLAM_WINDUP -> {
				this.getNavigation().stop();
				if (target != null && this.animTicks < this.animLength / 2) {
					this.turnTowards(target, this.turnRate() * 0.5F);
				}
				if (this.animTicks >= this.animLength) {
					this.setAnim(ANIM_SLAM, 6);
					this.slam(level, target);
				}
				return;
			}
			case ANIM_SWEEP_WINDUP -> {
				this.getNavigation().stop();
				if (this.animTicks >= this.animLength) {
					this.setAnim(ANIM_SWEEP, 6);
					this.sweep(level);
				}
				return;
			}
			case ANIM_CHARGE_WINDUP -> {
				this.getNavigation().stop();
				if (target != null) {
					this.turnTowards(target, this.turnRate());
				}
				if (this.animTicks % 5 == 0) {
					this.playSound(SoundEvents.IRON_GOLEM_STEP, 1.5F, 0.6F);
				}
				if (this.animTicks >= this.animLength) {
					this.setAnim(ANIM_CHARGE, CHARGE_MAX);
					this.playSound(SoundEvents.RAVAGER_ROAR, 1.2F, 0.9F);
				}
				return;
			}
			case ANIM_CHARGE -> {
				this.tickCharge(level, target);
				return;
			}
			case ANIM_SLAM, ANIM_SWEEP, ANIM_STUCK, ANIM_STAGGER, ANIM_STUNNED, ANIM_RECOVER -> {
				this.getNavigation().stop();
				if (anim == ANIM_STUNNED && this.animTicks % 5 == 0) {
					level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 3.0, this.getZ(), 4, 0.4, 0.1, 0.4, 0.05);
				}
				if (this.animTicks >= this.animLength) {
					this.afterMove(level, anim, target);
				}
				return;
			}
			default -> {
			}
		}
		if (target == null) {
			this.wanderHome();
			return;
		}
		this.lostTicks = 0;
		this.choose(level, target);
	}

	private void afterMove(ServerLevel level, int anim, @Nullable LivingEntity target) {
		if (anim == ANIM_SLAM) {
			if (!this.struck) {
				this.setAnim(ANIM_STUCK, STUCK_TICKS);   // the greatsword is stuck in the floor
				this.playSound(SoundEvents.CHAIN_HIT, 1.5F, 0.5F);
			} else if (this.isEnraged() && target != null && this.distanceTo(target) < SWEEP_RADIUS + 0.5) {
				this.setAnim(ANIM_SWEEP_WINDUP, Math.round(this.windup(SWEEP_WINDUP) * 0.7F));   // the combo
			} else {
				this.setAnim(ANIM_RECOVER, 15);
			}
		} else if (anim == ANIM_SWEEP) {
			if (!this.struck) {
				this.setAnim(ANIM_STAGGER, STAGGER_TICKS);
			} else {
				this.setAnim(ANIM_RECOVER, 12);
			}
		} else {
			this.setAnim(ANIM_NONE, 0);
		}
	}

	private void choose(ServerLevel level, LivingEntity target) {
		double dist = this.distanceTo(target);
		float off = this.angleTo(target.position());
		if (dist <= SWEEP_RADIUS && off < 45.0F && (this.slamCooldown <= 0 || this.sweepCooldown <= 0)) {
			boolean slam = this.slamCooldown <= 0 && (this.sweepCooldown > 0 || this.random.nextFloat() < 0.55F);
			if (slam) {
				this.setAnim(ANIM_SLAM_WINDUP, Math.round(this.windup(SLAM_WINDUP)));
				this.slamCooldown = 50;
				this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.5F, 0.5F);
			} else {
				this.setAnim(ANIM_SWEEP_WINDUP, Math.round(this.windup(SWEEP_WINDUP)));
				this.sweepCooldown = 40;
				this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.5F);
			}
			return;
		}
		if (dist <= SWEEP_RADIUS && off < SWEEP_HALF_ARC - 10.0F && this.sweepCooldown <= 0) {
			this.setAnim(ANIM_SWEEP_WINDUP, Math.round(this.windup(SWEEP_WINDUP)));
			this.sweepCooldown = 40;
			this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.5F);
			return;
		}
		if (dist >= 6.0 && dist <= 14.0 && this.chargeCooldown <= 0 && off < 25.0F && this.hasLineOfSight(target)) {
			this.setAnim(ANIM_CHARGE_WINDUP, Math.round(this.windup(CHARGE_WINDUP)));
			this.chargeCooldown = 140;
			return;
		}
		if (dist > 2.6) {
			if (this.tickCount % 10 == 0 || this.getNavigation().isDone()) {
				this.getNavigation().moveTo(target, 1.0);
			}
		} else {
			this.getNavigation().stop();
		}
		this.turnTowards(target, this.turnRate());
	}

	private void wanderHome() {
		if (++this.lostTicks < 200 || this.home == null) {
			this.getNavigation().stop();
			return;
		}
		Vec3 h = Vec3.atBottomCenterOf(this.home);
		if (this.position().distanceToSqr(h) < 1.5) {
			this.moveTo(h.x, h.y, h.z, this.homeYaw, 0.0F);
			this.petrify();
		} else if (this.tickCount % 20 == 0) {
			this.getNavigation().moveTo(h.x, h.y, h.z, 0.8);
		}
	}

	private Vec3 forward() {
		return Vec3.directionFromRotation(0, this.getYRot());
	}

	/** Degrees between where it faces and {@code at} (horizontal), 0..180. */
	public float angleTo(Vec3 at) {
		Vec3 to = new Vec3(at.x - this.getX(), 0, at.z - this.getZ());
		if (to.lengthSqr() < 1.0E-6) {
			return 0.0F;
		}
		double dot = Mth.clamp(this.forward().dot(to.normalize()), -1.0, 1.0);
		return (float) Math.toDegrees(Math.acos(dot));
	}

	private void turnTowards(Entity target, float rate) {
		double dx = target.getX() - this.getX(), dz = target.getZ() - this.getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, rate));
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
	}

	private boolean foe(LivingEntity e) {
		return e != this && e.isAlive() && !(e instanceof CryptWardenEntity) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	/** Crushing Blow: a disc of 2.2 blocks, 2.2 in front. */
	private void slam(ServerLevel level, @Nullable LivingEntity target) {
		Vec3 c = this.position().add(this.forward().scale(SLAM_REACH));
		this.struck = false;
		LivingEntity first = null;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(SLAM_RADIUS + 0.5, 2.0, SLAM_RADIUS + 0.5), this::foe)) {
			double dx = e.getX() - c.x, dz = e.getZ() - c.z;
			if (Math.sqrt(dx * dx + dz * dz) <= SLAM_RADIUS + e.getBbWidth() / 2 && Math.abs(e.getY() - this.getY()) < 2.0) {
				if (e.hurt(this.damageSources().mobAttack(this), this.damage(12.0F))) {
					e.push(0, 0.35, 0);
					e.hurtMarked = true;
				}
				this.struck = true;
				first = first == null ? e : first;
			}
		}
		CombatCues.impact(this, first, new Vec3(c.x, this.getY(), c.z), this.struck ? 0.9F : 0.65F, (float) SLAM_RADIUS + 0.6F, 3);
		BlockPos ground = BlockPos.containing(c.x, this.getY() - 0.5, c.z);
		BlockState gs = level.getBlockState(ground);
		if (!gs.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, gs), c.x, this.getY() + 0.1, c.z, 40, 1.0, 0.1, 1.0, 0.2);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, this.getY() + 0.2, c.z, 1, 0, 0, 0, 0);
		this.playSound(SoundEvents.ANVIL_LAND, 1.4F, 0.5F);
		this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 1.4F);
	}

	/** Sweep: a 200-degree arc of 3.4 blocks. */
	private void sweep(ServerLevel level) {
		this.struck = false;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(SWEEP_RADIUS, 1.0, SWEEP_RADIUS), this::foe)) {
			if (this.distanceTo(e) <= SWEEP_RADIUS + e.getBbWidth() / 2 && this.angleTo(e.position()) <= SWEEP_HALF_ARC) {
				if (e.hurt(this.damageSources().mobAttack(this), this.damage(8.0F))) {
					e.knockback(1.0, this.getX() - e.getX(), this.getZ() - e.getZ());
					e.hurtMarked = true;
				}
				if (!this.struck) {
					CombatCues.impact(this, e, e.position().add(0, e.getBbHeight() * 0.5, 0), 0.5F, 0.0F, 2);
				}
				this.struck = true;
			}
		}
		Vec3 f = this.forward();
		for (int i = -4; i <= 4; i++) {
			Vec3 d = f.yRot((float) Math.toRadians(i * 22.0));
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX() + d.x * 2.4, this.getY() + 1.4, this.getZ() + d.z * 2.4, 1, 0, 0, 0, 0);
		}
		this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.5F);
	}

	private void tickCharge(ServerLevel level, @Nullable LivingEntity target) {
		this.getNavigation().stop();
		if (target != null && this.animTicks < 4) {
			this.turnTowards(target, this.turnRate());
		}
		Vec3 f = this.forward();
		this.setDeltaMovement(f.x * 0.5, this.getDeltaMovement().y, f.z * 0.5);
		this.hasImpulse = true;
		if (this.animTicks % 3 == 0) {
			this.playSound(SoundEvents.IRON_GOLEM_STEP, 1.5F, 0.8F);
			level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.1, this.getZ(), 2, 0.3, 0, 0.3, 0.01);
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().move(f.scale(0.6)).inflate(0.3), this::foe)) {
			if (e.hurt(this.damageSources().mobAttack(this), this.damage(10.0F))) {
				e.knockback(2.0, -f.x, -f.z);
				e.push(0, 0.4, 0);
				e.hurtMarked = true;
			}
			CombatCues.impact(this, e, e.position().add(0, e.getBbHeight() * 0.5, 0), 0.75F, 1.2F, 3);
			this.playSound(SoundEvents.SHIELD_BLOCK, 1.5F, 0.6F);
			this.setDeltaMovement(Vec3.ZERO);
			this.setAnim(ANIM_RECOVER, 20);
			return;
		}
		if (this.horizontalCollision && this.animTicks > 3) {
			this.setDeltaMovement(Vec3.ZERO);
			CombatCues.impact(this, null, this.position().add(f.x * 0.9, 1.5, f.z * 0.9), 0.85F, 0.0F, 4);
			this.setAnim(ANIM_STUNNED, STUNNED_TICKS);
			this.playSound(SoundEvents.ANVIL_LAND, 1.5F, 0.4F);
			this.playSound(SoundEvents.STONE_BREAK, 1.5F, 0.6F);
			level.sendParticles(ParticleTypes.EXPLOSION, this.getX() + f.x, this.getY() + 1.5, this.getZ() + f.z, 1, 0, 0, 0, 0);
			return;
		}
		if (this.animTicks >= this.animLength) {
			this.setDeltaMovement(Vec3.ZERO);
			this.setAnim(ANIM_RECOVER, 15);
		}
	}

	// ---- the shield ----------------------------------------------------------------------------

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (this.level().isClientSide || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurt(source, amount);
		}
		if (this.isDormant() || this.getAnim() == ANIM_AWAKEN) {
			if (this.isDormant() && source.getEntity() instanceof LivingEntity by) {
				this.awaken(by);
			}
			this.playSound(SoundEvents.STONE_HIT, 1.2F, 0.6F);
			return false;
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			return super.hurt(source, amount * 0.5F);
		}
		Vec3 from = source.getSourcePosition();
		if (from == null) {
			return super.hurt(source, amount * 0.5F);   // fire, magic, a fall: no side to guard
		}
		float off = this.angleTo(from);
		if (off > GUARD_ANGLE) {
			if (this.level() instanceof ServerLevel server) {
				server.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.6, this.getZ(), 10, 0.3, 0.4, 0.3, 0.3);
			}
			return super.hurt(source, amount * 1.25F);
		}
		if (this.isExposed()) {
			return super.hurt(source, amount);
		}
		this.guard(source);
		return false;
	}

	/** The shield takes it: sparks, a clang, a shove back, and (once per fight) a hint for whoever struck. */
	private void guard(DamageSource source) {
		this.playSound(SoundEvents.SHIELD_BLOCK, 1.3F, 0.7F);
		this.playSound(SoundEvents.ANVIL_PLACE, 0.4F, 1.8F);
		Vec3 f = this.forward();
		CombatCues.impact(this, source.getDirectEntity(), this.position().add(f.x * 0.9, 1.6, f.z * 0.9), 0.25F, 0.0F, 2);
		if (this.level() instanceof ServerLevel server) {
			server.sendParticles(ParticleTypes.CRIT, this.getX() + f.x * 0.9, this.getY() + 1.6, this.getZ() + f.z * 0.9, 8, 0.25, 0.4, 0.25, 0.3);
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX() + f.x * 0.9, this.getY() + 1.6, this.getZ() + f.z * 0.9, 4, 0.2, 0.3, 0.2, 0.2);
		}
		if (source.getDirectEntity() instanceof LivingEntity attacker && source.getEntity() == attacker) {
			attacker.knockback(0.5, this.getX() - attacker.getX(), this.getZ() - attacker.getZ());
			attacker.hurtMarked = true;
		}
		if (source.getEntity() instanceof ServerPlayer player && this.hinted.add(player.getUUID())) {
			player.displayClientMessage(Component.translatable("message.remnants.warden.guard").withStyle(ChatFormatting.GRAY), true);
		}
	}

	@Override
	public void knockback(double strength, double x, double z) {
		// a statue in plate: never pushed around
	}

	// ---- boss bar ------------------------------------------------------------------------------

	private void updateBossBar(ServerLevel level) {
		this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
		this.bossEvent.setColor(this.isEnraged() ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.WHITE);
		this.bossEvent.setVisible(true);
		if (this.tickCount % 10 == 0) {
			for (ServerPlayer p : level.players()) {
				boolean near = p.distanceToSqr(this) < 32 * 32 && !p.isSpectator();
				if (near) {
					this.bossEvent.addPlayer(p);
				} else {
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
		super.remove(reason);
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		super.setCustomName(name);
		this.bossEvent.setName(this.getDisplayName());
	}

	// ---- plumbing ------------------------------------------------------------------------------

	@Override
	@Nullable
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, @Nullable SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, type, data);
		AttributeInstance hp = this.getAttribute(Attributes.MAX_HEALTH);
		if (hp != null) {
			hp.setBaseValue(RemnantsConfig.get().wardenHealth);
		}
		this.setHealth(this.getMaxHealth());
		this.home = this.blockPosition();
		this.homeYaw = this.getYRot();
		return out;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.isDormant() ? null : SoundEvents.IRON_GOLEM_STEP;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.55F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.IRON_GOLEM_STEP, 1.0F, 0.7F);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("Dormant", this.isDormant());
		tag.putBoolean("Enraged", this.isEnraged());
		if (this.home != null) {
			tag.putIntArray("Home", new int[] {this.home.getX(), this.home.getY(), this.home.getZ()});
			tag.putFloat("HomeYaw", this.homeYaw);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.entityData.set(DATA_DORMANT, !tag.contains("Dormant") || tag.getBoolean("Dormant"));
		this.entityData.set(DATA_ENRAGED, tag.getBoolean("Enraged"));
		int[] h = tag.getIntArray("Home");
		if (h.length == 3) {
			this.home = new BlockPos(h[0], h[1], h[2]);
			this.homeYaw = tag.getFloat("HomeYaw");
		}
		this.bossEvent.setName(this.getDisplayName());
	}

	/** Walks only where it faces, and turns slowly: what makes circling behind it possible. */
	static final class SlowTurnMoveControl extends MoveControl {
		SlowTurnMoveControl(CryptWardenEntity warden) {
			super(warden);
		}

		@Override
		public void tick() {
			if (this.operation != Operation.MOVE_TO) {
				super.tick();
				return;
			}
			this.operation = Operation.WAIT;
			double dx = this.wantedX - this.mob.getX(), dz = this.wantedZ - this.mob.getZ(), dy = this.wantedY - this.mob.getY();
			if (dx * dx + dy * dy + dz * dz < 2.5E-7) {
				this.mob.setZza(0.0F);
				return;
			}
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			float rate = ((CryptWardenEntity) this.mob).turnRate();
			this.mob.setYRot(Mth.approachDegrees(this.mob.getYRot(), yaw, rate));
			float off = Math.abs(Mth.wrapDegrees(yaw - this.mob.getYRot()));
			// turning on the spot: barely any forward speed until it faces the way it wants to go
			float align = off > 60.0F ? 0.25F : 1.0F - off / 120.0F;
			this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)) * align);
			if (dy > this.mob.maxUpStep() && dx * dx + dz * dz < Math.max(1.0F, this.mob.getBbWidth())) {
				this.mob.getJumpControl().jump();
				this.operation = Operation.JUMPING;
			}
		}
	}

	@Override
	public int lookTargetId() {
		return this.entityData.get(DATA_LOOK);
	}
}
