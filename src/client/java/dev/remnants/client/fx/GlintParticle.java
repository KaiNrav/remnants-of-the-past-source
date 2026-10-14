package dev.remnants.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** The wind-up glint: a four-pointed soul star that flashes open and shut in 7 ticks.
 * The first velocity component carries its size (1 = about a third of a block). */
public class GlintParticle extends TextureSheetParticle {
	private final SpriteSet sprites;
	private final float size;

	protected GlintParticle(ClientLevel level, double x, double y, double z, double size, SpriteSet sprites) {
		super(level, x, y, z);
		this.sprites = sprites;
		this.size = (float) (size <= 0 ? 1.0 : size);
		this.lifetime = 7;
		this.gravity = 0.0F;
		this.hasPhysics = false;
		this.xd = this.yd = this.zd = 0;
		this.roll = this.oRoll = 0.0F;
		this.setSpriteFromAge(sprites);
		this.quadSize = 0.0F;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.removed) {
			this.setSpriteFromAge(this.sprites);
		}
	}

	@Override
	public float getQuadSize(float partial) {
		float t = (this.age + partial) / this.lifetime;
		float open = t < 0.3F ? t / 0.3F : 1.0F - (t - 0.3F) / 0.7F;
		return 0.33F * this.size * Math.max(0.0F, open);
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
			return new GlintParticle(level, x, y, z, vx, this.sprites);
		}
	}
}
