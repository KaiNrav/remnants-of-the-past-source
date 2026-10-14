package dev.remnants;

import dev.remnants.entity.RemnantEntity;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The branches of the Path of the Soul (1.5): at rank {@code branchRank} (III) a player picks one, for free; changing it
 * later costs a Soul King's Heart. Picked from the Codex (BranchPayload).
 * <ul>
 *   <li>Reaper: +15 % melee damage to Remnants and the undead; blows on Remnants heal 15 % of the damage dealt.</li>
 *   <li>Keeper: a shield sends 25 % of what it blocks back at a melee attacker and shoves it off; a lantern ally fights
 *   twice as long, a decoy stands half as long again.</li>
 *   <li>Wanderer: the Soul Tether reaches 32 blocks and recharges twice as fast, the Shroud Glider flies faster and rides
 *   the updrafts higher, the anchor network carries you for free.</li>
 * </ul>
 */
public final class PathBranch {
	public enum Branch {
		NONE, REAPER, KEEPER, WANDERER;

		public String key() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		public static Branch of(String key) {
			for (Branch b : values()) {
				if (b.key().equals(key)) {
					return b;
				}
			}
			return NONE;
		}
	}

	public static final float REAPER_BONUS = 0.15F, REAPER_LEECH = 0.15F, KEEPER_REFLECT = 0.25F;
	public static final int WANDERER_TETHER_RANGE = 32;
	public static final float WANDERER_GLIDE = 1.25F;
	private static final ResourceLocation REAPER = RemnantsMod.id("reaper");
	private static final Set<UUID> ARMED = new HashSet<>();

	public static Branch of(ServerPlayer player) {
		if (!RemnantsConfig.get().soulResonance) {
			return Branch.NONE;
		}
		RemnantTracker tracker = RemnantTracker.get(player.server);
		if (SoulResonance.rankOf(tracker.resonance(player.getUUID()).points()) < RemnantsConfig.get().branchRank) {
			return Branch.NONE;
		}
		return Branch.of(tracker.branch(player.getUUID()));
	}

	public static boolean is(@Nullable Entity entity, Branch branch) {
		return entity instanceof ServerPlayer p && of(p) == branch;
	}

	/** What happened when a player picked a branch in the Codex. */
	public enum Result {
		CHOSEN, CHANGED, SAME, RANK, NEEDS_HEART
	}

