package dev.remnants;

import dev.remnants.entity.RemnantEntity;
import dev.remnants.net.ResonancePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * The Path of the Soul: the reason to seek fights instead of fleeing them. Laying Remnants to rest (your own, other
 * players', Forgotten ones) and slaying the Soul King earns Soul Resonance; five ranks each unlock a permanent perk
 * that bends the death loop in your favour. Shown in the Soul Codex.
 */
public final class SoulResonance {
	/** Points needed for ranks I..V. */
	public static final int[] THRESHOLDS = {5, 15, 30, 50, 80};
	/** Last Breath recharges once per in-game day. */
	public static final long LAST_BREATH_COOLDOWN = 24000L;

	public enum Perk {
		/** I: respawn with Regeneration, Speed and Strength while a Remnant of yours is out there. */
		VENGEANCE(1),
		/** II: +20 % damage to your own Remnants, and they hit you 15 % softer. */
		SOUL_BOND(2),
		/** III: the item in your main hand stays with you when you die. */
		FAITHFUL_BLADE(3),
		/** IV: once per in-game day a killing blow leaves you on three hearts instead. */
		LAST_BREATH(4),
		/** V: the armor you wear stays with you when you die. */
		UNDYING_MEMORY(5);

		public final int rank;

		Perk(int rank) {
			this.rank = rank;
		}

		public String key() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	public static int rankOf(int points) {
		int rank = 0;
		for (int t : THRESHOLDS) {
			if (points >= t) {
				rank++;
			}
		}
		return rank;
	}

	public static boolean has(ServerPlayer player, Perk perk) {
		return RemnantsConfig.get().soulResonance
			&& rankOf(RemnantTracker.get(player.server).resonance(player.getUUID()).points()) >= perk.rank;
	}

	// ---- earning -----------------------------------------------------------------------------------

	/** Points for laying a Remnant to rest: your own are worth the most (more for a long streak or an Empowered one). */
	public static void onRemnantLaidToRest(ServerPlayer killer, RemnantEntity remnant) {
		if (remnant.isIllusion()) {
			return;
		}
		if (remnant.isForgotten()) {
			award(killer, remnant.isEmpowered() ? 3 : 1, "forgotten");
		} else if (remnant.isOwner(killer)) {
			award(killer, 3 + Math.min(4, remnant.getTier()) + (remnant.isEmpowered() ? 3 : 0), "own");
		} else if (remnant.getOwnerUuid().isPresent()) {
			award(killer, 2, "other");
		}
	}

	/** Everyone who stood in the fight shares the King's fall. */
	public static void onKingSlain(LivingEntity king) {
		if (!(king.level() instanceof ServerLevel level)) {
			return;
		}
		for (ServerPlayer p : level.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(king) < 48 * 48)) {
			award(p, 15, "king");
		}
	}

	/** Everyone who stood in the fight shares the Archivist's fall (1.5). */
	public static void onArchivistSlain(LivingEntity archivist) {
		if (!(archivist.level() instanceof ServerLevel level)) {
			return;
		}
		for (ServerPlayer p : level.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(archivist) < 48 * 48)) {
			award(p, RemnantsConfig.get().archivistResonance, "archivist");
		}
	}

	public static void award(ServerPlayer player, int points, String tally) {
		if (!RemnantsConfig.get().soulResonance || points <= 0) {
			return;
		}
		RemnantTracker tracker = RemnantTracker.get(player.server);
		// 1.5: a full Memorial wall adds its share (fractions wait for the next award)
		if (tracker.hasFullMemorial(player.getUUID()) && RemnantsConfig.get().memorialResonanceBonus > 0) {
			points += tracker.bonusTenths(player.getUUID(), (float) (points * RemnantsConfig.get().memorialResonanceBonus * 10.0));
		}
		int before = tracker.resonance(player.getUUID()).points();
		int after = tracker.addResonance(player.getUUID(), points, tally);
		int rank = rankOf(after);
		Component progress = rank >= THRESHOLDS.length ? Component.literal(Integer.toString(after))
			: Component.literal(after + "/" + THRESHOLDS[rank]);
		player.sendSystemMessage(Component.translatable("message.remnants.resonance.gain", points, progress).withStyle(ChatFormatting.DARK_AQUA));
		if (rank > rankOf(before)) {
			rankUp(player, rank);
		}
		sync(player);
	}

	private static void rankUp(ServerPlayer player, int rank) {
		Perk perk = Perk.values()[rank - 1];
		Component rankName = Component.translatable("resonance.remnants.rank." + rank);
		Component perkName = Component.translatable("resonance.remnants.perk." + perk.key());
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.remnants.resonance.rank.sub", perkName)
			.withStyle(ChatFormatting.AQUA)));
		player.connection.send(new ClientboundSetTitleTextPacket(rankName.copy().withStyle(ChatFormatting.LIGHT_PURPLE)));
		player.sendSystemMessage(Component.translatable("message.remnants.resonance.rank", rank, rankName, perkName,
			Component.translatable("resonance.remnants.perk." + perk.key() + ".desc")).withStyle(ChatFormatting.LIGHT_PURPLE));
		player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 0.8F);
		player.serverLevel().sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 0.8, 0.5, 0.05);
	}

	// ---- perks -------------------------------------------------------------------------------------

	/**
	 * Last Breath (rank IV): called from ALLOW_DEATH before anything else. Returns true when it saved the player (the
	 * death is then cancelled). A totem in hand goes first; /kill and the void can't be cheated.
	 */
	public static boolean tryLastBreath(ServerPlayer player, DamageSource source) {
		if (!has(player, Perk.LAST_BREATH) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
			|| player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			return false;
		}
		RemnantTracker tracker = RemnantTracker.get(player.server);
		long now = player.server.overworld().getGameTime();
		if (now - tracker.resonance(player.getUUID()).lastBreathAt() < LAST_BREATH_COOLDOWN) {
			return false;
		}
		tracker.setLastBreath(player.getUUID(), now);
		player.setHealth(6.0F);
		player.clearFire();
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 2));
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 50, 0.4, 0.8, 0.4, 0.08);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 0.8, 0.5, 0.04);
		level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 0.6F);
		level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 2.0F, 0.6F);
		player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 15));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.remnants.last_breath.sub").withStyle(ChatFormatting.GRAY)));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.remnants.last_breath").withStyle(ChatFormatting.AQUA)));
		sync(player);
		return true;
	}

	/** Vengeance (rank I): back from the dead with a Remnant to hunt, you come back hungry for it. */
	public static void onRespawn(ServerPlayer player) {
		if (has(player, Perk.VENGEANCE) && !RemnantTracker.get(player.server).remnants(player.getUUID()).isEmpty()) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 0));
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1800, 0));
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1800, 0));
			player.sendSystemMessage(Component.translatable("message.remnants.vengeance").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
		}
		sync(player);
	}

	// ---- client sync -------------------------------------------------------------------------------

	public static void sync(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, ResonancePayload.TYPE)) {
			return;
		}
		RemnantTracker tracker = RemnantTracker.get(player.server);
		RemnantTracker.Resonance r = tracker.resonance(player.getUUID());
		long since = player.server.overworld().getGameTime() - r.lastBreathAt();
		int breath = (int) Math.max(0, Math.min(LAST_BREATH_COOLDOWN, LAST_BREATH_COOLDOWN - since));
		ServerPlayNetworking.send(player, new ResonancePayload(r.points(), r.laidOwn(), r.laidForgotten(), r.laidOthers(), r.kingsSlain(),
			tracker.streak(player.getUUID()), tracker.remnants(player.getUUID()).size(), breath, tracker.archivistsSlain(player.getUUID()),
			tracker.branch(player.getUUID()), RemnantsConfig.get().branchRank, tracker.hasFullMemorial(player.getUUID())));
	}

	private SoulResonance() {
	}
}
