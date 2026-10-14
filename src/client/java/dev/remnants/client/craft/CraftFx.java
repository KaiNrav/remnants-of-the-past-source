package dev.remnants.client.craft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.remnants.RemnantsMod;
import dev.remnants.multiblock.HeartBlockEntity;
import dev.remnants.multiblock.MultiblockPattern;
import dev.remnants.multiblock.Multiblocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared drawing for the 1.3 multiblocks: the missing-block hologram (generalised from the summoning circle's ghost
 * pedestals), the formation wave, flat textured quads and beams, items. Everything glowing is full-bright.
 */
public final class CraftFx {
	public static final ResourceLocation RUNES = RemnantsMod.id("textures/entity/crafting/runes.png");
	public static final ResourceLocation RIPPLE = RemnantsMod.id("textures/entity/crafting/ripple.png");
	public static final ResourceLocation MOLTEN = RemnantsMod.id("textures/entity/crafting/molten.png");
	public static final ResourceLocation POUR = RemnantsMod.id("textures/entity/crafting/pour.png");
	public static final ResourceLocation STEAM = RemnantsMod.id("textures/entity/crafting/steam.png");
	public static final ResourceLocation BEAM = ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");
	public static final int RUNE_COUNT = 8, RIPPLE_FRAMES = 8, MOLTEN_FRAMES = 8, STEAM_FRAMES = 4;

