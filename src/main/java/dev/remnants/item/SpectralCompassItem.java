package dev.remnants.item;

import dev.remnants.RemnantTracker;
import dev.remnants.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/** Right-click: a soul trail points toward your nearest Remnant, with distance and direction in the action bar. */
public class SpectralCompassItem extends Item {
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	public SpectralCompassItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}
		player.getCooldowns().addCooldown(this, 30);
		RemnantTracker tracker = RemnantTracker.get(server.getServer());
		Optional<RemnantTracker.Location> here = tracker.nearestLiving(server.getServer(), player.getUUID(), level.dimension(), player.blockPosition());

		if (here.isPresent()) {
			BlockPos target = here.get().pos();
			Vec3 eye = player.getEyePosition();
			Vec3 to = Vec3.atCenterOf(target).add(0, 1, 0).subtract(eye);
			int dist = (int) Math.round(to.horizontalDistance());
			float yaw = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
			int idx = Math.floorMod(Math.round(Mth.wrapDegrees(yaw - player.getYRot()) / 45.0F), 8);

			Vec3 dir = to.normalize();
			for (int i = 1; i <= 14; i++) {
				Vec3 p = eye.add(dir.scale(0.6 * i)).add(0, -0.3, 0);
				server.sendParticles(sp, ModParticles.REMNANT_SOUL, true, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
			player.displayClientMessage(Component.translatable("message.remnants.compass.found",
				ARROWS[idx], dist, target.getY(), here.get().tier() + 1).withStyle(ChatFormatting.AQUA), true);
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.5F);
		} else if (tracker.any(player.getUUID()).isPresent()) {
			Component dim = Component.literal(tracker.any(player.getUUID()).get().dimension().location().getPath());
			player.displayClientMessage(Component.translatable("message.remnants.compass.other_dimension", dim).withStyle(ChatFormatting.DARK_AQUA), true);
		} else {
			player.displayClientMessage(Component.translatable("message.remnants.compass.none").withStyle(ChatFormatting.GRAY), true);
		}
		return InteractionResultHolder.success(stack);
	}

	/** Every second while carried: point the needle (vanilla compass "angle" property) at the nearest living Remnant. */
	@Override
	public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
		if (!(level instanceof ServerLevel server) || !(entity instanceof Player player) || level.getGameTime() % 20 != 0) {
			return;
		}
		Optional<RemnantTracker.Location> target = RemnantTracker.get(server.getServer())
			.nearestLiving(server.getServer(), player.getUUID(), level.dimension(), player.blockPosition());
		LodestoneTracker wanted = target.map(l -> new LodestoneTracker(Optional.of(GlobalPos.of(l.dimension(), l.pos())), false)).orElse(null);
		if (!java.util.Objects.equals(stack.get(DataComponents.LODESTONE_TRACKER), wanted)) {
			if (wanted == null) {
				stack.remove(DataComponents.LODESTONE_TRACKER);
			} else {
				stack.set(DataComponents.LODESTONE_TRACKER, wanted);
			}
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.remnants_of_the_past.spectral_compass.tooltip").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}
}
