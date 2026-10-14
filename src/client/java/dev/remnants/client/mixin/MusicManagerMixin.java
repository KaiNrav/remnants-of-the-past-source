package dev.remnants.client.mixin;

import dev.remnants.client.bossbar.BossMusic;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla's music waits while a boss theme plays (and picks up again after it fades out). */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void remnants$bossTheme(CallbackInfo ci) {
		if (BossMusic.holdsMusic()) {
			ci.cancel();
		}
	}
}
