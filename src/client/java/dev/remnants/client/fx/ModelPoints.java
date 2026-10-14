package dev.remnants.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * World position of a point on a posed model part, for effects that must sit on the model (a glint on the blade, sparks
 * where the sword meets the floor). Mirrors LivingEntityRenderer.render: entity position, Y turn by 180 - body yaw,
 * flip (-1, -1, 1), down 1.501, then each part's translateAndRotate. Call it after the pose is set (end of setupAnim).
 */
public final class ModelPoints {
	private ModelPoints() {
	}

	/**
	 * @param chain the parts from the model's top part down to the one the point belongs to
	 * @param px    the point in that part's own Java model space, in pixels (x = -Blockbench x, y = -Blockbench y,
	 *              z = Blockbench z, all relative to the part's pivot)
	 */
	public static Vec3 world(LivingEntity e, float partial, float px, float py, float pz, ModelPart... chain) {
		PoseStack ps = new PoseStack();
		float yaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
		float s = e.getScale();
		ps.scale(s, s, s);
		ps.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
		ps.scale(-1.0F, -1.0F, 1.0F);
		ps.translate(0.0F, -1.501F, 0.0F);
		for (ModelPart p : chain) {
			p.translateAndRotate(ps);
		}
		Vector3f v = ps.last().pose().transformPosition(px / 16.0F, py / 16.0F, pz / 16.0F, new Vector3f());
		return new Vec3(Mth.lerp(partial, e.xo, e.getX()) + v.x, Mth.lerp(partial, e.yo, e.getY()) + v.y,
			Mth.lerp(partial, e.zo, e.getZ()) + v.z);
	}
}
