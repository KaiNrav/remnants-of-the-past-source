package dev.remnants.entity;

import dev.remnants.RemnantsConfig;
import dev.remnants.item.ChakramInlay;
import dev.remnants.registry.ModComponents;
import dev.remnants.registry.ModEntities;
import dev.remnants.registry.ModTags;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A thrown Bone Chakram. Out: it flies straight and slows a little for {@link #OUT_TICKS} ticks (about fifteen
 * blocks), cutting grass and cobwebs, pressing buttons and pulling levers it passes, slicing through every creature
 * once; a wall (or a bell, a target block...) turns it back. Back: it homes in on the thrower through anything,
 * picks up the items it passes (up to eight stacks) and delivers them with itself into the thrower's hand.
 */
public class BoneChakramEntity extends Projectile {
	private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(BoneChakramEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<Boolean> DATA_RETURNING = SynchedEntityData.defineId(BoneChakramEntity.class, EntityDataSerializers.BOOLEAN);
	public static final int OUT_TICKS = 14;
	private static final int MAX_CARRIED = 8;

	private ItemStack stack = ItemStack.EMPTY;
	private InteractionHand hand = InteractionHand.MAIN_HAND;
	/** Thrown from a creative inventory: the thrower kept theirs, so this one vanishes on return. */
	private boolean creativeCopy;
	private int outTicks = OUT_TICKS;
	private int flightTicks;
	private int returnTicks;
	private int ownerlessTicks;
	private boolean ricocheted;
	/** Scarab ricochet: the second creature it now flies at (steered toward every tick until the out leg ends). */
	@Nullable
	private LivingEntity ricochetTarget;
	/** Creatures already struck on this leg (cleared when it turns back, so it can hit them again on the way home). */
	private final IntOpenHashSet struck = new IntOpenHashSet();
	private final List<ItemEntity> carried = new ArrayList<>();

	public BoneChakramEntity(EntityType<? extends BoneChakramEntity> type, Level level) {
		super(type, level);
	}

	public BoneChakramEntity(Level level, LivingEntity owner, ItemStack stack, InteractionHand hand, boolean creativeCopy) {
		this(ModEntities.BONE_CHAKRAM, level);
		this.setOwner(owner);
		this.setPos(owner.getX(), owner.getEyeY() - 0.2, owner.getZ());
		this.stack = stack;
		this.hand = hand;
		this.creativeCopy = creativeCopy;
		this.entityData.set(DATA_ITEM, stack.copy());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_ITEM, ItemStack.EMPTY);
		builder.define(DATA_RETURNING, false);
	}

	/** The thrown stack as the client sees it (for the renderer). */
	public ItemStack getItem() {
		return this.entityData.get(DATA_ITEM);
	}

	public boolean isReturning() {
		return this.entityData.get(DATA_RETURNING);
	}

	private void startReturn() {
		if (!this.isReturning()) {
			this.entityData.set(DATA_RETURNING, true);
			this.struck.clear();
		}
	}

