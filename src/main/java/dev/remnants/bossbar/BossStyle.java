package dev.remnants.bossbar;

/**
 * Whose boss bar it is: the client draws each in its own frame and plays its
 * own theme. The order is the music's priority when
 * several bars are up at once (the King over everyone, the Night of Memory under any boss). Sent by ordinal.
 */
public enum BossStyle {
	KING("music.boss_king"),
	ARCHIVIST("music.boss_archivist"),
	WARDEN("music.boss_warden"),
	ECHO("music.boss_echo"),
	NIGHT("music.night_of_memory"),
	/** A Remnant's poise, drawn as a thin bar under its own; no music of its own. */
	POISE(null);

	/** The theme's sound event path (remnants_of_the_past:...), or null for none. */
	public final String music;

	BossStyle(String music) {
		this.music = music;
	}

	public String id() {
		return this.name().toLowerCase(java.util.Locale.ROOT);
	}

	public static BossStyle byOrdinal(int i) {
		BossStyle[] all = values();
		return i >= 0 && i < all.length ? all[i] : null;
	}
}
