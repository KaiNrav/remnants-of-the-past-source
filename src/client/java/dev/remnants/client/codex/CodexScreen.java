package dev.remnants.client.codex;

import dev.remnants.SoulResonance;
import dev.remnants.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Soul Codex (v2): a flat, modern guide panel in the mod's colours, after the way current guidebooks are built
 * (GuideME's single scrolling column and live 3D scenes, Spectrum's clean chapter tabs, Lavender's landing page with
 * category cards and a progress bar). A sidebar of chapters on the left, a header, and a smoothly scrolling column
 * of cards on the right. Motion is short and functional: the panel fades in, chapters cross-fade with a small
 * slide, the active-chapter marker glides, hover states ease in, the resonance bar fills.
 */
public class CodexScreen extends Screen {
	// palette: deep violet slate, one soul-cyan accent, gold only for ranks and headings that matter
	static final int C_DIM = 0xB00A0812, C_PANEL = 0xF5161320, C_SIDE = 0xF7110E19, C_SURFACE = 0xFF1E1929, C_SURFACE_HI = 0xFF2A2340,
		C_BORDER = 0xFF352D49, C_BORDER_HI = 0xFF5A4C7E, C_ACCENT = 0xFF56E1E6, C_ACCENT_DIM = 0xFF2B8791, C_GOLD = 0xFFE3B95E,
		C_TEXT = 0xFFEFEAF7, C_BODY = 0xFFCEC6DF, C_MUTED = 0xFF978EAF, C_FAINT = 0xFF655C80, C_WARN = 0xFFEB7A6E;
	static final int LINE_H = 10, GAP = 8;
	private static final int SIDEBAR_W = 108, HEADER_H = 26, OPEN_MS = 180, SWITCH_MS = 200;

	enum Chapter {
		HOME(ModItems.SOUL_CODEX, 0xFF9B74C9), DEATH(ModItems.SPECTRAL_COMPASS, 0xFF3FB0BF), BATTLE(Items.IRON_SWORD, 0xFFD45C57),
		FRAGMENTS(ModItems.SOUL_FRAGMENT, 0xFF5D8FE0), GRAVES(ModItems.SOUL_DOWSER, 0xFFB99560),
		CHRONICLE(ModItems.EPITAPH, 0xFFA79BBE), RELICS(ModItems.SOUL_WARD, 0xFF6DB46A), KING(ModItems.SOUL_KING_HEART, 0xFFE0A650),
		ARSENAL(ModItems.SOUL_KING_ARSENAL, 0xFF98A4C2), CRAFT(ModItems.RITUAL_HAMMER, 0xFF5FBFC4), CRYPTS(ModItems.OSSUARY_KEY, 0xFF8F86B8),
		ARCHIVE(ModItems.ARCHIVIST_QUILL, 0xFFC9A86A), PATH(ModItems.REMNANT_CROWN, 0xFFCB76C3);

		final ItemStack icon;
		final int color;

		Chapter(Item icon, int color) {
			this.icon = new ItemStack(icon);
			this.color = color;
		}

		String key() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		Component title() {
			return Component.translatable("codex.remnants.chapter." + this.key());
		}

		Component shortTitle() {
			return Component.translatable("codex.remnants.chapter." + this.key() + ".short");
		}
	}

	private static Chapter remembered = Chapter.HOME;
	private int chronicleVersion = -1;

	final CodexPortraits portraits = new CodexPortraits();
	private Chapter chapter = Chapter.HOME;
	private List<CodexBlocks.Block> blocks = new ArrayList<>();
	private int[] blockY = new int[0];
	private int contentHeight;
	private float scroll, scrollTarget;
	private long openedAt, switchedAt, lastFrame;
	private float markerY = -1;
	private final float[] hover = new float[Chapter.values().length];
	private boolean draggingBar;
	private CodexBlocks.Block dragging;
	int ticks;
	float partialTick;
	int mouseX, mouseY;
	// layout
	int px, py, pw, ph;
	private List<Component> tooltip;
	private ItemStack tooltipStack = ItemStack.EMPTY;

	public CodexScreen() {
		super(Component.translatable("item.remnants_of_the_past.soul_codex"));
	}

