package dev.remnants.client.bossbar;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.remnants.RemnantsMod;
import dev.remnants.bossbar.BossStyle;
import dev.remnants.client.mixin.BossHealthOverlayAccessor;
import dev.remnants.net.BossStylePayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent.BossBarColor;

/**
 * The mod's bosses' bars, each in its own frame: the frame with its empty channel, the fill (its row picked by the bar's colour, i.e. the boss's phase,
 * scrolling slowly), a pale trail where health was just lost, the ornaments that lie over the bar, the animated light,
 * and the name under it in the boss's colour, outlined. Bars of other mods keep vanilla's look in the same stack.
 */
public final class BossBars {
	/** Bar id -> style, from {@link BossStylePayload} (sent just before the bar's own packet). */
	private static final Map<UUID, BossStyle> STYLES = new HashMap<>();
	private static final Map<UUID, Trail> TRAILS = new HashMap<>();
	private static final int FILL_W = 64, GLOW_FRAMES = 4, INK = 0xFF0D0914;
	/** A compact bar: the name (10 px) over the track cut from its frame with this margin round it. */
	private static final int COMPACT_PAD_X = 4, COMPACT_PAD_Y = 3, COMPACT_SPAN = 10 + 6 + 2 * COMPACT_PAD_Y + 3;
	private static final int VANILLA_SPAN = 19;

	/** Must match the frame textures in textures/gui/bossbar. */
	record Layout(BossStyle style, int w, int h, int tx, int ty, int tw, int th, int nameY, int nameColor, BossBarColor... rows) {
		ResourceLocation tex(String layer) {
			return RemnantsMod.id("textures/gui/bossbar/" + this.style.id() + "_" + layer + ".png");
		}

		/** The fill row for the bar's colour: each boss turns its bar another colour per phase. */
		int row(BossBarColor color) {
			for (int i = 0; i < this.rows.length; i++) {
				if (this.rows[i] == color) {
					return i;
				}
			}
			return 0;
		}

		/** Height the bar takes in the stack, its name included. */
		int span() {
			return this.nameY >= 0 ? Math.max(this.h, this.nameY + 10) : this.h;
		}
	}

	private static final Map<BossStyle, Layout> LAYOUTS = new EnumMap<>(BossStyle.class);

	static {
		put(new Layout(BossStyle.KING, 236, 50, 27, 33, 182, 6, 50, 0xF5BF44, BossBarColor.PURPLE, BossBarColor.BLUE, BossBarColor.RED));
		put(new Layout(BossStyle.WARDEN, 236, 58, 27, 28, 182, 6, 50, 0xE9DFCA, BossBarColor.WHITE, BossBarColor.RED));
		put(new Layout(BossStyle.ARCHIVIST, 240, 54, 29, 30, 182, 6, 50, 0xEFDCB4, BossBarColor.PURPLE, BossBarColor.BLUE, BossBarColor.RED));
		put(new Layout(BossStyle.ECHO, 232, 50, 25, 32, 182, 6, 48, 0xAAE1FF, BossBarColor.BLUE, BossBarColor.PURPLE, BossBarColor.RED));
		put(new Layout(BossStyle.NIGHT, 228, 54, 23, 22, 182, 6, 50, 0xC8A0FF, BossBarColor.PURPLE));
		put(new Layout(BossStyle.POISE, 128, 13, 18, 5, 104, 3, -1, 0xFFFFFF, BossBarColor.WHITE));
	}

	private static void put(Layout l) {
		LAYOUTS.put(l.style(), l);
	}

	/** Health just lost, shown fading behind the fill: it holds a moment after each hit, then drains to the bar. */
	private static final class Trail {
		float value, last;
		long hold, time;
		/** When health last dropped, to pick the bar in focus. */
		long lastHit = Long.MIN_VALUE;

		Trail(float p, long now) {
			this.value = p;
			this.last = p;
			this.time = now;
		}
	}

	private BossBars() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(BossStylePayload.TYPE, (payload, context) -> {
			BossStyle style = BossStyle.byOrdinal(payload.style());
			if (style != null) {
				STYLES.put(payload.bar(), style);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			STYLES.clear();
			TRAILS.clear();
		});
	}

	static Map<UUID, LerpingBossEvent> events() {
		return ((BossHealthOverlayAccessor) Minecraft.getInstance().gui.getBossOverlay()).remnants$events();
	}

	/** The style of the bar with the most important theme on screen (BossStyle order), or null. */
	public static BossStyle musicStyle() {
		BossStyle best = null;
		for (UUID id : events().keySet()) {
			BossStyle s = STYLES.get(id);
			if (s != null && s.music != null && (best == null || s.ordinal() < best.ordinal())) {
				best = s;
			}
		}
		return best;
	}

