package dev.remnants.item;

import dev.remnants.registry.ModComponents;
import dev.remnants.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * An Epitaph: a stone tablet that remembers one death. Your own Remnants carry the epitaph of the death that raised
 * them and leave it when laid to rest; Forgotten Graves hold the epitaphs of adventurers nobody remembers. The tooltip
 * is drawn as the tablet itself (client: EpitaphTooltipComponent).
 */
public class EpitaphItem extends Item {
	private static final String[] NAMES = {"Aldric", "Mira", "Tomas", "Elowen", "Bram", "Isolde", "Corwin", "Nessa", "Hadrik",
		"Liesl", "Osric", "Wren", "Garrick", "Sable", "Edda", "Rowan", "Ylva", "Fenwick", "Maud", "Torvald", "Agnes", "Cedric",
		"Ingrid", "Lothar"};
	public static final int UNKNOWN_CAUSES = 8;

	public EpitaphItem(Properties properties) {
		super(properties);
	}

	/** The epitaph of a player's death; {@code rank} is the echo it left (0 = no Remnant rose). */
	public static EpitaphData death(ServerPlayer player, DamageSource source, BlockPos pos, int levels, int rank) {
		ServerLevel level = player.serverLevel();
		return new EpitaphData(player.getGameProfile().getName(), plain(source.getLocalizedDeathMessage(player)),
			level.dimension().location().toString(), pos.immutable(), levels, rank, level.getDayTime() / 24000L + 1, false);
	}

	public static ItemStack stack(EpitaphData data) {
		ItemStack stack = new ItemStack(ModItems.EPITAPH);
		stack.set(ModComponents.EPITAPH, data);
		return stack;
	}

	/** Someone from long ago, buried at {@code pos}. */
	public static ItemStack unknown(RandomSource random, BlockPos pos, String dimension) {
		ItemStack stack = new ItemStack(ModItems.EPITAPH);
		Component cause = Component.translatable("epitaph.remnants.unknown.cause." + random.nextInt(UNKNOWN_CAUSES));
		stack.set(ModComponents.EPITAPH, new EpitaphData(NAMES[random.nextInt(NAMES.length)], cause, dimension, pos.immutable(),
			random.nextInt(31), 0, 0L, true));
		return stack;
	}

	private static final String[] HEROES = {"Aldric Bellwarden", "Sister Maud", "Old Torvald", "Edda of the Tower", "Brother Corwin",
		"Ylva the Ringer", "Hadrik Lanternbearer", "Liesl Ashgrave"};
	public static final int HERO_CAUSES = 3;

	/** The reward of a Night of Memory: someone who once held a bell against the night, remembered again. */
	public static ItemStack hero(RandomSource random, BlockPos pos, String dimension, long day) {
		ItemStack stack = new ItemStack(ModItems.EPITAPH);
		Component cause = Component.translatable("epitaph.remnants.hero.cause." + random.nextInt(HERO_CAUSES));
		stack.set(ModComponents.EPITAPH, new EpitaphData(HEROES[random.nextInt(HEROES.length)], cause, dimension, pos.immutable(),
			20 + random.nextInt(21), 3, day, true));
		return stack;
	}

	/** Death messages carry hover / click events on every name in them; the tablet only needs the words. */
	private static Component plain(Component message) {
		if (message.getContents() instanceof TranslatableContents tc) {
			Object[] args = new Object[tc.getArgs().length];
			for (int i = 0; i < args.length; i++) {
				args[i] = tc.getArgs()[i] instanceof Component c ? c.copy().setStyle(Style.EMPTY) : tc.getArgs()[i];
			}
			return Component.translatable(tc.getKey(), args);
		}
		return message.copy().setStyle(Style.EMPTY);
	}

	/** Right-click: the epitaph is inscribed into your Chronicle (the tablet is used up). */
	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		EpitaphData data = stack.get(ModComponents.EPITAPH);
		if (data == null) {
			return InteractionResultHolder.pass(stack);
		}
		if (player instanceof ServerPlayer sp) {
			dev.remnants.Chronicle.record(sp, data, dev.remnants.Chronicle.INSCRIBED);
			stack.consume(1, player);
			sp.displayClientMessage(Component.translatable("message.remnants.chronicle.inscribed", data.name()).withStyle(ChatFormatting.AQUA), true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.7F);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		if (stack.has(ModComponents.EPITAPH)) {
			tooltip.add(Component.translatable("item.remnants_of_the_past.epitaph.tooltip").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	@Override
	public Component getName(ItemStack stack) {
		EpitaphData data = stack.get(ModComponents.EPITAPH);
		return data == null ? super.getName(stack) : Component.translatable("item.remnants_of_the_past.epitaph.of", data.name());
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		EpitaphData data = stack.get(ModComponents.EPITAPH);
		return data == null ? Optional.empty() : Optional.of(new Tablet(data));
	}

	/** The tooltip image: the tablet with the engraved text. */
	public record Tablet(EpitaphData data) implements TooltipComponent {
	}
}
