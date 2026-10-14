package dev.remnants.client.craft;

import dev.remnants.block.entity.ReflectingPoolBlockEntity;
import dev.remnants.item.RitualHammerItem;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.net.PoolSelectPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The mouse wheel picks the Reflecting Pool's enchantment while you hold the Ritual Hammer and look at the pool: the
 * hotbar scroll is caught at the start of the next client tick (before the slot change is sent to the server), undone,
 * and sent as a selection step instead. No mixin needed.
 */
public final class PoolWheel {
	private static int lastSlot = -1;

	public static void onStartTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.screen != null) {
			lastSlot = -1;
			return;
		}
		int slot = player.getInventory().selected;
		if (lastSlot >= 0 && slot != lastSlot && player.getInventory().getItem(lastSlot).getItem() instanceof RitualHammerItem) {
			ReflectingPoolBlockEntity pool = lookedAt(mc);
			if (pool != null && pool.isFormed() && !pool.isWorking() && pool.options().size() > 1) {
				int d = Math.floorMod(slot - lastSlot + 4, 9) - 4;   // the short way round the hotbar: wheel down = +1
				player.getInventory().selected = lastSlot;
				ClientPlayNetworking.send(new PoolSelectPayload(pool.getBlockPos(), Integer.signum(d)));
				slot = lastSlot;
			}
		}
		lastSlot = slot;
	}

	@Nullable
	private static ReflectingPoolBlockEntity lookedAt(Minecraft mc) {
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		HeartBlockEntity heart = Multiblocks.findHeart(mc.level, hit.getBlockPos());
		return heart instanceof ReflectingPoolBlockEntity pool ? pool : null;
	}

	private PoolWheel() {
	}
}
