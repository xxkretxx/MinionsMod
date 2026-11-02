package dev.buildminions.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.mojang.blaze3d.platform.InputConstants;
import dev.buildminions.minion.MinionPhase;
import dev.buildminions.minion.MinionTier;
import dev.buildminions.net.ModNetwork;
import dev.buildminions.platform.Services;
import dev.buildminions.schematic.Schematic;
import dev.buildminions.schematic.SchematicReader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * The minion's control panel. Three pages:
 * <ol>
 *   <li><b>Blueprints</b> – pick a schematic from .minecraft/schematics</li>
 *   <li><b>Materials</b> – what is needed / already delivered, deposit from inventory</li>
 *   <li><b>Overview</b> – size, build time, durability and the big accept slot in the middle</li>
 * </ol>
 * Everything is drawn with the mod's own sprites; clicks are hit-tested against areas
 * registered while drawing.
 */
public class MinionScreen extends Screen {
    private static final int W = 316, H = 206;
    private enum Tab { BLUEPRINTS, MATERIALS, OVERVIEW }

    private record Hit(int x, int y, int w, int h, Runnable action) {}
    private record FileEntry(Path path, String label) {}

    private ModNetwork.MinionState state;
    private Tab tab;
    private int filePage, matPage, selected = -1;
    private List<FileEntry> files = List.of();
    private boolean scanning;
    private CompletableFuture<Schematic> loading;
    private Schematic preview;
    private String error;
    private final List<Hit> hits = new ArrayList<>();
    private int left, top;

    public MinionScreen(ModNetwork.MinionState state) {
        super(Component.translatable(MinionTier.byId(state.tier()).translationKey()));
        this.state = state;
        this.tab = state.blueprint().isEmpty() ? Tab.BLUEPRINTS : Tab.OVERVIEW;
        if (!state.blueprint().isEmpty() && phase() == MinionPhase.COLLECTING && missing() > 0) tab = Tab.MATERIALS;
    }

    public int entityId() { return state.entityId(); }

    public void update(ModNetwork.MinionState newState) {
        boolean newBlueprint = !newState.blueprint().equals(state.blueprint()) && !newState.blueprint().isEmpty();
        this.state = newState;
        if (newBlueprint) { tab = Tab.MATERIALS; matPage = 0; }
    }

    private MinionTier tier() { return MinionTier.byId(state.tier()); }
    private MinionPhase phase() { return MinionPhase.byId(state.phase()); }

    private int missing() {
        int m = 0;
        for (ModNetwork.MaterialLine l : state.materials()) m += Math.max(0, l.need() - l.have());
        return m;
    }

    private int needTotal() {
        int n = 0;
        for (ModNetwork.MaterialLine l : state.materials()) n += l.need();
        return n;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2 + 8;
        scanFiles();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ================================================================= files

    private void scanFiles() {
        scanning = true;
        Path dir = ClientState.schematicsFolder();
        CompletableFuture.supplyAsync(() -> {
            List<FileEntry> list = new ArrayList<>();
            try {
                Files.createDirectories(dir);
                try (Stream<Path> s = Files.walk(dir, 4)) {
                    s.filter(Files::isRegularFile).filter(SchematicReader::isSupported).sorted()
                            .forEach(p -> list.add(new FileEntry(p, dir.relativize(p).toString().replace('\\', '/'))));
                }
            } catch (Exception ignored) {}
            return list;
        }).thenAccept(list -> minecraft.execute(() -> { files = list; scanning = false; }));
    }

    private void select(int index) {
        selected = index;
        preview = null;
        error = null;
        Path p = files.get(index).path();
        loading = CompletableFuture.supplyAsync(() -> {
            try { return SchematicReader.read(p); } catch (Exception e) { throw new RuntimeException(e.getMessage(), e); }
        });
    }

    private static void openFolder(Path dir) {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String[] cmd = os.contains("win") ? new String[]{"explorer", dir.toString()}
                : os.contains("mac") ? new String[]{"open", dir.toString()}
                : new String[]{"xdg-open", dir.toString()};
        try { new ProcessBuilder(cmd).start(); } catch (Exception ignored) {}
    }

    // ================================================================= drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        pendingTooltip = null;
        g.fill(0, 0, width, height, 0xA0080A12);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        hits.clear();
        if (loading != null && loading.isDone()) {
            try { preview = loading.join(); } catch (Exception e) {
                error = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage() : "Could not read file";
            }
            loading = null;
        }

        drawTabs(g, mouseX, mouseY);
        Ui.sprite(g, Ui.PANEL, left, top, W, H);
        Ui.sprite(g, Ui.BLUEPRINT, left + 8, top + 32, W - 16, H - 40);
        drawHeader(g);
        switch (tab) {
            case BLUEPRINTS -> drawBlueprints(g, mouseX, mouseY);
            case MATERIALS -> drawMaterials(g, mouseX, mouseY);
            case OVERVIEW -> drawOverview(g, mouseX, mouseY);
        }
        if (pendingTooltip != null) g.setTooltipForNextFrame(font, pendingTooltip, mouseX, mouseY);
    }

