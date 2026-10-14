package dev.remnants.client.codex;

import dev.remnants.Chronicle;
import dev.remnants.RemnantsMod;
import dev.remnants.item.EpitaphData;
import dev.remnants.SoulResonance;
import dev.remnants.client.codex.CodexScreen.Chapter;
import dev.remnants.net.ResonancePayload;
import dev.remnants.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static dev.remnants.client.codex.CodexScreen.C_ACCENT;
import static dev.remnants.client.codex.CodexScreen.C_BODY;
import static dev.remnants.client.codex.CodexScreen.C_BORDER;
import static dev.remnants.client.codex.CodexScreen.C_BORDER_HI;
import static dev.remnants.client.codex.CodexScreen.C_GOLD;
import static dev.remnants.client.codex.CodexScreen.C_MUTED;
import static dev.remnants.client.codex.CodexScreen.C_SURFACE;
import static dev.remnants.client.codex.CodexScreen.C_SURFACE_HI;
import static dev.remnants.client.codex.CodexScreen.C_TEXT;
import static dev.remnants.client.codex.CodexScreen.C_WARN;
import static dev.remnants.client.codex.CodexScreen.LINE_H;
import static dev.remnants.client.codex.CodexScreen.box;
import static dev.remnants.client.codex.CodexScreen.card;
import static dev.remnants.client.codex.CodexScreen.fade;

/**
 * The Codex's content, as a column of blocks per chapter. Text comes from the lang files (codex.remnants.*); every
 * block wraps to the column width, so nothing is paginated.
 */
final class CodexBlocks {

	abstract static class Block {
		int height;

		/** Wraps / measures for this width and returns the height. */
		abstract int layout(CodexScreen s, int w);

		abstract void render(CodexScreen s, GuiGraphics g, int x, int y, int w, int mx, int my, float a, boolean inView);

		boolean click(CodexScreen s, int x, int y, int w, double mx, double my) {
			return false;
		}

		void drag(double dx, double dy) {
		}
	}

	// ---- text ----------------------------------------------------------------------------------------

	static final class Heading extends Block {
		final Component text;
		final int color;

		Heading(Component text, int color) {
			this.text = text;
			this.color = color;
		}

		@Override
		int layout(CodexScreen s, int w) {
			return this.height = 14;
		}

		@Override
		void render(CodexScreen s, GuiGraphics g, int x, int y, int w, int mx, int my, float a, boolean inView) {
			g.drawString(s.font(), this.text, x, y + 1, fade(C_TEXT, a), false);
			g.fill(x, y + 12, x + w, y + 13, fade(C_BORDER, a));
			g.fill(x, y + 12, x + 18, y + 13, fade(this.color, a));
		}
	}

	static final class Paragraph extends Block {
		final Component text;
		List<FormattedCharSequence> lines = List.of();

		Paragraph(Component text) {
			this.text = text;
		}

		@Override
		int layout(CodexScreen s, int w) {
			this.lines = s.font().split(this.text, w);
			return this.height = this.lines.size() * LINE_H - 1;
		}

		@Override
		void render(CodexScreen s, GuiGraphics g, int x, int y, int w, int mx, int my, float a, boolean inView) {
			s.lines(g, this.lines, x, y, fade(C_BODY, a));
		}
	}

	enum Kind {
		TIP(C_ACCENT, CodexAtlas.Glyph.INFO), WARN(C_WARN, CodexAtlas.Glyph.WARN), LORE(C_GOLD, CodexAtlas.Glyph.DOT);

		final int color;
		final CodexAtlas.Glyph glyph;

		Kind(int color, CodexAtlas.Glyph glyph) {
			this.color = color;
			this.glyph = glyph;
		}
	}

	/** A boxed note with a coloured edge: tips, warnings, lore. */
	static final class Callout extends Block {
		final Kind kind;
		final Component text;
		List<FormattedCharSequence> lines = List.of();

		Callout(Kind kind, Component text) {
			this.kind = kind;
			this.text = this.kind == Kind.LORE ? text.copy().withStyle(ChatFormatting.ITALIC) : text;
		}

		@Override
		int layout(CodexScreen s, int w) {
			this.lines = s.font().split(this.text, w - 26);
			return this.height = this.lines.size() * LINE_H + 11;
		}

		@Override
		void render(CodexScreen s, GuiGraphics g, int x, int y, int w, int mx, int my, float a, boolean inView) {
			card(g, x, y, w, this.height, fade(C_SURFACE, a), fade(C_BORDER, a));
			g.fill(x + 1, y + 2, x + 3, y + this.height - 2, fade(this.kind.color, a));
			CodexAtlas.glyph(g, this.kind.glyph, x + 8, y + 6, fade(this.kind.color, a));
			s.lines(g, this.lines, x + 21, y + 6, fade(C_BODY, a));
		}
	}

	// ---- items, recipes, portraits -------------------------------------------------------------------

	static final class Items extends Block {
		final ItemStack[] stacks;

		Items(ItemStack... stacks) {
			this.stacks = stacks;
		}

		@Override
		int layout(CodexScreen s, int w) {
			return this.height = 22;
		}

		@Override
		void render(CodexScreen s, GuiGraphics g, int x, int y, int w, int mx, int my, float a, boolean inView) {
			for (int i = 0; i < this.stacks.length; i++) {
				int tx = x + i * 26;
				boolean over = s.canHover(inView) && s.inside(mx, my, tx, y, 22, 22);
				card(g, tx, y, 22, 22, fade(over ? C_SURFACE_HI : C_SURFACE, a), fade(over ? C_BORDER_HI : C_BORDER, a));
				s.item(g, this.stacks[i], tx + 3, y + 3, inView);
			}
		}
	}

