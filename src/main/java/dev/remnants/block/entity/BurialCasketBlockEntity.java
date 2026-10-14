package dev.remnants.block.entity;

import dev.remnants.RemnantsConfig;
import dev.remnants.RemnantsMod;
import dev.remnants.block.BurialCasketBlock;
import dev.remnants.entity.RemnantEntity;
import dev.remnants.item.EpitaphItem;
import dev.remnants.registry.ModBlockEntities;
import dev.remnants.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A casket's memory: whether it still holds a dead adventurer's belongings ({@link #sealed}, rolled from the
 * {@code remnants_of_the_past:graves/forgotten_grave} loot table when it is first opened) and when it began to rise (synced, so
 * the client can animate the climb out of the ground).
 */
public class BurialCasketBlockEntity extends BlockEntity {
	public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, RemnantsMod.id("graves/forgotten_grave"));
	/** How many caskets have spilled their loot since the server started (game tests check it happens exactly once). */
	public static final java.util.concurrent.atomic.AtomicInteger SPILLS = new java.util.concurrent.atomic.AtomicInteger();
	private boolean sealed;
	private long riseStart = Long.MIN_VALUE;

	public BurialCasketBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BURIAL_CASKET, pos, state);
	}

	public boolean isSealed() {
		return this.sealed;
	}

	/** Game time the rise started (client animation). */
	public long riseStart() {
		return this.riseStart;
	}

	public void seal(long gameTime) {
		this.sealed = true;
		this.riseStart = gameTime;
		this.setChanged();
		if (this.level != null) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Right-click: the first time it gives up its dead (and maybe wakes them); afterwards the lid opens and closes. */
	public void use(ServerLevel level, ServerPlayer player) {
		BlockPos pos = this.worldPosition;
		BlockState state = this.getBlockState();
		if (!this.sealed) {
			boolean open = !state.getValue(BurialCasketBlock.OPEN);
			level.setBlock(pos, state.setValue(BurialCasketBlock.OPEN, open), Block.UPDATE_ALL);
			level.playSound(null, pos, open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.7F, 0.6F);
			return;
		}
		level.setBlock(pos, state.setValue(BurialCasketBlock.OPEN, true), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0F, 0.5F);
		this.disturb(level, player);
	}

	/**
	 * A player gives up a sealed casket's dead - by opening it, or by breaking it (BurialCasketBlock#playerWillDestroy,
	 * before the block is gone): the belongings spill once, Grave Robber is earned, and the owner may climb out.
	 */
	public void disturb(ServerLevel level, ServerPlayer player) {
		if (!this.sealed) {
			return;
		}
		BlockPos pos = this.worldPosition;
		level.playSound(null, pos, SoundEvents.BRUSH_GRAVEL_COMPLETED, SoundSource.BLOCKS, 1.0F, 0.7F);
		level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.03);
		level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 10, 0.3, 0.1, 0.3, 0.01);
		this.spill(level, player);
		AdvancementHolder robbed = level.getServer().getAdvancements().get(RemnantsMod.id("main/grave_robber"));
		if (robbed != null) {
			player.getAdvancements().award(robbed, "opened");
		}
		if (level.random.nextDouble() < RemnantsConfig.get().graveOwnerChance) {
			raiseOwner(level, pos, player);
		}
	}

	/**
	 * Throws out what the casket held (once): the loot table, plus, sometimes, the epitaph of whoever lay here.
	 * {@code opener} may be null (the casket was broken or blown up).
	 */
	public void spill(ServerLevel level, @Nullable ServerPlayer opener) {
		if (!this.sealed) {
			return;
		}
		this.sealed = false;
		SPILLS.incrementAndGet();
		this.setChanged();
		BlockPos pos = this.worldPosition;
		LootParams.Builder params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos));
		if (opener != null) {
			params.withParameter(LootContextParams.THIS_ENTITY, opener).withLuck(opener.getLuck());
		}
		List<ItemStack> loot = new ArrayList<>(level.getServer().reloadableRegistries().getLootTable(LOOT)
			.getRandomItems(params.create(LootContextParamSets.CHEST)));
		if (RemnantsConfig.get().epitaphs && level.random.nextDouble() < RemnantsConfig.get().graveEpitaphChance) {
			loot.add(EpitaphItem.unknown(level.random, pos, level.dimension().location().toString()));
		}
		for (ItemStack stack : loot) {
			ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5, stack,
				Mth.nextDouble(level.random, -0.12, 0.12), Mth.nextDouble(level.random, 0.25, 0.38), Mth.nextDouble(level.random, -0.12, 0.12));
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
	}

	/** The one who lay here climbs out of the earth next to the hole, angry at being disturbed. */
	private static void raiseOwner(ServerLevel level, BlockPos casket, ServerPlayer player) {
		RemnantEntity owner = ModEntities.FORGOTTEN_REMNANT.create(level);
		if (owner == null) {
			return;
		}
		BlockPos spot = null;
		search:
		for (int r = 1; r <= 3; r++) {
			for (int dy : new int[]{1, 0, 2, -1}) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
							continue;
						}
						BlockPos p = casket.offset(dx, dy, dz);
						if (level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
							&& level.noCollision(owner.getType().getSpawnAABB(p.getX() + 0.5, p.getY(), p.getZ() + 0.5))) {
							spot = p;
							break search;
						}
					}
				}
			}
		}
		if (spot == null) {
			spot = casket.above();
		}
		float yaw = (float) (Mth.atan2(player.getZ() - spot.getZ() - 0.5, player.getX() - spot.getX() - 0.5) * Mth.RAD_TO_DEG) - 90.0F;
		owner.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0F);
		owner.setYHeadRot(yaw);
		owner.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
		owner.setTarget(player);
		level.addFreshEntity(owner);
		player.displayClientMessage(Component.translatable("message.remnants.grave.owner").withStyle(ChatFormatting.RED), true);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putBoolean("Sealed", this.sealed);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		this.sealed = tag.getBoolean("Sealed");
		if (tag.contains("RiseStart")) {
			this.riseStart = tag.getLong("RiseStart");
		}
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("Sealed", this.sealed);
		tag.putLong("RiseStart", this.riseStart);
		return tag;
	}

	@Nullable
	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
