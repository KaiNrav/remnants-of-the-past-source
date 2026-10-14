package dev.remnants.client.craft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.remnants.block.entity.ReflectingPoolBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The Reflecting Pool in motion (see {@link ReflectingPoolBlockEntity} for the phases): the source bobbing on the water,
 * the target in the basin, rings on the water, the rune glyphs peeling off and the chosen one arcing over, the flash.
 * Idle, a flat Codex-style label over the water names the chosen enchantment and its price.
 */
public class ReflectingPoolRenderer implements BlockEntityRenderer<ReflectingPoolBlockEntity> {
	private static final int C_SURFACE = 0xD81E1929, C_TEXT = 0xFFEFEAF7, C_ACCENT = 0xFF56E1E6, C_GOLD = 0xFFE3B95E,
		C_WARN = 0xFFEB7A6E, C_MUTED = 0xFF978EAF;
	private static final float LIFT = 0.45F;

	public ReflectingPoolRenderer(BlockEntityRendererProvider.Context ctx) {
	}

	@Override
	public void render(ReflectingPoolBlockEntity pool, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		Level level = pool.getLevel();
		if (level == null) {
			return;
		}
		CraftFx.hologram(pool, partial, pose, buffers);
		CraftFx.wave(pool, partial, pose, buffers);
		long now = level.getGameTime();
		float time = now + partial;
		int phase = pool.phase();
		float t = now - pool.phaseStart() + partial;
		Direction side = pool.sourceSide();
		float sx = 0.5F + side.getStepX(), sz = 0.5F + side.getStepZ();
		float lift = switch (phase) {
			case ReflectingPoolBlockEntity.RISE -> LIFT * CraftFx.ease(t / 20.0F);
			case ReflectingPoolBlockEntity.RUNES, ReflectingPoolBlockEntity.FLIGHT -> LIFT;
			case ReflectingPoolBlockEntity.IMPACT -> LIFT * (1.0F - CraftFx.ease(t / 10.0F));
			default -> 0.0F;
		};
		float bob = Mth.sin(time * 0.08F) * 0.03F;
		int full = LightTexture.FULL_BRIGHT;
		// the items: on the water, in the basin
		CraftFx.floatingItem(pool.source(), pose, buffers, level, sx, 1.02F + bob + lift, sz, 0.55F, time * 1.5F, phase != 0 ? full : light);
		CraftFx.floatingItem(pool.target(), pose, buffers, level, 0.5F, 1.38F - bob + lift, 0.5F, 0.55F, -time * 1.5F, phase != 0 ? full : light);
		if (phase != 0) {
			this.rite(pool, phase, t, time, sx, sz, lift, pose, buffers, level);
		} else if (pool.isFormed() && !pool.source().isEmpty()) {
			this.label(pool, pose, buffers);
		}
	}

	private void rite(ReflectingPoolBlockEntity pool, int phase, float t, float time, float sx, float sz, float lift, PoseStack pose,
		MultiBufferSource buffers, Level level) {
		int full = LightTexture.FULL_BRIGHT;
		// a consumer is only valid until another render type is asked for: fetch at each use
		for (int k = 0; k < 3; k++) {   // rings spreading from the source and from the basin
			float cycle = ((time + k * 8.0F) % 24.0F) / 24.0F;
			int frame = Math.min(CraftFx.RIPPLE_FRAMES - 1, (int) (cycle * CraftFx.RIPPLE_FRAMES));
			float u0 = frame / (float) CraftFx.RIPPLE_FRAMES, u1 = (frame + 1) / (float) CraftFx.RIPPLE_FRAMES;
			CraftFx.flat(buffers.getBuffer(CraftFx.glow(CraftFx.RIPPLE)), pose, sx, 0.89F, sz, 1.6F, u0, 0, u1, 1, 0xFFFFFFFF, full);
			CraftFx.flat(buffers.getBuffer(CraftFx.glow(CraftFx.RIPPLE)), pose, 0.5F, 0.89F, 0.5F, 3.0F, u0, 0, u1, 1, 0xB0FFFFFF, full);
		}
		List<Holder<Enchantment>> options = pool.options();
		if (options.isEmpty() || phase < ReflectingPoolBlockEntity.RUNES) {
			return;
		}
		int chosen = pool.selectedIndex();
		Vec3 source = new Vec3(sx, 1.02F + lift + 0.15F, sz);
		Vec3 target = new Vec3(0.5F, 1.38F + lift + 0.2F, 0.5F);
		for (int i = 0; i < options.size(); i++) {
			boolean selected = i == chosen;
			int glyph = Math.floorMod(options.get(i).unwrapKey().map(k -> k.location().hashCode()).orElse(i), CraftFx.RUNE_COUNT);
			float u0 = glyph / (float) CraftFx.RUNE_COUNT, u1 = (glyph + 1) / (float) CraftFx.RUNE_COUNT;
			// peel off in turn and settle into a slow orbit round the source
			float peel = phase == ReflectingPoolBlockEntity.RUNES ? CraftFx.ease((t - i * 4.0F) / 10.0F) : 1.0F;
			if (peel <= 0) {
				continue;
			}
			double a = Math.PI * 2 * i / options.size() + time * 0.04;
			Vec3 orbit = source.add(Math.cos(a) * 0.5 * peel, 0.25 * peel, Math.sin(a) * 0.5 * peel);
			if (phase == ReflectingPoolBlockEntity.RUNES) {
				CraftFx.billboard(buffers.getBuffer(CraftFx.glow(CraftFx.RUNES)), pose, (float) orbit.x, (float) orbit.y, (float) orbit.z, selected ? 0.42F : 0.3F, u0, 0, u1, 1,
					selected ? 0xFFFFFFFF : 0x70FFFFFF, full);
			} else if (phase == ReflectingPoolBlockEntity.FLIGHT) {
				if (!selected) {   // the others fade back into the source
					float fade = 1.0F - t / 8.0F;
					if (fade > 0) {
						CraftFx.billboard(buffers.getBuffer(CraftFx.glow(CraftFx.RUNES)), pose, (float) orbit.x, (float) orbit.y, (float) orbit.z, 0.3F, u0, 0, u1, 1,
							(int) (0x70 * fade) << 24 | 0xFFFFFF, full);
					}
					continue;
				}
				for (int trail = 4; trail >= 0; trail--) {   // the rune and its fading trail along the arc
					float f = CraftFx.ease(Mth.clamp((t - trail * 1.5F) / 20.0F, 0.0F, 1.0F));
					Vec3 p = arc(orbit, target, f);
					int alpha = trail == 0 ? 255 : 120 - trail * 22;
					CraftFx.billboard(buffers.getBuffer(CraftFx.glow(CraftFx.RUNES)), pose, (float) p.x, (float) p.y, (float) p.z, 0.42F - trail * 0.05F, u0, 0, u1, 1,
						alpha << 24 | 0xFFFFFF, full);
					if (trail == 0) {
						CraftFx.flat(buffers.getBuffer(CraftFx.glow(CraftFx.RIPPLE)), pose, (float) p.x, 0.89F, (float) p.z, 1.0F, 0.0F, 0, 1.0F / CraftFx.RIPPLE_FRAMES, 1, 0xFFFFFFFF, full);
						this.trailParticles(pool, level, p);
					}
				}
			} else if (phase == ReflectingPoolBlockEntity.IMPACT && selected) {
				float f = 1.0F - t / 10.0F;
				if (f > 0) {   // the flash as the rune sinks into the target
					CraftFx.billboard(buffers.getBuffer(CraftFx.glow(CraftFx.RUNES)), pose, (float) target.x, (float) target.y, (float) target.z, 0.4F + (1 - f) * 0.9F, u0, 0, u1, 1,
						(int) (255 * f) << 24 | 0xFFFFFF, full);
				}
			}
		}
	}