    private void drawTabs(GuiGraphicsExtractor g, int mx, int my) {
        String[] keys = {"gui.buildminions.tab.blueprints", "gui.buildminions.tab.materials", "gui.buildminions.tab.overview"};
        String[] icons = {"file", "chest", "clock"};
        for (int i = 0; i < 3; i++) {
            Tab t = Tab.values()[i];
            int x = left + 10 + i * 100, y = top - 20, w = 96, h = 22;
            boolean sel = tab == t;
            boolean hov = Ui.inside(mx, my, x, y, w, h);
            Ui.sprite(g, sel ? Ui.TAB_SELECTED : Ui.TAB, x, sel ? y : y + 3, w, sel ? h + 2 : h - 3);
            Ui.sprite(g, Ui.icon(icons[i]), x + 6, y + (sel ? 3 : 5), 16, 16);
            g.text(font, Component.translatable(keys[i]), x + 26, y + (sel ? 7 : 9), sel ? Ui.BRASS : hov ? Ui.WHITE : Ui.MUTED);
            hits.add(new Hit(x, y, w, h, () -> { tab = t; }));
        }
    }

    private void drawHeader(GuiGraphicsExtractor g) {
        MinionTier t = tier();
        Ui.sprite(g, Ui.HEADER, left + 30, top + 8, W - 60, 18);
        Ui.sprite(g, Ui.s("badge_" + t.id), left + 10, top + 5, 24, 24);
        g.text(font, title.copy().withStyle(ChatFormatting.BOLD), left + 40, top + 13, 0xFFFFF4D6);
        String status = Component.translatable("gui.buildminions.phase." + phase().name().toLowerCase()).getString();
        int sw = font.width(status);
        g.text(font, status, left + W - 36 - sw, top + 13, Ui.BRASS);
        // durability pill
        int remaining = Math.max(0, state.maxBlocks() - state.used());
        Ui.sprite(g, Ui.icon("heart"), left + W - 30, top + 9, 16, 16);
        Component upg = Component.translatable("gui.buildminions.upgrade." + state.upgrade());
        hitTooltip(left + W - 30, top + 9, 16, 16, Component.translatable("gui.buildminions.durability", Ui.num(remaining), Ui.num(state.maxBlocks())).append(" · ").append(upg));
        if (state.upgrade() > 0) {
            int nameW = font.width(title.copy().withStyle(ChatFormatting.BOLD));
            g.text(font, state.upgrade() == 2 ? "★★" : "★", left + 44 + nameW, top + 13, state.upgrade() == 2 ? 0xFFFF7BFF : Ui.YELLOW);
        }
    }

    // ---------------------------------------------------------------- page 1

