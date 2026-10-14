package dev.remnants.world;

import dev.remnants.RemnantsMod;
import dev.remnants.block.OssuaryGateBlock;
import dev.remnants.registry.ModBlocks;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Sealed treasuries: while an Ossuary Gate is shut, the walls, floor and ceiling of the room behind it cannot be broken,
 * by a pick or a blast, so the only way in is the way the crypt means (the Crypt Eyes, the Ossuary Key, the Archivist's
 * death). Before this a player simply dug round the gate. Only the mod's own generated structures are warded (found
 * through the chunk's structure references), creative players are not stopped, and once the gate is open the room is
 * ordinary stone again.
 */
public final class Treasuries {
	/** A treasury in its template's own coordinates: the room with its shell, and the gate's 3 x 3. */
	private record Vault(String template, BoundingBox room, BlockPos gateFrom, BlockPos gateTo) {
	}

	private static final List<Vault> VAULTS = List.of(
		// vault(): the vault behind the antechamber's far wall (z 12-13) - hall x 0..14, z 11..22, top 8
		new Vault("ossuary/vault", new BoundingBox(0, 0, 12, 14, 8, 22), new BlockPos(6, 1, 12), new BlockPos(8, 3, 12)),
		// warden_hall(): the treasury hall x 6..18, z 29..36, top 7; its gate in the hall's back wall z 29
		new Vault("ossuary/warden_hall", new BoundingBox(6, 0, 29, 18, 7, 36), new BlockPos(11, 1, 29), new BlockPos(13, 3, 29)),
		// library_hall(): the room shell x 7..15, z 22..27, roof at 5; its gate in the hall's far wall z 22
		new Vault("ossuary/library_hall", new BoundingBox(7, 0, 22, 15, 5, 27), new BlockPos(10, 1, 22), new BlockPos(12, 3, 22)));

	private Treasuries() {
	}

	public static void register() {
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register(Treasuries::allowBreak);
	}

	private static boolean allowBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity be) {
		if (!(level instanceof ServerLevel server) || player.getAbilities().instabuild || !isSealed(server, pos)) {
			return true;
		}
		player.displayClientMessage(Component.translatable("message.remnants.treasury_warded").withStyle(ChatFormatting.DARK_AQUA), true);
		return false;
	}

	/** True if the block is part of a treasury of the mod's structures whose gate is still shut. */
	public static boolean isSealed(ServerLevel level, BlockPos pos) {
		if (!level.structureManager().hasAnyStructureAt(pos)) {
			return false;
		}
		var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
		List<StructureStart> starts = level.structureManager().startsForStructure(new ChunkPos(pos), s -> {
			ResourceLocation key = registry.getKey(s);
			return key != null && key.getNamespace().equals(RemnantsMod.MOD_ID);
		});
		for (StructureStart start : starts) {
			for (StructurePiece piece : start.getPieces()) {
				if (piece instanceof PoolElementStructurePiece p && p.getBoundingBox().isInside(pos)) {
					Vault v = vaultOf(p);
					if (v != null && world(p, v.room()).isInside(pos) && gateShut(level, p, v)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static Vault vaultOf(PoolElementStructurePiece piece) {
		String element = piece.getElement().toString();   // "Single[Left[remnants_of_the_past:ossuary/vault]]"
		for (Vault v : VAULTS) {
			if (element.contains(RemnantsMod.MOD_ID + ":" + v.template() + "]")) {
				return v;
			}
		}
		return null;
	}

	/** Any bar of the gate still down. An opened gate keeps only its top row, raised (OPEN). */
	private static boolean gateShut(ServerLevel level, PoolElementStructurePiece piece, Vault v) {
		for (BlockPos local : BlockPos.betweenClosed(v.gateFrom(), v.gateTo())) {
			BlockState s = level.getBlockState(world(piece, local));
			if (s.is(ModBlocks.OSSUARY_GATE) && !s.getValue(OssuaryGateBlock.OPEN)) {
				return true;
			}
		}
		return false;
	}

	/** Template coordinates to the world, the way a jigsaw piece placed them (turned about its origin). */
	private static BlockPos world(PoolElementStructurePiece piece, BlockPos local) {
		return piece.getPosition().offset(StructureTemplate.transform(local, Mirror.NONE, piece.getRotation(), BlockPos.ZERO));
	}

	private static BoundingBox world(PoolElementStructurePiece piece, BoundingBox local) {
		BlockPos a = world(piece, new BlockPos(local.minX(), local.minY(), local.minZ()));
		BlockPos b = world(piece, new BlockPos(local.maxX(), local.maxY(), local.maxZ()));
		return BoundingBox.fromCorners(a, b);
	}
}
