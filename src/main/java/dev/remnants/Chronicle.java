package dev.remnants;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.remnants.item.EpitaphData;
import dev.remnants.net.ChroniclePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The Chronicle: every death of a player, written down (kept in {@link RemnantTracker}, synced to the client for the
 * Codex's Chronicle chapter). Own deaths are written automatically (unless {@link RemnantsConfig#epitaphItemsForOwnDeaths}
 * hands them out as items, the 1.2.0 way); Epitaphs found in graves are inscribed by right-clicking them. Only the
 * newest {@link #LIMIT} entries are kept. The 1.5 Memorial will read its names from here.
 */
public final class Chronicle {
	public static final int LIMIT = 200;
	/** Entry sources: your own death, or an Epitaph item inscribed by hand. */
	public static final String DEATH = "death", INSCRIBED = "inscribed";

	/**
	 * One entry: the epitaph itself, where it came from and when (game time) it was written. New optional fields can
	 * be added to the codec later without breaking saved worlds.
	 */
	public record Entry(EpitaphData epitaph, String source, long recordedAt) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			EpitaphData.CODEC.fieldOf("epitaph").forGetter(Entry::epitaph),
			Codec.STRING.optionalFieldOf("source", DEATH).forGetter(Entry::source),
			Codec.LONG.optionalFieldOf("recorded_at", 0L).forGetter(Entry::recordedAt)
		).apply(i, Entry::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
	}

	public static void record(ServerPlayer player, EpitaphData data, String source) {
		RemnantTracker.get(player.server).addChronicle(player.getUUID(), new Entry(data, source, player.server.overworld().getGameTime()));
		sync(player);
	}

	public static List<Entry> entries(ServerPlayer player) {
		return RemnantTracker.get(player.server).chronicle(player.getUUID());
	}

	/** Sent on join and whenever an entry is written. */
	public static void sync(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, ChroniclePayload.TYPE)) {
			ServerPlayNetworking.send(player, new ChroniclePayload(List.copyOf(entries(player))));
		}
	}

	private Chronicle() {
	}
}
