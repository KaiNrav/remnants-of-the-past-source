package dev.remnants.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * An Eye of the Archive: one of the four glowing eyes the Archivist opens on the walls of its hall in phase 2. Only a
 * projectile closes it (an arrow, a trident, a snowball, an egg, the Bone Chakram) - a blade cannot reach that high, and
 * would not count. A closed eye tells its Archivist ({@link ArchivistEntity#onEyeClosed}). It hangs where it was put,
 * never moves, and is gone when the fight ends.
 */
public class ArchiveEyeEntity extends Mob {
	private static final EntityDataAccessor<Boolean> DATA_OPEN = SynchedEntityData.defineId(ArchiveEyeEntity.class, EntityDataSerializers.BOOLEAN);
	private int ownerId = -1;
	/** Client: when it closed (the lid comes down). */
	public int clientClosedAt = -1;

	public ArchiveEyeEntity(EntityType<? extends ArchiveEyeEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.setNoAi(true);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1000.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_OPEN, true);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_OPEN.equals(key) && !this.isOpen()) {
			this.clientClosedAt = this.tickCount;
		}
	}

	public void setOwner(ArchivistEntity owner) {
		this.ownerId = owner.getId();
	}

	public boolean isOpen() {
		return this.entityData.get(DATA_OPEN);
	}

	/** Faces {@code at} (the hall's middle). */
	public void lookToward(Vec3 at) {
		double dx = at.x - this.getX(), dz = at.z - this.getZ();
		float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
		this.yRotO = yaw;
		this.yBodyRotO = yaw;
		this.yHeadRotO = yaw;
	}

	/** Closes the eye (a projectile struck it). Returns false if it was already shut. */
	public boolean close(ServerPlayer by) {
		if (!this.isOpen()) {
			return false;
		}
		this.entityData.set(DATA_OPEN, false);
		ServerLevel level = (ServerLevel) this.level();
		level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 0.5, this.getZ(), 16, 0.3, 0.3, 0.3, 0.05);
		level.sendParticles(ParticleTypes.ENCHANT, this.getX(), this.getY() + 0.5, this.getZ(), 30, 0.4, 0.4, 0.4, 0.8);
		level.playSound(null, this.blockPosition(), SoundEvents.ENDER_EYE_DEATH, SoundSource.HOSTILE, 1.5F, 0.6F);
		level.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.5F, 0.6F);
		if (level.getEntity(this.ownerId) instanceof ArchivistEntity archivist) {
			archivist.onEyeClosed(this, by);
		}
		return true;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (this.level().isClientSide) {
			return false;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			this.discard();
			return true;
		}
		boolean shot = source.getDirectEntity() instanceof Projectile || source.is(DamageTypeTags.IS_PROJECTILE);
		if (shot && this.isOpen()) {
			ServerPlayer by = source.getEntity() instanceof ServerPlayer p ? p : null;
			this.close(by);
			return true;
		}
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		this.setDeltaMovement(Vec3.ZERO);
		if (!this.level().isClientSide && (this.tickCount % 20 == 0) && !(this.level().getEntity(this.ownerId) instanceof ArchivistEntity a && a.isAlive())) {
			this.discard();   // its Archivist is gone (dead, reset, unloaded): the eye goes with it
		}
		if (this.level().isClientSide && this.isOpen() && this.random.nextInt(4) == 0) {
			this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, this.getRandomX(0.5), this.getY() + 0.5, this.getRandomZ(0.5), 0, 0.01, 0);
		}
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public void knockback(double strength, double x, double z) {
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean shouldShowName() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return true;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("Open", this.isOpen());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.entityData.set(DATA_OPEN, !tag.contains("Open") || tag.getBoolean("Open"));
	}

	@Override
	public boolean shouldBeSaved() {
		return false;   // a fight's eyes are never written to disk
	}
}
