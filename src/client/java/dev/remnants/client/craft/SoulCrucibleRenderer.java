package dev.remnants.client.craft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.remnants.RemnantsMod;
import dev.remnants.block.entity.SoulCrucibleBlockEntity;
import dev.remnants.multiblock.MultiblockPattern;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

import static dev.remnants.block.entity.SoulCrucibleBlockEntity.BEATS;
import static dev.remnants.block.entity.SoulCrucibleBlockEntity.BEAT_TICKS;
import static dev.remnants.block.entity.SoulCrucibleBlockEntity.LEAD_TICKS;

/**
 * The Soul Crucible at work (see {@link SoulCrucibleBlockEntity} for the phases). Drawn in the structure's own frame
 * (rotation 0: front to the north, cauldron front-left, anvil front-right under the trough's spout, bellows west of the
 * hearth): the pot tipping on its axle, the melt, the pour down the trough onto the anvil, the glowing billet, the rhythm
 * mark sweeping the anvil with each strike's verdict, the quench and its steam, and the bellows' board pumping.
 */
public class SoulCrucibleRenderer implements BlockEntityRenderer<SoulCrucibleBlockEntity> {
	public static final ResourceLocation POT = RemnantsMod.id("block/soul_crucible_pot");
	public static final ResourceLocation POT_FORMED = RemnantsMod.id("block/soul_crucible_pot_formed");
	public static final ResourceLocation BELLOWS_TOP = RemnantsMod.id("block/soul_bellows_top");
	private static final int C_GOLD = 0xFFE3B95E, C_ACCENT = 0xFF56E1E6, C_WARN = 0xFFEB7A6E, C_MUTED = 0xFF655C80, C_SURFACE = 0xC01E1929;
	/** Path of the pour (rotation-0 frame, heart at the origin): off the pot's lip, along the trough, off its spout onto the anvil. */
	private static final float[][] POUR_PATH = {{0.97F, 0.62F, 0.5F}, {1.12F, 0.4F, 0.5F}, {1.5F, 0.4F, 0.5F}, {1.5F, 0.4F, -0.19F},
		{1.5F, 0.03F, -0.42F}};
	private static final float[] POUR_WIDTH = {0.09F, 0.17F, 0.17F, 0.09F};

	public SoulCrucibleRenderer(BlockEntityRendererProvider.Context ctx) {
	}

	private static BakedModel model(ResourceLocation id) {
		return ((FabricBakedModelManager) Minecraft.getInstance().getModelManager()).getModel(id);
	}

