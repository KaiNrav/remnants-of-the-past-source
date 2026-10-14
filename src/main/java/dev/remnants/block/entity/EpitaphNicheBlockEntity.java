package dev.remnants.block.entity;

import dev.remnants.item.EpitaphData;
import dev.remnants.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * An Epitaph Niche's tablet: the epitaph it shows, whether it was set in as an item (it comes back out as one) or chiselled
 * from the Chronicle (it is erased when taken out), and when it was set (the letters appear one by one after that).
 */
public class EpitaphNicheBlockEntity extends BlockEntity {
	@Nullable
	private EpitaphData epitaph;
	private boolean fromItem;
	private long setAt;

	public EpitaphNicheBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.EPITAPH_NICHE, pos, state);
	}

	@Nullable
	public EpitaphData epitaph() {
		return this.epitaph;
	}

	public boolean fromItem() {
		return this.fromItem;
	}

	public long setAt() {
		return this.setAt;
	}

	public void set(@Nullable EpitaphData data, boolean fromItem) {
		this.epitaph = data;
		this.fromItem = fromItem;
		this.setAt = this.level == null ? 0 : this.level.getGameTime();
		this.setChanged();
		if (this.level != null && !this.level.isClientSide) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (this.epitaph != null) {
			EpitaphData.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this.epitaph).result()
				.ifPresent(t -> tag.put("Epitaph", t));
			tag.putBoolean("FromItem", this.fromItem);
		}
		tag.putLong("SetAt", this.setAt);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		this.epitaph = tag.contains("Epitaph")
			? EpitaphData.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("Epitaph")).result().orElse(null) : null;
		this.fromItem = tag.getBoolean("FromItem");
		this.setAt = tag.getLong("SetAt");
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		this.saveAdditional(tag, registries);
		return tag;
	}

	@Nullable
	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
