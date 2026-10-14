package dev.remnants.bossbar;

import dev.remnants.net.BossStylePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * A boss bar that knows whose it is: before a player first sees it, the player's client is told its style (vanilla's
 * packet only carries a name, a colour and notches), so the client can frame it and play the boss's theme.
 */
public class StyledBossEvent extends ServerBossEvent {
	private final BossStyle style;

	public StyledBossEvent(Component name, BossBarColor color, BossBarOverlay overlay, BossStyle style) {
		super(name, color, overlay);
		this.style = style;
	}

	public BossStyle style() {
		return this.style;
	}

	@Override
	public void addPlayer(ServerPlayer player) {
		if (!this.getPlayers().contains(player) && ServerPlayNetworking.canSend(player, BossStylePayload.TYPE)) {
			ServerPlayNetworking.send(player, new BossStylePayload(this.getId(), this.style.ordinal()));
		}
		super.addPlayer(player);
	}
}
