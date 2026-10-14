package dev.remnants.entity;

import dev.remnants.fx.CombatCues;

import dev.remnants.RemnantsConfig;
import dev.remnants.SoulResonance;
import dev.remnants.block.OssuaryGateBlock;
import dev.remnants.registry.ModBlocks;
import dev.remnants.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Archivist, keeper of every memory, boss of the Library (1.5). It floats over its lectern reading until a player
 * comes within {@link #WAKE_RANGE} blocks in its sight (or strikes it), then closes the book over {@link #AWAKEN} ticks.
 * <ul>
 *   <li><b>Phase 1, "Tomes"</b> (to {@link #PHASE2_AT} of its health): keeps 6..12 blocks off, throws flying tomes that
 *   come back like boomerangs ({@link FlyingTomeEntity}), tears Pages from its book ({@link ArchivePageEntity}, at most
 *   {@link #PAGE_CAP}); too close for 2 s it blinks away; right next to it, a burst of pages throws you back.</li>
 *   <li><b>Phase 2, "Eyes"</b>: it rises behind a barrier of four arcs - nothing gets through - and opens four Eyes of the
 *   Archive on the walls ({@link ArchiveEyeEntity}). Each eye closed by a projectile breaks an arc and stuns it for
 *   {@link #STUN} ticks: it falls and takes x1.25. The fourth shatters the barrier ({@link #SHATTER} ticks), then phase 3.
 *   It cannot die in phase 2 (not below {@link #FLOOR} of its health): the puzzle can't be skipped.</li>
 *   <li><b>Phase 3, "Constellation"</b>: spirals of runes ({@link ArchiveRuneEntity}), stars on the floor that burst
 *   ({@link ArchiveStarEntity}), tomes. Below {@link #FRENZY_AT} everything recharges faster.</li>
 * </ul>
 * With nobody to fight for 15 s it floats back to its lectern, whole, and reads again. Its fall slows the world for a
 * moment (singleplayer), the hall exhales its pages, and the Library's treasury gate rises.
 */
public class ArchivistEntity extends Monster implements LookTarget {
	private static final EntityDataAccessor<Boolean> DATA_DORMANT = SynchedEntityData.defineId(ArchivistEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(ArchivistEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(ArchivistEntity.class, EntityDataSerializers.INT);
	/** Ticks the current move lasts: the client fits each clip to it (art 2.0). */
	private static final EntityDataAccessor<Integer> DATA_ANIM_LENGTH = SynchedEntityData.defineId(ArchivistEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(ArchivistEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_BARRIER = SynchedEntityData.defineId(ArchivistEntity.class, EntityDataSerializers.INT);
	public static final int ANIM_NONE = 0, ANIM_AWAKEN = 1, ANIM_THROW = 2, ANIM_SUMMON = 3, ANIM_BURST = 4, ANIM_BLINK = 5, ANIM_RISE = 6,
		ANIM_STUNNED = 7, ANIM_SHATTER = 8, ANIM_SPIRAL = 9, ANIM_STARS = 10;
	public static final int AWAKEN = 40, STUN = 60, SHATTER = 100, SPIRAL = 60, PAGE_CAP = 6, EYES = 4, LOST_TICKS = 300;
	public static final float PHASE2_AT = 0.66F, FRENZY_AT = 0.15F, FLOOR = 0.10F;
	public static final double WAKE_RANGE = 12.0, BURST_RADIUS = 3.0;
	private static int finaleTicks;
	private static float finaleRestore = 20.0F;

	private final ServerBossEvent bossEvent = new dev.remnants.bossbar.StyledBossEvent(Component.translatable("entity.remnants_of_the_past.archivist"),
		BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10, dev.remnants.bossbar.BossStyle.ARCHIVIST);
	private int animTicks, animLength;
	private int tomeCd = 30, summonCd = 120, burstCd, blinkCd = 60, spiralCd = 60, starsCd = 100, closeTicks, lostTicks, repath, eyeCheck;
	private float spiralAngle;
	@Nullable
	private BlockPos home;
	private float homeYaw;
	private final List<UUID> eyes = new ArrayList<>();
	private final Set<UUID> hinted = new HashSet<>();
	/** Client: when the current animation began, and the one before it (the model cross-fades between them). */
	public int clientAnimStart, clientAnimChanged, clientPrevAnimStart;
	public int clientAnim, clientPrevAnim;
	/** Client: real length of the current / previous move in ticks. */
	public int clientAnimLength, clientPrevAnimLength;

	public ArchivistEntity(EntityType<? extends ArchivistEntity> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl(this, 10, true);
		this.setNoGravity(true);
		this.xpReward = 300;
		this.setPersistenceRequired();
		this.bossEvent.setVisible(false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 400.0)
			.add(Attributes.ARMOR, 8.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
			.add(Attributes.FLYING_SPEED, 0.32)
			.add(Attributes.MOVEMENT_SPEED, 0.25)
			.add(Attributes.ATTACK_DAMAGE, 6.0)
			.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_LOOK, -1);
		builder.define(DATA_DORMANT, true);
		builder.define(DATA_ANIM, ANIM_NONE);
		builder.define(DATA_ANIM_LENGTH, 0);
		builder.define(DATA_PHASE, 1);
		builder.define(DATA_BARRIER, 0);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_ANIM.equals(key)) {
			this.clientPrevAnim = this.clientAnim;
			this.clientPrevAnimStart = this.clientAnimStart;
			this.clientPrevAnimLength = this.clientAnimLength;
			this.clientAnim = this.entityData.get(DATA_ANIM);
			this.clientAnimStart = this.tickCount;
			this.clientAnimChanged = this.tickCount;
			this.clientAnimLength = this.entityData.get(DATA_ANIM_LENGTH);
		} else if (DATA_ANIM_LENGTH.equals(key)) {
			this.clientAnimLength = this.entityData.get(DATA_ANIM_LENGTH);
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
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ArchivePageEntity.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, p -> !this.isDormant()));
	}

	// ---- state ---------------------------------------------------------------------------------

	public boolean isDormant() {
		return this.entityData.get(DATA_DORMANT);
	}

	public int getAnim() {
		return this.entityData.get(DATA_ANIM);
	}

	public int phase() {
		return this.entityData.get(DATA_PHASE);
	}

	/** Arcs of the phase-2 barrier still standing (0..4). */
	public int barrier() {
		return this.entityData.get(DATA_BARRIER);
	}

	public boolean isStunned() {
		int a = this.getAnim();
		return a == ANIM_STUNNED || a == ANIM_SHATTER;
	}

	/** Phase 2 with the barrier up and not stunned: nothing gets through. */
	public boolean isShielded() {
		return this.phase() == 2 && this.barrier() > 0 && !this.isStunned();
	}

	private void setAnim(int anim, int length) {
		this.entityData.set(DATA_ANIM_LENGTH, length);
		this.entityData.set(DATA_ANIM, anim);
		this.animTicks = 0;
		this.animLength = length;
		this.telegraph(anim);
	}

	/** Art 2.0 hit feel: the push ring and the tome's lane show on the ground until the moment they come. */
	private void telegraph(int anim) {
		switch (anim) {
			case ANIM_BURST -> CombatCues.disc(this, this.position(), (float) BURST_RADIUS, 10);
			case ANIM_THROW -> CombatCues.lineAttached(this, 0.6F, 12.0F, 1.2F, 7);
			case ANIM_NONE, ANIM_STUNNED, ANIM_SHATTER -> CombatCues.clear(this);
			default -> {
			}
		}
	}

	private int cd(int base) {
		return this.getHealth() < this.getMaxHealth() * FRENZY_AT ? Math.round(base * 0.7F) : base;
	}

	private float damage(float base) {
		return (float) (base * RemnantsConfig.get().archivistDamageMultiplier);
	}

	/** Test hook: begin a move right now. */
	public void forceAnim(int anim) {
		switch (anim) {
			case ANIM_THROW -> this.setAnim(ANIM_THROW, 14);
			case ANIM_SUMMON -> this.setAnim(ANIM_SUMMON, 24);
			case ANIM_SPIRAL -> this.setAnim(ANIM_SPIRAL, SPIRAL);
			case ANIM_STARS -> this.setAnim(ANIM_STARS, 20);
			default -> this.setAnim(anim, 20);
		}
	}

	/** Test hook: jump into phase 2 now (barrier up, eyes on the walls). */
	public void forcePhase2() {
		this.enterPhase2();
	}

	public List<ArchiveEyeEntity> eyes() {
		List<ArchiveEyeEntity> out = new ArrayList<>();
		if (this.level() instanceof ServerLevel level) {
			for (UUID id : this.eyes) {
				if (level.getEntity(id) instanceof ArchiveEyeEntity e && e.isAlive()) {
					out.add(e);
				}
			}
		}
		return out;
	}

	// ---- waking and resting ----------------------------------------------------------------------

	public void awaken(@Nullable LivingEntity by) {
		if (!this.isDormant()) {
			return;
		}
		this.entityData.set(DATA_DORMANT, false);
		this.setAnim(ANIM_AWAKEN, AWAKEN);
		this.playSound(SoundEvents.BOOK_PUT, 2.5F, 0.4F);
		this.playSound(SoundEvents.WITHER_AMBIENT, 0.8F, 1.6F);
		if (by != null && !(by instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			this.setTarget(by);
		}
	}

	/** Client display copies (the Codex portrait): awake, book shut. */
	public void showAwake() {
		this.entityData.set(DATA_DORMANT, false);
	}

	/** Back at the lectern: reading again, whole, phase 1. */
	public void resume() {
		this.removeEyes();
		this.entityData.set(DATA_DORMANT, true);
		this.entityData.set(DATA_PHASE, 1);
		this.entityData.set(DATA_BARRIER, 0);
		this.setAnim(ANIM_NONE, 0);
		this.setTarget(null);
		this.setHealth(this.getMaxHealth());
		this.setNoGravity(true);
		this.getNavigation().stop();
		this.setYRot(this.homeYaw);
		this.yBodyRot = this.homeYaw;
		this.yHeadRot = this.homeYaw;
		this.bossEvent.removeAllPlayers();
		this.hinted.clear();
		this.playSound(SoundEvents.BOOK_PAGE_TURN, 1.5F, 0.6F);
	}

	// ---- the fight -------------------------------------------------------------------------------

	@Override
	protected void customServerAiStep() {
		this.entityData.set(DATA_LOOK, this.getTarget() == null ? -1 : this.getTarget().getId());
		super.customServerAiStep();
		ServerLevel level = (ServerLevel) this.level();
		if (this.home == null) {
			this.home = this.blockPosition();
			this.homeYaw = this.getYRot();
		}
		this.animTicks++;
		if (this.isDormant()) {
			this.getNavigation().stop();
			this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
			if (this.tickCount % 10 == 0) {
				Player near = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), WAKE_RANGE,
					p -> !p.isSpectator() && !((Player) p).isCreative() && this.hasLineOfSight(p));
				if (near != null) {
					this.awaken(near);
				}
			}
			return;
		}
		this.updateBossBar(level);
		this.tomeCd--;
		this.summonCd--;
		this.burstCd--;
		this.blinkCd--;
		this.spiralCd--;
		this.starsCd--;
		LivingEntity target = this.getTarget();
		if (target != null && (!target.isAlive() || (target instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			this.setTarget(null);
			target = null;
		}
		if (target != null) {
			this.lookAt(target);
		}
		if (this.phase() == 1 && this.getHealth() <= this.getMaxHealth() * PHASE2_AT && this.getAnim() != ANIM_AWAKEN) {
			this.enterPhase2();
		}
		if (this.phase() == 2 && this.barrier() > 0 && ++this.eyeCheck % 40 == 0 && this.eyes().stream().noneMatch(ArchiveEyeEntity::isOpen)) {
			this.placeEyes(level, this.barrier());   // came back from an unloaded chunk without its eyes
		}
		int anim = this.getAnim();
		switch (anim) {
			case ANIM_AWAKEN -> {
				this.getNavigation().stop();
				if (this.animTicks % 4 == 0) {
					level.sendParticles(ParticleTypes.ENCHANT, this.getX(), this.getY() + 2.4, this.getZ(), 12, 0.5, 0.6, 0.5, 0.6);
				}
				if (this.animTicks >= this.animLength) {
					this.setAnim(ANIM_NONE, 0);
				}
				return;
			}
			case ANIM_THROW -> {
				this.stopMoving();
				if (this.animTicks == 7 && target != null) {
					this.throwTomes(level, target);
				}
				this.endMove();
				return;
			}
			case ANIM_SUMMON -> {
				this.stopMoving();
				if (this.animTicks == 12) {
					this.summonPages(level, 3);
				}
				this.endMove();
				return;
			}
			case ANIM_BURST -> {
				this.stopMoving();
				if (this.animTicks == 10) {
					this.burst(level);
				}
				this.endMove();
				return;
			}
			case ANIM_BLINK -> {
				this.stopMoving();
				if (this.animTicks == 6) {
					this.blink(level, target);
				}
				this.endMove();
				return;
			}
			case ANIM_RISE -> {
				this.setDeltaMovement(0, 0.06, 0);
				if (this.animTicks == 12) {
					this.placeEyes(level, EYES);
				}
				this.endMove();
				return;
			}
			case ANIM_STUNNED, ANIM_SHATTER -> {
				this.getNavigation().stop();
				this.setNoGravity(false);
				if (this.animTicks % 5 == 0) {
					level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 3.2, this.getZ(), 4, 0.4, 0.1, 0.4, 0.05);
				}
				if (this.animTicks >= this.animLength) {
					this.setNoGravity(true);
					if (this.phase() == 2 && this.barrier() == 0) {
						this.enterPhase3(level);
					} else {
						this.setAnim(ANIM_NONE, 0);
					}
				}
				return;
			}
			case ANIM_SPIRAL -> {
				this.stopMoving();
				if (this.animTicks % 3 == 0) {
					this.spiral(level, target);
				}
				this.endMove();
				return;
			}
			case ANIM_STARS -> {
				this.stopMoving();
				if (this.animTicks == 10) {
					this.placeStars(level);
				}
				this.endMove();
				return;
			}
			default -> {
			}
		}
		if (target == null) {
			this.wanderHome();
			return;
		}
		this.lostTicks = 0;
		this.choose(level, target);
	}

	private void stopMoving() {
		this.getNavigation().stop();
		this.setDeltaMovement(this.getDeltaMovement().scale(0.6));
	}

	private void endMove() {
		if (this.animTicks >= this.animLength) {
			this.setAnim(ANIM_NONE, 0);
		}
	}

	private void lookAt(Entity target) {
		double dx = target.getX() - this.getX(), dz = target.getZ() - this.getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, 10.0F));
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
		this.getLookControl().setLookAt(target, 30.0F, 30.0F);
	}

	private void choose(ServerLevel level, LivingEntity target) {
		double d = this.distanceTo(target);
		this.closeTicks = d < 3.5 ? this.closeTicks + 1 : 0;
		int phase = this.phase();
		if (phase != 2 && this.closeTicks > 40 && this.blinkCd <= 0) {
			this.setAnim(ANIM_BLINK, 12);
			this.blinkCd = this.cd(160);
			this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.6F);
			return;
		}
		if (phase != 2 && d < BURST_RADIUS + 0.5 && this.burstCd <= 0) {
			this.setAnim(ANIM_BURST, 18);
			this.burstCd = this.cd(120);
			this.playSound(SoundEvents.BOOK_PAGE_TURN, 2.0F, 0.5F);
			return;
		}
		if (phase == 3 && this.spiralCd <= 0) {
			this.setAnim(ANIM_SPIRAL, SPIRAL);
			this.spiralCd = this.cd(160) + SPIRAL;
			this.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 2.0F, 0.6F);
			return;
		}
		if (phase == 3 && this.starsCd <= 0) {
			this.setAnim(ANIM_STARS, 20);
			this.starsCd = this.cd(140);
			this.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 2.0F, 1.4F);
			return;
		}
		if (this.tomeCd <= 0 && this.hasLineOfSight(target) && d < 18.0) {
			this.setAnim(ANIM_THROW, 14);
			this.tomeCd = this.cd(phase == 1 ? 50 : phase == 2 ? 40 : 60);
			return;
		}
		if (phase != 3 && this.summonCd <= 0 && this.pages(level) < PAGE_CAP) {
			this.setAnim(ANIM_SUMMON, 24);
			this.summonCd = this.cd(phase == 1 ? 240 : 200);
			this.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.5F, 0.8F);
			return;
		}
		this.hover(level, target);
	}

	/** Keeps 6..12 blocks off its target, a little above it (phase 2: high over the hall's middle). */
	private void hover(ServerLevel level, LivingEntity target) {
		if (--this.repath > 0) {
			return;
		}
		this.repath = 18 + this.random.nextInt(14);
		if (this.phase() == 2 && this.home != null) {
			Vec3 c = Vec3.atBottomCenterOf(this.home);
			double a = this.random.nextDouble() * Math.PI * 2;
			this.moveControl.setWantedPosition(c.x + Math.cos(a) * 2.5, this.home.getY() + 4.5 + this.random.nextDouble(), c.z + Math.sin(a) * 2.5, 0.7);
			return;
		}
		double keepMin = this.phase() == 3 ? 5.0 : 6.0, keepMax = this.phase() == 3 ? 9.0 : 12.0;
		for (int attempt = 0; attempt < 10; attempt++) {
			double a = Math.atan2(this.getZ() - target.getZ(), this.getX() - target.getX()) + (this.random.nextDouble() - 0.5) * 1.8;
			double r = keepMin + this.random.nextDouble() * (keepMax - keepMin);
			Vec3 p = new Vec3(target.getX() + Math.cos(a) * r, target.getY() + 1.5 + this.random.nextDouble() * 1.5, target.getZ() + Math.sin(a) * r);
			if (!this.roomAt(level, p)) {
				continue;
			}
			if (level.clip(new ClipContext(p.add(0, 2.4, 0), target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
				.getType() == HitResult.Type.MISS) {
				this.moveControl.setWantedPosition(p.x, p.y, p.z, this.distanceTo(target) < keepMin ? 1.3 : 0.9);
				return;
			}
		}
		this.moveControl.setWantedPosition(target.getX(), target.getY() + 2.5, target.getZ(), 0.6);
	}

	/** Room for its 1.2 x 3.4 body at {@code p}. */
	private boolean roomAt(ServerLevel level, Vec3 p) {
		AABB box = this.getType().getDimensions().makeBoundingBox(p.x, p.y, p.z);
		return level.noCollision(this, box);
	}

	private void wanderHome() {
		// lost its target, but someone is still close by (knocked back, out of sight a moment): no reset, take them up again
		if (this.tickCount % 10 == 0) {
			Player near = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 24.0,
				p -> !p.isSpectator() && !((Player) p).isCreative());
			if (near != null) {
				this.setTarget(near);
				this.lostTicks = 0;
				return;
			}
		}
		if (++this.lostTicks < LOST_TICKS || this.home == null) {
			this.getNavigation().stop();
			this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
			return;
		}
		Vec3 h = Vec3.atBottomCenterOf(this.home);
		if (this.position().distanceToSqr(h) < 1.5) {
			this.moveTo(h.x, h.y, h.z, this.homeYaw, 0.0F);
			this.resume();
		} else if (this.tickCount % 20 == 0) {
			this.moveControl.setWantedPosition(h.x, h.y, h.z, 0.8);
		}
	}

	private int pages(ServerLevel level) {
		return level.getEntitiesOfClass(ArchivePageEntity.class, this.getBoundingBox().inflate(32.0), Entity::isAlive).size();
	}

	// ---- moves -----------------------------------------------------------------------------------

	private Vec3 hand() {
		Vec3 f = Vec3.directionFromRotation(0, this.getYRot());
		return this.position().add(f.x * 0.8, 2.3, f.z * 0.8);
	}

	private void throwTomes(ServerLevel level, LivingEntity target) {
		Vec3 from = this.hand();
		Vec3 dir = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(from);
		if (this.phase() == 2) {   // a fan of two
			FlyingTomeEntity.throwAt(level, this, from, dir.yRot((float) Math.toRadians(14)));
			FlyingTomeEntity.throwAt(level, this, from, dir.yRot((float) Math.toRadians(-14)));
		} else {
			FlyingTomeEntity.throwAt(level, this, from, dir);
		}
	}

	/** A Page tears loose at {@code at} (a tome turning back), if the book still has room for more. */
	public void tearPage(Vec3 at) {
		if (this.level() instanceof ServerLevel level && this.pages(level) < PAGE_CAP) {
			this.spawnPage(level, at);
		}
	}

	private void summonPages(ServerLevel level, int n) {
		this.playSound(SoundEvents.BOOK_PAGE_TURN, 2.0F, 0.7F);
		int room = PAGE_CAP - this.pages(level);
		for (int i = 0; i < Math.min(n, room); i++) {
			double a = i / (double) n * Math.PI * 2 + this.random.nextDouble();
			this.spawnPage(level, this.position().add(Math.cos(a) * 1.2, 2.2, Math.sin(a) * 1.2));
		}
	}

	private void spawnPage(ServerLevel level, Vec3 at) {
		ArchivePageEntity page = ModEntities.ARCHIVE_PAGE.create(level);
		if (page == null) {
			return;
		}
		page.moveTo(at.x, at.y, at.z, this.random.nextFloat() * 360.0F, 0.0F);
		page.setTarget(this.getTarget());
		level.addFreshEntity(page);
		level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.5);
	}

	private boolean foe(LivingEntity e) {
		return e != this && e.isAlive() && !(e instanceof ArchivePageEntity) && !(e instanceof ArchiveEyeEntity)
			&& !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	/** A burst of pages: everything within 3 blocks takes 4 and is thrown back hard. */
	private void burst(ServerLevel level) {
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(BURST_RADIUS, 1.0, BURST_RADIUS), this::foe)) {
			if (this.distanceTo(e) <= BURST_RADIUS + e.getBbWidth()) {
				if (e.hurt(this.damageSources().indirectMagic(this, this), this.damage(4.0F))) {
					Vec3 push = new Vec3(e.getX() - this.getX(), 0, e.getZ() - this.getZ()).normalize().scale(1.6);
					e.push(push.x, 0.45, push.z);
					e.hurtMarked = true;
				}
			}
		}
		for (int i = 0; i < 24; i++) {
			double a = i / 24.0 * Math.PI * 2;
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)),
				this.getX() + Math.cos(a) * 1.5, this.getY() + 1.4, this.getZ() + Math.sin(a) * 1.5, 1, Math.cos(a) * 0.4, 0.05, Math.sin(a) * 0.4, 0.3);
		}
		this.playSound(SoundEvents.ENDER_DRAGON_FLAP, 2.0F, 1.2F);
		this.playSound(SoundEvents.BOOK_PAGE_TURN, 2.0F, 0.6F);
		CombatCues.impact(this, null, this.position(), 0.55F, (float) BURST_RADIUS + 0.5F, 2);
	}

	/** Leafing away: gone in a flurry of pages, back 8 blocks off. */
	private void blink(ServerLevel level, @Nullable LivingEntity target) {
		Vec3 around = target != null ? target.position() : this.position();
		for (int attempt = 0; attempt < 16; attempt++) {
			double a = this.random.nextDouble() * Math.PI * 2;
			Vec3 p = new Vec3(around.x + Math.cos(a) * 8.0, around.y + 1.0 + this.random.nextDouble() * 2.0, around.z + Math.sin(a) * 8.0);
			if (this.roomAt(level, p)) {
				level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)), this.getX(), this.getY() + 1.6, this.getZ(),
					30, 0.5, 1.0, 0.5, 0.1);
				this.teleportTo(p.x, p.y, p.z);
				level.sendParticles(ParticleTypes.ENCHANT, p.x, p.y + 1.6, p.z, 30, 0.5, 1.0, 0.5, 0.6);
				this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.8F);
				this.closeTicks = 0;
				return;
			}
		}
	}

	private void spiral(ServerLevel level, @Nullable LivingEntity target) {
		Vec3 from = this.position().add(0, 1.6, 0);
		double dy = target == null ? 0 : Mth.clamp((target.getY() + 1.0 - from.y) / 7.0, -0.25, 0.25);
		for (int arm = 0; arm < 2; arm++) {
			double a = Math.toRadians(this.spiralAngle + arm * 180.0);
			ArchiveRuneEntity.fire(level, this, from, new Vec3(Math.cos(a), dy, Math.sin(a)));
		}
		this.spiralAngle += 23.0F;
		if (this.animTicks % 9 == 0) {
			this.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 1.4F);
		}
	}

	private void placeStars(ServerLevel level) {
		List<ServerPlayer> players = level.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceToSqr(this) < 24 * 24);
		int n = Math.min(8, 4 + Math.max(0, players.size() - 1));
		for (int i = 0; i < n; i++) {
			Vec3 c = players.isEmpty() ? this.position() : players.get(i % players.size()).position();
			double spread = i < players.size() ? 0.6 : 3.0;
			double x = c.x + (this.random.nextDouble() - 0.5) * 2 * spread, z = c.z + (this.random.nextDouble() - 0.5) * 2 * spread;
			double y = this.floor(level, x, c.y + 1.0, z);
			ArchiveStarEntity.place(level, this, x, y, z);
			CombatCues.disc(this, new Vec3(x, y, z), (float) ArchiveStarEntity.RADIUS, ArchiveStarEntity.FUSE);
		}
		this.playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 2.0F, 1.2F);
	}

	/** The top of the floor under (x, from, z), at most 8 blocks down. */
	private double floor(ServerLevel level, double x, double from, double z) {
		BlockPos.MutableBlockPos p = BlockPos.containing(x, from, z).mutable();
		for (int i = 0; i < 8; i++) {
			BlockState s = level.getBlockState(p.below());
			if (!s.getCollisionShape(level, p.below()).isEmpty()) {
				return p.getY();
			}
			p.move(0, -1, 0);
		}
		return Math.floor(from) - 1;
	}

	// ---- phase 2: the barrier and the eyes ---------------------------------------------------------

	private void enterPhase2() {
		this.entityData.set(DATA_PHASE, 2);
		this.entityData.set(DATA_BARRIER, EYES);
		this.setAnim(ANIM_RISE, 40);
		this.playSound(SoundEvents.BEACON_ACTIVATE, 2.5F, 0.5F);
		this.playSound(SoundEvents.WITHER_SPAWN, 0.6F, 1.6F);
		this.title("event.remnants.archivist.eyes", "event.remnants.archivist.eyes.sub");
	}

	/** Opens {@code count} eyes on the walls round the hall's middle (where it reads). */
	private void placeEyes(ServerLevel level, int count) {
		this.removeEyes();
		Vec3 c = this.home != null ? Vec3.atBottomCenterOf(this.home) : this.position();
		double floorY = this.floor(level, c.x, c.y + 0.5, c.z);
		double base = this.random.nextDouble() * Math.PI * 2;
		for (int i = 0; i < count; i++) {
			double a = base + i * Math.PI * 2 / EYES + (this.random.nextDouble() - 0.5) * 0.5;
			Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
			double y = floorY + 3 + this.random.nextInt(4);
			Vec3 at = null;
			for (double d = 4.0; d <= 16.0; d += 0.5) {
				Vec3 p = new Vec3(c.x + dir.x * d, y, c.z + dir.z * d);
				BlockPos bp = BlockPos.containing(p);
				if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
					at = new Vec3(c.x + dir.x * (d - 0.7), y, c.z + dir.z * (d - 0.7));
					break;
				}
			}
			if (at == null) {
				at = new Vec3(c.x + dir.x * 9.0, y, c.z + dir.z * 9.0);
			}
			ArchiveEyeEntity eye = ModEntities.ARCHIVE_EYE.create(level);
			if (eye == null) {
				continue;
			}
			eye.moveTo(at.x, at.y - 0.45, at.z, 0.0F, 0.0F);
			eye.lookToward(c);
			eye.setOwner(this);
			level.addFreshEntity(eye);
			this.eyes.add(eye.getUUID());
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.03);
		}
		this.playSound(SoundEvents.ENDER_EYE_LAUNCH, 2.0F, 0.6F);
	}

	private void removeEyes() {
		if (this.level() instanceof ServerLevel level) {
			for (UUID id : this.eyes) {
				Entity e = level.getEntity(id);
				if (e != null) {
					e.discard();
				}
			}
		}
		this.eyes.clear();
	}

	/** An eye was closed: an arc breaks and it reels; the last one shatters the barrier. */
	public void onEyeClosed(ArchiveEyeEntity eye, @Nullable ServerPlayer by) {
		if (this.phase() != 2 || this.barrier() <= 0) {
			return;
		}
		int left = this.barrier() - 1;
		this.entityData.set(DATA_BARRIER, left);
		ServerLevel level = (ServerLevel) this.level();
		level.sendParticles(ParticleTypes.ENCHANT, this.getX(), this.getY() + 1.8, this.getZ(), 40, 1.0, 1.2, 1.0, 1.0);
		if (left > 0) {
			this.setAnim(ANIM_STUNNED, STUN);
			this.playSound(SoundEvents.GLASS_BREAK, 2.0F, 0.6F);
		} else {
			this.setAnim(ANIM_SHATTER, SHATTER);
			this.playSound(SoundEvents.GLASS_BREAK, 2.5F, 0.4F);
			this.playSound(SoundEvents.WITHER_HURT, 1.5F, 0.6F);
			level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.8, this.getZ(), 60, 1.2, 1.4, 1.2, 0.1);
		}
		this.getNavigation().stop();
		for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(this) < 48 * 48)) {
			p.displayClientMessage(Component.translatable("message.remnants.archivist.eye", EYES - left, EYES).withStyle(ChatFormatting.AQUA), true);
		}
	}

	private void enterPhase3(ServerLevel level) {
		this.entityData.set(DATA_PHASE, 3);
		this.setAnim(ANIM_NONE, 0);
		this.removeEyes();
		this.spiralCd = 40;
		this.starsCd = 90;
		this.playSound(SoundEvents.WITHER_AMBIENT, 2.0F, 0.5F);
		this.title("event.remnants.archivist.stars", "event.remnants.archivist.stars.sub");
	}

	private void title(String title, String sub) {
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(this) < 48 * 48)) {
			p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
			p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(sub).withStyle(ChatFormatting.GRAY)));
			p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(title).withStyle(ChatFormatting.LIGHT_PURPLE)));
		}
	}

	// ---- taking hits -----------------------------------------------------------------------------

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (this.level().isClientSide || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurt(source, amount);
		}
		if (this.isDormant() || this.getAnim() == ANIM_AWAKEN) {
			if (this.isDormant() && source.getEntity() instanceof LivingEntity by) {
				this.awaken(by);
			}
			this.playSound(SoundEvents.BOOK_PAGE_TURN, 1.2F, 0.5F);
			return false;
		}
		if (this.isShielded()) {
			this.playSound(SoundEvents.AMETHYST_BLOCK_HIT, 1.5F, 0.5F);
			if (this.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.ENCHANT, this.getX(), this.getY() + 1.8, this.getZ(), 10, 0.8, 1.0, 0.8, 0.4);
			}
			if (source.getEntity() instanceof ServerPlayer player && this.hinted.add(player.getUUID())) {
				player.displayClientMessage(Component.translatable("message.remnants.archivist.barrier").withStyle(ChatFormatting.GRAY), true);
			}
			return false;
		}
		if (this.isStunned()) {
			amount *= 1.25F;
		}
		if (this.phase() == 2) {   // no dying before the eyes are shut
			amount = Math.min(amount, this.getHealth() - this.getMaxHealth() * FLOOR);
			if (amount <= 0) {
				return false;
			}
		}
		return super.hurt(source, amount);
	}

	@Override
	public void knockback(double strength, double x, double z) {
		super.knockback(strength * 0.3, x, z);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.removeEyes();
		this.bossEvent.removeAllPlayers();
		SoulResonance.onArchivistSlain(this);
		// the hall exhales its pages
		for (int i = 0; i < 6; i++) {
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)), this.getX(), this.getY() + 1.8, this.getZ(),
				40, 2.5, 1.8, 2.5, 0.15);
		}
		level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.8, this.getZ(), 80, 1.0, 1.5, 1.0, 0.1);
		level.playSound(null, this.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 0.8F, 1.4F);
		level.playSound(null, this.blockPosition(), SoundEvents.BOOK_PUT, SoundSource.HOSTILE, 3.0F, 0.4F);
		for (ArchivePageEntity page : level.getEntitiesOfClass(ArchivePageEntity.class, this.getBoundingBox().inflate(48.0), Entity::isAlive)) {
			page.crumble(level);
		}
		for (Entity e : level.getEntitiesOfClass(Entity.class, this.getBoundingBox().inflate(48.0),
			e -> e instanceof ArchiveStarEntity || e instanceof ArchiveRuneEntity || e instanceof FlyingTomeEntity)) {
			e.discard();
		}
		this.openTreasury(level);
		MinecraftServer server = level.getServer();
		if (RemnantsConfig.get().bossFinaleSlowMotion && server.isSingleplayer() && finaleTicks == 0) {
			finaleRestore = server.tickRateManager().tickrate();
			server.tickRateManager().setTickRate(6.0F);
			finaleTicks = 7;   // about a second at 6 ticks a second
		}
	}

	/** End of every server tick: the finale's slow world speeds back up. */
	public static void finaleTick(MinecraftServer server) {
		if (finaleTicks > 0 && --finaleTicks == 0) {
			server.tickRateManager().setTickRate(finaleRestore);
		}
	}

	/** Server stopped mid-finale: the next world starts at normal speed. */
	public static void resetFinale() {
		finaleTicks = 0;
		finaleRestore = 20.0F;
	}

	/** The Library's treasury: every closed Ossuary Gate near its lectern rises. */
	private void openTreasury(ServerLevel level) {
		BlockPos c = this.home != null ? this.home : this.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-16, -6, -16), c.offset(16, 6, 16))) {
			BlockState s = level.getBlockState(p);
			if (s.is(ModBlocks.OSSUARY_GATE) && !s.getValue(OssuaryGateBlock.OPEN)) {
				OssuaryGateBlock.open(level, p.immutable());
			}
		}
	}

	// ---- boss bar ------------------------------------------------------------------------------

	private void updateBossBar(ServerLevel level) {
		this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
		this.bossEvent.setColor(switch (this.phase()) {
			case 2 -> BossEvent.BossBarColor.BLUE;
			case 3 -> BossEvent.BossBarColor.RED;
			default -> BossEvent.BossBarColor.PURPLE;
		});
		this.bossEvent.setVisible(true);
		if (this.tickCount % 10 == 0) {
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(this) < 40 * 40 && !p.isSpectator()) {
					this.bossEvent.addPlayer(p);
				} else {
					this.bossEvent.removePlayer(p);
				}
			}
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bossEvent.removePlayer(player);
	}

	@Override
	public void remove(RemovalReason reason) {
		this.bossEvent.removeAllPlayers();
		if (reason.shouldDestroy()) {
			this.removeEyes();
		}
		super.remove(reason);
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		super.setCustomName(name);
		this.bossEvent.setName(this.getDisplayName());
	}

	// ---- plumbing ------------------------------------------------------------------------------

	@Override
	@Nullable
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, @Nullable SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, type, data);
		AttributeInstance hp = this.getAttribute(Attributes.MAX_HEALTH);
		if (hp != null) {
			hp.setBaseValue(RemnantsConfig.get().archivistHealth);
		}
		this.setHealth(this.getMaxHealth());
		this.home = this.blockPosition();
		this.homeYaw = this.getYRot();
		return out;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof ArchivePageEntity || super.isAlliedTo(other);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.isDormant() ? SoundEvents.BOOK_PAGE_TURN : SoundEvents.SOUL_ESCAPE.value();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WITHER_SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WITHER_SKELETON_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.6F;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("Dormant", this.isDormant());
		tag.putInt("Phase", this.phase());
		tag.putInt("Barrier", this.barrier());
		if (this.home != null) {
			tag.putIntArray("Home", new int[] {this.home.getX(), this.home.getY(), this.home.getZ()});
			tag.putFloat("HomeYaw", this.homeYaw);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.entityData.set(DATA_DORMANT, !tag.contains("Dormant") || tag.getBoolean("Dormant"));
		this.entityData.set(DATA_PHASE, Math.max(1, tag.getInt("Phase")));
		this.entityData.set(DATA_BARRIER, tag.getInt("Barrier"));
		int[] h = tag.getIntArray("Home");
		if (h.length == 3) {
			this.home = new BlockPos(h[0], h[1], h[2]);
			this.homeYaw = tag.getFloat("HomeYaw");
		}
		this.bossEvent.setName(this.getDisplayName());
	}

	@Override
	protected net.minecraft.world.entity.ai.control.BodyRotationControl createBodyControl() {
		return new dev.remnants.entity.ai.FaceBody(this);
	}

	@Override
	public int lookTargetId() {
		return this.entityData.get(DATA_LOOK);
	}
}
