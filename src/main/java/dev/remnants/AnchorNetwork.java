package dev.remnants;

import dev.remnants.net.AnchorListPayload;
import dev.remnants.registry.ModBlocks;
import dev.remnants.registry.ModItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Soul Anchor network (1.5): every anchor a player attunes (right-click; placing a renamed one attunes it with its
 * name) joins their network, up to {@code anchorNetworkSize}. Standing at one of them (Shift + right-click it with an
 * empty hand) opens the Anchors screen; a jump to another anchor in the same dimension costs {@code anchorTeleportCost}
 * Soul Fragments (free for a Wanderer) and has a cooldown. The jump takes {@link #DISSOLVE} ticks: the player comes apart
 * into soul light (stepping away cancels it) and gathers again on top of the other anchor.
 */
public final class AnchorNetwork {
	public static final int DISSOLVE = 20;
	public static final double REACH = 3.0;
	private static final Map<UUID, Jump> PENDING = new HashMap<>();
	private static final Map<UUID, Long> LAST_JUMP = new HashMap<>();

	private record Jump(BlockPos from, BlockPos to, Vec3 start, long at, int paid) {
	}

	public static Component displayName(RemnantTracker.AnchorLink link) {
		return link.name().isEmpty() ? Component.translatable("message.remnants.anchor.unnamed", link.pos().getX(), link.pos().getZ())
			: Component.literal(link.name());
	}

	/** Right-click on an anchor: join the network (or rename it, with {@code name}). Returns false when the network is full. */
	public static boolean attune(ServerPlayer player, BlockPos pos, @Nullable String name) {
		RemnantTracker tracker = RemnantTracker.get(player.server);
		boolean had = tracker.isLinked(player.getUUID(), player.level().dimension(), pos);
		int max = Math.max(1, RemnantsConfig.get().anchorNetworkSize);
		if (!tracker.linkAnchor(player.getUUID(), player.level().dimension(), pos, name, max)) {
			player.displayClientMessage(Component.translatable("message.remnants.anchor.network_full", max).withStyle(ChatFormatting.GOLD), true);
			return false;
		}
		if (!had) {
			int n = tracker.anchors(player.getUUID()).size();
			player.displayClientMessage(Component.translatable("message.remnants.anchor.attuned", n, max).withStyle(ChatFormatting.AQUA), true);
			ServerLevel level = player.serverLevel();
			level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.02);
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.7F);
		} else if (name != null) {
			player.displayClientMessage(Component.translatable("message.remnants.anchor.renamed", name).withStyle(ChatFormatting.AQUA), true);
		}
		return true;
	}

	public static int cost(ServerPlayer player) {
		return PathBranch.is(player, PathBranch.Branch.WANDERER) ? 0 : Math.max(0, RemnantsConfig.get().anchorTeleportCost);
	}

	public static int cooldownLeft(ServerPlayer player) {
		Long at = LAST_JUMP.get(player.getUUID());
		if (at == null) {
			return 0;
		}
		return (int) Math.max(0, at + RemnantsConfig.get().anchorTeleportCooldown - player.server.overworld().getGameTime());
	}

	/** Opens the Anchors screen for a player standing at {@code from}. */
	public static void open(ServerPlayer player, BlockPos from) {
		RemnantTracker tracker = RemnantTracker.get(player.server);
		ServerLevel level = player.serverLevel();
		List<AnchorListPayload.Entry> list = new ArrayList<>();
		for (RemnantTracker.AnchorLink a : tracker.anchors(player.getUUID())) {
			if (a.dimension() != level.dimension()) {
				continue;
			}
			// a loaded chunk tells whether it still stands; an unloaded one is checked on the jump
			boolean present = !level.isLoaded(a.pos()) || level.getBlockState(a.pos()).is(ModBlocks.SOUL_ANCHOR);
			list.add(new AnchorListPayload.Entry(a.pos(), a.name(), present));
		}
		if (ServerPlayNetworking.canSend(player, AnchorListPayload.TYPE)) {
			ServerPlayNetworking.send(player, new AnchorListPayload(from, list, cost(player), cooldownLeft(player),
				Math.max(1, RemnantsConfig.get().anchorNetworkSize)));
		}
	}

	/** The jump was asked for (from the screen, or a test): checks everything, takes the cost, starts the dissolve. */
	public static boolean request(ServerPlayer player, BlockPos from, BlockPos to) {
		ServerLevel level = player.serverLevel();
		RemnantTracker tracker = RemnantTracker.get(player.server);
		if (from.equals(to) || PENDING.containsKey(player.getUUID())) {
			return false;
		}
		if (player.distanceToSqr(Vec3.atCenterOf(from)) > (REACH + 1) * (REACH + 1) || !level.getBlockState(from).is(ModBlocks.SOUL_ANCHOR)
			|| !tracker.isLinked(player.getUUID(), level.dimension(), from)) {
			player.displayClientMessage(Component.translatable("message.remnants.anchor.stand").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!tracker.isLinked(player.getUUID(), level.dimension(), to)) {
			return false;
		}
		if (!level.getBlockState(to).is(ModBlocks.SOUL_ANCHOR)) {   // loads the chunk if it has to
			tracker.unlinkAnchor(player.getUUID(), level.dimension(), to);
			player.displayClientMessage(Component.translatable("message.remnants.anchor.gone").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		int wait = cooldownLeft(player);
		if (wait > 0 && !player.getAbilities().instabuild) {
			player.displayClientMessage(Component.translatable("message.remnants.anchor.cooldown", wait / 20 + 1).withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		int cost = player.getAbilities().instabuild ? 0 : cost(player);
		if (cost > 0) {
			Inventory inv = player.getInventory();
			if (inv.countItem(ModItems.SOUL_FRAGMENT) < cost) {
				player.displayClientMessage(Component.translatable("message.remnants.anchor.cost", cost).withStyle(ChatFormatting.GOLD), true);
				return false;
			}
			int left = cost;
			for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
				if (inv.getItem(i).is(ModItems.SOUL_FRAGMENT)) {
					int take = Math.min(left, inv.getItem(i).getCount());
					inv.getItem(i).shrink(take);
					left -= take;
				}
			}
		}
		LAST_JUMP.put(player.getUUID(), player.server.overworld().getGameTime());
		PENDING.put(player.getUUID(), new Jump(from.immutable(), to.immutable(), player.position(), level.getGameTime(), cost));
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DISSOLVE + 5, 2, true, false, false));
		level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.4F, 1.6F);
		level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5F, 0.8F);
		return true;
	}

	public static boolean isJumping(ServerPlayer player) {
		return PENDING.containsKey(player.getUUID());
	}

	public static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Jump>> it = PENDING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Jump> en = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
			Jump j = en.getValue();
			if (p == null || !p.isAlive()) {
				it.remove();
				continue;
			}
			ServerLevel level = p.serverLevel();
			long t = level.getGameTime() - j.at();
			if (p.position().distanceToSqr(j.start()) > 4.0) {
				it.remove();
				LAST_JUMP.remove(p.getUUID());
				if (j.paid() > 0) {   // stepped off: the soul is given back
					p.getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(ModItems.SOUL_FRAGMENT, j.paid()));
				}
				p.displayClientMessage(Component.translatable("message.remnants.anchor.cancelled").withStyle(ChatFormatting.GRAY), true);
				continue;
			}
			// coming apart: soul light rises off the body from the feet up
			double h = p.getBbHeight() * Math.min(1.0, t / (double) DISSOLVE);
			level.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + h, p.getZ(), 4, 0.3, 0.1, 0.3, 0.03);
			if (t >= DISSOLVE) {
				it.remove();
				arrive(p, j.to());
			}
		}
	}

	/** Puts the player on top of the anchor (or the nearest free spot beside it) and gathers them again. */
	public static void arrive(ServerPlayer p, BlockPos anchor) {
		ServerLevel level = p.serverLevel();
		BlockPos spot = anchor.above();
		if (!free(level, spot)) {
			for (BlockPos q : BlockPos.betweenClosed(anchor.offset(-1, 0, -1), anchor.offset(1, 1, 1))) {
				if (free(level, q) && !level.getBlockState(q.below()).getCollisionShape(level, q.below()).isEmpty()) {
					spot = q.immutable();
					break;
				}
			}
		}
		level.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.3, 0.8, 0.3, 0.05);
		p.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, p.getYRot(), p.getXRot());
		p.resetFallDistance();
		level.sendParticles(ParticleTypes.SOUL, spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5, 30, 0.3, 0.8, 0.3, 0.05);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, spot.getX() + 0.5, spot.getY() + 0.1, spot.getZ() + 0.5, 16, 0.4, 0.1, 0.4, 0.02);
		level.playSound(null, spot, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 0.8F, 1.3F);
		level.playSound(null, spot, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5F, 1.2F);
	}

	private static boolean free(ServerLevel level, BlockPos p) {
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
	}

	public static void forget(UUID player) {
		PENDING.remove(player);
	}

	/** Server stopped: the cooldowns are game times of this world and mean nothing in the next one. */
	public static void reset() {
		PENDING.clear();
		LAST_JUMP.clear();
	}

	private AnchorNetwork() {
	}
}