	public static void open() {
		Minecraft.getInstance().setScreen(new CodexScreen());
	}

	@Override
	protected void init() {
		this.pw = Math.min(430, this.width - 16);
		this.ph = Math.min(256, this.height - 12);
		this.px = (this.width - this.pw) / 2;
		this.py = (this.height - this.ph) / 2;
		this.portraits.create(this.minecraft);
		if (this.openedAt == 0) {
			this.openedAt = Util.getMillis();
			this.chapter = remembered;
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.1F, 0.6F));
		}
		this.rebuild();
	}

	Font font() {
		return this.font;
	}

	// ---- layout ------------------------------------------------------------------------------------

	int contentX() {
		return this.px + SIDEBAR_W + 12;
	}

	int contentY() {
		return this.py + HEADER_H + 8;
	}

	int contentW() {
		return this.pw - SIDEBAR_W - 12 - 14;
	}

	int contentH() {
		return this.ph - HEADER_H - 14;
	}

	private void rebuild() {
		this.chronicleVersion = ClientChronicle.version();
		this.blocks = CodexBlocks.build(this, this.chapter);
		this.blockY = new int[this.blocks.size()];
		int y = 0;
		for (int i = 0; i < this.blocks.size(); i++) {
			this.blockY[i] = y;
			y += this.blocks.get(i).layout(this, this.contentW()) + GAP;
		}
		this.contentHeight = Math.max(0, y - GAP);
		this.scrollTarget = Mth.clamp(this.scrollTarget, 0, this.maxScroll());
		this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll());
	}

	private float maxScroll() {
		return Math.max(0, this.contentHeight - this.contentH());
	}

	void openChapter(Chapter c) {
		if (c == this.chapter) {
			return;
		}
		this.chapter = c;
		remembered = c;
		this.switchedAt = Util.getMillis();
		this.scroll = 0;
		this.scrollTarget = 0;
		this.rebuild();
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6F, 0.25F));
	}

	// ---- input -------------------------------------------------------------------------------------

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		Chapter[] all = Chapter.values();
		switch (key) {
			case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> this.scrollTarget = Mth.clamp(this.scrollTarget + 30, 0, this.maxScroll());
			case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> this.scrollTarget = Mth.clamp(this.scrollTarget - 30, 0, this.maxScroll());
			case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_SPACE -> this.scrollTarget = Mth.clamp(this.scrollTarget + this.contentH() - 20, 0, this.maxScroll());
			case GLFW.GLFW_KEY_PAGE_UP -> this.scrollTarget = Mth.clamp(this.scrollTarget - this.contentH() + 20, 0, this.maxScroll());
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> this.openChapter(all[Math.min(all.length - 1, this.chapter.ordinal() + 1)]);
			case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> this.openChapter(all[Math.max(0, this.chapter.ordinal() - 1)]);
			case GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_BACKSPACE -> this.openChapter(Chapter.HOME);
			default -> {
				return super.keyPressed(key, scan, mods);
			}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		this.scrollTarget = Mth.clamp(this.scrollTarget - (float) sy * 28, 0, this.maxScroll());
		return true;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != 0) {
			return super.mouseClicked(mx, my, button);
		}
		// header buttons
		if (this.inside(mx, my, this.closeX(), this.py + 7, 13, 13)) {
			this.onClose();
			return true;
		}
		if (this.chapter != Chapter.HOME && this.inside(mx, my, this.closeX() - 17, this.py + 7, 13, 13)) {
			this.openChapter(Chapter.HOME);
			return true;
		}
		// sidebar
		for (Chapter c : Chapter.values()) {
			if (this.inside(mx, my, this.px + 4, this.rowY(c), SIDEBAR_W - 8, this.rowH())) {
				this.openChapter(c);
				return true;
			}
		}
		if (this.showResonanceCard() && this.inside(mx, my, this.px + 4, this.py + this.ph - 42, SIDEBAR_W - 8, 36)) {
			this.openChapter(Chapter.PATH);
			return true;
		}
		// scrollbar
		if (this.maxScroll() > 0 && this.inside(mx, my, this.barX() - 2, this.contentY(), 7, this.contentH())) {
			this.draggingBar = true;
			this.dragBar(my);
			return true;
		}
		// content
		if (this.inside(mx, my, this.contentX(), this.contentY(), this.contentW(), this.contentH())) {
			for (int i = 0; i < this.blocks.size(); i++) {
				int by = this.contentY() + this.blockY[i] - Math.round(this.scroll);
				CodexBlocks.Block b = this.blocks.get(i);
				if (b.click(this, this.contentX(), by, this.contentW(), mx, my)) {
					this.dragging = b;
					return true;
				}
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (this.draggingBar) {
			this.dragBar(my);
			return true;
		}
		if (this.dragging != null) {
			this.dragging.drag(dx, dy);
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		this.draggingBar = false;
		this.dragging = null;
		return super.mouseReleased(mx, my, button);
	}

	private void dragBar(double my) {
		float f = (float) ((my - this.contentY()) / this.contentH());
		this.scrollTarget = Mth.clamp(f * this.contentHeight - this.contentH() / 2.0F, 0, this.maxScroll());
	}

	boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private int closeX() {
		return this.px + this.pw - 20;
	}

	/** The sidebar fits any GUI scale: rows shrink to 14px, and the resonance card only shows when there is room. */
	private boolean showResonanceCard() {
		return (this.ph - 48 - 38) / Chapter.values().length >= 17;
	}

	private int rowH() {
		int bottom = this.ph - (this.showResonanceCard() ? 48 : 6);
		return Mth.clamp((bottom - 38) / Chapter.values().length, 14, 22);
	}

	private int rowY(Chapter c) {
		return this.py + 38 + c.ordinal() * this.rowH();
	}

	/** Cuts text to a width with an ellipsis, measuring it in its own style (bold is wider). */
	Component fit(Component text, int width) {
		if (this.font.width(text) <= width) {
			return text;
		}
		Component dots = Component.literal("…").withStyle(text.getStyle());
		String head = this.font.substrByWidth(text, width - this.font.width(dots)).getString();
		return Component.literal(head.stripTrailing()).withStyle(text.getStyle()).append(dots);
	}

	private int barX() {
		return this.px + this.pw - 8;
	}

	@Override
	public void tick() {
		this.ticks++;
		this.portraits.tick();
		if (this.chapter == Chapter.CHRONICLE && this.chronicleVersion != ClientChronicle.version()) {
			this.rebuild(); // a new entry arrived while the page was open
		}
	}

	@Override
	public void removed() {
		remembered = this.chapter;
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	// ---- rendering ---------------------------------------------------------------------------------

	static float easeOut(float t) {
		t = Mth.clamp(t, 0, 1);
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}

	static int fade(int argb, float a) {
		int alpha = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(a, 0, 1));
		return (Math.max(alpha, 5) << 24) | (argb & 0xFFFFFF);
	}

	static int mix(int a, int b, float t) {
		t = Mth.clamp(t, 0, 1);
		int r = 0;
		for (int s = 0; s < 32; s += 8) {
			int ca = (a >>> s) & 0xFF, cb = (b >>> s) & 0xFF;
			r |= Math.round(ca + (cb - ca) * t) << s;
		}
		return r;
	}

	/** A rectangle with the corner pixels left out (the "rounded" look of modern Minecraft UI). */
	static void box(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x + 1, y, x + w - 1, y + h, color);
		g.fill(x, y + 1, x + 1, y + h - 1, color);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	/** A 1px outline with rounded corners. */
	static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x + 1, y, x + w - 1, y + 1, color);
		g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
		g.fill(x, y + 1, x + 1, y + h - 1, color);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	static void card(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
		box(g, x, y, w, h, border);
		box(g, x + 1, y + 1, w - 2, h - 2, fill);
		g.fill(x + 2, y + 1, x + w - 2, y + 2, mix(fill, 0xFFFFFFFF, 0.06F)); // a hairline of light on the top edge
	}

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
		float open = easeOut((Util.getMillis() - this.openedAt) / (float) OPEN_MS);
		g.fillGradient(0, 0, this.width, this.height, fade(C_DIM, open), fade(0xD8050409, open));
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		this.mouseX = mx;
		this.mouseY = my;
		this.partialTick = pt;
		this.tooltip = null;
		this.tooltipStack = ItemStack.EMPTY;
		long now = Util.getMillis();
		float dt = this.lastFrame == 0 ? 0.016F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
		this.lastFrame = now;
		this.scroll += (this.scrollTarget - this.scroll) * Math.min(1.0F, dt * 14.0F);
		if (Math.abs(this.scrollTarget - this.scroll) < 0.3F) {
			this.scroll = this.scrollTarget;
		}

		this.renderBackground(g, mx, my, pt);
		float open = easeOut((now - this.openedAt) / (float) OPEN_MS);
		int lift = Math.round((1 - open) * 10);
		int savedY = this.py;
		this.py += lift;

		// panel + shadow
		box(g, this.px - 2, this.py - 1, this.pw + 4, this.ph + 4, fade(0x60000000, open));
		card(g, this.px, this.py, this.pw, this.ph, fade(C_PANEL, open), fade(C_BORDER, open));
		this.renderSidebar(g, mx, my, dt, open);
		this.renderHeader(g, mx, my, open);
		this.renderContent(g, mx, my, now, open);
		this.py = savedY;

		if (this.tooltip != null) {
			g.renderComponentTooltip(this.font, this.tooltip, mx, my);
		} else if (!this.tooltipStack.isEmpty()) {
			g.renderTooltip(this.font, this.tooltipStack, mx, my);
		}
	}

	private void renderSidebar(GuiGraphics g, int mx, int my, float dt, float open) {
		int x = this.px + 1, y = this.py + 1;
		g.fill(x + 1, y + 1, x + SIDEBAR_W - 1, y + this.ph - 2, fade(C_SIDE, open));
		g.fill(x + SIDEBAR_W - 1, y + 1, x + SIDEBAR_W, y + this.ph - 2, fade(C_BORDER, open));
		// brand
		float glow = 0.35F + 0.15F * Mth.sin((this.ticks + this.partialTick) * 0.07F);
		CodexAtlas.glyph(g, CodexAtlas.Glyph.DOT, x + 7, y + 10, fade(C_ACCENT, open * glow * 2));
		if (open > 0.5F) {
			g.renderFakeItem(new ItemStack(ModItems.SOUL_CODEX), x + 7, y + 7);
		}
		g.drawString(this.font, Component.translatable("codex.remnants.cover"), x + 28, y + 11, fade(C_TEXT, open), false);
		g.fill(x + 6, y + 30, x + SIDEBAR_W - 7, y + 31, fade(C_BORDER, open));

		// chapters, with a marker that glides to the active one
		int rh = this.rowH();
		float target = this.rowY(this.chapter);
		this.markerY = this.markerY < 0 ? target : this.markerY + (target - this.markerY) * Math.min(1.0F, dt * 16.0F);
		box(g, this.px + 4, Math.round(this.markerY), SIDEBAR_W - 9, rh, fade(C_SURFACE_HI, open));
		g.fill(this.px + 4, Math.round(this.markerY) + 3, this.px + 6, Math.round(this.markerY) + rh - 3, fade(this.chapter.color, open));
		float iconScale = rh >= 18 ? 1.0F : 0.75F;
		int iconSize = Math.round(16 * iconScale);
		for (Chapter c : Chapter.values()) {
			int ry = this.rowY(c);
			boolean over = this.inside(mx, my, this.px + 4, ry, SIDEBAR_W - 8, rh);
			this.hover[c.ordinal()] += ((over ? 1 : 0) - this.hover[c.ordinal()]) * Math.min(1.0F, dt * 18.0F);
			if (c != this.chapter && this.hover[c.ordinal()] > 0.02F) {
				box(g, this.px + 4, ry, SIDEBAR_W - 9, rh, fade(C_SURFACE, open * this.hover[c.ordinal()]));
			}
			if (open > 0.5F) {
				g.pose().pushPose();
				g.pose().translate(this.px + 10, ry + (rh - iconSize) / 2.0F, 0);
				g.pose().scale(iconScale, iconScale, 1);
				g.renderFakeItem(c.icon, 0, 0);
				g.pose().popPose();
			}
			int color = c == this.chapter ? C_TEXT : mix(C_MUTED, C_TEXT, this.hover[c.ordinal()]);
			g.drawString(this.font, this.fit(c.shortTitle(), SIDEBAR_W - 36), this.px + 14 + iconSize, ry + (rh - 8) / 2, fade(color, open), false);
		}
		if (!this.showResonanceCard()) {
			return;
		}

		// resonance at the foot of the sidebar
		int fy = this.py + this.ph - 42;
		boolean over = this.inside(mx, my, this.px + 4, fy, SIDEBAR_W - 8, 36);
		card(g, this.px + 5, fy, SIDEBAR_W - 10, 36, fade(over ? C_SURFACE_HI : C_SURFACE, open), fade(over ? C_BORDER_HI : C_BORDER, open));
		int points = ClientResonance.get().points();
		int rank = SoulResonance.rankOf(points);
		CodexAtlas.blit(g, CodexAtlas.sigil(rank, false), this.px + 9, fy + 4);
		int tw = SIDEBAR_W - 44;
		g.drawString(this.font, this.fit(Component.translatable("resonance.remnants.rank." + rank), tw), this.px + 35, fy + 6, fade(C_GOLD, open), false);
		g.drawString(this.font, this.fit(Component.translatable("codex.remnants.ui.resonance_short", points), tw), this.px + 35, fy + 17, fade(C_MUTED, open), false);
		float frac = progressToNext(points);
		g.fill(this.px + 9, fy + 29, this.px + SIDEBAR_W - 9, fy + 31, fade(C_BORDER, open));
		g.fill(this.px + 9, fy + 29, this.px + 9 + Math.round((SIDEBAR_W - 18) * frac * open), fy + 31, fade(C_ACCENT, open));
	}

	static float progressToNext(int points) {
		int rank = SoulResonance.rankOf(points);
		if (rank >= SoulResonance.THRESHOLDS.length) {
			return 1.0F;
		}
		int prev = rank == 0 ? 0 : SoulResonance.THRESHOLDS[rank - 1];
		return (points - prev) / (float) (SoulResonance.THRESHOLDS[rank] - prev);
	}

	private void renderHeader(GuiGraphics g, int mx, int my, float open) {
		int x = this.contentX();
		int y = this.py + 8;
		g.fill(x, y + 1, x + 3, y + 9, fade(this.chapter.color, open));
		g.drawString(this.font, this.chapter.title(), x + 7, y + 1, fade(C_TEXT, open), false);
		int crumbX = x + 7 + this.font.width(this.chapter.title()) + 8;
		Component crumb = Component.translatable("codex.remnants.chapter." + this.chapter.key() + ".desc");
		if (crumbX + 40 < this.closeX() - 22) {
			String s = this.font.plainSubstrByWidth(crumb.getString(), this.closeX() - 24 - crumbX);
			g.drawString(this.font, s, crumbX, y + 1, fade(C_FAINT, open), false);
		}
		this.headerButton(g, CodexAtlas.Glyph.CLOSE, this.closeX(), this.py + 7, mx, my, open, "codex.remnants.ui.close");
		if (this.chapter != Chapter.HOME) {
			this.headerButton(g, CodexAtlas.Glyph.HOME, this.closeX() - 17, this.py + 7, mx, my, open, "codex.remnants.ui.home");
		}
		g.fill(this.px + SIDEBAR_W + 1, this.py + HEADER_H, this.px + this.pw - 1, this.py + HEADER_H + 1, fade(C_BORDER, open));
	}

	private void headerButton(GuiGraphics g, CodexAtlas.Glyph glyph, int x, int y, int mx, int my, float open, String tip) {
		boolean over = this.inside(mx, my, x, y, 13, 13);
		if (over) {
			box(g, x, y, 13, 13, fade(C_SURFACE_HI, open));
			this.tooltip = List.of(Component.translatable(tip));
		}
		CodexAtlas.glyph(g, glyph, x + 2, y + 2, fade(over ? C_TEXT : C_MUTED, open));
	}

	private void renderContent(GuiGraphics g, int mx, int my, long now, float open) {
		float sw = this.switchedAt == 0 ? 1 : easeOut((now - this.switchedAt) / (float) SWITCH_MS);
		float a = open * sw;
		int slide = Math.round((1 - sw) * 10);
		int cx = this.contentX() + slide, cy = this.contentY(), cw = this.contentW(), ch = this.contentH();
		g.enableScissor(this.contentX(), cy, this.contentX() + cw + 1, cy + ch);
		int sc = Math.round(this.scroll);
		for (int i = 0; i < this.blocks.size(); i++) {
			int by = cy + this.blockY[i] - sc;
			CodexBlocks.Block b = this.blocks.get(i);
			if (by > cy + ch || by + b.height < cy) {
				continue;
			}
			b.render(this, g, cx, by, cw, mx, my, a, my >= cy && my < cy + ch);
		}
		g.disableScissor();
		// soft fades at the top and bottom edge of the scroll area
		if (sc > 0) {
			g.fillGradient(this.contentX(), cy, this.contentX() + cw, cy + 8, fade(C_PANEL, open), 0x00161320);
		}
		if (sc < this.maxScroll()) {
			g.fillGradient(this.contentX(), cy + ch - 10, this.contentX() + cw, cy + ch, 0x00161320, fade(C_PANEL, open));
		}
		// scrollbar
		if (this.maxScroll() > 0) {
			int bx = this.barX();
			g.fill(bx, cy, bx + 2, cy + ch, fade(C_BORDER, open));
			int thumb = Math.max(18, Math.round(ch * ch / (float) this.contentHeight));
			int ty = cy + Math.round((ch - thumb) * (this.scroll / this.maxScroll()));
			boolean over = this.draggingBar || this.inside(mx, my, bx - 2, cy, 7, ch);
			g.fill(bx, ty, bx + 2, ty + thumb, fade(over ? C_ACCENT : C_ACCENT_DIM, open));
		}
	}

	// ---- helpers for blocks ------------------------------------------------------------------------

	boolean canHover(boolean inView) {
		return inView && Util.getMillis() - this.openedAt > OPEN_MS;
	}

	void setTooltip(List<Component> lines) {
		this.tooltip = lines;
	}

	void item(GuiGraphics g, ItemStack stack, int x, int y, boolean inView) {
		if (stack.isEmpty()) {
			return;
		}
		g.renderFakeItem(stack, x, y);
		g.renderItemDecorations(this.font, stack, x, y);
		if (this.canHover(inView) && this.inside(this.mouseX, this.mouseY, x, y, 16, 16)) {
			this.tooltipStack = stack;
		}
	}

	void lines(GuiGraphics g, List<FormattedCharSequence> lines, int x, int y, int color) {
		for (int i = 0; i < lines.size(); i++) {
			g.drawString(this.font, lines.get(i), x, y + i * LINE_H, color, false);
		}
	}

	/** A model turning slowly in a box (or by the mouse: dragging sets {@code yaw}). */
	void entity(GuiGraphics g, LivingEntity e, int x0, int y0, int x1, int y1, float scale, float yaw) {
		if (e == null) {
			return;
		}
		g.enableScissor(x0, y0, x1, y1); // the scissor stack intersects this with the scroll area
		float cx = (x0 + x1) / 2.0F;
		float cy = (y0 + y1) / 2.0F;
		Quaternionf pose = new Quaternionf().rotateZ(Mth.PI);
		Quaternionf camera = new Quaternionf().rotateX(-8.0F * Mth.DEG_TO_RAD);
		pose.mul(camera);
		float body = e.yBodyRot, rot = e.getYRot(), pitch = e.getXRot(), head = e.yHeadRot, headO = e.yHeadRotO;
		e.yBodyRot = 180.0F + yaw;
		e.setYRot(180.0F + yaw);
		e.setXRot(0.0F);
		e.yHeadRot = e.getYRot();
		e.yHeadRotO = e.getYRot();
		InventoryScreen.renderEntityInInventory(g, cx, cy, scale, new Vector3f(0.0F, e.getBbHeight() / 2.0F, 0.0F), pose, camera, e);
		e.yBodyRot = body;
		e.setYRot(rot);
		e.setXRot(pitch);
		e.yHeadRot = head;
		e.yHeadRotO = headO;
		g.disableScissor();
	}
}
