package dev.remnants.entity;

import dev.remnants.fx.CombatCues;

import dev.remnants.multiblock.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Keener (Плакальщица): a floating ghost in a torn shroud that comes with the third wave of a Night of Memory. It
 * keeps 6..10 blocks away and is all but invisible - a shimmer and dripping soul light - until something reveals it for
 * {@link #REVEAL_TICKS}: a hit, a Hand Bell's ring, or a player looking straight at it through a spyglass.
 * <ul>
 *   <li>Wail: 3 s on the spot; every Forgotten within 12 blocks heals and gets Strength. A single hit of 6+ or a Hand
 *   Bell breaks it off and stuns it for 2 s.</li>
 *   <li>Scream: a 50-degree cone, 8 blocks: 3 damage, a shove and 2 s of Darkness.</li>
 * </ul>
 */
public class KeenerEntity extends Monster implements LookTarget {
	private static final EntityDataAccessor<Long> DATA_REVEALED_UNTIL = SynchedEntityData.defineId(KeenerEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(KeenerEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(KeenerEntity.class, EntityDataSerializers.INT);
	public static final int ANIM_NONE = 0, ANIM_WAIL = 1, ANIM_SCREAM = 2, ANIM_STUNNED = 3;
	public static final int WAIL_TICKS = 60, SCREAM_WINDUP = 12, SCREAM_TICKS = 22, STUN_TICKS = 40, REVEAL_TICKS = 100;
	public static final double WAIL_RADIUS = 12.0, SCREAM_RANGE = 8.0, SCREAM_HALF_ANGLE = 50.0;

	private int animTicks;
	private int wailCooldown = 80;
	private int screamCooldown = 40;
	private int repath;
	/** Client: when the current animation began, and the one before it (the models cross-fade between them). */
	public int clientAnimStart, clientAnimChanged, clientPrevAnimStart;
	public int clientAnim, clientPrevAnim;
	/** Client display copies (the Codex portrait) are always drawn revealed. */
	public boolean displayRevealed;

	public KeenerEntity(EntityType<? extends KeenerEntity> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl(this, 20, true);
		this.setNoGravity(true);
		this.xpReward = 20;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 40.0)
			.add(Attributes.FLYING_SPEED, 0.45)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.ATTACK_DAMAGE, 3.0)
			.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_LOOK, -1);
		builder.define(DATA_REVEALED_UNTIL, 0L);
		builder.define(DATA_ANIM, ANIM_NONE);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_ANIM.equals(key)) {
			this.clientPrevAnim = this.clientAnim;
			this.clientPrevAnimStart = this.clientAnimStart;
			this.clientAnim = this.entityData.get(DATA_ANIM);
			this.clientAnimStart = this.tickCount;
			this.clientAnimChanged = this.tickCount;
		}
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
		nav.setCanOpenDoors(false);
		nav.setCanFloat(true);
		nav.setCanPassDoors(true);
		return nav;
	}

	@Override
	protected void registerGoals() {
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	// ---- state ---------------------------------------------------------------------------------

	public int getAnim() {
		return this.entityData.get(DATA_ANIM);
	}

	private void setAnim(int anim) {
		this.entityData.set(DATA_ANIM, anim);
		this.animTicks = 0;
		// art 2.0 hit feel: the scream's cone and the wail's reach show on the ground until they come
		switch (anim) {
			case ANIM_SCREAM -> CombatCues.arcAttached(this, (float) SCREAM_RANGE, (float) (SCREAM_HALF_ANGLE * 2), SCREAM_WINDUP);
			case ANIM_WAIL -> {
				CombatCues.disc(this, this.position(), (float) WAIL_RADIUS, WAIL_TICKS);
				this.playSound(dev.remnants.registry.ModSounds.KEENER_WAIL, 1.6F, 0.95F + this.random.nextFloat() * 0.1F);
			}
			default -> CombatCues.clear(this);
		}
	}

	public boolean isRevealed() {
		return this.displayRevealed || this.level().getGameTime() < this.entityData.get(DATA_REVEALED_UNTIL);
	}

	public void reveal(int ticks) {
		long until = this.level().getGameTime() + ticks;
		if (until > this.entityData.get(DATA_REVEALED_UNTIL)) {
			this.entityData.set(DATA_REVEALED_UNTIL, until);
		}
	}

	public boolean isWailing() {
		return this.getAnim() == ANIM_WAIL;
	}

	/** A Hand Bell rang nearby: seen, and a wail breaks off. Returns true if it was hidden (for the advancement). */
	public boolean ringed() {
		boolean hidden = !this.isRevealed();
		this.reveal(REVEAL_TICKS);
		if (this.isWailing()) {
			this.interrupt();
		}
		return hidden;
	}

	/** Test hook: begin a wail right now. */
	public void startWail() {
		this.setAnim(ANIM_WAIL);
		this.getNavigation().stop();
	}

	private void interrupt() {
		this.setAnim(ANIM_STUNNED);
		this.wailCooldown = 200;
		this.reveal(REVEAL_TICKS);
		this.playSound(SoundEvents.VEX_HURT, 1.5F, 0.5F);
		if (this.level() instanceof ServerLevel server) {
			server.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.4, this.getZ(), 16, 0.4, 0.4, 0.4, 0.04);
		}
	}

	// ---- life ----------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		// its ambient light (soul drips off the hem, the only sign of it while hidden) lives in KeenerModel.ambient
	}

	@Override
	protected void customServerAiStep() {
		this.entityData.set(DATA_LOOK, this.getTarget() == null ? -1 : this.getTarget().getId());
		super.customServerAiStep();
		ServerLevel level = (ServerLevel) this.level();
		this.animTicks++;
		if (this.wailCooldown > 0) {
			this.wailCooldown--;
		}
		if (this.screamCooldown > 0) {
			this.screamCooldown--;
		}
		if (this.tickCount % 4 == 0) {
			this.spyglassCheck(level);
		}
		LivingEntity target = this.getTarget();
		if (target != null && (!target.isAlive() || (target instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			this.setTarget(null);
			target = null;
		}
		if (target != null) {
			this.getLookControl().setLookAt(target, 30.0F, 30.0F);
			this.faceTowards(target);
		}
		switch (this.getAnim()) {
			case ANIM_WAIL -> this.tickWail(level);
			case ANIM_SCREAM -> {
				this.getNavigation().stop();
				this.setDeltaMovement(this.getDeltaMovement().scale(0.6));
				if (this.animTicks == SCREAM_WINDUP) {
					this.scream(level);
				} else if (this.animTicks >= SCREAM_TICKS) {
					this.setAnim(ANIM_NONE);
				}
			}
			case ANIM_STUNNED -> {
				this.getNavigation().stop();
				this.setDeltaMovement(0, -0.03, 0);
				if (this.animTicks >= STUN_TICKS) {
					this.setAnim(ANIM_NONE);
				}
			}
			default -> this.choose(level, target);
		}
	}

	private void faceTowards(Entity target) {
		double dx = target.getX() - this.getX(), dz = target.getZ() - this.getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, 12.0F));
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
	}

	private void choose(ServerLevel level, LivingEntity target) {
		if (this.wailCooldown <= 0 && !this.forgottenNear(level).isEmpty()) {
			this.setAnim(ANIM_WAIL);
			this.getNavigation().stop();
			this.playSound(SoundEvents.GHAST_AMBIENT, 2.5F, 0.45F);
			this.playSound(SoundEvents.SOUL_ESCAPE.value(), 2.0F, 0.5F);
			return;
		}
		if (target != null && this.screamCooldown <= 0 && this.distanceTo(target) < SCREAM_RANGE && this.inCone(target) && this.hasLineOfSight(target)) {
			this.setAnim(ANIM_SCREAM);
			this.screamCooldown = 120;
			this.playSound(SoundEvents.GHAST_WARN, 1.5F, 0.6F);
			return;
		}
		this.hover(level, target);
	}

	/** Keep 6..10 blocks off the target, a little above it, somewhere it can still see it from. */
	private void hover(ServerLevel level, LivingEntity target) {
		if (--this.repath > 0) {
			return;
		}
		this.repath = 20 + this.random.nextInt(15);
		if (target == null) {
			Vec3 drift = this.position().add((this.random.nextDouble() - 0.5) * 8, (this.random.nextDouble() - 0.5) * 2, (this.random.nextDouble() - 0.5) * 8);
			this.moveControl.setWantedPosition(drift.x, drift.y, drift.z, 0.6);
			return;
		}
		double d = this.distanceTo(target);
		for (int attempt = 0; attempt < 8; attempt++) {
			double a = Math.atan2(this.getZ() - target.getZ(), this.getX() - target.getX()) + (this.random.nextDouble() - 0.5) * 1.6;
			double r = 6.5 + this.random.nextDouble() * 3.0;
			Vec3 p = new Vec3(target.getX() + Math.cos(a) * r, target.getEyeY() + 1.0 + this.random.nextDouble() * 2.0, target.getZ() + Math.sin(a) * r);
			BlockPos bp = BlockPos.containing(p);
			if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty() || !level.getBlockState(bp.above()).getCollisionShape(level, bp.above()).isEmpty()) {
				continue;
			}
			if (level.clip(new ClipContext(p, target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS) {
				this.moveControl.setWantedPosition(p.x, p.y, p.z, d < 5 ? 1.4 : 1.0);
				return;
			}
		}
		this.moveControl.setWantedPosition(target.getX(), target.getEyeY() + 2.0, target.getZ(), 0.8);
	}

	private List<RemnantEntity> forgottenNear(ServerLevel level) {
		return level.getEntitiesOfClass(RemnantEntity.class, this.getBoundingBox().inflate(WAIL_RADIUS), e -> e.isAlive() && e.isForgotten());
	}

	private void tickWail(ServerLevel level) {
		this.getNavigation().stop();
		this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
		if (this.animTicks % 10 == 0) {
			for (RemnantEntity f : this.forgottenNear(level)) {
				f.heal(2.0F);
				f.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0), this);
				level.sendParticles(ParticleTypes.SOUL, f.getX(), f.getY() + 1.2, f.getZ(), 3, 0.3, 0.4, 0.3, 0.02);
			}
			for (int i = 0; i < 16; i++) {
				double a = i / 16.0 * Math.PI * 2;
				double r = 1.0 + this.animTicks / (double) WAIL_TICKS * 3.0;
				level.sendParticles(ParticleTypes.SCULK_SOUL, this.getX() + Math.cos(a) * r, this.getY() + 1.2, this.getZ() + Math.sin(a) * r, 1, 0, 0.02, 0, 0.0);
			}
		}
		if (this.animTicks % 20 == 10) {
			this.playSound(SoundEvents.GHAST_AMBIENT, 1.8F, 0.4F + this.random.nextFloat() * 0.1F);
		}
		if (this.animTicks >= WAIL_TICKS) {
			this.setAnim(ANIM_NONE);
			this.wailCooldown = 160;
		}
	}

	private boolean inCone(Entity e) {
		Vec3 look = Vec3.directionFromRotation(0, this.getYRot());
		Vec3 to = new Vec3(e.getX() - this.getX(), 0, e.getZ() - this.getZ());
		if (to.lengthSqr() < 1.0E-4) {
			return true;
		}
		return look.dot(to.normalize()) >= Math.cos(Math.toRadians(SCREAM_HALF_ANGLE));
	}

	private void scream(ServerLevel level) {
		this.playSound(SoundEvents.GHAST_SCREAM, 2.0F, 0.7F);
		CombatCues.impact(this, null, this.position(), 0.45F, 2.5F, 2);
		Vec3 look = Vec3.directionFromRotation(0, this.getYRot());
		for (int i = 1; i <= 8; i++) {
			Vec3 p = this.position().add(0, 1.4, 0).add(look.scale(i));
			level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y - (i * 0.08), p.z, 1, 0, 0, 0, 0);
		}
		for (Player p : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(SCREAM_RANGE),
			p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && this.distanceTo(p) <= SCREAM_RANGE && this.inCone(p))) {
			if (p.hurt(this.damageSources().indirectMagic(this, this), 3.0F)) {
				Vec3 push = new Vec3(p.getX() - this.getX(), 0, p.getZ() - this.getZ()).normalize().scale(1.2);
				p.push(push.x, 0.35, push.z);
				p.hurtMarked = true;
			}
			p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0), this);
		}
	}

	/** A player looking straight at it through a spyglass sees it. */
	private void spyglassCheck(ServerLevel level) {
		for (ServerPlayer p : level.players()) {
			if (!p.isScoping() || p.distanceToSqr(this) > 64 * 64) {
				continue;
			}
			Vec3 to = this.position().add(0, this.getBbHeight() * 0.6, 0).subtract(p.getEyePosition()).normalize();
			if (p.getViewVector(1.0F).dot(to) > 0.996 && p.hasLineOfSight(this)) {
				if (!this.isRevealed()) {
					HeartBlockEntity.award(p, "ring_out");
				}
				this.reveal(REVEAL_TICKS);
			}
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		boolean hurt = super.hurt(source, amount);
		if (hurt && !this.level().isClientSide) {
			this.reveal(REVEAL_TICKS);
			if (this.isWailing() && amount >= 6.0F) {
				this.interrupt();
			}
		}
		return hurt;
	}

	// ---- plumbing ------------------------------------------------------------------------------

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof RemnantEntity || other instanceof KeenerEntity || super.isAlliedTo(other);
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
		return SoundEvents.GHAST_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.5F + this.random.nextFloat() * 0.15F;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("WailCooldown", this.wailCooldown);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.wailCooldown = tag.getInt("WailCooldown");
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
