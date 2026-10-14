package dev.remnants.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.remnants.client.fx.CombatFx;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera shake from heavy blows (art 2.0 hit feel): the view and the hand get the same small rotation right after
 * vanilla's hurt tilt, so a slam next to you is felt as well as seen. Strength lives in {@link CombatFx}; off with
 * {@code screenShake: 0} in config/remnants_of_the_past.json. Fabric has no camera hook.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererShakeMixin {
	@Inject(method = "bobHurt", at = @At("TAIL"))
	private void remnants$shake(PoseStack poseStack, float partialTick, CallbackInfo ci) {
		CombatFx.applyShake(poseStack, partialTick);
	}
}
