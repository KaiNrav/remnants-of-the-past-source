package dev.remnants.item;

import dev.remnants.RemnantsConfig;
import dev.remnants.entity.SoulTetherEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Soul Tether: a chain of soul with a mimic's tongue for a hook (see {@link SoulTetherEntity}). Right-click fires
 * it; right-click again while it is out lets go. Each shot costs a point of durability; cooldown {@code tetherCooldown}.
 */
public class SoulTetherItem extends LoreItem {
	private static final Map<UUID, SoulTetherEntity> ACTIVE = new HashMap<>();

	public SoulTetherItem(Properties properties) {
		super(properties, 2);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel server) {
			SoulTetherEntity out = ACTIVE.get(player.getUUID());
			if (out != null && out.isAlive()) {
				out.discard();
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8F, 1.3F);
				return InteractionResultHolder.success(stack);
			}
			ACTIVE.put(player.getUUID(), SoulTetherEntity.fire(server, player));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
			player.getCooldowns().addCooldown(this, dev.remnants.PathBranch.tetherCooldown(player));
			stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
		}
		player.swing(hand);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	/** The tether of {@code owner} is gone (it came back, let go, or was unloaded). */
	public static void forget(UUID owner, SoulTetherEntity tether) {
		ACTIVE.remove(owner, tether);
	}

	/** Server stopped: drop the references to this world's tethers. */
	public static void reset() {
		ACTIVE.clear();
	}

	@Override
	public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
		return repair.is(Items.CHAIN);
	}
}
