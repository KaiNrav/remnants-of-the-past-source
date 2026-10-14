package dev.remnants.entity;

import dev.remnants.fx.CombatCues;

import dev.remnants.RemnantsConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * A flying tome, the Archivist's phase-1 throw: it flies out straight at {@link #SPEED} for up to {@link #RANGE} blocks
 * (or until it meets a wall), turns, and homes back to its thrower, who catches it. Everyone it passes through takes
 * {@link #DAMAGE} - once on the way out, once on the way back. Where it turns, a Page may tear loose (50 %).
 */
public class FlyingTomeEntity extends Projectile {
	private static final EntityDataAccessor<Boolean> DATA_RETURNING = SynchedEntityData.defineId(FlyingTomeEntity.class, EntityDataSerializers.BOOLEAN);
	public static final double SPEED = 0.9, RANGE = 16.0;
	public static final float DAMAGE = 7.0F;
	private final Set<Integer> hitOut = new HashSet<>(), hitBack = new HashSet<>();
	private Vec3 start = Vec3.ZERO;
	private int life;

	public FlyingTomeEntity(EntityType<? extends FlyingTomeEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_RETURNING, false);
	}

	public static FlyingTomeEntity throwAt(ServerLevel level, LivingEntity thrower, Vec3 from, Vec3 dir) {
		FlyingTomeEntity tome = new FlyingTomeEntity(dev.remnants.registry.ModEntities.FLYING_TOME, level);
		tome.setOwner(thrower);
		tome.setPos(from);
		tome.start = from;
		tome.setDeltaMovement(dir.normalize().scale(SPEED));
		level.addFreshEntity(tome);
		level.playSound(null, thrower.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 1.5F, 0.6F);
		level.playSound(null, thrower.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 0.6F, 1.8F);
		return tome;
	}

	public boolean isReturning() {
		return this.entityData.get(DATA_RETURNING);
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 v = this.getDeltaMovement();
		if (this.level().isClientSide) {
			this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
			if (this.random.nextInt(2) == 0) {
				this.level().addParticle(ParticleTypes.ENCHANT, this.getX(), this.getY() + 0.2, this.getZ(), -v.x, 0.1, -v.z);
			}
			return;
		}
		ServerLevel level = (ServerLevel) this.level();
		Entity owner = this.getOwner();
		if (++this.life > 200 || owner == null || !owner.isAlive() || owner.level() != this.level()) {
			this.discard();
			return;
		}
		if (!this.isReturning()) {
			Vec3 next = this.position().add(v);
			HitResult wall = level.clip(new ClipContext(this.position(), next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			if (wall.getType() != HitResult.Type.MISS || this.position().distanceTo(this.start) >= RANGE) {
				this.turn(level);
			}
		} else {
			Vec3 home = owner.position().add(0, owner.getBbHeight() * 0.6, 0);
			Vec3 to = home.subtract(this.position());
			if (to.length() < 1.2) {
				this.discard();   // caught
				level.playSound(null, owner.blockPosition(), SoundEvents.BOOK_PUT, SoundSource.HOSTILE, 1.2F, 0.7F);
				return;
			}
			this.setDeltaMovement(to.normalize().scale(SPEED * 1.1));
		}
		v = this.getDeltaMovement();
		Vec3 from = this.position(), to = from.add(v);
		for (Entity e : level.getEntities(this, this.getBoundingBox().expandTowards(v).inflate(0.6), this::canHitEntity)) {
			AABB box = e.getBoundingBox().inflate(0.4);
			if (box.contains(from) || box.clip(from, to).isPresent()) {
				Set<Integer> done = this.isReturning() ? this.hitBack : this.hitOut;
				if (done.add(e.getId()) && e instanceof LivingEntity victim) {
					float dmg = (float) (DAMAGE * RemnantsConfig.get().archivistDamageMultiplier);
					if (victim.hurt(this.damageSources().indirectMagic(this, owner), dmg)) {
						victim.knockback(0.4, -v.x, -v.z);
						CombatCues.impact(this, victim, victim.position().add(0, victim.getBbHeight() * 0.6, 0), 0.35F, 0.0F, 2);
						level.playSound(null, victim.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 1.5F, 1.4F);
					}
				}
			}
		}
		this.setPos(to);
		this.checkInsideBlocks();
	}

	private void turn(ServerLevel level) {
		this.entityData.set(DATA_RETURNING, true);
		level.sendParticles(ParticleTypes.ENCHANT, this.getX(), this.getY(), this.getZ(), 20, 0.3, 0.3, 0.3, 0.6);
		level.playSound(null, this.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 1.2F, 0.9F);
		if (this.getOwner() instanceof ArchivistEntity archivist && this.random.nextFloat() < 0.5F) {
			archivist.tearPage(this.position());
		}
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		return e instanceof LivingEntity l && l.isAlive() && !l.isSpectator() && e != this.getOwner() && !(e instanceof ArchivePageEntity)
			&& !(e instanceof ArchiveEyeEntity) && !(e instanceof net.minecraft.world.entity.player.Player p && p.isCreative());
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 96 * 96;
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

	/** Test hook: where it turned around (the distance it flew). */
	public double flown() {
		return this.position().distanceTo(this.start);
	}
}
