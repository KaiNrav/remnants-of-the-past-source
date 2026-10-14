package dev.remnants.entity;

import dev.remnants.RemnantsConfig;
import dev.remnants.item.SoulTetherItem;
import dev.remnants.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The head of a Soul Tether's chain. It flies straight ({@link #SPEED} a tick, no gravity) up to {@code tetherRange}
 * blocks, then:
 * <ul>
 *   <li>a block - it bites in and reels its owner to it (up to 40 ticks; sneaking or using the tether again lets go;
 *   falls are forgiven on the way);</li>
 *   <li>a Remnant (any, Forgotten too) - it tears one worn armour piece off for 10 s ({@link RemnantEntity#stripArmor});</li>
 *   <li>a small creature (40 HP or less) - it reels the creature in; anything bigger, or a boss, reels the owner to it.</li>
 * </ul>
 * Missing everything it rattles back. The owner is moved from the server (velocity + hurtMarked, like knockback), so
 * the pull is the same on every client.
 */
public class SoulTetherEntity extends Projectile {
	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(SoulTetherEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_HOOKED = SynchedEntityData.defineId(SoulTetherEntity.class, EntityDataSerializers.INT);
	public static final int FLYING = 0, ANCHORED = 1, HOOKED = 2, RETURNING = 3;
	public static final float SPEED = 2.4F;
	public static final int PULL_MAX = 40, REEL_MAX = 20, STRIP_TICKS = 200;

	private int life;
	/** The last pull given to the owner (server; the test checks it - a test player has no client to move). */
	public Vec3 lastPull = Vec3.ZERO;
	private int pullTicks;
	private boolean pullOwner;

	public SoulTetherEntity(EntityType<? extends SoulTetherEntity> type, Level level) {
		super(type, level);
	}

	public static SoulTetherEntity fire(ServerLevel level, Player owner) {
		SoulTetherEntity t = new SoulTetherEntity(ModEntities.SOUL_TETHER, level);
		t.setOwner(owner);
		Vec3 eye = owner.getEyePosition();
		t.setPos(eye.x, eye.y - 0.15, eye.z);
		t.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0.0F, SPEED, 0.0F);
		level.addFreshEntity(t);
		return t;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_STATE, FLYING);
		builder.define(DATA_HOOKED, -1);
	}

	public int state() {
		return this.entityData.get(DATA_STATE);
	}

	private void setState(int state) {
		this.entityData.set(DATA_STATE, state);
		this.pullTicks = 0;
		if (state != FLYING) {
			this.setDeltaMovement(Vec3.ZERO);
		}
	}

	/** How far the chain reaches: the config's tetherRange, farther for a Wanderer (1.5). */
	private int range() {
		return dev.remnants.PathBranch.tetherRange(this.getOwner());
	}

	@Override
	public void tick() {
		super.tick();
		Entity owner = this.getOwner();
		boolean server = !this.level().isClientSide;
		if (server && (!(owner instanceof Player p) || !p.isAlive() || p.level() != this.level()
			|| p.distanceToSqr(this) > (range() + 8.0) * (range() + 8.0) || ++this.life > 200)) {
			this.discard();
			return;
		}
		switch (this.state()) {
			case FLYING -> {
				HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
				if (hit.getType() != HitResult.Type.MISS) {
					if (server) {
						this.onHit(hit);
					}
				} else {
					Vec3 v = this.getDeltaMovement();
					this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
				}
				if (server && owner != null && this.distanceTo(owner) > range()) {
					this.setState(RETURNING);
					this.playSound(SoundEvents.CHAIN_STEP, 1.0F, 1.4F);
				}
			}
			case ANCHORED -> {
				this.setDeltaMovement(Vec3.ZERO);
				if (server && owner instanceof Player p) {
					this.pull(p, this.position());
				}
			}
			case HOOKED -> {
				Entity e = this.level().getEntity(this.entityData.get(DATA_HOOKED));
				if (e == null || !e.isAlive()) {
					if (server) {
						this.setState(RETURNING);
					}
					return;
				}
				this.setPos(e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ());
				if (server && owner instanceof Player p) {
					if (this.pullOwner) {
						this.pull(p, e.position().add(0, e.getBbHeight() * 0.5, 0));
					} else {
						this.reel(e, p);
					}
				}
			}
			default -> {
				if (owner == null) {
					return;
				}
				Vec3 to = owner.getEyePosition().subtract(0, 0.5, 0).subtract(this.position());
				double len = to.length();
				if (len < 1.5) {
					if (server) {
						this.discard();
					}
				} else {
					Vec3 step = to.normalize().scale(Math.min(len, SPEED));
					this.setPos(this.getX() + step.x, this.getY() + step.y, this.getZ() + step.z);
				}
			}
		}
	}

	/** Reels the owner to {@code to}. */
	private void pull(Player owner, Vec3 to) {
		Vec3 d = to.subtract(owner.position().add(0, 0.6, 0));
		double len = d.length();
		this.pullTicks++;
		owner.fallDistance = 0.0F;
		if (len < 1.6 || this.pullTicks > PULL_MAX || owner.isShiftKeyDown()) {
			owner.setDeltaMovement(owner.getDeltaMovement().multiply(0.5, 1.0, 0.5).add(0, 0.25, 0));
			owner.hurtMarked = true;
			this.playSound(SoundEvents.CHAIN_BREAK, 0.8F, 1.4F);
			this.discard();
			return;
		}
		double speed = Math.min(1.4, 0.45 + this.pullTicks * 0.07);
		this.lastPull = d.normalize().scale(speed);
		owner.setDeltaMovement(this.lastPull);
		owner.hurtMarked = true;
		if (this.pullTicks % 4 == 0) {
			this.playSound(SoundEvents.CHAIN_STEP, 0.8F, 1.2F);
		}
	}

	/** Reels a small creature in to its owner. */
	private void reel(Entity e, Player owner) {
		Vec3 d = owner.position().subtract(e.position());
		double len = d.length();
		this.pullTicks++;
		if (len < 2.2 || this.pullTicks > REEL_MAX) {
			e.setDeltaMovement(e.getDeltaMovement().scale(0.3));
			e.hurtMarked = true;
			this.setState(RETURNING);
			return;
		}
		e.setDeltaMovement(d.normalize().scale(0.9).add(0, 0.12, 0));
		e.hurtMarked = true;
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);   // eyes, bells and targets feel it like any projectile
		Vec3 at = hit.getLocation();
		this.setPos(at.x, at.y, at.z);
		this.setState(ANCHORED);
		this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2F, 0.8F);
		if (this.level() instanceof ServerLevel server) {
			server.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.02);
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		Entity e = hit.getEntity();
		Entity owner = this.getOwner();
		this.playSound(SoundEvents.CHAIN_HIT, 1.2F, 0.8F);
		if (e instanceof RemnantEntity remnant) {
			remnant.hurt(this.damageSources().thrown(this, owner), 2.0F);
			if (remnant.stripArmor(STRIP_TICKS) != null && this.level() instanceof ServerLevel server) {
				server.sendParticles(ParticleTypes.SOUL, remnant.getX(), remnant.getY() + 1.0, remnant.getZ(), 10, 0.3, 0.5, 0.3, 0.03);
			}
			this.setState(RETURNING);
			return;
		}
		if (e instanceof LivingEntity living) {
			living.hurt(this.damageSources().thrown(this, owner), 2.0F);
			this.pullOwner = living.getMaxHealth() > 40.0F || living instanceof SoulKingEntity || living instanceof CryptWardenEntity;
			this.entityData.set(DATA_HOOKED, living.getId());
			this.setState(HOOKED);
			return;
		}
		this.setState(RETURNING);
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		return super.canHitEntity(e) && e != this.getOwner() && !(e instanceof SoulTetherEntity);
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 96 * 96;
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!this.level().isClientSide && this.getOwner() != null) {
			SoulTetherItem.forget(this.getOwner().getUUID(), this);
		}
		super.remove(reason);
	}
}
