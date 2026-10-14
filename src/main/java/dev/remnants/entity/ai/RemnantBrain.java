package dev.remnants.entity.ai;

import dev.remnants.RemnantsConfig;
import dev.remnants.fx.CombatCues;
import dev.remnants.entity.RemnantEntity;
import dev.remnants.entity.SpectralWeaponEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Remnant's combat brain: one goal that owns movement, basic attacks and abilities.
 *
 * <ul>
 *   <li><b>Utility AI.</b> Every ability scores the current situation (distance, line of sight, phase, what the
 *   player is doing); the best score above a threshold wins, with a little randomness so it never feels scripted.</li>
 *   <li><b>Memory.</b> It learns how this player fights: kiting, shield blocking, bow use and melee aggression are
 *   tracked as decaying scores and feed the ability scores. Kite it and it starts pulling and blinking; turtle
 *   behind a shield and it flanks, cleaves and novas; spam hits and it starts parrying.</li>
 *   <li><b>Reactions.</b> Sometimes dodges incoming projectiles (sidestep, or a short blink in phase 3), retaliates
 *   when hit, and refuses to be cheesed: if it can't reach you it teleports to you.</li>
 *   <li><b>Fair rhythm.</b> Every basic strike is wound up (weapon drawn back) before it lands, so stepping back or
 *   raising a shield in time works; a shield raised just before the strike is a perfect parry that cracks its
 *   poise. Combos end in a pause, big moves in a recovery window, and abilities are spaced by a global cooldown.</li>
 * </ul>
 */
public class RemnantBrain extends Goal {
	public final RemnantEntity boss;
	@Nullable
	public LivingEntity target;

	private final List<Ability> abilities = Abilities.create();
	private final Map<Ability, Integer> cooldowns = new HashMap<>();
	@Nullable
	private Ability active;
	private int activeTick;
	private int globalCooldown = 40;
	public int attackCooldown;
	/** Ticks until the current wound-up strike lands (0 = not winding up). */
	private int windup;

	/** What the brain is doing right now (for tests and /remnants debugging). */
	public String debugState() {
		return this.active != null ? this.active.name : this.windup > 0 ? "windup" : this.target == null ? "idle" : "move";
	}

	/** Winding up a strike or in the middle of a move: the Remnant holds its ground against knockback. */
	public boolean isCommitted() {
		return this.windup > 0 || this.active != null;
	}
	private int comboStep;
	private int strafeTimer;
	private boolean strafeLeft;
	private int dodgeCooldown;
	private int unreachableTicks;
	private int flankTicks;
	private double flankSide = 1;

	/** Decaying 0..1 scores describing how the player fights. */
	public double kite, block, aggression, bowUse;

