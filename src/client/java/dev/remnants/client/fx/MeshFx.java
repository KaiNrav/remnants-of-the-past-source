package dev.remnants.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.remnants.RemnantsMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Mesh VFX. Effects made of textured planes, rings, tubes, arcs and spinning
 * cubes that grow, turn, scroll and fade over a fraction of a second, glowing additively in the dark
 * (RenderType.eyes). Seven recipes:
 * <ul>
 * <li><b>shockwave</b> - a ground slam: a flash, two rings racing out, a low wall of soul fire riding the first, debris;</li>
 * <li><b>runeCircle</b> - a cast: a magic circle unfolding on the ground, glyph disc and hexagram turning opposite ways,
 * a faint column of light, held for the cast and folded away;</li>
 * <li><b>crack</b> - the ground torn open: glowing cracks and tongues of soul flame licking up along them;</li>
 * <li><b>pillar</b> - a soul rising or laid to rest: a tube of flame shooting up, scrolling, with a ring at its foot;</li>
 * <li><b>slash</b> - a blade's arc in the air, its head racing round and its tail burning out;</li>
 * <li><b>burst</b> - a heavy hit: a white star flash and crystal shards flung out spinning;</li>
 * <li><b>vortex</b> - a whirlwind: two tubes of streaks spinning round a mob, following it.</li>
 * </ul>
 * Client only: models call the recipes from their clip events, CombatFx from combat cues, and trigger particles
 * (ModParticles.FX_*) let the server start one with an ordinary particle packet.
 */
public final class MeshFx {
	private static final List<Fx> LIVE = new ArrayList<>();
	private static final Map<Long, Integer> ONCE = new HashMap<>();
	private static final int LIGHT = 0xF000F0;
	static final ResourceLocation RING = tex("ring"), RUNES = tex("runes"), STAR = tex("star"), CRACK = tex("crack"),
		FLAME = tex("flame"), SLASH = tex("slash"), FLASH = tex("flash"), CHUNK = tex("chunk"), SWIRL = tex("swirl");

	private MeshFx() {
	}

	private static ResourceLocation tex(String name) {
		return RemnantsMod.id("textures/misc/fx/" + name + ".png");
	}

	enum Kind { SHOCKWAVE, RUNE_CIRCLE, CRACK, PILLAR, SLASH, BURST, VORTEX }

	/** One effect: where, when (game ticks), for how long, how big, and a few shape parameters. */
	record Fx(Kind kind, double x, double y, double z, float start, float life, float size, float yaw, float tilt, float arc, int follow,
		long seed) {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(MeshFx::render);
	}

	static float now(float partial) {
		var level = Minecraft.getInstance().level;
		return level == null ? 0.0F : (level.getGameTime() % 1_000_000L) + partial;
	}

	private static void add(Kind kind, Vec3 at, float life, float size, float yaw, float tilt, float arc, int follow) {
		if (Minecraft.getInstance().level == null || LIVE.size() > 96) {
			return;
		}
		LIVE.add(new Fx(kind, at.x, at.y, at.z, now(0), life, size, yaw, tilt, arc, follow, (long) (at.x * 31 + at.z * 17 + now(0))));
	}

	/** True the first frame a model sees a new start tick for (entity, key): starts an effect once per clip. */
	public static boolean once(Entity e, int key, int tick) {
		if (!CombatFx.isLive(e)) {
			return false;   // a Codex portrait or a replay copy
		}
		long k = ((long) e.getId() << 8) | (key & 0xFF);
		Integer last = ONCE.put(k, tick);
		if (ONCE.size() > 512) {
			ONCE.clear();
		}
		return last == null || last != tick;
	}

	/** True when (entity, key)'s value changes from one frame to the next (not on the first frame it is seen): models
	 * start an effect when a clip begins without a start tick of their own. */
	public static boolean changed(Entity e, int key, int value) {
		if (!CombatFx.isLive(e)) {
			return false;
		}
		long k = ((long) e.getId() << 8) | 0x80 | (key & 0x7F);
		Integer last = ONCE.put(k, value);
		return last != null && last != value;
	}

	// ---- the recipes -----------------------------------------------------------------------------------------------

	/** A ground slam spreading {@code radius} blocks from {@code at} (its feet). */
	public static void shockwave(Vec3 at, float radius) {
		add(Kind.SHOCKWAVE, at, 16, radius, 0, 0, 0, -1);
	}

