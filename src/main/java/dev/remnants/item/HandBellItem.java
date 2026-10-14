package dev.remnants.item;

import dev.remnants.RemnantsConfig;
import dev.remnants.entity.GraveMimicEntity;
import dev.remnants.entity.KeenerEntity;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.registry.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Hand Bell: one clear ring that shows what hides. Everything within {@code handBellRadius} (12) for 5 s: hostile and
 * invisible creatures glow, a Keener is revealed and its wail breaks off, sleeping Grave Mimics flinch and glow (they do
 * not wake), and hidden Forgotten Graves send up soul light. Cooldown {@code handBellCooldown} (20 s).
 */
public class HandBellItem extends LoreItem {
	public static final int SHOW_TICKS = 100;
	private static final List<Pulse> PULSES = new ArrayList<>();

	private record Pulse(ServerLevel level, List<BlockPos> at, long until) {
	}

	public HandBellItem(Properties properties) {
		super(properties, 2);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel server) {
			ring(server, player.position(), RemnantsConfig.get().handBellRadius, player);
			player.getCooldowns().addCooldown(this, RemnantsConfig.get().handBellCooldown);
		}
		player.swing(hand);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	/** One ring at {@code center} (also the test hook). Returns how many things it showed. */
	public static int ring(ServerLevel level, Vec3 center, double radius, Player by) {
		level.playSound(null, center.x, center.y, center.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2F, 1.9F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 1.5F);
		for (int i = 0; i < 32; i++) {
			double a = i / 32.0 * Math.PI * 2;
			level.sendParticles(ParticleTypes.SOUL, center.x + Math.cos(a) * 2.0, center.y + 1.2, center.z + Math.sin(a) * 2.0, 1,
				Math.cos(a) * 0.4, 0, Math.sin(a) * 0.4, 0.3);
		}
		int shown = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(radius),
			e -> e != by && e.isAlive() && e.position().distanceToSqr(center) <= radius * radius)) {
			if (e instanceof KeenerEntity keener) {
				if (keener.ringed() && by instanceof ServerPlayer sp) {
					HeartBlockEntity.award(sp, "ring_out");
				}
				shown++;
			}
			if (e instanceof GraveMimicEntity mimic && mimic.isDormant()) {
				mimic.flinch();
				shown++;
				continue;
			}
			if (e instanceof Enemy || e.isInvisible()) {
				e.addEffect(new MobEffectInstance(MobEffects.GLOWING, SHOW_TICKS, 0, false, false));
				shown++;
			}
		}
		List<BlockPos> graves = level.getPoiManager().getInRange(h -> h.is(ModWorldgen.GRAVE_POI), BlockPos.containing(center), (int) radius,
			PoiManager.Occupancy.ANY).map(r -> r.getPos().immutable()).toList();
		if (!graves.isEmpty()) {
			List<BlockPos> tops = new ArrayList<>();
			for (BlockPos g : graves) {
				tops.add(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, g));
			}
			PULSES.add(new Pulse(level, tops, level.getGameTime() + SHOW_TICKS));
			shown += graves.size();
		}
		return shown;
	}

	/** Server stopped: the pulses hold their ServerLevel, which must not stay alive into the next world. */
	public static void reset() {
		PULSES.clear();
	}

	/** End of every server tick: graves a bell found keep breathing soul light for a while. */
	public static void tickPulses(MinecraftServer server) {
		for (Iterator<Pulse> it = PULSES.iterator(); it.hasNext(); ) {
			Pulse p = it.next();
			long now = p.level().getGameTime();
			if (now > p.until()) {
				it.remove();
				continue;
			}
			if (now % 10 == 0) {
				for (BlockPos at : p.at()) {
					p.level().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, 3, 0.2, 0.3, 0.2, 0.01);
					p.level().sendParticles(ParticleTypes.SOUL, at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 1, 0.1, 0.4, 0.1, 0.02);
				}
			}
		}
	}
}
