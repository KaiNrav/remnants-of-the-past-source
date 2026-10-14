package dev.remnants.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A floating soul crystal raised over a pedestal in the Soul King's second phase. While any crystal stands the King
 * takes only 30 % damage and slowly heals. One hit from a player (melee or projectile) shatters it.
 */
public class SoulCrystalEntity extends Entity {
	@Nullable
	private UUID kingId;

	public SoulCrystalEntity(EntityType<? extends SoulCrystalEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void setKing(SoulKingEntity king) {
		this.kingId = king.getUUID();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel server) {
			Entity king = this.kingId == null ? null : server.getEntity(this.kingId);
			if (!(king instanceof SoulKingEntity) || !king.isAlive()) {
				this.discard();
			}
		} else if (this.random.nextInt(3) == 0) {
			this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, this.getRandomX(0.6), this.getY() + this.random.nextDouble(), this.getRandomZ(0.6), 0, 0.02, 0);
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (this.isRemoved() || this.level().isClientSide || !(source.getEntity() instanceof Player)) {
			return false;
		}
		ServerLevel server = (ServerLevel) this.level();
		server.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 0.5, this.getZ(), 40, 0.4, 0.4, 0.4, 0.1);
		server.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.5, this.getZ(), 1, 0, 0, 0, 0);
		server.playSound(null, this.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
		server.playSound(null, this.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.5F, 1.4F);
		if (this.kingId != null && server.getEntity(this.kingId) instanceof SoulKingEntity king) {
			king.onCrystalDestroyed(this);
		}
		this.discard();
		return true;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}