	private static Vec3 arc(Vec3 from, Vec3 to, float f) {
		Vec3 mid = from.add(to).scale(0.5).add(0, 1.1, 0);
		double a = (1 - f) * (1 - f), b = 2 * (1 - f) * f, c = f * f;
		return from.scale(a).add(mid.scale(b)).add(to.scale(c));
	}

	private void trailParticles(ReflectingPoolBlockEntity pool, Level level, Vec3 local) {
		long now = level.getGameTime();
		if (pool.clientFxTick == now) {
			return;
		}
		pool.clientFxTick = now;
		Vec3 p = local.add(pool.getBlockPos().getX(), pool.getBlockPos().getY(), pool.getBlockPos().getZ());
		level.addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, 0.005, 0);
		level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y - 0.05, p.z, 0, 0.0, 0);
	}

	/** The Codex-style label: which enchantment (wheel with the Ritual Hammer to change it) and what it costs. */
	private void label(ReflectingPoolBlockEntity pool, PoseStack pose, MultiBufferSource buffers) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.distanceToSqr(Vec3.atCenterOf(pool.getBlockPos())) > 10 * 10) {
			return;
		}
		Holder<Enchantment> e = pool.selectedEnchantment();
		if (e == null) {
			return;
		}
		List<Holder<Enchantment>> options = pool.options();
		int level = Math.min(net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(pool.source()).getLevel(e),
			e.value().getMaxLevel());
		Component name = Enchantment.getFullname(e, level);
		Component title = options.size() > 1
			? Component.literal("‹ ").withColor(C_ACCENT).append(name.copy().withColor(C_TEXT)).append(Component.literal(" ›").withColor(C_ACCENT))
			: name.copy().withColor(C_TEXT);
		Component line2;
		String verdict = pool.verdict();
		if (pool.target().isEmpty()) {
			line2 = Component.translatable("label.remnants.pool.place_target").withColor(C_MUTED);
		} else if ("ok".equals(verdict)) {
			line2 = Component.translatable("label.remnants.pool.cost", pool.costLevels(), pool.costFragments()).withColor(C_GOLD);
		} else {
			line2 = Component.translatable("message.remnants.pool.refused." + verdict).withColor(C_WARN);
		}
		Component line3 = options.size() > 1 ? Component.translatable("label.remnants.pool.wheel", pool.selectedIndex() + 1, options.size()).withColor(C_MUTED)
			: null;
		Font font = mc.font;
		pose.pushPose();
		pose.translate(0.5F, 2.0F, 0.5F);
		pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
		pose.scale(0.0125F, -0.0125F, 0.0125F);
		Matrix4f m = pose.last().pose();
		int full = LightTexture.FULL_BRIGHT;
		Component[] lines = line3 == null ? new Component[] {title, line2} : new Component[] {title, line2, line3};
		for (int i = 0; i < lines.length; i++) {   // like a name tag: the flat panel in a see-through pass, then the text over it
			float x = -font.width(lines[i]) / 2.0F;
			font.drawInBatch(lines[i], x, i * 11, 0x20FFFFFF, false, m, buffers, Font.DisplayMode.SEE_THROUGH, C_SURFACE, full);
			font.drawInBatch(lines[i], x, i * 11, -1, false, m, buffers, Font.DisplayMode.NORMAL, 0, full);
		}
		pose.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen(ReflectingPoolBlockEntity pool) {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 48;
	}
}
