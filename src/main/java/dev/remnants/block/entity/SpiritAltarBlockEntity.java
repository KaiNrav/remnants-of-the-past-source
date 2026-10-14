package dev.remnants.block.entity;

import dev.remnants.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds no data: it only exists so the client can draw the summoning circle's missing pedestals as ghosts. */
public class SpiritAltarBlockEntity extends BlockEntity {
	public SpiritAltarBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SPIRIT_ALTAR, pos, state);
	}
}
