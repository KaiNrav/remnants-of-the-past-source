package dev.remnants.block.entity;

import dev.remnants.RemnantsConfig;
import dev.remnants.crafting.CrucibleRecipes;
import dev.remnants.crafting.Stacks;
import dev.remnants.item.ForgeQuality;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.multiblock.MultiblockPattern;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Soul Crucible: forging with a rhythm. Lay the piece on the anvil, fill the pot, strike the crucible with the Ritual
 * Hammer, then strike in time as a glowing mark runs over the anvil. One smith per forge.
 *
 * <p>MELT 25 ticks - the souls melt in the pot; POUR 25 - the pot tips and the melt runs down the trough onto the anvil;
 * FORGE 8 + 5 x 14 - five beats, the mark crosses the anvil once per beat and the right moment is the middle; the server
 * times every strike itself (the client decides nothing), forgiving up to {@code forgeLagTicks} of network lag: within
 * {@code forgePerfectWindow} ticks it is perfect, within {@code forgeGoodWindow} good, else a miss; QUENCH 30 - into the
 * water in a cloud of steam: here, and only here, the materials are used up and the piece becomes the result, with a
 * {@link ForgeQuality} from the strikes.
 */
public class SoulCrucibleBlockEntity extends HeartBlockEntity {
	public static final int MELT = 1, POUR = 2, FORGE = 3, QUENCH = 4;
	public static final int BEATS = 5, BEAT_TICKS = 14, LEAD_TICKS = 8;
	public static final int MISS = -1, NONE = 0, GOOD = 1, PERFECT = 2;
	private static final int[] LENGTH = {0, 25, 25, LEAD_TICKS + BEATS * BEAT_TICKS, 30};
	/** Where the parts the renderer animates stand, from the heart, for rotation 0 (see Multiblocks#crucible). */
	public static final BlockPos ANVIL = new BlockPos(1, -1, -1), CAULDRON = new BlockPos(-1, -1, -1), BELLOWS = new BlockPos(-1, -1, 0),
		TROUGH = new BlockPos(1, 0, 0);

	private ItemStack work = ItemStack.EMPTY;
	private final List<ItemStack> materials = new ArrayList<>();
	/** The work is a finished piece (it floats up out of the quench water until taken). */
	private boolean finished;
	private final int[] beats = new int[BEATS];
	private int quality = -1;