	public RemnantBrain(RemnantEntity boss) {
		this.boss = boss;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	public CombatStyle style() {
		return this.boss.getStyle();
	}

	public int phase() {
		return this.boss.getPhase();
	}

	public double dist() {
		return this.target == null ? 99 : this.boss.distanceTo(this.target);
	}

	public boolean canSee() {
		return this.target != null && this.boss.getSensing().hasLineOfSight(this.target);
	}

	public boolean targetUsingBow() {
		return this.target != null && this.target.isUsingItem() && this.target.getUseItem().getItem() instanceof ProjectileWeaponItem;
	}

	@Override
	public boolean canUse() {
		LivingEntity t = this.boss.getTarget();
		return t != null && t.isAlive();
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse() || this.active != null;
	}

	@Override
	public void start() {
		this.boss.setAggressive(true);
	}

	@Override
	public void stop() {
		this.interrupt();
		this.boss.setAggressive(false);
		this.boss.stopUsingItem();
		this.target = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		this.target = this.boss.getTarget();
		if (this.target == null || !this.target.isAlive()) {
			this.interrupt();
			return;
		}
		this.cooldowns.replaceAll((a, cd) -> Math.max(0, cd - 1));
		this.globalCooldown--;
		this.attackCooldown--;
		this.dodgeCooldown--;
		this.updateMemory();

		if (this.boss.isTransitioning()) {
			return;
		}
		if (this.boss.isStunned()) {
			this.windup = 0;
			this.boss.setAnim(RemnantEntity.ANIM_STUNNED);
			this.boss.stopUsingItem();
			return;
		} else if (this.boss.getAnim() == RemnantEntity.ANIM_STUNNED) {
			this.boss.setAnim(RemnantEntity.ANIM_NONE);
		}
		if (this.boss.isRecovering()) {
			this.windup = 0;
			this.boss.stopUsingItem();
			this.boss.getLookControl().setLookAt(this.target, 10, 10);
			return;
		}

		this.tryDodge();

		if (this.active != null) {
			Ability a = this.active;
			a.tick(this, this.activeTick++);
			if (this.active == a && this.activeTick >= a.duration(this)) {
				this.finishAbility();
			} else if (this.active == a && a.allowsMovement(this.activeTick)) {
				this.move(false);
			}
			return;
		}

		if (this.globalCooldown <= 0 && this.windup <= 0 && !this.boss.isIllusion()) {
			Ability pick = this.choose();
			if (pick != null) {
				this.startAbility(pick);
				return;
			}
		}
		if (this.antiCheese()) {
			return;
		}
		this.move(true);
	}

	// ---- abilities ---------------------------------------------------------------------------------

	@Nullable
	private Ability choose() {
		Ability best = null;
		double bestScore = 0.9;
		for (Ability a : this.abilities) {
			if (!a.allows(this) || this.cooldowns.getOrDefault(a, 0) > 0) {
				continue;
			}
			double s = a.score(this);
			if (s <= 0) {
				continue;
			}
			s *= 0.75 + this.boss.getRandom().nextDouble() * 0.5;
			if (s > bestScore) {
				bestScore = s;
				best = a;
			}
		}
		return best;
	}

	private void startAbility(Ability a) {
		this.active = a;
		this.activeTick = 0;
		this.boss.stopUsingItem();
		a.start(this);
	}

	/** Starts an ability right now, ignoring its cooldown (used by anti-cheese). */
	public void force(Class<? extends Ability> type) {
		for (Ability a : this.abilities) {
			if (type.isInstance(a)) {
				this.interrupt();
				this.startAbility(a);
				return;
			}
		}
	}

	public void finishAbility() {
		if (this.active == null) {
			return;
		}
		Ability done = this.active;
		done.stop(this);
		this.cooldowns.put(done, this.scaledCooldown(done.cooldown));
		this.active = null;
		this.boss.setAnim(RemnantEntity.ANIM_NONE);
		this.globalCooldown = this.phase() >= 3 ? 30 : this.phase() == 2 ? 40 : 50;
		int rest = done.recovery(this);
		if (rest > 0 && !this.boss.isStunned()) {
			// the deeper the phase, the shorter the breather (but never gone)
			this.boss.recover(this.phase() >= 3 ? rest * 3 / 4 : rest);
		}
	}

	public void interrupt() {
		this.windup = 0;
		if (this.active != null) {
			this.active.stop(this);
			this.cooldowns.put(this.active, this.scaledCooldown(this.active.cooldown) / 2);
			this.active = null;
		}
		if (!this.boss.isTransitioning()) {
			this.boss.setAnim(RemnantEntity.ANIM_NONE);
		}
		this.boss.setStance(0);
		this.boss.setShieldUp(false);
	}

	private int scaledCooldown(int cd) {
		double m = RemnantsConfig.get().abilityCooldownMultiplier;
		m *= this.phase() >= 3 ? 0.8 : this.phase() == 2 ? 0.9 : 1.0;
		m *= this.boss.isEmpowered() ? 0.9 : 1.0;
		return (int) Math.max(20, cd * m);
	}

	// ---- reactions ---------------------------------------------------------------------------------

	public void onHurtBy(LivingEntity attacker, DamageSource source, boolean melee) {
		if (melee) {
			this.aggression = Math.min(1.0, this.aggression + 0.15);
			// retaliate soon instead of waiting out the attack cooldown (the strike is still wound up first)
			this.attackCooldown = Math.min(this.attackCooldown, 8 + this.boss.getRandom().nextInt(8));
		} else {
			this.bowUse = Math.min(1.0, this.bowUse + 0.08);
		}
	}

	/** Riposte: the player struck the Remnant during its stance. */
	public void counterAttack(LivingEntity attacker) {
		this.boss.setStance(0);
		Vec3 back = attacker.getLookAngle().multiply(1, 0, 1).normalize();
		Vec3 behind = attacker.position().subtract(back.scale(1.6));
		Vec3 from = this.boss.position();
		if (this.boss.randomTeleport(behind.x, behind.y + 0.5, behind.z, false)) {
			this.boss.serverLevel().sendParticles(ParticleTypes.SOUL, from.x, from.y + 1, from.z, 15, 0.3, 0.6, 0.3, 0.04);
		}
		this.boss.getLookControl().setLookAt(attacker, 180, 180);
		this.boss.swing(InteractionHand.MAIN_HAND);
		this.boss.abilityHit(attacker, this.boss.weaponDamage() * 1.3F + 1.0F);
		this.boss.push(attacker, back.scale(0.9).add(0, 0.4, 0));
		this.boss.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, attacker.getX(), attacker.getY() + 1, attacker.getZ(), 3, 0.4, 0.3, 0.4, 0);
		this.boss.sound(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 1.2F, 0.6F);
		this.boss.sound(SoundEvents.PLAYER_ATTACK_CRIT, 1.2F, 0.7F);
		if (attacker instanceof ServerPlayer sp) {
			sp.displayClientMessage(Component.translatable("message.remnants.parried").withStyle(ChatFormatting.RED), true);
		}
		if (this.active instanceof Abilities.Riposte riposte) {
			riposte.landed = true;
			this.finishAbility();
		}
		this.aggression *= 0.5;
	}

