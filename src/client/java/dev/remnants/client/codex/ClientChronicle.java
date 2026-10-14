package dev.remnants.client.codex;

import dev.remnants.Chronicle;
import dev.remnants.net.ChroniclePayload;

import java.util.List;

/** The last Chronicle the server sent (for the Codex's Chronicle chapter); {@link #version} bumps on every update. */
public final class ClientChronicle {
	private static List<Chronicle.Entry> entries = List.of();
	private static int version;

	public static void set(ChroniclePayload payload) {
		entries = List.copyOf(payload.entries());
		version++;
	}

	/** Oldest first, as stored. */
	public static List<Chronicle.Entry> entries() {
		return entries;
	}

	public static int version() {
		return version;
	}

	private ClientChronicle() {
	}
}
