package dev.remnants.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What an Epitaph remembers: who fell, how, where and when, how many levels they carried and how strong their echo
 * was. {@code unknown} epitaphs come out of Forgotten Graves: some adventurer from long ago, no day, no rank.
 */
public record EpitaphData(String name, Component cause, String dimension, BlockPos pos, int levels, int rank, long day, boolean unknown) {
	public static final Codec<EpitaphData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("name").forGetter(EpitaphData::name),
		ComponentSerialization.CODEC.fieldOf("cause").forGetter(EpitaphData::cause),
		Codec.STRING.optionalFieldOf("dimension", "minecraft:overworld").forGetter(EpitaphData::dimension),
		BlockPos.CODEC.fieldOf("pos").forGetter(EpitaphData::pos),
		Codec.INT.optionalFieldOf("levels", 0).forGetter(EpitaphData::levels),
		Codec.INT.optionalFieldOf("rank", 0).forGetter(EpitaphData::rank),
		Codec.LONG.optionalFieldOf("day", 0L).forGetter(EpitaphData::day),
		Codec.BOOL.optionalFieldOf("unknown", false).forGetter(EpitaphData::unknown)
	).apply(i, EpitaphData::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, EpitaphData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
