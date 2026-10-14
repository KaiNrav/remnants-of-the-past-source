package dev.remnants.client.archive;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.remnants.RemnantsMod;
import dev.remnants.block.EpitaphNicheBlock;
import dev.remnants.block.entity.AstrolabeBlockEntity;
import dev.remnants.block.entity.EpitaphNicheBlockEntity;
import dev.remnants.block.entity.MemorialBlockEntity;
import dev.remnants.client.craft.CraftFx;
import dev.remnants.item.EpitaphData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Block entity renderers of 1.5: the niches' tablets, the Memorial Projector, the Soul Astrolabe. */
public final class ArchiveBlockRenderers {
	public static final ResourceLocation RING = RemnantsMod.id("textures/entity/archive/ring.png");

	/**
	 * An Epitaph Niche's tablet: a slate set into the recess, the name in gold, the cause in bone, the day below. The
	 * letters appear one by one after the tablet is set (a chisel at work).
	 */
	public static final class Niche implements BlockEntityRenderer<EpitaphNicheBlockEntity> {
		private final Font font;

		public Niche(BlockEntityRendererProvider.Context ctx) {
			this.font = ctx.getFont();
		}

		@Override
		public void render(EpitaphNicheBlockEntity niche, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
			EpitaphData d = niche.epitaph();
			Level level = niche.getLevel();
			if (d == null || level == null) {
				return;
			}
			Direction facing = niche.getBlockState().getValue(EpitaphNicheBlock.FACING);
			pose.pushPose();
			pose.translate(0.5, 0.5, 0.5);
			pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
			// the slate, just in front of the recess' back wall (2 px deep)
			TextureAtlasSprite slate = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
				.apply(ResourceLocation.withDefaultNamespace("block/polished_deepslate"));
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
			PoseStack.Pose p = pose.last();
			float x0 = -0.34F, x1 = 0.34F, y0 = -0.28F, y1 = 0.3F, z = 0.5F - 2.0F / 16.0F + 0.002F;
			CraftFx.vertex(vc, p, x0, y0, z, slate.getU0(), slate.getV1(), 255, 255, 255, 255, light, 0, 0, 1);
			CraftFx.vertex(vc, p, x1, y0, z, slate.getU1(), slate.getV1(), 255, 255, 255, 255, light, 0, 0, 1);
			CraftFx.vertex(vc, p, x1, y1, z, slate.getU1(), slate.getV0(), 255, 255, 255, 255, light, 0, 0, 1);
			CraftFx.vertex(vc, p, x0, y1, z, slate.getU0(), slate.getV0(), 255, 255, 255, 255, light, 0, 0, 1);
			// the inscription
			float s = 0.0068F;
			pose.translate(0, y1 - 0.035F, z + 0.003F);
			pose.scale(s, -s, s);
			int width = (int) ((x1 - x0 - 0.06F) / s);
			List<Line> lines = new ArrayList<>();
			lines.add(new Line(Component.literal(d.name()).getVisualOrderText(), 0xFFE7C26A));
			for (FormattedCharSequence l : this.font.split(d.cause(), width)) {
				if (lines.size() >= 6) {
					break;
				}
				lines.add(new Line(l, 0xFFD9CFB6));
			}
			Component when = d.unknown() ? Component.translatable("epitaph.remnants.long_ago") : Component.translatable("epitaph.remnants.day", d.day());
			lines.add(new Line(when.getVisualOrderText(), 0xFF8FD8D2));
			int shown = (int) ((level.getGameTime() - niche.setAt() + partial) * 2.0F);
			int bright = LightTexture.pack(Math.max(10, LightTexture.block(light)), LightTexture.sky(light));
			Matrix4f m = pose.last().pose();
			float y = 0;
			for (Line line : lines) {
				int len = length(line.text());
				if (shown <= 0) {
					break;
				}
				FormattedCharSequence text = shown >= len ? line.text() : cut(line.text(), shown);
				shown -= len;
				float x = -this.font.width(line.text()) / 2.0F;
				this.font.drawInBatch(text, x, y, line.color(), false, m, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, bright);
				y += 10;
			}
			pose.popPose();
		}

		private record Line(FormattedCharSequence text, int color) {
		}

		private static int length(FormattedCharSequence seq) {
			int[] n = {0};
			seq.accept((i, style, cp) -> {
				n[0]++;
				return true;
			});
			return n[0];
		}

		private static FormattedCharSequence cut(FormattedCharSequence seq, int max) {
			return sink -> {
				int[] n = {0};
				return seq.accept((i, style, cp) -> n[0]++ < max && sink.accept(i, style, cp));
			};
		}
	}

	/** The Memorial Projector: the missing-block hologram, the locking wave, a soft cone of light while it plays. */
	public static final class Memorial implements BlockEntityRenderer<MemorialBlockEntity> {
		public Memorial(BlockEntityRendererProvider.Context ctx) {
		}

		@Override
		public void render(MemorialBlockEntity m, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
			CraftFx.hologram(m, partial, pose, buffers);
			CraftFx.wave(m, partial, pose, buffers);
			if (m.isWorking() && m.getLevel() != null) {
				float t = m.getLevel().getGameTime() - m.phaseStart() + partial;
				float in = Mth.clamp(t / 20.0F, 0.0F, 1.0F) * Mth.clamp((MemorialBlockEntity.REPLAY_TICKS - t) / 20.0F, 0.0F, 1.0F);
				int a = (int) (36 * in);
				// a beacon-style beam writes no depth: the replaying ghost inside it must still be drawn
				VertexConsumer vc = buffers.getBuffer(RenderType.beaconBeam(CraftFx.BEAM, true));
				CraftFx.column(vc, pose, 0.5F, 0.5F, 0.75F, 2.6F, 0.12F + 0.3F * in, -t * 0.02F, a << 24 | 0x7FE8E2);
			}
		}

