package dev.remnants.block.entity;

import dev.remnants.crafting.EnchantTransfer;
import dev.remnants.crafting.Stacks;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.multiblock.MultiblockPattern;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.registry.ModBlockEntities;
import dev.remnants.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Reflecting Pool: an enchantment of your choice moves from one item to another, no lottery. The source floats on
 * the water (on the side you placed it from), the target rests in the basin; with the Ritual Hammer in hand, the mouse
 * wheel picks the enchantment (a flat label over the water shows it and its price) and a strike confirms.
 *
 * <p>The rite (80 ticks): RISE 20 - both items lift, rings spread over the water; RUNES 30 - a glyph peels off the source
 * for each enchantment, the chosen one brightest; FLIGHT 20 - the chosen rune arcs to the target; IMPACT 10 - it sinks in
 * (the transfer and the payment happen here, and only here), the items settle.
 */
public class ReflectingPoolBlockEntity extends HeartBlockEntity {
	public static final int RISE = 1, RUNES = 2, FLIGHT = 3, IMPACT = 4;
	private static final int[] LENGTH = {0, 20, 30, 20, 10};

	private ItemStack source = ItemStack.EMPTY;
	private ItemStack target = ItemStack.EMPTY;
	private Direction sourceSide = Direction.SOUTH;
	private int selected;
	/** Client copy of the server's verdict on the current choice: price and whether it can go through. */
	private int costLevels, costFragments;
	private String verdict = "";