	@Nullable
	private ChakramInlay inlay() {
		return this.stack.get(ModComponents.CHAKRAM_INLAY);
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 pos = this.position();
		Vec3 vel = this.getDeltaMovement();
		Entity owner = this.getOwner();
		if (!this.isReturning()) {
			vel = vel.scale(0.95);
			if (++this.flightTicks >= this.outTicks) {
				this.startReturn();
			}
		}
		if (this.isReturning()) {
			if (owner == null || !owner.isAlive() || owner.level() != this.level()) {
				vel = vel.scale(0.8);
				if (!this.level().isClientSide && ++this.ownerlessTicks > 40) {
					this.dropAsItem();
					return;
				}
			} else {
				Vec3 to = owner.getEyePosition().subtract(0, 0.5, 0).subtract(pos);
				if (!this.level().isClientSide && (to.length() < 1.4 || ++this.returnTicks > 120)) {
					this.catchBy(owner);
					return;
				}
				double speed = Math.min(2.2, 0.7 + this.returnTicks * 0.09);
				vel = vel.scale(0.55).add(to.normalize().scale(speed * 0.45));
				if (vel.length() > speed) {
					vel = vel.normalize().scale(speed);
				}
			}
		}
		Vec3 next = pos.add(vel);
		if (this.level() instanceof ServerLevel level) {
			this.sweepBlocks(level, pos, next);
			if (!this.isReturning()) {
				BlockHitResult wall = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
				if (wall.getType() == HitResult.Type.BLOCK) {
					next = wall.getLocation().subtract(vel.normalize().scale(0.1));
					this.hitWall(level, wall);
				}
			}
			this.sweepEntities(level, pos, next, vel);
			if (this.isRemoved()) {
				return;
			}
			if (this.ricochetTarget != null) {
				// home in from where it actually is now: aiming from the first hit's spot alone sent it past the target
				if (this.isReturning() || !this.ricochetTarget.isAlive() || this.struck.contains(this.ricochetTarget.getId())) {
					this.ricochetTarget = null;
				} else {
					Vec3 to = this.ricochetTarget.getBoundingBox().getCenter().subtract(next);
					vel = to.lengthSqr() < 1.0E-4 ? vel : to.normalize().scale(Math.max(1.0, vel.length()));
				}
			}
			if (this.isReturning()) {
				this.gather(level);
			}
		} else {
			this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 0, 0, 0);
		}
		this.setDeltaMovement(vel);
		this.updateRotation();
		this.setPos(next);
		for (ItemEntity item : this.carried) {
			if (item.isAlive()) {
				item.setPos(next.x, next.y - 0.25, next.z);
				item.setDeltaMovement(Vec3.ZERO);
				item.setPickUpDelay(5); // refreshed every tick: nobody picks them up before the chakram is back in hand
			}
		}
		if (this.tickCount % 4 == 0) {
			this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.15F, 1.8F + this.random.nextFloat() * 0.2F);
		}
	}

	/** Cuts what it flies through (grass, cobwebs, vines...) and, on the way out, presses buttons and pulls levers. */
	private void sweepBlocks(ServerLevel level, Vec3 from, Vec3 to) {
		Set<BlockPos> cells = new LinkedHashSet<>();
		Vec3 d = to.subtract(from);
		int n = Math.max(1, (int) Math.ceil(d.length() / 0.3));
		for (int i = 0; i <= n; i++) {
			cells.add(BlockPos.containing(from.add(d.scale((double) i / n))));
		}
		Player thrower = this.getOwner() instanceof Player p ? p : null;
		for (BlockPos p : cells) {
			BlockState state = level.getBlockState(p);
			if (state.isAir()) {
				continue;
			}
			if (state.is(ModTags.CHAKRAM_CUTTABLE)) {
				if (this.mayInteract(level, p)) {
					level.destroyBlock(p, true, this.getOwner());
				}
				continue;
			}
			if (this.isReturning() || !(state.getBlock() instanceof ButtonBlock || state.getBlock() instanceof LeverBlock)
				|| state.getShape(level, p).clip(from, to, p) == null) {
				continue;
			}
			if (state.getBlock() instanceof ButtonBlock button && !state.getValue(ButtonBlock.POWERED)) {
				button.press(state, level, p, thrower);
			} else if (state.getBlock() instanceof LeverBlock lever) {
				lever.pull(state, level, p, thrower);
			}
			level.playSound(null, p, SoundEvents.BONE_BLOCK_HIT, SoundSource.PLAYERS, 0.8F, 1.4F);
			this.startReturn();
			return;
		}
	}

	private void hitWall(ServerLevel level, BlockHitResult hit) {
		BlockState state = level.getBlockState(hit.getBlockPos());
		state.onProjectileHit(level, state, hit, this); // bells ring, targets fire, chorus flowers break...
		level.playSound(null, hit.getBlockPos(), SoundEvents.BONE_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 1.2F);
		level.sendParticles(ParticleTypes.CRIT, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 4, 0.1, 0.1, 0.1, 0.1);
		this.startReturn();
	}

	private void sweepEntities(ServerLevel level, Vec3 from, Vec3 to, Vec3 vel) {
		AABB box = this.getBoundingBox().expandTowards(to.subtract(from)).inflate(0.4);
		for (Entity e : level.getEntities(this, box, this::canHitEntity)) {
			if (this.struck.contains(e.getId())) {
				continue;
			}
			AABB target = e.getBoundingBox().inflate(0.35, 0.5, 0.35);
			if (target.contains(from) || target.clip(from, to).isPresent()) {
				this.strike(level, (LivingEntity) e, vel);
				if (this.isRemoved()) {
					return;
				}
			}
		}
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		Entity owner = this.getOwner();
		return e instanceof LivingEntity && e != owner && e.isAlive() && !e.isSpectator() && super.canHitEntity(e)
			&& !(e instanceof OwnableEntity pet && owner != null && owner.getUUID().equals(pet.getOwnerUUID()));
	}

	private void strike(ServerLevel level, LivingEntity target, Vec3 vel) {
		this.struck.add(target.getId());
		Entity owner = this.getOwner();
		DamageSource source = this.damageSources().thrown(this, owner == null ? this : owner);
		float damage = EnchantmentHelper.modifyDamage(level, this.stack, target, source, (float) RemnantsConfig.get().chakramDamage);
		if (!target.hurt(source, damage)) {
			return;
		}
		EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, source, this.stack);
		target.knockback(0.3, -vel.x, -vel.z);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 1.3F);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
		ChakramInlay inlay = this.inlay();
		if (inlay == ChakramInlay.FROST) {
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1), owner);
			target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 30, target.getTicksFrozen() + 90));
			level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
		} else if (inlay == ChakramInlay.TIDE && owner != null && target.getMaxHealth() <= 100.0F) {
			Vec3 pull = owner.position().subtract(target.position());
			if (pull.length() > 2.0) {
				target.setDeltaMovement(pull.normalize().scale(Math.min(1.4, 0.35 + pull.length() * 0.08)).add(0, 0.3, 0));
				target.hurtMarked = true;
			}
			level.sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY(0.6), target.getZ(), 16, 0.3, 0.4, 0.3, 0.1);
		} else if (inlay == ChakramInlay.SCARAB && !this.ricocheted && !this.isReturning()) {
			LivingEntity next = this.nextTarget(level, target);
			if (next != null) {
				this.ricocheted = true;
				this.ricochetTarget = next;
				this.flightTicks = 0;
				this.outTicks = 10;
				level.sendParticles(ParticleTypes.WAX_OFF, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BONE_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 1.8F);
			}
		}
		if (!this.creativeCopy) {
			this.stack.hurtAndBreak(1, level, owner instanceof ServerPlayer sp ? sp : null, item -> {
			});
			if (this.stack.isEmpty()) {
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
				this.releaseCarried();
				this.discard();
			}
		}
	}

	/** Scarab ricochet: the nearest other creature within eight blocks that is not the thrower (monsters first). */
	@Nullable
	private LivingEntity nextTarget(ServerLevel level, LivingEntity from) {
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(8.0),
			e -> e != from && this.canHitEntity(e) && !this.struck.contains(e.getId()) && from.hasLineOfSight(e))) {
			double score = e.distanceToSqr(from) * (e instanceof net.minecraft.world.entity.monster.Enemy ? 0.25 : 1.0);
			if (score < bestScore) {
				bestScore = score;
				best = e;
			}
		}
		return best;
	}

	/** On the way back: items it flies through hang on to it. */
	private void gather(ServerLevel level) {
		if (this.carried.size() >= MAX_CARRIED) {
			return;
		}
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(1.0),
			i -> i.isAlive() && !this.carried.contains(i))) {
			this.carry(item);
			if (this.carried.size() >= MAX_CARRIED) {
				return;
			}
		}
	}

	/**
	 * Takes an item along. Carried items keep their gravity (nothing is written into their NBT): the chakram just puts
	 * them back at its own position every tick, so if it vanishes mid-flight (chunk unloaded, server stopped) they
	 * simply fall.
	 */
	public void carry(ItemEntity item) {
		if (this.carried.size() < MAX_CARRIED && !this.carried.contains(item)) {
			this.carried.add(item);
		}
	}

	private void releaseCarried() {
		for (ItemEntity item : this.carried) {
			item.setNoGravity(false); // (older builds set it; make sure nothing stays floating)
		}
		this.carried.clear();
	}

	@Override
	public void remove(RemovalReason reason) {
		this.releaseCarried();
		super.remove(reason);
	}

	/** Home: back into the hand it was thrown from (or anywhere in the inventory), with whatever it picked up. */
	private void catchBy(Entity owner) {
		if (owner instanceof Player player) {
			if (!this.creativeCopy && !this.stack.isEmpty()) {
				if (player.getItemInHand(this.hand).isEmpty()) {
					player.setItemInHand(this.hand, this.stack);
				} else if (!player.getInventory().add(this.stack)) {
					player.drop(this.stack, false);
				}
			}
			for (ItemEntity item : this.carried) {
				if (item.isAlive()) {
					item.setPos(player.getX(), player.getY() + 0.3, player.getZ());
					item.setDeltaMovement(Vec3.ZERO);
					item.setNoPickUpDelay();
				}
			}
			this.carried.clear();
			this.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.7F, 1.7F);
		} else {
			this.dropAsItem();
			return;
		}
		this.discard();
	}

	private void dropAsItem() {
		if (!this.creativeCopy && !this.stack.isEmpty()) {
			this.spawnAtLocation(this.stack, 0.1F);
		}
		this.releaseCarried();
		this.discard();
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (!this.stack.isEmpty()) {
			tag.put("Item", this.stack.save(this.registryAccess()));
		}
		tag.putBoolean("Returning", this.isReturning());
		tag.putBoolean("OffHand", this.hand == InteractionHand.OFF_HAND);
		tag.putBoolean("CreativeCopy", this.creativeCopy);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.stack = tag.contains("Item") ? ItemStack.parse(this.registryAccess(), tag.getCompound("Item")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
		this.entityData.set(DATA_ITEM, this.stack.copy());
		this.entityData.set(DATA_RETURNING, tag.getBoolean("Returning"));
		this.hand = tag.getBoolean("OffHand") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		this.creativeCopy = tag.getBoolean("CreativeCopy");
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 64.0 * 64.0;
	}
}