	private void updateMemory() {
		double d = this.dist();
		this.kite = this.kite * 0.995 + (d > 9 ? 0.005 : 0);
		this.block = this.block * 0.99 + (this.target.isBlocking() ? 0.01 : 0);
		this.bowUse = this.bowUse * 0.992 + (this.targetUsingBow() ? 0.008 : 0);
		this.aggression *= 0.985;
	}

	private void tryDodge() {
		if (this.dodgeCooldown > 0 || this.boss.inStance() || this.windup > 0) {
			return;
		}
		List<Projectile> incoming = this.boss.level().getEntitiesOfClass(Projectile.class, this.boss.getBoundingBox().inflate(8),
			p -> !(p.getOwner() instanceof RemnantEntity) && !(p instanceof SpectralWeaponEntity));
		for (Projectile p : incoming) {
			Vec3 v = p.getDeltaMovement();
			if (v.lengthSqr() < 0.25) {
				continue;
			}
			Vec3 to = this.boss.position().add(0, 1, 0).subtract(p.position());
			if (to.length() > 8 || to.normalize().dot(v.normalize()) < 0.93) {
				continue;
			}
			int echo = this.boss.getEchoRank();
			double chance = this.boss.isIllusion() || echo <= 1 ? 0.0 : (this.phase() >= 3 ? 0.45 : this.phase() == 2 ? 0.35 : 0.25) * (echo >= 4 ? 1.0 : echo == 3 ? 0.7 : 0.45);
			this.dodgeCooldown = 60;
			if (this.boss.getRandom().nextDouble() > chance) {
				return;
			}
			Vec3 side = new Vec3(-v.z, 0, v.x).normalize().scale(this.boss.getRandom().nextBoolean() ? 1 : -1);
			if (this.phase() >= 3 && echo >= 4 && this.boss.getRandom().nextBoolean()) {
				Vec3 from = this.boss.position();
				Vec3 dest = from.add(side.scale(3.0));
				if (this.boss.randomTeleport(dest.x, dest.y + 1, dest.z, false)) {
					this.boss.serverLevel().sendParticles(ParticleTypes.SOUL, from.x, from.y + 1, from.z, 10, 0.2, 0.5, 0.2, 0.03);
					this.boss.sound(SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.6F);
					return;
				}
			}
			this.boss.push(this.boss, side.scale(0.85).add(0, 0.3, 0));
			return;
		}
	}

	/** If the target is somewhere the Remnant can't path to (pillar, hole, across water), go and get them. */
	private boolean antiCheese() {
		if (this.style().ranged || this.boss.isForgotten()) {
			return false; // the Forgotten are natural monsters: like a zombie, they don't come through your walls
		}
		double dy = this.target.getY() - this.boss.getY();
		double flat = this.boss.position().multiply(1, 0, 1).distanceTo(this.target.position().multiply(1, 0, 1));
		boolean stuck = (dy > 2.5 && flat < 6) || (this.boss.getNavigation().isDone() && this.dist() > this.style().reach + 2);
		this.unreachableTicks = stuck ? this.unreachableTicks + 1 : Math.max(0, this.unreachableTicks - 2);
		if (this.unreachableTicks > 50) {
			this.unreachableTicks = 0;
			this.force(Abilities.PhantomStep.class);
			return true;
		}
		return false;
	}

