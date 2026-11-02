package dev.buildminions.minion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import dev.buildminions.BuildMinions;
import dev.buildminions.ModContent;
import dev.buildminions.net.ModNetwork;
import dev.buildminions.platform.Services;
import dev.buildminions.schematic.Schematic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Build Minion. All building logic runs on the logical server (integrated server in singleplayer).
 */
public class MinionEntity extends Mob {
    private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_USED = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE_TICKS = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.INT);

    public static final byte EVENT_SWING = 60;
    public static final int BREAK_DURATION = 40;
    private static final Codec<Map<Item, Integer>> STORED_CODEC = Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.INT);

    // ---- server state
    private Schematic schematic;
    private final Map<Item, Integer> stored = new LinkedHashMap<>();
    private List<Schematic.Placement> queue;
    private int queueIndex;
    private int cooldown;
    private int placedThisJob, skippedThisJob;
    private BlockPos anchor;
    private Rotation rotation = Rotation.NONE;
    private Mirror mirror = Mirror.NONE;
    private boolean creativeJob;
    private UUID owner;
    private byte[][] uploadParts;
    private UUID uploader;

    // ---- client animation
    public int swingTicks;
    public int prevSwingTicks;

    public MinionEntity(EntityType<? extends MinionEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FLYING_SPEED, 0.4);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TIER, 0);
        builder.define(DATA_PHASE, 0);
        builder.define(DATA_USED, 0);
        builder.define(DATA_PHASE_TICKS, 0);
    }

    // ================================================================= accessors

    public void setup(MinionTier tier, int used, UUID owner) {
        this.entityData.set(DATA_TIER, tier.ordinal());
        this.entityData.set(DATA_USED, used);
        this.owner = owner;
        this.setCustomName(null);
    }

    public MinionTier getTier() { return MinionTier.byId(this.entityData.get(DATA_TIER)); }
    public MinionPhase getPhase() { return MinionPhase.byId(this.entityData.get(DATA_PHASE)); }
    public int getUsed() { return this.entityData.get(DATA_USED); }
    public int getRemainingBlocks() { return Math.max(0, getTier().maxBlocks - getUsed()); }
    /** Ticks since the phase last changed (synced, used for animations). */
    public int getPhaseTicks() { return this.entityData.get(DATA_PHASE_TICKS); }

    private void setPhase(MinionPhase phase) {
        this.entityData.set(DATA_PHASE, phase.ordinal());
        this.entityData.set(DATA_PHASE_TICKS, 0);
        boolean flying = phase == MinionPhase.BUILDING;
        this.setNoGravity(flying);
        this.noPhysics = flying;
    }

    public float getSwing(float partialTick) {
        return Mth.lerp(partialTick, prevSwingTicks, swingTicks) / 8f;
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected void registerGoals() { /* no AI goals – the minion only does what it is told */ }

    // ================================================================= ticking

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            prevSwingTicks = swingTicks;
            if (swingTicks > 0) swingTicks--;
            if (getPhase() == MinionPhase.BUILDING && random.nextInt(4) == 0) {
                MinionTier t = getTier();
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + (random.nextDouble() - .5) * .4, getY() + .1, getZ() + (random.nextDouble() - .5) * .4, 0, -0.05, 0);
                if (t == MinionTier.DIAMOND && random.nextInt(3) == 0)
                    level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 1.1, getZ(), 0, 0.02, 0);
            }
            return;
        }
        this.entityData.set(DATA_PHASE_TICKS, Math.min(100_000, getPhaseTicks() + 1));
        switch (getPhase()) {
            case BUILDING -> buildTick((ServerLevel) level());
            case BREAKING -> breakTick((ServerLevel) level());
            default -> {}
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_SWING) {
            swingTicks = 8;
            prevSwingTicks = 8;
        } else {
            super.handleEntityEvent(id);
        }
    }

    private void buildTick(ServerLevel level) {
        if (schematic == null || anchor == null) { setPhase(MinionPhase.COLLECTING); return; }
        if (queue == null) queue = schematic.transformed(anchor, rotation, mirror);
        // skip blocks that already match the blueprint (free)
        int guard = 0;
        while (queueIndex < queue.size() && guard++ < 64 && level.getBlockState(queue.get(queueIndex).pos()) == queue.get(queueIndex).state()) {
            queueIndex++;
        }
        if (queueIndex >= queue.size()) { finishBuild(level); return; }

        Schematic.Placement next = queue.get(queueIndex);
        Vec3 target = Vec3.atCenterOf(next.pos());
        // hover above and slightly beside the block, on the side we are coming from
        Vec3 side = new Vec3(getX() - target.x, 0, getZ() - target.z);
        side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 hover = target.add(side.scale(1.1)).add(0, 0.9, 0);
        Vec3 pos = position();
        Vec3 delta = hover.subtract(pos);
        double dist = delta.length();
        double speed = getTier().flySpeed * (dist > 12 ? 2.0 : 1.0);
        Vec3 step = dist > speed ? delta.scale(speed / dist) : delta;
        Vec3 bob = new Vec3(0, Math.sin(tickCount * 0.25) * 0.02, 0);
        setPos(pos.add(step).add(bob));
        setDeltaMovement(step);
        // look at the block
        double dx = target.x - getX(), dz = target.z - getZ(), dy = target.y - getEyeY();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        setYRot(yaw); setYHeadRot(yaw); setYBodyRot(yaw); setXRot(pitch);

        if (cooldown > 0) { cooldown--; return; }
        if (dist > 2.5) return;
        placeNext(level, next);
        cooldown = getTier().ticksPerBlock;
    }

    private void placeNext(ServerLevel level, Schematic.Placement p) {
        queueIndex++;
        BlockState current = level.getBlockState(p.pos());
        if (!current.isAir() && !current.canBeReplaced()) { skippedThisJob++; return; }
        Schematic.Cost cost = Schematic.costOf(p.state());
        if (cost != null && !creativeJob) {
            int have = stored.getOrDefault(cost.item(), 0);
            if (have < cost.count()) { skippedThisJob++; return; }
            if (have == cost.count()) stored.remove(cost.item());
            else stored.put(cost.item(), have - cost.count());
        }
        level.setBlock(p.pos(), p.state(), Block.UPDATE_ALL);
        SoundType st = p.state().getSoundType();
        level.playSound(null, p.pos(), st.getPlaceSound(), SoundSource.BLOCKS, (st.getVolume() + 1f) / 2f, st.getPitch() * 0.8f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, p.state()), p.pos().getX() + .5, p.pos().getY() + .5, p.pos().getZ() + .5, 10, .3, .3, .3, .05);
        level.broadcastEntityEvent(this, EVENT_SWING);
        placedThisJob++;
        int used = getUsed() + 1;
        this.entityData.set(DATA_USED, used);
        if (used >= getTier().maxBlocks) {
            setPhase(MinionPhase.BREAKING);
            level.playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.NEUTRAL, 1f, 1.6f);
        }
    }

    private void finishBuild(ServerLevel level) {
        setPhase(MinionPhase.DONE);
        level.playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.8f, 1.4f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1, getZ(), 20, .5, .5, .5, .1);
        notifyOwner(level, Component.translatable("message.buildminions.done", placedThisJob, skippedThisJob));
        queue = null;
        anchor = null;
    }

    private void breakTick(ServerLevel level) {
        int t = getPhaseTicks();
        if (t % 4 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.8, getZ(), 4, .2, .3, .2, 0.02);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.8, getZ(), 3, .3, .3, .3, 0.2);
        }
        if (t < BREAK_DURATION) return;
        level.playSound(null, blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.NEUTRAL, 1.2f, 0.8f);
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 0.4f, 1.8f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, getTier().particleBlock().defaultBlockState()), getX(), getY() + 0.6, getZ(), 60, .3, .4, .3, .15);
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.6, getZ(), 15, .3, .3, .3, .05);
        dropStored(level);
        notifyOwner(level, Component.translatable("message.buildminions.broke", Component.translatable(getTier().translationKey()), getTier().maxBlocks));
        discard();
    }

    // ================================================================= interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown() && getPhase() != MinionPhase.BUILDING && getPhase() != MinionPhase.BREAKING) {
            pickup(sp);
            return InteractionResult.SUCCESS;
        }
        if (getPhase() == MinionPhase.BREAKING) return InteractionResult.PASS;
        sendState(sp, true);
        return InteractionResult.SUCCESS;
    }

    public void handleAction(ServerPlayer player, int action) {
        MinionPhase phase = getPhase();
        switch (action) {
            case ModNetwork.Action.REFRESH -> {}
            case ModNetwork.Action.DEPOSIT -> { if (phase == MinionPhase.COLLECTING) deposit(player); }
            case ModNetwork.Action.ACCEPT -> {
                if (phase == MinionPhase.COLLECTING && schematic != null && (player.hasInfiniteMaterials() || missingCount() == 0)) {
                    creativeJob = player.hasInfiniteMaterials();
                    setPhase(MinionPhase.AWAITING_PLACEMENT);
                    owner = player.getUUID();
                    Services.toPlayer.accept(player, new ModNetwork.StartPlacement(getId()));
                    playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1f, 1.5f);
                    return;
                }
            }
            case ModNetwork.Action.CANCEL -> {
                if (phase == MinionPhase.BUILDING || phase == MinionPhase.AWAITING_PLACEMENT) {
                    setPhase(MinionPhase.COLLECTING);
                    queue = null;
                    anchor = null;
                }
            }
            case ModNetwork.Action.CLEAR -> {
                if (phase == MinionPhase.COLLECTING || phase == MinionPhase.DONE) {
                    schematic = null;
                    dropStoredTo(player);
                    setPhase(MinionPhase.IDLE);
                }
            }
            case ModNetwork.Action.PICKUP -> {
                if (phase != MinionPhase.BUILDING && phase != MinionPhase.BREAKING) { pickup(player); return; }
            }
            default -> {}
        }
        sendState(player, false);
    }

    public void handlePlace(ServerPlayer player, BlockPos anchor, int rot, int mir) {
        if (getPhase() != MinionPhase.AWAITING_PLACEMENT || schematic == null) return;
        if (anchor.distToCenterSqr(player.position()) > 160 * 160) {
            player.sendSystemMessage(Component.translatable("message.buildminions.too_far"));
            return;
        }
        this.anchor = anchor.immutable();
        this.rotation = Rotation.values()[Math.floorMod(rot, 4)];
        this.mirror = Mirror.values()[Math.floorMod(mir, 3)];
        this.queue = null;
        this.queueIndex = 0;
        this.placedThisJob = 0;
        this.skippedThisJob = 0;
        this.cooldown = 10;
        setPhase(MinionPhase.BUILDING);
        playSound(SoundEvents.BEACON_ACTIVATE, 0.6f, 1.6f);
        int blocks = schematic.placeableCount();
        if (blocks > getRemainingBlocks()) {
            player.sendSystemMessage(Component.translatable("message.buildminions.will_break", getRemainingBlocks(), blocks));
        }
    }

    public void receiveUploadChunk(ServerPlayer player, int index, int total, byte[] data) {
        if (!getPhase().acceptsBlueprint()) return;
        if (total <= 0 || total > 512 || index < 0 || index >= total) return;
        if (uploadParts == null || uploadParts.length != total || !player.getUUID().equals(uploader)) {
            uploadParts = new byte[total][];
            uploader = player.getUUID();
        }
        uploadParts[index] = data;
        for (byte[] part : uploadParts) if (part == null) return;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            for (byte[] part : uploadParts) bos.write(part);
            CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bos.toByteArray()), NbtAccounter.create(128L * 1024 * 1024));
            Schematic s = Schematic.load(tag);
            if (s.placeableCount() > Schematic.MAX_BLOCKS) throw new IllegalArgumentException("too many blocks");
            this.schematic = s;
            this.queue = null;
            this.anchor = null;
            setPhase(MinionPhase.COLLECTING);
            playSound(SoundEvents.BOOK_PAGE_TURN, 1f, 1f);
        } catch (Exception e) {
            BuildMinions.LOG.warn("Rejected blueprint upload from {}", player.getName().getString(), e);
            player.sendSystemMessage(Component.translatable("message.buildminions.bad_blueprint"));
        } finally {
            uploadParts = null;
        }
        sendState(player, false);
    }

    private void deposit(ServerPlayer player) {
        if (schematic == null) return;
        Inventory inv = player.getInventory();
        boolean moved = false;
        for (Map.Entry<Item, Integer> e : schematic.requirements().entrySet()) {
            int have = stored.getOrDefault(e.getKey(), 0);
            int missing = e.getValue() - have;
            for (int i = 0; i < inv.getContainerSize() && missing > 0; i++) {
                ItemStack st = inv.getItem(i);
                if (st.isEmpty() || !st.is(e.getKey())) continue;
                int take = Math.min(missing, st.getCount());
                st.shrink(take);
                missing -= take;
                have += take;
                moved = true;
            }
            if (have > 0) stored.put(e.getKey(), have);
        }
        if (moved) {
            inv.setChanged();
            playSound(SoundEvents.ITEM_PICKUP, 0.6f, 1.2f);
        }
    }

    private int missingCount() {
        if (schematic == null) return Integer.MAX_VALUE;
        int missing = 0;
        for (Map.Entry<Item, Integer> e : schematic.requirements().entrySet())
            missing += Math.max(0, e.getValue() - stored.getOrDefault(e.getKey(), 0));
        return missing;
    }

    private void pickup(ServerPlayer player) {
        ItemStack stack = new ItemStack(ModContent.MINION_ITEMS.get(getTier()).get());
        stack.setDamageValue(Math.min(getUsed(), getTier().maxBlocks - 1));
        dropStoredTo(player);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        playSound(SoundEvents.ITEM_PICKUP, 1f, 0.8f);
        discard();
    }

    private void dropStoredTo(ServerPlayer player) {
        for (Map.Entry<Item, Integer> e : stored.entrySet()) {
            int left = e.getValue();
            while (left > 0) {
                int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
                ItemStack st = new ItemStack(e.getKey(), n);
                if (!player.getInventory().add(st)) player.drop(st, false);
                left -= n;
            }
        }
        stored.clear();
    }

    private void dropStored(ServerLevel level) {
        for (Map.Entry<Item, Integer> e : stored.entrySet()) {
            int left = e.getValue();
            while (left > 0) {
                int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
                level.addFreshEntity(new ItemEntity(level, getX(), getY() + 0.5, getZ(), new ItemStack(e.getKey(), n)));
                left -= n;
            }
        }
        stored.clear();
    }

    private void notifyOwner(ServerLevel level, Component msg) {
        if (owner == null) return;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
        if (p != null) p.sendSystemMessage(msg);
    }

    public void sendState(ServerPlayer player, boolean open) {
        List<ModNetwork.MaterialLine> lines = new ArrayList<>();
        int sx = 0, sy = 0, sz = 0, placeable = 0;
        String name = "";
        if (schematic != null) {
            for (Map.Entry<Item, Integer> e : schematic.requirements().entrySet())
                lines.add(new ModNetwork.MaterialLine(e.getKey(), e.getValue(), Math.min(e.getValue(), stored.getOrDefault(e.getKey(), 0))));
            sx = schematic.sizeX; sy = schematic.sizeY; sz = schematic.sizeZ;
            placeable = schematic.placeableCount();
            name = schematic.name;
        }
        int total = queue != null ? queue.size() : placeable;
        Services.toPlayer.accept(player, new ModNetwork.MinionState(getId(), getTier().ordinal(), getPhase().ordinal(), getUsed(),
                queueIndex, total, name, sx, sy, sz, placeable, player.hasInfiniteMaterials(), open, lines));
    }

    // ================================================================= saving

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("Tier", getTier().ordinal());
        out.putInt("Phase", getPhase().ordinal());
        out.putInt("Used", getUsed());
        out.putBoolean("CreativeJob", creativeJob);
        if (owner != null) out.store("Owner", UUIDUtil.CODEC, owner);
        if (schematic != null) out.store("Blueprint", CompoundTag.CODEC, schematic.save());
        out.store("Stored", STORED_CODEC, new LinkedHashMap<>(stored));
        if (anchor != null) {
            out.store("Anchor", BlockPos.CODEC, anchor);
            out.putInt("Rotation", rotation.ordinal());
            out.putInt("Mirror", mirror.ordinal());
            out.putInt("QueueIndex", queueIndex);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        this.entityData.set(DATA_TIER, in.getIntOr("Tier", 0));
        this.entityData.set(DATA_USED, in.getIntOr("Used", 0));
        this.creativeJob = in.getBooleanOr("CreativeJob", false);
        this.owner = in.read("Owner", UUIDUtil.CODEC).orElse(null);
        this.schematic = in.read("Blueprint", CompoundTag.CODEC).map(tag -> {
            try { return Schematic.load(tag); } catch (Exception e) { return null; }
        }).orElse(null);
        stored.clear();
        in.read("Stored", STORED_CODEC).ifPresent(stored::putAll);
        this.anchor = in.read("Anchor", BlockPos.CODEC).orElse(null);
        this.rotation = Rotation.values()[Math.floorMod(in.getIntOr("Rotation", 0), 4)];
        this.mirror = Mirror.values()[Math.floorMod(in.getIntOr("Mirror", 0), 3)];
        this.queueIndex = in.getIntOr("QueueIndex", 0);
        MinionPhase phase = MinionPhase.byId(in.getIntOr("Phase", 0));
        if (phase == MinionPhase.AWAITING_PLACEMENT) phase = MinionPhase.COLLECTING; // hologram is client-side only
        if (phase == MinionPhase.BUILDING && (anchor == null || schematic == null)) phase = schematic == null ? MinionPhase.IDLE : MinionPhase.COLLECTING;
        setPhase(phase);
    }
}
