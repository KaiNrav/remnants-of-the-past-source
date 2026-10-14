package dev.remnants.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.remnants.block.SpiritAltarBlock;
import dev.remnants.client.craft.CraftFx;
import dev.remnants.entity.SoulKingEntity;
import dev.remnants.registry.ModBlocks;
import dev.remnants.registry.ModItems;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The summoning, drawn around the King while his intro runs (t = ticks since the offering; the server plays the sounds):
 * 0-20 the Condensed Soul rises over the table; 20-60 the pedestals beam into it one by one; 60-85 a vortex of soul
 * shards spirals in; 85-100 a pillar of light; 100-120 the King steps out of it as it fades.
 */
final class SoulKingRitual {
	private static final ItemStack OFFERING = new ItemStack(ModItems.CONDENSED_SOUL);
	private static final ItemStack SHARD = new ItemStack(ModItems.SOUL_FRAGMENT);
	private static final int SOUL = 0x50DCFF, FULL = LightTexture.FULL_BRIGHT;

	static void render(SoulKingEntity king, float t, float partial, PoseStack pose, MultiBufferSource buffers) {
		Level level = king.level();
		float time = king.tickCount + partial;
		Vec3 origin = new Vec3(Mth.lerp(partial, king.xo, king.getX()), Mth.lerp(partial, king.yo, king.getY()), Mth.lerp(partial, king.zo, king.getZ()));
		float offerY = 0.15F + 1.35F * CraftFx.ease(t / 20.0F) + 0.05F * Mth.sin(time * 0.2F);
		// the offering, until the pillar swallows it
		if (t < 90) {
			float s = t < 85 ? 0.8F : 0.8F * (1.0F - (t - 85.0F) / 5.0F);
			CraftFx.floatingItem(OFFERING, pose, buffers, level, 0, offerY, 0, s, time * 6.0F, FULL);
		}
		// pedestal beams
		if (t >= 20 && t < 88) {
			BlockPos altar = king.blockPosition().below();
			if (level.getBlockState(altar).is(ModBlocks.SPIRIT_ALTAR)) {
				List<BlockPos> pedestals = SpiritAltarBlock.findPedestals(level, altar, new ArrayList<>());
				VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(CraftFx.POUR));
				for (int k = 0; k < pedestals.size(); k++) {
					float in = CraftFx.ease((t - 20.0F - k * 10.0F) / 5.0F);
					if (in <= 0) {
						continue;
					}
					Vec3 from = Vec3.atCenterOf(pedestals.get(k)).add(0, 0.9, 0).subtract(origin);
					float w = (0.09F + 0.03F * Mth.sin(time * 0.7F + k)) * in;
					int alpha = (int) (220 * in * (t > 82 ? (88.0F - t) / 6.0F : 1.0F));
					CraftFx.ribbon(vc, pose, (float) from.x, (float) from.y, (float) from.z, 0, offerY, 0, w, -time * 0.15F, -time * 0.15F + 3.0F,
						alpha << 24 | 0xFFFFFF, FULL);
				}
			}
		}
		// the vortex of shards
		if (t >= 60 && t < 87) {
			float p = Mth.clamp((t - 60.0F) / 25.0F, 0.0F, 1.0F);
			for (int i = 0; i < 14; i++) {
				double a = Math.PI * 2 * i / 14 + p * p * Math.PI * 7 + time * 0.05;
				float r = 3.2F * (1.0F - CraftFx.ease(p)) + 0.15F;
				float y = offerY - 0.5F + (i % 3) * 0.35F * (1.0F - p) + 0.3F * Mth.sin((float) a);
				CraftFx.floatingItem(SHARD, pose, buffers, level, (float) Math.cos(a) * r, y, (float) Math.sin(a) * r, 0.5F * (1.0F - p * 0.6F),
					(float) Math.toDegrees(a), FULL);
			}
		}
		// the pillar of light: grows, holds, then fades as the King steps out of it
		if (t >= 85) {
			float grow = CraftFx.ease((t - 85.0F) / 10.0F);
			float fade = t < SoulKingEntity.EMERGE_TICK ? 1.0F : 1.0F - CraftFx.ease((t - SoulKingEntity.EMERGE_TICK) / 20.0F);
			float r = 1.4F * grow;
			if (fade > 0.01F && r > 0.01F) {
				VertexConsumer beam = buffers.getBuffer(RenderType.beaconBeam(CraftFx.BEAM, true));
				float scroll = -time * 0.05F;
				CraftFx.column(beam, pose, 0, 0, -0.2F, 14.0F, r, scroll, (int) (120 * fade) << 24 | SOUL);
				CraftFx.column(beam, pose, 0, 0, -0.2F, 14.0F, r * 0.35F, scroll * 2.0F, (int) (230 * fade) << 24 | 0xC8F8FF);
			}
		}
	}

	/** How the King himself is drawn during the summoning: not at all before he emerges, then growing out of the light. */
	static float kingScale(float t) {
		if (t < SoulKingEntity.EMERGE_TICK) {
			return 0.0F;
		}
		return 0.75F + 0.25F * CraftFx.ease((t - SoulKingEntity.EMERGE_TICK) / 16.0F);
	}

	private SoulKingRitual() {
	}
}