	static final class RecipeCard extends Block {
		final ResourceLocation id;

		RecipeCard(ResourceLocation id) {
			this.id = id;
		}

		@Override
		int layout(CodexScreen s, int w) {
			return this.height = 70;
		}

		private static void slot(GuiGraphics g, int x, int y, int size, int border, float a) {
			box(g, x, y, size, size, fade(border, a));
			box(g, x + 1, y + 1, size - 2, size - 2, fade(0xFF120E1A, a));
		}

		@Override
		void render(CodexScreen s, GuiGraphics g, int x, int y, int w, int mx, int my, float a, boolean inView) {
			card(g, x, y, w, this.height, fade(C_SURFACE, a), fade(C_BORDER, a));
			int gx = x + 8, gy = y + 8;
			for (int r = 0; r < 3; r++) {
				for (int c = 0; c < 3; c++) {
					slot(g, gx + c * 18, gy + r * 18, 18, C_BORDER, a);
				}
			}
			CodexAtlas.glyph(g, CodexAtlas.Glyph.RIGHT, gx + 62, gy + 23, fade(C_GOLD, a));
			int rx = gx + 78, ry = gy + 14;
			slot(g, rx, ry, 26, C_GOLD, a);
			Minecraft mc = Minecraft.getInstance();
			RecipeHolder<?> holder = mc.level == null ? null : mc.level.getRecipeManager().byKey(this.id).orElse(null);
			if (holder == null) {
				return;
			}
			Recipe<?> recipe = holder.value();
			int rw = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
			int ox = recipe instanceof ShapedRecipe shaped ? (3 - shaped.getWidth()) / 2 : 0;
			int oy = recipe instanceof ShapedRecipe shaped ? (3 - shaped.getHeight()) / 2 : 0;
			List<Ingredient> ingredients = recipe.getIngredients();
			for (int i = 0; i < ingredients.size() && i < 9; i++) {
				ItemStack[] options = ingredients.get(i).getItems();
				if (options.length > 0) {
					s.item(g, options[(s.ticks / 24) % options.length], gx + (ox + i % rw) * 18 + 1, gy + (oy + i / rw) * 18 + 1, inView);
				}
			}
			ItemStack result = recipe.getResultItem(mc.level.registryAccess());
			s.item(g, result, rx + 5, ry + 5, inView);
			int tx = rx + 34;
			if (tx + 40 < x + w) {
				Font f = s.font();
				String name = f.plainSubstrByWidth(result.getHoverName().getString(), x + w - tx - 8);
				g.drawString(f, Component.literal(name), tx, ry + 4, fade(C_TEXT, a), false);
				g.drawString(f, Component.translatable("codex.remnants.ui.crafting"), tx, ry + 15, fade(C_MUTED, a), false);
			}
		}
	}

	/** A live, turning 3D model on a dark stage, with a title and a few lines beside it. Drag to turn it. */
	static final class Portrait extends Block {
		final Function<CodexPortraits, LivingEntity> who;
		final Component title;
		final Component text;
		final float scale;
		List<FormattedCharSequence> lines = List.of();
		float userYaw;
		boolean touched;

		Portrait(Function<CodexPortraits, LivingEntity> who, Component title, Component text, float scale) {
			this.who = who;
			this.title = title;
			this.text = text;
			this.scale = scale;
		}

		@Override
		int layout(CodexScreen s, int w) {
			this.lines = s.font().split(this.text, w - 112);
			return this.height = Math.max(100, 26 + this.lines.size() * LINE_H + 8);
		}

		@Override
		void render(CodexScreen s, GuiGraphics g, int x, int y, int w, int mx, int my, float a, boolean inView) {
			card(g, x, y, w, this.height, fade(C_SURFACE, a), fade(C_BORDER, a));
			int sx = x + 4, sy = y + 4, sw = 96, sh = this.height - 8;
			g.fillGradient(sx, sy, sx + sw, sy + sh, fade(0xFF2E2647, a), fade(0xFF0D0A14, a));
			g.fill(sx + 18, sy + sh - 9, sx + sw - 18, sy + sh - 8, fade(0x30FFFFFF, a));
			float t = s.ticks + s.partialTick;
			float yaw = this.touched ? this.userYaw : Mth.sin(t * 0.02F) * 30.0F;
			if (a > 0.6F) {
				s.entity(g, this.who.apply(s.portraits), sx, sy, sx + sw, sy + sh, this.scale, yaw);
			}
			CodexAtlas.glyph(g, CodexAtlas.Glyph.ROTATE, sx + 4, sy + sh - 13,
				fade(s.canHover(inView) && s.inside(mx, my, sx, sy, sw, sh) ? C_TEXT : C_MUTED, a * 0.8F));
			g.drawString(s.font(), this.title, x + 108, y + 9, fade(C_TEXT, a), false);
			s.lines(g, this.lines, x + 108, y + 23, fade(C_BODY, a));
		}

		@Override
		boolean click(CodexScreen s, int x, int y, int w, double mx, double my) {
			if (s.inside(mx, my, x + 4, y + 4, 96, this.height - 8)) {
				if (!this.touched) {
					this.userYaw = Mth.sin((s.ticks + s.partialTick) * 0.02F) * 30.0F;
				}
				this.touched = true;
				return true;
			}
			return false;
		}

		@Override
		void drag(double dx, double dy) {
			this.userYaw += (float) dx * 2.5F;
		}
	}

	// ---- home ----------------------------------------------------------------------------------------

	static final class Hero extends Block {
		List<FormattedCharSequence> lines = List.of();

