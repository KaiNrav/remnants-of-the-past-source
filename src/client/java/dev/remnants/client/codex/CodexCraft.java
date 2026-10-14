package dev.remnants.client.codex;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.remnants.client.craft.CraftFx;
import dev.remnants.client.craft.SoulCrucibleRenderer;
import dev.remnants.crafting.CrucibleRecipes;
import dev.remnants.multiblock.MultiblockPattern;
import dev.remnants.multiblock.Multiblocks;
import dev.remnants.registry.ModBlocks;
import dev.remnants.registry.ModItems;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static dev.remnants.client.codex.CodexScreen.C_ACCENT;
import static dev.remnants.client.codex.CodexScreen.C_BODY;
import static dev.remnants.client.codex.CodexScreen.C_BORDER;
import static dev.remnants.client.codex.CodexScreen.C_BORDER_HI;
import static dev.remnants.client.codex.CodexScreen.C_GOLD;
import static dev.remnants.client.codex.CodexScreen.C_MUTED;
import static dev.remnants.client.codex.CodexScreen.C_SURFACE;
import static dev.remnants.client.codex.CodexScreen.C_SURFACE_HI;
import static dev.remnants.client.codex.CodexScreen.C_TEXT;
import static dev.remnants.client.codex.CodexScreen.LINE_H;
import static dev.remnants.client.codex.CodexScreen.box;
import static dev.remnants.client.codex.CodexScreen.card;
import static dev.remnants.client.codex.CodexScreen.fade;

/** The Codex's "Craft" chapter blocks: layered multiblock schematics and the Soul Crucible's recipes. */
final class CodexCraft {

	/**
	 * A multiblock drawn in 3D on a dark stage, layer by layer like GuideME / Patchouli: the buttons show every layer or
	 * one at a time (the others ghosted), drag to turn it. Built from the same {@link MultiblockPattern} the heart checks.
	 */
	static final class Schematic extends CodexBlocks.Block {
		final MultiblockPattern pattern;
		final BlockState heart;
		final Component caption;
		final int minY, maxY;
		int layer = Integer.MIN_VALUE;   // MIN_VALUE = all layers
		float userYaw = 225.0F;
		boolean touched;
		List<FormattedCharSequence> lines = List.of();

		Schematic(MultiblockPattern pattern, BlockState heart, Component caption) {
			this.pattern = pattern;
			this.heart = heart;
			this.caption = caption;
			int lo = 0, hi = 0;
			for (MultiblockPattern.Part p : pattern.parts()) {
				lo = Math.min(lo, p.offset().getY());
				hi = Math.max(hi, p.offset().getY());
			}
			this.minY = lo;
			this.maxY = hi;
		}

		@Override
		int layout(CodexScreen s, int w) {
			this.lines = s.font().split(this.caption, w - 16);
			return this.height = this.stageH() + 2 + this.lines.size() * LINE_H + 6;
		}

		private int stageH() {
			return 132;
		}

		@Override
		void render(CodexScreen s, GuiGraphics g, int x, int y, int w, int mx, int my, float a, boolean inView) {
			card(g, x, y, w, this.height, fade(C_SURFACE, a), fade(C_BORDER, a));
			int sx = x + 4, sy = y + 4, sw = w - 8, sh = this.stageH() - 8;
			g.fillGradient(sx, sy, sx + sw, sy + sh, fade(0xFF2E2647, a), fade(0xFF0D0A14, a));
			float t = s.ticks + s.partialTick;
			float yaw = this.touched ? this.userYaw : 225.0F + Mth.sin(t * 0.015F) * 25.0F;
			if (a > 0.6F) {
				this.drawStructure(g, sx, sy, sw, sh, yaw, t);
			}
			CodexAtlas.glyph(g, CodexAtlas.Glyph.ROTATE, sx + 4, sy + sh - 13,
				fade(s.canHover(inView) && s.inside(mx, my, sx, sy, sw, sh) ? C_TEXT : C_MUTED, a * 0.8F));
			// layer buttons (a one-layer structure needs none)
			Font f = s.font();
			int bx = sx + sw;
			for (int i = this.maxY; this.maxY > this.minY && i >= this.minY - 1; i--) {
				int value = i < this.minY ? Integer.MIN_VALUE : i;
				Component label = value == Integer.MIN_VALUE ? Component.translatable("codex.remnants.ui.layers_all")
					: Component.literal(Integer.toString(i - this.minY + 1));
				int bw = f.width(label) + 8;
				bx -= bw + 2;
				boolean on = this.layer == value;
				boolean over = s.canHover(inView) && s.inside(mx, my, bx, sy + sh - 14, bw, 12);
				card(g, bx, sy + sh - 14, bw, 12, fade(on ? C_SURFACE_HI : C_SURFACE, a), fade(on ? C_ACCENT : over ? C_BORDER_HI : C_BORDER, a));
				g.drawString(f, label, bx + 4, sy + sh - 12, fade(on ? C_TEXT : C_MUTED, a), false);
			}
			s.lines(g, this.lines, x + 8, y + this.stageH() + 2, fade(C_BODY, a));
		}