	public static Result choose(ServerPlayer player, Branch branch) {
		if (branch == Branch.NONE) {
			return Result.SAME;
		}
		RemnantTracker tracker = RemnantTracker.get(player.server);
		int rank = SoulResonance.rankOf(tracker.resonance(player.getUUID()).points());
		if (!RemnantsConfig.get().soulResonance || rank < RemnantsConfig.get().branchRank) {
			player.displayClientMessage(Component.translatable("message.remnants.branch.rank", RemnantsConfig.get().branchRank)
				.withStyle(ChatFormatting.GRAY), true);
			return Result.RANK;
		}
		Branch had = Branch.of(tracker.branch(player.getUUID()));
		if (had == branch) {
			return Result.SAME;
		}
		if (had != Branch.NONE) {
			int slot = -1;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (player.getInventory().getItem(i).is(ModItems.SOUL_KING_HEART)) {
					slot = i;
					break;
				}
			}
			if (slot < 0 && !player.getAbilities().instabuild) {
				player.displayClientMessage(Component.translatable("message.remnants.branch.heart").withStyle(ChatFormatting.GOLD), true);
				return Result.NEEDS_HEART;
			}
			if (slot >= 0 && !player.getAbilities().instabuild) {
				player.getInventory().getItem(slot).shrink(1);
			}
		}
		tracker.setBranch(player.getUUID(), branch.key());
		Component name = Component.translatable("resonance.remnants.branch." + branch.key());
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("resonance.remnants.branch." + branch.key() + ".motto")
			.withStyle(ChatFormatting.GRAY)));
		player.connection.send(new ClientboundSetTitleTextPacket(name.copy().withStyle(ChatFormatting.LIGHT_PURPLE)));
		player.sendSystemMessage(Component.translatable(had == Branch.NONE ? "message.remnants.branch.chosen" : "message.remnants.branch.changed", name)
			.withStyle(ChatFormatting.LIGHT_PURPLE));
		player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.2F);
		player.serverLevel().sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
		HeartBlockEntity.award(player, "crossroads");
		SoulResonance.sync(player);
		return had == Branch.NONE ? Result.CHOSEN : Result.CHANGED;
	}

	/** Reaper's prey: Remnants (any) and the undead. */
	public static boolean reaperPrey(Entity e) {
		return e instanceof RemnantEntity || (e instanceof LivingEntity l && l.getType().is(EntityTypeTags.UNDEAD));
	}

	// ---- events ------------------------------------------------------------------------------------

	/** AttackEntityCallback: the Reaper's bonus, as a modifier that lives for this one swing (like the Gauntlet). */
	public static InteractionResult onAttack(Player player, Level level, InteractionHand hand, Entity target, @Nullable EntityHitResult hit) {
		if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.PASS;
		}
		disarm(sp);
		if (reaperPrey(target) && of(sp) == Branch.REAPER) {
			AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
			if (damage != null) {
				damage.addOrUpdateTransientModifier(new AttributeModifier(REAPER, REAPER_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				ARMED.add(player.getUUID());
			}
		}
		return InteractionResult.PASS;
	}

	private static void disarm(ServerPlayer player) {
		if (ARMED.remove(player.getUUID())) {
			AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
			if (damage != null) {
				damage.removeModifier(REAPER);
			}
		}
	}

	public static void endTick(MinecraftServer server) {
		if (ARMED.isEmpty()) {
			return;
		}
		for (UUID id : Set.copyOf(ARMED)) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				disarm(p);
			} else {
				ARMED.remove(id);
			}
		}
	}

	/** AFTER_DAMAGE: the Reaper's leech on Remnants, the Keeper's shield riposte. */
	public static void afterDamage(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		Entity attacker = source.getEntity();
		boolean melee = attacker != null && source.getDirectEntity() == attacker;
		if (melee && taken > 0 && victim instanceof RemnantEntity && attacker instanceof ServerPlayer p && of(p) == Branch.REAPER) {
			p.heal(taken * REAPER_LEECH);
			p.serverLevel().sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
				4, 0.2, 0.3, 0.2, 0.02);
		}
		if (blocked && melee && base > 0 && victim instanceof ServerPlayer keeper && attacker instanceof LivingEntity foe && foe != keeper
			&& of(keeper) == Branch.KEEPER) {
			ServerLevel level = keeper.serverLevel();
			foe.hurt(level.damageSources().thorns(keeper), base * KEEPER_REFLECT);
			foe.knockback(0.8, keeper.getX() - foe.getX(), keeper.getZ() - foe.getZ());
			foe.hurtMarked = true;
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, foe.getX(), foe.getY() + foe.getBbHeight() * 0.6, foe.getZ(), 6, 0.2, 0.3, 0.2, 0.03);
			level.playSound(null, keeper.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 1.4F);
		}
	}

	/** Soul Tether range for this player (Wanderer: farther). */
	public static int tetherRange(@Nullable Entity owner) {
		int base = Math.max(4, RemnantsConfig.get().tetherRange);
		return is(owner, Branch.WANDERER) ? Math.max(base, WANDERER_TETHER_RANGE) : base;
	}

	public static int tetherCooldown(@Nullable Entity owner) {
		int base = RemnantsConfig.get().tetherCooldown;
		return is(owner, Branch.WANDERER) ? base / 2 : base;
	}

	public static void reset() {
		ARMED.clear();
	}

	private PathBranch() {
	}
}
