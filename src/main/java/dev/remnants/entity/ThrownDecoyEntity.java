package dev.remnants.entity;

import dev.remnants.registry.ModEntities;
import dev.remnants.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A thrown Decoy Echo: where it lands, the thrower's ghostly copy stands up ({@link DecoyEchoEntity}). */
public class ThrownDecoyEntity extends ThrowableItemProjectile {
	public ThrownDecoyEntity(EntityType<? extends ThrownDecoyEntity> type, Level level) {
		super(type, level);
	}

	public ThrownDecoyEntity(Level level, LivingEntity owner) {
		super(ModEntities.THROWN_DECOY, owner, level);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.DECOY_ECHO;
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 at = hit.getLocation();
		if (hit instanceof BlockHitResult bh) {
			at = Vec3.atBottomCenterOf(bh.getBlockPos().relative(bh.getDirection()));
			if (bh.getDirection() != net.minecraft.core.Direction.UP) {
				at = new Vec3(at.x, Math.floor(hit.getLocation().y), at.z);
			}
		}
		if (this.getOwner() instanceof Player owner) {
			spawn(level, owner, at);
		}
		this.discard();
	}

	public static DecoyEchoEntity spawn(ServerLevel level, Player owner, Vec3 at) {
		DecoyEchoEntity decoy = ModEntities.DECOY_ECHO.create(level);
		decoy.moveTo(at.x, at.y, at.z, owner.getYRot(), 0.0F);
		decoy.setup(owner, DecoyEchoEntity.lifetime(owner));
		level.addFreshEntity(decoy);
		level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1.0, at.z, 24, 0.3, 0.7, 0.3, 0.04);
		level.playSound(null, decoy.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.7F);
		decoy.lure(level);
		return decoy;
	}
}
