package dev.remnants.item;

import dev.remnants.RemnantsConfig;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.registry.ModComponents;
import dev.remnants.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Archivist's Quill, its trophy: held in the main hand with an enchanted item in the off hand and a book in the
 * inventory, right-click writes the item's enchantments (curses left out) onto a new Enchanted Book; the item keeps its
 * own. Once per {@code quillCooldown} (a game day) per quill. Enchanted books are never copied (that would be a dupe).
 *
 * <p>Its sharpness is the durability bar: four writings, then it is blunt (it never breaks) until an anvil sharpens it
 * with a Condensed Soul (one point each).
 */
public class ArchivistQuillItem extends LoreItem {
	public ArchivistQuillItem(Properties properties) {
		super(properties, 2);
	}

	public static boolean isBlunt(ItemStack quill) {
		return quill.getDamageValue() >= quill.getMaxDamage();
	}

	/** Ticks until this quill may write again (0 = now). */
	public static long waitTicks(ItemStack quill, Level level) {
		Long at = quill.get(ModComponents.QUILL_WRITTEN);
		if (at == null) {
			return 0;
		}
		return Math.max(0, at + RemnantsConfig.get().quillCooldown - level.getGameTime());
	}

	/** The item's enchantments without curses (what the quill would write). */
	public static ItemEnchantments writable(ItemStack item) {
		ItemEnchantments.Mutable out = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		for (var e : item.getEnchantments().entrySet()) {
			Holder<Enchantment> ench = e.getKey();
			if (!ench.is(EnchantmentTags.CURSE)) {
				out.set(ench, e.getIntValue());
			}
		}
		return out.toImmutable();
	}

	public enum Result {
		WRITTEN, BLUNT, DRY, NOTHING, NO_BOOK, BOOK_COPY
	}

	/** Tries to write; shared by right-click and the game tests. */
	public static Result write(ServerPlayer player, ItemStack quill, ItemStack item) {
		if (isBlunt(quill)) {
			player.displayClientMessage(Component.translatable("message.remnants.quill.blunt").withStyle(ChatFormatting.GRAY), true);
			return Result.BLUNT;
		}
		if (item.is(Items.ENCHANTED_BOOK) || item.has(DataComponents.STORED_ENCHANTMENTS)) {
			player.displayClientMessage(Component.translatable("message.remnants.quill.book").withStyle(ChatFormatting.GRAY), true);
			return Result.BOOK_COPY;
		}
		ItemEnchantments ench = writable(item);
		if (ench.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.remnants.quill.nothing").withStyle(ChatFormatting.GRAY), true);
			return Result.NOTHING;
		}
		long wait = waitTicks(quill, player.level());
		if (wait > 0) {
			player.displayClientMessage(Component.translatable("message.remnants.quill.dry", wait / 1200 + 1).withStyle(ChatFormatting.GRAY), true);
			return Result.DRY;
		}
		Inventory inv = player.getInventory();
		int book = -1;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(Items.BOOK)) {
				book = i;
				break;
			}
		}
		if (book < 0 && !player.getAbilities().instabuild) {
			player.displayClientMessage(Component.translatable("message.remnants.quill.no_book").withStyle(ChatFormatting.GRAY), true);
			return Result.NO_BOOK;
		}
		if (book >= 0 && !player.getAbilities().instabuild) {
			inv.getItem(book).shrink(1);
		}
		ItemStack out = new ItemStack(Items.ENCHANTED_BOOK);
		out.set(DataComponents.STORED_ENCHANTMENTS, ench);
		if (!inv.add(out)) {
			player.drop(out, false);
		}
		quill.setDamageValue(quill.getDamageValue() + 1);   // set, never hurtAndBreak: a blunt quill stays a quill
		quill.set(ModComponents.QUILL_WRITTEN, player.level().getGameTime());
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.2F, 0.8F);
		level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.3F);
		level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.4, player.getZ(), 30, 0.5, 0.4, 0.5, 0.6);
		level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.2, player.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
		player.displayClientMessage(Component.translatable("message.remnants.quill.written", item.getHoverName()).withStyle(ChatFormatting.AQUA), true);
		HeartBlockEntity.award(player, "scribe");
		return Result.WRITTEN;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack quill = player.getItemInHand(hand);
		if (hand != InteractionHand.MAIN_HAND || player.getOffhandItem().isEmpty()) {
			if (!level.isClientSide) {
				player.displayClientMessage(Component.translatable("message.remnants.quill.how").withStyle(ChatFormatting.GRAY), true);
			}
			return InteractionResultHolder.pass(quill);
		}
		if (level.isClientSide) {
			return InteractionResultHolder.success(quill);
		}
		Result r = write((ServerPlayer) player, quill, player.getOffhandItem());
		player.getCooldowns().addCooldown(this, 20);
		return r == Result.WRITTEN ? InteractionResultHolder.success(quill) : InteractionResultHolder.consume(quill);
	}

	@Override
	public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
		return repair.is(ModItems.CONDENSED_SOUL);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		int left = stack.getMaxDamage() - stack.getDamageValue();
		tooltip.add(left <= 0 ? Component.translatable("item.remnants_of_the_past.archivist_quill.blunt").withStyle(ChatFormatting.DARK_RED)
			: Component.translatable("item.remnants_of_the_past.archivist_quill.sharp", left, stack.getMaxDamage()).withStyle(ChatFormatting.DARK_AQUA));
	}
}
