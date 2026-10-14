package dev.remnants.registry;

import dev.remnants.RemnantsMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * The mod's own sounds (assets/remnants_of_the_past/sounds + sounds.json). The combat cues play on the client (CombatFx: wind-up when a telegraph appears, a swing when it
 * runs out, a hit / a ground impact when a blow lands, a ting with a glint); the rest are played by their mobs.
 */
public final class ModSounds {
	public static final SoundEvent COMBAT_WINDUP = register("combat.windup");
	public static final SoundEvent COMBAT_SWING_HEAVY = register("combat.swing_heavy");
	public static final SoundEvent COMBAT_SWING_LIGHT = register("combat.swing_light");
	public static final SoundEvent COMBAT_HIT = register("combat.hit");
	public static final SoundEvent COMBAT_IMPACT = register("combat.impact");
	public static final SoundEvent COMBAT_GLINT = register("combat.glint");
	public static final SoundEvent MIMIC_SNAP = register("entity.grave_mimic.snap");
	public static final SoundEvent KEENER_WAIL = register("entity.keener.wail");
	// boss themes (bossbar.BossStyle): streamed loops in sounds/music
	public static final SoundEvent MUSIC_KING = register("music.boss_king");
	public static final SoundEvent MUSIC_ARCHIVIST = register("music.boss_archivist");
	public static final SoundEvent MUSIC_WARDEN = register("music.boss_warden");
	public static final SoundEvent MUSIC_ECHO = register("music.boss_echo");
	public static final SoundEvent MUSIC_NIGHT = register("music.night_of_memory");

	private ModSounds() {
	}

	public static void init() {
	}

	private static SoundEvent register(String name) {
		ResourceLocation id = RemnantsMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
