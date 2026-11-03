package dev.buildminions.client;

import dev.buildminions.BuildMinions;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Small drawing helpers around the mod's GUI sprites (assets/buildminions/textures/gui/sprites). */
public final class Ui {
    public static final int WHITE = 0xFFFFFFFF, TEXT = 0xFFE6ECFF, MUTED = 0xFF9FB2D8, DARK = 0xFF2A1A08,
            GREEN = 0xFF7CF29A, RED = 0xFFFF6B6B, YELLOW = 0xFFFFD86B, BRASS = 0xFFFAD682;

    public static final ResourceLocation PANEL = s("panel"), BLUEPRINT = s("blueprint"), INSET = s("inset"), HEADER = s("header"),
            BUTTON = s("button_normal"), BUTTON_HOVER = s("button_hover"), BUTTON_DISABLED = s("button_disabled"),
            TAB = s("tab"), TAB_SELECTED = s("tab_selected"), ROW = s("row"), ROW_DONE = s("row_done"), SLOT = s("slot"),
            PROGRESS_BG = s("progress_bg"), PROGRESS_FILL = s("progress_fill"), PROGRESS_WARN = s("progress_fill_warn"),
            PROGRESS_BLUE = s("progress_fill_blue"), ACCEPT_READY = s("accept_ready"), ACCEPT_HOVER = s("accept_hover"),
            ACCEPT_LOCKED = s("accept_locked");

    private Ui() {}

    public static ResourceLocation s(String name) {
        return BuildMinions.id(name);
    }

    public static ResourceLocation icon(String name) {
        return BuildMinions.id("icon_" + name);
    }

    /** sprite name -> {texture width, height, nine-slice border (0 = stretch, -1 = tile)} (1.20.1 has no GUI sprite atlas). */
    private static final java.util.Map<String, int[]> META = new java.util.HashMap<>();
    static {
        META.put("panel", new int[]{48, 48, 8}); META.put("blueprint", new int[]{16, 16, -1}); META.put("inset", new int[]{16, 16, 2});
        for (String b : new String[]{"button_normal", "button_hover", "button_disabled"}) META.put(b, new int[]{20, 20, 3});
        META.put("tab", new int[]{24, 20, 3}); META.put("tab_selected", new int[]{24, 20, 3});
        META.put("row", new int[]{32, 22, 3}); META.put("row_done", new int[]{32, 22, 3}); META.put("header", new int[]{32, 16, 3});
        for (String b : new String[]{"progress_bg", "progress_fill", "progress_fill_warn", "progress_fill_blue"}) META.put(b, new int[]{16, 8, 1});
        META.put("slot", new int[]{18, 18, 0});
        for (String b : new String[]{"accept_ready", "accept_hover", "accept_locked"}) META.put(b, new int[]{40, 40, 0});
        for (String b : new String[]{"badge_copper", "badge_gold", "badge_diamond"}) META.put(b, new int[]{24, 24, 0});
    }

    public static void sprite(GuiGraphics g, ResourceLocation id, int x, int y, int w, int h) {
        String name = id.getPath();
        ResourceLocation tex = new ResourceLocation(id.getNamespace(), "textures/gui/sprites/" + name + ".png");
        int[] m = META.getOrDefault(name, new int[]{16, 16, 0});
        int tw = m[0], th = m[1], b = m[2];
        if (b == -1) { // tile
            for (int yy = 0; yy < h; yy += th)
                for (int xx = 0; xx < w; xx += tw) {
                    int cw = Math.min(tw, w - xx), ch = Math.min(th, h - yy);
                    g.blit(tex, x + xx, y + yy, 0, 0, cw, ch, tw, th);
                }
            return;
        }
        if (b == 0 || w < 2 * b || h < 2 * b) { g.blit(tex, x, y, w, h, 0, 0, tw, th, tw, th); return; }
        int cw = w - 2 * b, ch = h - 2 * b, ctw = tw - 2 * b, cth = th - 2 * b;
        g.blit(tex, x, y, b, b, 0, 0, b, b, tw, th);                                  // corners
        g.blit(tex, x + w - b, y, b, b, tw - b, 0, b, b, tw, th);
        g.blit(tex, x, y + h - b, b, b, 0, th - b, b, b, tw, th);
        g.blit(tex, x + w - b, y + h - b, b, b, tw - b, th - b, b, b, tw, th);
        g.blit(tex, x + b, y, cw, b, b, 0, ctw, b, tw, th);                           // edges
        g.blit(tex, x + b, y + h - b, cw, b, b, th - b, ctw, b, tw, th);
        g.blit(tex, x, y + b, b, ch, 0, b, b, cth, tw, th);
        g.blit(tex, x + w - b, y + b, b, ch, tw - b, b, b, cth, tw, th);
        g.blit(tex, x + b, y + b, cw, ch, b, b, ctw, cth, tw, th);                    // centre
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public static void button(GuiGraphics g, Font font, Component label, int x, int y, int w, int h, boolean enabled, boolean hovered) {
        sprite(g, !enabled ? BUTTON_DISABLED : hovered ? BUTTON_HOVER : BUTTON, x, y, w, h);
        int color = !enabled ? 0xFF7A7F8C : hovered ? 0xFFFFF3C8 : WHITE;
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, color);
    }

    public static void progress(GuiGraphics g, int x, int y, int w, int h, float value, ResourceLocation fill) {
        sprite(g, PROGRESS_BG, x, y, w, h);
        int fw = Math.round((w - 2) * Math.max(0f, Math.min(1f, value)));
        if (fw >= 2) sprite(g, fill, x + 1, y + 1, fw, h - 2);
    }

    public static String time(int seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }

    public static String num(int n) {
        return String.format("%,d", n).replace(' ', ' ');
    }

    public static String trim(Font font, String s, int width) {
        if (font.width(s) <= width) return s;
        return font.plainSubstrByWidth(s, width - font.width("…")) + "…";
    }
}
