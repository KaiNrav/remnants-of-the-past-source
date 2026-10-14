package dev.remnants;

import dev.remnants.entity.CarrionCrowEntity;
import dev.remnants.entity.RemnantEntity;
import dev.remnants.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Carrion Crows: pressure to go back for your things, but fair. A Remnant whose owner has not come
 * within {@link #OWNER_NEAR} blocks for {@code crowDelayTicks} (10 min of game time - the clock runs on world time, so
 * an unloaded chunk counts too) draws a flock of {@code crowFlockMin..crowFlockMax} crows that circle over it in plain
 * sight. Every {@code crowIntervalTicks} one dives and carries off a stack of the Remnant's supplies (never its gear,
 * see {@link RemnantEntity#takeForCrow}) to a tree, at most one stack per crow. Snatches that fell due while nobody was
 * there happen at once when the Remnant loads again: those crows are already sitting on the trees around with the loot.
 * When the owner comes back the empty-handed crows fly off and the clock starts over. Only under an open sky (or
 * through leaves); {@code carrionCrows} turns it all off.
 */
public final class CarrionCrows {
	public static final double OWNER_NEAR = 32.0, FLOCK_RANGE = 48.0;
	/** Test hook: the empty test box has a barrier roof. */
	public static boolean ignoreSky;

	/** Every 40 ticks of a loaded Remnant (server). */
	public static void tick(RemnantEntity r) {
		RemnantsConfig cfg = RemnantsConfig.get();
		if (!cfg.carrionCrows || r.isForgotten() || r.isIllusion() || r.getOwnerUuid().isEmpty() || !(r.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		if (r.abandonedSince() == 0) {
			r.setAbandonedSince(now);
		}
		UUID owner = r.getOwnerUuid().get();
		ServerPlayer o = level.getServer().getPlayerList().getPlayer(owner);
		boolean ownerNear = o != null && o.level() == level && o.distanceToSqr(r) < OWNER_NEAR * OWNER_NEAR;
		long elapsed = now - r.abandonedSince();
		if (!ownerNear && elapsed < cfg.crowDelayTicks) {
			return;   // nothing for crows to do yet: skip the 97-block flock search (every 40 ticks per Remnant)
		}
		List<CarrionCrowEntity> flock = flock(level, r);
		int maxSteals = Math.max(0, cfg.crowFlockMax);
		if (elapsed >= cfg.crowDelayTicks && r.crowSteals() < maxSteals && r.hasCrowLoot() && (ignoreSky || openSky(level, r.blockPosition()))) {
			int due = (int) Math.min(maxSteals, 1 + (elapsed - cfg.crowDelayTicks) / Math.max(1, cfg.crowIntervalTicks)) - r.crowSteals();
			if (flock.isEmpty() && !ownerNear) {
				flock = spawn(level, r);
			}
			// a backlog from while nobody was here (or the owner walking up just now): the crows already sit on the trees
			int backlog = ownerNear ? due : due - 1;
			for (int i = 0; i < backlog && r.hasCrowLoot(); i++) {
				CarrionCrowEntity crow = ModEntities.CARRION_CROW.create(level);
				if (crow == null) {
					break;
				}
				crow.moveTo(r.getX(), r.getY() + CarrionCrowEntity.CIRCLE_HEIGHT, r.getZ(), level.random.nextFloat() * 360.0F, 0.0F);
				crow.serve(r);
				level.addFreshEntity(crow);
				ItemStack took = r.takeForCrow(level.random);
				r.addCrowSteal();
				crow.carry(took);
				crow.settle();
			}
			if (!ownerNear && due - Math.max(0, backlog) > 0) {
				for (CarrionCrowEntity crow : flock) {
					if (crow.state() == CarrionCrowEntity.CIRCLE && !crow.isCarrying() && crow.tickCount > 200) {   // circle a while first
						crow.beginSnatch();
						r.addCrowSteal();
						break;
					}
				}
			}
		}
		if (ownerNear) {
			r.setAbandonedSince(now);
			for (CarrionCrowEntity crow : flock) {
				crow.leave();
			}
		}
	}

	public static List<CarrionCrowEntity> flock(ServerLevel level, RemnantEntity r) {
		return level.getEntitiesOfClass(CarrionCrowEntity.class, r.getBoundingBox().inflate(FLOCK_RANGE),
			c -> c.isAlive() && r.getUUID().equals(c.remnant()));
	}

	private static List<CarrionCrowEntity> spawn(ServerLevel level, RemnantEntity r) {
		RemnantsConfig cfg = RemnantsConfig.get();
		int lo = Math.max(1, cfg.crowFlockMin), hi = Math.max(lo, cfg.crowFlockMax);
		int n = lo + level.random.nextInt(hi - lo + 1);
		for (int i = 0; i < n; i++) {
			CarrionCrowEntity crow = ModEntities.CARRION_CROW.create(level);
			if (crow == null) {
				continue;
			}
			double a = i / (double) n * Math.PI * 2;
			crow.moveTo(r.getX() + Math.cos(a) * 12, r.getY() + CarrionCrowEntity.CIRCLE_HEIGHT + 6, r.getZ() + Math.sin(a) * 12,
				level.random.nextFloat() * 360.0F, 0.0F);
			crow.serve(r);
			level.addFreshEntity(crow);
		}
		level.playSound(null, r.blockPosition().above(8), SoundEvents.PARROT_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.5F);
		level.sendParticles(ParticleTypes.SOUL, r.getX(), r.getY() + 2, r.getZ(), 6, 0.4, 0.4, 0.4, 0.01);
		return flock(level, r);
	}

	/** Open sky above (leaves let crows through), up to 32 blocks. */
	public static boolean openSky(ServerLevel level, BlockPos pos) {
		BlockPos.MutableBlockPos p = pos.above(2).mutable();
		for (int i = 0; i < 32; i++) {
			var s = level.getBlockState(p);
			if (!s.isAir() && !s.is(net.minecraft.tags.BlockTags.LEAVES) && !s.getCollisionShape(level, p).isEmpty()) {
				return false;
			}
			p.move(0, 1, 0);
		}
		return true;
	}

	private CarrionCrows() {
	}
}