		@Override
		int layout(CodexScreen s, int w) {
			this.lines = s.font().split(Component.translatable("codex.remnants.prologue.text"), w - 62);
			return this.height = Math.max(56, 30 + this.lines.size() * LINE_H + 6);
		}

		@Override
		void render(CodexScreen s, GuiGraphics g, int x, int y, int w, int mx, int my, float a, boolean inView) {
			float t = s.ticks + s.partialTick;
			com.mojang.blaze3d.systems.RenderSystem.enableBlend();
			g.setColor(0.34F, 0.88F, 0.9F, (0.22F + 0.1F * Mth.sin(t * 0.06F)) * a);
			g.pose().pushPose();
			g.pose().translate(x - 4, y - 4, 0);
			g.pose().scale(1.5F, 1.5F, 1);
			g.blit(CodexAtlas.TEX, 0, 0, CodexAtlas.GLOW[0], CodexAtlas.GLOW[1], 32, 32, CodexAtlas.TW, CodexAtlas.TH);
			g.pose().popPose();
			g.setColor(1, 1, 1, a);
			CodexAtlas.blit(g, CodexAtlas.EMBLEM, x + 4, y + 2);
			g.setColor(1, 1, 1, 1);
			Font f = s.font();
			g.pose().pushPose();
			g.pose().translate(x + 56, y + 2, 0);
			g.pose().scale(1.5F, 1.5F, 1);
			g.drawString(f, Component.translatable("codex.remnants.cover"), 0, 0, fade(C_TEXT, a), false);
			g.pose().popPose();
			g.drawString(f, Component.translatable("codex.remnants.motto").withStyle(ChatFormatting.ITALIC), x + 56, y + 17, fade(C_GOLD, a), false);
			s.lines(g, this.lines, x + 56, y + 30, fade(C_BODY, a));
		}
	}

	/** Chapter cards on the home page: two columns, each with its colour, icon, name and one line of what is inside. */
	static final class ChapterCards extends Block {
		static final int H = 44;
		final List<Chapter> chapters = new ArrayList<>();
		final List<List<FormattedCharSequence>> desc = new ArrayList<>();
		final float[] lift;

		ChapterCards() {
			for (Chapter c : Chapter.values()) {
				if (c != Chapter.HOME) {
					this.chapters.add(c);
				}
			}
			this.lift = new float[this.chapters.size()];
		}

		private int cardW(int w) {
			return (w - 6) / 2;
		}

		@Override
		int layout(CodexScreen s, int w) {
			this.desc.clear();
			for (Chapter c : this.chapters) {
				List<FormattedCharSequence> l = s.font().split(Component.translatable("codex.remnants.chapter." + c.key() + ".desc"), this.cardW(w) - 36);
				this.desc.add(l.subList(0, Math.min(2, l.size())));
			}
			int rows = (this.chapters.size() + 1) / 2;
			return this.height = rows * (H + 6) - 6;
		}

		@Override
		void render(CodexScreen s, GuiGraphics g, int x, int y, int w, int mx, int my, float a, boolean inView) {
			int cw = this.cardW(w);
			for (int i = 0; i < this.chapters.size(); i++) {
				Chapter c = this.chapters.get(i);
				int cx = x + (i % 2) * (cw + 6);
				int cy = y + (i / 2) * (H + 6);
				boolean over = s.canHover(inView) && s.inside(mx, my, cx, cy, cw, H);
				this.lift[i] += ((over ? 1 : 0) - this.lift[i]) * 0.35F;
				int up = Math.round(this.lift[i]);
				card(g, cx, cy - up, cw, H, fade(CodexScreen.mix(C_SURFACE, C_SURFACE_HI, this.lift[i]), a),
					fade(CodexScreen.mix(C_BORDER, c.color, this.lift[i] * 0.8F), a));
				g.fill(cx + 2, cy - up + 1, cx + cw - 2, cy - up + 2, fade(c.color, a));
				if (a > 0.5F) {
					g.renderFakeItem(c.icon, cx + 8, cy - up + 8);
				}
				Component title = c.title();
				g.drawString(s.font(), s.fit(title, cw - 36), cx + 30, cy - up + 8, fade(C_TEXT, a), false);
				s.lines(g, this.desc.get(i), cx + 30, cy - up + 20, fade(C_MUTED, a));
			}
		}

		@Override
		boolean click(CodexScreen s, int x, int y, int w, double mx, double my) {
			int cw = this.cardW(w);
			for (int i = 0; i < this.chapters.size(); i++) {
				int cx = x + (i % 2) * (cw + 6);
				int cy = y + (i / 2) * (H + 6);
				if (s.inside(mx, my, cx, cy, cw, H)) {
					s.openChapter(this.chapters.get(i));
					return true;
				}
			}
			return false;
		}
	}

	// ---- Path of the Soul ------------------------------------------------------------------------------

	/** Rank, resonance, progress to the next rank (and, in full, the tallies). */
	static final class RankCard extends Block {
		final boolean full;
		float shown = 0;

		RankCard(boolean full) {
			this.full = full;
		}

		@Override
		int layout(CodexScreen s, int w) {
			int rank = SoulResonance.rankOf(ClientResonance.get().points());
			return this.height = this.full ? 56 + 3 * LINE_H + 8 + (rank >= SoulResonance.Perk.LAST_BREATH.rank ? LINE_H + 2 : 0) : 50;
		}

