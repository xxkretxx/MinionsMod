package dev.buildminions.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import dev.buildminions.BuildMinions;
import dev.buildminions.minion.MinionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;

/**
 * All packets of the mod (1.20.1: plain id + FriendlyByteBuf messages). Loaders iterate
 * {@link #SERVERBOUND} / {@link #CLIENTBOUND} and register them with their own networking API.
 */
public final class ModNetwork {
    private ModNetwork() {}

    /** A message that knows its channel id and how to write itself. */
    public interface Msg {
        ResourceLocation id();
        void write(FriendlyByteBuf buf);
    }

    public record Serverbound<T extends Msg>(ResourceLocation id, Class<T> type, Function<FriendlyByteBuf, T> decoder, BiConsumer<T, ServerPlayer> handler) {}

    public record Clientbound<T extends Msg>(ResourceLocation id, Class<T> type, Function<FriendlyByteBuf, T> decoder, Consumer<T> handler) {}

    public static final List<Serverbound<?>> SERVERBOUND = new ArrayList<>();
    public static final List<Clientbound<?>> CLIENTBOUND = new ArrayList<>();

    public static Consumer<MinionState> onState = p -> {};
    public static Consumer<StartPlacement> onStartPlacement = p -> {};

    public static final int CHUNK_SIZE = 30_000;

    static Item readItem(FriendlyByteBuf buf) { return BuiltInRegistries.ITEM.byId(buf.readVarInt()); }
    static void writeItem(FriendlyByteBuf buf, Item item) { buf.writeVarInt(BuiltInRegistries.ITEM.getId(item)); }

    // ================================================================= serverbound

    public record UploadChunk(int entityId, int index, int total, byte[] data) implements Msg {
        public static final ResourceLocation ID = BuildMinions.id("upload_chunk");
        static UploadChunk read(FriendlyByteBuf buf) { return new UploadChunk(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(CHUNK_SIZE + 64)); }
        @Override public void write(FriendlyByteBuf buf) { buf.writeVarInt(entityId); buf.writeVarInt(index); buf.writeVarInt(total); buf.writeByteArray(data); }
        @Override public ResourceLocation id() { return ID; }
    }

    public record Action(int entityId, int action) implements Msg {
        public static final int REFRESH = 0, DEPOSIT = 1, ACCEPT = 2, CANCEL = 3, PICKUP = 4, CLEAR = 5, CRAFT_ALL = 6, CLEAR_QUEUE = 7;
        /** action = TOGGLE_SKIP + raw item id: skip / include that material. */
        public static final int TOGGLE_SKIP = 1 << 20;
        public static final ResourceLocation ID = BuildMinions.id("action");
        static Action read(FriendlyByteBuf buf) { return new Action(buf.readVarInt(), buf.readVarInt()); }
        @Override public void write(FriendlyByteBuf buf) { buf.writeVarInt(entityId); buf.writeVarInt(action); }
        @Override public ResourceLocation id() { return ID; }
    }

    public record Place(int entityId, BlockPos anchor, int rotation, int mirror) implements Msg {
        public static final ResourceLocation ID = BuildMinions.id("place");
        static Place read(FriendlyByteBuf buf) { return new Place(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()); }
        @Override public void write(FriendlyByteBuf buf) { buf.writeVarInt(entityId); buf.writeBlockPos(anchor); buf.writeVarInt(rotation); buf.writeVarInt(mirror); }
        @Override public ResourceLocation id() { return ID; }
    }

    // ================================================================= clientbound

    public record MaterialLine(Item item, int need, int have, boolean skipped) {}

    public record CraftLine(Item item, int count, int total, String note) {}