    private void drawBlueprints(GuiGraphicsExtractor g, int mx, int my) {
        int lx = left + 14, ly = top + 38;
        if (files.isEmpty()) {
            Ui.sprite(g, Ui.INSET, lx, ly, W - 28, 126);
            Component msg = Component.translatable(scanning ? "gui.buildminions.scanning" : "gui.buildminions.no_files");
            int y = ly + 40;
            for (var line : font.split(msg, W - 60)) {
                g.text(font, line, left + W / 2 - font.width(line) / 2, y, Ui.TEXT);
                y += 11;
            }
            g.centeredText(font, Component.literal(".minecraft/schematics  (.litematic · .schem · .nbt)"), left + W / 2, ly + 96, Ui.MUTED);
            actionButton(g, mx, my, Component.translatable("gui.buildminions.open_folder"), left + W / 2 - 104, top + 172, 100, 20, true,
                    () -> openFolder(ClientState.schematicsFolder()));
            actionButton(g, mx, my, Component.translatable("gui.buildminions.refresh"), left + W / 2 + 4, top + 172, 100, 20, true, this::scanFiles);
            return;
        }
        int perPage = 6, pages = Math.max(1, (files.size() + perPage - 1) / perPage);
        filePage = Math.min(filePage, pages - 1);
        for (int i = 0; i < perPage; i++) {
            int idx = filePage * perPage + i;
            if (idx >= files.size()) break;
            int y = ly + i * 21;
            boolean sel = idx == selected, hov = Ui.inside(mx, my, lx, y, 150, 20);
            Ui.sprite(g, sel ? Ui.ROW_DONE : Ui.ROW, lx, y, 150, 20);
            Ui.sprite(g, Ui.icon("file"), lx + 3, y + 2, 16, 16);
            g.text(font, Ui.trim(font, files.get(idx).label(), 124), lx + 22, y + 6, sel ? Ui.WHITE : hov ? 0xFFFFF3C8 : Ui.TEXT);
            final int fi = idx;
            hits.add(new Hit(lx, y, 150, 20, () -> select(fi)));
        }
        pager(g, mx, my, lx, top + 166, 150, filePage, pages, p -> filePage = p);

        // details
        int dx = left + 170, dy = ly, dw = W - 184;
        Ui.sprite(g, Ui.INSET, dx, dy, dw, 126);
        int tx = dx + 6, ty = dy + 6;
        if (selected < 0) {
            for (var line : font.split(Component.translatable("gui.buildminions.pick_file"), dw - 12)) {
                g.text(font, line, tx, ty, Ui.MUTED); ty += 11;
            }
        } else if (loading != null) {
            g.text(font, Component.translatable("gui.buildminions.reading"), tx, ty, Ui.YELLOW);
        } else if (error != null) {
            g.text(font, Component.translatable("gui.buildminions.read_error"), tx, ty, Ui.RED);
            ty += 12;
            for (var line : font.split(Component.literal(error), dw - 12)) { g.text(font, line, tx, ty, Ui.MUTED); ty += 10; }
        } else if (preview != null) {
            int blocks = preview.placeableCount();
            g.text(font, Ui.trim(font, preview.name, dw - 12), tx, ty, Ui.BRASS); ty += 14;
            stat(g, "size", Component.translatable("gui.buildminions.size", preview.sizeX, preview.sizeY, preview.sizeZ), tx, ty); ty += 18;
            stat(g, "blocks", Component.translatable("gui.buildminions.blocks", Ui.num(blocks)), tx, ty); ty += 18;
            stat(g, "chest", Component.translatable("gui.buildminions.types", preview.distinctBlockTypes()), tx, ty); ty += 18;
            stat(g, "clock", Component.literal("≈ " + Ui.time(tier().estimateSeconds(blocks))), tx, ty); ty += 18;
            int remaining = Math.max(0, state.maxBlocks() - state.used());
            if (blocks > remaining) stat(g, "warning", Component.translatable("gui.buildminions.too_big_short", Ui.num(remaining)).withStyle(ChatFormatting.RED), tx, ty);
        }
        boolean canLoad = preview != null && phase().acceptsBlueprint();
        actionButton(g, mx, my, Component.translatable("gui.buildminions.load"), dx, top + 168, dw, 20, canLoad, () -> {
            ClientState.upload(state.entityId(), preview);
        });
        if (preview != null && !phase().acceptsBlueprint())
            hitTooltip(dx, top + 168, dw, 20, Component.translatable("gui.buildminions.busy"));
    }

    // ---------------------------------------------------------------- page 2

