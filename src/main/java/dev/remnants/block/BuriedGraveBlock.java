package dev.remnants.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * A Forgotten Grave: earth that looks exactly like the dirt, sand or gravel around it (same texture, same sound), two
 * blocks under the surface. Only a Soul Dowser finds it. Dig it up and a Burial Casket rises out of the hole.
 * Destroyed any other way (an explosion, say) it is just earth.
 */
public class BuriedGraveBlock extends Block {
	public static final MapCodec<BuriedGraveBlock> CODEC = simpleCodec(BuriedGraveBlock::new);
	public static final EnumProperty<Soil> SOIL = EnumProperty.create("soil", Soil.class);

	public enum Soil implements StringRepresentable {
		DIRT("dirt", SoundType.GRAVEL), SAND("sand", SoundType.SAND), RED_SAND("red_sand", SoundType.SAND), GRAVEL("gravel", SoundType.GRAVEL);

		private final String key;
		final SoundType sound;

		Soil(String key, SoundType sound) {
			this.key = key;
			this.sound = sound;
		}

		@Override
		public String getSerializedName() {
			return this.key;
		}

		/** The soil that blends into {@code ground}, or null if a grave does not belong there. */
		@Nullable
		public static Soil matching(BlockState ground) {
			if (ground.is(Blocks.SAND) || ground.is(Blocks.SUSPICIOUS_SAND)) {
				return SAND;
			} else if (ground.is(Blocks.RED_SAND)) {
				return RED_SAND;
			} else if (ground.is(Blocks.GRAVEL) || ground.is(Blocks.SUSPICIOUS_GRAVEL)) {
				return GRAVEL;
			} else if (ground.is(BlockTags.DIRT) && !ground.is(Blocks.MUD) && !ground.is(Blocks.MOSS_BLOCK)) {
				return DIRT;
			}
			return null;
		}
	}

	public BuriedGraveBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(SOIL, Soil.DIRT));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SOIL);
	}

	@Override
	protected SoundType getSoundType(BlockState state) {
		return state.getValue(SOIL).sound;
	}

	/** Called only when a player digs it (survival or creative): the casket rises into the hole. */
	@Override
	public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel server && server.getBlockState(pos).isAir()) {
			BurialCasketBlock.raise(server, pos, Direction.Plane.HORIZONTAL.getRandomDirection(server.random), state);
		}
	}
}
