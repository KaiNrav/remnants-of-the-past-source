package dev.remnants.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.remnants.block.OssuaryGateBlock;
import dev.remnants.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * An opening Ossuary Gate's portcullis rising into its lintel (OssuaryGateBlock.EVENT_LIFT): the server has already
 * cleared the column under its top block, so for a moment the closed bars are drawn here instead, climbing on the chain
 * - a jolt, a heavy ease, a rattle - until their spiked foot sits where the open top block shows it. What rises past
 * the top block goes into the stone above and the depth test hides it.
 */
public final class GateLift {
	private static final class Lift {
		final BlockPos top;
		final Direction.Axis axis;
		final int height;
		final float start, ticks;
		int clank = -1;

		Lift(BlockPos top, Direction.Axis axis, int height, float start, float ticks) {
			this.top = top;
			this.axis = axis;
			this.height = height;
			this.start = start;
			this.ticks = ticks;
		}
	}

	private static final List<Lift> LIVE = new ArrayList<>();
	private static ClientLevel liveIn;
	/** Where the raised foot sits in the open top block (its model's bars start 13 px up). */
	private static final float FOOT = 13.0F / 16.0F;

	private GateLift() {
	}

	public static void register() {
		OssuaryGateBlock.clientLift = GateLift::start;
		WorldRenderEvents.AFTER_ENTITIES.register(GateLift::render);
	}

	static void start(BlockPos top, Direction.Axis axis, int height) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || LIVE.size() > 64) {
			return;
		}
		if (level != liveIn) {
			LIVE.clear();
			liveIn = level;
		}
		LIVE.add(new Lift(top.immutable(), axis, height, MeshFx.now(0.0F), 12.0F + 9.0F * height));
	}

	/** Blocks risen at {@code t} (0..1 of the lift): a jolt as the chain takes the weight, a heavy ease, a rattle. */
	static float risen(float t, int height, float age) {
		float travel = height - 1 + FOOT;
		float jolt = t < 0.12F ? Mth.sin(t / 0.12F * Mth.PI) * 0.06F : 0.0F;
		float ease = t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
		float rattle = Mth.sin(age * 2.7F) * 0.012F * Mth.sin(t * Mth.PI);
		return travel * ease + jolt + rattle;
	}

	private static void render(WorldRenderContext ctx) {
		if (LIVE.isEmpty()) {
			return;
		}
		PoseStack ps = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		ClientLevel level = Minecraft.getInstance().level;
		if (ps == null || buffers == null || level == null || level != liveIn) {
			LIVE.clear();
			return;
		}
		Vec3 cam = ctx.camera().getPosition();
		float now = MeshFx.now(ctx.tickCounter().getGameTimeDeltaPartialTick(false));
		var blocks = Minecraft.getInstance().getBlockRenderer();
		Iterator<Lift> it = LIVE.iterator();
		while (it.hasNext()) {
			Lift l = it.next();
			float age = now - l.start;
			if (age < 0.0F || age >= l.ticks || !level.getBlockState(l.top).is(ModBlocks.OSSUARY_GATE)) {
				it.remove();
				continue;
			}
			float t = age / l.ticks;
			// the chain rattling over its wheel: a step every few ticks while it climbs (the server played the opening)
			int step = (int) (age / 4.0F);
			if (step != l.clank) {
				l.clank = step;
				if (step > 0) {
					level.playLocalSound(l.top, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.4F, 0.55F + 0.06F * (step % 3), false);
				}
			}
			float risen = risen(t, l.height, age);
			int bottom = l.top.getY() - (l.height - 1);
			for (int i = 0; i < l.height; i++) {
				double y = bottom + i + risen;
				if (y >= l.top.getY() + 1.0) {
					continue;   // wholly in the lintel
				}
				BlockState bars = ModBlocks.OSSUARY_GATE.defaultBlockState().setValue(OssuaryGateBlock.AXIS, l.axis)
					.setValue(OssuaryGateBlock.OPEN, false).setValue(OssuaryGateBlock.TIP, i == 0);
				int light = LevelRenderer.getLightColor(level, BlockPos.containing(l.top.getX() + 0.5, y + 0.5, l.top.getZ() + 0.5));
				ps.pushPose();
				ps.translate(l.top.getX() - cam.x, y - cam.y, l.top.getZ() - cam.z);
				blocks.renderSingleBlock(bars, ps, buffers, light, OverlayTexture.NO_OVERLAY);
				ps.popPose();
			}
		}
	}
}