    private void drawMaterials(GuiGraphicsExtractor g, int mx, int my) {
        List<ModNetwork.MaterialLine> mats = state.materials();
        if (mats.isEmpty()) {
            emptyHint(g, "gui.buildminions.no_blueprint");
            return;
        }
        int perPage = 10, pages = Math.max(1, (mats.size() + perPage - 1) / perPage);
        matPage = Math.min(matPage, pages - 1);
        for (int i = 0; i < perPage; i++) {
            int idx = matPage * perPage + i;
            if (idx >= mats.size()) break;
            ModNetwork.MaterialLine m = mats.get(idx);
            int x = left + 14 + (i % 2) * 146, y = top + 38 + (i / 2) * 24;
            boolean done = m.have() >= m.need() || state.creative();
            Ui.sprite(g, done ? Ui.ROW_DONE : Ui.ROW, x, y, 142, 22);
            Ui.sprite(g, Ui.SLOT, x + 2, y + 2, 18, 18);
            ItemStack stack = new ItemStack(m.item());
            g.item(stack, x + 3, y + 3);
            g.text(font, Ui.trim(font, stack.getHoverName().getString(), 76), x + 24, y + 3, Ui.WHITE);
            String count = Ui.num(Math.min(m.have(), m.need())) + " / " + Ui.num(m.need());
            g.text(font, count, x + 24, y + 12, done ? Ui.GREEN : Ui.MUTED);
            Ui.progress(g, x + 104, y + 14, 34, 5, m.need() == 0 ? 1f : m.have() / (float) m.need(), done ? Ui.PROGRESS_FILL : Ui.PROGRESS_WARN);
            if (done) Ui.sprite(g, Ui.icon("check"), x + 122, y + 1, 12, 12);
            hitTooltip(x, y, 142, 22, Component.translatable("gui.buildminions.mat_tooltip", stack.getHoverName(), Ui.num(m.have()), Ui.num(m.need())));
        }
        pager(g, mx, my, left + 14, top + 160, W - 28, matPage, pages, p -> matPage = p);

        boolean collecting = phase() == MinionPhase.COLLECTING;
        actionButton(g, mx, my, Component.translatable("gui.buildminions.deposit"), left + 14, top + 176, 140, 20, collecting && !state.creative(),
                () -> Services.toServer.accept(new ModNetwork.Action(state.entityId(), ModNetwork.Action.DEPOSIT)));
        int need = needTotal(), have = need - missing();
        g.text(font, Component.translatable("gui.buildminions.materials_total", Ui.num(have), Ui.num(need)), left + 162, top + 176, Ui.TEXT);
        Ui.progress(g, left + 162, top + 187, W - 176, 8, need == 0 ? 1f : have / (float) need, missing() == 0 ? Ui.PROGRESS_FILL : Ui.PROGRESS_BLUE);
        if (state.creative())
            g.text(font, Component.translatable("gui.buildminions.creative"), left + 162, top + 166, Ui.YELLOW);
    }

    // ---------------------------------------------------------------- page 3