	/** A magic circle of {@code radius} on the ground at {@code at}, held {@code seconds}. */
	public static void runeCircle(Vec3 at, float radius, float seconds) {
		add(Kind.RUNE_CIRCLE, at, Math.max(14, seconds * 20.0F), radius, 0, 0, 0, -1);
	}

	/** A magic circle that stays under a mob while it casts. */
	public static void runeCircle(Entity under, float radius, float seconds) {
		add(Kind.RUNE_CIRCLE, under.position(), Math.max(14, seconds * 20.0F), radius, 0, 0, 0, under.getId());
	}

	/** The ground torn open round {@code at}: cracks of {@code radius} with flames licking up along them. */
	public static void crack(Vec3 at, float radius) {
		add(Kind.CRACK, at, 34, radius, 0, 0, 0, -1);
	}

	/** A pillar of soul fire {@code height} high and {@code radius} wide rising from {@code at}. */
	public static void pillar(Vec3 at, float radius, float height, float seconds) {
		add(Kind.PILLAR, at, Math.max(10, seconds * 20.0F), radius, 0, height, 0, -1);
	}

	/**
	 * A blade's arc round {@code centre}: radius {@code radius}, facing yaw {@code yaw} (Minecraft degrees), the swing
	 * covering {@code arc} degrees, the swing's plane rolled {@code roll} degrees (0 = level, 90 = an overhead chop).
	 */
	public static void slash(Vec3 centre, float yaw, float roll, float radius, float arc) {
		add(Kind.SLASH, centre, 9, radius, yaw, roll, arc, -1);
	}

	/** A heavy hit at {@code at}: a white star and shards flung out. */
	public static void burst(Vec3 at, float size) {
		add(Kind.BURST, at, 14, size, 0, 0, 0, -1);
	}

	/** A whirlwind round {@code e} for {@code seconds}. */
	public static void vortex(Entity e, float radius, float height, float seconds) {
		add(Kind.VORTEX, e.position(), seconds * 20.0F, radius, 0, height, 0, e.getId());
	}

	// ---- rendering --------------------------------------------------------------------------------------------------

	static void render(WorldRenderContext ctx) {
		if (LIVE.isEmpty()) {
			return;
		}
		PoseStack ps = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		var level = Minecraft.getInstance().level;
		if (ps == null || buffers == null || level == null) {
			return;
		}
		Camera cam = ctx.camera();
		Vec3 c = cam.getPosition();
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		float now = now(partial);
		ps.pushPose();
		ps.translate(-c.x, -c.y, -c.z);
		PoseStack.Pose pose = ps.last();
		Iterator<Fx> it = LIVE.iterator();
		while (it.hasNext()) {
			Fx fx = it.next();
			float age = now - fx.start();
			if (age < 0 || age > fx.life() || Math.abs(age) > 100_000) {
				it.remove();
				continue;
			}
			float t = age / fx.life();
			double x = fx.x(), y = fx.y(), z = fx.z();
			if (fx.follow() >= 0) {
				Entity e = level.getEntity(fx.follow());
				if (e != null) {
					x = Mth.lerp(partial, e.xo, e.getX());
					y = Mth.lerp(partial, e.yo, e.getY());
					z = Mth.lerp(partial, e.zo, e.getZ());
				}
			}
			Draw d = new Draw(buffers, pose, cam, (float) x, (float) y, (float) z);
			switch (fx.kind()) {
				case SHOCKWAVE -> shockwave(d, fx, t, age);
				case RUNE_CIRCLE -> runeCircle(d, fx, t, age);
				case CRACK -> crack(d, fx, t, age);
				case PILLAR -> pillar(d, fx, t, age);
				case SLASH -> slash(d, fx, t);
				case BURST -> burst(d, fx, t, age);
				case VORTEX -> vortex(d, fx, t, age);
			}
		}
		ps.popPose();
	}

	private static float easeOut(float t) {
		t = Mth.clamp(t, 0, 1);
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}

	private static float backOut(float t) {
		t = Mth.clamp(t, 0, 1);
		float s = 1.7F;
		return 1 + (s + 1) * (float) Math.pow(t - 1, 3) + s * (float) Math.pow(t - 1, 2);
	}

