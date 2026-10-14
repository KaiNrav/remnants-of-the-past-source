package dev.remnants.client.codex;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.remnants.RemnantsMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Regions of textures/gui/codex_ui.png (keep both in sync). */
final class CodexAtlas {
	static final ResourceLocation TEX = RemnantsMod.id("textures/gui/codex_ui.png");
	static final int TW = 256, TH = 128;

	static final int[] EMBLEM = {0, 0, 40, 40};
	static final int[] GLOW = {40, 0, 32, 32};

	/** 9x9 white glyphs, tinted when drawn. */
	enum Glyph {
		CLOSE, HOME, LEFT, RIGHT, INFO, WARN, LOCK, CHECK, ROTATE, DOT
	}

	/** 23x23 rank medallions (odd, so the numerals sit on a centre pixel). */
	static int[] sigil(int rank, boolean locked) {
		return new int[]{23 * rank, locked ? 63 : 40, 23, 23};
	}

	static void blit(GuiGraphics g, int[] r, int x, int y) {
		RenderSystem.enableBlend();
		g.blit(TEX, x, y, r[0], r[1], r[2], r[3], TW, TH);
	}

	/** A glyph in the given ARGB colour. */
	static void glyph(GuiGraphics g, Glyph glyph, int x, int y, int argb) {
		RenderSystem.enableBlend();
		g.setColor(((argb >> 16) & 0xFF) / 255.0F, ((argb >> 8) & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, ((argb >>> 24) & 0xFF) / 255.0F);
		g.blit(TEX, x, y, 72 + 10 * glyph.ordinal(), 0, 9, 9, TW, TH);
		g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	private CodexAtlas() {
	}
}
