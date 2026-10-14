package dev.remnants.client.bossbar;

import dev.remnants.RemnantsMod;
import dev.remnants.bossbar.BossStyle;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Each boss's own theme while its bar is up (the most important one when several are: {@link BossStyle} order): it
 * fades in over a second, loops, crossfades when another
 * boss takes over and fades out over two and a half seconds after the fight. Played on the Music slider; vanilla's
 * music waits meanwhile (MusicManagerMixin).
 */
public final class BossMusic {
	private static final List<Theme> THEMES = new ArrayList<>();

	private BossMusic() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(BossMusic::tick);
	}

	/** True while a theme plays or fades: vanilla's music manager sits it out. */
	public static boolean holdsMusic() {
		return !THEMES.isEmpty();
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null) {
			THEMES.forEach(t -> mc.getSoundManager().stop(t));
			THEMES.clear();
			return;
		}
		THEMES.removeIf(t -> t.isStopped() || (t.age > 40 && !mc.getSoundManager().isActive(t)));
		BossStyle want = BossBars.musicStyle();
		Theme playing = null;
		for (Theme t : THEMES) {
			if (t.style == want) {
				playing = t;
			} else {
				t.out = true;
			}
		}
		if (playing != null) {
			playing.out = false;   // the same boss again (back in range, a second bar): fade back in
		} else if (want != null) {
			SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(RemnantsMod.id(want.music));
			if (event != null) {
				if (THEMES.isEmpty()) {
					mc.getMusicManager().stopPlaying();
				}
				Theme theme = new Theme(want, event);
				THEMES.add(theme);
				mc.getSoundManager().play(theme);
			}
		}
	}

	private static final class Theme extends AbstractTickableSoundInstance {
		final BossStyle style;
		boolean out;
		int age;

		Theme(BossStyle style, SoundEvent event) {
			super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			this.style = style;
			this.looping = true;
			this.delay = 0;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.volume = 0.0F;
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			this.age++;
			if (this.out) {
				this.volume -= 1.0F / 50.0F;
				if (this.volume <= 0.0F) {
					this.volume = 0.0F;
					this.stop();
				}
			} else {
				this.volume = Math.min(1.0F, this.volume + 1.0F / 20.0F);
			}
		}
	}
}
