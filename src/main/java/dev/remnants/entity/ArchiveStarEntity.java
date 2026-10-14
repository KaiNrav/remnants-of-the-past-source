package dev.remnants.entity;

import dev.remnants.fx.CombatCues;

import dev.remnants.RemnantsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A star of the Archivist's constellation (phase 3): set on the floor, it glows for {@link #FUSE} ticks and bursts -
 * {@link #DAMAGE} and a toss upward to everyone within {@link #RADIUS} (never the Archivist or its Pages). The floor
 * cracks only in the particles: the boss can be fought anywhere, so it never breaks blocks.
 */
public class ArchiveStarEntity extends Entity {
	public static final int FUSE = 40;
	public static final double RADIUS = 2.5;
	public static final float DAMAGE = 8.0F;
	@Nullable
	private UUID owner;

	public ArchiveStarEntity(EntityType<? extends ArchiveStarEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	public static ArchiveStarEntity place(ServerLevel level, ArchivistEntity owner, double x, double y, double z) {
		ArchiveStarEntity star = new ArchiveStarEntity(dev.remnants.registry.ModEntities.ARCHIVE_STAR, level);
		star.owner = owner.getUUID();
		star.setPos(x, y + 0.05, z);
		level.addFreshEntity(star);
		level.playSound(null, star.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.5F, 1.6F);
		return star;
	}

	public float fuse(float partial) {
		return Math.min(1.0F, (this.tickCount + partial) / FUSE);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide) {
			if (this.random.nextInt(3) == 0) {
				this.level().addParticle(ParticleTypes.END_ROD, this.getX() + (this.random.nextDouble() - 0.5) * RADIUS * 1.4, this.getY() + 0.1,
					this.getZ() + (this.random.nextDouble() - 0.5) * RADIUS * 1.4, 0, 0.03, 0);
			}
			return;
		}
		if (this.tickCount >= FUSE) {
			this.burst((ServerLevel) this.level());
		}
	}

	private void burst(ServerLevel level) {
		Entity owner = this.owner == null ? null : level.getEntity(this.owner);
		float dmg = (float) (DAMAGE * RemnantsConfig.get().archivistDamageMultiplier);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(RADIUS, 2.0, RADIUS),
			e -> e.isAlive() && !(e instanceof ArchivistEntity) && !(e instanceof ArchivePageEntity) && !(e instanceof ArchiveEyeEntity)
				&& e.distanceToSqr(this) <= (RADIUS + 0.5) * (RADIUS + 0.5))) {
			if (e.hurt(this.damageSources().indirectMagic(this, owner), dmg)) {
				e.push(0, 0.55, 0);
				e.hurtMarked = true;
			}
		}
		BlockPos floor = this.blockPosition().below();
		BlockState fs = level.getBlockState(floor);
		if (!fs.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, fs), this.getX(), this.getY() + 0.1, this.getZ(), 40,
				RADIUS * 0.5, 0.05, RADIUS * 0.5, 0.2);
		}
		level.sendParticles(ParticleTypes.FLASH, this.getX(), this.getY() + 0.3, this.getZ(), 1, 0, 0, 0, 0);
		CombatCues.impact(this, null, this.position(), 0.6F, (float) RADIUS + 0.4F, 0);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 0.2, this.getZ(), 30, RADIUS * 0.4, 0.1, RADIUS * 0.4, 0.06);
		level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8F, 1.6F);
		level.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.5F, 0.8F);
		this.discard();
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

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}
}
