package dev.remnants.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A multiblock as offsets from its heart -> what must stand there. Authored for rotation 0, the structure's front
 * facing north; rotation r turns it r quarter turns clockwise seen from above (0 north, 1 east, 2 south, 3 west), like
 * {@link Rotation}'s order. The heart itself (offset 0, 0, 0) is not a part.
 */
public final class MultiblockPattern {
	/**
	 * One block of the structure. {@code ghost} is what the hologram draws for a missing part (authored for rotation 0);
	 * {@code facing}, when set, is the direction the mod's own part is turned to when the structure locks (so players
	 * never have to place it the right way round).
	 */
	public record Part(BlockPos offset, Predicate<BlockState> test, BlockState ghost, @Nullable Direction facing) {
	}

	private final List<Part> parts;
	private final boolean symmetric;

	public MultiblockPattern(List<Part> parts, boolean symmetric) {
		this.parts = List.copyOf(parts);
		this.symmetric = symmetric;
	}

	public List<Part> parts() {
		return this.parts;
	}

	public static BlockPos rotate(BlockPos offset, int rot) {
		int x = offset.getX(), z = offset.getZ();
		for (int i = 0; i < (rot & 3); i++) {   // north (0, -1) -> east (1, 0)
			int t = x;
			x = -z;
			z = t;
		}
		return new BlockPos(x, offset.getY(), z);
	}

	public static Direction rotate(Direction dir, int rot) {
		for (int i = 0; i < (rot & 3); i++) {
			dir = dir.getClockWise();
		}
		return dir;
	}

	public static Rotation rotation(int rot) {
		return Rotation.values()[rot & 3];
	}

	/** The rotation whose front is {@code facing}. */
	public static int rotationOf(Direction facing) {
		return switch (facing) {
			case EAST -> 1;
			case SOUTH -> 2;
			case WEST -> 3;
			default -> 0;
		};
	}

	public BlockPos at(BlockPos heart, Part part, int rot) {
		return heart.offset(rotate(part.offset(), rot));
	}

	public BlockState ghost(Part part, int rot) {
		return part.ghost().rotate(rotation(rot));
	}

	public List<Part> missingParts(BlockGetter level, BlockPos heart, int rot) {
		List<Part> out = new ArrayList<>();
		for (Part part : this.parts) {
			if (!part.test().test(level.getBlockState(this.at(heart, part, rot)))) {
				out.add(part);
			}
		}
		return out;
	}

	public List<BlockPos> missing(BlockGetter level, BlockPos heart, int rot) {
		List<BlockPos> out = new ArrayList<>();
		for (Part part : this.missingParts(level, heart, rot)) {
			out.add(this.at(heart, part, rot));
		}
		return out;
	}

	/** A rotation in which the whole structure stands (trying {@code preferred} first), or -1. */
	public int match(BlockGetter level, BlockPos heart, int preferred) {
		for (int i = 0; i < (this.symmetric ? 1 : 4); i++) {
			int rot = (preferred + i) & 3;
			if (this.missingParts(level, heart, rot).isEmpty()) {
				return rot;
			}
		}
		return -1;
	}

	/** The rotation with the fewest missing blocks (ties go to {@code preferred}): what the hologram shows. */
	public int closest(BlockGetter level, BlockPos heart, int preferred) {
		if (this.symmetric) {
			return preferred & 3;
		}
		int best = preferred & 3, fewest = Integer.MAX_VALUE;
		for (int i = 0; i < 4; i++) {
			int rot = (preferred + i) & 3;
			int n = this.missingParts(level, heart, rot).size();
			if (n < fewest) {
				fewest = n;
				best = rot;
			}
		}
		return best;
	}

	public boolean contains(BlockPos heart, int rot, BlockPos pos) {
		if (pos.equals(heart)) {
			return true;
		}
		for (Part part : this.parts) {
			if (this.at(heart, part, rot).equals(pos)) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	public Part partAt(BlockPos heart, int rot, BlockPos pos) {
		for (Part part : this.parts) {
			if (this.at(heart, part, rot).equals(pos)) {
				return part;
			}
		}
		return null;
	}
}