	public ReflectingPoolBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.REFLECTING_POOL, pos, state);
	}

	@Override
	public MultiblockPattern pattern() {
		return Multiblocks.pool();
	}

	@Override
	protected int phaseLength(int phase) {
		return LENGTH[Math.min(phase, LENGTH.length - 1)];
	}

	@Override
	protected int lastPhase() {
		return IMPACT;
	}

	public ItemStack source() {
		return this.source;
	}

	public ItemStack target() {
		return this.target;
	}

	public Direction sourceSide() {
		return this.sourceSide;
	}

	public int selectedIndex() {
		List<Holder<Enchantment>> options = this.options();
		return options.isEmpty() ? 0 : Math.floorMod(this.selected, options.size());
	}

	public List<Holder<Enchantment>> options() {
		return EnchantTransfer.options(this.source);
	}

	@Nullable
	public Holder<Enchantment> selectedEnchantment() {
		List<Holder<Enchantment>> options = this.options();
		return options.isEmpty() ? null : options.get(this.selectedIndex());
	}

	public int costLevels() {
		return this.costLevels;
	}

	public int costFragments() {
		return this.costFragments;
	}

	public String verdict() {
		return this.verdict;
	}

	// ---- interaction ---------------------------------------------------------------------------------

	public static boolean isTarget(ItemStack stack) {
		return stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK) || stack.getItem().isEnchantable(stack)
			|| !stack.getEnchantments().isEmpty();
	}

	/** Right-click with an item: the first enchanted item floats on the water as the source, the next goes in the basin. */
	public void insert(ServerLevel level, Player player, InteractionHand hand, ItemStack held) {
		if (this.phase != 0) {
			player.displayClientMessage(Component.translatable("message.remnants.multiblock.busy").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (!this.formed) {
			player.displayClientMessage(Component.translatable("message.remnants.pool.unformed").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (this.source.isEmpty() && !EnchantTransfer.options(held).isEmpty()) {
			this.source = held.copyWithCount(1);
			this.sourceSide = this.sideToward(player);
			this.selected = 0;
		} else if (this.target.isEmpty() && isTarget(held)) {
			this.target = held.copyWithCount(1);
		} else {
			player.displayClientMessage(Component.translatable(this.source.isEmpty() || this.target.isEmpty()
				? "message.remnants.pool.cannot" : "message.remnants.pool.full").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		held.consume(1, player);
		level.playSound(null, this.worldPosition, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4F, 1.4F);
		this.sync();
	}

	/** Test hook: put both items straight in. */
	public void load(ItemStack source, ItemStack target) {
		this.source = source;
		this.target = target;
		this.selected = 0;
		this.sync();
	}

	/** Empty hand: the basin's item first, then the one on the water. */
	public void takeBack(ServerLevel level, Player player) {
		if (this.phase != 0) {
			player.displayClientMessage(Component.translatable("message.remnants.multiblock.busy").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		ItemStack out;
		if (!this.target.isEmpty()) {
			out = this.target;
			this.target = ItemStack.EMPTY;
		} else if (!this.source.isEmpty()) {
			out = this.source;
			this.source = ItemStack.EMPTY;
		} else {
			player.displayClientMessage(Component.translatable(this.formed ? "message.remnants.pool.hint" : "message.remnants.pool.unformed")
				.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		Stacks.give(player, InteractionHand.MAIN_HAND, out);
		level.playSound(null, this.worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
		this.sync();
	}

	/** The mouse wheel (with the hammer, looking at the basin) picks the next / previous enchantment. */
	public void select(int delta) {
		int n = this.options().size();
		if (this.phase != 0 || n == 0 || delta == 0) {
			return;
		}
		this.selected = Math.floorMod(this.selectedIndex() + delta, n);
		if (this.level != null) {
			this.level.playSound(null, this.worldPosition, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.6F, 1.6F);
		}
		this.sync();
	}

	/** The Ritual Hammer: lock the pool, or start the rite for the chosen enchantment. */
	public void onHammer(ServerLevel level, ServerPlayer player) {
		if (!this.formed) {
			this.tryAssemble(level, player);
			return;
		}
		if (this.phase != 0) {
			player.displayClientMessage(Component.translatable("message.remnants.multiblock.busy").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (this.source.isEmpty() || this.target.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.remnants.pool.hint").withStyle(ChatFormatting.GOLD), true);
			return;
		}
		Holder<Enchantment> e = this.selectedEnchantment();
		if (e == null) {
			return;
		}
		EnchantTransfer.Result result = EnchantTransfer.check(this.source, this.target, e);
		if (result != EnchantTransfer.Result.OK) {
			player.displayClientMessage(Component.translatable("message.remnants.pool.refused." + result.key()).withStyle(ChatFormatting.RED), true);
			level.playSound(null, this.worldPosition, SoundEvents.VAULT_INSERT_ITEM_FAIL, SoundSource.BLOCKS, 0.8F, 0.8F);
			return;
		}
		int moved = EnchantTransfer.movedLevel(this.source, e);
		int levels = EnchantTransfer.levelCost(e, moved), fragments = EnchantTransfer.fragmentCost(e, moved);
		if (!player.getAbilities().instabuild && (player.experienceLevel < levels || Stacks.count(player, ModItems.SOUL_FRAGMENT) < fragments)) {
			player.displayClientMessage(Component.translatable("message.remnants.pool.cost", levels, fragments).withStyle(ChatFormatting.RED), true);
			level.playSound(null, this.worldPosition, SoundEvents.VAULT_INSERT_ITEM_FAIL, SoundSource.BLOCKS, 0.8F, 0.8F);
			return;
		}
		this.operator = player.getUUID();
		this.operatorName = player.getGameProfile().getName();
		this.setPhase(level, RISE);
	}

	// ---- the rite ------------------------------------------------------------------------------------

	@Override
	protected void onPhaseStart(ServerLevel level, int phase) {
		BlockPos pos = this.worldPosition;
		Vec3 basin = Vec3.atCenterOf(pos).add(0, 0.75, 0);
		Vec3 water = this.sourcePoint();
		switch (phase) {
			case RISE -> {
				level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.6F);
				level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			case RUNES -> {
				level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.2F, 0.9F);
				level.sendParticles(ParticleTypes.ENCHANT, water.x, water.y + 0.6, water.z, 30, 0.3, 0.3, 0.3, 0.6);
			}
			case FLIGHT -> level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 2.0F, 1.3F);
			case IMPACT -> {
				if (this.perform(level)) {
					level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.7F, 1.9F);
					level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.4F);
					level.sendParticles(ParticleTypes.END_ROD, basin.x, basin.y + 0.4, basin.z, 18, 0.15, 0.15, 0.15, 0.08);
					level.sendParticles(ParticleTypes.ENCHANT, basin.x, basin.y + 0.4, basin.z, 40, 0.4, 0.4, 0.4, 0.5);
				}
			}
			default -> {
			}
		}
	}

	/** Where the source floats: one block out from the basin, on the water. */
	public Vec3 sourcePoint() {
		return Vec3.atCenterOf(this.worldPosition.relative(this.sourceSide)).add(0, 0.4, 0);
	}

	/** The rite's last phase: check again, pay, move the enchantment. False (and the rite stops) if anything changed. */
	private boolean perform(ServerLevel level) {
		ServerPlayer player = this.operator == null ? null : level.getServer().getPlayerList().getPlayer(this.operator);
		Holder<Enchantment> e = this.selectedEnchantment();
		boolean ok = e != null && EnchantTransfer.check(this.source, this.target, e) == EnchantTransfer.Result.OK
			&& player != null && player.level() == level && player.distanceToSqr(Vec3.atCenterOf(this.worldPosition)) < 32 * 32;
		int moved = ok ? EnchantTransfer.movedLevel(this.source, e) : 0;
		int levels = ok ? EnchantTransfer.levelCost(e, moved) : 0, fragments = ok ? EnchantTransfer.fragmentCost(e, moved) : 0;
		boolean free = player != null && player.getAbilities().instabuild;
		if (ok && !free && (player.experienceLevel < levels || Stacks.count(player, ModItems.SOUL_FRAGMENT) < fragments)) {
			ok = false;
		}
		if (!ok) {
			this.phase = 0;   // setPhase finishes the stop (the items stay where they are)
			if (player != null) {
				player.displayClientMessage(Component.translatable("message.remnants.pool.broken").withStyle(ChatFormatting.RED), true);
			}
			level.playSound(null, this.worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.2F);
			return false;
		}
		if (!free) {
			player.giveExperienceLevels(-levels);
			Stacks.take(player, ModItems.SOUL_FRAGMENT, fragments);
		}
		ItemStack[] out = EnchantTransfer.apply(this.source, this.target, e);
		this.source = out[0];
		this.target = out[1];
		this.selected = 0;
		player.displayClientMessage(Component.translatable("message.remnants.pool.done",
			Enchantment.getFullname(e, EnchantTransfer.enchantmentsOf(this.target).getLevel(e)), this.target.getHoverName())
			.withStyle(ChatFormatting.AQUA), true);
		award(player, "reflection");
		return true;
	}

	@Override
	protected List<ItemStack> takeAll() {
		List<ItemStack> out = new ArrayList<>();
		if (!this.source.isEmpty()) {
			out.add(this.source);
		}
		if (!this.target.isEmpty()) {
			out.add(this.target);
		}
		this.source = ItemStack.EMPTY;
		this.target = ItemStack.EMPTY;
		return out;
	}

	// ---- data ----------------------------------------------------------------------------------------

	private Direction sideToward(Player player) {
		double dx = player.getX() - (this.worldPosition.getX() + 0.5), dz = player.getZ() - (this.worldPosition.getZ() + 0.5);
		return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	@Override
	protected void saveContents(CompoundTag tag, HolderLookup.Provider registries) {
		if (!this.source.isEmpty()) {
			tag.put("Source", this.source.save(registries));
		}
		if (!this.target.isEmpty()) {
			tag.put("Target", this.target.save(registries));
		}
		tag.putInt("Side", this.sourceSide.get2DDataValue());
		tag.putInt("Selected", this.selected);
	}

	@Override
	protected void loadContents(CompoundTag tag, HolderLookup.Provider registries) {
		this.source = tag.contains("Source") ? ItemStack.parseOptional(registries, tag.getCompound("Source")) : ItemStack.EMPTY;
		this.target = tag.contains("Target") ? ItemStack.parseOptional(registries, tag.getCompound("Target")) : ItemStack.EMPTY;
		this.sourceSide = Direction.from2DDataValue(tag.getInt("Side"));
		this.selected = tag.getInt("Selected");
		this.costLevels = tag.getInt("CostLevels");
		this.costFragments = tag.getInt("CostFragments");
		this.verdict = tag.getString("Verdict");
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		Holder<Enchantment> e = this.selectedEnchantment();
		if (e != null) {
			int moved = EnchantTransfer.movedLevel(this.source, e);
			tag.putInt("CostLevels", EnchantTransfer.levelCost(e, moved));
			tag.putInt("CostFragments", EnchantTransfer.fragmentCost(e, moved));
			tag.putString("Verdict", this.target.isEmpty() ? "" : EnchantTransfer.check(this.source, this.target, e).key());
		}
		return tag;
	}
}
