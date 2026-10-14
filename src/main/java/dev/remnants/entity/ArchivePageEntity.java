package dev.remnants.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A Page, torn from the Archivist's book: a folded leaf of soul-inked paper that flutters round its target and darts
 * in for a paper cut ({@link #CUT}, every {@link #DASH_COOLDOWN} ticks at most). Fragile, and fire burns it twice as
 * fast. It crumbles after {@link #LIFE} ticks.
 */
public class ArchivePageEntity extends Monster {
	private static final EntityDataAccessor<Boolean> DATA_DASHING = SynchedEntityData.defineId(ArchivePageEntity.class, EntityDataSerializers.BOOLEAN);
	public static final int LIFE = 800, DASH_COOLDOWN = 25, DASH_TICKS = 10;
	public static final float CUT = 3.0F;
	private int dashCooldown = 20, dashTicks, repath;

	public ArchivePageEntity(EntityType<? extends ArchivePageEntity> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl(this, 20, true);
		this.setNoGravity(true);
		this.xpReward = 2;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 8.0)
			.add(Attributes.FLYING_SPEED, 0.6)
			.add(Attributes.MOVEMENT_SPEED, 0.35)
			.add(Attributes.ATTACK_DAMAGE, CUT)
			.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_DASHING, false);
	}

	public boolean isDashing() {
		return this.entityData.get(DATA_DASHING);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
		nav.setCanOpenDoors(false);
		nav.setCanFloat(true);
		return nav;
	}

	@Override
	protected void registerGoals() {
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ArchivistEntity.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		ServerLevel level = (ServerLevel) this.level();
		if (this.tickCount > LIFE) {
			this.crumble(level);
			return;
		}
		if (this.dashCooldown > 0) {
			this.dashCooldown--;
		}
		LivingEntity target = this.getTarget();
		if (target != null && (!target.isAlive() || (target instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			this.setTarget(null);
			target = null;
		}
		if (this.dashTicks > 0) {
			this.dashTicks--;
			if (target != null && this.getBoundingBox().inflate(0.3).intersects(target.getBoundingBox())) {
				this.doHurtTarget(target);
				this.dashTicks = 0;
				this.setDeltaMovement(this.getDeltaMovement().scale(-0.4));
			}
			if (this.dashTicks == 0) {
				this.entityData.set(DATA_DASHING, false);
			}
			return;
		}
		if (target == null) {
			if (--this.repath <= 0) {
				this.repath = 30;
				Vec3 p = this.position().add((this.random.nextDouble() - 0.5) * 6, (this.random.nextDouble() - 0.5) * 2, (this.random.nextDouble() - 0.5) * 6);
				this.moveControl.setWantedPosition(p.x, p.y, p.z, 0.6);
			}
			return;
		}
		double d = this.distanceTo(target);
		if (this.dashCooldown <= 0 && d < 7.0 && this.hasLineOfSight(target)) {
			Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0).subtract(this.position()).normalize();
			this.setDeltaMovement(to.scale(0.75));
			this.dashTicks = DASH_TICKS;
			this.dashCooldown = DASH_COOLDOWN + this.random.nextInt(15);
			this.entityData.set(DATA_DASHING, true);
			this.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.6F);
			return;
		}
		if (--this.repath <= 0) {
			this.repath = 8 + this.random.nextInt(8);
			double a = this.random.nextDouble() * Math.PI * 2;
			double r = 2.5 + this.random.nextDouble() * 2.0;
			this.moveControl.setWantedPosition(target.getX() + Math.cos(a) * r, target.getEyeY() + this.random.nextDouble() * 1.5,
				target.getZ() + Math.sin(a) * r, 1.0);
		}
		this.getLookControl().setLookAt(target, 30.0F, 30.0F);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide && this.random.nextInt(5) == 0) {
			this.level().addParticle(ParticleTypes.ENCHANT, this.getX(), this.getY() + 0.3, this.getZ(), 0, 0.05, 0);
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FIRE)) {
			amount *= 2.0F;
		}
		return super.hurt(source, amount);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)), this.getX(), this.getY() + 0.3, this.getZ(),
				10, 0.2, 0.2, 0.2, 0.08);
		}
	}

	/** Its time is up (or its Archivist fell): it falls apart into scraps. */
	public void crumble(ServerLevel level) {
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)), this.getX(), this.getY() + 0.3, this.getZ(),
			12, 0.2, 0.2, 0.2, 0.06);
		this.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 0.5F);
		this.discard();
	}

	public float flap(float partial) {
		return Mth.sin((this.tickCount + partial) * (this.isDashing() ? 1.6F : 0.7F));
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof ArchivistEntity || other instanceof ArchivePageEntity || super.isAlliedTo(other);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.BOOK_PAGE_TURN;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.BOOK_PAGE_TURN;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BOOK_PUT;
	}

	@Override
	public float getVoicePitch() {
		return 1.3F + this.random.nextFloat() * 0.3F;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return true;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
