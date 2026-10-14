package dev.remnants.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A small full-bright soul wisp that drifts upward and fades out. */
public class RemnantSoulParticle extends TextureSheetParticle {
	private static final float START_ALPHA = 0.85F;
	private final SpriteSet sprites;

	protected RemnantSoulParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
		super(level, x, y, z);
		this.sprites = sprites;
		this.xd = vx * 0.3 + (this.random.nextDouble() - 0.5) * 0.02;
		this.yd = vy * 0.3 + 0.012 + this.random.nextDouble() * 0.02;
		this.zd = vz * 0.3 + (this.random.nextDouble() - 0.5) * 0.02;
		this.lifetime = 18 + this.random.nextInt(18);
		this.quadSize = 0.07F + this.random.nextFloat() * 0.05F;
		this.alpha = START_ALPHA;
		this.gravity = 0.0F;
		this.hasPhysics = false;
	}

	@Override
	public void tick() {
		super.tick();
		this.alpha = START_ALPHA * (1.0F - (float) this.age / this.lifetime);
		if (!this.removed) {
			this.setSpriteFromAge(this.sprites);   // art 2.0: the wisp gutters out over 4 frames
		}
		this.xd *= 0.94;
		this.zd *= 0.94;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	protected int getLightColor(float partialTick) {
		return 0xF000F0;
	}

	public static class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
			RemnantSoulParticle particle = new RemnantSoulParticle(level, x, y, z, vx, vy, vz, this.sprites);
			particle.setSpriteFromAge(this.sprites);
			return particle;
		}
	}
}
