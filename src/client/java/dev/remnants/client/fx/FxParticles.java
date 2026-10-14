package dev.remnants.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Art 2.0 impact particles: bone chips struck off by a blow, and puffs of grave dust. */
public final class FxParticles {
	private FxParticles() {
	}

	/** A chip of bone flying off a blow: fast, falls, white-hot -> bone -> a soul ember over its short life. */
	public static final class BoneSpark extends TextureSheetParticle {
		private final SpriteSet sprites;

		BoneSpark(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
			super(level, x, y, z, vx, vy, vz);
			this.sprites = sprites;
			this.xd = vx;
			this.yd = vy;
			this.zd = vz;
			this.gravity = 0.9F;
			this.friction = 0.9F;
			this.lifetime = 7 + this.random.nextInt(7);
			this.quadSize = 0.07F + this.random.nextFloat() * 0.05F;
			this.setSpriteFromAge(sprites);
		}

		@Override
		public void tick() {
			super.tick();
			if (!this.removed) {
				this.setSpriteFromAge(this.sprites);
			}
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
		}

		@Override
		protected int getLightColor(float partial) {
			return 0xF000F0;
		}
	}

	/** A puff of grave dust: drifts out and up, grows, thins out. */
	public static final class SoulDust extends TextureSheetParticle {
		private final SpriteSet sprites;
		private final float grow;

		SoulDust(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
			super(level, x, y, z, vx, vy, vz);
			this.sprites = sprites;
			this.xd = vx;
			this.yd = vy;
			this.zd = vz;
			this.gravity = -0.01F;
			this.friction = 0.86F;
			this.lifetime = 14 + this.random.nextInt(10);
			this.quadSize = 0.18F + this.random.nextFloat() * 0.12F;
			this.grow = 1.6F + this.random.nextFloat() * 0.6F;
			this.roll = this.oRoll = this.random.nextFloat() * 6.28F;
			this.setSpriteFromAge(sprites);
		}

		@Override
		public void tick() {
			super.tick();
			if (!this.removed) {
				this.setSpriteFromAge(this.sprites);
				this.alpha = 1.0F - (float) this.age / this.lifetime * 0.6F;
			}
		}

		@Override
		public float getQuadSize(float partial) {
			return this.quadSize * (1.0F + (this.grow - 1.0F) * Math.min(1.0F, (this.age + partial) / this.lifetime));
		}

		@Override
		public ParticleRenderType getRenderType() {
			return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
		}
	}

	public record SparkProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
			return new BoneSpark(level, x, y, z, vx, vy, vz, this.sprites);
		}
	}

	public record DustProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
			return new SoulDust(level, x, y, z, vx, vy, vz, this.sprites);
		}
	}
}
