package dev.remnants;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.NbtOps;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * World-level memory (data/remnants_of_the_past.dat in the overworld): each player's death streak and where their
 * unclaimed Remnants are, even when those chunks are unloaded. Drives tier scaling and the Spectral Compass.
 * Also each player's Soul Anchor binding (+ charges), an inventory a Phylactery is holding until respawn, their
 * Soul Resonance (points and tallies) and the soul-bound items waiting for them on the other side.
 */
public class RemnantTracker extends SavedData {
	private static final String NAME = RemnantsMod.MOD_ID;

	public record Location(ResourceKey<Level> dimension, BlockPos pos, int tier) {
	}

	private static final class PlayerData {
		int streak;
		final Map<UUID, Location> remnants = new LinkedHashMap<>();
		@Nullable
		Location anchor;
		int anchorCharges;
		@Nullable
		ListTag keptInventory;
		/** Soul-bound items (the Codex; the weapon / armor kept by Resonance perks), given back on respawn. */
		@Nullable
		ListTag soulbound;
		int resonance;
		int laidOwn;
		int laidForgotten;
		int laidOthers;
		int kingsSlain;
		long lastBreathAt = Long.MIN_VALUE / 2;
		/** Mourner's set: when Ghost Step last saved this player (1.3). */
		long ghostStepAt = Long.MIN_VALUE / 2;
		boolean codexGiven;
		/** The Chronicle, oldest first (at most Chronicle.LIMIT). */
		final List<Chronicle.Entry> chronicle = new ArrayList<>();
		// ---- 1.5 Archive
		/** Path of the Soul branch ("" = none yet; see PathBranch). */
		String branch = "";
		/** Tenths of a point the Memorial bonus has not paid out yet. */
		float resonanceRemainder;
		int archivistsSlain;
		/** Soul Anchors attuned to this player's network, in the order they were added. */
		final List<AnchorLink> anchors = new ArrayList<>();
		/** Full Memorial walls this player locked ("dim x y z" of each projector). */
		final java.util.Set<String> memorials = new java.util.LinkedHashSet<>();
		/** The last 10 s before the latest death (LastRecord's own format). */
		@Nullable
		CompoundTag lastRecord;
	}

	/** One Soul Anchor in a player's network: where it stands and what it is called ("" = unnamed). */
	public record AnchorLink(ResourceKey<Level> dimension, BlockPos pos, String name) {
	}

	/** What the Codex shows on its Soul Resonance page. */
	public record Resonance(int points, int laidOwn, int laidForgotten, int laidOthers, int kingsSlain, long lastBreathAt) {
	}

	public static final int MAX_ANCHOR_CHARGES = 3;

	private final Map<UUID, PlayerData> players = new HashMap<>();

