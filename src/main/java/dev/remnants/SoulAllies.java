package dev.remnants;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Soul allies (1.5): creatures released from a Soul Lantern fight for whoever freed them until their time runs out,
 * then dissolve without a drop. They never hurt a player (ALLOW_DAMAGE), go after whatever hurts their keeper or what
 * the keeper strikes, else the nearest monster bothering the keeper. They carry the scoreboard tag {@link #TAG}: an ally
 * that comes back from an unloaded chunk after the server forgot it is a spent ghost and is removed.
 */
public final class SoulAllies {
	public static final String TAG = "remnants.soul_ally";
	private static final Map<UUID, Ally> ALLIES = new HashMap<>();

	private record Ally(UUID owner, long until) {
	}

	public static void bind(Mob mob, ServerPlayer owner, int ticks) {
		ALLIES.put(mob.getUUID(), new Ally(owner.getUUID(), mob.level().getGameTime() + ticks));
		mob.addTag(TAG);
		mob.setPersistenceRequired();
		mob.setTarget(null);
		mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, true, false, false));
	}

	public static boolean isAlly(@Nullable Entity e) {
		return e != null && ALLIES.containsKey(e.getUUID());
	}

	@Nullable
	public static UUID ownerOf(Entity e) {
		Ally a = ALLIES.get(e.getUUID());
		return a == null ? null : a.owner();
	}

	/** ALLOW_DAMAGE: an ally (or its arrow, its blast) never hurts a player. */
	public static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (ALLIES.isEmpty()) {
			return true;
		}
		Entity by = source.getEntity();
		if (by == null && source.getDirectEntity() instanceof Projectile p) {
			by = p.getOwner();
		}
		if (isAlly(by) && (victim instanceof Player || isAlly(victim))) {
			return false;
		}
		return true;
	}

	/** ENTITY_LOAD: a tagged ally the server no longer knows is a spent ghost. */
	public static void onLoad(Entity e, ServerLevel level) {
		if (e.getTags().contains(TAG) && !ALLIES.containsKey(e.getUUID())) {
			e.discard();
		}
	}

	public static void tick(MinecraftServer server) {
		if (ALLIES.isEmpty() || server.getTickCount() % 4 != 0) {
			return;
		}
		Iterator<Map.Entry<UUID, Ally>> it = ALLIES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Ally> en = it.next();
			Mob mob = find(server, en.getKey());
			if (mob == null) {
				continue;   // unloaded for now: it is discarded if it comes back after the server restarts
			}
			if (!mob.isAlive()) {
				it.remove();
				continue;
			}
			ServerLevel level = (ServerLevel) mob.level();
			if (level.getGameTime() >= en.getValue().until()) {
				it.remove();
				dissolve(mob);
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(en.getValue().owner());
			steer(mob, owner);
			if (level.getGameTime() % 8 == 0) {
				level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + mob.getBbHeight() * 0.7, mob.getZ(), 1, 0.25, 0.3, 0.25, 0.01);
			}
		}
	}

	@Nullable
	private static Mob find(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof Mob m) {
				return m;
			}
		}
		return null;
	}

	/** Picks the ally's foe: who hurts its keeper, what the keeper hit, or the nearest monster after the keeper. */
	private static void steer(Mob mob, @Nullable ServerPlayer owner) {
		LivingEntity current = mob.getTarget();
		if (current instanceof Player || isAlly(current) || (current != null && !current.isAlive())) {
			mob.setTarget(null);
			current = null;
		}
		if (owner == null || owner.level() != mob.level()) {
			return;
		}
		LivingEntity want = null;
		LivingEntity hurtBy = owner.getLastHurtByMob();
		LivingEntity hit = owner.getLastHurtMob();
		if (valid(mob, hurtBy) && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200) {
			want = hurtBy;
		} else if (valid(mob, hit) && owner.tickCount - owner.getLastHurtMobTimestamp() < 200) {
			want = hit;
		} else if (current == null) {
			double best = Double.MAX_VALUE;
			for (Mob m : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(12.0),
				m -> m != mob && m.isAlive() && m instanceof Enemy && !isAlly(m) && m.getTarget() == owner)) {
				double d = m.distanceToSqr(mob);
				if (d < best) {
					best = d;
					want = m;
				}
			}
		}
		if (want != null && want != current) {
			mob.setTarget(want);
		}
		if (mob.getTarget() == null && mob.distanceToSqr(owner) > 64.0 && mob.tickCount % 20 == 0) {
			mob.getNavigation().moveTo(owner, 1.1);   // keep up with the keeper
		}
	}

	private static boolean valid(Mob mob, @Nullable LivingEntity e) {
		return e != null && e.isAlive() && e != mob && !(e instanceof Player) && !isAlly(e) && e.distanceToSqr(mob) < 24 * 24;
	}

	public static void dissolve(Mob mob) {
		if (mob.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 24, 0.3, 0.5, 0.3, 0.04);
			level.playSound(null, mob.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.NEUTRAL, 1.5F, 0.8F);
		}
		ALLIES.remove(mob.getUUID());
		mob.discard();
	}

	/** Server stopped: allies never outlive their world (a reloaded one is a spent ghost, see {@link #onLoad}). */
	public static void reset() {
		ALLIES.clear();
	}

	private SoulAllies() {
	}
}
