package dev.remnants.entity;

import dev.remnants.RemnantsConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A rune of the Archivist's spiral (phase 3): flies straight, no gravity, for {@link #LIFE} ticks; {@link #DAMAGE} to
 * the first creature it meets (never the Archivist or its Pages), gone on a wall.
 */
public class ArchiveRuneEntity extends Projectile {
	public static final int LIFE = 30;
	public static final float DAMAGE = 4.0F;
	public static final double SPEED = 0.55;
	private int life;

	public ArchiveRuneEntity(EntityType<? extends ArchiveRuneEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	public static ArchiveRuneEntity fire(Level level, LivingEntity owner, Vec3 from, Vec3 dir) {
		ArchiveRuneEntity rune = new ArchiveRuneEntity(dev.remnants.registry.ModEntities.ARCHIVE_RUNE, level);
		rune.setOwner(owner);
		rune.setPos(from);
		rune.setDeltaMovement(dir.normalize().scale(SPEED));
		level.addFreshEntity(rune);
		return rune;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide && ++this.life > LIFE) {
			this.discard();
			return;
		}
		HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
		if (hit.getType() != HitResult.Type.MISS && !this.level().isClientSide) {
			this.onHit(hit);
			if (this.isRemoved()) {
				return;
			}
		}
		Vec3 v = this.getDeltaMovement();
		this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
		if (this.level().isClientSide && this.random.nextInt(2) == 0) {
			this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		Entity e = hit.getEntity();
		float dmg = (float) (DAMAGE * RemnantsConfig.get().archivistDamageMultiplier);
		e.hurt(this.damageSources().indirectMagic(this, this.getOwner()), dmg);
		this.discard();
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (hit.getType() == HitResult.Type.BLOCK) {
			this.discard();
		}
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		return super.canHitEntity(e) && e instanceof LivingEntity && !(e instanceof ArchivistEntity) && !(e instanceof ArchivePageEntity)
			&& !(e instanceof ArchiveEyeEntity);
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
