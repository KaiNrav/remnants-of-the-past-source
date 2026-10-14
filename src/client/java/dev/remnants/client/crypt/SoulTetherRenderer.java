package dev.remnants.client.crypt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.remnants.entity.SoulTetherEntity;
import dev.remnants.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The Soul Tether in flight: a chain (the vanilla chain texture, two crossed strips so it reads from every side) from
 * the holder's hand to the hook - a mimic's tongue - with a faint soul glow along the links.
 */
public class SoulTetherRenderer extends EntityRenderer<SoulTetherEntity> {
	private static final ResourceLocation CHAIN = ResourceLocation.withDefaultNamespace("textures/block/chain.png");
	private static final float WIDTH = 3.0F / 32.0F;
	private final ItemStack hook = new ItemStack(ModItems.MIMIC_TONGUE);

	public SoulTetherRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(SoulTetherEntity tether, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		Entity owner = tether.getOwner();
		Vec3 at = tether.getPosition(partial);
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-this.entityRenderDispatcher.camera.getYRot()));
		pose.scale(0.6F, 0.6F, 0.6F);
		Minecraft.getInstance().getItemRenderer().renderStatic(this.hook, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT,
			OverlayTexture.NO_OVERLAY, pose, buffers, tether.level(), tether.getId());
		pose.popPose();
		if (owner instanceof Player player) {
			Vec3 hand = handPos(player, partial);
			Vec3 d = hand.subtract(at);
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(CHAIN));
			float len = (float) d.length();
			float v1 = len * 2.0F;
			Matrix4f m = pose.last().pose();
			Vec3 side = d.cross(new Vec3(0, 1, 0));
			if (side.lengthSqr() < 1.0E-4) {
				side = new Vec3(1, 0, 0);
			}
			side = side.normalize().scale(WIDTH);
			Vec3 up = d.cross(side).normalize().scale(WIDTH);
			strip(vc, m, pose, Vec3.ZERO, d, side, v1, light);
			strip(vc, m, pose, Vec3.ZERO, d, up, v1, light);
		}
		super.render(tether, yaw, partial, pose, buffers, light);
	}

	private static void strip(VertexConsumer vc, Matrix4f m, PoseStack pose, Vec3 a, Vec3 b, Vec3 w, float v1, int light) {
		int full = LightTexture.FULL_BRIGHT;
		vertex(vc, m, pose, a.subtract(w), 0.0F, 0.0F, full);
		vertex(vc, m, pose, a.add(w), 0.1875F, 0.0F, full);
		vertex(vc, m, pose, b.add(w), 0.1875F, v1, light);
		vertex(vc, m, pose, b.subtract(w), 0.0F, v1, light);
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack pose, Vec3 p, float u, float v, int light) {
		vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(205, 235, 255, 255).setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0.0F, 1.0F, 0.0F);
	}

	/** Roughly where the holder's hand is (first person: low right of the view, like the fishing line). */
	private Vec3 handPos(Player player, float partial) {
		int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
		if (!player.getMainHandItem().is(ModItems.SOUL_TETHER)) {
			side = -side;
		}
		float bodyYaw = Mth.lerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		boolean firstPerson = this.entityRenderDispatcher.options.getCameraType().isFirstPerson() && player == Minecraft.getInstance().player;
		if (firstPerson) {
			Vec3 look = player.getViewVector(partial);
			Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
			return player.getEyePosition(partial).add(look.scale(0.6)).add(right.scale(0.32 * side)).add(0, -0.32, 0);
		}
		double s = Mth.sin(bodyYaw), c = Mth.cos(bodyYaw);
		Vec3 base = player.getPosition(partial);
		return base.add(-c * 0.35 * side - s * 0.4, player.isCrouching() ? 0.95 : 1.15, -s * 0.35 * side + c * 0.4);
	}

	@Override
	public ResourceLocation getTextureLocation(SoulTetherEntity entity) {
		return CHAIN;
	}
}
