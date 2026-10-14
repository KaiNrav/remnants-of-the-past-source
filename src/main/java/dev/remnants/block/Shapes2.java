package dev.remnants.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/** Voxel shapes authored facing north (like the models), turned like the blockstate's y rotation. */
final class Shapes2 {
	static VoxelShape of(double[]... boxes) {
		VoxelShape shape = Shapes.empty();
		for (double[] b : boxes) {
			shape = Shapes.or(shape, Block.box(b[0], b[1], b[2], b[3], b[4], b[5]));
		}
		return shape;
	}

	static Map<Direction, VoxelShape> horizontal(double[]... boxes) {
		Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			int turns = switch (facing) {
				case EAST -> 1;
				case SOUTH -> 2;
				case WEST -> 3;
				default -> 0;
			};
			VoxelShape shape = Shapes.empty();
			for (double[] b : boxes) {
				double[] r = b.clone();
				for (int k = 0; k < turns; k++) {   // blockstate y+90: (x, z) -> (16 - z, x)
					r = new double[] {16 - r[5], r[1], r[0], 16 - r[2], r[4], r[3]};
				}
				shape = Shapes.or(shape, Block.box(r[0], r[1], r[2], r[3], r[4], r[5]));
			}
			out.put(facing, shape);
		}
		return out;
	}

	private Shapes2() {
	}
}
