package dev.remnants.item;

import dev.remnants.RemnantsConfig;
import dev.remnants.RemnantsMod;
import dev.remnants.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Warden's Gauntlet, the Crypt Warden's lesson: held in the off hand, a melee blow against someone who is not facing
 * you (more than {@link #BEHIND} degrees off) deals {@code gauntletBackstabBonus} more (+40 %). Done before the attack
 * is processed (AttackEntityCallback) with a modifier that lives for that one swing, so knockback, Sharpness and
 * Fire Aspect work as usual.
 */
public class WardensGauntletItem extends LoreItem {
	public static final ResourceLocation BACKSTAB = RemnantsMod.id("backstab");
	public static final float BEHIND = 100.0F;
	private static final Set<UUID> ARMED = new HashSet<>();

	public WardensGauntletItem(Properties properties) {
		super(properties, 2);
	}

	/** Whether {@code victim} has its back (or flank) to {@code attacker}. */
	public static boolean notFacing(LivingEntity victim, Entity attacker) {
		Vec3 look = Vec3.directionFromRotation(0, victim.getYHeadRot());
		Vec3 to = new Vec3(attacker.getX() - victim.getX(), 0, attacker.getZ() - victim.getZ());
		if (to.lengthSqr() < 1.0E-6) {
			return false;
		}
		return look.dot(to.normalize()) < Math.cos(Math.toRadians(BEHIND));
	}

	/** AttackEntityCallback (both sides; acts on the server). */
	public static InteractionResult onAttack(Player player, Level level, InteractionHand hand, Entity target, @Nullable EntityHitResult hit) {
		if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.PASS;
		}
		disarm(sp);
		if (player.getOffhandItem().is(ModItems.WARDENS_GAUNTLET) && target instanceof LivingEntity victim && notFacing(victim, player)) {
			AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
			if (damage != null) {
				damage.addOrUpdateTransientModifier(new AttributeModifier(BACKSTAB, RemnantsConfig.get().gauntletBackstabBonus,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				ARMED.add(player.getUUID());
				((ServerLevel) level).sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.7, victim.getZ(),
					8, 0.25, 0.25, 0.25, 0.2);
			}
		}
		return InteractionResult.PASS;
	}

	private static void disarm(ServerPlayer player) {
		if (ARMED.remove(player.getUUID())) {
			AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
			if (damage != null) {
				damage.removeModifier(BACKSTAB);
			}
		}
	}

	public static void reset() {
		ARMED.clear();
	}

	/** End of every server tick: the bonus only ever lasts for the swing it was given for. */
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
}
