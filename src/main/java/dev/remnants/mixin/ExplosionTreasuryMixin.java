package dev.remnants.mixin;

import dev.remnants.world.Treasuries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A blast spares the shell of a sealed treasury ({@link Treasuries}): TNT is no key. Fabric has no explosion event. */
@Mixin(Explosion.class)
public abstract class ExplosionTreasuryMixin {
	@Shadow
	@Final
	private Level level;

	@Inject(method = "finalizeExplosion", at = @At("HEAD"))
	private void remnants$spareTreasuries(boolean particles, CallbackInfo ci) {
		if (this.level instanceof ServerLevel server && !((Explosion) (Object) this).getToBlow().isEmpty()) {
			((Explosion) (Object) this).getToBlow().removeIf(pos -> Treasuries.isSealed(server, pos));
		}
	}
}