	// ---- movement & basic attacks --------------------------------------------------------------------

	private void move(boolean attack) {
		if (this.style().ranged) {
			this.rangedTick(attack);
		} else {
			this.meleeTick(attack);
		}
	}

	private void meleeTick(boolean attack) {
		LivingEntity t = this.target;
		double dist = this.dist();
		double reach = this.style().reach + t.getBbWidth() * 0.5;
		boolean see = this.canSee();
		this.boss.getLookControl().setLookAt(t, 30, 30);

		if (this.windup > 0) {
			// feet planted, weapon drawn back: step out of reach or raise a shield now (it tracks you, slowly)
			this.boss.getNavigation().stop();
			this.boss.turnToward(t, 8.0F);
			if (--this.windup == 0) {
				this.landStrike(t, reach);
			}
			return;
		}

		// Shield turtles get flanked instead of hit in the face.
		if (t.isBlocking() && this.style() != CombatStyle.AXE && dist < 6 && this.flankTicks <= 0 && this.boss.getRandom().nextFloat() < 0.08F) {
			this.flankTicks = 25;
			this.flankSide = this.boss.getRandom().nextBoolean() ? 1 : -1;
		}
		if (this.flankTicks > 0) {
			this.flankTicks--;
			Vec3 toBoss = this.boss.position().subtract(t.position()).multiply(1, 0, 1).normalize();
			Vec3 side = new Vec3(-toBoss.z, 0, toBoss.x).scale(2.5 * this.flankSide);
			Vec3 goal = t.position().add(side).subtract(t.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
			this.boss.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.4);
		} else if (dist > reach) {
			if (dist < reach + 2.0) {
				// the last steps: pathing stops a block short (and fails onto ledges), so walk straight in
				this.boss.getNavigation().stop();
				this.boss.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.15);
			} else if (!this.boss.getNavigation().moveTo(t, dist > 8 ? 1.35 : 1.15) && dist < 8) {
				this.boss.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.15);
			}
		} else {
			this.boss.getNavigation().stop();
			if (this.attackCooldown > 5) {
				if (--this.strafeTimer <= 0) {
					this.strafeTimer = 25 + this.boss.getRandom().nextInt(30);
					this.strafeLeft = !this.strafeLeft;
				}
				this.boss.getMoveControl().strafe(-0.2F, this.strafeLeft ? 0.5F : -0.5F);
			}
			this.boss.turnToward(t, 25.0F);
		}

		if (attack && dist <= reach && see && this.attackCooldown <= 0) {
			this.windup = this.boss.isIllusion() ? 9 : this.phase() >= 3 ? 5 : this.phase() == 2 ? 6 : 7;
			this.boss.setAnim(RemnantEntity.ANIM_WINDUP);
			this.boss.sound(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 0.8F, 0.55F);
			// art 2.0 hit feel: a glint on the weapon and the reach of the swing on the ground until it lands
			Vec3 f = Vec3.directionFromRotation(0, this.boss.getYRot());
			Vec3 r = new Vec3(-f.z, 0, f.x);
			CombatCues.glint(this.boss, this.boss.position().add(0, 2.1, 0).add(r.scale(-0.45)).add(f.scale(0.2)), 1.0F);
			CombatCues.arcAttached(this.boss, (float) (this.style().reach + 0.6), 110.0F, this.windup);
		}
	}

	/** The wound-up strike: lands if you are still in reach, bounces off a perfectly timed shield, whiffs otherwise. */
	private void landStrike(LivingEntity t, double reach) {
		this.boss.setAnim(RemnantEntity.ANIM_NONE);
		this.boss.swing(InteractionHand.MAIN_HAND);
		int[] combo = this.style().combo;
		int cd = combo[this.comboStep % combo.length];
		this.comboStep++;
		this.attackCooldown = (int) (cd * (this.phase() >= 3 ? 0.85 : 1.0));
		double dist = this.dist();
		if (dist > reach && dist < reach + 1.6 && this.canSee()) {
			// the strike steps in: backing off half a step is not enough, you have to really get out of reach
			Vec3 dir = t.position().subtract(this.boss.position()).multiply(1, 0, 1).normalize();
			this.boss.setDeltaMovement(this.boss.getDeltaMovement().add(dir.scale(0.45)));
			dist -= 0.9;
		}
		if (dist > reach + 0.5 || !this.canSee()) {
			this.boss.sound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0F, 0.7F);
			return;
		}
		if (t instanceof Player p && p.isBlocking() && p.getTicksUsingItem() <= RemnantEntity.PARRY_WINDOW
			&& p.isDamageSourceBlocked(this.boss.damageSources().mobAttack(this.boss)) && !this.boss.isIllusion()) {
			this.perfectParry(p);
			return;
		}
		if (this.boss.doHurtTarget(t)) {
			boolean finisher = combo.length <= 1 || this.comboStep % combo.length == 0;
			CombatCues.impact(this.boss, t, t.position().add(0, t.getBbHeight() * 0.6, 0), finisher ? 0.45F : 0.25F, 0.0F, finisher ? 3 : 2);
		}
		if (combo.length > 1 && this.comboStep % combo.length != 0) {
			Vec3 dir = t.position().subtract(this.boss.position()).multiply(1, 0, 1).normalize();
			this.boss.setDeltaMovement(this.boss.getDeltaMovement().add(dir.scale(0.2)));
		}
	}

	/** Shield raised just before the strike: the blow glances off, the Remnant reels and its poise cracks. */
	private void perfectParry(Player p) {
		Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(0.6)).add(0, 1.2, 0);
		this.boss.serverLevel().sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 16, 0.2, 0.3, 0.2, 0.4);
		this.boss.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 8, 0.2, 0.3, 0.2, 0.05);
		this.boss.sound(SoundEvents.SHIELD_BLOCK, 1.4F, 1.5F);
		this.boss.sound(SoundEvents.ANVIL_LAND, 0.5F, 1.9F);
		this.comboStep = 0;
		this.attackCooldown = Math.max(this.attackCooldown, 20);
		if (!this.boss.damagePoise(45.0F, p)) {
			this.boss.recover(20);
			Vec3 back = this.boss.position().subtract(p.position()).multiply(1, 0, 1).normalize();
			this.boss.push(this.boss, back.scale(0.5).add(0, 0.15, 0));
		}
		if (p instanceof ServerPlayer sp) {
			sp.displayClientMessage(Component.translatable("message.remnants.perfect_parry").withStyle(ChatFormatting.AQUA), true);
		}
	}

	private void rangedTick(boolean attack) {
		LivingEntity t = this.target;
		double dist = this.dist();
		boolean see = this.canSee();
		this.boss.getLookControl().setLookAt(t, 30, 30);

		if (dist > 16 || !see) {
			this.boss.getNavigation().moveTo(t, 1.1);
		} else {
			// Never turn its back: keep facing the target and back-pedal / circle with strafing.
			this.boss.getNavigation().stop();
			if (--this.strafeTimer <= 0) {
				this.strafeTimer = 20 + this.boss.getRandom().nextInt(30);
				this.strafeLeft = !this.strafeLeft;
			}
			float back = dist < 5 ? -0.9F : dist < 9 ? -0.45F : 0.0F;
			this.boss.getMoveControl().strafe(back, this.strafeLeft ? 0.55F : -0.55F);
			this.boss.faceTarget(t);
		}

		if (!attack) {
			return;
		}
		if (this.boss.isUsingItem()) {
			int used = this.boss.getTicksUsingItem();
			int draw = Math.max(14, 22 - this.phase() * 2 - Math.min(2, this.boss.getTier()));
			if (used >= draw && see) {
				this.boss.stopUsingItem();
				this.boss.performRangedAttack(t, BowItem.getPowerForTime(Math.max(used, 20)));
				this.attackCooldown = (int) (this.style().combo[0] * (this.phase() >= 3 ? 0.8 : this.phase() == 2 ? 0.9 : 1.0));
			} else if (!see && used > 60) {
				this.boss.stopUsingItem();
			}
		} else if (this.attackCooldown <= 0 && see && dist < 28) {
			this.boss.startUsingItem(InteractionHand.MAIN_HAND);
		}
	}
}
