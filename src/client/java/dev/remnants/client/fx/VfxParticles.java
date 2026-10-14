package dev.remnants.client.fx;

import dev.remnants.registry.ModParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The 2.0 VFX particles. One particle class, one {@link Style} per effect: how long it lives
 * (its frames play over that life, about two ticks each, like vanilla's), how big it is and how it grows, how it moves.
 * All are full-bright: they are light.
 */
public final class VfxParticles {
	private VfxParticles() {
	}

	/**
	 * @param life     lifetime in ticks (min, + random up to lifeJitter)
	 * @param size     quad size (min, + random up to sizeJitter)
	 * @param grow     size multiplier reached at the end of its life
	 * @param gravity  vanilla gravity (negative rises)
	 * @param friction velocity kept per tick
	 * @param keepVelocity share of the spawn velocity it starts with
	 * @param random   picks one frame at random and keeps it (runes) instead of playing the frames
	 * @param spin     random roll speed (radians per tick, either way)
	 * @param fade     alpha falls to 0 over the last part of its life
	 * @param tint     r, g, b multiplier (the grey smoke is tinted soul-violet), or null
	 * @param physics  collides with blocks and dies on landing (drips)
	 * @param sway     side-to-side flutter while it falls (feathers, paper), blocks per tick
	 */
	public record Style(int life, int lifeJitter, float size, float sizeJitter, float grow, float gravity, float friction, float keepVelocity,
		boolean random, float spin, boolean fade, boolean translucent, float[] tint, boolean physics, float sway) {
		Style(int life, int lifeJitter, float size, float sizeJitter, float grow, float gravity, float friction, float keepVelocity,
			boolean random, float spin, boolean fade, boolean translucent, float[] tint) {
			this(life, lifeJitter, size, sizeJitter, grow, gravity, friction, keepVelocity, random, spin, fade, translucent, tint, false, 0.0F);
		}
	}

	static final Style BURST = new Style(14, 0, 0.55F, 0.25F, 1.6F, 0.0F, 0.8F, 0.2F, false, 0.0F, false, true, null);
	static final Style RING = new Style(14, 0, 1.4F, 0.4F, 2.2F, 0.0F, 0.8F, 0.0F, false, 0.0F, false, true, null);
	static final Style SLASH = new Style(12, 0, 0.7F, 0.2F, 1.2F, 0.0F, 0.6F, 0.3F, false, 0.0F, false, true, null);
	static final Style FLAME = new Style(12, 10, 0.12F, 0.06F, 0.7F, -0.02F, 0.9F, 0.5F, false, 0.0F, false, false, null);
	static final Style EMBER = new Style(8, 10, 0.06F, 0.04F, 1.0F, -0.04F, 0.92F, 1.0F, false, 0.0F, false, false, null);
	static final Style TWINKLE = new Style(10, 4, 0.12F, 0.08F, 1.0F, 0.0F, 0.85F, 0.6F, false, 0.0F, false, false, null);
	static final Style RUNE = new Style(26, 14, 0.12F, 0.05F, 1.0F, -0.015F, 0.9F, 0.4F, true, 0.0F, true, true, null);
	static final Style SKULL = new Style(16, 4, 0.45F, 0.15F, 1.25F, -0.02F, 0.9F, 0.3F, false, 0.0F, false, true, null);
	static final Style SMOKE = new Style(16, 8, 0.35F, 0.2F, 1.8F, -0.01F, 0.88F, 0.6F, false, 0.08F, true, true,
		new float[] {0.62F, 0.55F, 0.86F});
	static final Style EXPLOSION = new Style(20, 0, 1.0F, 0.4F, 1.5F, 0.0F, 0.7F, 0.1F, false, 0.0F, false, true, null);
	// art 4.0: the mobs' ambient life
	static final Style WISP = new Style(16, 8, 0.11F, 0.05F, 1.35F, -0.025F, 0.9F, 0.6F, false, 0.0F, true, true, null);
	static final Style DRIP = new Style(40, 10, 0.05F, 0.02F, 1.0F, 0.55F, 0.98F, 0.3F, false, 0.0F, false, false, null, true, 0.0F);
	static final Style FEATHER = new Style(60, 30, 0.09F, 0.04F, 1.0F, 0.05F, 0.9F, 0.5F, true, 0.06F, true, false, null, true, 0.012F);
	static final Style SCRAP = new Style(45, 25, 0.08F, 0.04F, 1.0F, 0.06F, 0.9F, 0.6F, true, 0.14F, true, false, null, true, 0.01F);
	static final Style DUST = new Style(14, 6, 0.13F, 0.07F, 1.7F, -0.004F, 0.82F, 0.7F, false, 0.05F, true, true, null);
	static final Style CURSE = new Style(10, 10, 0.06F, 0.04F, 1.0F, -0.03F, 0.92F, 1.0F, false, 0.0F, false, false, null);
	static final Style EYE = new Style(5, 3, 0.055F, 0.015F, 0.5F, 0.0F, 0.5F, 0.0F, false, 0.0F, false, false, null);

	public static void register() {
		ParticleFactoryRegistry r = ParticleFactoryRegistry.getInstance();
		r.register(ModParticles.SOUL_BURST, sprites -> new Provider(sprites, BURST));
		r.register(ModParticles.SHOCK_RING, sprites -> (t, l, x, y, z, a, b, c) -> {
			MeshFx.shockwave(new Vec3(x, y - 0.2, z), 4.5F);   // art 4.0: the mesh shockwave instead of the flat sprite ring
			return null;
		});
		r.register(ModParticles.SOUL_SLASH, sprites -> new Provider(sprites, SLASH));
		r.register(ModParticles.SOUL_FLAME, sprites -> new Provider(sprites, FLAME));
		r.register(ModParticles.SOUL_EMBER, sprites -> new Provider(sprites, EMBER));
		r.register(ModParticles.TWINKLE, sprites -> new Provider(sprites, TWINKLE));
		r.register(ModParticles.RUNE, sprites -> new Provider(sprites, RUNE));
		r.register(ModParticles.SKULL_WISP, sprites -> new Provider(sprites, SKULL));
		r.register(ModParticles.SOUL_SMOKE, sprites -> new Provider(sprites, SMOKE));
		r.register(ModParticles.SOUL_EXPLOSION, sprites -> (t, l, x, y, z, a, b, c) -> {
			MeshFx.pillar(new Vec3(x, y - 1.1, z), 0.8F, 4.5F, 1.2F);
			MeshFx.burst(new Vec3(x, y, z), 1.1F);
			return new Fx(l, x, y, z, a, b, c, sprites, EXPLOSION);
		});
		r.register(ModParticles.SOUL_WISP, sprites -> new Provider(sprites, WISP));
		r.register(ModParticles.SOUL_DRIP, sprites -> new Provider(sprites, DRIP));
		r.register(ModParticles.FEATHER, sprites -> new Provider(sprites, FEATHER));
		r.register(ModParticles.PAGE_SCRAP, sprites -> new Provider(sprites, SCRAP));
		r.register(ModParticles.GRAVE_DUST, sprites -> new Provider(sprites, DUST));
		r.register(ModParticles.CURSE_EMBER, sprites -> new Provider(sprites, CURSE));
		r.register(ModParticles.EYE_TRAIL, sprites -> new Provider(sprites, EYE));
		// mesh VFX triggers (art 4.0): they start a MeshFx effect and add no particle
		r.register(ModParticles.FX_SHOCKWAVE, (ParticleProvider<SimpleParticleType>) (t, l, x, y, z, a, b, c) -> {
			MeshFx.shockwave(new Vec3(x, y, z), a > 0 ? (float) a : 4.0F);
			return null;
		});
		r.register(ModParticles.FX_RUNE_CIRCLE, (ParticleProvider<SimpleParticleType>) (t, l, x, y, z, a, b, c) -> {
			MeshFx.runeCircle(new Vec3(x, y, z), a > 0 ? (float) a : 3.0F, b > 0 ? (float) b : 1.5F);
			return null;
		});
		r.register(ModParticles.FX_CRACK, (ParticleProvider<SimpleParticleType>) (t, l, x, y, z, a, b, c) -> {
			MeshFx.crack(new Vec3(x, y, z), a > 0 ? (float) a : 3.0F);
			return null;
		});
		r.register(ModParticles.FX_PILLAR, (ParticleProvider<SimpleParticleType>) (t, l, x, y, z, a, b, c) -> {
			MeshFx.pillar(new Vec3(x, y, z), a > 0 ? (float) a : 0.8F, b > 0 ? (float) b : 4.0F, c > 0 ? (float) c : 1.2F);
			return null;
		});
		r.register(ModParticles.FX_BURST, (ParticleProvider<SimpleParticleType>) (t, l, x, y, z, a, b, c) -> {
			MeshFx.burst(new Vec3(x, y, z), a > 0 ? (float) a : 1.0F);
			return null;
		});
	}

	public static final class Fx extends TextureSheetParticle {
		private final SpriteSet sprites;
		private final Style style;
		private final float baseSize;
		private final float spin;
		private final float phase;

		Fx(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, Style style) {
			super(level, x, y, z, 0.0, 0.0, 0.0);
			this.sprites = sprites;
			this.style = style;
			this.xd = vx * style.keepVelocity();
			this.yd = vy * style.keepVelocity();
			this.zd = vz * style.keepVelocity();
			this.gravity = style.gravity();
			this.friction = style.friction();
			this.hasPhysics = style.physics();
			this.phase = this.random.nextFloat() * 6.2832F;
			this.lifetime = style.life() + (style.lifeJitter() > 0 ? this.random.nextInt(style.lifeJitter() + 1) : 0);
			this.baseSize = style.size() + this.random.nextFloat() * style.sizeJitter();
			this.quadSize = this.baseSize;
			this.spin = style.spin() * (this.random.nextBoolean() ? 1.0F : -1.0F);
			this.roll = this.oRoll = style.spin() > 0 || style == SLASH ? this.random.nextFloat() * 6.2832F : 0.0F;
			if (style.tint() != null) {
				this.setColor(style.tint()[0], style.tint()[1], style.tint()[2]);
			}
			if (style.random()) {
				this.pickSprite(sprites);
			} else {
				this.setSpriteFromAge(sprites);
			}
		}

		@Override
		public void tick() {
			super.tick();
			if (this.removed) {
				return;
			}
			if (this.style.physics() && this.onGround) {
				this.remove();   // a drop splashes, a feather or a scrap settles
				return;
			}
			if (this.style.sway() > 0.0F) {
				// a falling leaf's flutter: side to side across its fall, rolling with it
				float s = Mth.sin(this.age * 0.35F + this.phase);
				this.xd += s * this.style.sway() * Mth.cos(this.phase);
				this.zd += s * this.style.sway() * Mth.sin(this.phase);
			}
			if (!this.style.random()) {
				this.setSpriteFromAge(this.sprites);
			}
			this.oRoll = this.roll;
			this.roll += this.spin;
			if (this.style.fade()) {
				float left = 1.0F - (float) this.age / this.lifetime;
				this.alpha = Math.min(1.0F, left * 2.5F);
			}
		}

		@Override
		public float getQuadSize(float partial) {
			float t = Math.min(1.0F, (this.age + partial) / this.lifetime);
			return this.baseSize * (1.0F + (this.style.grow() - 1.0F) * t);
		}

		@Override
		public ParticleRenderType getRenderType() {
			return this.style.translucent() ? ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT : ParticleRenderType.PARTICLE_SHEET_OPAQUE;
		}

		@Override
		protected int getLightColor(float partial) {
			return 0xF000F0;
		}
	}

	public record Provider(SpriteSet sprites, Style style) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
			return new Fx(level, x, y, z, vx, vy, vz, this.sprites, this.style);
		}
	}
}
