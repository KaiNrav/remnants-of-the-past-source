package dev.remnants.client.codex;

import dev.remnants.net.ResonancePayload;
import net.minecraft.client.Minecraft;

/** The last Soul Resonance the server sent (for the Codex's Path of the Soul pages). */
public final class ClientResonance {
	private static ResonancePayload data = ResonancePayload.EMPTY;
	private static long receivedAt;

	public static void set(ResonancePayload payload) {
		data = payload;
		Minecraft mc = Minecraft.getInstance();
		receivedAt = mc.level != null ? mc.level.getGameTime() : 0;
	}

	public static ResonancePayload get() {
		return data;
	}

	/** Ticks until Last Breath is ready again, counted down locally since the last sync. */
	public static int lastBreathTicks() {
		Minecraft mc = Minecraft.getInstance();
		long since = mc.level != null ? mc.level.getGameTime() - receivedAt : 0;
		return (int) Math.max(0, data.lastBreathTicks() - since);
	}

	private ClientResonance() {
	}
}