		@Override
		void render(CodexScreen s, GuiGraphics g, int x, int y, int w, int mx, int my, float a, boolean inView) {
			Font f = s.font();
			ResonancePayload r = ClientResonance.get();
			int rank = SoulResonance.rankOf(r.points());
			boolean over = !this.full && s.canHover(inView) && s.inside(mx, my, x, y, w, this.height);
			card(g, x, y, w, this.height, fade(over ? C_SURFACE_HI : C_SURFACE, a), fade(over ? C_BORDER_HI : C_BORDER, a));
			CodexAtlas.blit(g, CodexAtlas.sigil(rank, false), x + 8, y + 8);
			g.drawString(f, Component.translatable("resonance.remnants.rank." + rank), x + 36, y + 9, fade(C_GOLD, a), false);
			g.drawString(f, Component.translatable("codex.remnants.path.rank_of", rank), x + 36, y + 20, fade(C_MUTED, a), false);
			String pts = Component.translatable("codex.remnants.path.points", r.points()).getString();
			g.drawString(f, pts, x + w - 8 - f.width(pts), y + 9, fade(C_TEXT, a), false);
			int next = rank >= SoulResonance.THRESHOLDS.length ? -1 : SoulResonance.THRESHOLDS[rank];
			if (next > 0) {
				String n = Component.translatable("codex.remnants.ui.next", next - r.points()).getString();
				g.drawString(f, n, x + w - 8 - f.width(n), y + 20, fade(C_MUTED, a), false);
			}
			this.shown += (CodexScreen.progressToNext(r.points()) - this.shown) * 0.12F;
			int bx = x + 8, by = y + 36, bw = w - 16;
			box(g, bx, by, bw, 6, fade(C_BORDER, a));
			if (this.shown > 0.01F) {
				box(g, bx, by, Math.max(3, Math.round(bw * this.shown)), 6, fade(C_ACCENT, a));
				g.fill(bx + 1, by + 1, bx + Math.max(2, Math.round(bw * this.shown)) - 1, by + 2, fade(0x60FFFFFF, a));
			}
			if (!this.full) {
				return;
			}
			Object[][] rows = {
				{"codex.remnants.path.own", r.laidOwn()}, {"codex.remnants.path.forgotten", r.laidForgotten()},
				{"codex.remnants.path.others", r.laidOthers()}, {"codex.remnants.path.kings", r.kingsSlain()},
				{"codex.remnants.path.streak", r.streak()}, {"codex.remnants.path.waiting", r.remnantsOut()},
			};
			int col = (w - 24) / 2;
			for (int i = 0; i < rows.length; i++) {
				int rx = x + 8 + (i % 2) * (col + 8);
				int ry = y + 50 + (i / 2) * LINE_H;
				g.drawString(f, Component.translatable((String) rows[i][0]), rx, ry, fade(C_MUTED, a), false);
				String v = rows[i][1].toString();
				g.drawString(f, v, rx + col - f.width(v), ry, fade(C_TEXT, a), false);
			}
			if (rank >= SoulResonance.Perk.LAST_BREATH.rank) {
				int left = ClientResonance.lastBreathTicks();
				Component status = left <= 0 ? Component.translatable("codex.remnants.path.breath_ready")
					: Component.translatable("codex.remnants.path.breath_wait", left / 1200 + 1);
				g.drawString(f, status, x + 8, y + 52 + 3 * LINE_H, fade(left <= 0 ? C_ACCENT : C_MUTED, a), false);
			}
		}

		@Override
		boolean click(CodexScreen s, int x, int y, int w, double mx, double my) {
			if (!this.full && s.inside(mx, my, x, y, w, this.height)) {
				s.openChapter(Chapter.PATH);
				return true;
			}
			return false;
		}
	}

	static final class PerkCard extends Block {
		final SoulResonance.Perk perk;
		List<FormattedCharSequence> lines = List.of();

		PerkCard(SoulResonance.Perk perk) {
			this.perk = perk;
		}

		@Override
		int layout(CodexScreen s, int w) {
			this.lines = s.font().split(Component.translatable("resonance.remnants.perk." + this.perk.key() + ".desc"), w - 44);
			return this.height = 22 + this.lines.size() * LINE_H + 2;
		}

		@Override
		void render(CodexScreen s, GuiGraphics g, int x, int y, int w, int mx, int my, float a, boolean inView) {
			Font f = s.font();
			boolean got = SoulResonance.rankOf(ClientResonance.get().points()) >= this.perk.rank;
			card(g, x, y, w, this.height, fade(C_SURFACE, a), fade(got ? 0xFF6B5A2E : C_BORDER, a));
			CodexAtlas.blit(g, CodexAtlas.sigil(this.perk.rank, !got), x + 7, y + 6);
			Component name = Component.translatable("resonance.remnants.perk." + this.perk.key());
			g.drawString(f, name, x + 36, y + 7, fade(got ? C_TEXT : C_MUTED, a), false);
			Component status = got ? Component.translatable("codex.remnants.path.earned")
				: Component.translatable("codex.remnants.path.needs", SoulResonance.THRESHOLDS[this.perk.rank - 1]);
			int sw = f.width(status) + 13;
			CodexAtlas.glyph(g, got ? CodexAtlas.Glyph.CHECK : CodexAtlas.Glyph.LOCK, x + w - 8 - sw, y + 6, fade(got ? C_ACCENT : C_MUTED, a));
			g.drawString(f, status, x + w - 8 - sw + 12, y + 7, fade(got ? C_ACCENT : C_MUTED, a), false);
			s.lines(g, this.lines, x + 36, y + 19, fade(got ? C_BODY : C_MUTED, a));
		}
	}

	// ---- the Chronicle --------------------------------------------------------------------------------