		@Override
		public boolean shouldRenderOffScreen(MemorialBlockEntity m) {
			return true;
		}
	}

	/**
	 * The Soul Astrolabe: three bronze rings round the sphere drifting slowly; during a search they spin fast and
	 * slow down until they lock in line, then a thin beam runs from the sphere toward the target, the distance and
	 * what it found floating above. The focus floats in the middle.
	 */
	public static final class Astrolabe implements BlockEntityRenderer<AstrolabeBlockEntity> {
		private final Font font;

		public Astrolabe(BlockEntityRendererProvider.Context ctx) {
			this.font = ctx.getFont();
		}

		@Override
		public void render(AstrolabeBlockEntity a, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
			CraftFx.hologram(a, partial, pose, buffers);
			CraftFx.wave(a, partial, pose, buffers);
			Level level = a.getLevel();
			if (level == null) {
				return;
			}
			float time = level.getGameTime() + partial;
			float cx = 0.5F, cy = 0.68F, cz = 0.5F;
			Vec3 target = a.target();
			float beamYaw = 0;
			if (target != null) {
				beamYaw = (float) Math.toDegrees(Math.atan2(target.z - (a.getBlockPos().getZ() + cz), target.x - (a.getBlockPos().getX() + cx)));
			}
			float lock = 0;   // 0 = drifting, 1 = locked in line
			if (a.phase() == 1) {
				lock = CraftFx.ease((level.getGameTime() - a.phaseStart() + partial) / AstrolabeBlockEntity.SPIN);
			} else if (a.phase() == 2) {
				lock = 1;
			}
			float spin = a.phase() == 1 ? 9.0F * (1 - lock) + 0.6F : 0.6F;
			VertexConsumer vc = buffers.getBuffer(CraftFx.glow(RING));
			float[] radius = {0.42F, 0.35F, 0.28F};
			float[] speed = {1.0F, -1.7F, 2.6F};
			for (int i = 0; i < 3; i++) {
				pose.pushPose();
				pose.translate(cx, cy, cz);
				pose.mulPose(Axis.YP.rotationDegrees(-beamYaw * lock));
				float drift = time * spin * speed[i];
				pose.mulPose(Axis.YP.rotationDegrees((drift + i * 60) * (1 - lock)));
				pose.mulPose(Axis.XP.rotationDegrees((drift * 0.7F + i * 35) * (1 - lock) + 90 * lock));
				ring(vc, pose, radius[i], i == 0 ? 0.06F : 0.045F);
				pose.popPose();
			}
			CraftFx.floatingItem(a.focus(), pose, buffers, level, cx, cy - 0.08F, cz, 0.32F, time * 2.0F, LightTexture.FULL_BRIGHT);
			if (a.phase() == 2 && target != null) {
				float t = level.getGameTime() - a.phaseStart() + partial;
				Vec3 from = new Vec3(a.getBlockPos().getX() + cx, a.getBlockPos().getY() + cy, a.getBlockPos().getZ() + cz);
				Vec3 dir = target.subtract(from);
				double dist = dir.length();
				dir = dir.normalize();
				double len = Math.min(dist, 64.0) * CraftFx.ease(t / 20.0F);
				VertexConsumer beam = buffers.getBuffer(CraftFx.glow(CraftFx.BEAM));
				CraftFx.ribbon(beam, pose, cx, cy, cz, cx + (float) (dir.x * len), cy + (float) (dir.y * len), cz + (float) (dir.z * len), 0.07F,
					-t * 0.05F, (float) len * 0.5F - t * 0.05F, 0xC07FE8E2, LightTexture.FULL_BRIGHT);
				label(a, pose, buffers, cx, cy + 0.9F, cz);
			}
		}

		/** A ring as 24 short ribbon segments round the local Z axis. */
		private static void ring(VertexConsumer vc, PoseStack pose, float r, float w) {
			int n = 24;
			for (int i = 0; i < n; i++) {
				double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
				CraftFx.ribbon(vc, pose, (float) Math.cos(a0) * r, (float) Math.sin(a0) * r, 0, (float) Math.cos(a1) * r, (float) Math.sin(a1) * r, 0,
					w, 0, 1, 0xFFFFFFFF, LightTexture.FULL_BRIGHT);
			}
		}

		private void label(AstrolabeBlockEntity a, PoseStack pose, MultiBufferSource buffers, float x, float y, float z) {
			Component what = Component.translatable("astrolabe.remnants.kind." + a.kind());
			Component dist = Component.translatable("astrolabe.remnants.distance", a.distance());
			pose.pushPose();
			pose.translate(x, y, z);
			pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
			pose.scale(-0.025F, -0.025F, 0.025F);
			Matrix4f m = pose.last().pose();
			this.font.drawInBatch(what, -this.font.width(what) / 2.0F, 0, 0xFFE7C26A, false, m, buffers, Font.DisplayMode.SEE_THROUGH, 0x40000000,
				LightTexture.FULL_BRIGHT);
			this.font.drawInBatch(dist, -this.font.width(dist) / 2.0F, 10, 0xFF8FF5EC, false, m, buffers, Font.DisplayMode.SEE_THROUGH, 0x40000000,
				LightTexture.FULL_BRIGHT);
			pose.popPose();
		}

		@Override
		public boolean shouldRenderOffScreen(AstrolabeBlockEntity a) {
			return true;
		}

		@Override
		public int getViewDistance() {
			return 128;
		}
	}

	private ArchiveBlockRenderers() {
	}
}
