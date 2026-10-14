package dev.remnants.client;

import dev.remnants.item.EpitaphData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * An Epitaph's tooltip drawn as the stone tablet itself: an arched slab (flat panel, lit edge top-left like the mod's
 * other panels) with the text cut into it - "here lies", the name in gold, how they died, then when, where, how many
 * levels and what echo they left. No textures, no bold (Cyrillic), integer pixels only.
 */
public class EpitaphTooltipComponent implements ClientTooltipComponent {
	private static final int STONE = 0xFF3B3546, STONE_HI = 0xFF5B536A, STONE_LO = 0xFF1F1B27, STONE_IN = 0xFF2E2938;
	private static final int CARVED = 0xFFD9D1E6, CARVED_DIM = 0xFF9C93B0, GOLD = 0xFFE3BC6A, SHADOW = 0xFF15121B;
	private static final int PAD = 9, MAX_TEXT = 150;

	private final List<Line> lines = new ArrayList<>();
	private final int width;
	private final int height;

	private record Line(FormattedCharSequence text, int color, int gapBefore) {
	}

	public EpitaphTooltipComponent(EpitaphData data) {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		Font font = mc.font;
		this.add(font, Component.translatable(data.unknown() ? "epitaph.remnants.forgotten" : "epitaph.remnants.here_lies"), CARVED_DIM, 0);
		this.add(font, Component.literal(data.name()), GOLD, 2);
		boolean first = true;
		for (FormattedCharSequence seq : font.split(data.cause(), MAX_TEXT)) {
			this.lines.add(new Line(seq, CARVED, first ? 4 : 0));
			first = false;
		}
		Component when = data.unknown() ? Component.translatable("epitaph.remnants.long_ago")
			: Component.translatable("epitaph.remnants.day", data.day());
		this.add(font, Component.translatable("epitaph.remnants.where", when, data.pos().getX(), data.pos().getY(), data.pos().getZ()), CARVED_DIM, 7);
		ResourceLocation dim = ResourceLocation.tryParse(data.dimension());
		String dimKey = dim == null ? "overworld" : dim.getPath();
		Component place = Component.translatableWithFallback("epitaph.remnants.dim." + dimKey, dimKey);
		Component tail = data.rank() > 0
			? Component.translatable("epitaph.remnants.tail", place, data.levels(), Component.translatable("entity.remnants_of_the_past.echo_rank." + data.rank()))
			: Component.translatable("epitaph.remnants.tail_unknown", place, data.levels());
		this.add(font, tail, CARVED_DIM, 0);
		int w = 0;
		for (Line l : this.lines) {
			w = Math.max(w, font.width(l.text()));
		}
		this.width = Math.max(110, w + PAD * 2);
		int h = PAD + 6;
		for (Line l : this.lines) {
			h += l.gapBefore() + 10;
		}
		this.height = h + PAD - 2;
	}

	private void add(Font font, Component text, int color, int gap) {
		for (FormattedCharSequence seq : font.split(text, MAX_TEXT)) {
			this.lines.add(new Line(seq, color, gap));
			gap = 0;
		}
	}

	@Override
	public int getHeight() {
		return this.height + 4;
	}

	@Override
	public int getWidth(Font font) {
		return this.width;
	}

	@Override
	public void renderImage(Font font, int x, int y, GuiGraphics g) {
		int w = this.width, h = this.height;
		y += 2;
		// the arch: rows near the top are inset more and more, like a rounded headstone
		int[] inset = {7, 4, 3, 2, 1, 1};
		for (int row = 0; row < h; row++) {
			int in = row < inset.length ? inset[row] : 0;
			int above = row == 0 ? w / 2 : row - 1 < inset.length ? inset[row - 1] : 0;
			g.fill(x + in, y + row, x + w - in, y + row + 1, STONE);
			// light from the top-left: left edge and every upward-facing step lit, right edge shaded
			g.fill(x + in, y + row, x + in + 1, y + row + 1, STONE_HI);
			g.fill(x + w - in - 1, y + row, x + w - in, y + row + 1, STONE_LO);
			if (above > in) {
				g.fill(x + in, y + row, x + Math.min(above, w / 2), y + row + 1, STONE_HI);
				g.fill(x + Math.max(w - above, w / 2), y + row, x + w - in - 1, y + row + 1, STONE_HI);
			}
		}
		g.fill(x, y + h - 1, x + w, y + h, STONE_LO);
		// a sunken field the words are cut into
		g.fill(x + 4, y + 6, x + w - 4, y + h - 4, STONE_IN);
		g.fill(x + 4, y + 6, x + w - 4, y + 7, STONE_LO);
		g.fill(x + 4, y + h - 5, x + w - 4, y + h - 4, STONE_HI);

		int ty = y + PAD + 2;
		for (Line l : this.lines) {
			ty += l.gapBefore();
			int tx = x + (w - font.width(l.text())) / 2;
			g.drawString(font, l.text(), tx + 1, ty + 1, SHADOW, false); // cut into the stone: the shadow sits below-right
			g.drawString(font, l.text(), tx, ty, l.color(), false);
			ty += 10;
		}
	}
}