	@Override
	public void render(SoulCrucibleBlockEntity crucible, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		Level level = crucible.getLevel();
		if (level == null) {
			return;
		}
		CraftFx.hologram(crucible, partial, pose, buffers);
		CraftFx.wave(crucible, partial, pose, buffers);
		BlockState state = crucible.getBlockState();
		int rot = crucible.isFormed() ? crucible.rotation()
			: MultiblockPattern.rotationOf(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
		long now = level.getGameTime();
		float time = now + partial;
		int phase = crucible.phase();
		float t = now - crucible.phaseStart() + partial;

		pose.pushPose();
		pose.translate(0.5F, 0.0F, 0.5F);
		pose.mulPose(Axis.YP.rotationDegrees(-90.0F * rot));
		pose.translate(-0.5F, 0.0F, -0.5F);
		this.pot(crucible, phase, t, time, pose, buffers, light);
		if (crucible.isFormed()) {
			this.bellows(crucible, phase, time, pose, buffers, level);
			this.materials(crucible, phase, t, time, pose, buffers, level, light);
			this.work(crucible, phase, t, time, pose, buffers, level);
			if (phase == SoulCrucibleBlockEntity.POUR) {
				this.pour(t, time, pose, buffers);
			} else if (phase == SoulCrucibleBlockEntity.FORGE) {
				this.rhythm(crucible, t, time, pose, buffers);
			} else if (phase == SoulCrucibleBlockEntity.QUENCH) {
				this.steam(t, pose, buffers);
			}
			this.particles(crucible, phase, t, level);
		}
		pose.popPose();
	}

	private int lightAt(SoulCrucibleBlockEntity crucible, Level level, BlockPos offset) {
		return LevelRenderer.getLightColor(level, crucible.partPos(offset));
	}

	/** The pot on its axle; it tips east into the trough while pouring. The melt glows in it while melting and pouring. */
	private void pot(SoulCrucibleBlockEntity crucible, int phase, float t, float time, PoseStack pose, MultiBufferSource buffers, int light) {
		float tilt = 0.0F;
		if (phase == SoulCrucibleBlockEntity.POUR) {
			tilt = 38.0F * (t < 6 ? CraftFx.ease(t / 6.0F) : t > 19 ? CraftFx.ease((25.0F - t) / 6.0F) : 1.0F);
		}
		pose.pushPose();
		pose.translate(0.5F, 0.5F, 0.5F);
		pose.mulPose(Axis.ZP.rotationDegrees(-tilt));
		pose.translate(-0.5F, -0.5F, -0.5F);
		BakedModel model = model(crucible.isFormed() ? POT_FORMED : POT);
		Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
			null, model, 1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY);
		float melt = phase == SoulCrucibleBlockEntity.MELT ? CraftFx.ease(t / 20.0F)
			: phase == SoulCrucibleBlockEntity.POUR ? 1.0F - CraftFx.ease((t - 8.0F) / 14.0F) : 0.0F;
		if (melt > 0.01F) {
			int frame = (int) (time / 2.0F) % CraftFx.MOLTEN_FRAMES;
			float v0 = frame / (float) CraftFx.MOLTEN_FRAMES, v1 = (frame + 1) / (float) CraftFx.MOLTEN_FRAMES;
			VertexConsumer vc = buffers.getBuffer(CraftFx.glow(CraftFx.MOLTEN));
			float y = (4.5F + 3.0F * melt) / 16.0F;
			CraftFx.flat(vc, pose, 0.5F, y, 0.5F, 7.0F / 16.0F, 0, v0, 1, v1, (int) (230 * Math.min(1.0F, melt * 2.5F)) << 24 | 0xFFFFFF,
				LightTexture.FULL_BRIGHT);
		}
		pose.popPose();
	}

