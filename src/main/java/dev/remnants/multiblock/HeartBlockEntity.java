package dev.remnants.multiblock;

import dev.remnants.RemnantsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The heart of a 1.3 multiblock. Struck with the Ritual Hammer it checks its {@link MultiblockPattern}: missing blocks
 * show as a hologram (layer by layer, for {@link #HOLOGRAM_TICKS}), a complete structure locks - its blocks get
 * {@link Multiblocks#FORMED}, and a wave of soul light runs over them. While formed it re-checks itself every few ticks
 * (every tick while crafting): a broken block unlocks the structure, stops the craft and drops what it held.
 *
 * <p>Crafts are a server state machine (phase + ticks since it began), synced to the client, which interpolates the
 * animation. Ingredients are only used up in the last phase; the phase is never written to disk, so a chunk that unloads
 * mid-craft comes back idle with the ingredients still inside - every item exists exactly once.
 */
public abstract class HeartBlockEntity extends BlockEntity {
	public static final int HOLOGRAM_TICKS = 200;
	public static final int WAVE_TICKS = 20;
	public static final long NEVER = Long.MIN_VALUE / 4;

	protected boolean formed;
	protected int rotation;
	protected long formedAt = NEVER;
	protected long hologramAt = NEVER;
	protected int hologramRotation;
	protected int phase;
	protected long phaseStart;
	@Nullable
	protected UUID operator;
	protected String operatorName = "";
	/** Client only: the last game tick a renderer spawned particles for this heart (renderers run every frame). */
	public long clientFxTick = NEVER;

	protected HeartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public abstract MultiblockPattern pattern();

	/** Ticks the phase lasts before the next one starts (0 = it waits for something else). */
	protected abstract int phaseLength(int phase);

	protected abstract int lastPhase();

	/** Called when {@code phase} begins (0 = the craft is over). */
	protected abstract void onPhaseStart(ServerLevel level, int phase);

	protected void onPhaseTick(ServerLevel level, int phase, int ticks) {
	}

	/** Every item the heart holds, taken out of it (the heart is empty afterwards). */
	protected abstract List<ItemStack> takeAll();

	protected abstract void saveContents(CompoundTag tag, HolderLookup.Provider registries);

	protected abstract void loadContents(CompoundTag tag, HolderLookup.Provider registries);

	// ---- state ---------------------------------------------------------------------------------------

	public boolean isFormed() {
		return this.formed;
	}

	public int rotation() {
		return this.rotation;
	}

	public int phase() {
		return this.phase;
	}

	public boolean isWorking() {
		return this.phase != 0;
	}

	public long phaseStart() {
		return this.phaseStart;
	}

	public long formedAt() {
		return this.formedAt;
	}

	public long hologramAt() {
		return this.hologramAt;
	}

	public int hologramRotation() {
		return this.hologramRotation;
	}

	public String operatorName() {
		return this.operatorName;
	}

	@Nullable
	public UUID operator() {
		return this.operator;
	}

	// ---- ticking -------------------------------------------------------------------------------------

	public static void serverTick(Level level, BlockPos pos, BlockState state, HeartBlockEntity heart) {
		if (level instanceof ServerLevel server) {
			heart.tick(server);
		}
	}

	protected void tick(ServerLevel level) {
		if (this.formed && (this.phase != 0 || level.getGameTime() % 4 == 0)
			&& !this.pattern().missingParts(level, this.worldPosition, this.rotation).isEmpty()) {
			this.disassemble(level);
			return;
		}
		if (this.phase != 0) {
			int ticks = (int) (level.getGameTime() - this.phaseStart);
			this.onPhaseTick(level, this.phase, ticks);
			// onPhaseTick may itself have moved on (a cleared wave): time the phase that is running now
			int length = this.phaseLength(this.phase);
			if (this.phase != 0 && length > 0 && level.getGameTime() - this.phaseStart >= length) {
				this.advance(level);
			}
		}
	}

	protected void advance(ServerLevel level) {
		this.setPhase(level, this.phase >= this.lastPhase() ? 0 : this.phase + 1);
	}

	protected void setPhase(ServerLevel level, int phase) {
		this.phase = phase;
		this.phaseStart = level.getGameTime();
		this.onPhaseStart(level, phase);
		if (this.phase == 0) {
			this.operator = null;
		}
		this.sync();
	}

	/** Test hook: start {@code phase} right now (game tests drive the forge's rhythm without waiting). */
	public void jumpTo(ServerLevel level, int phase) {
		this.setPhase(level, phase);
	}

	/** Runs the current craft to its end right now (game tests). */
	public void finishNow(ServerLevel level) {
		for (int guard = 0; this.phase != 0 && guard < 16; guard++) {
			this.advance(level);
		}
	}

	// ---- assembly ------------------------------------------------------------------------------------

	/** The Ritual Hammer struck this structure; hearts with no rite of their own just lock (1.4: the Belfry). */
	public void onHammer(ServerLevel level, ServerPlayer player, ItemStack hammer) {
		if (!this.formed) {
			this.tryAssemble(level, player);
		}
	}

	protected int preferredRotation() {
		BlockState state = this.getBlockState();
		return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
			? MultiblockPattern.rotationOf(state.getValue(BlockStateProperties.HORIZONTAL_FACING)) : 0;
	}

	/** The Ritual Hammer struck an unformed heart: lock the structure, or show what is missing. */
	public boolean tryAssemble(ServerLevel level, @Nullable Player player) {
		BlockPos pos = this.worldPosition;
		int preferred = this.preferredRotation();
		int rot = this.pattern().match(level, pos, preferred);
		if (rot < 0) {
			this.hologramRotation = this.pattern().closest(level, pos, preferred);
			this.hologramAt = level.getGameTime();
			int missing = this.pattern().missingParts(level, pos, this.hologramRotation).size();
			if (player != null) {
				player.displayClientMessage(Component.translatable("message.remnants.multiblock.missing", missing).withStyle(ChatFormatting.GOLD), true);
			}
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.6F);
			this.sync();
			return false;
		}
		this.form(level, rot, player);
		return true;
	}

	public void form(ServerLevel level, int rot, @Nullable Player player) {
		BlockPos pos = this.worldPosition;
		this.formed = true;
		this.rotation = rot;
		this.formedAt = level.getGameTime();
		this.hologramAt = NEVER;
		for (MultiblockPattern.Part part : this.pattern().parts()) {
			BlockPos p = this.pattern().at(pos, part, rot);
			BlockState s = level.getBlockState(p);
			BlockState n = s;
			if (n.hasProperty(Multiblocks.FORMED)) {
				n = n.setValue(Multiblocks.FORMED, true);
			}
			if (part.facing() != null && n.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
				n = n.setValue(BlockStateProperties.HORIZONTAL_FACING, MultiblockPattern.rotate(part.facing(), rot));
			}
			if (n != s) {
				level.setBlock(p, n, Block.UPDATE_CLIENTS);
			}
		}
		BlockState heart = this.getBlockState();
		BlockState n = heart.hasProperty(Multiblocks.FORMED) ? heart.setValue(Multiblocks.FORMED, true) : heart;
		if (n.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			n = n.setValue(BlockStateProperties.HORIZONTAL_FACING, MultiblockPattern.rotate(Direction.NORTH, rot));
		}
		if (n != heart) {
			level.setBlock(pos, n, Block.UPDATE_CLIENTS);
		}
		level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.3F);
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5F, 0.7F);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 1.2, 0.3, 1.2, 0.02);
		level.sendParticles(dev.remnants.registry.ModParticles.RUNE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 16, 1.4, 0.4, 1.4, 0.02);
		level.sendParticles(dev.remnants.registry.ModParticles.TWINKLE, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, 6, 1.0, 0.5, 1.0, 0.0);
		level.sendParticles(dev.remnants.registry.ModParticles.SOUL_BURST, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
		if (player instanceof ServerPlayer sp) {
			sp.displayClientMessage(Component.translatable("message.remnants.multiblock.formed").withStyle(ChatFormatting.AQUA), true);
			award(sp, "first_structure");
		}
		this.sync();
	}

	/** A block of the structure is gone (or the heart itself): unlock, stop any craft, drop what the heart held. */
	public void disassemble(ServerLevel level) {
		if (!this.formed) {
			return;
		}
		this.formed = false;
		this.dropContents(level);
		if (this.phase != 0) {
			this.phase = 0;
			this.operator = null;
		}
		this.unformParts(level);
		BlockState heart = level.getBlockState(this.worldPosition);
		if (heart.is(this.getBlockState().getBlock()) && heart.hasProperty(Multiblocks.FORMED) && heart.getValue(Multiblocks.FORMED)) {
			level.setBlock(this.worldPosition, heart.setValue(Multiblocks.FORMED, false), Block.UPDATE_CLIENTS);
		}
		level.playSound(null, this.worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
		this.sync();
	}

	protected void unformParts(ServerLevel level) {
		for (MultiblockPattern.Part part : this.pattern().parts()) {
			BlockPos p = this.pattern().at(this.worldPosition, part, this.rotation);
			BlockState s = level.getBlockState(p);
			if (s.hasProperty(Multiblocks.FORMED) && s.getValue(Multiblocks.FORMED)) {
				level.setBlock(p, s.setValue(Multiblocks.FORMED, false), Block.UPDATE_CLIENTS);
			}
		}
	}

	/** The heart block is being removed: its contents fall out and the rest of the structure goes dark. */
	public void onHeartRemoved(ServerLevel level) {
		this.dropContents(level);
		if (this.formed) {
			this.formed = false;
			this.unformParts(level);
		}
	}

	protected void dropContents(ServerLevel level) {
		BlockPos pos = this.worldPosition;
		for (ItemStack stack : this.takeAll()) {
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
		}
		this.setChanged();
	}

	/** Shift + right-click while crafting: stop, and hand everything back (the inventory, or dropped at the player). */
	public boolean cancel(ServerLevel level, @Nullable Player player) {
		if (this.phase == 0) {
			return false;
		}
		if (player != null && this.operator != null && !this.operator.equals(player.getUUID()) && !player.getAbilities().instabuild) {
			player.displayClientMessage(Component.translatable("message.remnants.crucible.busy", this.operatorName).withStyle(ChatFormatting.GRAY), true);
			return false;   // one smith per forge: only whoever started it may stop it
		}
		this.phase = 0;
		this.operator = null;
		for (ItemStack stack : this.takeAll()) {
			give(level, player, this.worldPosition, stack);
		}
		level.playSound(null, this.worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.4F);
		if (player != null) {
			player.displayClientMessage(Component.translatable("message.remnants.multiblock.cancelled").withStyle(ChatFormatting.GRAY), true);
		}
		this.sync();
		return true;
	}

	protected static void give(ServerLevel level, @Nullable Player player, BlockPos at, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (player == null || !player.getInventory().add(stack)) {
			if (player != null) {
				player.drop(stack, false);
			} else {
				Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, stack);
			}
		}
	}

	public static void award(ServerPlayer player, String advancement) {
		AdvancementHolder holder = player.server.getAdvancements().get(RemnantsMod.id("main/" + advancement));
		if (holder != null) {
			player.getAdvancements().award(holder, "done");
		}
	}

	public void sync() {
		this.setChanged();
		if (this.level != null && !this.level.isClientSide) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	// ---- saving and syncing ------------------------------------------------------------------------------

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putBoolean("Formed", this.formed);
		tag.putInt("Rotation", this.rotation);
		this.saveContents(tag, registries);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		this.formed = tag.getBoolean("Formed");
		this.rotation = tag.getInt("Rotation");
		// the craft state only travels in update packets: from disk a heart always comes back idle
		this.phase = tag.getInt("Phase");
		this.phaseStart = tag.getLong("PhaseStart");
		this.formedAt = tag.contains("FormedAt") ? tag.getLong("FormedAt") : NEVER;
		this.hologramAt = tag.contains("HologramAt") ? tag.getLong("HologramAt") : NEVER;
		this.hologramRotation = tag.getInt("HologramRotation");
		this.operatorName = tag.getString("Operator");
		this.loadContents(tag, registries);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("Formed", this.formed);
		tag.putInt("Rotation", this.rotation);
		tag.putInt("Phase", this.phase);
		tag.putLong("PhaseStart", this.phaseStart);
		tag.putLong("FormedAt", this.formedAt);
		tag.putLong("HologramAt", this.hologramAt);
		tag.putInt("HologramRotation", this.hologramRotation);
		tag.putString("Operator", this.operatorName);
		this.saveContents(tag, registries);
		return tag;
	}

	@Nullable
	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