	/** The bar that gets the full frame; the rest of the stack is drawn compact. Sticky until another bar is hit. */
	private static UUID lead;

	/**
	 * Draws the whole stack when any bar on it is ours; false leaves it to vanilla. Only the lead bar (the most
	 * important boss, and among equals the one hit last) gets its full frame and its poise bar; every other bar is a
	 * compact strip, and the stack stops at a third of the screen like vanilla's, with a "+N" for the rest. Before
	 * this, three echoes at once covered half the screen.
	 */
	public static boolean render(GuiGraphics g, BossHealthOverlay overlay) {
		Map<UUID, LerpingBossEvent> events = ((BossHealthOverlayAccessor) overlay).remnants$events();
		if (events.isEmpty() || events.keySet().stream().noneMatch(STYLES::containsKey)) {
			return false;
		}
		if (STYLES.size() > 64) {
			STYLES.keySet().removeIf(id -> !events.containsKey(id));
			TRAILS.keySet().removeIf(id -> !events.containsKey(id));
		}
		Minecraft mc = Minecraft.getInstance();
		long now = Util.getMillis();
		int width = g.guiWidth();
		int limit = Math.max(g.guiHeight() / 3, 70);
		pickLead(events, now);

		// the lead first (with its poise bar, which the server adds right after its echo's bar), then the rest in order
		LerpingBossEvent leadEvent = lead != null ? events.get(lead) : null;
		LerpingBossEvent leadPoise = null;
		LerpingBossEvent prev = null;
		for (LerpingBossEvent e : events.values()) {
			if (prev != null && prev == leadEvent && STYLES.get(e.getId()) == BossStyle.POISE) {
				leadPoise = e;
			}
			prev = e;
		}
		List<LerpingBossEvent> order = new ArrayList<>(events.size());
		if (leadEvent != null) {
			order.add(leadEvent);
			if (leadPoise != null) {
				order.add(leadPoise);
			}
		}
		for (LerpingBossEvent e : events.values()) {
			if (e != leadEvent && e != leadPoise && STYLES.get(e.getId()) != BossStyle.POISE) {
				order.add(e);   // other echoes' poise bars are left out: only the fight in focus needs one
			}
		}

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		int y = 2;
		for (int i = 0; i < order.size(); i++) {
			LerpingBossEvent e = order.get(i);
			Layout l = LAYOUTS.get(STYLES.get(e.getId()));
			boolean full = l != null && (e == leadEvent || l.style() == BossStyle.POISE);
			int span = l == null ? VANILLA_SPAN : full ? l.span() + 1 : COMPACT_SPAN;
			if (i > 0 && y + span > limit) {
				String more = "+" + (order.size() - i);
				g.drawString(mc.font, more, width / 2 - mc.font.width(more) / 2, y, 0xFFB8B8C8);
				break;
			}
			if (l == null) {
				// another mod's bar, as vanilla draws it: the name over a plain bar
				Component name = e.getName();
				g.drawString(mc.font, name, width / 2 - mc.font.width(name) / 2, y + 1, 0xFFFFFF);
				((BossHealthOverlayAccessor) overlay).remnants$drawBar(g, width / 2 - 91, y + 11, e);
			} else if (full) {
				if (l.style() == BossStyle.POISE) {
					y -= 2;   // tucked under its echo's bar
				}
				draw(g, mc.font, l, e, width / 2 - l.w() / 2, y, now);
			} else {
				drawCompact(g, mc.font, l, e, width / 2, y, now);
			}
			y += span;
		}
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.disableBlend();
		return true;
	}

	/** Keeps the lead unless it is gone, a more important boss shows up, or a bar of the same rank was hit later. */
	private static void pickLead(Map<UUID, LerpingBossEvent> events, long now) {
		UUID best = null;
		BossStyle bestStyle = null;
		long bestHit = Long.MIN_VALUE;
		for (LerpingBossEvent e : events.values()) {
			BossStyle s = STYLES.get(e.getId());
			if (s == null || s == BossStyle.POISE) {
				continue;
			}
			long hit = trailOf(e.getId(), Mth.clamp(e.getProgress(), 0.0F, 1.0F), now).lastHit;
			if (best == null || s.ordinal() < bestStyle.ordinal() || s == bestStyle && hit > bestHit) {
				best = e.getId();
				bestStyle = s;
				bestHit = hit;
			}
		}
		if (best != null && lead != null && events.containsKey(lead) && STYLES.get(lead) == bestStyle
			&& TRAILS.get(lead).lastHit >= bestHit) {
			return;   // sticky: no reshuffling while nothing new happens
		}
		lead = best;
	}

