package dev.remnants.entity;

import dev.remnants.multiblock.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A Carrion Crow (Костяной ворон): a bony black bird of the flock that gathers over a Remnant left waiting too long
 * ({@link dev.remnants.CarrionCrows}). It circles high over its Remnant; when told, it dives, snatches one stack of the
 * Remnant's supplies (never its gear) and carries it to the nearest tree, where it perches - in plain sight, six hearts,
 * and it drops what it holds when it dies. A perched crow hops to another branch if someone comes too close.
 * When the Remnant is gone or its owner comes back, the crows that carry nothing fly off.
 */
public class CarrionCrowEntity extends PathfinderMob {
	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(CarrionCrowEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<ItemStack> DATA_HELD = SynchedEntityData.defineId(CarrionCrowEntity.class, EntityDataSerializers.ITEM_STACK);
	public static final int CIRCLE = 0, SNATCH = 1, CARRY = 2, PERCH = 3, LEAVE = 4;
	public static final double CIRCLE_RADIUS = 5.0, CIRCLE_HEIGHT = 8.0;
	@Nullable
	private UUID remnant;
	@Nullable
	private BlockPos perch;
	private float angle;
	private int stateTicks, hopCooldown;
	/** Client: when the current state began (the model's clips). */
	public int clientStateStart;

	public CarrionCrowEntity(EntityType<? extends CarrionCrowEntity> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl(this, 20, true);
		this.setNoGravity(true);
		this.setPersistenceRequired();
		this.angle = this.random.nextFloat() * 360.0F;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.FLYING_SPEED, 0.6).add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, CIRCLE);
		builder.define(DATA_HELD, ItemStack.EMPTY);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_STATE.equals(key)) {
			this.clientStateStart = this.tickCount;
		}
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
		nav.setCanOpenDoors(false);
		nav.setCanFloat(true);
		return nav;
	}

	@Override
	protected void registerGoals() {
	}

	// ---- state ---------------------------------------------------------------------------------

	public int state() {
		return this.entityData.get(DATA_STATE);
	}

	private void setState(int state) {
		this.entityData.set(DATA_STATE, state);
		this.stateTicks = 0;
	}

	public ItemStack held() {
		return this.entityData.get(DATA_HELD);
	}

	public boolean isCarrying() {
		return !this.held().isEmpty();
	}

	@Nullable
	public UUID remnant() {
		return this.remnant;
	}

	public void serve(RemnantEntity owner) {
		this.remnant = owner.getUUID();
	}

	/** Client display copies (the Codex portrait): perched, a stack in its beak. */
	public void showCarrying(ItemStack stack) {
		this.entityData.set(DATA_HELD, stack);
		this.entityData.set(DATA_STATE, PERCH);
	}

	/** Told by the flock: dive for a stack. */
	public void beginSnatch() {
		if (this.state() == CIRCLE && !this.isCarrying()) {
			this.setState(SNATCH);
			this.playSound(SoundEvents.PARROT_AMBIENT, 1.5F, 0.5F);
		}
	}

	/** Holds {@code stack} and takes it to a tree (a snatch, or the catch-up of a long absence). */
	public void carry(ItemStack stack) {
		this.entityData.set(DATA_HELD, stack);
		this.perch = this.findPerch((ServerLevel) this.level());
		this.setState(CARRY);
	}

	/** Puts it straight onto its perch (catch-up: it took the stack while nobody was here to see). */
	public void settle() {
		if (this.perch == null) {
			this.perch = this.findPerch((ServerLevel) this.level());
		}
		this.moveTo(this.perch.getX() + 0.5, this.perch.getY(), this.perch.getZ() + 0.5, this.random.nextFloat() * 360.0F, 0.0F);
		this.setState(PERCH);
	}

	/** Flies off for good (only an empty-handed crow). */
	public void leave() {
		if (!this.isCarrying() && this.state() != LEAVE) {
			this.setState(LEAVE);
			this.playSound(SoundEvents.PARROT_AMBIENT, 1.2F, 0.6F);
		}
	}

	@Nullable
	private RemnantEntity remnantEntity() {
		return this.remnant != null && this.level() instanceof ServerLevel s && s.getEntity(this.remnant) instanceof RemnantEntity r && r.isAlive() ? r : null;
	}

	// ---- life ----------------------------------------------------------------------------------

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		ServerLevel level = (ServerLevel) this.level();
		this.stateTicks++;
		if (this.hopCooldown > 0) {
			this.hopCooldown--;
		}
		RemnantEntity r = this.remnantEntity();
		switch (this.state()) {
			case CIRCLE -> {
				if (r == null) {
					if (this.stateTicks > 200) {
						this.leave();
					}
					return;
				}
				this.stateTicks = Math.min(this.stateTicks, 1);
				this.angle += 3.0F;
				double a = Math.toRadians(this.angle);
				this.moveControl.setWantedPosition(r.getX() + Math.cos(a) * CIRCLE_RADIUS, r.getY() + CIRCLE_HEIGHT + Math.sin(a * 2) * 0.8,
					r.getZ() + Math.sin(a) * CIRCLE_RADIUS, 0.8);
				if (this.random.nextInt(240) == 0) {
					this.playSound(SoundEvents.PARROT_AMBIENT, 1.2F, 0.5F + this.random.nextFloat() * 0.1F);
				}
			}
			case SNATCH -> {
				if (r == null || this.stateTicks > 160) {
					this.setState(CIRCLE);
					return;
				}
				this.moveControl.setWantedPosition(r.getX(), r.getY() + r.getBbHeight() * 0.7, r.getZ(), 1.4);
				if (this.distanceToSqr(r.getX(), r.getY() + r.getBbHeight() * 0.7, r.getZ()) < 1.6) {
					ItemStack took = r.takeForCrow(this.random);
					if (took.isEmpty()) {
						this.setState(CIRCLE);
						return;
					}
					level.sendParticles(ParticleTypes.SOUL, r.getX(), r.getY() + 1.4, r.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
					this.playSound(SoundEvents.ITEM_PICKUP, 1.0F, 0.6F);
					this.playSound(SoundEvents.PARROT_AMBIENT, 1.5F, 0.7F);
					this.carry(took);
				}
			}
			case CARRY -> {
				if (this.perch == null) {
					this.perch = this.findPerch(level);
				}
				Vec3 p = Vec3.atBottomCenterOf(this.perch);
				this.moveControl.setWantedPosition(p.x, p.y + 0.1, p.z, 1.2);
				if (this.position().distanceToSqr(p) < 0.5 || this.stateTicks > 400) {
					this.moveTo(p.x, p.y, p.z, this.getYRot(), 0.0F);
					this.setState(PERCH);
				}
			}
			case PERCH -> {
				this.setDeltaMovement(Vec3.ZERO);
				this.getNavigation().stop();
				if (this.perch != null && level.getBlockState(this.perch.below()).getCollisionShape(level, this.perch.below()).isEmpty()) {
					this.hop(level);   // its branch is gone
					return;
				}
				if (this.hopCooldown <= 0 && this.stateTicks % 10 == 0) {
					Player near = level.getNearestPlayer(this, 4.0);
					if (near != null && !near.isSpectator() && !near.isCreative()) {
						this.hop(level);
					}
				}
				if (this.random.nextInt(400) == 0) {
					this.playSound(SoundEvents.PARROT_AMBIENT, 1.0F, 0.55F);
				}
			}
			case LEAVE -> {
				this.moveControl.setWantedPosition(this.getX() + this.getLookAngle().x * 4, this.getY() + 3, this.getZ() + this.getLookAngle().z * 4, 1.2);
				if (this.stateTicks > 100) {
					this.discard();
				}
			}
			default -> {
			}
		}
	}

	/** Startled off its branch: to another perch. */
	private void hop(ServerLevel level) {
		this.perch = this.findPerch(level);
		this.hopCooldown = 600;
		this.setState(CARRY);
		this.playSound(SoundEvents.PARROT_FLY, 1.0F, 0.8F);
	}

	/** The top of the nearest tree (leaves, else a log) within 24 blocks, else a high spot 10 blocks off. */
	public BlockPos findPerch(ServerLevel level) {
		RemnantEntity r = this.remnantEntity();
		BlockPos c = r != null ? r.blockPosition() : this.blockPosition();
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		for (int dx = -24; dx <= 24; dx += 2) {
			for (int dz = -24; dz <= 24; dz += 2) {
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c.offset(dx, 0, dz));
				BlockState s = level.getBlockState(top.below());
				if (!s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS)) {
					continue;
				}
				double score = dx * dx + dz * dz - (s.is(BlockTags.LEAVES) ? 30 : 0) + this.random.nextDouble() * 40 + (this.perch != null && top.equals(this.perch) ? 1e6 : 0);
				if (score < bestScore && Math.abs(top.getY() - c.getY()) < 24) {
					bestScore = score;
					best = top;
				}
			}
		}
		if (best != null) {
			return best;
		}
		double a = this.random.nextDouble() * Math.PI * 2;
		BlockPos far = c.offset((int) (Math.cos(a) * 10), 0, (int) (Math.sin(a) * 10));
		for (int y = 12; y >= -12; y--) {   // the first solid block under open air near the Remnant's height
			BlockPos p = far.above(y);
			if (!level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()
				&& level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) {
				return p;
			}
		}
		return c.above(2);
	}

	// ---- death, loot ---------------------------------------------------------------------------

	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel level && this.isCarrying()) {
			this.spawnAtLocation(this.held().copy());
			if (source.getEntity() instanceof ServerPlayer p) {
				HeartBlockEntity.award(p, "murder_of_crows");
			}
			this.entityData.set(DATA_HELD, ItemStack.EMPTY);
			level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 0.3, this.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
		}
		super.die(source);
	}

	@Override
	public void remove(RemovalReason reason) {
		// never lose a stolen stack: anything that removes a carrying crow for good drops it first
		if (reason.shouldDestroy() && reason != RemovalReason.KILLED && this.isCarrying() && this.level() instanceof ServerLevel) {
			this.spawnAtLocation(this.held().copy());
			this.entityData.set(DATA_HELD, ItemStack.EMPTY);
		}
		super.remove(reason);
	}

	// ---- plumbing ------------------------------------------------------------------------------

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return this.state() != PERCH;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PARROT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PARROT_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.55F + this.random.nextFloat() * 0.1F;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("State", this.state());
		if (this.remnant != null) {
			tag.putUUID("Remnant", this.remnant);
		}
		if (this.perch != null) {
			tag.putIntArray("Perch", new int[]{this.perch.getX(), this.perch.getY(), this.perch.getZ()});
		}
		if (this.isCarrying()) {
			tag.put("Held", this.held().save(this.registryAccess()));
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.entityData.set(DATA_STATE, tag.getInt("State"));
		this.remnant = tag.hasUUID("Remnant") ? tag.getUUID("Remnant") : null;
		int[] p = tag.getIntArray("Perch");
		this.perch = p.length == 3 ? new BlockPos(p[0], p[1], p[2]) : null;
		this.entityData.set(DATA_HELD, tag.contains("Held") ? ItemStack.parse(this.registryAccess(), tag.getCompound("Held")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY);
		if (this.state() == SNATCH) {
			this.entityData.set(DATA_STATE, CIRCLE);
		}
	}
}
