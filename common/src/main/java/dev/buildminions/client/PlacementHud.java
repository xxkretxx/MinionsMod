package dev.buildminions.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/** Hint panel shown at the top of the screen while the hologram is being positioned. */
public final class PlacementHud {
    private PlacementHud() {}

    public static void extract(GuiGraphics g, DeltaTracker delta) {
        if (!PlacementController.active()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = 260, h = 64;
        int x = g.guiWidth() / 2 - w / 2, y = 6;
        Ui.sprite(g, Ui.PANEL, x, y, w, h);
        Ui.sprite(g, Ui.BLUEPRINT, x + 6, y + 6, w - 12, h - 12);
        Ui.sprite(g, Ui.icon("file"), x + 10, y + 9, 16, 16);
        g.drawString(font, Component.translatable("hud.buildminions.placing", Ui.trim(font, PlacementController.schematic().name, 150)), x + 30, y + 13, Ui.BRASS);

        String rot = switch (PlacementController.rotation()) {
            case NONE -> "0°"; case CLOCKWISE_90 -> "90°"; case CLOCKWISE_180 -> "180°"; case COUNTERCLOCKWISE_90 -> "270°";
        };
        Component info = Component.translatable("hud.buildminions.info", rot,
                Component.translatable("hud.buildminions.mirror." + PlacementController.mirror().name().toLowerCase()),
                Component.translatable(PlacementController.locked() ? "hud.buildminions.locked" : "hud.buildminions.following"));
        g.drawString(font, info, x + 12, y + 28, Ui.TEXT);

        Component keys = Component.empty()
                .append(k(ModKeys.ROTATE)).append(" ").append(Component.translatable("hud.buildminions.k.rotate")).append("  ")
                .append(k(ModKeys.MIRROR)).append(" ").append(Component.translatable("hud.buildminions.k.mirror")).append("  ")
                .append(k(ModKeys.RAISE)).append("/").append(k(ModKeys.LOWER)).append(" ").append(Component.translatable("hud.buildminions.k.height"));
        Component keys2 = Component.empty()
                .append(k(ModKeys.LOCK)).append(" ").append(Component.translatable("hud.buildminions.k.lock")).append("  ")
                .append(k(ModKeys.CONFIRM)).append(" ").append(Component.translatable("hud.buildminions.k.confirm")).append("  ")
                .append(k(ModKeys.CANCEL)).append(" ").append(Component.translatable("hud.buildminions.k.cancel"));
        g.drawString(font, keys, x + 12, y + 40, Ui.MUTED);
        g.drawString(font, keys2, x + 12, y + 50, Ui.MUTED);

        int conflicts = PlacementController.conflictCount();
        if (conflicts > 0) {
            Ui.sprite(g, Ui.icon("warning"), x + w - 26, y + 9, 16, 16);
            Component c = Component.translatable("hud.buildminions.blocked", conflicts);
            g.drawString(font, c, x + w - 30 - font.width(c), y + 13, Ui.RED);
        }
    }

    private static Component k(KeyMapping key) {
        return Component.literal("[").append(key.getTranslatedKeyMessage()).append("]").withColor(0xFAD682);
    }
}
