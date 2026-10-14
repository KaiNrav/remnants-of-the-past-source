package dev.remnants.item;

import dev.remnants.registry.ModComponents;
import dev.remnants.registry.ModWorldgen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The Soul Dowser: held in either hand it listens for Forgotten Graves within {@link #RANGE} blocks. It clicks once
 * a second at the edge of its range, faster as you close in, in triple clicks within five blocks, and chimes with
 * its rod pointing straight down when you stand right over one. The needle trembles to match (client: the
 * {@code remnants_of_the_past:signal} item property reads {@link ModComponents#DOWSE_SIGNAL}).
 */
public class SoulDowserItem extends LoreItem {
	public static final int RANGE = 16;
	/** How close a sleeping Grave Mimic makes the rod twitch (1.4). */
	public static final int MIMIC_RANGE = 6;
	private static final Map<UUID, State> STATES = new HashMap<>();

	/** Per player: the grave last heard, how far, and when the next click is due. */
	private static final class State {
		double dist = -1;
		int signal;
		long nextClick;
		int burst;
		boolean announced;
	}

	public SoulDowserItem(Properties properties) {
		super(properties, 2);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer player)) {
			return;
		}
		boolean held = player.getMainHandItem() == stack || player.getOffhandItem() == stack;
		if (!held) {
			setSignal(stack, 0);
			return;
		}
		State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		long now = server.getGameTime();
		if (now % 4 == 0) {
			listen(server, player, state);
			setSignal(stack, state.signal);
		}
		if (state.signal > 0 && now >= state.nextClick) {
			this.click(server, player, state, now);
		}
	}

	/** Finds the nearest grave (a point of interest, so this is cheap) and turns its distance into a signal. */
	private static void listen(ServerLevel server, ServerPlayer player, State state) {
		Optional<BlockPos> grave = server.getPoiManager().findClosest(h -> h.is(ModWorldgen.GRAVE_POI), player.blockPosition(), RANGE,
			PoiManager.Occupancy.ANY);
		int before = state.signal;
		if (grave.isEmpty()) {
			// 1.4: a sleeping Grave Mimic is full of something that used to be alive - the rod twitches near it
			boolean mimic = !server.getEntitiesOfClass(dev.remnants.entity.GraveMimicEntity.class, player.getBoundingBox().inflate(MIMIC_RANGE),
				dev.remnants.entity.GraveMimicEntity::isDormant).isEmpty();
			state.signal = mimic ? 1 : 0;
			state.dist = mimic ? RANGE * 0.9 : -1;
			state.announced = false;
			if (before == 0 && mimic) {
				state.nextClick = 0;
			}
			return;
		}
		Vec3 c = Vec3.atCenterOf(grave.get());
		double dx = c.x - player.getX();
		double dz = c.z - player.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		state.dist = c.distanceTo(player.position());
		state.signal = flat <= 0.9 && player.getY() - c.y < 6 && player.getY() > c.y - 1 ? 3 : state.dist <= 5 ? 2 : state.dist <= RANGE ? 1 : 0;
		if (state.signal == 3 && !state.announced) {
			state.announced = true;
			player.displayClientMessage(Component.translatable("message.remnants.dowser.here").withStyle(ChatFormatting.AQUA), true);
			server.sendParticles(player, ParticleTypes.SOUL, true, c.x, player.getY() + 0.1, c.z, 6, 0.2, 0.05, 0.2, 0.01);
		} else if (state.signal < 3) {
			state.announced = false;
		}
		if (before == 0 && state.signal > 0) {
			state.nextClick = 0;
		}
	}

	private void click(ServerLevel server, ServerPlayer player, State state, long now) {
		float closeness = (float) Math.max(0.0, 1.0 - state.dist / RANGE);
		switch (state.signal) {
			case 3 -> {
				player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
				player.playNotifySound(SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 0.5F, 1.8F);
				state.nextClick = now + 10;
			}
			case 2 -> {
				// triple clicks: three quick ones, then a pause
				player.playNotifySound(SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 0.7F, 1.4F + 0.2F * closeness);
				state.burst = (state.burst + 1) % 3;
				state.nextClick = now + (state.burst == 0 ? 12 : 3);
			}
			default -> {
				player.playNotifySound(SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 0.45F, 0.9F + 0.5F * closeness);
				// once a second at the edge of its range, about four times a second five blocks out
				state.nextClick = now + Math.round(5 + 15 * (float) ((state.dist - 5) / (RANGE - 5)));
			}
		}
	}

	private static void setSignal(ItemStack stack, int signal) {
		Integer wanted = signal == 0 ? null : signal;
		if (!Objects.equals(stack.get(ModComponents.DOWSE_SIGNAL), wanted)) {
			if (wanted == null) {
				stack.remove(ModComponents.DOWSE_SIGNAL);
			} else {
				stack.set(ModComponents.DOWSE_SIGNAL, wanted);
			}
		}
	}

	/** The needle frame lives in a component; a new frame must not replay the held item's equip bob. */
	@Override
	public boolean allowComponentsUpdateAnimation(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
		ItemStack oldStack, ItemStack newStack) {
		return !oldStack.is(newStack.getItem());
	}

	/** ...nor stop the digging you are doing with the other hand. */
	@Override
	public boolean allowContinuingBlockBreaking(net.minecraft.world.entity.player.Player player, ItemStack oldStack, ItemStack newStack) {
		return true;
	}

	public static void reset() {
		STATES.clear();
	}

	/** Players who leave stop being tracked. */
	public static void forget(UUID player) {
		STATES.remove(player);
	}
}
