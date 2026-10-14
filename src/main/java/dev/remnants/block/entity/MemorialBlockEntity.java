package dev.remnants.block.entity;

import dev.remnants.LastRecord;
import dev.remnants.RemnantTracker;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.multiblock.MultiblockPattern;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The Memorial's heart, the Memorial Projector (1.5). Locked with the Ritual Hammer (whoever locks it owns it); a wall
 * of 15 tablets gives its owner the Memorial bonus on the Path of the Soul (RemnantTracker.setMemorial). Right-click
 * with an empty hand plays your last record ({@link LastRecord}) to everyone around: phase 1 lasts the
 * {@link #REPLAY_TICKS} the clients take to show it.
 */
public class MemorialBlockEntity extends HeartBlockEntity {
	public static final int REPLAY_TICKS = 20 + LastRecord.FRAMES * LastRecord.STEP + 20, FULL = 15;
	@Nullable
	private UUID owner;
	private int tablets;

	public MemorialBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MEMORIAL, pos, state);
	}

	@Override
	public MultiblockPattern pattern() {
		return Multiblocks.memorial();
	}

	@Override
	protected int phaseLength(int phase) {
		return phase == 1 ? REPLAY_TICKS : 0;
	}

	@Override
	protected int lastPhase() {
		return 1;
	}

	@Override
	protected void onPhaseStart(ServerLevel level, int phase) {
	}

	@Override
	protected List<ItemStack> takeAll() {
		return List.of();   // the tablets live in the niches
	}

	public int tablets() {
		return this.tablets;
	}

	@Nullable
	public UUID owner() {
		return this.owner;
	}

	public boolean isFull() {
		return this.formed && this.tablets >= FULL;
	}

	private String key() {
		return (this.level == null ? "?" : this.level.dimension().location().toString()) + " " + this.worldPosition.getX() + " "
			+ this.worldPosition.getY() + " " + this.worldPosition.getZ();
	}

	@Override
	public void form(ServerLevel level, int rot, @Nullable Player player) {
		if (player != null) {
			if (this.owner != null && !this.owner.equals(player.getUUID())) {
				RemnantTracker.get(level.getServer()).setMemorial(this.owner, this.key(), false);
			}
			this.owner = player.getUUID();
		}
		super.form(level, rot, player);
		this.refresh(level);
	}

	/** Counts the tablets on the wall and tells the tracker whether the owner's wall is full. */
	public void refresh(ServerLevel level) {
		int n = 0;
		if (this.formed) {
			for (MultiblockPattern.Part part : this.pattern().parts()) {
				if (level.getBlockEntity(this.pattern().at(this.worldPosition, part, this.rotation)) instanceof EpitaphNicheBlockEntity niche
					&& niche.epitaph() != null) {
					n++;
				}
			}
		}
		boolean was = this.isFull();
		this.tablets = n;
		if (this.owner != null) {
			RemnantTracker.get(level.getServer()).setMemorial(this.owner, this.key(), this.isFull());
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(this.owner);
			if (p != null) {
				if (this.isFull() && !was) {
					award(p, "eternal_memory");
					p.sendSystemMessage(Component.translatable("message.remnants.memorial.full",
						Math.round(dev.remnants.RemnantsConfig.get().memorialResonanceBonus * 100)).withStyle(ChatFormatting.LIGHT_PURPLE));
					level.playSound(null, this.worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 0.8F);
				}
				dev.remnants.SoulResonance.sync(p);
			}
		}
		this.sync();
	}

	@Override
	public void disassemble(ServerLevel level) {
		super.disassemble(level);
		this.refresh(level);
	}

	@Override
	public void onHeartRemoved(ServerLevel level) {
		super.onHeartRemoved(level);
		if (this.owner != null) {
			RemnantTracker.get(level.getServer()).setMemorial(this.owner, this.key(), false);
		}
	}

	/** Right-click: play the clicking player's last record. */
	public void play(ServerLevel level, ServerPlayer player) {
		if (!this.formed) {
			this.tryAssemble(level, null);
			player.displayClientMessage(Component.translatable("message.remnants.memorial.unformed").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (this.phase != 0) {
			player.displayClientMessage(Component.translatable("message.remnants.memorial.busy").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (!LastRecord.play(level, player.getUUID(), this.worldPosition)) {
			player.displayClientMessage(Component.translatable("message.remnants.memorial.no_record").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		this.operator = player.getUUID();
		this.operatorName = player.getGameProfile().getName();
		this.setPhase(level, 1);
		level.playSound(null, this.worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.6F);
		level.playSound(null, this.worldPosition, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.5F, 0.6F);
		level.sendParticles(ParticleTypes.SOUL, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.0, this.worldPosition.getZ() + 0.5,
			20, 0.3, 0.1, 0.3, 0.02);
		award(player, "last_record");
	}

	@Override
	protected void saveContents(CompoundTag tag, HolderLookup.Provider registries) {
		if (this.owner != null) {
			tag.putUUID("Owner", this.owner);
		}
		tag.putInt("Tablets", this.tablets);
	}

	@Override
	protected void loadContents(CompoundTag tag, HolderLookup.Provider registries) {
		this.owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
		this.tablets = tag.getInt("Tablets");
	}
}
