package dev.remnants.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.remnants.RemnantsMod;
import dev.remnants.fx.CombatCues;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Ground telegraphs and shock rings (art 2.0 hit feel): soul-cyan decals lying on the floor, drawn after the entities
 * with the translucent emissive entity type (no depth write, no culling). A disc shows its rim at once and an inner
 * ring that grows to meet it on the strike tick; an arc and a lane fill from the attacker outwards; at the end the
 * shape flashes and fades. Texture atlas: textures/misc/telegraph.png.
 */
public final class TelegraphRenderer {
	private static final ResourceLocation TEX = RemnantsMod.id("textures/misc/telegraph.png");
	private static final int R = 63, G = 210, B = 226;   // the soul colour
	private static final int LIGHT = 0xF000F0;

	private TelegraphRenderer() {
	}

	static void render(WorldRenderContext ctx) {
		if (CombatFx.TELEGRAPHS.isEmpty() && CombatFx.SHOCKS.isEmpty()) {
			return;
		}
		PoseStack ps = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		if (ps == null || buffers == null) {
			return;
		}
		Vec3 cam = ctx.camera().getPosition();
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		float now = CombatFx.now(partial);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEX));
		ps.pushPose();
		ps.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose pose = ps.last();
		for (CombatFx.Telegraph raw : CombatFx.TELEGRAPHS) {
			CombatFx.Telegraph t = raw.placed(partial);
			if (t == null) {
				continue;
			}
			float p = Mth.clamp((now - t.start()) / t.ticks(), 0.0F, 1.0F);
			float after = now - t.end();
			float fade = after <= 0 ? 1.0F : Mth.clamp(1.0F - after / 6.0F, 0.0F, 1.0F);
			float flash = after > 0 ? fade : 0.0F;
			float y = t.y() + 0.03F;
			switch (t.shape()) {
				case CombatCues.DISC -> {
					float r = t.size();
					disc(vc, pose, t.x(), y, t.z(), r, 0, 0, 0.5F, 0.5F, (0.55F + 0.4F * p) * fade, flash);
					if (after <= 0) {
						disc(vc, pose, t.x(), y + 0.005F, t.z(), Math.max(0.05F, r * ease(p)), 0, 0, 0.5F, 0.5F, 0.6F, 0.0F);
						runes(vc, pose, t.x(), y + 0.008F, t.z(), r * 0.86F, now, 0.5F * p);
					}
				}
				case CombatCues.ARC -> arc(vc, pose, t, y, p, fade, flash);
				case CombatCues.LINE -> lane(vc, pose, t, y, p, fade, flash);
				default -> {
				}
			}
		}
		for (float[] s : CombatFx.SHOCKS) {
			float k = Mth.clamp((now - s[4]) / s[5], 0.0F, 1.0F);
			float r = s[3] * (1.0F - (1.0F - k) * (1.0F - k));
			disc(vc, pose, s[0], s[1] + 0.04F, s[2], Math.max(0.1F, r), 0.5F, 0.5F, 1.0F, 1.0F, 0.9F * (1.0F - k), 1.0F - k);
		}
		ps.popPose();
	}

	private static float ease(float p) {
		return p * p * (3 - 2 * p);
	}

	/** A square decal of half-size r round (x, z), texture cell (u0, v0)..(u1, v1); white mixes in by {@code white}. */
	private static void disc(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float r, float u0, float v0, float u1, float v1,
		float alpha, float white) {
		int a = Mth.clamp((int) (alpha * 255), 0, 255);
		if (a <= 2) {
			return;
		}
		int cr = (int) Mth.lerp(white, R, 255), cg = (int) Mth.lerp(white, G, 255), cb = (int) Mth.lerp(white, B, 255);
		vertex(vc, pose, x - r, y, z - r, u0, v0, cr, cg, cb, a);
		vertex(vc, pose, x - r, y, z + r, u0, v1, cr, cg, cb, a);
		vertex(vc, pose, x + r, y, z + r, u1, v1, cr, cg, cb, a);
		vertex(vc, pose, x + r, y, z - r, u1, v0, cr, cg, cb, a);
	}

	/** A slow-turning ring of carved glyphs just inside a disc's rim. */
	private static void runes(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float r, float now, float alpha) {
		int n = Math.max(6, Math.round(r * 4));
		float w = (float) (Math.PI * 2 * r / n) * 0.42F;
		for (int i = 0; i < n; i++) {
			float ang = i * Mth.TWO_PI / n + now * 0.02F;
			float cx = x + Mth.cos(ang) * r, cz = z + Mth.sin(ang) * r;
			int g = i % 8;
			float u0 = (g % 4) * 8 / 64.0F, v0 = (32 + (g / 4) * 16) / 64.0F;
			quad(vc, pose, cx, y, cz, ang + Mth.HALF_PI, w * 0.5F, w, u0, v0, u0 + 8 / 64.0F, v0 + 16 / 64.0F, alpha, 0.0F);
		}
	}

	/** A rectangle centred on (cx, cz), turned by {@code ang} (radians, its "up" along the angle), half-width hw, half-length hl. */
	private static void quad(VertexConsumer vc, PoseStack.Pose pose, float cx, float y, float cz, float ang, float hw, float hl,
		float u0, float v0, float u1, float v1, float alpha, float white) {
		int a = Mth.clamp((int) (alpha * 255), 0, 255);
		if (a <= 2) {
			return;
		}
		int cr = (int) Mth.lerp(white, R, 255), cg = (int) Mth.lerp(white, G, 255), cb = (int) Mth.lerp(white, B, 255);
		float fx = Mth.cos(ang), fz = Mth.sin(ang), sx = -fz, sz = fx;
		vertex(vc, pose, cx - sx * hw - fx * hl, y, cz - sz * hw - fz * hl, u0, v1, cr, cg, cb, a);
		vertex(vc, pose, cx + sx * hw - fx * hl, y, cz + sz * hw - fz * hl, u1, v1, cr, cg, cb, a);
		vertex(vc, pose, cx + sx * hw + fx * hl, y, cz + sz * hw + fz * hl, u1, v0, cr, cg, cb, a);
		vertex(vc, pose, cx - sx * hw + fx * hl, y, cz - sz * hw + fz * hl, u0, v0, cr, cg, cb, a);
	}

	/** Minecraft yaw (degrees) -> radians of the facing direction in the XZ plane (x = cos, z = sin). */
	private static float dirAngle(float yaw) {
		return (yaw + 90.0F) * Mth.DEG_TO_RAD;
	}

	private static void arc(VertexConsumer vc, PoseStack.Pose pose, CombatFx.Telegraph t, float y, float p, float fade, float flash) {
		float r = t.size(), half = t.width() * 0.5F * Mth.DEG_TO_RAD, mid = dirAngle(t.yaw());
		int seg = Math.max(4, Math.round(t.width() / 10.0F));
		float reach = r * ease(p);
		for (int i = 0; i < seg; i++) {
			float a0 = mid - half + 2 * half * i / seg, a1 = mid - half + 2 * half * (i + 1) / seg;
			// faint whole sector, the growing fill, the bright rim band
			fan(vc, pose, t.x(), y, t.z(), a0, a1, 0.0F, r, 0.16F * fade, flash);
			if (flash <= 0) {
				fan(vc, pose, t.x(), y + 0.004F, t.z(), a0, a1, 0.0F, reach, 0.32F, 0.0F);
			}
			fan(vc, pose, t.x(), y + 0.008F, t.z(), a0, a1, r - 0.14F, r, (0.6F + 0.35F * p) * fade, flash);
		}
		for (float a : new float[] {mid - half, mid + half}) {
			float cx = t.x() + Mth.cos(a) * r * 0.5F, cz = t.z() + Mth.sin(a) * r * 0.5F;
			quad(vc, pose, cx, y + 0.008F, cz, a, 0.06F, r * 0.5F, 0.5F, 0.0F, 1.0F, 0.5F, (0.5F + 0.4F * p) * fade, flash);
		}
	}

	/** One slice of a ring between radii r0 and r1 from angle a0 to a1 (fill cell). */
	private static void fan(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float a0, float a1, float r0, float r1,
		float alpha, float white) {
		int a = Mth.clamp((int) (alpha * 255), 0, 255);
		if (a <= 2 || r1 <= r0) {
			return;
		}
		int cr = (int) Mth.lerp(white, R, 255), cg = (int) Mth.lerp(white, G, 255), cb = (int) Mth.lerp(white, B, 255);
		float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
		vertex(vc, pose, x + c0 * r0, y, z + s0 * r0, 0.5F, 0.0F, cr, cg, cb, a);
		vertex(vc, pose, x + c1 * r0, y, z + s1 * r0, 1.0F, 0.0F, cr, cg, cb, a);
		vertex(vc, pose, x + c1 * r1, y, z + s1 * r1, 1.0F, 0.5F, cr, cg, cb, a);
		vertex(vc, pose, x + c0 * r1, y, z + s0 * r1, 0.5F, 0.5F, cr, cg, cb, a);
	}

	private static void lane(VertexConsumer vc, PoseStack.Pose pose, CombatFx.Telegraph t, float y, float p, float fade, float flash) {
		float ang = dirAngle(t.yaw()), len = t.size(), hw = t.width() * 0.5F;
		float fx = Mth.cos(ang), fz = Mth.sin(ang);
		float cx = t.x() + fx * len * 0.5F, cz = t.z() + fz * len * 0.5F;
		quad(vc, pose, cx, y, cz, ang, hw, len * 0.5F, 0.5F, 0.0F, 1.0F, 0.5F, 0.16F * fade, flash);
		if (flash <= 0) {
			float l = len * ease(p);
			quad(vc, pose, t.x() + fx * l * 0.5F, y + 0.004F, t.z() + fz * l * 0.5F, ang, hw, l * 0.5F, 0.5F, 0.0F, 1.0F, 0.5F, 0.3F, 0.0F);
		}
		float sx = -fz, sz = fx;
		for (int side = -1; side <= 1; side += 2) {
			quad(vc, pose, cx + sx * hw * side, y + 0.008F, cz + sz * hw * side, ang, 0.07F, len * 0.5F, 0.5F, 0.0F, 1.0F, 0.5F,
				(0.55F + 0.4F * p) * fade, flash);
		}
		quad(vc, pose, t.x() + fx * len, y + 0.008F, t.z() + fz * len, ang + Mth.HALF_PI, 0.07F, hw, 0.5F, 0.0F, 1.0F, 0.5F,
			(0.55F + 0.4F * p) * fade, flash);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int r, int g, int b, int a) {
		vc.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT)
			.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}
}
