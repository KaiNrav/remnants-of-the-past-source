package dev.remnants;

import dev.remnants.net.ReplayPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The last record (1.5): the server remembers the last {@link #FRAMES} x 2 ticks (10 s) of every player - where they
 * stood, where they looked, how they moved, what they held, and up to {@link #THREATS} monsters (or other players)
 * within {@link #SEEN} blocks. At death the buffer is kept as the player's last record (one per player, in the tracker),
 * the killer's track picked out of it, and a Memorial projector can play it back (ReplayPayload).
 *
 * <p>Flags per frame: 1 sneaking, 2 sprinting, 4 swinging, 8 hurt, 16 gliding (elytra), 32 swimming, 64 on the ground.
 */
public final class LastRecord {
	public static final int FRAMES = 100, STEP = 2, THREATS = 3;
	public static final double SEEN = 16.0;
	public static final int SNEAK = 1, SPRINT = 2, SWING = 4, HURT = 8, GLIDE = 16, SWIM = 32, GROUND = 64;
	private static final Map<UUID, Recorder> RECORDERS = new HashMap<>();

	/** One player's ring buffer. */
	static final class Recorder {
		final double[] pos = new double[FRAMES * 3];
		final float[] rot = new float[FRAMES * 2];
		final byte[] flags = new byte[FRAMES];
		final ItemStack[] held = new ItemStack[FRAMES];
		final int[] threatId = new int[FRAMES * THREATS];
		final EntityType<?>[] threatType = new EntityType<?>[FRAMES * THREATS];
		final double[] threatPos = new double[FRAMES * THREATS * 3];
		int head;
		int count;

		void add(ServerPlayer p) {
			int i = this.head;
			this.pos[i * 3] = p.getX();
			this.pos[i * 3 + 1] = p.getY();
			this.pos[i * 3 + 2] = p.getZ();
			this.rot[i * 2] = p.getYRot();
			this.rot[i * 2 + 1] = p.getXRot();
			int f = 0;
			f |= p.isShiftKeyDown() ? SNEAK : 0;
			f |= p.isSprinting() ? SPRINT : 0;
			f |= p.swinging ? SWING : 0;
			f |= p.hurtTime > 0 ? HURT : 0;
			f |= p.isFallFlying() ? GLIDE : 0;
			f |= p.isSwimming() ? SWIM : 0;
			f |= p.onGround() ? GROUND : 0;
			this.flags[i] = (byte) f;
			ItemStack hand = p.getMainHandItem();
			ItemStack prev = this.held[(i + FRAMES - 1) % FRAMES];
			// ten frames a second per player: reuse the last copy while the same item is held (no fresh copy of its components)
			this.held[i] = hand.isEmpty() ? ItemStack.EMPTY
				: prev != null && ItemStack.isSameItemSameComponents(prev, hand) ? prev : hand.copyWithCount(1);
			List<LivingEntity> near = p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(SEEN),
				e -> e != p && e.isAlive() && (e instanceof Enemy || e instanceof Player) && !e.isSpectator());
			near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
			for (int k = 0; k < THREATS; k++) {
				int t = i * THREATS + k;
				if (k < near.size()) {
					LivingEntity e = near.get(k);
					this.threatId[t] = e.getId();
					this.threatType[t] = e.getType();
					this.threatPos[t * 3] = e.getX();
					this.threatPos[t * 3 + 1] = e.getY();
					this.threatPos[t * 3 + 2] = e.getZ();
				} else {
					this.threatId[t] = -1;
					this.threatType[t] = null;
				}
			}
			this.head = (this.head + 1) % FRAMES;
			this.count = Math.min(FRAMES, this.count + 1);
		}

		/** Frame index of the k-th oldest kept frame. */
		int at(int k) {
			return (this.head - this.count + k + FRAMES * 2) % FRAMES;
		}
	}

	public static void tick(MinecraftServer server) {
		if (!RemnantsConfig.get().lastRecord || server.getTickCount() % STEP != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isAlive() && !p.isSpectator()) {
				RECORDERS.computeIfAbsent(p.getUUID(), id -> new Recorder()).add(p);
			}
		}
	}

	public static void forget(UUID player) {
		RECORDERS.remove(player);
	}

	/** The player is dying: keep the buffer (with a last frame of the very moment) as their last record. */
	public static void capture(ServerPlayer player, DamageSource source) {
		if (!RemnantsConfig.get().lastRecord) {
			return;
		}
		Recorder r = RECORDERS.computeIfAbsent(player.getUUID(), id -> new Recorder());
		r.add(player);
		int n = r.count;
		double ox = player.getX(), oy = player.getY(), oz = player.getZ();
		int[] pos = new int[n * 3];
		int[] rot = new int[n * 2];
		byte[] flags = new byte[n];
		byte[] itemIdx = new byte[n];
		List<ItemStack> palette = new ArrayList<>();
		for (int k = 0; k < n; k++) {
			int i = r.at(k);
			pos[k * 3] = Float.floatToIntBits((float) (r.pos[i * 3] - ox));
			pos[k * 3 + 1] = Float.floatToIntBits((float) (r.pos[i * 3 + 1] - oy));
			pos[k * 3 + 2] = Float.floatToIntBits((float) (r.pos[i * 3 + 2] - oz));
			rot[k * 2] = Float.floatToIntBits(r.rot[i * 2]);
			rot[k * 2 + 1] = Float.floatToIntBits(r.rot[i * 2 + 1]);
			flags[k] = r.flags[i];
			ItemStack held = r.held[i] == null ? ItemStack.EMPTY : r.held[i];
			int idx = -1;
			for (int j = 0; j < palette.size(); j++) {
				if (ItemStack.isSameItemSameComponents(palette.get(j), held)) {
					idx = j;
					break;
				}
			}
			if (idx < 0 && palette.size() < 8) {
				palette.add(held);
				idx = palette.size() - 1;
			}
			itemIdx[k] = (byte) Math.max(0, idx);
		}
		// the killer: its own track if it was among those seen, else the nearest one seen at the end
		Entity killer = source.getEntity();
		int killerId = -1;
		EntityType<?> killerType = null;
		if (killer instanceof LivingEntity && killer != player) {
			killerId = killer.getId();
			killerType = killer.getType();
		}
		boolean seen = false;
		for (int k = 0; k < n && killerId >= 0 && !seen; k++) {
			for (int t = 0; t < THREATS; t++) {
				seen |= r.threatId[r.at(k) * THREATS + t] == killerId;
			}
		}
		if (!seen && killerId < 0) {
			int last = r.at(n - 1);
			if (r.threatId[last * THREATS] >= 0) {
				killerId = r.threatId[last * THREATS];
				killerType = r.threatType[last * THREATS];
			}
		}
		int[] killerPos = new int[n * 3];
		for (int k = 0; k < n; k++) {
			int i = r.at(k);
			float x = Float.NaN, y = Float.NaN, z = Float.NaN;
			for (int t = 0; t < THREATS && killerId >= 0; t++) {
				int ti = i * THREATS + t;
				if (r.threatId[ti] == killerId) {
					x = (float) (r.threatPos[ti * 3] - ox);
					y = (float) (r.threatPos[ti * 3 + 1] - oy);
					z = (float) (r.threatPos[ti * 3 + 2] - oz);
				}
			}
			if (k == n - 1 && Float.isNaN(x) && killer != null && killer.getId() == killerId) {
				x = (float) (killer.getX() - ox);   // shot from afar: at least where it stood
				y = (float) (killer.getY() - oy);
				z = (float) (killer.getZ() - oz);
			}
			killerPos[k * 3] = Float.floatToIntBits(x);
			killerPos[k * 3 + 1] = Float.floatToIntBits(y);
			killerPos[k * 3 + 2] = Float.floatToIntBits(z);
		}
		CompoundTag tag = new CompoundTag();
		tag.putUUID("Owner", player.getUUID());
		tag.putString("Name", player.getGameProfile().getName());
		tag.putString("Cause", Component.Serializer.toJson(source.getLocalizedDeathMessage(player), player.registryAccess()));
		tag.putString("Dim", player.level().dimension().location().toString());
		tag.putIntArray("Origin", new int[]{player.getBlockX(), player.getBlockY(), player.getBlockZ()});
		tag.putLong("At", player.server.overworld().getGameTime());
		tag.putIntArray("Pos", pos);
		tag.putIntArray("Rot", rot);
		tag.putByteArray("Flags", flags);
		tag.putByteArray("ItemIdx", itemIdx);
		ListTag items = new ListTag();
		for (ItemStack s : palette) {
			items.add(s.isEmpty() ? new CompoundTag() : s.save(player.registryAccess()));
		}
		tag.put("Items", items);
		tag.putString("Killer", killerType == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(killerType).toString());
		tag.putIntArray("KillerPos", killerPos);
		RemnantTracker.get(player.server).setLastRecord(player.getUUID(), tag);
	}

	public static boolean has(ServerPlayer player) {
		return RemnantTracker.get(player.server).lastRecord(player.getUUID()) != null;
	}

	/** Builds the replay of {@code owner}'s last record for the projector at {@code at}, or null when there is none. */
	@Nullable
	public static ReplayPayload replay(MinecraftServer server, UUID owner, BlockPos at) {
		CompoundTag tag = RemnantTracker.get(server).lastRecord(owner);
		if (tag == null) {
			return null;
		}
		var registries = server.registryAccess();
		Component cause = Component.Serializer.fromJson(tag.getString("Cause"), registries);
		List<ItemStack> items = new ArrayList<>();
		ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			items.add(list.getCompound(i).isEmpty() ? ItemStack.EMPTY : ItemStack.parse(registries, list.getCompound(i)).orElse(ItemStack.EMPTY));
		}
		return new ReplayPayload(at, owner, tag.getString("Name"), cause == null ? Component.empty() : cause, floats(tag.getIntArray("Pos")),
			floats(tag.getIntArray("Rot")), tag.getByteArray("Flags"), items, tag.getByteArray("ItemIdx"), tag.getString("Killer"),
			floats(tag.getIntArray("KillerPos")));
	}

	/** Sends the replay to everyone within 48 blocks of the projector. Returns false when there is nothing to play. */
	public static boolean play(ServerLevel level, UUID owner, BlockPos at) {
		ReplayPayload payload = replay(level.getServer(), owner, at);
		if (payload == null || payload.frames() == 0) {
			return false;
		}
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().distSqr(at) < 48 * 48 && ServerPlayNetworking.canSend(p, ReplayPayload.TYPE)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
		return true;
	}

	private static float[] floats(int[] bits) {
		float[] out = new float[bits.length];
		for (int i = 0; i < bits.length; i++) {
			out[i] = Float.intBitsToFloat(bits[i]);
		}
		return out;
	}

	public static void reset() {
		RECORDERS.clear();
	}

	private LastRecord() {
	}
}
