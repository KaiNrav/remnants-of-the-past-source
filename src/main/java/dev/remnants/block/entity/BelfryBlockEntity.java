package dev.remnants.block.entity;

import dev.remnants.RemnantsConfig;
import dev.remnants.SoulResonance;
import dev.remnants.block.BellOfRestBlock;
import dev.remnants.entity.KeenerEntity;
import dev.remnants.entity.RemnantEntity;
import dev.remnants.item.EpitaphItem;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.multiblock.MultiblockPattern;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.registry.ModBlockEntities;
import dev.remnants.registry.ModEntities;
import dev.remnants.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Belfry's heart (the Rest Stone) and the Night of Memory it calls. At night, with a Condensed Soul, the Bell of Rest
 * wakes the dead around the tower: three waves of Forgotten Remnants climb out of the ground in a ring of
 * {@code 8..nightRadius} blocks (the second wave brings a Crowned one, the third a Keener), with a respite between the
 * waves. Clear all three and the bell gives back Soul Fragments, maybe a Remnant Crown, a hero's Epitaph, and Path of the
 * Soul resonance to everyone who stood the night. Dawn, or nobody left within {@link #LEASH} blocks for 30 s, ends it:
 * the dead sink back into the earth. One night per bell per game day.
 *
 * <p>Like every heart, the night only lives in memory: a world reloaded mid-night comes back with the bell quiet (the
 * risen Forgotten stay as ordinary monsters and despawn as they do).
 */
public class BelfryBlockEntity extends HeartBlockEntity {
	public static final int WAVE1 = 1, BREAK1 = 2, WAVE2 = 3, BREAK2 = 4, WAVE3 = 5, VICTORY = 6;
	public static final int BREAK_TICKS = 200, VICTORY_TICKS = 40, SPAWN_EVERY = 6, LEASH = 48, JOIN_RADIUS = 32, AWAY_LIMIT = 600;
	/** The bell sits 3 above the stone. */
	public static final BlockPos BELL = new BlockPos(0, 3, 0);
	/** Night window (day time mod 24000) in which the bell can call the night. */
	public static final long NIGHT_FROM = 13000, NIGHT_UNTIL = 22000;

	private long lastNight = NEVER;
	private final Set<UUID> participants = new HashSet<>();
	private final List<UUID> waveMobs = new ArrayList<>();
	private int pending, waveTotal, awayTicks;
	private boolean keenerPending, crownedPending;
	@Nullable
	private ServerBossEvent bar;

	public BelfryBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BELFRY, pos, state);
	}

	@Override
	public MultiblockPattern pattern() {
		return Multiblocks.belfry();
	}

	@Override
	protected int phaseLength(int phase) {
		return switch (phase) {
			case BREAK1, BREAK2 -> BREAK_TICKS;
			case VICTORY -> VICTORY_TICKS;
			default -> 0;
		};
	}

	@Override
	protected int lastPhase() {
		return VICTORY;
	}

	public static int waveOf(int phase) {
		return phase == WAVE1 ? 1 : phase == WAVE2 ? 2 : phase == WAVE3 ? 3 : 0;
	}

	public long lastNight() {
		return this.lastNight;
	}

	public int waveMobsLeft() {
		return this.waveMobs.size() + this.pending + (this.keenerPending ? 1 : 0);
	}

	public Set<UUID> participants() {
		return this.participants;
	}

	/** Test hook: the mobs of the current wave. */
	public List<UUID> waveMobs() {
		return this.waveMobs;
	}

	// ---- starting a night ----------------------------------------------------------------------

	public static boolean isNightTime(ServerLevel level) {
		long t = level.getDayTime() % 24000L;
		return t >= NIGHT_FROM && t <= NIGHT_UNTIL;
	}

	/** A Condensed Soul rung against the bell (or laid on the stone). */
	public boolean tryStartNight(ServerLevel level, Player player, ItemStack soul) {
		String refusal = null;
		Object arg = null;
		long now = level.getGameTime();
		long cooldown = RemnantsConfig.get().nightCooldown;
		if (!this.formed) {
			refusal = "unformed";
		} else if (this.isWorking()) {
			refusal = "busy";
		} else if (level.dimensionType().hasFixedTime()) {
			refusal = "no_night";
		} else if (!isNightTime(level)) {
			refusal = "day";
		} else if (level.getDifficulty() == Difficulty.PEACEFUL) {
			refusal = "peaceful";
		} else if (this.lastNight != NEVER && now - this.lastNight < cooldown && now >= this.lastNight) {
			refusal = "cooldown";
			long left = cooldown - (now - this.lastNight);
			arg = left / 1200 + 1;
		}
		if (refusal != null) {
			player.displayClientMessage((arg == null ? Component.translatable("message.remnants.belfry." + refusal)
				: Component.translatable("message.remnants.belfry." + refusal, arg)).withStyle(ChatFormatting.GOLD), true);
			level.playSound(null, this.bellPos(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.4F, 0.4F);
			return false;
		}
		soul.consume(1, player);
		this.start(level, player);
		return true;
	}

	/** Starts a Night of Memory now (also the test and command hook; no checks). */
	public void start(ServerLevel level, @Nullable Player starter) {
		this.lastNight = level.getGameTime();
		this.participants.clear();
		this.waveMobs.clear();
		this.awayTicks = 0;
		for (ServerPlayer p : this.playersWithin(level, JOIN_RADIUS)) {
			this.participants.add(p.getUUID());
		}
		if (starter != null) {
			this.participants.add(starter.getUUID());
			this.operator = starter.getUUID();
			this.operatorName = starter.getGameProfile().getName();
		}
		this.bar = new dev.remnants.bossbar.StyledBossEvent(Component.translatable("event.remnants.night"), BossEvent.BossBarColor.PURPLE,
			BossEvent.BossBarOverlay.NOTCHED_10, dev.remnants.bossbar.BossStyle.NIGHT);
		this.bar.setDarkenScreen(true);
		this.bar.setCreateWorldFog(true);
		for (ServerPlayer p : this.participants(level)) {
			title(p, Component.translatable("event.remnants.night").withStyle(ChatFormatting.LIGHT_PURPLE),
				Component.translatable("event.remnants.night.sub").withStyle(ChatFormatting.GRAY));
		}
		this.bell(level, true);
		level.playSound(null, this.worldPosition, SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 2.0F, 0.6F);
		this.setPhase(level, WAVE1);
	}

	// ---- phases --------------------------------------------------------------------------------

	@Override
	protected void onPhaseStart(ServerLevel level, int phase) {
		switch (phase) {
			case WAVE1, WAVE2, WAVE3 -> this.startWave(level, waveOf(phase));
			case BREAK1, BREAK2 -> {
				if (this.bar != null) {
					this.bar.setName(Component.translatable("event.remnants.night.respite", waveOf(phase - 1), 3));
					this.bar.setProgress(0.0F);
					this.bar.setColor(BossEvent.BossBarColor.BLUE);
				}
				BellOfRestBlock.ring(level, this.bellPos(), Direction.NORTH, BellOfRestBlockEntity.HUM, 1.0F);
				for (ServerPlayer p : this.participants(level)) {
					p.displayClientMessage(Component.translatable("event.remnants.night.cleared", waveOf(phase - 1)).withStyle(ChatFormatting.AQUA), true);
				}
			}
			case VICTORY -> this.reward(level);
			default -> this.finish(level);
		}
	}

	@Override
	protected void onPhaseTick(ServerLevel level, int phase, int ticks) {
		if (ticks % 20 == 0) {
			this.track(level);
		}
		if (phase != VICTORY && this.failed(level)) {
			return;
		}
		int wave = waveOf(phase);
		if (wave > 0) {
			if (this.pending > 0 && ticks % SPAWN_EVERY == 0) {
				this.pending--;
				this.spawnForgotten(level, this.crownedPending && this.pending == 0);
			}
			if (this.keenerPending && ticks >= 30) {
				this.keenerPending = false;
				this.spawnKeener(level);
			}
			if (ticks % 10 == 0) {
				this.prune(level);
			}
			if (this.bar != null) {
				this.bar.setProgress(Mth.clamp(this.waveMobsLeft() / (float) Math.max(1, this.waveTotal), 0.0F, 1.0F));
			}
			if (ticks > 20 && this.waveMobsLeft() == 0) {
				this.advance(level);
			}
		} else if (phase == BREAK1 || phase == BREAK2) {
			if (this.bar != null) {
				this.bar.setProgress(Mth.clamp(ticks / (float) BREAK_TICKS, 0.0F, 1.0F));
			}
			if (ticks % 40 == 20) {
				BellOfRestBlock.ring(level, this.bellPos(), Direction.EAST, BellOfRestBlockEntity.HUM, 0.6F);
			}
		}
		if (ticks % 20 == 10 && phase != VICTORY) {
			this.ringParticles(level);
		}
	}

	private void startWave(ServerLevel level, int wave) {
		RemnantsConfig cfg = RemnantsConfig.get();
		int[] sizes = cfg.nightWaveSizes == null || cfg.nightWaveSizes.length < 3 ? new int[] {4, 6, 8} : cfg.nightWaveSizes;
		int extra = Math.max(0, this.participants.size() - 1);
		this.waveMobs.clear();
		this.pending = Math.max(1, sizes[wave - 1] + cfg.nightExtraPerPlayer * extra);
		this.crownedPending = wave == 2;
		this.keenerPending = wave == 3;
		this.waveTotal = this.pending + (this.keenerPending ? 1 : 0);
		if (this.bar != null) {
			this.bar.setName(Component.translatable("event.remnants.night.wave", wave, 3));
			this.bar.setColor(wave == 3 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE);
			this.bar.setProgress(1.0F);
		}
		BellOfRestBlock.ring(level, this.bellPos(), Direction.from2DDataValue(wave), BellOfRestBlockEntity.RING, 1.4F);
		level.playSound(null, this.worldPosition, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 2.0F, 0.5F);
		for (ServerPlayer p : this.participants(level)) {
			title(p, Component.translatable("event.remnants.night.wave", wave, 3).withStyle(wave == 3 ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE),
				Component.translatable("event.remnants.night.wave.sub." + wave).withStyle(ChatFormatting.GRAY));
		}
	}

	// ---- the dead ------------------------------------------------------------------------------

	private void spawnForgotten(ServerLevel level, boolean crowned) {
		BlockPos at = this.spot(level);
		RemnantEntity mob = ModEntities.FORGOTTEN_REMNANT.create(level);
		if (mob == null) {
			return;
		}
		mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
		if (crowned) {
			mob.crown();
		}
		mob.restrictTo(this.worldPosition, RemnantsConfig.get().nightRadius + 6);
		level.addFreshEntity(mob);
		ServerPlayer target = this.nearestParticipant(level, mob.position());
		if (target != null) {
			mob.setTarget(target);
		}
		this.waveMobs.add(mob.getUUID());
	}

	private void spawnKeener(ServerLevel level) {
		BlockPos at = this.spot(level).above(3);
		KeenerEntity keener = ModEntities.KEENER.create(level);
		if (keener == null) {
			return;
		}
		keener.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
		keener.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
		level.addFreshEntity(keener);
		ServerPlayer target = this.nearestParticipant(level, keener.position());
		if (target != null) {
			keener.setTarget(target);
		}
		this.waveMobs.add(keener.getUUID());
		level.playSound(null, at, SoundEvents.GHAST_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.4F);
	}

	/** Somewhere in the ring round the tower a body could climb out (firm ground, two free blocks above). */
	private BlockPos spot(ServerLevel level) {
		int radius = RemnantsConfig.get().nightRadius;
		BlockPos heart = this.worldPosition;
		for (int attempt = 0; attempt < 24; attempt++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double r = 8 + level.random.nextDouble() * Math.max(1, radius - 8);
			int x = heart.getX() + (int) Math.round(Math.cos(a) * r);
			int z = heart.getZ() + (int) Math.round(Math.sin(a) * r);
			for (int y = heart.getY() + 8; y >= heart.getY() - 10; y--) {
				BlockPos p = new BlockPos(x, y, z);
				if (standable(level, p)) {
					return p;
				}
			}
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos p = heart.relative(d, 3);
			if (standable(level, p)) {
				return p;
			}
		}
		return heart.above();
	}

	private static boolean standable(ServerLevel level, BlockPos p) {
		BlockState below = level.getBlockState(p.below());
		return below.isFaceSturdy(level, p.below(), Direction.UP) && below.getFluidState().isEmpty()
			&& level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getFluidState(p).isEmpty()
			&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
	}

	/** Drops the dead that fell or wandered off (they sink back into the ground). */
	private void prune(ServerLevel level) {
		Vec3 c = Vec3.atCenterOf(this.worldPosition);
		for (Iterator<UUID> it = this.waveMobs.iterator(); it.hasNext(); ) {
			Entity e = level.getEntity(it.next());
			if (e == null || !e.isAlive() || e.isRemoved()) {
				it.remove();
			} else if (e.position().distanceToSqr(c) > LEASH * LEASH) {
				sink(level, e);
				it.remove();
			}
		}
	}

	private static void sink(ServerLevel level, Entity e) {
		level.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + 0.5, e.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 0.2, e.getZ(), 6, 0.3, 0.2, 0.3, 0.01);
		e.discard();
	}

	// ---- players -------------------------------------------------------------------------------

	private List<ServerPlayer> playersWithin(ServerLevel level, double radius) {
		Vec3 c = Vec3.atCenterOf(this.worldPosition);
		return level.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.position().distanceToSqr(c) < radius * radius);
	}

	private List<ServerPlayer> participants(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : this.participants) {
			if (level.getPlayerByUUID(id) instanceof ServerPlayer p) {
				out.add(p);
			}
		}
		return out;
	}

	@Nullable
	private ServerPlayer nearestParticipant(ServerLevel level, Vec3 from) {
		ServerPlayer best = null;
		double bestD = Double.MAX_VALUE;
		for (ServerPlayer p : this.participants(level)) {
			double d = p.position().distanceToSqr(from);
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && d < bestD) {
				best = p;
				bestD = d;
			}
		}
		return best;
	}

	/** Newcomers join, the bar follows whoever is near. */
	private void track(ServerLevel level) {
		Vec3 c = Vec3.atCenterOf(this.worldPosition);
		for (ServerPlayer p : this.playersWithin(level, JOIN_RADIUS)) {
			this.participants.add(p.getUUID());
		}
		if (this.bar != null) {
			for (ServerPlayer p : level.players()) {
				double d = p.position().distanceToSqr(c);
				if (d < 56 * 56) {
					this.bar.addPlayer(p);
				} else if (d > 72 * 72) {
					this.bar.removePlayer(p);
				}
			}
		}
	}

	/** Dawn, or nobody left to stand the night: the dead sink back and the bell falls silent. */
	private boolean failed(ServerLevel level) {
		long t = level.getDayTime() % 24000L;
		boolean dawn = t < NIGHT_FROM - 1000 && !level.dimensionType().hasFixedTime();
		boolean anyone = false;
		Vec3 c = Vec3.atCenterOf(this.worldPosition);
		for (ServerPlayer p : this.participants(level)) {
			anyone |= p.isAlive() && !p.isSpectator() && p.position().distanceToSqr(c) < LEASH * LEASH;
		}
		this.awayTicks = anyone ? 0 : this.awayTicks + 1;
		if (dawn || this.awayTicks >= AWAY_LIMIT) {
			this.fail(level, dawn ? "dawn" : "forsaken");
			return true;
		}
		return false;
	}

	public void fail(ServerLevel level, String reason) {
		if (this.phase == 0) {
			return;
		}
		for (ServerPlayer p : this.participants(level)) {
			p.displayClientMessage(Component.translatable("event.remnants.night.failed." + reason).withStyle(ChatFormatting.GRAY), false);
		}
		for (UUID id : this.waveMobs) {
			Entity e = level.getEntity(id);
			if (e != null && e.isAlive()) {
				sink(level, e);
			}
		}
		this.waveMobs.clear();
		this.pending = 0;
		this.keenerPending = false;
		level.playSound(null, this.bellPos(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 0.3F);
		this.setPhase(level, 0);
	}

	// ---- the end -------------------------------------------------------------------------------

	private void reward(ServerLevel level) {
		RemnantsConfig cfg = RemnantsConfig.get();
		List<ServerPlayer> standing = this.participants(level);
		int extra = Math.max(0, this.participants.size() - 1);
		int fragments = cfg.nightFragmentsMin + level.random.nextInt(Math.max(1, cfg.nightFragmentsMax - cfg.nightFragmentsMin + 1)) + 2 * extra;
		Vec3 at = Vec3.atCenterOf(this.worldPosition).add(0, 1.0, 0);
		drop(level, at, new ItemStack(ModItems.SOUL_FRAGMENT, fragments));
		if (level.random.nextDouble() < cfg.nightCrownChance) {
			drop(level, at, new ItemStack(ModItems.REMNANT_CROWN));
		}
		drop(level, at, EpitaphItem.hero(level.random, this.worldPosition, level.dimension().location().toString(), level.getDayTime() / 24000L + 1));
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 2.0, at.z, 60, 1.0, 1.6, 1.0, 0.05);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 2.5, at.z, 30, 0.6, 1.2, 0.6, 0.04);
		BellOfRestBlock.ring(level, this.bellPos(), Direction.SOUTH, BellOfRestBlockEntity.RING, 1.6F);
		level.playSound(null, this.worldPosition, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 0.9F);
		for (ServerPlayer p : standing) {
			title(p, Component.translatable("event.remnants.night.won").withStyle(ChatFormatting.AQUA),
				Component.translatable("event.remnants.night.won.sub").withStyle(ChatFormatting.GRAY));
			SoulResonance.award(p, cfg.nightResonance, "night");
			award(p, "night_of_memory");
		}
	}

	private static void drop(ServerLevel level, Vec3 at, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack);
		item.setDeltaMovement(level.random.nextGaussian() * 0.05, 0.25, level.random.nextGaussian() * 0.05);
		item.setPickUpDelay(20);
		level.addFreshEntity(item);
	}

	private void finish(ServerLevel level) {
		if (this.bar != null) {
			this.bar.removeAllPlayers();
			this.bar = null;
		}
		this.bell(level, false);
		this.waveMobs.clear();
		this.pending = 0;
		this.keenerPending = false;
	}

	private void bell(ServerLevel level, boolean night) {
		if (level.getBlockEntity(this.bellPos()) instanceof BellOfRestBlockEntity bell) {
			bell.setNight(night);
		}
	}

	/** A thin ring of soul light marks how far the dead rise. */
	private void ringParticles(ServerLevel level) {
		int radius = RemnantsConfig.get().nightRadius;
		BlockPos h = this.worldPosition;
		for (int i = 0; i < 24; i++) {
			double a = (i + level.random.nextDouble()) / 24.0 * Math.PI * 2;
			level.sendParticles(ParticleTypes.SOUL, h.getX() + 0.5 + Math.cos(a) * radius, h.getY() + 0.3, h.getZ() + 0.5 + Math.sin(a) * radius,
				1, 0.1, 0.1, 0.1, 0.0);
		}
	}

	public BlockPos bellPos() {
		return this.worldPosition.offset(MultiblockPattern.rotate(BELL, this.rotation));
	}

	private static void title(ServerPlayer p, Component title, Component sub) {
		p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
		p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
		p.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	// ---- heart plumbing ------------------------------------------------------------------------

	@Override
	public void disassemble(ServerLevel level) {
		if (this.phase != 0) {
			this.fail(level, "broken");
		}
		super.disassemble(level);
	}

	@Override
	public void onHeartRemoved(ServerLevel level) {
		if (this.phase != 0) {
			this.fail(level, "broken");
		}
		super.onHeartRemoved(level);
	}

	@Override
	public void setRemoved() {
		if (this.bar != null) {
			this.bar.removeAllPlayers();
			this.bar = null;
		}
		super.setRemoved();
	}

	/** The night is not a craft: it cannot be called off. */
	@Override
	public boolean cancel(ServerLevel level, @Nullable Player player) {
		if (player != null && this.phase != 0) {
			player.displayClientMessage(Component.translatable("message.remnants.belfry.no_cancel").withStyle(ChatFormatting.GRAY), true);
		}
		return false;
	}

	@Override
	protected List<ItemStack> takeAll() {
		return List.of();
	}

	@Override
	protected void saveContents(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putLong("LastNight", this.lastNight);
	}

	@Override
	protected void loadContents(CompoundTag tag, HolderLookup.Provider registries) {
		this.lastNight = tag.contains("LastNight") ? tag.getLong("LastNight") : NEVER;
	}
}
