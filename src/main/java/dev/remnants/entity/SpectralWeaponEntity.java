package dev.remnants.entity;

import dev.remnants.registry.ModEntities;
import dev.remnants.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A ghostly copy of the Remnant's weapon (rendered as the real 3D item). It hovers at an offset around its owner,
 * aimed at the target, then launches in a straight line. Used by Phantom Armory and the trident throws.
 */
public class SpectralWeaponEntity extends Projectile {
	private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(SpectralWeaponEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<Boolean> DATA_LAUNCHED = SynchedEntityData.defineId(SpectralWeaponEntity.class, EntityDataSerializers.BOOLEAN);

	private Vec3 hoverOffset = Vec3.ZERO;
	private int hoverTicks;
	private float damage = 6.0F;
	private double speed = 1.5;
	@Nullable
	private LivingEntity target;
	private int life;
	private int stuckTicks = -1;
	private boolean lightningOnHit;

	public SpectralWeaponEntity(EntityType<? extends SpectralWeaponEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	public static SpectralWeaponEntity hovering(RemnantEntity owner, ItemStack item, Vec3 offset, int hoverTicks, LivingEntity target, float damage, double speed) {
		SpectralWeaponEntity e = new SpectralWeaponEntity(ModEntities.SPECTRAL_WEAPON, owner.level());
		e.setOwner(owner);
		e.entityData.set(DATA_ITEM, item.copyWithCount(1));
		e.hoverOffset = offset;
		e.hoverTicks = hoverTicks;
		e.target = target;
		e.damage = damage;
		e.speed = speed;
		e.setPos(owner.position().add(rotate(offset, owner.yBodyRot)));
		return e;
	}

	/** Fired straight away by any shooter (the Arsenal's Soul Slash and trident throw). */
	public static SpectralWeaponEntity launched(Level level, LivingEntity owner, ItemStack item, Vec3 pos, Vec3 velocity, float damage, boolean lightning) {
		SpectralWeaponEntity e = new SpectralWeaponEntity(ModEntities.SPECTRAL_WEAPON, level);
		e.setOwner(owner);
		e.entityData.set(DATA_ITEM, item.copyWithCount(1));
		e.entityData.set(DATA_LAUNCHED, true);
		e.damage = damage;
		e.lightningOnHit = lightning;
		e.setPos(pos);
		e.setDeltaMovement(velocity);
		e.aimAt(velocity);
		return e;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_ITEM, ItemStack.EMPTY);
		builder.define(DATA_LAUNCHED, false);
	}

	public ItemStack getItem() {
		return this.entityData.get(DATA_ITEM);
	}

	public boolean isLaunched() {
		return this.entityData.get(DATA_LAUNCHED);
	}

	public boolean isStuck() {
		return this.stuckTicks >= 0;
	}

	private static Vec3 rotate(Vec3 offset, float yaw) {
		return offset.yRot(-yaw * Mth.DEG_TO_RAD);
	}

	@Override
	public void tick() {
		super.tick();
		this.life++;
		if (this.level().isClientSide) {
			if (this.isLaunched() || this.tickCount % 3 == 0) {
				this.level().addParticle(ModParticles.REMNANT_SOUL, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
			}
			if (this.isLaunched()) {
				this.setPos(this.position().add(this.getDeltaMovement()));
			}
			return;
		}
		if (this.life > 200) {
			this.discard();
			return;
		}
		if (this.stuckTicks >= 0) {
			if (++this.stuckTicks > 25) {
				this.poof();
			}
			return;
		}
		Entity owner = this.getOwner();
		if (!this.isLaunched()) {
			if (!(owner instanceof RemnantEntity remnant) || !remnant.isAlive() || this.target == null || !this.target.isAlive()) {
				this.poof();
				return;
			}
			Vec3 pos = remnant.position().add(rotate(this.hoverOffset, remnant.yBodyRot));
			pos = pos.add(0, Mth.sin((this.tickCount + this.getId()) * 0.25F) * 0.08, 0);
			this.setPos(pos);
			this.aimAt(this.target.getEyePosition().subtract(pos));
			if (--this.hoverTicks <= 0) {
				Vec3 aim = this.target.position().add(0, this.target.getBbHeight() * 0.55, 0)
					.add(this.target.getDeltaMovement().multiply(6, 0, 6)).subtract(pos).normalize().scale(this.speed);
				this.setDeltaMovement(aim);
				this.aimAt(aim);
				this.entityData.set(DATA_LAUNCHED, true);
				this.hasImpulse = true;
				this.level().playSound(null, this.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 0.8F, 1.6F);
			}
			return;
		}
		HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
		if (hit.getType() != HitResult.Type.MISS) {
			this.onHit(hit);
			if (this.isRemoved() || this.isStuck()) {
				return;
			}
		}
		this.setPos(this.position().add(this.getDeltaMovement()));
		this.aimAt(this.getDeltaMovement());
	}

	private void aimAt(Vec3 v) {
		double h = v.horizontalDistance();
		this.setYRot((float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
		this.setXRot((float) (Mth.atan2(v.y, h) * Mth.RAD_TO_DEG));
		this.yRotO = this.getYRot();
		this.xRotO = this.getXRot();
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		Entity owner = this.getOwner();
		if (entity == owner || entity instanceof SpectralWeaponEntity) {
			return false;
		}
		// boss weapons never hit their own side; player weapons hit anything
		boolean bossOwned = owner instanceof RemnantEntity || owner instanceof SoulKingEntity;
		return super.canHitEntity(entity) && !(bossOwned && (entity instanceof RemnantEntity || entity instanceof SoulKingEntity));
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (result.getEntity() instanceof LivingEntity victim) {
			Entity owner = this.getOwner();
			victim.hurt(this.damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null), this.damage);
			victim.knockback(0.4, -this.getDeltaMovement().x, -this.getDeltaMovement().z);
		}
		this.strikeLightning();
		this.poof();
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		this.setPos(result.getLocation().subtract(this.getDeltaMovement().normalize().scale(0.25)));
		this.setDeltaMovement(Vec3.ZERO);
		this.stuckTicks = 0;
		this.level().playSound(null, this.blockPosition(), SoundEvents.TRIDENT_HIT_GROUND, SoundSource.HOSTILE, 0.8F, 1.4F);
		this.strikeLightning();
	}

	/** Visual lightning plus a small soul shock around the impact (never sets the world on fire). */
	private void strikeLightning() {
		if (!this.lightningOnHit || !(this.level() instanceof ServerLevel server)) {
			return;
		}
		this.lightningOnHit = false;
		net.minecraft.world.entity.LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
		if (bolt != null) {
			bolt.moveTo(this.position());
			bolt.setVisualOnly(true);
			server.addFreshEntity(bolt);
		}
		Entity owner = this.getOwner();
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(2.5), e -> e != owner && e.isAlive())) {
			e.hurt(this.damageSources().lightningBolt(), 5.0F);
		}
		server.playSound(null, this.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.5F, 1.0F);
	}

	private void poof() {
		if (this.level() instanceof ServerLevel server) {
			server.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY(), this.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
		}
		this.discard();
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}
}