	private static void shockwave(Draw d, Fx fx, float t, float age) {
		float R = fx.size();
		if (t < 0.25F) {
			d.ground(FLASH, 0.05F, R * 0.35F * (0.6F + t * 2), age * 0.05F, 1.0F - t / 0.25F);
		}
		float r1 = R * easeOut(t * 1.15F);
		d.ground(RING, 0.06F, Math.max(0.2F, r1), age * 0.02F, (1 - t) * 1.1F);
		float t2 = (t - 0.18F) / 0.82F;
		if (t2 > 0) {
			d.ground(RING, 0.07F, Math.max(0.2F, R * 0.72F * easeOut(t2)), -age * 0.03F, (1 - t2) * 0.8F);
		}
		// a low wall of soul fire riding the first ring
		d.tube(FLAME, 0.0F, r1 * 0.86F, 0.95F * (1 - t), 20, 4, 0.0F, (1 - t) * 0.9F, age * 0.05F);
		Random rnd = new Random(fx.seed());
		for (int i = 0; i < 7; i++) {
			float a = i * Mth.TWO_PI / 7 + rnd.nextFloat() * 0.5F;
			float sp = R * (0.45F + rnd.nextFloat() * 0.4F);
			float k = easeOut(t);
			float h = 1.4F * (k - k * k) * 4 * (0.6F + rnd.nextFloat() * 0.6F);
			float s = 0.16F + rnd.nextFloat() * 0.12F;
			d.cube(CHUNK, Mth.cos(a) * sp * k, h + s, Mth.sin(a) * sp * k, s * (1 - t * 0.6F), age * (0.2F + rnd.nextFloat() * 0.2F), a, 1 - t);
		}
	}

	private static void runeCircle(Draw d, Fx fx, float t, float age) {
		float R = fx.size();
		float grow = backOut(age / 7.0F);
		float out = age > fx.life() - 8 ? Mth.clamp((fx.life() - age) / 8.0F, 0, 1) : 1.0F;
		float k = grow * (0.7F + 0.3F * out);
		float glow = out * (0.85F + 0.15F * Mth.sin(age * 0.25F));
		d.ground(RUNES, 0.03F, R * k, age * 0.012F, glow);
		d.ground(STAR, 0.04F, R * 0.62F * k, -age * 0.035F, glow);
		d.ground(RING, 0.035F, R * 1.06F * k, age * 0.02F, glow * 0.55F);
		if (age < 6) {
			d.ground(FLASH, 0.05F, R * 0.9F, 0, 1 - age / 6.0F);
		}
		d.tube(FLAME, 0.0F, R * 0.66F * k, 1.6F * k * (0.9F + 0.1F * Mth.sin(age * 0.7F)), 24, 6, 0.0F, glow * 0.25F, age * 0.02F);
	}

	private static void crack(Draw d, Fx fx, float t, float age) {
		float R = fx.size();
		float heat = age < 3 ? 1.2F : (1 - t) * (1 - t) + 0.15F * (1 - t);
		d.ground(CRACK, 0.04F, R, 0, heat);
		Random rnd = new Random(fx.seed() ^ 77);
		for (int i = 0; i < 9; i++) {
			float a = rnd.nextFloat() * Mth.TWO_PI, r = R * (0.25F + rnd.nextFloat() * 0.7F);
			float delay = rnd.nextFloat() * 0.25F;
			float u = Mth.clamp((t - delay) / (1 - delay), 0, 1);
			float h = 1.6F * Mth.sin(u * Mth.PI) * (0.5F + rnd.nextFloat() * 0.7F);
			if (h > 0.05F) {
				d.cross(FLAME, Mth.cos(a) * r, 0.0F, Mth.sin(a) * r, 0.35F + rnd.nextFloat() * 0.25F, h, 0.0F, (1 - u) * 0.95F);
			}
		}
	}

	private static void pillar(Draw d, Fx fx, float t, float age) {
		float r = fx.size(), H = fx.tilt();
		float rise = easeOut(age / 6.0F);
		float out = t > 0.7F ? (1 - t) / 0.3F : 1.0F;
		float flicker = 0.92F + 0.08F * Mth.sin(age * 1.3F) + 0.04F * Mth.sin(age * 2.9F);
		d.tube(FLAME, 0.0F, r * (1.0F - 0.25F * t), H * rise * flicker, 16, 3, 0.0F, out, age * 0.08F);
		d.tube(FLAME, 0.0F, r * 0.55F, H * 1.15F * rise * (2.0F - flicker), 12, 2, 0.0F, out * 0.8F, -age * 0.12F);
		d.tube(SWIRL, 0.0F, r * 0.75F, H * 0.9F * rise, 12, 2, age * 0.08F, out * 0.45F, age * 0.2F);
		d.ground(RING, 0.05F, r * (1.6F + t), age * 0.04F, out * 0.9F);
		if (age < 5) {
			d.ground(FLASH, 0.06F, r * 2.2F, 0, 1 - age / 5.0F);
		}
	}

