package dev.remnants.entity;

import dev.remnants.fx.CombatCues;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Grave Mimic: something that lives in an Ossuary Urn, or is one. Asleep it sits on the block grid, turned to a
 * quarter, and is drawn with the urn's own block models, so it cannot be told from the real thing by its look - only by
 * its tells: now and then the lid "breathes" (lifts a texel), a Soul Dowser trembles near it, and a Hand Bell makes it
 * flinch and glow ({@link #flinch}) without waking it.
 *
 * <p>It wakes when a player comes within 2.5 blocks (1.5 sneaking), touches it or hits it. Awake it hops after its prey,
 * bites, leaps on it and holds on (Slowness, a bite every half second, a hit makes it let go), and spits bones from
 * afar. With nothing to chase for 10 s it settles down as an urn again, wherever it is.
 */
public class GraveMimicEntity extends Monster implements LookTarget {
	private static final EntityDataAccessor<Boolean> DATA_DORMANT = SynchedEntityData.defineId(GraveMimicEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(GraveMimicEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(GraveMimicEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_QUARTER = SynchedEntityData.defineId(GraveMimicEntity.class, EntityDataSerializers.INT);
	public static final int ANIM_NONE = 0, ANIM_BITE = 1, ANIM_LEAP = 2, ANIM_SPIT = 3, ANIM_FLINCH = 4, ANIM_HOLD = 5, ANIM_WAKE = 6, ANIM_CROUCH = 7;
	public static final double WAKE_RADIUS = 2.5, WAKE_RADIUS_SNEAKING = 1.5;
	public static final int HOLD_TICKS = 30, SETTLE_TICKS = 200;
	/** Art 2.0: it squats for this long, lane on the ground, before it leaps (it used to leap with no warning). */
	public static final int CROUCH_TICKS = 8;

	private int animTicks;
	private int biteCooldown, leapCooldown = 40, spitCooldown = 30, hopCooldown;
	private int idleTicks;
	@Nullable
	private LivingEntity held;
	/** Client: when the current animation began, and the one before it (the models cross-fade between them). */
	public int clientAnimStart, clientAnimChanged, clientPrevAnimStart;
	public int clientAnim, clientPrevAnim;

	public GraveMimicEntity(EntityType<? extends GraveMimicEntity> type, Level level) {
		super(type, level);
		this.xpReward = 12;
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 30.0)
			.add(Attributes.ARMOR, 4.0)
			.add(Attributes.ATTACK_DAMAGE, 6.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FOLLOW_RANGE, 20.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_LOOK, -1);
		builder.define(DATA_DORMANT, true);
		builder.define(DATA_ANIM, ANIM_NONE);
		builder.define(DATA_QUARTER, 0);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_DORMANT.equals(key)) {
			this.refreshDimensions();
		}
		if (DATA_ANIM.equals(key)) {
			this.clientPrevAnim = this.clientAnim;
			this.clientPrevAnimStart = this.clientAnimStart;
			this.clientAnim = this.entityData.get(DATA_ANIM);
			this.clientAnimStart = this.tickCount;
			this.clientAnimChanged = this.tickCount;
		}
	}

	/** Asleep it is an urn (a block's footprint); awake it stands on its legs. */
	@Override
	protected net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
		return this.isDormant() ? super.getDefaultDimensions(pose)
			: net.minecraft.world.entity.EntityDimensions.scalable(0.95F, 1.35F).withEyeHeight(1.0F);
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

	private void setAnim(int anim) {
		this.entityData.set(DATA_ANIM, anim);
		this.animTicks = 0;
		if (anim == ANIM_CROUCH) {
			CombatCues.lineAttached(this, 0.4F, 5.5F, 1.2F, CROUCH_TICKS);
		} else if (anim == ANIM_NONE) {
			CombatCues.clear(this);
		}
	}

	/** The quarter turn it sleeps in (0 = facing south, like a block's default), so it lines up with real urns. */
	public int quarter() {
		return this.entityData.get(DATA_QUARTER);
	}

	/** Settles down where it stands: snapped to the block grid, still, an urn. */
	public void sleep() {
		BlockPos p = this.blockPosition();
		int q = Math.floorMod(Math.round(this.getYRot() / 90.0F), 4);
		this.entityData.set(DATA_QUARTER, q);
		this.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, q * 90.0F, 0.0F);
		this.setYHeadRot(q * 90.0F);
		this.yBodyRot = q * 90.0F;
		this.setDeltaMovement(Vec3.ZERO);
		this.entityData.set(DATA_DORMANT, true);
		this.setAnim(ANIM_NONE);
		this.setTarget(null);
		this.release();
		this.getNavigation().stop();
	}

	public void wake(@Nullable LivingEntity by) {
		if (!this.isDormant()) {
			return;
		}
		this.entityData.set(DATA_DORMANT, false);
		this.setAnim(ANIM_WAKE);
		this.idleTicks = 0;
		this.biteCooldown = 6;
		this.playSound(SoundEvents.DECORATED_POT_SHATTER, 1.0F, 0.6F);
		this.playSound(SoundEvents.RAVAGER_ROAR, 0.6F, 2.0F);
		if (this.level() instanceof ServerLevel server) {
			server.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.9, this.getZ(), 10, 0.2, 0.1, 0.2, 0.02);
			server.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 0.9, this.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
		}
		if (by != null && !(by instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			this.setTarget(by);
		}
	}

	/** Client display copies (the Codex portrait): show it awake, lid open. */
	public void showAwake() {
		this.entityData.set(DATA_DORMANT, false);
		this.entityData.set(DATA_ANIM, ANIM_SPIT);
	}

	/** A Hand Bell rang: a sleeping mimic shudders and glows, but stays an urn. */
	public void flinch() {
		if (this.isDormant()) {
			this.setAnim(ANIM_FLINCH);
			this.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));
			this.playSound(SoundEvents.DECORATED_POT_HIT, 1.0F, 0.6F);
		}
	}

	// ---- life ----------------------------------------------------------------------------------

	@Override
	public void tick() {
		if (this.isDormant()) {
			// asleep it is furniture: no drifting, no turning
			Vec3 v = this.getDeltaMovement();
			this.setDeltaMovement(0, Math.min(0, v.y), 0);
			this.setYRot(this.quarter() * 90.0F);
			this.yBodyRot = this.getYRot();
			this.yHeadRot = this.getYRot();
		}
		super.tick();
	}

	@Override
	protected void customServerAiStep() {
		this.entityData.set(DATA_LOOK, this.getTarget() == null ? -1 : this.getTarget().getId());
		super.customServerAiStep();
		ServerLevel level = (ServerLevel) this.level();
		this.animTicks++;
		if (this.isDormant()) {
			this.getNavigation().stop();
			if (this.getAnim() == ANIM_FLINCH && this.animTicks > 20) {
				this.setAnim(ANIM_NONE);
			}
			if (this.tickCount % 5 == 0) {
				Player near = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), WAKE_RADIUS,
					p -> !p.isSpectator() && !((Player) p).isCreative()
						&& (!p.isCrouching() || p.distanceTo(this) <= WAKE_RADIUS_SNEAKING) && this.hasLineOfSight(p));
				if (near != null) {
					this.wake(near);
				}
			}
			return;
		}
		if (this.biteCooldown > 0) {
			this.biteCooldown--;
		}
		if (this.leapCooldown > 0) {
			this.leapCooldown--;
		}
		if (this.spitCooldown > 0) {
			this.spitCooldown--;
		}
		if (this.hopCooldown > 0) {
			this.hopCooldown--;
		}
		LivingEntity target = this.getTarget();
		if (target != null && (!target.isAlive() || (target instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			this.setTarget(null);
			target = null;
		}
		if (target == null) {
			this.release();
			if (++this.idleTicks > SETTLE_TICKS && this.onGround() && this.getAnim() != ANIM_LEAP) {
				this.sleep();
				this.heal(this.getMaxHealth());
			}
			return;
		}
		this.idleTicks = 0;
		this.face(target, 20.0F);
		int anim = this.getAnim();
		double dist = this.distanceTo(target);
		switch (anim) {
			case ANIM_WAKE -> {
				if (this.animTicks >= 8) {
					this.setAnim(ANIM_NONE);
				}
			}
			case ANIM_BITE -> {
				if (this.animTicks == 4 && dist < 2.2) {
					this.doHurtTarget(target);
					CombatCues.impact(this, target, target.position().add(0, target.getBbHeight() * 0.5, 0), 0.3F, 0.0F, 2);
				} else if (this.animTicks >= 10) {
					this.setAnim(ANIM_NONE);
				}
			}
			case ANIM_CROUCH -> {
				this.getNavigation().stop();
				this.setDeltaMovement(this.getDeltaMovement().multiply(0.3, 1.0, 0.3));
				if (this.animTicks >= CROUCH_TICKS) {
					Vec3 to = new Vec3(target.getX() - this.getX(), 0, target.getZ() - this.getZ()).normalize();
					this.setDeltaMovement(to.x * 0.85, 0.48, to.z * 0.85);
					this.hasImpulse = true;
					this.setAnim(ANIM_LEAP);
					this.playSound(SoundEvents.RAVAGER_ROAR, 0.5F, 1.8F);
				}
			}
			case ANIM_LEAP -> {
				if (this.getBoundingBox().inflate(0.35).intersects(target.getBoundingBox())) {
					this.grab(target);
				} else if (this.animTicks > 5 && this.onGround() || this.animTicks > 40) {
					this.setAnim(ANIM_NONE);
				}
			}
			case ANIM_HOLD -> this.tickHold(target);
			case ANIM_SPIT -> {
				this.getNavigation().stop();
				if (this.animTicks == 6) {
					this.spit(level, target);
				} else if (this.animTicks >= 14) {
					this.setAnim(ANIM_NONE);
				}
			}
			default -> this.choose(target, dist);
		}
	}

	private void choose(LivingEntity target, double dist) {
		boolean sees = this.hasLineOfSight(target);
		if (dist < 1.9 && this.biteCooldown <= 0) {
			this.setAnim(ANIM_BITE);
			this.biteCooldown = 16;
			this.playSound(SoundEvents.DECORATED_POT_HIT, 1.0F, 0.5F);
		} else if (dist >= 3.0 && dist <= 6.0 && this.leapCooldown <= 0 && this.onGround() && sees) {
			this.setAnim(ANIM_CROUCH);
			this.leapCooldown = 100;
			this.playSound(SoundEvents.DECORATED_POT_HIT, 0.8F, 0.5F);
		} else if (dist >= 4.0 && dist <= 10.0 && this.spitCooldown <= 0 && sees) {
			this.setAnim(ANIM_SPIT);
			this.spitCooldown = 80;
		} else if (this.onGround() && this.hopCooldown <= 0 && dist > 1.4) {
			// it hops like a pot that learned to walk
			Vec3 to = new Vec3(target.getX() - this.getX(), 0, target.getZ() - this.getZ()).normalize();
			this.setDeltaMovement(to.x * 0.3, 0.38, to.z * 0.3);
			this.hasImpulse = true;
			this.hopCooldown = 9;
			this.playSound(SoundEvents.DECORATED_POT_STEP, 0.8F, 0.7F + this.random.nextFloat() * 0.2F);
		}
	}

	private void face(Entity target, float rate) {
		double dx = target.getX() - this.getX(), dz = target.getZ() - this.getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, rate));
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
	}

	private void grab(LivingEntity target) {
		this.held = target;
		this.setAnim(ANIM_HOLD);
		this.setDeltaMovement(Vec3.ZERO);
		this.playSound(SoundEvents.DECORATED_POT_HIT, 1.2F, 0.4F);
		this.doHurtTarget(target);
		CombatCues.impact(this, target, target.position().add(0, target.getBbHeight() * 0.5, 0), 0.45F, 0.0F, 2);
	}

	private void tickHold(LivingEntity target) {
		if (this.held == null || this.held != target || !target.isAlive() || this.animTicks > HOLD_TICKS) {
			this.release();
			this.setAnim(ANIM_NONE);
			return;
		}
		// clamped on: ride along at the prey's feet, keep it slowed, bite every half second
		Vec3 at = target.position().add(target.getLookAngle().multiply(1, 0, 1).normalize().scale(0.55));
		this.setPos(at.x, target.getY(), at.z);
		this.setDeltaMovement(Vec3.ZERO);
		target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 5, false, false), this);
		if (this.animTicks % 10 == 0) {
			target.hurt(this.damageSources().mobAttack(this), 1.0F);
		}
	}

	private void release() {
		this.held = null;
		if (this.getAnim() == ANIM_HOLD) {
			this.setAnim(ANIM_NONE);
		}
	}

	private void spit(ServerLevel level, LivingEntity target) {
		this.playSound(SoundEvents.LLAMA_SPIT, 1.0F, 0.6F);
		for (int i = -1; i <= 1; i++) {
			MimicSpitEntity bone = new MimicSpitEntity(level, this);
			bone.setPos(this.getX(), this.getY() + 0.75, this.getZ());
			Vec3 to = target.getEyePosition().subtract(bone.position());
			double flat = Math.sqrt(to.x * to.x + to.z * to.z);
			bone.shoot(to.x, to.y + flat * 0.12, to.z, 1.1F, 4.0F);
			bone.setDeltaMovement(bone.getDeltaMovement().yRot(i * 0.14F));
			level.addFreshEntity(bone);
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!this.level().isClientSide && this.isDormant() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			this.wake(source.getEntity() instanceof LivingEntity l ? l : null);
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt && this.held != null) {
			this.release();
			this.leapCooldown = Math.max(this.leapCooldown, 60);
		}
		return hurt;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (this.isDormant()) {
			if (!this.level().isClientSide) {
				this.wake(player);
				this.setAnim(ANIM_BITE);
			}
			return InteractionResult.sidedSuccess(this.level().isClientSide);
		}
		return super.mobInteract(player, hand);
	}

	@Override
	@Nullable
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, @Nullable SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, type, data);
		this.sleep();
		return out;
	}

	// ---- plumbing ------------------------------------------------------------------------------

	@Override
	public boolean isPushable() {
		return !this.isDormant() && super.isPushable();
	}

	@Override
	public boolean isPushedByFluid() {
		return !this.isDormant();
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
		return this.isDormant() ? null : SoundEvents.DECORATED_POT_STEP;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.DECORATED_POT_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.DECORATED_POT_SHATTER;
	}

	@Override
	protected void tickDeath() {
		super.tickDeath();
		if (this.deathTime == 1 && this.level() instanceof ServerLevel server) {
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dev.remnants.registry.ModBlocks.OSSUARY_URN.defaultBlockState()),
				this.getX(), this.getY() + 0.5, this.getZ(), 30, 0.25, 0.3, 0.25, 0.1);
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("Dormant", this.isDormant());
		tag.putInt("Quarter", this.quarter());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.entityData.set(DATA_DORMANT, !tag.contains("Dormant") || tag.getBoolean("Dormant"));
		this.entityData.set(DATA_QUARTER, Math.floorMod(tag.contains("Quarter") ? tag.getInt("Quarter")
			: Math.round(this.getYRot() / 90.0F), 4));
	}

	/** The urn sits on its block exactly like the block does (used to line it up with its neighbours in a template). */
	public static float yawOf(Direction facing) {
		return facing.toYRot();
	}

	@Override
	protected net.minecraft.world.entity.ai.control.BodyRotationControl createBodyControl() {
		return new dev.remnants.entity.ai.FaceBody(this);
	}

	@Override
	public int lookTargetId() {
		return this.entityData.get(DATA_LOOK);
	}
}
