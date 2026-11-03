package dev.buildminions.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import dev.buildminions.BuildMinions;
import dev.buildminions.minion.MinionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;

/**
 * All packets of the mod. Loaders iterate {@link #SERVERBOUND} / {@link #CLIENTBOUND} and register them
 * with their own networking API.
 */
public final class ModNetwork {
    private ModNetwork() {}

    public record Serverbound<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<RegistryFriendlyByteBuf, T> codec,
                                                            BiConsumer<T, ServerPlayer> handler) {}

    public record Clientbound<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                            StreamCodec<RegistryFriendlyByteBuf, T> codec,
                                                            Consumer<T> handler) {}

    public static final List<Serverbound<?>> SERVERBOUND = new ArrayList<>();
    public static final List<Clientbound<?>> CLIENTBOUND = new ArrayList<>();

    /** Client-side hooks, filled in by the client init (keeps client classes off the server). */
    public static Consumer<MinionState> onState = p -> {};
    public static Consumer<StartPlacement> onStartPlacement = p -> {};

    private static final StreamCodec<RegistryFriendlyByteBuf, Item> ITEM = ByteBufCodecs.registry(Registries.ITEM);
    public static final int CHUNK_SIZE = 30_000;

    // ================================================================= serverbound

    /** One slice of a compressed blueprint upload. */
    public record UploadChunk(int entityId, int index, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<UploadChunk> TYPE = new Type<>(BuildMinions.id("upload_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UploadChunk> CODEC = CustomPacketPayload.codec(UploadChunk::write, UploadChunk::new);

        UploadChunk(RegistryFriendlyByteBuf buf) {
            this(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(CHUNK_SIZE + 64));
        }

        void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(entityId); buf.writeVarInt(index); buf.writeVarInt(total); buf.writeByteArray(data);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Simple button actions from the minion GUI. */
    public record Action(int entityId, int action) implements CustomPacketPayload {
        public static final int REFRESH = 0, DEPOSIT = 1, ACCEPT = 2, CANCEL = 3, PICKUP = 4, CLEAR = 5, CRAFT_ALL = 6, CLEAR_QUEUE = 7;
        /** action = TOGGLE_SKIP + raw item id: skip / include that material. */
        public static final int TOGGLE_SKIP = 1 << 20;
        public static final Type<Action> TYPE = new Type<>(BuildMinions.id("action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = CustomPacketPayload.codec(Action::write, Action::new);

        Action(RegistryFriendlyByteBuf buf) { this(buf.readVarInt(), buf.readVarInt()); }

        void write(RegistryFriendlyByteBuf buf) { buf.writeVarInt(entityId); buf.writeVarInt(action); }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Final hologram position chosen by the player. */
    public record Place(int entityId, BlockPos anchor, int rotation, int mirror) implements CustomPacketPayload {
        public static final Type<Place> TYPE = new Type<>(BuildMinions.id("place"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Place> CODEC = CustomPacketPayload.codec(Place::write, Place::new);

        Place(RegistryFriendlyByteBuf buf) { this(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()); }

        void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(entityId); buf.writeBlockPos(anchor); buf.writeVarInt(rotation); buf.writeVarInt(mirror);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ================================================================= clientbound

    public record MaterialLine(Item item, int need, int have, boolean skipped) {}

    /** Workshop info: what can be crafted now, the queue and spare raw materials. */
    public record CraftLine(Item item, int count, int total, String note) {}

    /** Snapshot of a minion for the GUI. {@code open} = open the screen (otherwise just refresh it). */
    public record MinionState(int entityId, int tier, int phase, int used, int upgrade, int maxBlocks, int progress, int total,
                              String blueprint, int sizeX, int sizeY, int sizeZ, int placeable,
                              boolean creative, boolean open, List<MaterialLine> materials,
                              boolean workstation, float craftProgress, List<CraftLine> craftable, List<CraftLine> queue, List<CraftLine> raw) implements CustomPacketPayload {
        public static final Type<MinionState> TYPE = new Type<>(BuildMinions.id("minion_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MinionState> CODEC = CustomPacketPayload.codec(MinionState::write, MinionState::read);

        static MinionState read(RegistryFriendlyByteBuf buf) {
            int id = buf.readVarInt(), tier = buf.readVarInt(), phase = buf.readVarInt(), used = buf.readVarInt();
            int upgrade = buf.readVarInt(), maxBlocks = buf.readVarInt();
            int progress = buf.readVarInt(), total = buf.readVarInt();
            String bp = buf.readUtf(256);
            int sx = buf.readVarInt(), sy = buf.readVarInt(), sz = buf.readVarInt(), placeable = buf.readVarInt();
            boolean creative = buf.readBoolean(), open = buf.readBoolean();
            int n = buf.readVarInt();
            List<MaterialLine> mats = new ArrayList<>(n);
            for (int i = 0; i < n; i++) mats.add(new MaterialLine(ITEM.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
            boolean ws = buf.readBoolean();
            float cp = buf.readFloat();
            return new MinionState(id, tier, phase, used, upgrade, maxBlocks, progress, total, bp, sx, sy, sz, placeable, creative, open, mats,
                    ws, cp, readLines(buf), readLines(buf), readLines(buf));
        }

        void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(entityId); buf.writeVarInt(tier); buf.writeVarInt(phase); buf.writeVarInt(used);
            buf.writeVarInt(upgrade); buf.writeVarInt(maxBlocks);
            buf.writeVarInt(progress); buf.writeVarInt(total);
            buf.writeUtf(blueprint, 256);
            buf.writeVarInt(sizeX); buf.writeVarInt(sizeY); buf.writeVarInt(sizeZ); buf.writeVarInt(placeable);
            buf.writeBoolean(creative); buf.writeBoolean(open);
            buf.writeVarInt(materials.size());
            for (MaterialLine m : materials) { ITEM.encode(buf, m.item()); buf.writeVarInt(m.need()); buf.writeVarInt(m.have()); buf.writeBoolean(m.skipped()); }
            buf.writeBoolean(workstation);
            buf.writeFloat(craftProgress);
            writeLines(buf, craftable); writeLines(buf, queue); writeLines(buf, raw);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static List<CraftLine> readLines(RegistryFriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 256);
        List<CraftLine> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(new CraftLine(ITEM.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readUtf(128)));
        return out;
    }

    static void writeLines(RegistryFriendlyByteBuf buf, List<CraftLine> lines) {
        int n = Math.min(lines.size(), 256);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            CraftLine l = lines.get(i);
            ITEM.encode(buf, l.item()); buf.writeVarInt(l.count()); buf.writeVarInt(l.total());
            buf.writeUtf(l.note().length() > 128 ? l.note().substring(0, 128) : l.note(), 128);
        }
    }

    /** Tells the owner to start positioning the hologram. */
    public record StartPlacement(int entityId) implements CustomPacketPayload {
        public static final Type<StartPlacement> TYPE = new Type<>(BuildMinions.id("start_placement"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StartPlacement> CODEC = CustomPacketPayload.codec(StartPlacement::write, StartPlacement::new);

        StartPlacement(RegistryFriendlyByteBuf buf) { this(buf.readVarInt()); }

        void write(RegistryFriendlyByteBuf buf) { buf.writeVarInt(entityId); }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ================================================================= handlers

    private static MinionEntity find(ServerPlayer player, int entityId) {
        Entity e = player.level().getEntity(entityId);
        if (e instanceof MinionEntity m && m.isAlive() && m.distanceToSqr(player) < 128 * 128) return m;
        return null;
    }

    static {
        SERVERBOUND.add(new Serverbound<>(UploadChunk.TYPE, UploadChunk.CODEC, (p, player) -> {
            MinionEntity m = find(player, p.entityId());
            if (m != null) m.receiveUploadChunk(player, p.index(), p.total(), p.data());
        }));
        SERVERBOUND.add(new Serverbound<>(Action.TYPE, Action.CODEC, (p, player) -> {
            MinionEntity m = find(player, p.entityId());
            if (m != null) m.handleAction(player, p.action());
        }));
        SERVERBOUND.add(new Serverbound<>(Place.TYPE, Place.CODEC, (p, player) -> {
            MinionEntity m = find(player, p.entityId());
            if (m != null) m.handlePlace(player, p.anchor(), p.rotation(), p.mirror());
        }));
        CLIENTBOUND.add(new Clientbound<>(MinionState.TYPE, MinionState.CODEC, p -> onState.accept(p)));
        CLIENTBOUND.add(new Clientbound<>(StartPlacement.TYPE, StartPlacement.CODEC, p -> onStartPlacement.accept(p)));
    }
}