    private void drawOverview(GuiGraphicsExtractor g, int mx, int my) {
        if (state.blueprint().isEmpty()) {
            emptyHint(g, "gui.buildminions.no_blueprint");
            return;
        }
        MinionTier t = tier();
        int maxBlocks = state.maxBlocks();
        int remaining = Math.max(0, maxBlocks - state.used());
        int blocks = state.placeable();
        int sx = left + 12, sy = top + 36;
        Ui.sprite(g, Ui.INSET, sx, sy, 136, 162);
        int tx = sx + 6, ty = sy + 6;
        g.text(font, Ui.trim(font, state.blueprint(), 124), tx, ty, Ui.BRASS); ty += 14;
        stat(g, "size", Component.translatable("gui.buildminions.size", state.sizeX(), state.sizeY(), state.sizeZ()), tx, ty); ty += 19;
        stat(g, "blocks", Component.translatable("gui.buildminions.blocks", Ui.num(blocks)), tx, ty); ty += 19;
        stat(g, "clock", Component.translatable("gui.buildminions.time", Ui.time(t.estimateSeconds(blocks))), tx, ty); ty += 19;
        stat(g, "heart", Component.translatable("gui.buildminions.durability", Ui.num(remaining), Ui.num(maxBlocks)), tx, ty); ty += 17;
        Ui.progress(g, tx, ty, 122, 6, remaining / (float) maxBlocks, remaining < blocks ? Ui.PROGRESS_WARN : Ui.PROGRESS_FILL); ty += 12;
        if (blocks > remaining) {
            Ui.sprite(g, Ui.icon("warning"), tx, ty, 16, 16);
            int yy = ty + 1;
            for (var line : font.split(Component.translatable("gui.buildminions.too_big", Ui.num(remaining)), 102)) {
                g.text(font, line, tx + 20, yy, Ui.RED); yy += 10;
            }
        }

        int cx = left + 148 + (W - 148 - 8) / 2;
        switch (phase()) {
            case COLLECTING -> {
                boolean ready = missing() == 0 || state.creative();
                int ax = cx - 20, ay = top + 52;
                boolean hov = ready && Ui.inside(mx, my, ax, ay, 40, 40);
                Ui.sprite(g, Ui.SLOT, ax - 4, ay - 4, 48, 48);
                Ui.sprite(g, !ready ? Ui.ACCEPT_LOCKED : hov ? Ui.ACCEPT_HOVER : Ui.ACCEPT_READY, ax, ay, 40, 40);
                g.centeredText(font, Component.translatable("gui.buildminions.accept").withStyle(ChatFormatting.BOLD), cx, top + 100, ready ? Ui.GREEN : Ui.MUTED);
                Component sub = state.creative() ? Component.translatable("gui.buildminions.creative")
                        : ready ? Component.translatable("gui.buildminions.ready")
                        : Component.translatable("gui.buildminions.missing", Ui.num(missing()));
                g.centeredText(font, sub, cx, top + 114, state.creative() ? Ui.YELLOW : ready ? Ui.TEXT : Ui.RED);
                if (ready) {
                    hits.add(new Hit(ax, ay, 40, 40, () -> Services.toServer.accept(new ModNetwork.Action(state.entityId(), ModNetwork.Action.ACCEPT))));
                } else {
                    hits.add(new Hit(ax, ay, 40, 40, () -> tab = Tab.MATERIALS));
                    hitTooltip(ax, ay, 40, 40, Component.translatable("gui.buildminions.goto_materials"));
                }
                g.centeredText(font, Component.translatable("gui.buildminions.accept_hint"), cx, top + 132, Ui.MUTED);
                actionButton(g, mx, my, Component.translatable("gui.buildminions.clear"), left + 156, top + 176, 72, 20, true,
                        () -> Services.toServer.accept(new ModNetwork.Action(state.entityId(), ModNetwork.Action.CLEAR)));
            }
            case AWAITING_PLACEMENT -> {
                g.centeredText(font, Component.translatable("gui.buildminions.placing"), cx, top + 60, Ui.YELLOW);
                actionButton(g, mx, my, Component.translatable("gui.buildminions.resume_placing"), cx - 60, top + 80, 120, 20,
                        ClientState.BLUEPRINTS.containsKey(state.entityId()), () -> {
                            if (PlacementController.start(state.entityId())) onClose();
                        });
                actionButton(g, mx, my, Component.translatable("gui.buildminions.cancel"), cx - 60, top + 104, 120, 20, true,
                        () -> Services.toServer.accept(new ModNetwork.Action(state.entityId(), ModNetwork.Action.CANCEL)));
            }
            case BUILDING -> {
                float pct = state.total() == 0 ? 0f : state.progress() / (float) state.total();
                g.centeredText(font, Component.translatable("gui.buildminions.building", Math.round(pct * 100)), cx, top + 56, Ui.BRASS);
                Ui.progress(g, cx - 64, top + 70, 128, 10, pct, Ui.PROGRESS_FILL);
                g.centeredText(font, Ui.num(state.progress()) + " / " + Ui.num(state.total()), cx, top + 86, Ui.TEXT);
                int left2 = Math.max(0, state.total() - state.progress());
                g.centeredText(font, Component.translatable("gui.buildminions.time_left", Ui.time(t.estimateSeconds(left2))), cx, top + 98, Ui.MUTED);
                actionButton(g, mx, my, Component.translatable("gui.buildminions.stop"), cx - 60, top + 120, 120, 20, true,
                        () -> Services.toServer.accept(new ModNetwork.Action(state.entityId(), ModNetwork.Action.CANCEL)));
            }
            case DONE -> {
                Ui.sprite(g, Ui.icon("check"), cx - 16, top + 50, 32, 32);
                g.centeredText(font, Component.translatable("gui.buildminions.done").withStyle(ChatFormatting.BOLD), cx, top + 90, Ui.GREEN);
                actionButton(g, mx, my, Component.translatable("gui.buildminions.build_again"), cx - 60, top + 110, 120, 20, true,
                        () -> Services.toServer.accept(new ModNetwork.Action(state.entityId(), ModNetwork.Action.CANCEL)));
                actionButton(g, mx, my, Component.translatable("gui.buildminions.clear"), left + 156, top + 176, 72, 20, true,
                        () -> Services.toServer.accept(new ModNetwork.Action(state.entityId(), ModNetwork.Action.CLEAR)));
            }
            default -> {}
        }
        boolean canPick = phase() != MinionPhase.BUILDING && phase() != MinionPhase.BREAKING;
        actionButton(g, mx, my, Component.translatable("gui.buildminions.pickup"), left + W - 84, top + 176, 72, 20, canPick, () -> {
            Services.toServer.accept(new ModNetwork.Action(state.entityId(), ModNetwork.Action.PICKUP));
            onClose();
        });
    }

