package dev.remnants.block.entity;

import dev.remnants.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The swing of a Bell of Rest (the bell body is drawn by BellOfRestRenderer, like the vanilla bell): a block event
 * starts it on every client - a full ring (50 ticks, the vanilla bell's swing) or the soft hum between the waves of a
 * Night of Memory (30 ticks, a third of the angle). {@code night} keeps the soul glow inside the bell lit while a night
 * is under way.
 */
public class BellOfRestBlockEntity extends BlockEntity {
	public static final int RING = 1, HUM = 2;
	public static final int EVENT_SWING = 1, EVENT_NIGHT = 2;
	public int ticks;
	public boolean shaking;
	public int strength = RING;
	public Direction clickDirection = Direction.NORTH;
	public boolean night;

	public BellOfRestBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BELL_OF_REST, pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, BellOfRestBlockEntity bell) {
		if (bell.shaking) {
			bell.ticks++;
			if (bell.ticks >= bell.duration()) {
				bell.shaking = false;
				bell.ticks = 0;
			}
		}
	}

	public int duration() {
		return this.strength == HUM ? 30 : 50;
	}

	/** Swings the bell on every client ({@code strength}: {@link #RING} or {@link #HUM}). */
	public void swing(Direction from, int strength) {
		if (this.level != null && !this.level.isClientSide) {
			this.level.blockEvent(this.worldPosition, this.getBlockState().getBlock(), EVENT_SWING, from.get3DDataValue() | (strength << 3));
		}
	}

	public void setNight(boolean night) {
		if (this.level != null && !this.level.isClientSide) {
			this.level.blockEvent(this.worldPosition, this.getBlockState().getBlock(), EVENT_NIGHT, night ? 1 : 0);
		}
	}

	@Override
	public boolean triggerEvent(int id, int type) {
		if (id == EVENT_SWING) {
			this.clickDirection = Direction.from3DDataValue(type & 7);
			this.strength = Math.max(RING, type >> 3);
			this.ticks = 0;
			this.shaking = true;
			return true;
		}
		if (id == EVENT_NIGHT) {
			this.night = type != 0;
			return true;
		}
		return super.triggerEvent(id, type);
	}
}