	/**
	 * One death in the Chronicle: who and how, where and when, how many levels and what echo; the coordinates are a
	 * button that puts the entry into the chat input (to keep, or to send to friends).
	 */
	static final class ChronicleCard extends Block {
		final Chronicle.Entry entry;
		List<FormattedCharSequence> cause = List.of();
		Component when = Component.empty();
		Component place = Component.empty();
		Component meta = Component.empty();
		Component coords = Component.empty();

		ChronicleCard(Chronicle.Entry entry) {
			this.entry = entry;
		}

		@Override
		int layout(CodexScreen s, int w) {
			EpitaphData d = this.entry.epitaph();
			this.cause = s.font().split(d.cause(), w - 20);
			this.when = d.unknown() ? Component.translatable("epitaph.remnants.long_ago") : Component.translatable("epitaph.remnants.day", d.day());
			ResourceLocation dim = ResourceLocation.tryParse(d.dimension());
			String key = dim == null ? "overworld" : dim.getPath();
			this.place = Component.translatableWithFallback("epitaph.remnants.dim." + key, key);
			this.meta = d.rank() > 0
				? Component.translatable("epitaph.remnants.tail", this.place, d.levels(), Component.translatable("entity.remnants_of_the_past.echo_rank." + d.rank()))
				: Component.translatable("epitaph.remnants.tail_unknown", this.place, d.levels());
			this.coords = Component.literal(d.pos().getX() + " " + d.pos().getY() + " " + d.pos().getZ());
			return this.height = 7 + LINE_H + this.cause.size() * LINE_H + 3 + LINE_H + 17 + 4;
		}

		/** The coordinates button inside the card: {x, y, w, h}. */
		private int[] button(CodexScreen s) {
			Font f = s.font();
			int w = 14 + f.width(this.coords) + 8 + f.width(Component.translatable("codex.remnants.chronicle.to_chat")) + 6;
			return new int[]{10, this.height - 19, w, 14};
		}

		@Override
		void render(CodexScreen s, GuiGraphics g, int x, int y, int w, int mx, int my, float a, boolean inView) {
			Font f = s.font();
			EpitaphData d = this.entry.epitaph();
			boolean forgotten = d.unknown();
			card(g, x, y, w, this.height, fade(C_SURFACE, a), fade(C_BORDER, a));
			g.fill(x + 3, y + 5, x + 5, y + this.height - 5, fade(forgotten ? C_MUTED : C_GOLD, a));
			g.drawString(f, d.name(), x + 10, y + 7, fade(forgotten ? C_BODY : C_GOLD, a), false);
			if (forgotten) {
				Component tag = Component.translatable("codex.remnants.chronicle.from_grave");
				g.drawString(f, tag, x + 10 + f.width(d.name()) + 6, y + 7, fade(C_MUTED, a), false);
			}
			g.drawString(f, this.when, x + w - 8 - f.width(this.when), y + 7, fade(C_MUTED, a), false);
			s.lines(g, this.cause, x + 10, y + 7 + LINE_H, fade(C_BODY, a));
			int metaY = y + 7 + LINE_H + this.cause.size() * LINE_H + 3;
			g.drawString(f, this.meta, x + 10, metaY, fade(C_MUTED, a), false);
			int[] b = this.button(s);
			boolean over = s.canHover(inView) && s.inside(mx, my, x + b[0], y + b[1], b[2], b[3]);
			card(g, x + b[0], y + b[1], b[2], b[3], fade(over ? C_SURFACE_HI : C_SURFACE, a), fade(over ? C_ACCENT : C_BORDER_HI, a));
			CodexAtlas.glyph(g, CodexAtlas.Glyph.RIGHT, x + b[0] + 3, y + b[1] + 3, fade(over ? C_ACCENT : C_MUTED, a));
			g.drawString(f, this.coords, x + b[0] + 14, y + b[1] + 3, fade(C_TEXT, a), false);
			g.drawString(f, Component.translatable("codex.remnants.chronicle.to_chat"), x + b[0] + 14 + f.width(this.coords) + 8, y + b[1] + 3,
				fade(over ? C_ACCENT : C_MUTED, a), false);
		}

