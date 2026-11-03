package dev.buildminions.client;

import dev.buildminions.BuildMinions;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
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

    public static void sprite(GuiGraphics g, ResourceLocation id, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h);
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