	public static RemnantTracker get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(
			new SavedData.Factory<>(RemnantTracker::new, RemnantTracker::load, null), NAME);
	}

	private PlayerData data(UUID player) {
		return this.players.computeIfAbsent(player, id -> new PlayerData());
	}

	/** Records a new Remnant and returns its tier (= deaths in a row without reclaiming, before this one). */
	public int spawned(UUID owner, UUID remnant, ServerLevel level, BlockPos pos) {
		PlayerData d = this.data(owner);
		int tier = d.streak;
		d.streak++;
		d.remnants.put(remnant, new Location(level.dimension(), pos.immutable(), tier));
		this.setDirty();
		return tier;
	}

	public void update(UUID owner, UUID remnant, ServerLevel level, BlockPos pos, int tier) {
		PlayerData d = this.data(owner);
		Location old = d.remnants.get(remnant);
		if (old == null || !old.pos().equals(pos) || old.dimension() != level.dimension()) {
			d.remnants.put(remnant, new Location(level.dimension(), pos.immutable(), tier));
			this.setDirty();
		}
	}

	/** The Remnant was defeated and the items came back: the streak resets. */
	public void reclaimed(UUID owner, UUID remnant) {
		PlayerData d = this.data(owner);
		d.remnants.remove(remnant);
		d.streak = 0;
		this.setDirty();
	}

	/** The Remnant faded away without being defeated: forget it, keep the streak. */
	public void forget(UUID owner, UUID remnant) {
		if (this.data(owner).remnants.remove(remnant) != null) {
			this.setDirty();
		}
	}

	public int streak(UUID owner) {
		return this.data(owner).streak;
	}

	public Map<UUID, Location> remnants(UUID owner) {
		return this.data(owner).remnants;
	}

	public Optional<Location> nearest(UUID owner, ResourceKey<Level> dimension, BlockPos from) {
		return this.data(owner).remnants.values().stream()
			.filter(l -> l.dimension() == dimension)
			.min(Comparator.comparingDouble(l -> l.pos().distSqr(from)));
	}

	/** Like {@link #nearest}, but first forgets entries whose chunk is loaded and whose Remnant is no longer there. */
	public Optional<Location> nearestLiving(MinecraftServer server, UUID owner, ResourceKey<Level> dimension, BlockPos from) {
		this.prune(server, owner);
		return this.nearest(owner, dimension, from);
	}

	public void prune(MinecraftServer server, UUID owner) {
		boolean removed = this.data(owner).remnants.entrySet().removeIf(e -> {
			ServerLevel level = server.getLevel(e.getValue().dimension());
			if (level == null) {
				return true;
			}
			// a loaded chunk's entities arrive a few ticks after its blocks: until then a missing Remnant is not a dead one
			if (!level.isLoaded(e.getValue().pos())
				|| !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(e.getValue().pos()))) {
				return false;
			}
			Entity entity = level.getEntity(e.getKey());
			return entity == null || !entity.isAlive();
		});
		if (removed) {
			this.setDirty();
		}
	}

	// ---- Soul Anchor -------------------------------------------------------------------------------

	/** Binds the player to this anchor (moving the binding here if it was elsewhere) and adds a charge. Returns the
	 * new charge count, or -1 when already full. */
	public int chargeAnchor(UUID player, ResourceKey<Level> dimension, BlockPos pos) {
		PlayerData d = this.data(player);
		boolean same = d.anchor != null && d.anchor.dimension() == dimension && d.anchor.pos().equals(pos);
		if (same && d.anchorCharges >= MAX_ANCHOR_CHARGES) {
			return -1;
		}
		d.anchor = new Location(dimension, pos.immutable(), 0);
		d.anchorCharges = same ? d.anchorCharges + 1 : Math.max(1, Math.min(MAX_ANCHOR_CHARGES, d.anchorCharges + 1));
		this.setDirty();
		return d.anchorCharges;
	}

	public Optional<Location> anchor(UUID player) {
		PlayerData d = this.data(player);
		return d.anchor != null && d.anchorCharges > 0 ? Optional.of(d.anchor) : Optional.empty();
	}

	public int anchorCharges(UUID player) {
		return this.data(player).anchorCharges;
	}

	/** Spends one charge; the binding is dropped with the last one. Returns the charges left. */
	public int useAnchorCharge(UUID player) {
		PlayerData d = this.data(player);
		d.anchorCharges = Math.max(0, d.anchorCharges - 1);
		if (d.anchorCharges == 0) {
			d.anchor = null;
		}
		this.setDirty();
		return d.anchorCharges;
	}

	public void clearAnchor(UUID player) {
		PlayerData d = this.data(player);
		d.anchor = null;
		d.anchorCharges = 0;
		this.setDirty();
	}

	/** Whether anyone still has a charged binding at this anchor (drives its lit state). */
	public boolean anchorInUse(ResourceKey<Level> dimension, BlockPos pos) {
		return this.players.values().stream().anyMatch(d -> d.anchor != null && d.anchorCharges > 0
			&& d.anchor.dimension() == dimension && d.anchor.pos().equals(pos));
	}

	// ---- Phylactery ---------------------------------------------------------------------------------

	public void keepInventory(UUID player, ListTag inventory) {
		this.data(player).keptInventory = inventory;
		this.setDirty();
	}

	@Nullable
	public ListTag takeKeptInventory(UUID player) {
		PlayerData d = this.data(player);
		ListTag kept = d.keptInventory;
		if (kept != null) {
			d.keptInventory = null;
			this.setDirty();
		}
		return kept;
	}

	// ---- soul-bound items --------------------------------------------------------------------------

	public void addSoulbound(UUID player, CompoundTag entry) {
		PlayerData d = this.data(player);
		if (d.soulbound == null) {
			d.soulbound = new ListTag();
		}
		d.soulbound.add(entry);
		this.setDirty();
	}

	@Nullable
	public ListTag takeSoulbound(UUID player) {
		PlayerData d = this.data(player);
		ListTag list = d.soulbound;
		if (list != null) {
			d.soulbound = null;
			this.setDirty();
		}
		return list;
	}

	// ---- Soul Resonance ------------------------------------------------------------------------------

	public Resonance resonance(UUID player) {
		PlayerData d = this.data(player);
		return new Resonance(d.resonance, d.laidOwn, d.laidForgotten, d.laidOthers, d.kingsSlain, d.lastBreathAt);
	}

	/** Adds points and bumps the matching tally ("own", "forgotten", "other", "king" or "" for none). Returns the new total. */
	public int addResonance(UUID player, int points, String tally) {
		PlayerData d = this.data(player);
		d.resonance = Math.max(0, d.resonance + points);
		switch (tally) {
			case "own" -> d.laidOwn++;
			case "forgotten" -> d.laidForgotten++;
			case "other" -> d.laidOthers++;
			case "king" -> d.kingsSlain++;
			case "archivist" -> d.archivistsSlain++;
			default -> {
			}
		}
		this.setDirty();
		return d.resonance;
	}

	public void setLastBreath(UUID player, long gameTime) {
		this.data(player).lastBreathAt = gameTime;
		this.setDirty();
	}

	public long ghostStepAt(UUID player) {
		return this.data(player).ghostStepAt;
	}

	public void setGhostStep(UUID player, long gameTime) {
		this.data(player).ghostStepAt = gameTime;
		this.setDirty();
	}

	/** True the first time it is asked for this player (the Codex is handed out once). */
	public boolean claimCodex(UUID player) {
		PlayerData d = this.data(player);
		if (d.codexGiven) {
			return false;
		}
		d.codexGiven = true;
		this.setDirty();
		return true;
	}

	// ---- the Chronicle --------------------------------------------------------------------------------

	public List<Chronicle.Entry> chronicle(UUID player) {
		return java.util.Collections.unmodifiableList(this.data(player).chronicle);
	}

	/** Writes an entry; past the limit the oldest ones are dropped. */
	public void addChronicle(UUID player, Chronicle.Entry entry) {
		List<Chronicle.Entry> list = this.data(player).chronicle;
		list.add(entry);
		while (list.size() > Chronicle.LIMIT) {
			list.remove(0);
		}
		this.setDirty();
	}

	// ---- 1.5: Path branches, the anchor network, Memorials, the last record --------------------------------------

	public String branch(UUID player) {
		return this.data(player).branch;
	}

	public void setBranch(UUID player, String branch) {
		this.data(player).branch = branch;
		this.setDirty();
	}

	/** Adds {@code tenths} / 10 points of bonus; returns the whole points it now pays out (the rest waits). */
	public int bonusTenths(UUID player, float tenths) {
		PlayerData d = this.data(player);
		d.resonanceRemainder += tenths;
		int whole = (int) (d.resonanceRemainder / 10.0F);
		d.resonanceRemainder -= whole * 10.0F;
		this.setDirty();
		return whole;
	}

	public int archivistsSlain(UUID player) {
		return this.data(player).archivistsSlain;
	}

	public List<AnchorLink> anchors(UUID player) {
		return java.util.Collections.unmodifiableList(this.data(player).anchors);
	}

	/** Adds (or renames, when {@code name} is not null) an anchor in the player's network. Returns false when full. */
	public boolean linkAnchor(UUID player, ResourceKey<Level> dimension, BlockPos pos, @Nullable String name, int max) {
		List<AnchorLink> list = this.data(player).anchors;
		for (int i = 0; i < list.size(); i++) {
			AnchorLink a = list.get(i);
			if (a.dimension() == dimension && a.pos().equals(pos)) {
				if (name != null && !name.equals(a.name())) {
					list.set(i, new AnchorLink(dimension, pos.immutable(), name));
					this.setDirty();
				}
				return true;
			}
		}
		if (list.size() >= max) {
			return false;
		}
		list.add(new AnchorLink(dimension, pos.immutable(), name == null ? "" : name));
		this.setDirty();
		return true;
	}

	public boolean isLinked(UUID player, ResourceKey<Level> dimension, BlockPos pos) {
		return this.data(player).anchors.stream().anyMatch(a -> a.dimension() == dimension && a.pos().equals(pos));
	}

	public void unlinkAnchor(UUID player, ResourceKey<Level> dimension, BlockPos pos) {
		if (this.data(player).anchors.removeIf(a -> a.dimension() == dimension && a.pos().equals(pos))) {
			this.setDirty();
		}
	}

	/** Records whether the Memorial at {@code key} ("dim x y z") is a full, locked wall of this player's. */
	public void setMemorial(UUID player, String key, boolean full) {
		java.util.Set<String> set = this.data(player).memorials;
		if (full ? set.add(key) : set.remove(key)) {
			this.setDirty();
		}
	}

	public boolean hasFullMemorial(UUID player) {
		return !this.data(player).memorials.isEmpty();
	}

	@Nullable
	public CompoundTag lastRecord(UUID player) {
		return this.data(player).lastRecord;
	}

	public void setLastRecord(UUID player, @Nullable CompoundTag record) {
		this.data(player).lastRecord = record;
		this.setDirty();
	}

	public Optional<Location> any(UUID owner) {
		return this.data(owner).remnants.values().stream().findFirst();
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
		ListTag list = new ListTag();
		this.players.forEach((uuid, d) -> {
			CompoundTag p = new CompoundTag();
			p.putUUID("Player", uuid);
			p.putInt("Streak", d.streak);
			ListTag rs = new ListTag();
			d.remnants.forEach((id, loc) -> {
				CompoundTag r = new CompoundTag();
				r.putUUID("Id", id);
				r.putString("Dim", loc.dimension().location().toString());
				r.putIntArray("Pos", new int[]{loc.pos().getX(), loc.pos().getY(), loc.pos().getZ()});
				r.putInt("Tier", loc.tier());
				rs.add(r);
			});
			p.put("Remnants", rs);
			if (d.anchor != null && d.anchorCharges > 0) {
				p.putString("AnchorDim", d.anchor.dimension().location().toString());
				p.putIntArray("AnchorPos", new int[]{d.anchor.pos().getX(), d.anchor.pos().getY(), d.anchor.pos().getZ()});
				p.putInt("AnchorCharges", d.anchorCharges);
			}
			if (d.keptInventory != null) {
				p.put("KeptInventory", d.keptInventory);
			}
			if (d.soulbound != null) {
				p.put("Soulbound", d.soulbound);
			}
			p.putInt("Resonance", d.resonance);
			p.putInt("LaidOwn", d.laidOwn);
			p.putInt("LaidForgotten", d.laidForgotten);
			p.putInt("LaidOthers", d.laidOthers);
			p.putInt("KingsSlain", d.kingsSlain);
			p.putLong("LastBreathAt", d.lastBreathAt);
			p.putLong("GhostStepAt", d.ghostStepAt);
			p.putBoolean("CodexGiven", d.codexGiven);
			if (!d.chronicle.isEmpty()) {
				ListTag chronicle = new ListTag();
				var ops = provider.createSerializationContext(NbtOps.INSTANCE);
				for (Chronicle.Entry e : d.chronicle) {
					Chronicle.Entry.CODEC.encodeStart(ops, e).result().ifPresent(chronicle::add);
				}
				p.put("Chronicle", chronicle);
			}
			if (!d.branch.isEmpty()) {
				p.putString("Branch", d.branch);
			}
			p.putFloat("ResonanceRemainder", d.resonanceRemainder);
			p.putInt("ArchivistsSlain", d.archivistsSlain);
			if (!d.anchors.isEmpty()) {
				ListTag anchors = new ListTag();
				for (AnchorLink a : d.anchors) {
					CompoundTag t = new CompoundTag();
					t.putString("Dim", a.dimension().location().toString());
					t.putIntArray("Pos", new int[]{a.pos().getX(), a.pos().getY(), a.pos().getZ()});
					t.putString("Name", a.name());
					anchors.add(t);
				}
				p.put("Anchors", anchors);
			}
			if (!d.memorials.isEmpty()) {
				ListTag memorials = new ListTag();
				d.memorials.forEach(k -> memorials.add(net.minecraft.nbt.StringTag.valueOf(k)));
				p.put("Memorials", memorials);
			}
			if (d.lastRecord != null) {
				p.put("LastRecord", d.lastRecord);
			}
			list.add(p);
		});
		tag.put("Players", list);
		return tag;
	}

	private static RemnantTracker load(CompoundTag tag, HolderLookup.Provider provider) {
		RemnantTracker t = new RemnantTracker();
		ListTag list = tag.getList("Players", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag p = list.getCompound(i);
			PlayerData d = t.data(p.getUUID("Player"));
			d.streak = p.getInt("Streak");
			ListTag rs = p.getList("Remnants", Tag.TAG_COMPOUND);
			for (int j = 0; j < rs.size(); j++) {
				CompoundTag r = rs.getCompound(j);
				ResourceLocation dim = ResourceLocation.tryParse(r.getString("Dim"));
				int[] pos = r.getIntArray("Pos");
				if (dim != null && pos.length == 3) {
					d.remnants.put(r.getUUID("Id"), new Location(ResourceKey.create(Registries.DIMENSION, dim),
						new BlockPos(pos[0], pos[1], pos[2]), r.getInt("Tier")));
				}
			}
			ResourceLocation anchorDim = p.contains("AnchorDim") ? ResourceLocation.tryParse(p.getString("AnchorDim")) : null;
			int[] anchorPos = p.getIntArray("AnchorPos");
			if (anchorDim != null && anchorPos.length == 3) {
				d.anchor = new Location(ResourceKey.create(Registries.DIMENSION, anchorDim), new BlockPos(anchorPos[0], anchorPos[1], anchorPos[2]), 0);
				d.anchorCharges = p.getInt("AnchorCharges");
			}
			if (p.contains("KeptInventory")) {
				d.keptInventory = p.getList("KeptInventory", Tag.TAG_COMPOUND);
			}
			if (p.contains("Soulbound")) {
				d.soulbound = p.getList("Soulbound", Tag.TAG_COMPOUND);
			}
			d.resonance = p.getInt("Resonance");
			d.laidOwn = p.getInt("LaidOwn");
			d.laidForgotten = p.getInt("LaidForgotten");
			d.laidOthers = p.getInt("LaidOthers");
			d.kingsSlain = p.getInt("KingsSlain");
			if (p.contains("LastBreathAt")) {
				d.lastBreathAt = p.getLong("LastBreathAt");
			}
			if (p.contains("GhostStepAt")) {
				d.ghostStepAt = p.getLong("GhostStepAt");
			}
			d.codexGiven = p.getBoolean("CodexGiven");
			ListTag chronicle = p.getList("Chronicle", Tag.TAG_COMPOUND);
			var ops = provider.createSerializationContext(NbtOps.INSTANCE);
			for (int j = 0; j < chronicle.size(); j++) {
				Chronicle.Entry.CODEC.parse(ops, chronicle.get(j)).result().ifPresent(d.chronicle::add);
			}
			d.branch = p.getString("Branch");
			d.resonanceRemainder = p.getFloat("ResonanceRemainder");
			d.archivistsSlain = p.getInt("ArchivistsSlain");
			ListTag anchors = p.getList("Anchors", Tag.TAG_COMPOUND);
			for (int j = 0; j < anchors.size(); j++) {
				CompoundTag a = anchors.getCompound(j);
				ResourceLocation dim = ResourceLocation.tryParse(a.getString("Dim"));
				int[] pos = a.getIntArray("Pos");
				if (dim != null && pos.length == 3) {
					d.anchors.add(new AnchorLink(ResourceKey.create(Registries.DIMENSION, dim), new BlockPos(pos[0], pos[1], pos[2]), a.getString("Name")));
				}
			}
			ListTag memorials = p.getList("Memorials", Tag.TAG_STRING);
			for (int j = 0; j < memorials.size(); j++) {
				d.memorials.add(memorials.getString(j));
			}
			if (p.contains("LastRecord", Tag.TAG_COMPOUND)) {
				d.lastRecord = p.getCompound("LastRecord");
			}
		}
		return t;
	}
}
