package dev.remnants.registry;

import dev.remnants.RemnantsMod;
import dev.remnants.item.ChakramInlay;
import dev.remnants.item.EpitaphData;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

public final class ModComponents {
	/** The death an Epitaph remembers. */
	public static final DataComponentType<EpitaphData> EPITAPH = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		RemnantsMod.id("epitaph"), DataComponentType.<EpitaphData>builder().persistent(EpitaphData.CODEC)
			.networkSynchronized(EpitaphData.STREAM_CODEC).cacheEncoding().build());
	/** Soul Dowser needle (0 nothing, 1 far, 2 near, 3 right above a grave). Synced to the client, never saved. */
	public static final DataComponentType<Integer> DOWSE_SIGNAL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		RemnantsMod.id("dowse_signal"), DataComponentType.<Integer>builder().networkSynchronized(ByteBufCodecs.VAR_INT).build());
	/** The trophy set into a Bone Chakram. */
	public static final DataComponentType<ChakramInlay> CHAKRAM_INLAY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		RemnantsMod.id("chakram_inlay"), DataComponentType.<ChakramInlay>builder().persistent(ChakramInlay.CODEC)
			.networkSynchronized(ChakramInlay.STREAM_CODEC).build());

	/** How well the Soul Crucible forged an item (1.3). */
	public static final DataComponentType<dev.remnants.item.ForgeQuality> FORGE_QUALITY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		RemnantsMod.id("forge_quality"), DataComponentType.<dev.remnants.item.ForgeQuality>builder().persistent(dev.remnants.item.ForgeQuality.CODEC)
			.networkSynchronized(dev.remnants.item.ForgeQuality.STREAM_CODEC).build());

	// ---- 1.5 Archive
	/** Sealed with a Seal of Memory in the Soul Crucible: the item crosses over with you when you die. */
	public static final DataComponentType<net.minecraft.util.Unit> REMEMBERED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		RemnantsMod.id("remembered"), DataComponentType.<net.minecraft.util.Unit>builder().persistent(net.minecraft.util.Unit.CODEC)
			.networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(net.minecraft.util.Unit.INSTANCE)).build());
	/** The creature a Soul Lantern holds (its saved data, "id" included). */
	public static final DataComponentType<net.minecraft.world.item.component.CustomData> CAPTURED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		RemnantsMod.id("captured"), DataComponentType.<net.minecraft.world.item.component.CustomData>builder()
			.persistent(net.minecraft.world.item.component.CustomData.CODEC)
			.networkSynchronized(net.minecraft.world.item.component.CustomData.STREAM_CODEC).build());
	/** Game time an Archivist's Quill last wrote. */
	public static final DataComponentType<Long> QUILL_WRITTEN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		RemnantsMod.id("quill_written"), DataComponentType.<Long>builder().persistent(com.mojang.serialization.Codec.LONG)
			.networkSynchronized(ByteBufCodecs.VAR_LONG).build());

	public static void init() {
	}

	private ModComponents() {
	}
}
