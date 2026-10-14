package dev.remnants.client.mixin;

import dev.remnants.client.bossbar.BossBars;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While one of the mod's bosses has a bar up, the boss bars are drawn by {@link BossBars} (each in its own frame). */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void remnants$styledBars(GuiGraphics graphics, CallbackInfo ci) {
		if (BossBars.render(graphics, (BossHealthOverlay) (Object) this)) {
			ci.cancel();
		}
	}
}