    public record MinionState(int entityId, int tier, int phase, int used, int upgrade, int maxBlocks, int progress, int total,
                              String blueprint, int sizeX, int sizeY, int sizeZ, int placeable,
                              boolean creative, boolean open, List<MaterialLine> materials,
                              boolean workstation, float craftProgress, List<CraftLine> craftable, List<CraftLine> queue, List<CraftLine> raw) implements Msg {
        public static final ResourceLocation ID = BuildMinions.id("minion_state");

        static MinionState read(FriendlyByteBuf buf) {
            int id = buf.readVarInt(), tier = buf.readVarInt(), phase = buf.readVarInt(), used = buf.readVarInt();
            int upgrade = buf.readVarInt(), maxBlocks = buf.readVarInt();
            int progress = buf.readVarInt(), total = buf.readVarInt();
            String bp = buf.readUtf(256);
            int sx = buf.readVarInt(), sy = buf.readVarInt(), sz = buf.readVarInt(), placeable = buf.readVarInt();
            boolean creative = buf.readBoolean(), open = buf.readBoolean();
            int n = buf.readVarInt();
            List<MaterialLine> mats = new ArrayList<>(n);
            for (int i = 0; i < n; i++) mats.add(new MaterialLine(readItem(buf), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
            boolean ws = buf.readBoolean();
            float cp = buf.readFloat();
            return new MinionState(id, tier, phase, used, upgrade, maxBlocks, progress, total, bp, sx, sy, sz, placeable, creative, open, mats,
                    ws, cp, readLines(buf), readLines(buf), readLines(buf));
        }

        @Override public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId); buf.writeVarInt(tier); buf.writeVarInt(phase); buf.writeVarInt(used);
            buf.writeVarInt(upgrade); buf.writeVarInt(maxBlocks);
            buf.writeVarInt(progress); buf.writeVarInt(total);
            buf.writeUtf(blueprint, 256);
            buf.writeVarInt(sizeX); buf.writeVarInt(sizeY); buf.writeVarInt(sizeZ); buf.writeVarInt(placeable);
            buf.writeBoolean(creative); buf.writeBoolean(open);
            buf.writeVarInt(materials.size());
            for (MaterialLine m : materials) { writeItem(buf, m.item()); buf.writeVarInt(m.need()); buf.writeVarInt(m.have()); buf.writeBoolean(m.skipped()); }
            buf.writeBoolean(workstation);
            buf.writeFloat(craftProgress);
            writeLines(buf, craftable); writeLines(buf, queue); writeLines(buf, raw);
        }

        @Override public ResourceLocation id() { return ID; }
    }

    static List<CraftLine> readLines(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 256);
        List<CraftLine> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(new CraftLine(readItem(buf), buf.readVarInt(), buf.readVarInt(), buf.readUtf(128)));
        return out;
    }

    static void writeLines(FriendlyByteBuf buf, List<CraftLine> lines) {
        int n = Math.min(lines.size(), 256);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            CraftLine l = lines.get(i);
            writeItem(buf, l.item()); buf.writeVarInt(l.count()); buf.writeVarInt(l.total());
            buf.writeUtf(l.note().length() > 128 ? l.note().substring(0, 128) : l.note(), 128);
        }
    }

    public record StartPlacement(int entityId) implements Msg {
        public static final ResourceLocation ID = BuildMinions.id("start_placement");
        static StartPlacement read(FriendlyByteBuf buf) { return new StartPlacement(buf.readVarInt()); }
        @Override public void write(FriendlyByteBuf buf) { buf.writeVarInt(entityId); }
        @Override public ResourceLocation id() { return ID; }
    }

    // ================================================================= handlers

    private static MinionEntity find(ServerPlayer player, int entityId) {
        Entity e = player.level().getEntity(entityId);
        if (e instanceof MinionEntity m && m.isAlive() && m.distanceToSqr(player) < 128 * 128) return m;
        return null;
    }

    static {
        SERVERBOUND.add(new Serverbound<>(UploadChunk.ID, UploadChunk.class, UploadChunk::read, (p, player) -> {
            MinionEntity m = find(player, p.entityId());
            if (m != null) m.receiveUploadChunk(player, p.index(), p.total(), p.data());
        }));
        SERVERBOUND.add(new Serverbound<>(Action.ID, Action.class, Action::read, (p, player) -> {
            MinionEntity m = find(player, p.entityId());
            if (m != null) m.handleAction(player, p.action());
        }));
        SERVERBOUND.add(new Serverbound<>(Place.ID, Place.class, Place::read, (p, player) -> {
            MinionEntity m = find(player, p.entityId());
            if (m != null) m.handlePlace(player, p.anchor(), p.rotation(), p.mirror());
        }));
        CLIENTBOUND.add(new Clientbound<>(MinionState.ID, MinionState.class, MinionState::read, p -> onState.accept(p)));
        CLIENTBOUND.add(new Clientbound<>(StartPlacement.ID, StartPlacement.class, StartPlacement::read, p -> onStartPlacement.accept(p)));
    }
}