		private void drawStructure(GuiGraphics g, int x, int y, int w, int h, float yaw, float t) {
			Minecraft mc = Minecraft.getInstance();
			List<BlockPos> offsets = new ArrayList<>();
			List<BlockState> states = new ArrayList<>();
			offsets.add(BlockPos.ZERO);
			states.add(this.heart);
			for (MultiblockPattern.Part p : this.pattern.parts()) {
				offsets.add(p.offset());
				states.add(this.pattern.ghost(p, 0));
			}
			int minX = 0, maxX = 0, minZ = 0, maxZ = 0;
			for (BlockPos o : offsets) {
				minX = Math.min(minX, o.getX());
				maxX = Math.max(maxX, o.getX());
				minZ = Math.min(minZ, o.getZ());
				maxZ = Math.max(maxZ, o.getZ());
			}
			float span = Math.max(Math.max(maxX - minX, maxZ - minZ), this.maxY - this.minY) + 1;
			float size = Math.min(w, h) / (span * (this.maxY > this.minY ? 1.6F : 1.25F));
			g.enableScissor(x, y, x + w, y + h);
			PoseStack ps = g.pose();
			ps.pushPose();
			ps.translate(x + w / 2.0F, y + h / 2.0F + 4, 200);
			ps.scale(size, -size, size);
			ps.mulPose(Axis.XP.rotationDegrees(30.0F));
			ps.mulPose(Axis.YP.rotationDegrees(yaw));
			ps.translate(-(minX + maxX + 1) / 2.0F, -(this.minY + this.maxY + 1) / 2.0F, -(minZ + maxZ + 1) / 2.0F);
			MultiBufferSource.BufferSource buffers = g.bufferSource();
			Lighting.setupFor3DItems();
			RenderSystem.enableDepthTest();
			for (int i = 0; i < offsets.size(); i++) {
				BlockPos o = offsets.get(i);
				BlockState state = states.get(i);
				boolean shown = this.layer == Integer.MIN_VALUE || o.getY() == this.layer;
				if (!shown && o.getY() > this.layer) {
					continue;   // layers above the chosen one are hidden, below it ghosted
				}
				ps.pushPose();
				ps.translate(o.getX(), o.getY(), o.getZ());
				if (Multiblocks.isWaterSource(state)) {
					CraftFx.waterPlane(ps, buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)), 0.875F, 0.3F, 0.5F, 0.95F,
						shown ? 0.85F : 0.25F);
				} else if (shown) {
					mc.getBlockRenderer().renderSingleBlock(state, ps, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
					if (state.is(ModBlocks.SOUL_CRUCIBLE)) {   // the pot is drawn by the renderer in the world
						mc.getBlockRenderer().getModelRenderer().renderModel(ps.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), null,
							((FabricBakedModelManager) mc.getModelManager()).getModel(SoulCrucibleRenderer.POT), 1, 1, 1,
							LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
					}
				} else {
					CraftFx.ghostModel(ps, buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)), state, 0.6F, 0.6F, 0.75F, 0.22F);
				}
				ps.popPose();
			}
			buffers.endBatch();
			ps.popPose();
			Lighting.setupFor3DItems();
			g.disableScissor();
		}

		@Override
		boolean click(CodexScreen s, int x, int y, int w, double mx, double my) {
			int sx = x + 4, sy = y + 4, sw = w - 8, sh = this.stageH() - 8;
			Font f = s.font();
			int bx = sx + sw;
			for (int i = this.maxY; this.maxY > this.minY && i >= this.minY - 1; i--) {
				int value = i < this.minY ? Integer.MIN_VALUE : i;
				Component label = value == Integer.MIN_VALUE ? Component.translatable("codex.remnants.ui.layers_all")
					: Component.literal(Integer.toString(i - this.minY + 1));
				int bw = f.width(label) + 8;
				bx -= bw + 2;
				if (s.inside(mx, my, bx, sy + sh - 14, bw, 12)) {
					this.layer = value;
					return true;
				}
			}
			if (s.inside(mx, my, sx, sy, sw, sh)) {
				if (!this.touched) {
					this.userYaw = 225.0F + Mth.sin((s.ticks + s.partialTick) * 0.015F) * 25.0F;
				}
				this.touched = true;
				return true;
			}
			return false;
		}

		@Override
		void drag(double dx, double dy) {
			this.userYaw += (float) dx * 2.0F;
		}
	}

	/** One Soul Crucible recipe: workpiece + what goes into the pot -> the result. */
	static final class CrucibleRecipeCard extends CodexBlocks.Block {
		final CrucibleRecipes.Recipe recipe;
		List<FormattedCharSequence> lines = List.of();

		CrucibleRecipeCard(CrucibleRecipes.Recipe recipe) {
			this.recipe = recipe;
		}

		private ItemStack work() {
			return switch (this.recipe.kind()) {
				case SPECTRAL -> new ItemStack(Items.DIAMOND_SWORD);
				case AWAKEN -> new ItemStack(ModItems.SOUL_KING_ARSENAL);
				case MOURNER -> switch (this.recipe.key()) {
					case "mourner_hood" -> new ItemStack(Items.IRON_HELMET);
					case "mourner_robe" -> new ItemStack(Items.IRON_CHESTPLATE);
					case "mourner_leggings" -> new ItemStack(Items.IRON_LEGGINGS);
					default -> new ItemStack(Items.IRON_BOOTS);
				};
				case REFORGE -> new ItemStack(Items.IRON_PICKAXE);
				case REMEMBER -> new ItemStack(Items.DIAMOND_SWORD);
			};
		}

		private ItemStack result() {
			if (this.recipe.kind() == CrucibleRecipes.Kind.REMEMBER) {
				ItemStack out = this.work();
				out.set(dev.remnants.registry.ModComponents.REMEMBERED, net.minecraft.util.Unit.INSTANCE);
				return out;
			}
			return this.recipe.result() != null ? new ItemStack(this.recipe.result()) : this.work();
		}

		@Override
		int layout(CodexScreen s, int w) {
			this.lines = s.font().split(Component.translatable("codex.remnants.crucible_recipe." + this.recipe.key()), w - 16);
			return this.height = 32 + this.lines.size() * LINE_H + 4;
		}

		@Override
		void render(CodexScreen s, GuiGraphics g, int x, int y, int w, int mx, int my, float a, boolean inView) {
			card(g, x, y, w, this.height, fade(C_SURFACE, a), fade(C_BORDER, a));
			int sx = x + 6, sy = y + 6;
			slot(g, sx, sy, C_BORDER_HI, a);
			s.item(g, this.work(), sx + 2, sy + 2, inView);
			int px = sx + 26;
			g.drawString(s.font(), "+", px, sy + 6, fade(C_MUTED, a), false);
			px += 10;
			for (CrucibleRecipes.Need need : this.recipe.needs()) {
				slot(g, px, sy, C_BORDER, a);
				s.item(g, new ItemStack(need.item(), need.count()), px + 2, sy + 2, inView);
				px += 22;
			}
			CodexAtlas.glyph(g, CodexAtlas.Glyph.RIGHT, px + 2, sy + 6, fade(C_GOLD, a));
			px += 14;
			slot(g, px, sy, C_GOLD, a);
			s.item(g, this.result(), px + 2, sy + 2, inView);
			s.lines(g, this.lines, x + 8, y + 32, fade(C_BODY, a));
		}

		private static void slot(GuiGraphics g, int x, int y, int border, float a) {
			box(g, x, y, 20, 20, fade(border, a));
			box(g, x + 1, y + 1, 18, 18, fade(0xFF120E1A, a));
		}
	}

	private CodexCraft() {
	}
}
