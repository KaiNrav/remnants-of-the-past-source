package dev.remnants.item;

import dev.remnants.PathBranch;
import dev.remnants.RemnantsConfig;
import dev.remnants.registry.ModBlocks;
import dev.remnants.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Shroud Glider: hold right-click while falling and the shroud catches the air - you sink no faster than
 * {@link #SINK} blocks a tick and drift the way you look, up to {@link #CRUISE} (diving steeply trades height for up to
 * {@link #DIVE}); no fall damage while it is open. Rising air over a lit campfire, a soul campfire, a soul brazier or a
 * Soul Hearth (up to {@code gliderUpdraftHeight} blocks below) carries you up.
 *
 * <p>Players steer their own movement, so the flight itself runs on the client (onUseTick ticks on both sides); the
 * server forgives the fall, wears the shroud (1 a second) and, in an updraft, gives a breath of Levitation so a dedicated
 * server's anti-flight check knows the climb is honest.
 */
public class ShroudGliderItem extends LoreItem {
	public static final double SINK = 0.1, CRUISE = 0.55, DIVE = 0.9, LIFT = 0.25, ACCEL = 0.05;

	public ShroudGliderItem(Properties properties) {
		super(properties, 2);
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 72000;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.NONE;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.onGround() || player.isInWater() || player.isFallFlying() || player.getAbilities().flying) {
			return InteractionResultHolder.pass(stack);
		}
		player.startUsingItem(hand);
		return InteractionResultHolder.consume(stack);
	}

	/** Whether {@code entity} is gliding right now (holding an open glider in the air). */
	public static boolean gliding(LivingEntity entity) {
		return entity.isUsingItem() && entity.getUseItem().getItem() instanceof ShroudGliderItem && !entity.onGround()
			&& !entity.isInWater() && !entity.isFallFlying();
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
		if (!(entity instanceof Player player) || player.getAbilities().flying) {
			return;
		}
		if (player.onGround() || player.isInWater() || player.isFallFlying()) {
			player.stopUsingItem();
			return;
		}
		boolean wanderer = !level.isClientSide && PathBranch.is(player, PathBranch.Branch.WANDERER);
		boolean updraft = updraft(level, player.blockPosition());
		if (level.isClientSide) {
			fly(player, updraft, CLIENT_BOOST.getAsDouble());
			if (player.tickCount % 3 == 0) {
				level.addParticle(updraft ? ParticleTypes.SOUL : ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 2.2, player.getZ(), 0, -0.02, 0);
			}
			return;
		}
		player.resetFallDistance();
		if (updraft) {
			player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 6, wanderer ? 1 : 0, true, false, false));
		}
		int used = this.getUseDuration(stack, entity) - remaining;
		if (used > 0 && used % 20 == 0 && player instanceof ServerPlayer sp && !sp.getAbilities().instabuild) {
			stack.hurtAndBreak(1, sp, LivingEntity.getSlotForHand(sp.getUsedItemHand()));
		}
	}

	/** The local player's speed factor, set by the client from the synced branch (1.25 for a Wanderer). */
	public static java.util.function.DoubleSupplier CLIENT_BOOST = () -> 1.0;

	/** One tick of gliding: cap the sink, drift toward the look, rise in an updraft. */
	public static void fly(Player player, boolean updraft, double boost) {
		Vec3 v = player.getDeltaMovement();
		float pitch = player.getXRot();
		Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
		double dive = Mth.clamp((pitch - 25.0F) / 45.0F, 0.0, 1.0);
		double max = (CRUISE + (DIVE - CRUISE) * dive) * boost;
		Vec3 h = new Vec3(v.x, 0, v.z);
		Vec3 want = look.scale(max);
		h = h.add(want.subtract(h).scale(ACCEL * (1.0 + dive)));
		double vy;
		if (updraft) {
			vy = Math.min(v.y + 0.06, LIFT * boost);
		} else {
			double sink = SINK + dive * 0.25;
			vy = Math.max(v.y, -sink);
		}
		player.setDeltaMovement(h.x, vy, h.z);
		player.resetFallDistance();
	}

	/** Rising air: a lit campfire, a soul campfire, a soul brazier or a Soul Hearth somewhere below in the column. */
	public static boolean updraft(BlockGetter level, BlockPos at) {
		int reach = Math.max(1, RemnantsConfig.get().gliderUpdraftHeight);
		BlockPos.MutableBlockPos p = at.mutable();
		for (int i = 0; i <= reach; i++) {
			BlockState s = level.getBlockState(p);
			if (isHeat(s)) {
				return true;
			}
			if (!s.isAir() && s.isCollisionShapeFullBlock(level, p)) {
				return false;   // a solid block blocks the warm air
			}
			p.move(0, -1, 0);
		}
		return false;
	}

	public static boolean isHeat(BlockState s) {
		return (s.is(BlockTags.CAMPFIRES) && s.getValue(CampfireBlock.LIT)) || s.is(ModBlocks.SOUL_BRAZIER) || s.is(ModBlocks.SOUL_HEARTH);
	}

	@Override
	public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
		return repair.is(ModItems.WET_SHROUD);
	}
}
