package dev.remnants.entity;

import dev.remnants.registry.ModEntities;
import dev.remnants.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A spectral arrow fired by ranged Remnants and the Arsenal's bow: soul trail, a short chill on hit, can't be picked up,
 * fades fast. Ghost arrows (the Arsenal's) fly dead straight, stick where they hit with a small soul spark, stay
 * visible for a few seconds and then burst into soul fire.
 */
public class PhantomArrowEntity extends AbstractArrow {
	private static final int GHOST_LIFE = 80;
	private static final int STUCK_LIFE = 40;
	private static final int GHOST_STUCK_LIFE = 70;
	private boolean explosive;
	private boolean ghost;

	public PhantomArrowEntity(EntityType<? extends PhantomArrowEntity> type, Level level) {
		super(type, level);
	}

	public PhantomArrowEntity(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
		super(ModEntities.PHANTOM_ARROW, owner, level, pickup, weapon);
		this.pickup = Pickup.DISALLOWED;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide) {
			Entity owner = this.getOwner();
			// no trail right in front of the shooter's camera (big, long-lived soul wisps would fill the screen)
			if (!this.inGround && (owner == null || this.distanceToSqr(owner.getEyePosition()) > 9.0)) {
				this.level().addParticle(ModParticles.REMNANT_SOUL, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
			}
		} else if (this.inGround && this.inGroundTime > (this.ghost ? GHOST_STUCK_LIFE : STUCK_LIFE)
			|| this.ghost && !this.inGround && this.tickCount > GHOST_LIFE) {
			this.vanish();
		}
	}

	public void makeGhost() {
		this.ghost = true;
		this.setNoGravity(true);
	}

	public void setExplosive(boolean explosive) {
		this.explosive = explosive;
	}

	@Override
	protected void onHit(HitResult result) {
		if (this.explosive && !this.level().isClientSide) {
			// Soul bolt: knockback blast, never breaks blocks; the Remnant is immune to its own explosions.
			this.level().explode(this, this.getX(), this.getY(), this.getZ(), 1.6F, Level.ExplosionInteraction.NONE);
			this.discard();
			return;
		}
		super.onHit(result);
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		if (this.ghost && this.level() instanceof ServerLevel server) {
			// a spark where it hit (not where this tick started - that can be right at the shooter's eyes); the arrow
			// itself stays stuck and visible, the full burst comes when it fades (vanish)
			Vec3 at = result.getLocation();
			server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.01);
		}
	}

	private void vanish() {
		if (this.level() instanceof ServerLevel server) {
			server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY(), this.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
			server.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY(), this.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
		}
		this.discard();
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("Ghost", this.ghost);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.ghost = tag.getBoolean("Ghost");
	}

	@Override
	protected void doPostHurtEffects(LivingEntity target) {
		super.doPostHurtEffects(target);
		target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0), this.getEffectSource());
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		return new ItemStack(Items.ARROW);
	}
}
