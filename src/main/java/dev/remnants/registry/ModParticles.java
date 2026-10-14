package dev.remnants.registry;

import dev.remnants.RemnantsMod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModParticles {
	public static final SimpleParticleType REMNANT_SOUL = Registry.register(BuiltInRegistries.PARTICLE_TYPE, RemnantsMod.id("remnant_soul"),
		FabricParticleTypes.simple());
	/** A four-pointed flash of soul light on a weapon or visor when a wind-up starts (art 2.0 hit feel). */
	public static final SimpleParticleType GLINT = Registry.register(BuiltInRegistries.PARTICLE_TYPE, RemnantsMod.id("glint"),
		FabricParticleTypes.simple(true));
	/** Art 2.0: chips of bone struck off by a blow, and puffs of grave dust where it lands. */
	public static final SimpleParticleType BONE_SPARK = Registry.register(BuiltInRegistries.PARTICLE_TYPE, RemnantsMod.id("bone_spark"),
		FabricParticleTypes.simple());
	public static final SimpleParticleType SOUL_DUST = Registry.register(BuiltInRegistries.PARTICLE_TYPE, RemnantsMod.id("soul_dust"),
		FabricParticleTypes.simple());

	// ---- 2.0 VFX ----------------------------------
	/** A soul bursting: a white-hot point, a ring that breaks into arcs and motes. */
	public static final SimpleParticleType SOUL_BURST = vfx("soul_burst");
	/** A big pulse ring racing outward (slams, phase shockwaves). */
	public static final SimpleParticleType SHOCK_RING = vfx("shock_ring");
	/** A blade's crescent smear. */
	public static final SimpleParticleType SOUL_SLASH = vfx("soul_slash");
	/** A flickering soul flame that rises a little. */
	public static final SimpleParticleType SOUL_FLAME = vfx("soul_flame");
	/** A rising ember that shrinks to a point. */
	public static final SimpleParticleType SOUL_EMBER = vfx("soul_ember");
	/** A golden star twinkling open and shut (a stagger's dizzy stars, a formed multiblock). */
	public static final SimpleParticleType TWINKLE = vfx("twinkle");
	/** A glowing glyph that floats up and fades (one of eight, picked at random). */
	public static final SimpleParticleType RUNE = vfx("rune");
	/** A ghost's skull: glimmers in, burns, comes apart upward. */
	public static final SimpleParticleType SKULL_WISP = vfx("skull_wisp");
	/** A puff of soul-tinted smoke. */
	public static final SimpleParticleType SOUL_SMOKE = vfx("soul_smoke");
	/** Vanilla's explosion told in soul fire. */
	public static final SimpleParticleType SOUL_EXPLOSION = vfx("soul_explosion");


	// ---- art 4.0: the mobs' own life (ambient, so the particle setting may thin them out) ---------------------------
	/** A curl of ghost vapour off a veil, a hem, a plume. */
	public static final SimpleParticleType SOUL_WISP = ambient("soul_wisp");
	/** A drop of soul that beads and falls (a blade's edge, a Keener's tears, a Mimic's drool). */
	public static final SimpleParticleType SOUL_DRIP = ambient("soul_drip");
	/** A crow's feather, rocking down. */
	public static final SimpleParticleType FEATHER = ambient("feather");
	/** A torn scrap of a written page, tumbling. */
	public static final SimpleParticleType PAGE_SCRAP = ambient("page_scrap");
	/** Grave dust kicked up by a step or a landing. */
	public static final SimpleParticleType GRAVE_DUST = ambient("grave_dust");
	/** A violet ember (the Archivist's ink and curses). */
	public static final SimpleParticleType CURSE_EMBER = ambient("curse_ember");
	/** A glowing eye's afterimage: soul-ember frames that stay where they were left and shrink (eye trails). */
	public static final SimpleParticleType EYE_TRAIL = ambient("eye_trail");

	// ---- art 4.0 mesh VFX triggers: no sprite of their own - the client starts a MeshFx effect (client/fx/MeshFx). Sent with
	// count 0, the "speed" vector carries the parameters: x = size (radius), y = seconds / height, z = spare.
	public static final SimpleParticleType FX_SHOCKWAVE = vfx("fx_shockwave");
	public static final SimpleParticleType FX_RUNE_CIRCLE = vfx("fx_rune_circle");
	public static final SimpleParticleType FX_CRACK = vfx("fx_crack");
	public static final SimpleParticleType FX_PILLAR = vfx("fx_pillar");
	public static final SimpleParticleType FX_BURST = vfx("fx_burst");

	private static SimpleParticleType ambient(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, RemnantsMod.id(name), FabricParticleTypes.simple(false));
	}

	private static SimpleParticleType vfx(String name) {
		// always shown: these mark the big moments of a fight and must not be culled by the particle setting
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, RemnantsMod.id(name), FabricParticleTypes.simple(true));
	}

	public static void init() {
	}

	private ModParticles() {
	}
}