		@Override
		boolean click(CodexScreen s, int x, int y, int w, double mx, double my) {
			int[] b = this.button(s);
			if (!s.inside(mx, my, x + b[0], y + b[1], b[2], b[3])) {
				return false;
			}
			EpitaphData d = this.entry.epitaph();
			String text = Component.translatable("codex.remnants.chronicle.share", d.name(), this.when, this.place,
				d.pos().getX(), d.pos().getY(), d.pos().getZ()).getString();
			Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ChatScreen(text));
			return true;
		}
	}

	// ---- 1.5: the branches of the Path ----------------------------------------------------------------

	/**
	 * One branch of the Path of the Soul: what it gives, and a button - choose it (from rank III, free), change to it
	 * (a Soul King's Heart), or a mark when it is yours. The click goes to the server (BranchPayload).
	 */
	static final class BranchCard extends Block {
		final String key;
		final ItemStack icon;
		List<FormattedCharSequence> lines = List.of();

		BranchCard(String key, ItemLike icon) {
			this.key = key;
			this.icon = new ItemStack(icon);
		}

		@Override
		int layout(CodexScreen s, int w) {
			this.lines = s.font().split(Component.translatable("resonance.remnants.branch." + this.key + ".desc"), w - 44);
			return this.height = 22 + this.lines.size() * LINE_H + 22;
		}

		private int state() {   // 0 chosen, 1 can choose, 2 can change (heart), 3 locked
			ResonancePayload r = ClientResonance.get();
			if (this.key.equals(r.branch())) {
				return 0;
			}
			if (SoulResonance.rankOf(r.points()) < r.branchRank()) {
				return 3;
			}
			return r.branch().isEmpty() ? 1 : 2;
		}

		private int[] button(CodexScreen s, int w) {
			Component label = this.label();
			int bw = s.font().width(label) + 16;
			return new int[]{w - 8 - bw, this.height - 19, bw, 14};
		}

		private Component label() {
			return switch (this.state()) {
				case 0 -> Component.translatable("codex.remnants.path.branch.chosen");
				case 1 -> Component.translatable("codex.remnants.path.branch.choose");
				case 2 -> Component.translatable("codex.remnants.path.branch.change");
				default -> Component.translatable("codex.remnants.path.branch.locked", ClientResonance.get().branchRank());
			};
		}

		@Override
		void render(CodexScreen s, GuiGraphics g, int x, int y, int w, int mx, int my, float a, boolean inView) {
			Font f = s.font();
			int state = this.state();
			card(g, x, y, w, this.height, fade(C_SURFACE, a), fade(state == 0 ? C_ACCENT : C_BORDER, a));
			g.renderItem(this.icon, x + 10, y + 7);
			g.drawString(f, Component.translatable("resonance.remnants.branch." + this.key), x + 36, y + 7, fade(state == 0 ? C_GOLD : C_TEXT, a), false);
			Component motto = Component.translatable("resonance.remnants.branch." + this.key + ".motto").withStyle(ChatFormatting.ITALIC);
			g.drawString(f, motto, x + w - 8 - f.width(motto), y + 7, fade(C_MUTED, a), false);
			s.lines(g, this.lines, x + 36, y + 19, fade(state == 3 ? C_MUTED : C_BODY, a));
			int[] b = this.button(s, w);
			boolean active = state == 1 || state == 2;
			boolean over = active && s.canHover(inView) && s.inside(mx, my, x + b[0], y + b[1], b[2], b[3]);
			card(g, x + b[0], y + b[1], b[2], b[3], fade(over ? C_SURFACE_HI : C_SURFACE, a),
				fade(state == 0 ? C_ACCENT : over ? C_ACCENT : active ? C_BORDER_HI : C_BORDER, a));
			CodexAtlas.glyph(g, state == 0 ? CodexAtlas.Glyph.CHECK : state == 3 ? CodexAtlas.Glyph.LOCK : CodexAtlas.Glyph.RIGHT,
				x + b[0] + 3, y + b[1] + 3, fade(state == 0 || over ? C_ACCENT : C_MUTED, a));
			g.drawString(f, this.label(), x + b[0] + 13, y + b[1] + 3, fade(state == 0 ? C_ACCENT : active ? C_TEXT : C_MUTED, a), false);
		}

		@Override
		boolean click(CodexScreen s, int x, int y, int w, double mx, double my) {
			int state = this.state();
			int[] b = this.button(s, w);
			if ((state == 1 || state == 2) && s.inside(mx, my, x + b[0], y + b[1], b[2], b[3])) {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.remnants.net.BranchPayload(this.key));
				Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
				return true;
			}
			return false;
		}
	}

	// ---- chapters ----------------------------------------------------------------------------------------

	private static Component t(String key) {
		return Component.translatable("codex.remnants." + key);
	}

	private static ItemStack[] stacks(ItemLike... items) {
		ItemStack[] out = new ItemStack[items.length];
		for (int i = 0; i < items.length; i++) {
			out[i] = new ItemStack(items[i]);
		}
		return out;
	}

	/** A heading, its text, and (optionally) the items it talks about. */
	private static void section(List<Block> out, Chapter c, String key, ItemLike... items) {
		out.add(new Heading(t(key + ".title"), c.color));
		out.add(new Paragraph(t(key + ".text")));
		if (items.length > 0) {
			out.add(new Items(stacks(items)));
		}
	}

	private static void recipe(List<Block> out, Chapter c, String key, String recipe) {
		out.add(new Heading(t(key + ".title"), c.color));
		out.add(new RecipeCard(RemnantsMod.id(recipe)));
		out.add(new Paragraph(t(key + ".text")));
	}

	static List<Block> build(CodexScreen s, Chapter c) {
		List<Block> out = new ArrayList<>();
		switch (c) {
			case HOME -> {
				out.add(new Hero());
				out.add(new ChapterCards());
				out.add(new RankCard(false));
			}
			case DEATH -> {
				section(out, c, "death.1");
				out.add(new Portrait(p -> p.remnant, t("death.ghost.title"), t("death.ghost.text"), 30.0F));
				section(out, c, "death.echo");
				out.add(new Callout(Kind.TIP, t("death.echo.tip")));
				section(out, c, "death.2", ModItems.REMNANT_CROWN);
				out.add(new Portrait(p -> p.crow, t("death.crows.title"), t("death.crows.text"), 40.0F));
				out.add(new Callout(Kind.TIP, t("death.crows.tip")));
				section(out, c, "death.3", ModItems.SOUL_CODEX, ModItems.SPECTRAL_COMPASS);
			}
			case BATTLE -> {
				section(out, c, "battle.1");
				out.add(new Callout(Kind.WARN, t("battle.riposte")));
				section(out, c, "battle.2", net.minecraft.world.item.Items.SHIELD, net.minecraft.world.item.Items.IRON_AXE);
				section(out, c, "battle.3");
				section(out, c, "battle.4");
				out.add(new Heading(t("battle.5.title"), c.color));
				out.add(new Callout(Kind.TIP, t("battle.5.text")));
			}
			case FRAGMENTS -> {
				section(out, c, "fragments.1", ModItems.SOUL_FRAGMENT, ModItems.CONDENSED_SOUL);
				out.add(new Portrait(p -> p.forgotten, t("fragments.forgotten.title"), t("fragments.forgotten.text"), 30.0F));
				recipe(out, c, "fragments.condensed", "condensed_soul");
				recipe(out, c, "fragments.bricks", "soul_bricks");
				recipe(out, c, "fragments.brazier", "soul_brazier");
			}
			case GRAVES -> {
				section(out, c, "graves.1", ModItems.WEATHERED_GRAVESTONE, ModItems.BURIAL_CASKET);
				recipe(out, c, "graves.dowser", "soul_dowser");
				out.add(new Callout(Kind.TIP, t("graves.dowser.tip")));
				section(out, c, "graves.dig", ModItems.BURIAL_CASKET, ModItems.SOUL_FRAGMENT, ModItems.EPITAPH);
				out.add(new Callout(Kind.WARN, t("graves.dig.warn")));
				section(out, c, "graves.epitaph", ModItems.EPITAPH);
				out.add(new Callout(Kind.LORE, t("graves.epitaph.lore")));
				out.add(new Portrait(CodexPortraits::variant, t("graves.lands.title"), t("graves.lands.text"), 30.0F));
				section(out, c, "graves.lands2", ModItems.WET_SHROUD, ModItems.SOUL_FROST, ModItems.SCARAB_SEAL);
				recipe(out, c, "graves.chakram", "bone_chakram");
				section(out, c, "graves.inlay", ModItems.BONE_CHAKRAM, ModItems.WET_SHROUD, ModItems.SOUL_FROST, ModItems.SCARAB_SEAL);
			}
			case CHRONICLE -> {
				List<Chronicle.Entry> entries = ClientChronicle.entries();
				out.add(new Paragraph(Component.translatable("codex.remnants.chronicle.intro", Chronicle.LIMIT)));
				out.add(new Heading(Component.translatable("codex.remnants.chronicle.count", entries.size(), Chronicle.LIMIT), c.color));
				if (entries.isEmpty()) {
					out.add(new Callout(Kind.LORE, t("chronicle.empty")));
				}
				for (int i = entries.size() - 1; i >= 0; i--) {   // newest first
					out.add(new ChronicleCard(entries.get(i)));
				}
			}
			case RELICS -> {
				recipe(out, c, "relics.compass", "spectral_compass");
				recipe(out, c, "relics.ward", "soul_ward");
				recipe(out, c, "relics.anchor", "soul_anchor");
				recipe(out, c, "relics.forge", "soul_forge");
				recipe(out, c, "relics.phylactery", "phylactery");
			}
			case KING -> {
				section(out, c, "king.1", ModItems.SPIRIT_ALTAR, ModItems.SOUL_PEDESTAL, ModItems.CONDENSED_SOUL);
				recipe(out, c, "king.altar", "spirit_altar");
				recipe(out, c, "king.pedestal", "soul_pedestal");
				out.add(new Portrait(p -> p.king, t("king.portrait.title"), t("king.portrait.text"), 15.0F));
				section(out, c, "king.2", ModItems.SOUL_KING_ARSENAL, ModItems.SOUL_KING_HEART);
			}
			case ARSENAL -> {
				section(out, c, "arsenal.1", ModItems.SOUL_KING_ARSENAL);
				section(out, c, "arsenal.2");
			}
			case CRAFT -> {
				recipe(out, c, "craft.hammer", "ritual_hammer");
				section(out, c, "craft.structures", ModItems.RITUAL_HAMMER);
				out.add(new Callout(Kind.TIP, t("craft.structures.tip")));
				out.add(new Heading(t("craft.pool.title"), c.color));
				out.add(new CodexCraft.Schematic(dev.remnants.multiblock.Multiblocks.pool(), dev.remnants.registry.ModBlocks.REFLECTING_BASIN.defaultBlockState(),
					t("craft.pool.schematic")));
				out.add(new Paragraph(t("craft.pool.text")));
				out.add(new Callout(Kind.TIP, t("craft.pool.tip")));
				out.add(new Callout(Kind.WARN, t("craft.pool.warn")));
				recipe(out, c, "craft.basin", "reflecting_basin");
				out.add(new Heading(t("craft.crucible.title"), c.color));
				out.add(new CodexCraft.Schematic(dev.remnants.multiblock.Multiblocks.crucible(), dev.remnants.registry.ModBlocks.SOUL_CRUCIBLE.defaultBlockState(),
					t("craft.crucible.schematic")));
				out.add(new Paragraph(t("craft.crucible.text")));
				section(out, c, "craft.rhythm");
				out.add(new Callout(Kind.TIP, t("craft.rhythm.tip")));
				out.add(new Heading(t("craft.recipes.title"), c.color));
				for (dev.remnants.crafting.CrucibleRecipes.Recipe r : dev.remnants.crafting.CrucibleRecipes.all()) {
					out.add(new CodexCraft.CrucibleRecipeCard(r));
				}
				recipe(out, c, "craft.crucible_block", "soul_crucible");
				recipe(out, c, "craft.hearth", "soul_hearth");
				recipe(out, c, "craft.bellows", "soul_bellows");
				recipe(out, c, "craft.trough", "soul_trough");
				recipe(out, c, "craft.chimney", "soul_chimney");
				out.add(new Portrait(p -> p.mourner, t("craft.mourner.title"), t("craft.mourner.text"), 30.0F));
				out.add(new Callout(Kind.LORE, t("craft.mourner.lore")));
			}
			case CRYPTS -> {
				section(out, c, "crypts.ossuary", ModItems.OSSUARY_URN, ModItems.BURIAL_CASKET);
				out.add(new Callout(Kind.TIP, t("crypts.ossuary.tip")));
				section(out, c, "crypts.gates", ModItems.OSSUARY_GATE, ModItems.CRYPT_EYE, ModItems.OSSUARY_KEY);
				out.add(new Callout(Kind.WARN, t("crypts.gates.warn")));
				out.add(new Portrait(p -> p.warden, t("crypts.warden.title"), t("crypts.warden.text"), 17.0F));
				out.add(new Callout(Kind.WARN, t("crypts.warden.warn")));
				section(out, c, "crypts.gauntlet", ModItems.OSSUARY_KEY, ModItems.WARDENS_GAUNTLET);
				out.add(new Portrait(p -> p.mimic, t("crypts.mimic.title"), t("crypts.mimic.text"), 40.0F));
				recipe(out, c, "crypts.bell", "hand_bell");
				recipe(out, c, "crypts.tether", "soul_tether");
				out.add(new Heading(t("crypts.belfry.title"), c.color));
				out.add(new CodexCraft.Schematic(dev.remnants.multiblock.Multiblocks.belfry(), dev.remnants.registry.ModBlocks.REST_STONE.defaultBlockState(),
					t("crypts.belfry.schematic")));
				out.add(new Paragraph(t("crypts.belfry.text")));
				recipe(out, c, "crypts.rest_stone", "rest_stone");
				recipe(out, c, "crypts.bell_of_rest", "bell_of_rest");
				section(out, c, "crypts.night", ModItems.CONDENSED_SOUL, ModItems.EPITAPH, ModItems.REMNANT_CROWN);
				out.add(new Callout(Kind.WARN, t("crypts.night.warn")));
				out.add(new Portrait(p -> p.keener, t("crypts.keener.title"), t("crypts.keener.text"), 24.0F));
				section(out, c, "crypts.ruin", ModItems.CRACKED_BELL_OF_REST);
			}
			case ARCHIVE -> {
				section(out, c, "archive.library", net.minecraft.world.item.Items.CHISELED_BOOKSHELF, ModItems.OSSUARY_GATE);
				out.add(new Callout(Kind.TIP, t("archive.library.tip")));
				out.add(new Portrait(p -> p.archivist, t("archive.archivist.title"), t("archive.archivist.text"), 16.0F));
				section(out, c, "archive.tomes");
				section(out, c, "archive.eyes", ModItems.CRYPT_EYE, net.minecraft.world.item.Items.BOW, ModItems.BONE_CHAKRAM);
				out.add(new Callout(Kind.WARN, t("archive.eyes.warn")));
				section(out, c, "archive.stars");
				section(out, c, "archive.spoils", ModItems.ARCHIVIST_QUILL, ModItems.SEAL_OF_MEMORY);
				section(out, c, "archive.quill", ModItems.ARCHIVIST_QUILL, net.minecraft.world.item.Items.BOOK, ModItems.CONDENSED_SOUL);
				out.add(new Callout(Kind.WARN, t("archive.quill.warn")));
				section(out, c, "archive.seal", ModItems.SEAL_OF_MEMORY, ModItems.SOUL_CRUCIBLE);
				out.add(new Heading(t("archive.memorial.title"), c.color));
				out.add(new CodexCraft.Schematic(dev.remnants.multiblock.Multiblocks.memorial(), dev.remnants.registry.ModBlocks.MEMORIAL_PROJECTOR.defaultBlockState(),
					t("archive.memorial.schematic")));
				out.add(new Paragraph(t("archive.memorial.text")));
				recipe(out, c, "archive.projector", "memorial_projector");
				recipe(out, c, "archive.niche", "epitaph_niche");
				section(out, c, "archive.record");
				out.add(new Callout(Kind.LORE, t("archive.record.lore")));
				out.add(new Heading(t("archive.astrolabe.title"), c.color));
				out.add(new CodexCraft.Schematic(dev.remnants.multiblock.Multiblocks.astrolabe(), dev.remnants.registry.ModBlocks.SOUL_ASTROLABE.defaultBlockState(),
					t("archive.astrolabe.schematic")));
				out.add(new Paragraph(t("archive.astrolabe.text")));
				out.add(new Items(stacks(ModItems.SPECTRAL_COMPASS, net.minecraft.world.item.Items.MAP, ModItems.EPITAPH)));
				recipe(out, c, "archive.sphere", "soul_astrolabe");
				recipe(out, c, "archive.crystal", "focus_crystal");
				section(out, c, "archive.anchors", ModItems.SOUL_ANCHOR, net.minecraft.world.item.Items.NAME_TAG);
				recipe(out, c, "archive.glider", "shroud_glider");
				recipe(out, c, "archive.lantern", "soul_lantern_trap");
				recipe(out, c, "archive.decoy", "decoy_echo");
			}
			case PATH -> {
				out.add(new RankCard(true));
				out.add(new Paragraph(t("path.1.text")));
				out.add(new Heading(t("path.gifts"), c.color));
				for (SoulResonance.Perk perk : SoulResonance.Perk.values()) {
					out.add(new PerkCard(perk));
				}
				out.add(new Heading(t("path.branches"), c.color));
				out.add(new Paragraph(Component.translatable("codex.remnants.path.branches.text", ClientResonance.get().branchRank())));
				out.add(new BranchCard("reaper", ModItems.SOUL_KING_ARSENAL));
				out.add(new BranchCard("keeper", net.minecraft.world.item.Items.SHIELD));
				out.add(new BranchCard("wanderer", ModItems.SOUL_TETHER));
				if (ClientResonance.get().memorial()) {
					out.add(new Callout(Kind.TIP, t("path.memorial")));
				}
			}
		}
		return out;
	}

	private CodexBlocks() {
	}
}
