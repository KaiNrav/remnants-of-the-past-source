package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import dev.remnants.multiblock.Multiblocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** A plain block that can be part of a 1.3 multiblock: {@code formed} lights its soul seam (chiseled soul bricks). */
public class FormableBlock extends Block {
	public static final MapCodec<FormableBlock> CODEC = simpleCodec(FormableBlock::new);

	public FormableBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(Multiblocks.FORMED, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(Multiblocks.FORMED);
	}
}