	public SoulCrucibleBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SOUL_CRUCIBLE, pos, state);
	}

	@Override
	public MultiblockPattern pattern() {
		return Multiblocks.crucible();
	}

	@Override
	protected int phaseLength(int phase) {
		return LENGTH[Math.min(phase, LENGTH.length - 1)];
	}

	@Override
	protected int lastPhase() {
		return QUENCH;
	}

	public ItemStack work() {
		return this.work;
	}

	public List<ItemStack> materials() {
		return Collections.unmodifiableList(this.materials);
	}

	public boolean finished() {
		return this.finished;
	}

	public int beat(int k) {
		return this.beats[k];
	}

	public int quality() {
		return this.quality;
	}

	/** The tick the k-th strike should land on (the mark is in the middle of the anvil). */
	public long beatTarget(int k) {
		return this.phaseStart + LEAD_TICKS + (long) k * BEAT_TICKS + BEAT_TICKS / 2;
	}

	public BlockPos partPos(BlockPos offset) {
		return this.worldPosition.offset(MultiblockPattern.rotate(offset, this.rotation));
	}

	// ---- interaction ---------------------------------------------------------------------------------

	/** Right-click with an item: a workpiece onto the anvil, materials into the pot (only what a recipe can use). */
	public void insert(ServerLevel level, Player player, InteractionHand hand, ItemStack held) {
		if (this.phase != 0) {
			this.busy(player);
			return;
		}
		if (!this.formed) {
			player.displayClientMessage(Component.translatable("message.remnants.crucible.unformed").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (this.finished && !this.work.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.remnants.crucible.take").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (this.work.isEmpty()) {
			if (CrucibleRecipes.isWorkpiece(held)) {
				this.work = held.copyWithCount(1);
				held.consume(1, player);
				level.playSound(null, this.partPos(ANVIL), SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.3F, 1.6F);
				this.sync();
			} else {
				player.displayClientMessage(Component.translatable("message.remnants.crucible.not_forgeable").withStyle(ChatFormatting.GRAY), true);
			}
			return;
		}
		int have = 0;
		for (ItemStack s : this.materials) {
			if (s.is(held.getItem())) {
				have += s.getCount();
			}
		}
		int room = CrucibleRecipes.cap(this.work, held.getItem()) - have;
		if (room <= 0) {
			player.displayClientMessage(Component.translatable("message.remnants.crucible.no_need", held.getHoverName()).withStyle(ChatFormatting.GRAY), true);
			return;
		}
		int n = Math.min(room, held.getCount());
		this.addMaterial(held.copyWithCount(n));
		held.consume(n, player);
		level.playSound(null, this.worldPosition, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.8F, 1.4F);
		this.sync();
	}

	private void addMaterial(ItemStack stack) {
		for (ItemStack s : this.materials) {
			if (ItemStack.isSameItemSameComponents(s, stack)) {
				s.grow(stack.getCount());
				return;
			}
		}
		this.materials.add(stack);
	}

	/** Test hook: lay the workpiece and the materials straight in. */
	public void load(ItemStack work, List<ItemStack> materials) {
		this.work = work;
		this.finished = false;
		this.materials.clear();
		for (ItemStack s : materials) {
			this.addMaterial(s.copy());
		}
		this.sync();
	}

	/** Empty hand: the finished piece, else the materials, else the workpiece. */
	public void takeBack(ServerLevel level, Player player) {
		if (this.phase != 0) {
			this.busy(player);
			return;
		}
		if (!this.work.isEmpty() && (this.finished || this.materials.isEmpty())) {
			Stacks.give(player, InteractionHand.MAIN_HAND, this.work);
			this.work = ItemStack.EMPTY;
			this.finished = false;
		} else if (!this.materials.isEmpty()) {
			for (ItemStack s : this.materials) {
				Stacks.give(player, InteractionHand.MAIN_HAND, s);
			}
			this.materials.clear();
		} else {
			player.displayClientMessage(Component.translatable(this.formed ? "message.remnants.crucible.hint" : "message.remnants.crucible.unformed")
				.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		level.playSound(null, this.worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
		this.sync();
	}

	/** The Ritual Hammer on any block of the forge: lock it, start the forging, or strike in time. */
	public void onHammer(ServerLevel level, ServerPlayer player, ItemStack hammer) {
		if (!this.formed) {
			this.tryAssemble(level, player);
			return;
		}
		if (this.phase == FORGE) {
			this.strike(level, player, hammer);
			return;
		}
		if (this.phase != 0) {
			this.busy(player);
			return;
		}
		if (this.work.isEmpty() || this.finished) {
			player.displayClientMessage(Component.translatable("message.remnants.crucible.hint").withStyle(ChatFormatting.GOLD), true);
			return;
		}
		if (!this.start(level, player)) {
			CrucibleRecipes.Recipe closest = CrucibleRecipes.closest(this.work, this.materials);
			if (closest == null) {
				player.displayClientMessage(Component.translatable("message.remnants.crucible.no_recipe").withStyle(ChatFormatting.RED), true);
			} else {
				MutableComponent list = Component.empty();
				List<CrucibleRecipes.Need> lacking = CrucibleRecipes.lacking(closest, this.materials);
				for (int i = 0; i < lacking.size(); i++) {
					if (i > 0) {
						list.append(", ");
					}
					list.append(lacking.get(i).count() + " × ").append(lacking.get(i).item().getDescription());
				}
				player.displayClientMessage(Component.translatable("message.remnants.crucible.lacking", list).withStyle(ChatFormatting.GOLD), true);
			}
			level.playSound(null, this.worldPosition, SoundEvents.VAULT_INSERT_ITEM_FAIL, SoundSource.BLOCKS, 0.8F, 0.8F);
		}
	}

	/** Starts forging if the pot holds exactly what a recipe for the workpiece needs. */
	public boolean start(ServerLevel level, @Nullable ServerPlayer smith) {
		if (this.phase != 0 || !this.formed || this.work.isEmpty() || this.finished || CrucibleRecipes.match(this.work, this.materials) == null) {
			return false;
		}
		this.operator = smith == null ? null : smith.getUUID();
		this.operatorName = smith == null ? "" : smith.getGameProfile().getName();
		Arrays.fill(this.beats, NONE);
		this.quality = -1;
		this.setPhase(level, MELT);
		return true;
	}

	private void busy(Player player) {
		player.displayClientMessage(Component.translatable("message.remnants.crucible.busy", this.operatorName).withStyle(ChatFormatting.GRAY), true);
	}

	public int strike(ServerLevel level, ServerPlayer player, ItemStack hammer) {
		if (this.operator != null && !this.operator.equals(player.getUUID())) {
			this.busy(player);
			return NONE;
		}
		int lag = player.connection == null ? 0 : Math.min(RemnantsConfig.get().forgeLagTicks, Math.round(player.connection.latency() / 50.0F));
		return this.strikeAt(level, player, hammer, level.getGameTime() - lag);
	}

	/** A strike landing at game time {@code when}. Each beat takes its first strike only; returns the verdict. */
	public int strikeAt(ServerLevel level, @Nullable Player player, ItemStack hammer, long when) {
		if (this.phase != FORGE) {
			return NONE;
		}
		long rel = when - this.phaseStart - LEAD_TICKS;
		int k = rel < 0 ? -1 : (int) (rel / BEAT_TICKS);
		BlockPos anvil = this.partPos(ANVIL);
		if (k < 0 || k >= BEATS || this.beats[k] != NONE) {
			level.playSound(null, anvil, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.15F, 0.5F);
			return NONE;
		}
		long delta = Math.abs(when - this.beatTarget(k));
		RemnantsConfig cfg = RemnantsConfig.get();
		int result = delta <= cfg.forgePerfectWindow ? PERFECT : delta <= cfg.forgeGoodWindow ? GOOD : MISS;
		this.beats[k] = result;
		if (player != null && !hammer.isEmpty()) {
			hammer.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
		}
		Vec3 top = Vec3.atCenterOf(anvil).add(0, 0.55, 0);
		switch (result) {
			case PERFECT -> {
				level.playSound(null, anvil, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 1.25F);
				level.playSound(null, anvil, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.4F, 1.2F + k * 0.1F);
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, top.x, top.y, top.z, 14, 0.15, 0.05, 0.15, 0.12);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y, top.z, 12, 0.2, 0.1, 0.2, 0.25);
			}
			case GOOD -> {
				level.playSound(null, anvil, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7F, 1.0F);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y, top.z, 6, 0.15, 0.05, 0.15, 0.15);
			}
			default -> {
				level.playSound(null, anvil, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4F, 0.6F);
				level.sendParticles(ParticleTypes.SMOKE, top.x, top.y, top.z, 5, 0.1, 0.05, 0.1, 0.01);
			}
		}
		this.sync();
		return result;
	}

	// ---- the forging ---------------------------------------------------------------------------------

	@Override
	protected void onPhaseStart(ServerLevel level, int phase) {
		BlockPos pos = this.worldPosition;
		switch (phase) {
			case MELT -> {
				level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 0.8F, 1.3F);
				level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.5F, 0.7F);
			}
			case POUR -> level.playSound(null, this.partPos(TROUGH), SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0F, 0.8F);
			case FORGE -> {
				ServerPlayer smith = this.smith(level);
				if (smith != null) {
					smith.displayClientMessage(Component.translatable("message.remnants.crucible.strike").withStyle(ChatFormatting.AQUA), true);
				}
			}
			case QUENCH -> {
				this.complete(level);
				BlockPos cauldron = this.partPos(CAULDRON);
				level.playSound(null, cauldron, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.8F);
				level.playSound(null, cauldron, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			case 0 -> {
				ServerPlayer smith = this.smith(level);
				if (smith != null && this.finished && this.quality >= 0) {
					smith.displayClientMessage(Component.translatable(this.quality > 0 ? "message.remnants.crucible.done" : "message.remnants.crucible.done_plain",
						this.work.getHoverName(), this.quality).withStyle(this.quality >= 15 ? ChatFormatting.GOLD : ChatFormatting.AQUA), true);
				}
			}
			default -> {
			}
		}
	}

	@Override
	protected void onPhaseTick(ServerLevel level, int phase, int ticks) {
		if (phase == MELT && ticks % 4 == 0) {
			Vec3 c = Vec3.atCenterOf(this.worldPosition);
			level.sendParticles(ParticleTypes.SOUL, c.x, c.y + 0.2, c.z, 2, 0.15, 0.05, 0.15, 0.01);
		} else if (phase == FORGE) {
			int rel = ticks - LEAD_TICKS;
			if (rel >= 0 && rel % BEAT_TICKS == 0 && rel / BEAT_TICKS < BEATS) {   // each beat begins with a soft tick
				level.playSound(null, this.partPos(ANVIL), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.5F, 1.4F);
			}
		} else if (phase == QUENCH && ticks % 3 == 0) {
			Vec3 c = Vec3.atCenterOf(this.partPos(CAULDRON));
			level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.5, c.z, 2, 0.2, 0.1, 0.2, 0.02);
		}
	}

	@Nullable
	private ServerPlayer smith(ServerLevel level) {
		return this.operator == null ? null : level.getServer().getPlayerList().getPlayer(this.operator);
	}

	/** The last phase: the materials are used up and the workpiece becomes the result. */
	private void complete(ServerLevel level) {
		CrucibleRecipes.Recipe recipe = CrucibleRecipes.match(this.work, this.materials);
		if (recipe == null) {
			this.phase = 0;
			return;
		}
		int perfect = 0, good = 0;
		for (int b : this.beats) {
			perfect += b == PERFECT ? 1 : 0;
			good += b == GOOD ? 1 : 0;
		}
		int percent = ForgeQuality.percentFor(perfect, good);
		ItemStack out = CrucibleRecipes.forge(recipe, this.work, level.registryAccess());
		ForgeQuality.apply(out, percent, perfect == BEATS ? this.operatorName : "");
		this.materials.clear();
		this.work = out;
		this.finished = true;
		this.quality = percent;
		ServerPlayer smith = this.smith(level);
		if (smith != null && perfect == BEATS) {
			award(smith, "master_smith");
		}
	}

	@Override
	protected List<ItemStack> takeAll() {
		List<ItemStack> out = new ArrayList<>();
		if (!this.work.isEmpty()) {
			out.add(this.work);
		}
		out.addAll(this.materials);
		this.work = ItemStack.EMPTY;
		this.materials.clear();
		this.finished = false;
		return out;
	}

	// ---- data ----------------------------------------------------------------------------------------

	@Override
	protected void saveContents(CompoundTag tag, HolderLookup.Provider registries) {
		if (!this.work.isEmpty()) {
			tag.put("Work", this.work.save(registries));
		}
		ListTag list = new ListTag();
		for (ItemStack s : this.materials) {
			list.add(s.save(registries));
		}
		tag.put("Materials", list);
		tag.putBoolean("Finished", this.finished);
	}

	@Override
	protected void loadContents(CompoundTag tag, HolderLookup.Provider registries) {
		this.work = tag.contains("Work") ? ItemStack.parseOptional(registries, tag.getCompound("Work")) : ItemStack.EMPTY;
		this.materials.clear();
		ListTag list = tag.getList("Materials", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			ItemStack s = ItemStack.parseOptional(registries, list.getCompound(i));
			if (!s.isEmpty()) {
				this.materials.add(s);
			}
		}
		this.finished = tag.getBoolean("Finished");
		int[] b = tag.getIntArray("Beats");
		Arrays.fill(this.beats, NONE);
		System.arraycopy(b, 0, this.beats, 0, Math.min(b.length, BEATS));
		this.quality = tag.contains("Quality") ? tag.getInt("Quality") : -1;
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		tag.putIntArray("Beats", this.beats);
		tag.putInt("Quality", this.quality);
		return tag;
	}
}