	private static void slash(Draw d, Fx fx, float t) {
		// the head races round the first 40 %, the tail burns out behind it
		float head = easeOut(t / 0.4F);
		float tail = Mth.clamp((t - 0.25F) / 0.75F, 0, 1);
		if (head <= tail + 0.02F) {
			return;
		}
		d.arc(SLASH, fx.size(), fx.size() * 0.70F, fx.yaw(), fx.tilt(), fx.arc(), tail, head, 1.0F - t * 0.6F, 0.0F);
		d.arc(SLASH, fx.size() * 0.92F, fx.size() * 0.78F, fx.yaw(), fx.tilt(), fx.arc(), tail, head, (1.0F - t) * 0.9F, 0.0F);
		d.arc(SLASH, fx.size() * 0.95F, fx.size() * 0.95F, fx.yaw(), fx.tilt(), fx.arc(), tail, head, (1.0F - t) * 0.85F, fx.size() * 0.18F);
	}

	private static void burst(Draw d, Fx fx, float t, float age) {
		float s = fx.size();
		d.billboard(FLASH, s * (1.1F + t * 1.8F), age * 0.08F, (1 - t) * (1 - t) * 1.3F);
		d.ground(RING, -0.6F, s * (0.6F + 1.8F * easeOut(t)), age * 0.05F, (1 - t) * 0.7F);
		Random rnd = new Random(fx.seed() ^ 31);
		for (int i = 0; i < 8; i++) {
			Vector3f dir = new Vector3f(rnd.nextFloat() - 0.5F, rnd.nextFloat() * 0.8F - 0.1F, rnd.nextFloat() - 0.5F).normalize();
			float dist = s * 1.6F * easeOut(t);
			float cs = s * (0.12F + rnd.nextFloat() * 0.1F) * (1 - t * 0.7F);
			d.cube(CHUNK, dir.x * dist, dir.y * dist - 2.0F * t * t, dir.z * dist, cs, age * 0.4F, i, 1 - t);
		}
	}

	private static void vortex(Draw d, Fx fx, float t, float age) {
		float r = fx.size(), H = fx.tilt();
		float in = Mth.clamp(age / 8.0F, 0, 1), out = age > fx.life() - 10 ? Mth.clamp((fx.life() - age) / 10.0F, 0, 1) : 1.0F;
		float a = in * out;
		d.tube(SWIRL, 0.0F, r * (0.9F + 0.1F * Mth.sin(age * 0.3F)), H * a, 20, 3, age * 0.25F, a * 0.8F, age * 0.35F);
		d.tube(SWIRL, 0.0F, r * 1.35F, H * 0.75F * a, 20, 3, -age * 0.18F, a * 0.5F, -age * 0.5F);
		d.ground(RING, 0.05F, r * 1.6F, age * 0.06F, a * 0.7F);
	}

	// ---- geometry --------------------------------------------------------------------------------------------------

	/** Draws the shapes of one effect round its origin (world x, y, z), additive and full-bright. */
	static final class Draw {
		final MultiBufferSource buffers;
		final PoseStack.Pose pose;
		final Camera cam;
		final float ox, oy, oz;

		Draw(MultiBufferSource buffers, PoseStack.Pose pose, Camera cam, float ox, float oy, float oz) {
			this.buffers = buffers;
			this.pose = pose;
			this.cam = cam;
			this.ox = ox;
			this.oy = oy;
			this.oz = oz;
		}

		private VertexConsumer vc(ResourceLocation tex) {
			return this.buffers.getBuffer(RenderType.eyes(tex));
		}

		private void v(VertexConsumer vc, float x, float y, float z, float u, float w, int k) {
			vc.addVertex(this.pose, this.ox + x, this.oy + y, this.oz + z).setColor(k, k, k, 255).setUv(u, w).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LIGHT).setNormal(this.pose, 0.0F, 1.0F, 0.0F);
		}