	/**
	 * The render type for glowing effect sprites. It writes depth (entityTranslucent): translucent terrain - the pool's
	 * water, the cauldron's - is drawn after block entities, and would wash over effects that leave no depth behind.
	 * Drawn full-bright, so it still glows.
	 */
	public static RenderType glow(ResourceLocation texture) {
		return RenderType.entityTranslucent(texture);
	}
	private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};
	private static final int FULL = LightTexture.FULL_BRIGHT;

	public static float ease(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	// ---- the hologram and the wave ---------------------------------------------------------------------

	/**
	 * Missing blocks of an unformed structure as soul-tinted ghosts, layer by layer from the bottom (a layer every 25
	 * ticks), fading out at the end of {@link HeartBlockEntity#HOLOGRAM_TICKS}. Re-checked every frame, so a ghost
	 * disappears the moment its block is placed.
	 */
	public static void hologram(HeartBlockEntity heart, float partial, PoseStack pose, MultiBufferSource buffers) {
		Level level = heart.getLevel();
		if (level == null || heart.isFormed()) {
			return;
		}
		float t = level.getGameTime() - heart.hologramAt() + partial;
		if (t < 0 || t > HeartBlockEntity.HOLOGRAM_TICKS) {
			return;
		}
		BlockPos center = heart.getBlockPos();
		MultiblockPattern pattern = heart.pattern();
		int rot = heart.hologramRotation();
		List<MultiblockPattern.Part> missing = pattern.missingParts(level, center, rot);
		if (missing.isEmpty()) {
			return;
		}
		int minY = Integer.MAX_VALUE;
		for (MultiblockPattern.Part part : missing) {
			minY = Math.min(minY, part.offset().getY());
		}
		float fadeOut = Mth.clamp((HeartBlockEntity.HOLOGRAM_TICKS - t) / 30.0F, 0.0F, 1.0F);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
		for (int i = 0; i < missing.size(); i++) {
			MultiblockPattern.Part part = missing.get(i);
			int layer = part.offset().getY() - minY;
			float in = Mth.clamp((t - layer * 25.0F - (i % 5) * 1.5F) / 15.0F, 0.0F, 1.0F);
			if (in <= 0) {
				continue;
			}
			float alpha = (0.28F + 0.14F * Mth.sin(t * 0.09F + i)) * ease(in) * fadeOut;
			BlockPos rel = pattern.at(center, part, rot).subtract(center);
			pose.pushPose();
			pose.translate(rel.getX(), rel.getY() + 0.3F * (1.0F - ease(in)), rel.getZ());
			BlockState ghost = pattern.ghost(part, rot);
			if (Multiblocks.isWaterSource(ghost)) {
				waterPlane(pose, vc, 0.875F, 0.45F, 0.85F, 1.0F, alpha);
			} else {
				ghostModel(pose, vc, ghost, 0.55F, 0.95F, 1.0F, alpha);
			}
			pose.popPose();
		}
	}

	/** On locking, a wave of soul light runs out from the heart over every block of the structure (~1 s). */
	public static void wave(HeartBlockEntity heart, float partial, PoseStack pose, MultiBufferSource buffers) {
		Level level = heart.getLevel();
		if (level == null || !heart.isFormed()) {
			return;
		}
		float t = level.getGameTime() - heart.formedAt() + partial;
		if (t < 0 || t > HeartBlockEntity.WAVE_TICKS + 8) {
			return;
		}
		BlockPos center = heart.getBlockPos();
		MultiblockPattern pattern = heart.pattern();
		float front = t / HeartBlockEntity.WAVE_TICKS * 3.2F;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
		List<BlockPos> blocks = new ArrayList<>();
		blocks.add(center);
		for (MultiblockPattern.Part part : pattern.parts()) {
			blocks.add(pattern.at(center, part, heart.rotation()));
		}
		for (BlockPos p : blocks) {
			BlockPos rel = p.subtract(center);
			float d = Mth.sqrt((float) (rel.getX() * rel.getX() + rel.getY() * rel.getY() + rel.getZ() * rel.getZ()));
			float glow = 1.0F - Math.abs(d - front) / 1.1F;
			if (glow <= 0) {
				continue;
			}
			BlockState state = level.getBlockState(p);
			pose.pushPose();
			pose.translate(rel.getX() + 0.5F, rel.getY() + 0.5F, rel.getZ() + 0.5F);
			pose.scale(1.03F, 1.03F, 1.03F);
			pose.translate(-0.5F, -0.5F, -0.5F);
			if (Multiblocks.isWaterSource(state)) {
				waterPlane(pose, vc, 0.9F, 0.4F, 0.95F, 1.0F, 0.7F * glow);
			} else {
				ghostModel(pose, vc, state, 0.45F, 0.95F, 1.0F, 0.55F * glow);
			}
			pose.popPose();
		}
	}

	public static void ghostModel(PoseStack pose, VertexConsumer vc, BlockState state, float r, float g, float b, float alpha) {
		BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
		RandomSource random = RandomSource.create(42L);
		PoseStack.Pose last = pose.last();
		for (Direction side : SIDES) {
			random.setSeed(42L);
			for (BakedQuad quad : model.getQuads(state, side, random)) {
				vc.putBulkData(last, quad, r, g, b, alpha, FULL, OverlayTexture.NO_OVERLAY);
			}
		}
	}

	/** A water surface for a missing (or glowing) water block, from the block atlas' water sprite. */
	public static void waterPlane(PoseStack pose, VertexConsumer vc, float y, float r, float g, float b, float alpha) {
		TextureAtlasSprite water = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
			.apply(ResourceLocation.withDefaultNamespace("block/water_still"));
		PoseStack.Pose p = pose.last();
		int a = (int) (alpha * 255), ri = (int) (r * 255), gi = (int) (g * 255), bi = (int) (b * 255);
		vertex(vc, p, 0, y, 0, water.getU0(), water.getV0(), ri, gi, bi, a, FULL, 0, 1, 0);
		vertex(vc, p, 0, y, 1, water.getU0(), water.getV1(), ri, gi, bi, a, FULL, 0, 1, 0);
		vertex(vc, p, 1, y, 1, water.getU1(), water.getV1(), ri, gi, bi, a, FULL, 0, 1, 0);
		vertex(vc, p, 1, y, 0, water.getU1(), water.getV0(), ri, gi, bi, a, FULL, 0, 1, 0);
	}

	// ---- quads ---------------------------------------------------------------------------------------

	public static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int r, int g, int b, int a,
		int light, float nx, float ny, float nz) {
		vc.addVertex(p, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}

	/** A horizontal square of side {@code size} centred on (x, y, z), both faces, uv (u0, v0)-(u1, v1). */
	public static void flat(VertexConsumer vc, PoseStack pose, float x, float y, float z, float size, float u0, float v0, float u1, float v1,
		int argb, int light) {
		PoseStack.Pose p = pose.last();
		float h = size / 2;
		int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
		vertex(vc, p, x - h, y, z - h, u0, v0, r, g, b, a, light, 0, 1, 0);
		vertex(vc, p, x - h, y, z + h, u0, v1, r, g, b, a, light, 0, 1, 0);
		vertex(vc, p, x + h, y, z + h, u1, v1, r, g, b, a, light, 0, 1, 0);
		vertex(vc, p, x + h, y, z - h, u1, v0, r, g, b, a, light, 0, 1, 0);
		vertex(vc, p, x + h, y, z - h, u1, v0, r, g, b, a, light, 0, -1, 0);
		vertex(vc, p, x + h, y, z + h, u1, v1, r, g, b, a, light, 0, -1, 0);
		vertex(vc, p, x - h, y, z + h, u0, v1, r, g, b, a, light, 0, -1, 0);
		vertex(vc, p, x - h, y, z - h, u0, v0, r, g, b, a, light, 0, -1, 0);
	}

	/** A camera-facing square of side {@code size} at (x, y, z). */
	public static void billboard(VertexConsumer vc, PoseStack pose, float x, float y, float z, float size, float u0, float v0, float u1, float v1,
		int argb, int light) {
		pose.pushPose();
		pose.translate(x, y, z);
		Quaternionf cam = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
		pose.mulPose(cam);
		PoseStack.Pose p = pose.last();
		float h = size / 2;
		int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
		vertex(vc, p, -h, -h, 0, u1, v1, r, g, b, a, light, 0, 1, 0);
		vertex(vc, p, h, -h, 0, u0, v1, r, g, b, a, light, 0, 1, 0);
		vertex(vc, p, h, h, 0, u0, v0, r, g, b, a, light, 0, 1, 0);
		vertex(vc, p, -h, h, 0, u1, v0, r, g, b, a, light, 0, 1, 0);
		pose.popPose();
	}

	/**
	 * A ribbon from a to b, {@code width} wide, as two crossed strips (reads from any side); the texture runs along it,
	 * v from {@code v0} (at a) to {@code v1} (at b) - scroll v for flow.
	 */
	public static void ribbon(VertexConsumer vc, PoseStack pose, float ax, float ay, float az, float bx, float by, float bz, float width,
		float v0, float v1, int argb, int light) {
		float dx = bx - ax, dy = by - ay, dz = bz - az;
		float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0E-4F) {
			return;
		}
		// two side vectors perpendicular to the ribbon
		float[][] sides;
		if (Math.abs(dy) > 0.9F * len) {
			sides = new float[][] {{1, 0, 0}, {0, 0, 1}};
		} else {
			float hx = -dz / len, hz = dx / len;   // horizontal, perpendicular
			sides = new float[][] {{hx, 0, hz}, {0, 1, 0}};
		}
		PoseStack.Pose p = pose.last();
		int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
		for (float[] s : sides) {
			float sx = s[0] * width / 2, sy = s[1] * width / 2, sz = s[2] * width / 2;
			vertex(vc, p, ax - sx, ay - sy, az - sz, 0, v0, r, g, b, a, light, 0, 1, 0);
			vertex(vc, p, bx - sx, by - sy, bz - sz, 0, v1, r, g, b, a, light, 0, 1, 0);
			vertex(vc, p, bx + sx, by + sy, bz + sz, 1, v1, r, g, b, a, light, 0, 1, 0);
			vertex(vc, p, ax + sx, ay + sy, az + sz, 1, v0, r, g, b, a, light, 0, 1, 0);
			vertex(vc, p, ax + sx, ay + sy, az + sz, 1, v0, r, g, b, a, light, 0, 1, 0);
			vertex(vc, p, bx + sx, by + sy, bz + sz, 1, v1, r, g, b, a, light, 0, 1, 0);
			vertex(vc, p, bx - sx, by - sy, bz - sz, 0, v1, r, g, b, a, light, 0, 1, 0);
			vertex(vc, p, ax - sx, ay - sy, az - sz, 0, v0, r, g, b, a, light, 0, 1, 0);
		}
	}

	/** A vertical glowing column (an 8-sided prism, both faces) of radius r from y0 to y1 around (x, z). */
	public static void column(VertexConsumer vc, PoseStack pose, float x, float z, float y0, float y1, float r, float vOffset, int argb) {
		PoseStack.Pose p = pose.last();
		int a = argb >>> 24, cr = argb >> 16 & 255, cg = argb >> 8 & 255, cb = argb & 255;
		int n = 8;
		float vSpan = (y1 - y0) / 2.0F;
		for (int i = 0; i < n; i++) {
			double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
			float x0 = x + (float) Math.cos(a0) * r, z0 = z + (float) Math.sin(a0) * r;
			float x1 = x + (float) Math.cos(a1) * r, z1 = z + (float) Math.sin(a1) * r;
			float u0 = i / (float) n, u1 = (i + 1) / (float) n;
			vertex(vc, p, x0, y0, z0, u0, vOffset + vSpan, cr, cg, cb, a, FULL, 0, 1, 0);
			vertex(vc, p, x0, y1, z0, u0, vOffset, cr, cg, cb, a, FULL, 0, 1, 0);
			vertex(vc, p, x1, y1, z1, u1, vOffset, cr, cg, cb, a, FULL, 0, 1, 0);
			vertex(vc, p, x1, y0, z1, u1, vOffset + vSpan, cr, cg, cb, a, FULL, 0, 1, 0);
			vertex(vc, p, x1, y0, z1, u1, vOffset + vSpan, cr, cg, cb, a, FULL, 0, 1, 0);
			vertex(vc, p, x1, y1, z1, u1, vOffset, cr, cg, cb, a, FULL, 0, 1, 0);
			vertex(vc, p, x0, y1, z0, u0, vOffset, cr, cg, cb, a, FULL, 0, 1, 0);
			vertex(vc, p, x0, y0, z0, u0, vOffset + vSpan, cr, cg, cb, a, FULL, 0, 1, 0);
		}
	}

	// ---- items ---------------------------------------------------------------------------------------

	/** An item floating at (x, y, z), turning slowly, {@code scale} of its in-world size. */
	public static void floatingItem(ItemStack stack, PoseStack pose, MultiBufferSource buffers, Level level, float x, float y, float z,
		float scale, float spin, int light) {
		if (stack.isEmpty()) {
			return;
		}
		pose.pushPose();
		pose.translate(x, y, z);
		pose.mulPose(Axis.YP.rotationDegrees(spin));
		pose.scale(scale, scale, scale);
		Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
			level, 0);
		pose.popPose();
	}

	/** An item lying flat (an anvil's workpiece). */
	public static void lyingItem(ItemStack stack, PoseStack pose, MultiBufferSource buffers, Level level, float x, float y, float z, float scale,
		float yaw, int light) {
		if (stack.isEmpty()) {
			return;
		}
		pose.pushPose();
		pose.translate(x, y, z);
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.mulPose(Axis.XP.rotationDegrees(90.0F));
		pose.scale(scale, scale, scale);
		Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
			level, 0);
		pose.popPose();
	}

	private CraftFx() {
	}
}