	/** A bar off the lead: just its track (cut from its frame) with the fill, the name above it in the boss's colour. */
	private static void drawCompact(GuiGraphics g, Font font, Layout l, LerpingBossEvent e, int cx, int y, long now) {
		String name = e.getName().getString();
		g.drawString(font, name, cx - font.width(name) / 2, y, 0xFF000000 | l.nameColor(), true);
		int u = l.tx() - COMPACT_PAD_X, v = l.ty() - COMPACT_PAD_Y;
		int w = l.tw() + 2 * COMPACT_PAD_X, h = l.th() + 2 * COMPACT_PAD_Y;
		int x = cx - w / 2, top = y + 10;
		g.blit(l.tex("frame"), x, top, u, v, w, h, l.w(), l.h());
		float p = Mth.clamp(e.getProgress(), 0.0F, 1.0F);
		int filled = Math.round(l.tw() * p);
		int trail = Math.round(l.tw() * trail(e.getId(), p, now));
		int bx = x + COMPACT_PAD_X, by = top + COMPACT_PAD_Y;
		if (trail > filled) {
			g.fill(bx + filled, by, bx + trail, by + l.th(), 0xB4FFF0E0);
		}
		int row = Math.min(l.row(e.getColor()), l.rows().length - 1);
		for (int dx = 0; dx < filled; ) {
			int fu = dx % FILL_W;
			int seg = Math.min(FILL_W - fu, filled - dx);
			g.blit(l.tex("fill"), bx + dx, by, fu, row * l.th(), seg, l.th(), FILL_W, l.th() * l.rows().length);
			dx += seg;
		}
	}

	private static void draw(GuiGraphics g, Font font, Layout l, LerpingBossEvent e, int x, int y, long now) {
		g.blit(l.tex("frame"), x, y, 0, 0, l.w(), l.h(), l.w(), l.h());
		float p = Mth.clamp(e.getProgress(), 0.0F, 1.0F);
		int filled = Math.round(l.tw() * p);
		int trail = Math.round(l.tw() * trail(e.getId(), p, now));
		int bx = x + l.tx(), by = y + l.ty();
		if (trail > filled) {
			g.fill(bx + filled, by, bx + trail, by + l.th(), 0xB4FFF0E0);
		}
		int row = Math.min(l.row(e.getColor()), l.rows().length - 1);
		if (l.style() == BossStyle.POISE && e.getColor() == BossBarColor.YELLOW) {
			RenderSystem.setShaderColor(1.0F, 0.85F, 0.45F, 1.0F);   // guard can't be broken right now
		}
		int scroll = (int) ((now / 60L) % FILL_W);
		for (int dx = 0; dx < filled; ) {
			int u = (scroll + dx) % FILL_W;
			int seg = Math.min(FILL_W - u, filled - dx);
			g.blit(l.tex("fill"), bx + dx, by, u, row * l.th(), seg, l.th(), FILL_W, l.th() * l.rows().length);
			dx += seg;
		}
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		if (filled > 0 && filled < l.tw()) {
			g.fill(bx + filled - 1, by, bx + filled, by + l.th(), 0x9CFFFFFF);   // the leading edge
		}
		g.blit(l.tex("front"), x, y, 0, 0, l.w(), l.h(), l.w(), l.h());
		int frame = (int) ((now / 150L) % GLOW_FRAMES);
		g.blit(l.tex("glow"), x, y, 0, frame * l.h(), l.w(), l.h(), l.w(), l.h() * GLOW_FRAMES);
		if (l.nameY() >= 0) {
			String name = e.getName().getString();
			int nx = x + l.w() / 2 - font.width(name) / 2, ny = y + l.nameY();
			g.drawString(font, name, nx - 1, ny, INK, false);
			g.drawString(font, name, nx + 1, ny, INK, false);
			g.drawString(font, name, nx, ny - 1, INK, false);
			g.drawString(font, name, nx, ny + 1, INK, false);
			g.drawString(font, name, nx, ny, 0xFF000000 | l.nameColor(), false);
		}
	}

	/** The bar's trail, advanced to now (called for every bar each frame, drawn or not). */
	private static Trail trailOf(UUID id, float p, long now) {
		Trail t = TRAILS.computeIfAbsent(id, k -> new Trail(p, now));
		if (t.time == now) {
			return t;   // already advanced this frame
		}
		float dt = Math.min(0.25F, (now - t.time) / 1000.0F);
		t.time = now;
		if (p < t.last - 1.0E-4F) {
			t.hold = now + 550L;   // each new hit holds the trail a little longer, so a combo reads as one chunk
			t.lastHit = now;
		}
		t.last = p;
		if (p >= t.value) {
			t.value = p;
		} else if (now > t.hold) {
			t.value = Math.max(p, t.value - dt * 0.45F);
		}
		return t;
	}

	private static float trail(UUID id, float p, long now) {
		return trailOf(id, p, now).value;
	}
}
