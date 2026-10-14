package dev.remnants.entity;

import dev.remnants.registry.ModEntities;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** A bone a Grave Mimic spits out of whatever it swallowed: 3 damage, then it breaks. */
public class MimicSpitEntity extends ThrowableItemProjectile {
	public static final float DAMAGE = 3.0F;

	public MimicSpitEntity(EntityType<? extends MimicSpitEntity> type, Level level) {
		super(type, level);
	}

	public MimicSpitEntity(Level level, LivingEntity owner) {
		super(ModEntities.MIMIC_SPIT, owner, level);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.BONE;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (!(hit.getEntity() instanceof GraveMimicEntity)) {
			hit.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), DAMAGE);
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel server) {
			server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE)), this.getX(), this.getY(), this.getZ(),
				6, 0.1, 0.1, 0.1, 0.08);
			this.discard();
		}
	}
}