		private static int k(float intensity) {
			return Mth.clamp((int) (intensity * 255.0F), 0, 255);
		}

		/** Both windings of a quad (the eyes type culls back faces). */
		private void quad(VertexConsumer vc, float[] p, float[] uv, int k) {
			for (int i = 0; i < 4; i++) {
				this.v(vc, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], uv[i * 2], uv[i * 2 + 1], k);
			}
			for (int i = 3; i >= 0; i--) {
				this.v(vc, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], uv[i * 2], uv[i * 2 + 1], k);
			}
		}

		/** A square lying on the ground, half-size h, turned by ang (radians), y above the origin. */
		void ground(ResourceLocation tex, float y, float h, float ang, float intensity) {
			int k = k(intensity);
			if (k < 3 || h <= 0.01F) {
				return;
			}
			float c = Mth.cos(ang) * h, s = Mth.sin(ang) * h;
			this.quad(this.vc(tex), new float[] {-c + s, y, -s - c, c + s, y, s - c, c - s, y, s + c, -c - s, y, -s + c},
				new float[] {0, 0, 1, 0, 1, 1, 0, 1}, k);
		}

		/** A camera-facing square of half-size h, rolled by ang. */
		void billboard(ResourceLocation tex, float h, float ang, float intensity) {
			int k = k(intensity);
			if (k < 3) {
				return;
			}
			Vector3f l = new Vector3f(this.cam.getLeftVector()), u = new Vector3f(this.cam.getUpVector());
			Vector3f a = new Vector3f(l).mul(Mth.cos(ang)).add(new Vector3f(u).mul(Mth.sin(ang))).mul(h);
			Vector3f b = new Vector3f(u).mul(Mth.cos(ang)).sub(new Vector3f(l).mul(Mth.sin(ang))).mul(h);
			this.quad(this.vc(tex), new float[] {-a.x - b.x, -a.y - b.y, -a.z - b.z, a.x - b.x, a.y - b.y, a.z - b.z, a.x + b.x, a.y + b.y,
				a.z + b.z, -a.x + b.x, -a.y + b.y, -a.z + b.z}, new float[] {0, 1, 1, 1, 1, 0, 0, 0}, k);
		}

		/** An open tube of radius r and height h standing at y0, its texture wrapped {@code wraps} times round and
		 * scrolling up by {@code scroll}; {@code turn} spins it (radians). */
		void tube(ResourceLocation tex, float y0, float r, float h, int seg, int wraps, float scroll, float intensity) {
			this.tube(tex, y0, r, h, seg, wraps, scroll, intensity, 0.0F);
		}

		void tube(ResourceLocation tex, float y0, float r, float h, int seg, int wraps, float scroll, float intensity, float turn) {
			int k = k(intensity);
			if (k < 3 || r <= 0.01F || h <= 0.01F) {
				return;
			}
			VertexConsumer vc = this.vc(tex);
			float v0 = scroll % 1.0F, v1 = v0 + 1.0F;
			for (int i = 0; i < seg; i++) {
				float a0 = turn + i * Mth.TWO_PI / seg, a1 = turn + (i + 1) * Mth.TWO_PI / seg;
				float u0 = (float) i * wraps / seg, u1 = (float) (i + 1) * wraps / seg;
				float x0 = Mth.cos(a0) * r, z0 = Mth.sin(a0) * r, x1 = Mth.cos(a1) * r, z1 = Mth.sin(a1) * r;
				this.quad(vc, new float[] {x0, y0, z0, x1, y0, z1, x1, y0 + h, z1, x0, y0 + h, z0}, new float[] {u0, v1, u1, v1, u1, v0, u0, v0}, k);
			}
		}

		/** Two crossed upright planes (a tongue of flame) at (x, y, z), half-width w, height h. */
		void cross(ResourceLocation tex, float x, float y, float z, float w, float h, float scroll, float intensity) {
			int k = k(intensity);
			if (k < 3) {
				return;
			}
			VertexConsumer vc = this.vc(tex);
			float v0 = scroll % 1.0F, v1 = v0 + 1.0F;
			this.quad(vc, new float[] {x - w, y, z, x + w, y, z, x + w, y + h, z, x - w, y + h, z}, new float[] {0, v1, 0.5F, v1, 0.5F, v0, 0, v0}, k);
			this.quad(vc, new float[] {x, y, z - w, x, y, z + w, x, y + h, z + w, x, y + h, z - w}, new float[] {0.5F, v1, 1, v1, 1, v0, 0.5F, v0}, k);
		}

		/** A spinning cube of half-size s at (x, y, z). */
		void cube(ResourceLocation tex, float x, float y, float z, float s, float spin, float axis, float intensity) {
			int k = k(intensity);
			if (k < 3 || s <= 0.005F) {
				return;
			}
			VertexConsumer vc = this.vc(tex);
			Quaternionf q = new Quaternionf().rotateY(axis).rotateX(spin).rotateZ(spin * 0.7F);
			float[][] corners = new float[8][];
			for (int i = 0; i < 8; i++) {
				Vector3f p = q.transform(new Vector3f((i & 1) == 0 ? -s : s, (i & 2) == 0 ? -s : s, (i & 4) == 0 ? -s : s));
				corners[i] = new float[] {x + p.x, y + p.y, z + p.z};
			}
			int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
			float[] uv = {0, 0, 1, 0, 1, 1, 0, 1};
			for (int[] f : faces) {
				float[] p = new float[12];
				for (int i = 0; i < 4; i++) {
					System.arraycopy(corners[f[i]], 0, p, i * 3, 3);
				}
				this.quad(vc, p, uv, k);
			}
		}

		/**
		 * An arc band in a plane round the origin: radii r1 (outer) and r0 (inner), centred on yaw (Minecraft degrees),
		 * spanning {@code arc} degrees, the plane rolled {@code roll} degrees about the facing; only the part between
		 * {@code from} and {@code to} (0..1 along the swing) is drawn, the texture's head at {@code to}.
		 */
		void arc(ResourceLocation tex, float r1, float r0, float yaw, float roll, float arc, float from, float to, float intensity, float across) {
			int k = k(intensity);
			if (k < 3 || to <= from) {
				return;
			}
			float face = (yaw + 90.0F) * Mth.DEG_TO_RAD;
			Vector3f fwd = new Vector3f(Mth.cos(face), 0, Mth.sin(face));
			Vector3f side = new Vector3f(-fwd.z, 0, fwd.x);
			// roll the swing's plane about the facing: 0 = a level sweep, 90 = a vertical chop
			Quaternionf rollQ = new Quaternionf().rotateAxis(roll * Mth.DEG_TO_RAD, fwd.x, fwd.y, fwd.z);
			rollQ.transform(side);
			VertexConsumer vc = this.vc(tex);
			int seg = 14;
			float half = arc * 0.5F * Mth.DEG_TO_RAD;
			for (int i = 0; i < seg; i++) {
				float s0 = from + (to - from) * i / seg, s1 = from + (to - from) * (i + 1) / seg;
				float a0 = -half + 2 * half * s0, a1 = -half + 2 * half * s1;
				float u0 = (s0 - from) / (to - from), u1 = (s1 - from) / (to - from);
				Vector3f d0 = new Vector3f(fwd).mul(Mth.cos(a0)).add(new Vector3f(side).mul(Mth.sin(a0)));
				Vector3f d1 = new Vector3f(fwd).mul(Mth.cos(a1)).add(new Vector3f(side).mul(Mth.sin(a1)));
				if (across > 0.0F) {
					// a band standing across the swing's plane (along its normal), half-height {@code across}
					Vector3f n = new Vector3f(fwd).cross(side).normalize().mul(across);
					this.quad(vc, new float[] {d0.x * r1 - n.x, d0.y * r1 - n.y, d0.z * r1 - n.z, d1.x * r1 - n.x, d1.y * r1 - n.y, d1.z * r1 - n.z,
						d1.x * r1 + n.x, d1.y * r1 + n.y, d1.z * r1 + n.z, d0.x * r1 + n.x, d0.y * r1 + n.y, d0.z * r1 + n.z},
						new float[] {u0, 1, u1, 1, u1, 0, u0, 0}, k);
					continue;
				}
				this.quad(vc, new float[] {d0.x * r0, d0.y * r0, d0.z * r0, d1.x * r0, d1.y * r0, d1.z * r0, d1.x * r1, d1.y * r1, d1.z * r1,
					d0.x * r1, d0.y * r1, d0.z * r1}, new float[] {u0, 1, u1, 1, u1, 0, u0, 0}, k);
			}
		}
	}
}