	/** The bellows' board, hinged at the nozzle end: still when idle, pumping while the forge works. */
	private void bellows(SoulCrucibleBlockEntity crucible, int phase, float time, PoseStack pose, MultiBufferSource buffers, Level level) {
		BlockPos at = SoulCrucibleBlockEntity.BELLOWS;
		float angle = phase != 0 && phase != SoulCrucibleBlockEntity.QUENCH ? 7.0F + 7.0F * Mth.sin(time * 0.45F) : 0.0F;
		pose.pushPose();
		pose.translate(at.getX(), at.getY(), at.getZ());
		pose.translate(0.5F, 0.0F, 0.5F);
		pose.mulPose(Axis.YP.rotationDegrees(-90.0F));   // the bellows face east (nozzle into the hearth)
		pose.translate(-0.5F, 0.0F, -0.5F);
		pose.translate(0.5F, 9.5F / 16.0F, 2.0F / 16.0F);
		pose.mulPose(Axis.XP.rotationDegrees(-angle));
		pose.translate(-0.5F, -9.5F / 16.0F, -2.0F / 16.0F);
		Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
			null, model(BELLOWS_TOP), 1.0F, 1.0F, 1.0F, this.lightAt(crucible, level, at), OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	/** What is in the pot circles over it while idle, and sinks in as it melts. */
	private void materials(SoulCrucibleBlockEntity crucible, int phase, float t, float time, PoseStack pose, MultiBufferSource buffers, Level level,
		int light) {
		List<ItemStack> materials = crucible.materials();
		if (materials.isEmpty() || phase > SoulCrucibleBlockEntity.MELT) {
			return;
		}
		float sink = phase == SoulCrucibleBlockEntity.MELT ? CraftFx.ease(t / 20.0F) : 0.0F;
		for (int i = 0; i < materials.size(); i++) {
			double a = Math.PI * 2 * i / materials.size() + time * 0.05;
			float r = 0.2F * (1.0F - sink);
			float y = 0.95F + 0.04F * Mth.sin(time * 0.1F + i) - 0.5F * sink;
			CraftFx.floatingItem(materials.get(i), pose, buffers, level, 0.5F + (float) Math.cos(a) * r, y, 0.5F + (float) Math.sin(a) * r,
				0.32F * (1.0F - sink * 0.9F), time * 3.0F + i * 40, phase != 0 ? LightTexture.FULL_BRIGHT : light);
		}
	}

	/** The workpiece: on the anvil (glowing once the melt reaches it), flying to the cauldron, quenched, floating up. */
	private void work(SoulCrucibleBlockEntity crucible, int phase, float t, float time, PoseStack pose, MultiBufferSource buffers, Level level) {
		ItemStack work = crucible.work();
		if (work.isEmpty()) {
			return;
		}
		int anvilLight = this.lightAt(crucible, level, SoulCrucibleBlockEntity.ANVIL);
		Vec3 anvil = new Vec3(1.5, 0.03, -0.5);
		Vec3 cauldron = new Vec3(-0.5, 0.0, -0.5);
		if (phase == SoulCrucibleBlockEntity.QUENCH) {
			Vec3 p;
			float spin = 0;
			if (t < 8) {
				float f = CraftFx.ease(t / 8.0F);
				p = anvil.lerp(cauldron.add(0, 0.45, 0), f).add(0, Mth.sin(f * Mth.PI) * 0.6, 0);
			} else if (t < 14) {
				p = cauldron.add(0, 0.45 - 0.75 * CraftFx.ease((t - 8.0F) / 6.0F), 0);
			} else {
				p = cauldron.add(0, -0.3 + 0.62 * CraftFx.ease((t - 14.0F) / 16.0F), 0);
				spin = (t - 14.0F) * 6.0F;
			}
			CraftFx.floatingItem(work, pose, buffers, level, (float) p.x, (float) p.y, (float) p.z, 0.5F, spin, LightTexture.FULL_BRIGHT);
			return;
		}
		if (crucible.finished()) {   // waiting to be taken, over the quench water
			CraftFx.floatingItem(work, pose, buffers, level, -0.5F, 0.32F + 0.04F * Mth.sin(time * 0.1F), -0.5F, 0.5F, time * 2.0F,
				this.lightAt(crucible, level, SoulCrucibleBlockEntity.CAULDRON));
			return;
		}
		float glow = phase == SoulCrucibleBlockEntity.POUR ? CraftFx.ease((t - 12.0F) / 10.0F) : phase == SoulCrucibleBlockEntity.FORGE ? 1.0F : 0.0F;
		CraftFx.lyingItem(work, pose, buffers, level, (float) anvil.x, (float) anvil.y, (float) anvil.z, 0.55F, 0.0F,
			glow > 0.3F ? LightTexture.FULL_BRIGHT : anvilLight);
		if (glow > 0.01F) {
			int frame = (int) (time / 2.0F) % CraftFx.MOLTEN_FRAMES;
			VertexConsumer vc = buffers.getBuffer(CraftFx.glow(CraftFx.MOLTEN));
			CraftFx.flat(vc, pose, (float) anvil.x, 0.012F, (float) anvil.z, 0.5F, 0, frame / 8.0F, 1, (frame + 1) / 8.0F,
				(int) (150 * glow) << 24 | 0xFFFFFF, LightTexture.FULL_BRIGHT);
		}
	}

	/** The melt running off the pot's lip, along the trough and off its spout onto the anvil. */
	private void pour(float t, float time, PoseStack pose, MultiBufferSource buffers) {
		float total = 0;
		float[] lengths = new float[POUR_PATH.length - 1];
		for (int i = 0; i < lengths.length; i++) {
			float[] a = POUR_PATH[i], b = POUR_PATH[i + 1];
			lengths[i] = Mth.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]));
			total += lengths[i];
		}
		float head = total * CraftFx.ease((t - 3.0F) / 11.0F);
		float tail = total * CraftFx.ease((t - 17.0F) / 8.0F);
		if (head <= tail) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(CraftFx.glow(CraftFx.POUR));
		float scroll = -time * 0.12F, along = 0;
		for (int i = 0; i < lengths.length; i++) {
			float s0 = Math.max(along, tail), s1 = Math.min(along + lengths[i], head);
			if (s1 > s0) {
				float f0 = (s0 - along) / lengths[i], f1 = (s1 - along) / lengths[i];
				float[] a = POUR_PATH[i], b = POUR_PATH[i + 1];
				CraftFx.ribbon(vc, pose, Mth.lerp(f0, a[0], b[0]), Mth.lerp(f0, a[1], b[1]), Mth.lerp(f0, a[2], b[2]),
					Mth.lerp(f1, a[0], b[0]), Mth.lerp(f1, a[1], b[1]), Mth.lerp(f1, a[2], b[2]), POUR_WIDTH[i],
					scroll + s0 * 2.0F, scroll + s1 * 2.0F, 0xF0FFFFFF, LightTexture.FULL_BRIGHT);
			}
			along += lengths[i];
		}
	}

	/** The rhythm: a mark sweeps across the anvil once per beat, the strike should land as it crosses the middle. */
	private void rhythm(SoulCrucibleBlockEntity crucible, float t, float time, PoseStack pose, MultiBufferSource buffers) {
		float rel = t - LEAD_TICKS;
		VertexConsumer vc = buffers.getBuffer(CraftFx.glow(CraftFx.RUNES));
		float u1 = 1.0F / CraftFx.RUNE_COUNT;
		int full = LightTexture.FULL_BRIGHT;
		// a dim mark over the middle of the anvil (where to strike) and the bright one sweeping across it; both stand
		// upright a little over the anvil so the smith sees them from wherever they stand
		CraftFx.flat(vc, pose, 1.5F, 0.02F, -0.5F, 0.16F, 0, 0, u1, 1, 0x60FFFFFF, full);
		CraftFx.billboard(vc, pose, 1.5F, 0.2F, -0.5F, 0.14F, 0, 0, u1, 1, 0x70FFFFFF, full);
		if (rel >= 0 && rel < BEATS * BEAT_TICKS) {
			int k = (int) (rel / BEAT_TICKS);
			float f = (rel - k * BEAT_TICKS) / BEAT_TICKS;
			if (crucible.beat(k) == SoulCrucibleBlockEntity.NONE) {
				float close = 1.0F - Math.abs(f - 0.5F) * 2.0F;
				float x = Mth.lerp(f, 1.12F, 1.88F);
				CraftFx.flat(vc, pose, x, 0.025F, -0.5F, 0.13F + 0.07F * close, 0, 0, u1, 1, (int) (140 + 115 * close) << 24 | 0xFFFFFF, full);
				CraftFx.billboard(vc, pose, x, 0.2F, -0.5F, 0.16F + 0.12F * close, 0, 0, u1, 1, (int) (150 + 105 * close) << 24 | 0xFFFFFF, full);
			}
		}
		// the strikes so far, and the last one's verdict over the anvil
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		MutableComponent pips = Component.empty();
		Component verdict = null;
		for (int k = 0; k < BEATS; k++) {
			int b = crucible.beat(k);
			pips.append(Component.literal(b == SoulCrucibleBlockEntity.NONE ? "◇" : "◆").withColor(
				b == SoulCrucibleBlockEntity.PERFECT ? C_GOLD : b == SoulCrucibleBlockEntity.GOOD ? C_ACCENT : b == SoulCrucibleBlockEntity.MISS ? C_WARN : C_MUTED));
			float since = rel - k * BEAT_TICKS;
			if (b != SoulCrucibleBlockEntity.NONE && since >= 0 && since < BEAT_TICKS + 8) {
				verdict = Component.translatable(b == SoulCrucibleBlockEntity.PERFECT ? "label.remnants.forge.perfect"
					: b == SoulCrucibleBlockEntity.GOOD ? "label.remnants.forge.good" : "label.remnants.forge.miss")
					.withColor(b == SoulCrucibleBlockEntity.PERFECT ? C_GOLD : b == SoulCrucibleBlockEntity.GOOD ? C_ACCENT : C_WARN);
			}
		}
		pose.pushPose();
		pose.translate(1.5F, 0.85F, -0.5F);
		pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
		pose.scale(0.0125F, -0.0125F, 0.0125F);
		Matrix4f m = pose.last().pose();
		font.drawInBatch(pips, -font.width(pips) / 2.0F, 0, 0x20FFFFFF, false, m, buffers, Font.DisplayMode.SEE_THROUGH, C_SURFACE, full);
		font.drawInBatch(pips, -font.width(pips) / 2.0F, 0, -1, false, m, buffers, Font.DisplayMode.NORMAL, 0, full);
		if (verdict != null) {
			font.drawInBatch(verdict, -font.width(verdict) / 2.0F, -12, 0x20FFFFFF, false, m, buffers, Font.DisplayMode.SEE_THROUGH, C_SURFACE, full);
			font.drawInBatch(verdict, -font.width(verdict) / 2.0F, -12, -1, false, m, buffers, Font.DisplayMode.NORMAL, 0, full);
		}
		pose.popPose();
	}

	/** Puffs of steam rising from the quench cauldron. */
	private void steam(float t, PoseStack pose, MultiBufferSource buffers) {
		VertexConsumer vc = buffers.getBuffer(CraftFx.glow(CraftFx.STEAM));
		for (int k = 0; k < 5; k++) {
			float age = t - 8.0F - k * 3.5F;
			if (age < 0 || age > 18) {
				continue;
			}
			float f = age / 18.0F;
			int frame = Math.min(CraftFx.STEAM_FRAMES - 1, (int) (f * CraftFx.STEAM_FRAMES));
			float u0 = frame / (float) CraftFx.STEAM_FRAMES, u1 = (frame + 1) / (float) CraftFx.STEAM_FRAMES;
			float x = -0.5F + Mth.sin(k * 2.3F) * 0.18F, z = -0.5F + Mth.cos(k * 1.7F) * 0.18F;
			CraftFx.billboard(vc, pose, x, 0.15F + f * 1.3F, z, 0.5F + f * 0.6F, u0, 0, u1, 1, (int) (230 * (1.0F - f)) << 24 | 0xFFFFFF,
				LightTexture.FULL_BRIGHT);
		}
	}

	/** Once per tick: the hearth roars while melting, the bellows puff, sparks fly off the billet. */
	private void particles(SoulCrucibleBlockEntity crucible, int phase, float t, Level level) {
		long now = level.getGameTime();
		if (phase == 0 || crucible.clientFxTick == now) {
			return;
		}
		crucible.clientFxTick = now;
		Vec3 heart = Vec3.atBottomCenterOf(crucible.getBlockPos());
		if (phase == SoulCrucibleBlockEntity.MELT || phase == SoulCrucibleBlockEntity.POUR) {
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, heart.x + (level.random.nextDouble() - 0.5) * 0.4, heart.y - 0.1,
				heart.z + (level.random.nextDouble() - 0.5) * 0.4, 0, 0.04, 0);
		}
		if (phase != SoulCrucibleBlockEntity.QUENCH && now % 8 == 0) {
			Vec3 nozzle = Vec3.atCenterOf(crucible.partPos(SoulCrucibleBlockEntity.BELLOWS)).add(Vec3.atLowerCornerOf(
				MultiblockPattern.rotate(new BlockPos(1, 0, 0), crucible.rotation())).scale(0.45)).add(0, -0.05, 0);
			level.addParticle(ParticleTypes.SMOKE, nozzle.x, nozzle.y, nozzle.z, 0, 0.01, 0);
		}
	}

	@Override
	public boolean shouldRenderOffScreen(SoulCrucibleBlockEntity crucible) {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 48;
	}
}