    // ================================================================= widgets

    private void stat(GuiGraphicsExtractor g, String icon, Component text, int x, int y) {
        Ui.sprite(g, Ui.icon(icon), x, y, 16, 16);
        g.text(font, text, x + 20, y + 4, Ui.TEXT);
    }

    private void emptyHint(GuiGraphicsExtractor g, String key) {
        Ui.sprite(g, Ui.INSET, left + 14, top + 38, W - 28, 126);
        Ui.sprite(g, Ui.icon("file"), left + W / 2 - 16, top + 64, 32, 32);
        g.centeredText(font, Component.translatable(key), left + W / 2, top + 108, Ui.MUTED);
        hits.add(new Hit(left + 14, top + 38, W - 28, 126, () -> tab = Tab.BLUEPRINTS));
    }

    private void actionButton(GuiGraphicsExtractor g, int mx, int my, Component label, int x, int y, int w, int h, boolean enabled, Runnable action) {
        boolean hov = enabled && Ui.inside(mx, my, x, y, w, h);
        Ui.button(g, font, label, x, y, w, h, enabled, hov);
        if (enabled) hits.add(new Hit(x, y, w, h, action));
    }

    private void pager(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int page, int pages, java.util.function.IntConsumer set) {
        if (pages <= 1) return;
        boolean canPrev = page > 0, canNext = page < pages - 1;
        Ui.sprite(g, Ui.icon("arrow_left"), x, y - 2, 16, 16);
        Ui.sprite(g, Ui.icon("arrow_right"), x + w - 16, y - 2, 16, 16);
        g.centeredText(font, Component.translatable("gui.buildminions.page", page + 1, pages), x + w / 2, y + 2, Ui.TEXT);
        if (canPrev) hits.add(new Hit(x, y - 2, 16, 16, () -> set.accept(page - 1)));
        if (canNext) hits.add(new Hit(x + w - 16, y - 2, 16, 16, () -> set.accept(page + 1)));
    }

    private int lastMouseX, lastMouseY;
    private Component pendingTooltip;

    private void hitTooltip(int x, int y, int w, int h, Component text) {
        if (Ui.inside(lastMouseX, lastMouseY, x, y, w, h)) pendingTooltip = text;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // the screen draws its own dim layer instead of the vanilla blur
    }

    // ================================================================= input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) { // SDL3: left = 1
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (Ui.inside(event.x(), event.y(), h.x(), h.y(), h.w(), h.h())) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    h.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int dir = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
        if (tab == Tab.BLUEPRINTS) filePage = Math.max(0, filePage + dir);
        if (tab == Tab.MATERIALS) matPage = Math.max(0, matPage + dir);
        return true;
    }
}
