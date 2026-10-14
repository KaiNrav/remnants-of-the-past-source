package dev.remnants.entity.ai;

import dev.remnants.RemnantsConfig;
import dev.remnants.entity.PhantomArrowEntity;
import dev.remnants.entity.ForgottenVariant;
import dev.remnants.entity.RemnantEntity;
import dev.remnants.entity.SpectralWeaponEntity;
import dev.remnants.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.remnants.entity.ai.CombatStyle.AXE;
import static dev.remnants.entity.ai.CombatStyle.BOW;
import static dev.remnants.entity.ai.CombatStyle.CROSSBOW;
import static dev.remnants.entity.ai.CombatStyle.FISTS;
import static dev.remnants.entity.ai.CombatStyle.MACE;
import static dev.remnants.entity.ai.CombatStyle.SWORD;
import static dev.remnants.entity.ai.CombatStyle.TRIDENT;

/** Every boss move. Each brain gets its own instances (moves keep per-cast state). */
public final class Abilities {
	private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0F, 0.15F, 0.15F), 1.3F);

	public static List<Ability> create() {
		List<Ability> list = new ArrayList<>();
		// universal
		list.add(new PhantomStep());
		list.add(new SoulNova());
		list.add(new ShieldWall());
		list.add(new MirrorEchoes());
		// melee
		list.add(new SoulChain());
		list.add(new PhantomArmory());
		list.add(new Riposte());
		list.add(new Lunge());
		list.add(new Cleave());
		list.add(new Skyfall());
		list.add(new GaleBurst());
		list.add(new SpearVolley());
		list.add(new StormCall());
		list.add(new Uppercut());
		// ranged
		list.add(new Volley());
		list.add(new PiercingShot());
		list.add(new RainOfSouls());
		list.add(new Burst());
		list.add(new SoulBolt());
		// signature moves of the biome Forgotten (1.2)
		list.add(new Harpoon());
		list.add(new FrostBreath());
		list.add(new Burrow());
		return list;
	}

	private static Vec3 flatDir(Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from).multiply(1, 0, 1);
		return d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
	}

	/** Nothing solid between two standing spots at knee and chest height (no blinking or burrowing into closed rooms). */
	static boolean reachable(RemnantBrain b, Vec3 from, Vec3 to) {
		for (double h : new double[]{0.5, 1.5}) {
			if (b.boss.level().clip(new net.minecraft.world.level.ClipContext(from.add(0, h, 0), to.add(0, h, 0),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, b.boss)).getType()
				!= net.minecraft.world.phys.HitResult.Type.MISS) {
				return false;
			}
		}
		return true;
	}

	private static ItemStack weaponOrSword(RemnantEntity boss) {
		ItemStack main = boss.getMainHandItem();
		return main.isEmpty() ? new ItemStack(Items.IRON_SWORD) : main;
	}

	// =================================================================================================
	// Universal
	// =================================================================================================

	/** Blink: melee Remnants appear behind you; ranged ones blink away when you close in. Counter: keep turning. */
	public static final class PhantomStep extends Ability {
		private Vec3 dest = Vec3.ZERO;

		PhantomStep() {
			super("phantom_step", 110, 1);
			this.rank = 3;
		}

		@Override
		public double score(RemnantBrain b) {
			double d = b.dist();
			if (b.style().ranged) {
				return d < 5 ? 3.0 : d < 8 ? 1.0 : 0;
			}
			double s = 0;
			if (d > 8) {
				s += b.kite * 4 + (d > 14 ? 1.5 : 0) + (b.targetUsingBow() ? 2 : 0);
			}
			if (b.target.isBlocking() && d < 6) {
				s += 1 + b.block * 3;
			}
			return s;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 12;
		}

		private Vec3 pick(RemnantBrain b) {
			LivingEntity t = b.target;
			if (b.style().ranged) {
				Vec3 away = flatDir(t.position(), b.boss.position()).yRot((b.boss.getRandom().nextFloat() - 0.5F) * 2.0F);
				return t.position().add(away.scale(12));
			}
			return t.position().subtract(t.getLookAngle().multiply(1, 0, 1).normalize().scale(2.2));
		}

		/** A player's own Remnant comes for you anywhere; a Forgotten (a natural monster) won't blink into a closed room. */
		private boolean allowed(RemnantBrain b, Vec3 to) {
			return !b.boss.isForgotten() || reachable(b, b.boss.position(), to);
		}

		@Override
		public void start(RemnantBrain b) {
			this.dest = this.pick(b);
			if (!this.allowed(b, this.dest)) {
				this.dest = b.boss.position();
			}
			b.boss.setAnim(RemnantEntity.ANIM_CAST);
			b.boss.getNavigation().stop();
			b.boss.sound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.5F);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			ServerLevel level = b.boss.serverLevel();
			if (t == 6 && !b.style().ranged) {
				Vec3 again = this.pick(b);
				if (this.allowed(b, again)) {
					this.dest = again;
				}
			}
			if (t % 2 == 0) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.dest.x, this.dest.y + 1, this.dest.z, 8, 0.25, 0.6, 0.25, 0.02);
				level.sendParticles(ModParticles.REMNANT_SOUL, b.boss.getX(), b.boss.getY() + 1, b.boss.getZ(), 4, 0.3, 0.6, 0.3, 0.02);
			}
			if (t == 8) {
				Vec3 from = b.boss.position();
				if (b.boss.randomTeleport(this.dest.x, this.dest.y + 1, this.dest.z, false)) {
					level.sendParticles(ParticleTypes.SOUL, from.x, from.y + 1, from.z, 20, 0.3, 0.6, 0.3, 0.05);
					level.sendParticles(ParticleTypes.SOUL, this.dest.x, this.dest.y + 1, this.dest.z, 20, 0.3, 0.6, 0.3, 0.05);
					b.boss.sound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.7F);
				}
				b.boss.getLookControl().setLookAt(b.target, 180, 180);
				if (!b.style().ranged) {
					b.attackCooldown = 0;
				}
			}
		}
	}

	/** Shockwave ring that expands along the ground. Counter: jump over the ring. Phase 3: a second ring follows. */
	public static final class SoulNova extends Ability {
		private Vec3 center = Vec3.ZERO;
		private final Set<UUID> hit1 = new HashSet<>();
		private final Set<UUID> hit2 = new HashSet<>();

		SoulNova() {
			super("soul_nova", 240, 1);
			this.rank = 3;
			this.recovery = 25;
		}

		@Override
		public double score(RemnantBrain b) {
			if (b.boss.playersNear(b.boss.position(), 5.5).isEmpty()) {
				return 0;
			}
			return 1.0 + b.aggression * 3 + b.block * 2 + (b.style() == FISTS ? 0.8 : 0) + (b.phase() >= 2 ? 0.4 : 0);
		}

		@Override
		public int duration(RemnantBrain b) {
			return b.phase() >= 3 ? 52 : 40;
		}

		@Override
		public void start(RemnantBrain b) {
			this.center = b.boss.position();
			this.hit1.clear();
			this.hit2.clear();
			b.boss.setAnim(RemnantEntity.ANIM_NOVA);
			b.boss.getNavigation().stop();
			b.boss.sound(SoundEvents.WARDEN_SONIC_CHARGE, 1.5F, 1.3F);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			if (t < 18) {
				b.boss.ring(this.center, 4.5 - t * 0.22, ParticleTypes.SOUL_FIRE_FLAME, 24);
				return;
			}
			if (t == 18) {
				b.boss.sound(SoundEvents.WARDEN_SONIC_BOOM, 1.2F, 1.5F);
			}
			this.wave(b, t - 18, this.hit1);
			if (b.phase() >= 3) {
				this.wave(b, t - 30, this.hit2);
			}
		}

		private void wave(RemnantBrain b, int step, Set<UUID> hit) {
			if (step < 0) {
				return;
			}
			double r = step * 0.55;
			if (r > 9.5) {
				return;
			}
			b.boss.ring(this.center, r, ParticleTypes.SOUL_FIRE_FLAME, (int) (r * 9) + 8);
			b.boss.ring(this.center, r, ModParticles.REMNANT_SOUL, (int) (r * 4) + 4);
			for (Player p : b.boss.playersNear(this.center, r + 1.2)) {
				double d = p.position().multiply(1, 0, 1).distanceTo(this.center.multiply(1, 0, 1));
				if (Math.abs(d - r) < 0.9 && p.onGround() && Math.abs(p.getY() - this.center.y) < 1.5 && hit.add(p.getUUID())) {
					b.boss.abilityHit(p, 3.0F + b.boss.weaponDamage() * 0.5F);
					b.boss.push(p, flatDir(this.center, p.position()).scale(0.9).add(0, 0.45, 0));
				}
			}
		}
	}

	/** Raises the off-hand shield: frontal hits and arrows bounce off. Counter: an axe breaks it and stuns. */
	public static final class ShieldWall extends Ability {
		ShieldWall() {
			super("shield_wall", 100, 1);
			this.rank = 2;
		}

		@Override
		public double score(RemnantBrain b) {
			if (!b.boss.canRaiseShield()) {
				return 0;
			}
			return (b.targetUsingBow() ? 3.0 + b.bowUse * 3 : 0) + (b.aggression > 0.5 ? 1.5 : 0);
		}

		@Override
		public int duration(RemnantBrain b) {
			return 40;
		}

		@Override
		public void start(RemnantBrain b) {
			b.boss.setShieldUp(true);
			b.boss.sound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0F, 0.6F);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			if (!b.boss.isShieldUp()) {
				b.finishAbility();
			}
		}

		@Override
		public void stop(RemnantBrain b) {
			b.boss.setShieldUp(false);
		}

		@Override
		public boolean allowsMovement(int t) {
			return true;
		}
	}

	/** Phase 2+: one-hit decoys appear in a circle around you; the real Remnant hides among them. */
	public static final class MirrorEchoes extends Ability {
		MirrorEchoes() {
			super("mirror_echoes", 600, 2);
			this.rank = 4;
		}

		@Override
		public double score(RemnantBrain b) {
			return RemnantsConfig.get().illusions && b.boss.illusionCount() == 0 ? 0.9 + b.phase() * 0.3 : 0;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 20;
		}

		@Override
		public void start(RemnantBrain b) {
			b.boss.setAnim(RemnantEntity.ANIM_CAST);
			b.boss.getNavigation().stop();
			b.boss.sound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.5F, 0.8F);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			b.boss.serverLevel().sendParticles(ModParticles.REMNANT_SOUL, b.boss.getX(), b.boss.getY() + 1, b.boss.getZ(), 4, 0.6, 0.8, 0.6, 0.05);
			if (t == 12) {
				b.boss.spawnIllusions(2);
			}
		}
	}

	// =================================================================================================
	// Melee
	// =================================================================================================

	/** A soul chain forms between you, then yanks you in. Counter: break line of sight before it snaps. */
	public static final class SoulChain extends Ability {
		SoulChain() {
			super("soul_chain", 200, 1, SWORD, AXE, TRIDENT, MACE, FISTS);
			this.rank = 3;
		}

		@Override
		public double score(RemnantBrain b) {
			double d = b.dist();
			if (d < 7 || d > 24 || !b.canSee()) {
				return 0;
			}
			return 0.8 + b.kite * 4 + b.bowUse * 3 + (b.target.getY() - b.boss.getY() > 2 ? 1 : 0);
		}

		@Override
		public int duration(RemnantBrain b) {
			return 20;
		}

		@Override
		public void start(RemnantBrain b) {
			b.boss.setAnim(RemnantEntity.ANIM_CAST);
			b.boss.getNavigation().stop();
			b.boss.sound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.2F, 0.7F);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			LivingEntity target = b.target;
			b.boss.getLookControl().setLookAt(target, 60, 60);
			if (t < 14 && t % 2 == 0) {
				b.boss.line(b.boss.getEyePosition(), target.getEyePosition().subtract(0, 0.3, 0), t < 10 ? ModParticles.REMNANT_SOUL : ParticleTypes.SOUL_FIRE_FLAME, 0.6);
			}
			if (t == 14) {
				if (b.canSee()) {
					Vec3 v = b.boss.position().subtract(target.position());
					double d = v.length();
					b.boss.push(target, v.normalize().scale(Math.min(2.4, 0.5 + d * 0.11)).add(0, 0.42, 0));
					target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), b.boss);
					b.boss.abilityHit(target, 1.0F);
					b.boss.sound(SoundEvents.CHAIN_BREAK, 1.5F, 0.6F);
					b.attackCooldown = 6;
				} else {
					b.boss.sound(SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.6F);
				}
			}
		}
	}

	/** Ghost copies of the weapon gather in an arc above the Remnant, then fire at you one by one. Counter: strafe. */
	public static final class PhantomArmory extends Ability {
		private int count;

		PhantomArmory() {
			super("phantom_armory", 280, 1, SWORD, AXE, TRIDENT, MACE, FISTS);
			this.rank = 4;
			this.recovery = 15;
		}

		@Override
		public double score(RemnantBrain b) {
			double d = b.dist();
			if (d < 4 || d > 22 || !b.canSee()) {
				return 0;
			}
			return 1.0 + b.phase() * 0.35 + b.kite * 1.5 + b.block;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 24 + this.count * 5;
		}

		@Override
		public void start(RemnantBrain b) {
			this.count = 2 + b.phase() + (b.boss.isEmpowered() ? 1 : 0);
			b.boss.setAnim(RemnantEntity.ANIM_RAISE);
			b.boss.getNavigation().stop();
			b.boss.sound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.5F, 0.8F);
			ItemStack item = weaponOrSword(b.boss);
			float damage = 2.0F + b.boss.weaponDamage() * 0.4F;
			for (int i = 0; i < this.count; i++) {
				double a = Math.toRadians(-80 + 160.0 * i / Math.max(1, this.count - 1));
				Vec3 offset = new Vec3(Math.sin(a) * 1.9, 1.9 + Math.cos(a) * 0.5, -0.5);
				SpectralWeaponEntity blade = SpectralWeaponEntity.hovering(b.boss, item, offset, 22 + i * 5, b.target,
					damage * b.boss.abilityScale(), b.phase() >= 3 ? 1.9 : 1.6);
				b.boss.level().addFreshEntity(blade);
			}
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			if (t == 20) {
				b.boss.setAnim(RemnantEntity.ANIM_CAST);
			}
		}

		@Override
		public boolean allowsMovement(int t) {
			return t > 20;
		}
	}

	/** Glowing counter stance. Hit it and it teleports behind you and punishes. Counter: wait it out - then it lowers
	 * its guard (recovery window). */
	public static final class Riposte extends Ability {
		boolean landed;

		Riposte() {
			super("riposte", 260, 1, SWORD, AXE, TRIDENT, MACE, FISTS);
			this.rank = 3;
		}

		@Override
		public int recovery(RemnantBrain b) {
			return this.landed ? 0 : 30;
		}

		@Override
		public double score(RemnantBrain b) {
			return b.dist() < 6 ? b.aggression * 5 : 0;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 34;
		}

		@Override
		public void start(RemnantBrain b) {
			this.landed = false;
			b.boss.setStance(34);
			b.boss.setAnim(RemnantEntity.ANIM_STANCE);
			b.boss.getNavigation().stop();
			b.boss.sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 0.5F);
			b.boss.sound(SoundEvents.BELL_RESONATE, 0.8F, 1.5F);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			b.boss.getLookControl().setLookAt(b.target, 60, 60);
			if (t % 3 == 0) {
				b.boss.serverLevel().sendParticles(ParticleTypes.ENCHANTED_HIT, b.boss.getX(), b.boss.getY() + 1.2, b.boss.getZ(), 4, 0.4, 0.5, 0.4, 0.1);
			}
		}

		@Override
		public void stop(RemnantBrain b) {
			b.boss.setStance(0);
		}
	}

	/** Sword / trident: a fast dash through you. Trident version is a riptide spin. Counter: sidestep. */
	public static final class Lunge extends Ability {
		private Vec3 dir = Vec3.ZERO;
		private final Set<UUID> hit = new HashSet<>();

		Lunge() {
			super("lunge", 120, 1, SWORD, TRIDENT);
			this.rank = 2;
			this.recovery = 20;
		}

		@Override
		public double score(RemnantBrain b) {
			double d = b.dist();
			return d > 4 && d < 11 && b.canSee() ? (b.style() == TRIDENT ? 1.6 : 1.4) + b.kite * 2 : 0;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 22;
		}

		@Override
		public void start(RemnantBrain b) {
			this.hit.clear();
			b.boss.setAnim(RemnantEntity.ANIM_CHARGE);
			b.boss.getNavigation().stop();
			b.boss.sound(b.style() == TRIDENT ? SoundEvents.TRIDENT_RIPTIDE_1.value() : SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.6F);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			ServerLevel level = b.boss.serverLevel();
			if (t < 8) {
				Vec3 predicted = b.target.position().add(b.target.getDeltaMovement().scale(4));
				this.dir = flatDir(b.boss.position(), predicted);
				b.boss.getLookControl().setLookAt(predicted.x, predicted.y + 1, predicted.z);
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, b.boss.getX(), b.boss.getY() + 0.1, b.boss.getZ(), 3, 0.3, 0, 0.3, 0.01);
			} else if (t < 16) {
				b.boss.setDeltaMovement(this.dir.x * 1.15, b.boss.getDeltaMovement().y, this.dir.z * 1.15);
				b.boss.hasImpulse = true;
				level.sendParticles(b.style() == TRIDENT ? ParticleTypes.BUBBLE_POP : ParticleTypes.SWEEP_ATTACK,
					b.boss.getX(), b.boss.getY() + 1, b.boss.getZ(), 1, 0, 0, 0, 0);
				for (Player p : b.boss.playersNear(b.boss.position().add(0, 1, 0), 1.9)) {
					if (this.hit.add(p.getUUID())) {
						b.boss.swing(InteractionHand.MAIN_HAND);
						b.boss.abilityHit(p, b.boss.weaponDamage() * 1.1F + 1.0F);
						b.boss.push(p, this.dir.scale(0.9).add(0, 0.35, 0));
					}
				}
			} else if (t == 16) {
				b.boss.setDeltaMovement(Vec3.ZERO);
			}
		}
	}

	/** Axe (and sword in phase 3): raised overhead, then a wide arc that breaks shields. Counter: step back. */
	public static final class Cleave extends Ability {
		private float yaw;

		Cleave() {
			super("cleave", 140, 1, AXE, SWORD);
			this.rank = 2;
			this.recovery = 22;
		}

		@Override
		public boolean allows(RemnantBrain b) {
			return super.allows(b) && (b.style() == AXE || b.phase() >= 3);
		}

		@Override
		public double score(RemnantBrain b) {
			return b.dist() < 4.5 ? 1.3 + b.block * 4 + b.aggression : 0;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 26;
		}

		@Override
		public void start(RemnantBrain b) {
			b.boss.setAnim(RemnantEntity.ANIM_RAISE);
			b.boss.getNavigation().stop();
			b.boss.sound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 0.6F);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			if (t <= 12) {
				b.boss.getLookControl().setLookAt(b.target, 60, 60);
				this.yaw = b.boss.yBodyRot;
			}
			Vec3 facing = Vec3.directionFromRotation(0, this.yaw);
			if (t < 16 && t % 3 == 0) {
				for (int i = -4; i <= 4; i++) {
					Vec3 d = facing.yRot((float) Math.toRadians(i * 17));
					Vec3 p = b.boss.position().add(d.scale(3.6));
					b.boss.serverLevel().sendParticles(t > 10 ? RED : ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + 0.15, p.z, 1, 0, 0, 0, 0);
				}
			}
			if (t == 16) {
				b.boss.setAnim(RemnantEntity.ANIM_NONE);
				b.boss.swing(InteractionHand.MAIN_HAND);
				b.boss.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.6F);
				b.boss.sound(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 1.0F, 0.7F);
				for (int i = -3; i <= 3; i++) {
					Vec3 p = b.boss.position().add(facing.yRot((float) Math.toRadians(i * 22)).scale(2.2));
					b.boss.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y + 1, p.z, 1, 0, 0, 0, 0);
				}
				for (Player p : b.boss.playersNear(b.boss.position(), 4.3)) {
					Vec3 to = flatDir(b.boss.position(), p.position());
					if (to.dot(facing) > 0.34) {
						if (p.isBlocking()) {
							p.disableShield();
						}
						b.boss.abilityHit(p, b.boss.weaponDamage() * 1.3F + 1.0F);
						b.boss.push(p, to.scale(0.9).add(0, 0.3, 0));
					}
				}
			}
		}
	}

	/** Mace (axe from phase 2): leaps high, marks the landing zone, slams down. Then it's stunned: punish it. */
	public static final class Skyfall extends Ability {
		private Vec3 marker = Vec3.ZERO;
		private int slamTick = -1;

		Skyfall() {
			super("skyfall", 200, 1, MACE, AXE);
			this.rank = 2;
		}

		@Override
		public boolean allows(RemnantBrain b) {
			return super.allows(b) && (b.style() == MACE || b.phase() + (b.boss.isEmpowered() ? 1 : 0) >= 2);
		}

		@Override
		public double score(RemnantBrain b) {
			double d = b.dist();
			return d > 3 && d < 15 && b.canSee() ? (b.style() == MACE ? 1.6 : 1.1) + b.kite * 2.5 : 0;
		}

		@Override
		public int duration(RemnantBrain b) {
			return this.slamTick >= 0 ? this.slamTick + 3 : 70;
		}

		@Override
		public void start(RemnantBrain b) {
			this.slamTick = -1;
			this.marker = b.target.position().add(b.target.getDeltaMovement().multiply(8, 0, 8));
			Vec3 flat = this.marker.subtract(b.boss.position()).multiply(1, 0, 1);
			double d = flat.length();
			b.boss.getNavigation().stop();
			b.boss.setAnim(RemnantEntity.ANIM_RAISE);
			b.boss.push(b.boss, (d < 0.1 ? Vec3.ZERO : flat.normalize().scale(Math.min(d * 0.07, 0.9))).add(0, 1.3, 0));
			b.boss.sound(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.5F, 0.6F);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			if (this.slamTick >= 0) {
				return;
			}
			if (t % 2 == 0) {
				b.boss.ring(this.marker, 4.0, t > 8 ? RED : ParticleTypes.SOUL_FIRE_FLAME, 32);
				b.boss.ring(this.marker, 1.5, ParticleTypes.SOUL_FIRE_FLAME, 12);
			}
			if (t == 11) {
				Vec3 to = this.marker.subtract(b.boss.position());
				b.boss.push(b.boss, new Vec3(to.x * 0.25, -2.2, to.z * 0.25));
			}
			if (t > 12 && (b.boss.onGround() || t > 45)) {
				this.slamTick = t;
				ServerLevel level = b.boss.serverLevel();
				b.boss.sound(SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.0F, 0.8F);
				b.boss.sound(SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.4F);
				level.sendParticles(ParticleTypes.EXPLOSION, b.boss.getX(), b.boss.getY() + 0.3, b.boss.getZ(), 4, 1.2, 0.2, 1.2, 0);
				b.boss.ring(b.boss.position(), 2.5, ParticleTypes.SOUL_FIRE_FLAME, 24);
				b.boss.ring(b.boss.position(), 4.5, ParticleTypes.SOUL_FIRE_FLAME, 40);
				for (Player p : b.boss.playersNear(b.boss.position(), 4.8)) {
					double d = p.distanceTo(b.boss);
					b.boss.abilityHit(p, (4.0F + b.boss.weaponDamage() * 1.1F) * (float) Math.max(0.3, 1 - d / 7));
					b.boss.push(p, flatDir(b.boss.position(), p.position()).scale(0.6).add(0, 0.85, 0));
				}
				b.boss.stun(40);
			}
		}
	}

	/** Mace: fires wind charges that blast you away (phase 3: two). */
	public static final class GaleBurst extends Ability {
		GaleBurst() {
			super("gale_burst", 140, 1, MACE);
			this.rank = 3;
			this.recovery = 10;
		}

		@Override
		public double score(RemnantBrain b) {
			double d = b.dist();
			return d > 5 && d < 16 && b.canSee() ? 1.0 + b.kite * 2 : 0;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 14;
		}

		@Override
		public void start(RemnantBrain b) {
			b.boss.setAnim(RemnantEntity.ANIM_CAST);
			b.boss.getNavigation().stop();
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			b.boss.getLookControl().setLookAt(b.target, 60, 60);
			if (t == 8) {
				int n = b.phase() >= 3 ? 2 : 1;
				for (int i = 0; i < n; i++) {
					Vec3 dir = b.target.getEyePosition().subtract(b.boss.getEyePosition()).yRot((i - (n - 1) / 2.0F) * 0.25F);
					WindCharge charge = new WindCharge(b.boss.level(), b.boss.getX(), b.boss.getEyeY() - 0.2, b.boss.getZ(), Vec3.ZERO);
					charge.setOwner(b.boss);
					charge.shoot(dir.x, dir.y, dir.z, 1.6F, 1.0F);
					b.boss.level().addFreshEntity(charge);
				}
				b.boss.sound(SoundEvents.BREEZE_SHOOT, 1.2F, 0.8F);
			}
		}
	}

	/** Trident: throws ghostly tridents (one per phase). */
	public static final class SpearVolley extends Ability {
		private int count;

		SpearVolley() {
			super("spear_volley", 140, 1, TRIDENT);
			this.rank = 2;
			this.recovery = 12;
		}

		@Override
		public double score(RemnantBrain b) {
			return b.dist() > 5 && b.canSee() ? 1.5 + b.kite * 2 : 0;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 12 + this.count * 6;
		}

		@Override
		public void start(RemnantBrain b) {
			this.count = Math.min(3, b.phase() + (b.boss.isEmpowered() ? 1 : 0));
			b.boss.setAnim(RemnantEntity.ANIM_RAISE);
			b.boss.getNavigation().stop();
			for (int i = 0; i < this.count; i++) {
				Vec3 offset = new Vec3(0.6 * (i - (this.count - 1) / 2.0), 2.3, -0.2);
				b.boss.level().addFreshEntity(SpectralWeaponEntity.hovering(b.boss, weaponOrSword(b.boss), offset, 10 + i * 6, b.target,
					(2.5F + b.boss.weaponDamage() * 0.6F) * b.boss.abilityScale(), 2.1));
			}
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			b.boss.getLookControl().setLookAt(b.target, 60, 60);
		}
	}

	/** Trident: marks the ground under you, then calls lightning there (one strike per phase). Counter: keep moving. */
	public static final class StormCall extends Ability {
		private final Vec3[] marks = new Vec3[4];
		private int strikes;

		StormCall() {
			super("storm_call", 240, 1, TRIDENT);
			this.rank = 3;
			this.recovery = 18;
		}

		@Override
		public double score(RemnantBrain b) {
			return b.dist() > 4 ? 1.3 + b.kite * 2 + b.bowUse * 2 : 0;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 6 + (this.strikes - 1) * 8 + 20;
		}

		@Override
		public void start(RemnantBrain b) {
			this.strikes = Math.min(3, b.phase() + (b.boss.isEmpowered() ? 1 : 0));
			java.util.Arrays.fill(this.marks, null);
			b.boss.setAnim(RemnantEntity.ANIM_RAISE);
			b.boss.getNavigation().stop();
			b.boss.sound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.2F, 1.4F);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			ServerLevel level = b.boss.serverLevel();
			for (int k = 0; k < this.strikes; k++) {
				int place = k * 8;
				int strike = place + 18;
				if (t == place) {
					this.marks[k] = b.target.position();
				}
				if (this.marks[k] != null && t >= place && t < strike && t % 2 == 0) {
					b.boss.ring(this.marks[k], 2.2, ParticleTypes.ELECTRIC_SPARK, 20);
					b.boss.ring(this.marks[k], 0.8, ParticleTypes.ELECTRIC_SPARK, 8);
				}
				if (this.marks[k] != null && t == strike) {
					LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
					if (bolt != null) {
						bolt.moveTo(this.marks[k]);
						bolt.setVisualOnly(true);
						level.addFreshEntity(bolt);
					}
					b.boss.sound(SoundEvents.TRIDENT_THUNDER.value(), 2.0F, 1.0F);
					for (Player p : b.boss.playersNear(this.marks[k], 2.4)) {
						b.boss.abilityHit(p, 4.0F + b.boss.weaponDamage() * 0.4F);
						p.setRemainingFireTicks(40);
					}
				}
			}
		}
	}

	/** Fists / tools: a rising blow that launches you into the air. */
	public static final class Uppercut extends Ability {
		Uppercut() {
			super("uppercut", 110, 1, FISTS);
			this.rank = 2;
			this.recovery = 14;
		}

		@Override
		public double score(RemnantBrain b) {
			return b.dist() < 3.2 ? 1.5 + b.aggression : 0;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 14;
		}

		@Override
		public void start(RemnantBrain b) {
			b.boss.setAnim(RemnantEntity.ANIM_CHARGE);
			b.boss.getNavigation().stop();
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			b.boss.getLookControl().setLookAt(b.target, 60, 60);
			if (t == 6) {
				b.boss.setAnim(RemnantEntity.ANIM_RAISE);
				if (b.dist() < b.style().reach + 0.8) {
					b.boss.swing(InteractionHand.MAIN_HAND);
					b.boss.abilityHit(b.target, b.boss.weaponDamage() * 1.1F + 1.0F);
					b.boss.push(b.target, flatDir(b.boss.position(), b.target.position()).scale(0.3).add(0, 1.05, 0));
					b.boss.sound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2F, 0.6F);
				}
			}
		}
	}

	// =================================================================================================
	// Ranged
	// =================================================================================================

	/** Bow: a fan of arrows (5 / 7 / 9). Counter: close the gap or take cover. */
	public static final class Volley extends Ability {
		Volley() {
			super("volley", 130, 1, BOW);
			this.rank = 2;
			this.recovery = 15;
		}

		@Override
		public double score(RemnantBrain b) {
			double d = b.dist();
			return d > 6 && d < 20 && b.canSee() ? 1.2 + b.block * 1.5 : 0;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 18;
		}

		@Override
		public void start(RemnantBrain b) {
			b.boss.setAnim(RemnantEntity.ANIM_AIM);
			b.boss.getNavigation().stop();
			b.boss.startUsingItem(InteractionHand.MAIN_HAND);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			b.boss.getLookControl().setLookAt(b.target, 60, 60);
			if (t == 14) {
				b.boss.stopUsingItem();
				int n = b.phase() >= 2 ? 5 : 3;
				for (int i = 0; i < n; i++) {
					b.boss.shootArrow(b.target, 1.0F, -15 + 30.0F * i / (n - 1), 0.7F, false);
				}
			}
		}

		@Override
		public void stop(RemnantBrain b) {
			b.boss.stopUsingItem();
		}
	}

	/** Bow / crossbow: a tracking aim line, a red lock, then a very fast heavy shot. Counter: move after the lock. */
	public static final class PiercingShot extends Ability {
		private Vec3 lock = Vec3.ZERO;

		PiercingShot() {
			super("piercing_shot", 170, 1, BOW, CROSSBOW);
			this.rank = 3;
			this.recovery = 20;
		}

		@Override
		public double score(RemnantBrain b) {
			double d = b.dist();
			return d > 8 && d < 32 && b.canSee() ? 1.1 + b.bowUse * 1.5 + (b.target.getDeltaMovement().horizontalDistanceSqr() < 0.01 ? 1 : 0) : 0;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 34;
		}

		@Override
		public void start(RemnantBrain b) {
			b.boss.setAnim(RemnantEntity.ANIM_AIM);
			b.boss.getNavigation().stop();
			b.boss.startUsingItem(InteractionHand.MAIN_HAND);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			Vec3 eye = b.boss.getEyePosition();
			if (t < 24) {
				b.boss.getLookControl().setLookAt(b.target, 60, 60);
				if (t % 2 == 0) {
					b.boss.line(eye, b.target.getEyePosition(), ModParticles.REMNANT_SOUL, 1.0);
				}
			} else if (t == 24) {
				b.boss.faceTarget(b.target);
				this.lock = b.target.getEyePosition().subtract(0, 0.4, 0);
				b.boss.sound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.8F);
			} else if (t < 30) {
				b.boss.getLookControl().setLookAt(this.lock.x, this.lock.y, this.lock.z);
				b.boss.line(eye, eye.add(this.lock.subtract(eye).normalize().scale(24)), RED, 0.8);
			} else if (t == 30) {
				b.boss.stopUsingItem();
				PhantomArrowEntity arrow = new PhantomArrowEntity(b.boss.level(), b.boss, new ItemStack(Items.ARROW), b.boss.getMainHandItem().copyWithCount(1));
				Vec3 dir = this.lock.subtract(arrow.position());
				arrow.shoot(dir.x, dir.y, dir.z, 3.6F, 0.0F);
				arrow.setBaseDamage(2.3 * b.boss.abilityScale() * b.boss.damageScale());
				arrow.setCritArrow(true);
				b.boss.level().addFreshEntity(arrow);
				b.boss.sound(SoundEvents.CROSSBOW_SHOOT, 1.5F, 0.5F);
				b.boss.sound(SoundEvents.WITHER_SHOOT, 0.6F, 1.6F);
			}
		}

		@Override
		public void stop(RemnantBrain b) {
			b.boss.stopUsingItem();
		}
	}

	/** Bow / crossbow, phase 2+: marks a circle on you, then arrows rain from the sky into it. Counter: get out. */
	public static final class RainOfSouls extends Ability {
		private Vec3 center = Vec3.ZERO;

		RainOfSouls() {
			super("rain_of_souls", 260, 2, BOW, CROSSBOW);
			this.rank = 4;
			this.recovery = 15;
		}

		@Override
		public double score(RemnantBrain b) {
			double d = b.dist();
			return d > 5 && d < 26 ? 1.0 + b.block * 3 + (1 - b.kite) * 0.5 : 0;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 56;
		}

		@Override
		public void start(RemnantBrain b) {
			b.boss.setAnim(RemnantEntity.ANIM_RAISE);
			b.boss.getNavigation().stop();
			b.boss.sound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.2F, 0.9F);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			if (t < 20) {
				this.center = b.target.position();
			}
			if (t < 28 && t % 2 == 0) {
				b.boss.ring(this.center, 3.5, t >= 20 ? RED : ParticleTypes.SOUL_FIRE_FLAME, 28);
			}
			if (t == 20) {
				b.boss.sound(SoundEvents.ARROW_SHOOT, 1.5F, 0.5F);
			}
			if (t >= 28 && t < 52) {
				int n = b.phase() >= 3 ? 2 : 1;
				for (int i = 0; i < n; i++) {
					double a = b.boss.getRandom().nextDouble() * Math.PI * 2;
					double r = Math.sqrt(b.boss.getRandom().nextDouble()) * 3.5;
					PhantomArrowEntity arrow = new PhantomArrowEntity(b.boss.level(), b.boss, new ItemStack(Items.ARROW), null);
					arrow.setPos(this.center.x + Math.cos(a) * r, this.center.y + 16, this.center.z + Math.sin(a) * r);
					arrow.shoot(0, -1, 0, 2.2F, 0.0F);
					arrow.setBaseDamage(1.1 * b.boss.abilityScale() * b.boss.damageScale());
					b.boss.level().addFreshEntity(arrow);
				}
			}
		}
	}

	/** Crossbow: three quick bolts. */
	public static final class Burst extends Ability {
		Burst() {
			super("burst", 100, 1, CROSSBOW);
			this.rank = 2;
			this.recovery = 10;
		}

		@Override
		public double score(RemnantBrain b) {
			double d = b.dist();
			return d > 4 && d < 20 && b.canSee() ? 1.3 : 0;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 16;
		}

		@Override
		public void start(RemnantBrain b) {
			b.boss.setAnim(RemnantEntity.ANIM_AIM);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			b.boss.getLookControl().setLookAt(b.target, 60, 60);
			if (t == 2 || t == 7 || t == 12) {
				b.boss.shootArrow(b.target, 1.0F, (b.boss.getRandom().nextFloat() - 0.5F) * 6, 0.8F, false);
			}
		}

		@Override
		public boolean allowsMovement(int t) {
			return true;
		}
	}

	/** Crossbow: an explosive soul bolt (no block damage). Counter: don't stand next to walls you can't leave. */
	public static final class SoulBolt extends Ability {
		SoulBolt() {
			super("soul_bolt", 180, 1, CROSSBOW);
			this.rank = 3;
			this.recovery = 12;
		}

		@Override
		public double score(RemnantBrain b) {
			double d = b.dist();
			return d > 6 && d < 24 && b.canSee() ? 1.2 + b.block * 2 : 0;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 16;
		}

		@Override
		public void start(RemnantBrain b) {
			b.boss.setAnim(RemnantEntity.ANIM_AIM);
			b.boss.getNavigation().stop();
			b.boss.sound(SoundEvents.CROSSBOW_LOADING_END.value(), 1.0F, 0.6F);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			b.boss.getLookControl().setLookAt(b.target, 60, 60);
			if (t < 12 && t % 2 == 0) {
				Vec3 hand = b.boss.getEyePosition().add(b.boss.getLookAngle().scale(0.6));
				b.boss.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, hand.x, hand.y, hand.z, 2, 0.05, 0.05, 0.05, 0.01);
			}
			if (t == 12) {
				b.boss.shootArrow(b.target, 1.0F, 0, 0.6F, true);
			}
		}
	}

	// =================================================================================================
	// Biome Forgotten (1.2): one signature move each, known from the weakest echo up
	// =================================================================================================

	/** Drowned: a chain harpoon rattles out toward you, then drags you in. Counter: break line of sight before it bites. */
	public static final class Harpoon extends Ability {
		private static final ItemParticleOption CHAIN = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.CHAIN));

		Harpoon() {
			super("harpoon", 160, 1);
			this.rank = 1;
			this.recovery = 18;
		}

		@Override
		public boolean allows(RemnantBrain b) {
			return b.boss.getVariant() == ForgottenVariant.DROWNED;
		}

		@Override
		public double score(RemnantBrain b) {
			double d = b.dist();
			return d > 5 && d < 16 && b.canSee() ? 1.5 + b.kite * 3 + (b.target.isInWater() ? 1 : 0) : 0;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 24;
		}

		@Override
		public void start(RemnantBrain b) {
			b.boss.setAnim(RemnantEntity.ANIM_CAST);
			b.boss.getNavigation().stop();
			b.boss.sound(SoundEvents.CHAIN_PLACE, 1.4F, 0.6F);
			b.boss.sound(SoundEvents.DROWNED_SHOOT, 1.0F, 0.7F);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			LivingEntity target = b.target;
			b.boss.getLookControl().setLookAt(target, 60, 60);
			Vec3 hand = b.boss.getEyePosition().add(Vec3.directionFromRotation(0, b.boss.yBodyRot + 40).scale(0.4)).subtract(0, 0.4, 0);
			if (t < 14 && t % 2 == 0) {
				// the chain pays out a little further every other tick: you see it coming
				Vec3 to = target.getEyePosition().subtract(0, 0.4, 0);
				b.boss.line(hand, hand.add(to.subtract(hand).scale((t + 2) / 14.0)), CHAIN, 0.45);
				if (t % 4 == 0) {
					b.boss.sound(SoundEvents.CHAIN_STEP, 1.2F, 0.8F + t * 0.03F);
				}
			}
			if (t == 14) {
				if (b.canSee() && b.dist() < 18) {
					Vec3 v = b.boss.position().subtract(target.position());
					double d = v.length();
					b.boss.push(target, v.normalize().scale(Math.min(2.6, 0.6 + d * 0.13)).add(0, 0.45, 0));
					target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0), b.boss);
					b.boss.abilityHit(target, 2.0F);
					b.boss.sound(SoundEvents.CHAIN_BREAK, 1.5F, 0.7F);
					b.boss.serverLevel().sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + 1, target.getZ(), 20, 0.3, 0.5, 0.3, 0.1);
					b.attackCooldown = 4;
				} else {
					b.boss.sound(SoundEvents.CHAIN_FALL, 1.0F, 0.6F);
				}
			}
		}
	}

	/** Frozen: it draws a breath (frost gathering at its mouth), then breathes a freezing cone. Counter: step aside. */
	public static final class FrostBreath extends Ability {
		private Vec3 dir = Vec3.ZERO;
		private final Set<UUID> chilled = new HashSet<>();

		FrostBreath() {
			super("frost_breath", 140, 1);
			this.rank = 1;
			this.recovery = 20;
		}

		@Override
		public boolean allows(RemnantBrain b) {
			return b.boss.getVariant() == ForgottenVariant.FROZEN;
		}

		@Override
		public double score(RemnantBrain b) {
			double d = b.dist();
			return d < 6.5 && b.canSee() ? 1.4 + b.block * 2 + b.aggression : 0;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 26;
		}

		@Override
		public void start(RemnantBrain b) {
			this.chilled.clear();
			b.boss.setAnim(RemnantEntity.ANIM_ROAR);
			b.boss.getNavigation().stop();
			b.boss.sound(SoundEvents.BREEZE_INHALE, 1.3F, 0.6F);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			ServerLevel level = b.boss.serverLevel();
			Vec3 mouth = b.boss.getEyePosition().subtract(0, 0.15, 0);
			if (t < 12) {
				b.boss.getLookControl().setLookAt(b.target, 30, 30);
				this.dir = b.target.getEyePosition().subtract(0, 0.6, 0).subtract(mouth).normalize();
				for (int i = 0; i < 3; i++) {   // frost drawn in toward the mouth
					Vec3 from = mouth.add((b.boss.getRandom().nextDouble() - 0.5) * 1.6, (b.boss.getRandom().nextDouble() - 0.5) * 1.2,
						(b.boss.getRandom().nextDouble() - 0.5) * 1.6);
					Vec3 in = mouth.subtract(from);
					level.sendParticles(ParticleTypes.SNOWFLAKE, from.x, from.y, from.z, 0, in.x, in.y, in.z, 0.12);
				}
				return;
			}
			if (t == 12) {
				b.boss.setAnim(RemnantEntity.ANIM_CAST);
				b.boss.sound(SoundEvents.POWDER_SNOW_BREAK, 1.6F, 0.6F);
				b.boss.sound(SoundEvents.PLAYER_HURT_FREEZE, 1.2F, 0.6F);
			}
			if (t < 23) {
				for (int i = 0; i < 8; i++) {
					Vec3 jitter = new Vec3(b.boss.getRandom().nextGaussian(), b.boss.getRandom().nextGaussian() * 0.6, b.boss.getRandom().nextGaussian()).scale(0.22);
					Vec3 d = this.dir.add(jitter).normalize();
					level.sendParticles(i % 3 == 0 ? ParticleTypes.CLOUD : ParticleTypes.SNOWFLAKE, mouth.x, mouth.y, mouth.z, 0, d.x, d.y, d.z, 0.45);
				}
				if (t % 4 == 1) {
					for (Player p : b.boss.playersNear(mouth, 5.8)) {
						Vec3 to = p.getEyePosition().subtract(0, 0.5, 0).subtract(mouth);
						if (to.normalize().dot(this.dir) > 0.8 && b.boss.hasLineOfSight(p) && this.chilled.add(p.getUUID())) {
							b.boss.abilityHit(p, 2.0F);
							p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), b.boss);
							p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() + 20, p.getTicksFrozen() + 80));
						}
					}
				}
			}
		}
	}

	/**
	 * Sandy: it sinks into the ground, a trail of churning sand races under you, and it climbs out behind your back.
	 * Counter: watch the trail and turn around - it is helpless while climbing out. It only burrows toward a target it
	 * can see, and only to a spot it could walk to in a straight line (no digging into closed houses); with no such spot
	 * the move is off.
	 */
	public static final class Burrow extends Ability {
		private Vec3 from = Vec3.ZERO;
		private Vec3 dest = Vec3.ZERO;
		private boolean cancelled;

		Burrow() {
			super("burrow", 180, 1, SWORD, AXE, TRIDENT, MACE, FISTS);
			this.rank = 1;
		}

		@Override
		public boolean allows(RemnantBrain b) {
			return b.boss.getVariant() == ForgottenVariant.SANDY && super.allows(b);
		}

		@Override
		public double score(RemnantBrain b) {
			double d = b.dist();
			return d > 4 && d < 14 && b.canSee() && b.target.onGround() && b.boss.onGround() && b.boss.standsOnDiggable()
				&& behind(b, b.target, b.boss.position()).isPresent() ? 1.5 + b.kite * 3 + b.block * 1.5 : 0;
		}

		@Override
		public int duration(RemnantBrain b) {
			return 28;
		}

		@Override
		public void start(RemnantBrain b) {
			b.boss.getNavigation().stop();
			b.boss.setAnim(RemnantEntity.ANIM_NONE);
			b.boss.sound(SoundEvents.SAND_BREAK, 1.4F, 0.6F);
			b.boss.sound(SoundEvents.ROOTED_DIRT_BREAK, 1.0F, 0.6F);
			this.from = b.boss.position();
			java.util.Optional<Vec3> spot = b.target == null ? java.util.Optional.empty() : behind(b, b.target, this.from);
			this.cancelled = spot.isEmpty();
			this.dest = spot.orElse(this.from);
		}

		@Override
		public void tick(RemnantBrain b, int t) {
			ServerLevel level = b.boss.serverLevel();
			if (this.cancelled) {
				b.finishAbility(); // nowhere to come up: never mind
				return;
			}
			if (t < RemnantEntity.SINK_TICKS) {
				b.boss.getNavigation().stop();
				b.boss.setSinkTicks(t + 1);
				return;
			}
			if (t == RemnantEntity.SINK_TICKS) {
				// the target moved while it sank: follow if there is a new spot behind it, else keep the old one (if it
				// is still open), else come back up where it went down
				java.util.Optional<Vec3> now = b.target == null ? java.util.Optional.empty() : behind(b, b.target, this.from);
				this.dest = now.orElse(reachable(b, this.from, this.dest) ? this.dest : this.from);
			}
			if (t < 26) {
				// the churning trail under the ground: you can see where it is going
				Vec3 p = this.from.lerp(this.dest, (t - RemnantEntity.SINK_TICKS + 1) / (double) (26 - RemnantEntity.SINK_TICKS));
				BlockState ground = level.getBlockState(BlockPos.containing(p).below());
				if (!ground.isAir()) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), p.x, p.y + 0.1, p.z, 8, 0.3, 0.05, 0.3, 0.1);
				}
				if (t % 3 == 0) {
					level.playSound(null, p.x, p.y, p.z, SoundEvents.SAND_HIT, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.6F);
				}
				return;
			}
			if (t == 26) {
				this.surface(b);
			}
		}

		private void surface(RemnantBrain b) {
			b.boss.teleportTo(this.dest.x, this.dest.y, this.dest.z);
			if (b.target != null) {
				b.boss.faceTarget(b.target);
			}
			b.boss.setSinkTicks(0);
			b.boss.emerge(14);
			b.attackCooldown = 0;
		}

		@Override
		public void stop(RemnantBrain b) {
			if (b.boss.getSinkTicks() > 0) {   // interrupted under the ground: come up where it is
				this.dest = b.boss.position();
				this.surface(b);
			}
		}

		/**
		 * A standing spot about two blocks behind the target (ground within a block or two of its feet) that the burrower
		 * could reach in a straight line from {@code from}: nothing solid in the way at knee and chest height.
		 */
		private static java.util.Optional<Vec3> behind(RemnantBrain b, LivingEntity target, Vec3 from) {
			Vec3 back = target.getLookAngle().multiply(1, 0, 1);
			back = back.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : back.normalize();
			for (double dist : new double[]{2.0, 1.5, 2.6}) {
				Vec3 spot = target.position().subtract(back.scale(dist));
				for (int dy = 1; dy >= -2; dy--) {
					BlockPos feet = BlockPos.containing(spot.x, target.getY() + dy, spot.z);
					if (b.boss.level().getBlockState(feet.below()).isFaceSturdy(b.boss.level(), feet.below(), Direction.UP)
						&& b.boss.level().noCollision(b.boss, b.boss.getType().getSpawnAABB(spot.x, feet.getY(), spot.z))
						&& reachable(b, from, new Vec3(spot.x, feet.getY(), spot.z))) {
						return java.util.Optional.of(new Vec3(spot.x, feet.getY(), spot.z));
					}
				}
			}
			return java.util.Optional.empty();
		}
	}

	private Abilities() {
	}
}
