package dev.remnants.client.codex;

import dev.remnants.net.AnchorJumpPayload;
import dev.remnants.net.AnchorListPayload;
import dev.remnants.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import static dev.remnants.client.codex.CodexScreen.C_ACCENT;
import static dev.remnants.client.codex.CodexScreen.C_BORDER;
import static dev.remnants.client.codex.CodexScreen.C_BORDER_HI;
import static dev.remnants.client.codex.CodexScreen.C_DIM;
import static dev.remnants.client.codex.CodexScreen.C_GOLD;
import static dev.remnants.client.codex.CodexScreen.C_MUTED;
import static dev.remnants.client.codex.CodexScreen.C_PANEL;
import static dev.remnants.client.codex.CodexScreen.C_SURFACE;
import static dev.remnants.client.codex.CodexScreen.C_SURFACE_HI;
import static dev.remnants.client.codex.CodexScreen.C_TEXT;
import static dev.remnants.client.codex.CodexScreen.C_WARN;
import static dev.remnants.client.codex.CodexScreen.card;
import static dev.remnants.client.codex.CodexScreen.fade;

/**
 * The Anchors screen (1.5), opened by Shift + right-clicking one of your Soul Anchors: your network in this dimension
 * as flat Codex cards - name, coordinates, how far - and a click on one sends you there (AnchorJumpPayload). The anchor
 * you stand at, and anchors that are gone, cannot be picked.
 */
public class AnchorScreen extends Screen {
	private static final int W = 240, ROW = 26;
	private final AnchorListPayload data;
	private int px, py, ph;
	private float scroll, open;
	private final long opened = System.currentTimeMillis();

	public AnchorScreen(AnchorListPayload data) {
		super(Component.translatable("screen.remnants.anchors"));
		this.data = data;
	}

	@Override
	protected void init() {
		this.ph = Math.min(this.height - 40, 46 + Math.max(1, this.data.anchors().size()) * ROW + 26);
		this.px = (this.width - W) / 2;
		this.py = (this.height - this.ph) / 2;
	}

	private int listTop() {
		return this.py + 38;
	}

	private int listBottom() {
		return this.py + this.ph - 24;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
		g.fill(0, 0, this.width, this.height, fade(C_DIM, this.open));
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float partial) {
		this.open = Mth.clamp((System.currentTimeMillis() - this.opened) / 160.0F, 0.0F, 1.0F);
		super.render(g, mx, my, partial);
		float a = this.open;
		card(g, this.px, this.py, W, this.ph, fade(C_PANEL, a), fade(C_BORDER, a));
		g.renderItem(new ItemStack(ModItems.SOUL_ANCHOR), this.px + 8, this.py + 8);
		g.drawString(this.font, this.title, this.px + 30, this.py + 9, fade(C_TEXT, a), false);
		Component sub = this.data.cost() > 0 ? Component.translatable("screen.remnants.anchors.cost", this.data.cost())
			: Component.translatable("screen.remnants.anchors.free");
		g.drawString(this.font, sub, this.px + 30, this.py + 20, fade(C_MUTED, a), false);
		String count = this.data.anchors().size() + "/" + this.data.max();
		g.drawString(this.font, count, this.px + W - 10 - this.font.width(count), this.py + 9, fade(C_MUTED, a), false);
		var player = Minecraft.getInstance().player;
		int top = this.listTop(), bottom = this.listBottom();
		g.enableScissor(this.px + 4, top, this.px + W - 4, bottom);
		int y = top - Math.round(this.scroll);
		for (AnchorListPayload.Entry e : this.data.anchors()) {
			boolean here = e.pos().equals(this.data.from());
			boolean usable = !here && e.present();
			boolean over = usable && mx >= this.px + 8 && mx < this.px + W - 8 && my >= Math.max(top, y) && my < Math.min(bottom, y + ROW - 4);
			card(g, this.px + 8, y, W - 16, ROW - 4, fade(over ? C_SURFACE_HI : C_SURFACE, a), fade(over ? C_ACCENT : here ? C_GOLD : C_BORDER, a));
			Component name = e.name().isEmpty() ? Component.translatable("message.remnants.anchor.unnamed", e.pos().getX(), e.pos().getZ())
				: Component.literal(e.name());
			g.drawString(this.font, name, this.px + 14, y + 3, fade(here ? C_GOLD : e.present() ? C_TEXT : C_MUTED, a), false);
			String coords = e.pos().getX() + " " + e.pos().getY() + " " + e.pos().getZ();
			g.drawString(this.font, coords, this.px + 14, y + 12, fade(C_MUTED, a), false);
			Component right;
			int color;
			if (here) {
				right = Component.translatable("screen.remnants.anchors.here");
				color = C_GOLD;
			} else if (!e.present()) {
				right = Component.translatable("screen.remnants.anchors.gone");
				color = C_WARN;
			} else {
				double d = player == null ? 0 : Math.sqrt(player.blockPosition().distSqr(e.pos()));
				right = Component.translatable("astrolabe.remnants.distance", (int) d);
				color = over ? C_ACCENT : C_MUTED;
			}
			g.drawString(this.font, right, this.px + W - 14 - this.font.width(right), y + 7, fade(color, a), false);
			y += ROW;
		}
		g.disableScissor();
		if (this.data.anchors().size() <= 1) {
			g.drawCenteredString(this.font, Component.translatable("screen.remnants.anchors.lonely"), this.px + W / 2, bottom - 12, fade(C_MUTED, a));
		}
		if (this.data.cooldown() > 0) {
			Component cd = Component.translatable("message.remnants.anchor.cooldown", this.data.cooldown() / 20 + 1);
			g.drawString(this.font, cd, this.px + 10, this.py + this.ph - 16, fade(C_WARN, a), false);
		} else {
			g.drawString(this.font, Component.translatable("screen.remnants.anchors.hint"), this.px + 10, this.py + this.ph - 16, fade(C_MUTED, a), false);
		}
		card(g, this.px + W - 18, this.py + 4, 13, 13, fade(C_SURFACE, a), fade(C_BORDER_HI, a));
		g.drawString(this.font, "x", this.px + W - 14, this.py + 6, fade(C_MUTED, a), false);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (mx >= this.px + W - 18 && mx < this.px + W - 5 && my >= this.py + 4 && my < this.py + 17) {
			this.onClose();
			return true;
		}
		int top = this.listTop(), bottom = this.listBottom();
		if (my >= top && my < bottom && mx >= this.px + 8 && mx < this.px + W - 8) {
			int i = (int) ((my - top + this.scroll) / ROW);
			if (i >= 0 && i < this.data.anchors().size()) {
				AnchorListPayload.Entry e = this.data.anchors().get(i);
				if (!e.pos().equals(this.data.from()) && e.present()) {
					ClientPlayNetworking.send(new AnchorJumpPayload(this.data.from(), e.pos()));
					this.onClose();
					return true;
				}
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		int content = this.data.anchors().size() * ROW;
		int view = this.listBottom() - this.listTop();
		this.scroll = Mth.clamp(this.scroll - (float) dy * 14.0F, 0.0F, Math.max(0, content - view));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
