package dev.remnants.item;

import dev.remnants.entity.ThrownDecoyEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The Decoy Echo: thrown like a snowball; where it lands your ghostly copy draws the monsters (DecoyEchoEntity). */
public class DecoyEchoItem extends LoreItem {
	public DecoyEchoItem(Properties properties) {
		super(properties, 2);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F, 0.5F);
		if (!level.isClientSide) {
			ThrownDecoyEntity thrown = new ThrownDecoyEntity(level, player);
			thrown.setItem(stack);
			thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.2F, 1.0F);
			level.addFreshEntity(thrown);
		}
		player.getCooldowns().addCooldown(this, 200);
		stack.consume(1, player);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}
}
