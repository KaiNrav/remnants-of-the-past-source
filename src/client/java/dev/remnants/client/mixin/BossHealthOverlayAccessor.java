package dev.remnants.client.mixin;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The boss bars on screen (for client/bossbar: which to frame, which theme to play) and vanilla's own bar drawing. */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {
	@Accessor("events")
	Map<UUID, LerpingBossEvent> remnants$events();

	@Invoker("drawBar")
	void remnants$drawBar(GuiGraphics graphics, int x, int y, BossEvent event);
}
