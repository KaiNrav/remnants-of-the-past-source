package dev.remnants.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;

import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * The trophy of a biome Forgotten Remnant set into a Bone Chakram (crafting grid: chakram + trophy; a new trophy
 * replaces the old one). Each gives the throw that Remnant's signature.
 */
public enum ChakramInlay implements StringRepresentable {
	/** Soul Frost (Frozen Remnant): hit targets are slowed and start to freeze. */
	FROST("frost", () -> dev.remnants.registry.ModItems.SOUL_FROST),
	/** Wet Shroud (Drowned Remnant): hit targets are dragged toward you, like the harpoon. */
	TIDE("tide", () -> dev.remnants.registry.ModItems.WET_SHROUD),
	/** Scarab Seal (Sandy Remnant): the first hit ricochets into a second target. */
	SCARAB("scarab", () -> dev.remnants.registry.ModItems.SCARAB_SEAL);

	public static final Codec<ChakramInlay> CODEC = StringRepresentable.fromEnum(ChakramInlay::values);
	private static final IntFunction<ChakramInlay> BY_ID = i -> values()[Math.floorMod(i, values().length)];
	public static final StreamCodec<ByteBuf, ChakramInlay> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);

	private final String key;
	private final Supplier<Item> trophy;

	ChakramInlay(String key, Supplier<Item> trophy) {
		this.key = key;
		this.trophy = trophy;
	}

	@Override
	public String getSerializedName() {
		return this.key;
	}

	public Item trophy() {
		return this.trophy.get();
	}

	public static ChakramInlay byTrophy(Item item) {
		for (ChakramInlay inlay : values()) {
			if (inlay.trophy() == item) {
				return inlay;
			}
		}
		return null;
	}
}
