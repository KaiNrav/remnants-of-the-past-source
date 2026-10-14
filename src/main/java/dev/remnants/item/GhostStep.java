package dev.remnants.item;

import dev.remnants.RemnantTracker;
import dev.remnants.RemnantsConfig;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.net.GhostFormPayload;
import dev.remnants.registry.ModItems;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The Mourner's set bonus, Ghost Step: wearing all four pieces, a killing blow turns you into a ghost for
 * {@code ghostStepTicks} (2 s) instead - invulnerable, translucent, and mobs lose you and swing through you - then you
 * stand again on two hearts. Cooldown {@code ghostStepCooldown} (10 min, shown on the pieces' tooltips and as their
 * item cooldown).
 *
 * <p>Order on a killing blow: a Totem of Undying in hand goes first, then the Path of the Soul's Last Breath, then Ghost
 * Step; it never fires in the same tick as Last Breath. /kill and the void cannot be cheated.
 */
public final class GhostStep {
	private static final Map<UUID, Long> ACTIVE = new HashMap<>();
	public static final float HEALTH_AFTER = 4.0F;

	public static boolean wearsSet(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.MOURNER_HOOD) && entity.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.MOURNER_ROBE)
			&& entity.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.MOURNER_LEGGINGS) && entity.getItemBySlot(EquipmentSlot.FEET).is(ModItems.MOURNER_BOOTS);
	}

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	public static boolean isGhost(ServerPlayer player) {
		Long end = ACTIVE.get(player.getUUID());
		return end != null && end > now(player.server);
	}

	/** Ticks until Ghost Step is ready again (0 = ready). */
	public static long cooldownLeft(ServerPlayer player) {
		long since = now(player.server) - RemnantTracker.get(player.server).ghostStepAt(player.getUUID());
		return Math.max(0, RemnantsConfig.get().ghostStepCooldown - since);
	}

	/** ALLOW_DEATH, after Last Breath: true when Ghost Step saved the player (the death is then cancelled). */
	public static boolean tryGhostStep(ServerPlayer player, DamageSource source) {
		if (!wearsSet(player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
			|| player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			return false;
		}
		MinecraftServer server = player.server;
		RemnantTracker tracker = RemnantTracker.get(server);
		long now = now(server);
		if (cooldownLeft(player) > 0 || tracker.resonance(player.getUUID()).lastBreathAt() == now) {
			return false;
		}
		RemnantsConfig cfg = RemnantsConfig.get();
		tracker.setGhostStep(player.getUUID(), now);
		ACTIVE.put(player.getUUID(), now + cfg.ghostStepTicks);
		player.setHealth(HEALTH_AFTER);
		player.clearFire();
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, cfg.ghostStepTicks, 0, false, false, true));
		for (Item piece : new Item[]{ModItems.MOURNER_HOOD, ModItems.MOURNER_ROBE, ModItems.MOURNER_LEGGINGS, ModItems.MOURNER_BOOTS}) {
			player.getCooldowns().addCooldown(piece, cfg.ghostStepCooldown);
		}
		loseTrack(player);
		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.06);
		level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(), 12, 0.3, 0.6, 0.3, 0.04);
		level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 2.0F, 0.5F);
		level.playSound(null, player.blockPosition(), SoundEvents.ALLAY_HURT, SoundSource.PLAYERS, 0.8F, 0.6F);
		player.connection.send(new ClientboundSetTitlesAnimationPacket(4, 26, 10));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.remnants.ghost_step.sub").withStyle(ChatFormatting.GRAY)));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.remnants.ghost_step").withStyle(ChatFormatting.AQUA)));
		send(player, cfg.ghostStepTicks);
		HeartBlockEntity.award(player, "ghost_step");
		return true;
	}

	private static void send(ServerPlayer player, int ticks) {
		GhostFormPayload payload = new GhostFormPayload(player.getId(), ticks);
		if (ServerPlayNetworking.canSend(player, GhostFormPayload.TYPE)) {
			ServerPlayNetworking.send(player, payload);
		}
		for (ServerPlayer other : PlayerLookup.tracking(player)) {
			if (other != player && ServerPlayNetworking.canSend(other, GhostFormPayload.TYPE)) {
				ServerPlayNetworking.send(other, payload);
			}
		}
	}

	/** ALLOW_DAMAGE: a ghost takes nothing (but /kill and the void). */
	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		return !(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !isGhost(player);
	}

	private static void loseTrack(ServerPlayer player) {
		for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24), m -> m.getTarget() == player)) {
			mob.setTarget(null);
		}
	}

	/** Every server tick: monsters keep losing a ghost; finished ghosts stand again. */
	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		long now = now(server);
		for (Iterator<Map.Entry<UUID, Long>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Long> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null || e.getValue() <= now) {
				it.remove();
				if (player != null) {
					player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 1.4F);
				}
			} else {
				loseTrack(player);
			}
		}
	}

	/** On login: the pieces show the cooldown that is still running. */
	public static void onJoin(ServerPlayer player) {
		long left = cooldownLeft(player);
		if (left > 0) {
			for (Item piece : new Item[]{ModItems.MOURNER_HOOD, ModItems.MOURNER_ROBE, ModItems.MOURNER_LEGGINGS, ModItems.MOURNER_BOOTS}) {
				player.getCooldowns().addCooldown(piece, (int) left);
			}
		}
	}

	/** Server stopped: the end times are game times of this world (a fresh world would leave the player a ghost for hours). */
	public static void reset() {
		ACTIVE.clear();
	}

	private GhostStep() {
	}
}
