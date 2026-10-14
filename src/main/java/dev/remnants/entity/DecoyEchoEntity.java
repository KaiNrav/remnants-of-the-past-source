package dev.remnants.entity;

import dev.remnants.PathBranch;
import dev.remnants.RemnantsConfig;
import dev.remnants.multiblock.HeartBlockEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The Decoy Echo: a ghostly copy of whoever threw it, holding what they held, for {@code decoyTicks} (8 s; 12 s for a
 * Keeper). Every monster within {@code decoyRadius} that is after a player turns on it - their own Remnants too. It
 * stands still, has {@code decoyHealth}, and bursts into soul light when it is done.
 *
 * <p>The Memorial's last record reuses this entity client-side as its replaying ghost ({@link #replay}).
 */
public class DecoyEchoEntity extends PathfinderMob {
	private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(DecoyEchoEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	private static final EntityDataAccessor<String> DATA_OWNER_NAME = SynchedEntityData.defineId(DecoyEchoEntity.class, EntityDataSerializers.STRING);
	private int life;
	private int maxLife = 160;
	/** Client: a replay ghost of the Memorial (alpha and pose come from the replay, not from ticking). */
	public boolean replay;
	public float replayAlpha = 1.0F;

	public DecoyEchoEntity(EntityType<? extends DecoyEchoEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_OWNER, Optional.empty());
		builder.define(DATA_OWNER_NAME, "");
	}

	public void setup(Player owner, int ticks) {
		this.entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
		this.entityData.set(DATA_OWNER_NAME, owner.getGameProfile().getName());
		this.setItemSlot(EquipmentSlot.MAINHAND, owner.getMainHandItem().copyWithCount(1));
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		this.setYRot(owner.getYRot());
		this.yBodyRot = owner.getYRot();
		this.yHeadRot = owner.getYRot();
		this.maxLife = ticks;
		var hp = this.getAttribute(Attributes.MAX_HEALTH);
		if (hp != null) {
			hp.setBaseValue(RemnantsConfig.get().decoyHealth);
		}
		this.setHealth(this.getMaxHealth());
	}

	/** Client only: a replay figure for the Memorial (never added on the server). */
	public void setupReplay(UUID owner, String name, ItemStack held) {
		this.replay = true;
		this.entityData.set(DATA_OWNER, Optional.of(owner));
		this.entityData.set(DATA_OWNER_NAME, name);
		this.setItemSlot(EquipmentSlot.MAINHAND, held);
	}

	public Optional<UUID> owner() {
		return this.entityData.get(DATA_OWNER);
	}

	public String ownerName() {
		return this.entityData.get(DATA_OWNER_NAME);
	}

	public float lifeFraction(float partial) {
		return Math.min(1.0F, (this.life + partial) / Math.max(1, this.maxLife));
	}

	@Override
	protected void registerGoals() {
	}

	@Override
	public void calculateEntityAnimation(boolean flying) {
		if (!this.replay) {   // a replay ghost's stride is set by the replay
			super.calculateEntityAnimation(flying);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide) {
			if (!this.replay && this.random.nextInt(3) == 0) {
				this.level().addParticle(ParticleTypes.SOUL, this.getRandomX(0.6), this.getRandomY(), this.getRandomZ(0.6), 0, 0.02, 0);
			}
			return;
		}
		ServerLevel level = (ServerLevel) this.level();
		if (++this.life >= this.maxLife) {
			this.burst(level);
			return;
		}
		if (this.life % 10 == 1) {
			this.lure(level);
		}
	}

	/** Every monster around that is after a player (or this decoy's owner) turns on the decoy. */
	public void lure(ServerLevel level) {
		double r = RemnantsConfig.get().decoyRadius;
		UUID owner = this.owner().orElse(null);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(r), m -> m.isAlive() && m != this)) {
			if (mob instanceof RemnantEntity remnant) {
				LivingEntity t = remnant.getTarget();
				boolean mine = owner != null && remnant.getOwnerUuid().map(owner::equals).orElse(false);
				if (!remnant.isIllusion() && (t instanceof Player || mine)) {
					remnant.setTarget(this);
				}
				continue;
			}
			if (!(mob instanceof Enemy) || mob.getType().is(dev.remnants.item.SoulLanternItem.UNCATCHABLE) || dev.remnants.SoulAllies.isAlly(mob)) {
				continue;
			}
			LivingEntity t = mob.getTarget();
			if (t instanceof Player || (t == null && mob.distanceToSqr(this) < 64.0)) {
				mob.setTarget(this);
			}
		}
	}

	private void burst(ServerLevel level) {
		level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.3, 0.6, 0.3, 0.06);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 1.0, this.getZ(), 12, 0.3, 0.6, 0.3, 0.03);
		this.playSound(SoundEvents.SOUL_ESCAPE.value(), 1.5F, 1.2F);
		this.discard();
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!this.level().isClientSide && source.getEntity() instanceof RemnantEntity remnant && this.owner().isPresent()
			&& remnant.getOwnerUuid().map(this.owner().get()::equals).orElse(false)
			&& ((ServerLevel) this.level()).getServer().getPlayerList().getPlayer(this.owner().get()) instanceof ServerPlayer p) {
			HeartBlockEntity.award(p, "fooled_echo");
		}
		return super.hurt(source, amount);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			this.burst(level);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean player) {
	}

	@Override
	protected boolean shouldDropLoot() {
		return false;
	}

	@Override
	public boolean isPushable() {
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
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SOUL_ESCAPE.value();
	}

	@Override
	@Nullable
	protected SoundEvent getDeathSound() {
		return null;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("Life", this.life);
		tag.putInt("MaxLife", this.maxLife);
		this.owner().ifPresent(u -> tag.putUUID("Owner", u));
		tag.putString("OwnerName", this.ownerName());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.life = tag.getInt("Life");
		this.maxLife = Math.max(1, tag.getInt("MaxLife"));
		if (tag.hasUUID("Owner")) {
			this.entityData.set(DATA_OWNER, Optional.of(tag.getUUID("Owner")));
		}
		this.entityData.set(DATA_OWNER_NAME, tag.getString("OwnerName"));
	}

	/** Lifetime for a decoy thrown by {@code owner}. */
	public static int lifetime(Player owner) {
		int base = RemnantsConfig.get().decoyTicks;
		return PathBranch.is(owner, PathBranch.Branch.KEEPER) ? base * 3 / 2 : base;
	}
}
