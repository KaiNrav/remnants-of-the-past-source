package dev.remnants.client.archive;

import dev.remnants.LastRecord;
import dev.remnants.entity.DecoyEchoEntity;
import dev.remnants.net.ReplayPayload;
import dev.remnants.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Plays a Memorial projector's last record on this client: the dead player as a ghost (a client-only
 * {@link DecoyEchoEntity}) walking their last 10 s again on top of the projector - the path squeezed into
 * {@link #STAGE} blocks if it was longer - and whatever killed them alongside, glowing. The ghost gathers out of soul
 * light from the feet up for {@link #GATHER} ticks, and comes apart at the end.
 */
public final class MemorialReplays {
	public static final int GATHER = 20, SCATTER = 20;
	public static final double STAGE = 3.0;
	private static final List<Replay> ACTIVE = new ArrayList<>();
	private static int nextId = -2_000_000;

	private static final class Replay {
		final ReplayPayload data;
		final Vec3 center;
		final double scale;
		final DecoyEchoEntity ghost;
		@Nullable
		final LivingEntity killer;
		int t;

		Replay(ReplayPayload data, Vec3 center, double scale, DecoyEchoEntity ghost, @Nullable LivingEntity killer) {
			this.data = data;
			this.center = center;
			this.scale = scale;
			this.ghost = ghost;
			this.killer = killer;
		}

		int frames() {
			return this.data.frames();
		}

		int length() {
			return GATHER + this.frames() * LastRecord.STEP + SCATTER;
		}
	}

	public static void start(ReplayPayload p) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || p.frames() == 0) {
			return;
		}
		ACTIVE.removeIf(r -> {
			if (r.data.at().equals(p.at())) {
				discard(level, r);
				return true;
			}
			return false;
		});
		double reach = 0.01;
		for (float[] arr : new float[][] {p.pos(), p.killerPos()}) {
			for (int i = 0; i + 2 < arr.length; i += 3) {
				if (!Float.isNaN(arr[i])) {
					reach = Math.max(reach, Math.sqrt(arr[i] * arr[i] + arr[i + 2] * arr[i + 2]));
				}
			}
		}
		double scale = Math.min(1.0, STAGE / reach);
		Vec3 center = new Vec3(p.at().getX() + 0.5, p.at().getY() + 0.8, p.at().getZ() + 0.5);
		DecoyEchoEntity ghost = ModEntities.DECOY_ECHO.create(level);
		if (ghost == null) {
			return;
		}
		ghost.setId(nextId--);
		ghost.setupReplay(p.owner(), p.name(), item(p, 0));
		ghost.replayAlpha = 0.0F;
		ghost.setNoGravity(true);
		place(ghost, frame(p.pos(), 0), center, scale, p.rot()[0], p.rot()[1]);
		level.addEntity(ghost);
		LivingEntity killer = null;
		ResourceLocation kid = p.killer().isEmpty() ? null : ResourceLocation.tryParse(p.killer());
		if (kid != null) {
			Entity made = EntityType.byString(kid.toString()).map(type -> type.create(level)).orElse(null);
			if (made instanceof LivingEntity living) {
				living.setId(nextId--);
				living.setNoGravity(true);
				if (living instanceof Mob mob) {
					mob.setNoAi(true);
				}
				living.setGlowingTag(true);
				living.setInvisible(true);
				level.addEntity(living);
				killer = living;
			}
		}
		ACTIVE.add(new Replay(p, center, scale, ghost, killer));
		mc.gui.setOverlayMessage(p.cause().copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
	}

	private static Vec3 frame(float[] pos, int i) {
		return new Vec3(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]);
	}

	private static ItemStack item(ReplayPayload p, int i) {
		if (p.items().isEmpty() || i >= p.itemIdx().length) {
			return ItemStack.EMPTY;
		}
		int idx = p.itemIdx()[i];
		return idx >= 0 && idx < p.items().size() ? p.items().get(idx).copy() : ItemStack.EMPTY;
	}

	private static void place(Entity e, Vec3 rel, Vec3 center, double scale, float yaw, float pitch) {
		Vec3 at = center.add(rel.x * scale, rel.y * scale, rel.z * scale);
		e.setPos(at.x, at.y, at.z);
		e.setYRot(yaw);
		e.setXRot(pitch);
		if (e instanceof LivingEntity l) {
			l.yBodyRot = yaw;
			l.yHeadRot = yaw;
		}
	}

	/** End of every client tick. */
	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (ACTIVE.isEmpty()) {
			return;
		}
		if (level == null) {
			ACTIVE.clear();
			return;
		}
		Iterator<Replay> it = ACTIVE.iterator();
		while (it.hasNext()) {
			Replay r = it.next();
			r.t++;
			if (r.t >= r.length() || r.ghost.level() != level) {
				discard(level, r);
				it.remove();
				continue;
			}
			step(level, r);
		}
	}

	private static void step(ClientLevel level, Replay r) {
		ReplayPayload p = r.data;
		DecoyEchoEntity g = r.ghost;
		int n = r.frames();
		if (r.t <= GATHER) {
			float k = r.t / (float) GATHER;
			g.replayAlpha = k;
			double h = g.getBbHeight() * k;
			for (int i = 0; i < 3; i++) {
				level.addParticle(ParticleTypes.SOUL, g.getX() + (level.random.nextDouble() - 0.5) * 0.6, g.getY() + h,
					g.getZ() + (level.random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
			}
			return;
		}
		int play = r.t - GATHER;
		if (play >= n * LastRecord.STEP) {
			float k = 1.0F - (play - n * LastRecord.STEP) / (float) SCATTER;
			g.replayAlpha = Math.max(0, k);
			if (play == n * LastRecord.STEP) {
				for (int i = 0; i < 40; i++) {
					level.addParticle(ParticleTypes.SOUL, g.getX(), g.getY() + level.random.nextDouble() * 1.8, g.getZ(),
						(level.random.nextDouble() - 0.5) * 0.2, 0.05, (level.random.nextDouble() - 0.5) * 0.2);
				}
				Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("message.remnants.memorial.end", p.name())
					.withStyle(ChatFormatting.DARK_AQUA), false);
			}
			if (r.killer != null) {
				r.killer.setInvisible(true);
			}
			return;
		}
		g.replayAlpha = 1.0F;
		float f = play / (float) LastRecord.STEP;
		int i0 = Math.min(n - 1, (int) f), i1 = Math.min(n - 1, i0 + 1);
		double u = f - (int) f;
		Vec3 a = frame(p.pos(), i0), b = frame(p.pos(), i1);
		Vec3 rel = a.add(b.subtract(a).scale(u));
		float yaw = p.rot()[i0 * 2], pitch = p.rot()[i0 * 2 + 1];
		double before = g.position().distanceTo(r.center.add(rel.scale(r.scale)));
		place(g, rel, r.center, r.scale, yaw, pitch);
		g.walkAnimation.update((float) Math.min(1.0, before * 4.0), 0.4F);
		int flags = p.flags()[i0];
		g.setShiftKeyDown((flags & LastRecord.SNEAK) != 0);
		g.setSprinting((flags & LastRecord.SPRINT) != 0);
		if ((flags & LastRecord.SWING) != 0 && !g.swinging) {
			g.swing(InteractionHand.MAIN_HAND);
		}
		if ((flags & LastRecord.HURT) != 0 && g.hurtTime <= 0) {
			g.hurtTime = 10;
			g.hurtDuration = 10;
		}
		g.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, item(p, i0));
		if (r.killer != null && i0 * 3 + 2 < p.killerPos().length) {
			Vec3 k = frame(p.killerPos(), i0);
			if (Double.isNaN(k.x)) {
				r.killer.setInvisible(true);
			} else {
				r.killer.setInvisible(false);
				Vec3 at = r.center.add(k.scale(r.scale));
				Vec3 to = g.position().subtract(at);
				float kyaw = (float) (Math.atan2(to.z, to.x) * 180.0 / Math.PI) - 90.0F;
				r.killer.lerpTo(at.x, at.y, at.z, kyaw, 0.0F, 2);
				r.killer.yHeadRot = kyaw;
			}
		}
	}

	private static void discard(ClientLevel level, Replay r) {
		level.removeEntity(r.ghost.getId(), Entity.RemovalReason.DISCARDED);
		if (r.killer != null) {
			level.removeEntity(r.killer.getId(), Entity.RemovalReason.DISCARDED);
		}
	}

	/** Whether the projector at this position is playing right now (for its renderer's glow). */
	public static boolean playing(net.minecraft.core.BlockPos at) {
		for (Replay r : ACTIVE) {
			if (r.data.at().equals(at)) {
				return true;
			}
		}
		return false;
	}

	private MemorialReplays() {
	}
}
