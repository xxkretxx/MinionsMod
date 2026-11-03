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
import dev.buildminions.workshop.CraftPlanner;
import dev.buildminions.workshop.WorkstationBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.phys.Vec3;

/**
 * The Build Minion. All building logic runs on the logical server (integrated server in singleplayer).
 */
public class MinionEntity extends Mob {
    private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_USED = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE_TICKS = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_UPGRADE = SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.INT);

    public static final byte EVENT_SWING = 60;
    public static final int BREAK_DURATION = 40;
    private static final Codec<Map<Item, Integer>> STORED_CODEC = Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.INT);

    // ---- server state
    private Schematic schematic;
    private final Map<Item, Integer> stored = new LinkedHashMap<>();
    /** Materials the owner chose to leave out of the build. */
    private final java.util.Set<Item> skipped = new java.util.LinkedHashSet<>();
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

    // ---- workshop
    private static final class QueuedJob {
        final CraftPlanner.Option option; int left; final int total;
        QueuedJob(CraftPlanner.Option option, int left, int total) { this.option = option; this.left = left; this.total = total; }
    }
    private final List<QueuedJob> craftQueue = new ArrayList<>();
    private ListTag pendingQueueTag;
    private int craftTimer, craftDuration;
    private BlockPos workstationPos;

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
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_TIER, 0);
        this.entityData.define(DATA_PHASE, 0);
        this.entityData.define(DATA_USED, 0);
        this.entityData.define(DATA_PHASE_TICKS, 0);
        this.entityData.define(DATA_UPGRADE, 0);
    }

    @Override
    protected float getStandingEyeHeight(net.minecraft.world.entity.Pose pose, net.minecraft.world.entity.EntityDimensions dims) {
        return 0.85f;
    }

    // ================================================================= accessors

    public void setup(MinionTier tier, int used, int upgrade, UUID owner) {
        this.entityData.set(DATA_TIER, tier.ordinal());
        this.entityData.set(DATA_UPGRADE, Math.max(0, Math.min(2, upgrade)));
        this.entityData.set(DATA_USED, used);
        this.owner = owner;
        this.setCustomName(null);
    }

    public MinionTier getTier() { return MinionTier.byId(this.entityData.get(DATA_TIER)); }
    public MinionPhase getPhase() { return MinionPhase.byId(this.entityData.get(DATA_PHASE)); }
    public int getUsed() { return this.entityData.get(DATA_USED); }
    /** 0 = none, 1 = Golden Apple (x2 durability), 2 = Enchanted Golden Apple (x4). */
    public int getUpgrade() { return this.entityData.get(DATA_UPGRADE); }
    public int getMaxBlocks() { return getTier().maxBlocks(getUpgrade()); }
    public int getRemainingBlocks() { return Math.max(0, getMaxBlocks() - getUsed()); }
    /** Ticks since the phase last changed (synced, used for animations). */
    public int getPhaseTicks() { return this.entityData.get(DATA_PHASE_TICKS); }

    private void setPhase(MinionPhase phase) {
        this.entityData.set(DATA_PHASE, phase.ordinal());
        this.entityData.set(DATA_PHASE_TICKS, 0);
        boolean flying = phase == MinionPhase.BUILDING || phase == MinionPhase.CRAFTING;
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
            if ((getPhase() == MinionPhase.BUILDING || getPhase() == MinionPhase.CRAFTING) && random.nextInt(4) == 0) {
                MinionTier t = getTier();
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + (random.nextDouble() - .5) * .4, getY() + .1, getZ() + (random.nextDouble() - .5) * .4, 0, -0.05, 0);
                if (getUpgrade() > 0 && random.nextInt(3) == 0)
                    level().addParticle(getUpgrade() == 2 ? ParticleTypes.ENCHANT : ParticleTypes.WAX_ON, getX(), getY() + 1.0, getZ(), (random.nextDouble() - .5) * .4, 0.1, (random.nextDouble() - .5) * .4);
                if (t == MinionTier.DIAMOND && random.nextInt(3) == 0)
                    level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 1.1, getZ(), 0, 0.02, 0);
            }
            return;
        }
        this.entityData.set(DATA_PHASE_TICKS, Math.min(100_000, getPhaseTicks() + 1));
        switch (getPhase()) {
            case BUILDING -> buildTick((ServerLevel) level());
            case BREAKING -> breakTick((ServerLevel) level());
            case CRAFTING -> craftTick((ServerLevel) level());
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
        while (queueIndex < queue.size() && guard++ < 64 && (isSkipped(queue.get(queueIndex).state()) || level.getBlockState(queue.get(queueIndex).pos()) == queue.get(queueIndex).state())) {
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
        if (isSkipped(p.state())) return; // left out by the owner
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
        if (used >= getMaxBlocks()) {
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
        level.playSound(null, blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL, 1.2f, 0.8f);
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 0.4f, 1.8f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, getTier().particleBlock().defaultBlockState()), getX(), getY() + 0.6, getZ(), 60, .3, .4, .3, .15);
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.6, getZ(), 15, .3, .3, .3, .05);
        dropStored(level);
        notifyOwner(level, Component.translatable("message.buildminions.broke", Component.translatable(getTier().translationKey()), getMaxBlocks()));
        discard();
    }

    // ================================================================= workshop

    /** Finds a Minion Workstation within range (cached while it still exists). */
    public BlockPos findWorkstation() {
        Level level = level();
        if (workstationPos != null && level.getBlockState(workstationPos).getBlock() instanceof WorkstationBlock
                && workstationPos.closerToCenterThan(position(), WorkstationBlock.RANGE + 2)) return workstationPos;
        workstationPos = null;
        BlockPos c = blockPosition();
        int r = WorkstationBlock.RANGE;
        double best = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -4, -r), c.offset(r, 4, r))) {
            if (level.getBlockState(p).getBlock() instanceof WorkstationBlock) {
                double d = p.distSqr(c);
                if (d < best) { best = d; workstationPos = p.immutable(); }
            }
        }
        return workstationPos;
    }

    private Map<Item, Integer> missingMaterials() {
        Map<Item, Integer> missing = new LinkedHashMap<>();
        if (schematic == null) return missing;
        for (Map.Entry<Item, Integer> e : schematic.requirements().entrySet()) {
            if (skipped.contains(e.getKey())) continue;
            int m = e.getValue() - stored.getOrDefault(e.getKey(), 0);
            if (m > 0) missing.put(e.getKey(), m);
        }
        // items already being crafted are not missing any more
        for (QueuedJob j : craftQueue) {
            Item out = j.option.result();
            if (missing.containsKey(out)) missing.put(out, Math.max(0, missing.get(out) - j.left * j.option.resultCount()));
        }
        return missing;
    }

    /** Raw items in storage that are not needed directly by the blueprint (they can be crafted into something). */
    private Map<Item, Integer> spareStock() {
        Map<Item, Integer> spare = new LinkedHashMap<>();
        Map<Item, Integer> req = schematic != null ? schematic.requirements() : Map.of();
        for (Map.Entry<Item, Integer> e : stored.entrySet()) {
            int s = e.getValue() - (skipped.contains(e.getKey()) ? 0 : req.getOrDefault(e.getKey(), 0));
            if (s > 0) spare.put(e.getKey(), s);
        }
        return spare;
    }

    private void queueCraftAll(ServerPlayer player) {
        if (findWorkstation() == null || schematic == null) return;
        CraftPlanner.Plan plan = CraftPlanner.plan(level().getServer(), missingMaterials(), spareStock());
        for (CraftPlanner.Job j : plan.jobs()) craftQueue.add(new QueuedJob(j.option(), j.times(), j.times()));
        if (!craftQueue.isEmpty()) {
            setPhase(MinionPhase.CRAFTING);
            craftTimer = 0;
            playSound(SoundEvents.VILLAGER_WORK_TOOLSMITH, 0.8f, 1.3f);
        } else {
            player.sendSystemMessage(Component.translatable("message.buildminions.nothing_to_craft"));
        }
    }

    private int runDuration(CraftPlanner.Option o) {
        int t = getTier().ticksPerBlock;
        return o.kind() == CraftPlanner.Kind.SMELT ? 18 + t * 2 : 8 + t;
    }

    private void craftTick(ServerLevel level) {
        resolvePendingQueue(level);
        BlockPos ws = findWorkstation();
        if (ws == null || craftQueue.isEmpty()) { stopCrafting(level); return; }
        // hover next to the table, facing it
        Vec3 table = Vec3.atBottomCenterOf(ws).add(0, 0.75, 0);
        Vec3 side = new Vec3(getX() - table.x, 0, getZ() - table.z);
        side = side.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : side.normalize();
        Vec3 hover = table.add(side.scale(0.9)).add(0, 0.1 + Math.sin(tickCount * 0.2) * 0.04, 0);
        Vec3 delta = hover.subtract(position());
        double dist = delta.length(), speed = getTier().flySpeed;
        setPos(position().add(dist > speed ? delta.scale(speed / dist) : delta));
        setDeltaMovement(Vec3.ZERO);
        float yaw = (float) (Mth.atan2(table.z - getZ(), table.x - getX()) * Mth.RAD_TO_DEG) - 90f;
        setYRot(yaw); setYHeadRot(yaw); setYBodyRot(yaw); setXRot(25f);
        if (dist > 1.5) return;

        QueuedJob job = craftQueue.get(0);
        boolean smelting = job.option.kind() == CraftPlanner.Kind.SMELT;
        setLit(level, ws, smelting);
        if (craftTimer == 0) craftDuration = runDuration(job.option);
        craftTimer++;
        if (craftTimer % 6 == 0) {
            level.broadcastEntityEvent(this, EVENT_SWING);
            if (smelting) {
                level.sendParticles(ParticleTypes.FLAME, ws.getX() + .75, ws.getY() + .8, ws.getZ() + .5, 2, .08, .05, .08, 0.01);
                level.sendParticles(ParticleTypes.SMOKE, ws.getX() + .75, ws.getY() + 1.0, ws.getZ() + .5, 2, .05, .05, .05, 0.01);
            } else {
                level.sendParticles(ParticleTypes.CRIT, ws.getX() + .5, ws.getY() + .8, ws.getZ() + .5, 3, .15, .05, .15, 0.05);
                level.playSound(null, ws, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.15f, 1.6f + random.nextFloat() * 0.3f);
            }
        }
        if (craftTimer < craftDuration) return;
        craftTimer = 0;
        if (CraftPlanner.consumeOnce(job.option, stored)) {
            stored.merge(job.option.result(), job.option.resultCount(), Integer::sum);
            level.playSound(null, ws, smelting ? SoundEvents.FURNACE_FIRE_CRACKLE : SoundEvents.VILLAGER_WORK_TOOLSMITH, SoundSource.BLOCKS, 0.6f, 1.2f);
            job.left--;
        } else {
            job.left = 0; // ingredients gone (taken back by the player?) – skip this job
        }
        if (job.left <= 0) craftQueue.remove(0);
        if (craftQueue.isEmpty()) stopCrafting(level);
        notifyOwnerState(level);
    }

    private void stopCrafting(ServerLevel level) {
        if (workstationPos != null) setLit(level, workstationPos, false);
        craftTimer = 0;
        if (getPhase() == MinionPhase.CRAFTING) {
            setPhase(schematic != null ? MinionPhase.COLLECTING : MinionPhase.IDLE);
            playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 1.6f);
        }
    }

    private void setLit(ServerLevel level, BlockPos pos, boolean lit) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof WorkstationBlock && s.getValue(WorkstationBlock.LIT) != lit)
            level.setBlock(pos, s.setValue(WorkstationBlock.LIT, lit), Block.UPDATE_ALL);
    }

    private void notifyOwnerState(ServerLevel level) {
        if (owner == null) return;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
        if (p != null && p.distanceToSqr(this) < 64 * 64) sendState(p, false);
    }

    private void resolvePendingQueue(ServerLevel level) {
        if (pendingQueueTag == null) return;
        for (int i = 0; i < pendingQueueTag.size(); i++) {
            CompoundTag t = pendingQueueTag.getCompound(i);
            try {
                CraftPlanner.Option o = CraftPlanner.byId(level.getServer(), new ResourceLocation(t.getString("Recipe")));
                if (o != null) craftQueue.add(new QueuedJob(o, t.getInt("Left"), Math.max(1, t.getInt("Total"))));
            } catch (Exception ignored) {}
        }
        pendingQueueTag = null;
    }

    private static String itemName(Item item) {
        return new ItemStack(item).getHoverName().getString();
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
        ItemStack held = player.getItemInHand(hand);
        int level = held.is(Items.ENCHANTED_GOLDEN_APPLE) ? 2 : held.is(Items.GOLDEN_APPLE) ? 1 : 0;
        if (level > 0) {
            if (getUpgrade() >= level) {
                sp.sendSystemMessage(Component.translatable("message.buildminions.already_upgraded"));
                return InteractionResult.FAIL;
            }
            this.entityData.set(DATA_UPGRADE, level);
            if (!player.getAbilities().instabuild) held.shrink(1);
            ServerLevel sl = (ServerLevel) level();
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 0.8, getZ(), 40, .3, .5, .3, .4);
            sl.playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1f, level == 2 ? 0.8f : 1.2f);
            sp.sendSystemMessage(Component.translatable("message.buildminions.upgraded", Component.translatable(getTier().translationKey()), getMaxBlocks()));
            return InteractionResult.SUCCESS;
        }
        sendState(sp, true);
        return InteractionResult.SUCCESS;
    }

    public void handleAction(ServerPlayer player, int action) {
        MinionPhase phase = getPhase();
        if (action >= ModNetwork.Action.TOGGLE_SKIP) {
            Item item = BuiltInRegistries.ITEM.byId(action - ModNetwork.Action.TOGGLE_SKIP);
            if (schematic != null && phase != MinionPhase.BUILDING && phase != MinionPhase.BREAKING && item != null) {
                if (!skipped.remove(item)) skipped.add(item);
                playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, skipped.contains(item) ? 0.8f : 1.3f);
            }
            sendState(player, false);
            return;
        }
        switch (action) {
            case ModNetwork.Action.REFRESH -> {}
            case ModNetwork.Action.DEPOSIT -> { if (phase == MinionPhase.COLLECTING || phase == MinionPhase.CRAFTING) deposit(player); }
            case ModNetwork.Action.ACCEPT -> {
                if (phase == MinionPhase.COLLECTING && schematic != null && (player.getAbilities().instabuild || missingCount() == 0)) {
                    creativeJob = player.getAbilities().instabuild;
                    setPhase(MinionPhase.AWAITING_PLACEMENT);
                    owner = player.getUUID();
                    Services.toPlayer.accept(player, new ModNetwork.StartPlacement(getId()));
                    playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1f, 1.5f);
                    return;
                }
            }
            case ModNetwork.Action.CRAFT_ALL -> {
                if (phase == MinionPhase.COLLECTING || phase == MinionPhase.CRAFTING) queueCraftAll(player);
            }
            case ModNetwork.Action.CLEAR_QUEUE -> {
                craftQueue.clear();
                if (phase == MinionPhase.CRAFTING) stopCrafting((ServerLevel) level());
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
                    skipped.clear();
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
        int blocks = effectivePlaceable();
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
            CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bos.toByteArray()));
            Schematic s = Schematic.load(tag);
            if (s.placeableCount() > Schematic.MAX_BLOCKS) throw new IllegalArgumentException("too many blocks");
            this.schematic = s;
            this.skipped.clear();
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
            if (skipped.contains(e.getKey())) continue;
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
        // with a workstation nearby, also take the raw materials that can be crafted into missing blocks
        if (findWorkstation() != null) {
            Map<Item, Integer> invCounts = new LinkedHashMap<>();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (!st.isEmpty() && st.hasTag()) continue; // never eat named / enchanted items
                if (!st.isEmpty()) invCounts.merge(st.getItem(), st.getCount(), Integer::sum);
            }
            Map<Item, Integer> spare = spareStock();
            Map<Item, Integer> avail = new LinkedHashMap<>(spare);
            invCounts.forEach((k, v) -> avail.merge(k, v, Integer::sum));
            CraftPlanner.Plan plan = CraftPlanner.plan(level().getServer(), missingMaterials(), avail);
            for (Map.Entry<Item, Integer> e : plan.consumed().entrySet()) {
                int fromInv = e.getValue() - Math.min(e.getValue(), spare.getOrDefault(e.getKey(), 0));
                for (int i = 0; i < inv.getContainerSize() && fromInv > 0; i++) {
                    ItemStack st = inv.getItem(i);
                    if (st.isEmpty() || !st.is(e.getKey()) || st.hasTag()) continue;
                    int take = Math.min(fromInv, st.getCount());
                    st.shrink(take);
                    fromInv -= take;
                    stored.merge(e.getKey(), take, Integer::sum);
                    moved = true;
                }
            }
        }
        if (moved) {
            inv.setChanged();
            playSound(SoundEvents.ITEM_PICKUP, 0.6f, 1.2f);
        }
    }

    /** Blocks that will really be placed (skipped materials left out). */
    private int effectivePlaceable() {
        if (schematic == null) return 0;
        if (skipped.isEmpty()) return schematic.placeableCount();
        int[] perPalette = new int[schematic.palette.size()];
        for (int b : schematic.blocks) perPalette[b]++;
        int n = 0;
        for (int i = 0; i < perPalette.length; i++) {
            var st = schematic.palette.get(i);
            if (perPalette[i] > 0 && Schematic.isPlaceable(st) && !isSkipped(st)) n += perPalette[i];
        }
        return n;
    }

    private boolean isSkipped(BlockState state) {
        if (skipped.isEmpty()) return false;
        Schematic.Cost c = Schematic.costOf(state);
        return c != null && skipped.contains(c.item());
    }

    private int missingCount() {
        if (schematic == null) return Integer.MAX_VALUE;
        int missing = 0;
        for (Map.Entry<Item, Integer> e : schematic.requirements().entrySet())
            if (!skipped.contains(e.getKey())) missing += Math.max(0, e.getValue() - stored.getOrDefault(e.getKey(), 0));
        return missing;
    }

    private void pickup(ServerPlayer player) {
        ItemStack stack = new ItemStack(ModContent.MINION_ITEMS.get(getTier()).get());
        MinionItem.writeUsage(stack, getTier(), getUsed(), getUpgrade());
        dropStoredTo(player);
        if (!player.getInventory().add(stack)) player.drop(stack, false, false);
        playSound(SoundEvents.ITEM_PICKUP, 1f, 0.8f);
        discard();
    }

    private void dropStoredTo(ServerPlayer player) {
        for (Map.Entry<Item, Integer> e : stored.entrySet()) {
            int left = e.getValue();
            while (left > 0) {
                int n = Math.min(left, e.getKey().getMaxStackSize());
                ItemStack st = new ItemStack(e.getKey(), n);
                if (!player.getInventory().add(st)) player.drop(st, false, false);
                left -= n;
            }
        }
        stored.clear();
    }

    private void dropStored(ServerLevel level) {
        for (Map.Entry<Item, Integer> e : stored.entrySet()) {
            int left = e.getValue();
            while (left > 0) {
                int n = Math.min(left, e.getKey().getMaxStackSize());
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
                lines.add(new ModNetwork.MaterialLine(e.getKey(), e.getValue(), Math.min(e.getValue(), stored.getOrDefault(e.getKey(), 0)), skipped.contains(e.getKey())));
            sx = schematic.sizeX; sy = schematic.sizeY; sz = schematic.sizeZ;
            placeable = effectivePlaceable();
            name = schematic.name;
        }
        int total = queue != null ? queue.size() : placeable;
        boolean hasWs = findWorkstation() != null;
        List<ModNetwork.CraftLine> craftable = new ArrayList<>(), queueLines = new ArrayList<>(), raw = new ArrayList<>();
        if (schematic != null && hasWs && level() instanceof ServerLevel sl) {
            resolvePendingQueue(sl);
            CraftPlanner.Plan plan = CraftPlanner.plan(sl.getServer(), missingMaterials(), spareStock());
            for (Map.Entry<Item, Integer> e : plan.produced().entrySet()) {
                StringBuilder via = new StringBuilder();
                for (CraftPlanner.Job j : plan.jobs()) if (j.option().result() == e.getKey()) {
                    via.append(j.option().kind().name().toLowerCase());
                }
                craftable.add(new ModNetwork.CraftLine(e.getKey(), e.getValue(), e.getValue(), via.toString()));
            }
        }
        for (QueuedJob j : craftQueue)
            queueLines.add(new ModNetwork.CraftLine(j.option.result(), j.left * j.option.resultCount(), j.total * j.option.resultCount(), j.option.kind().name().toLowerCase()));
        for (Map.Entry<Item, Integer> e : spareStock().entrySet()) raw.add(new ModNetwork.CraftLine(e.getKey(), e.getValue(), e.getValue(), ""));
        float craftProgress = craftDuration > 0 ? craftTimer / (float) craftDuration : 0f;
        Services.toPlayer.accept(player, new ModNetwork.MinionState(getId(), getTier().ordinal(), getPhase().ordinal(), getUsed(), getUpgrade(), getMaxBlocks(),
                queueIndex, total, name, sx, sy, sz, placeable, player.getAbilities().instabuild, open, lines,
                hasWs, craftProgress, craftable, queueLines, raw));
    }

    // ================================================================= saving

    @Override
    public void addAdditionalSaveData(CompoundTag out) {
        super.addAdditionalSaveData(out);
        out.putInt("Tier", getTier().ordinal());
        out.putInt("Phase", getPhase().ordinal());
        out.putInt("Used", getUsed());
        out.putInt("Upgrade", getUpgrade());
        out.putBoolean("CreativeJob", creativeJob);
        if (owner != null) out.putUUID("Owner", owner);
        if (schematic != null) out.put("Blueprint", schematic.save());
        STORED_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, new LinkedHashMap<>(stored)).result().ifPresent(t -> out.put("Stored", t));
        ListTag q = new ListTag();
        for (QueuedJob j : craftQueue) {
            CompoundTag t = new CompoundTag();
            t.putString("Recipe", j.option.id().toString());
            t.putInt("Left", j.left);
            t.putInt("Total", j.total);
            q.add(t);
        }
        if (pendingQueueTag != null) q.addAll(pendingQueueTag);
        out.put("CraftQueue", q);
        ListTag sk = new ListTag();
        for (Item it : skipped) sk.add(net.minecraft.nbt.StringTag.valueOf(BuiltInRegistries.ITEM.getKey(it).toString()));
        out.put("Skipped", sk);
        if (anchor != null) {
            out.putIntArray("Anchor", new int[]{anchor.getX(), anchor.getY(), anchor.getZ()});
            out.putInt("Rotation", rotation.ordinal());
            out.putInt("Mirror", mirror.ordinal());
            out.putInt("QueueIndex", queueIndex);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag in) {
        super.readAdditionalSaveData(in);
        this.entityData.set(DATA_TIER, in.getInt("Tier"));
        this.entityData.set(DATA_USED, in.getInt("Used"));
        this.entityData.set(DATA_UPGRADE, in.getInt("Upgrade"));
        this.creativeJob = in.getBoolean("CreativeJob");
        this.owner = in.hasUUID("Owner") ? in.getUUID("Owner") : null;
        this.schematic = null;
        if (in.contains("Blueprint")) {
            try { this.schematic = Schematic.load(in.getCompound("Blueprint")); } catch (Exception ignored) {}
        }
        stored.clear();
        if (in.contains("Stored")) STORED_CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, in.get("Stored")).result().ifPresent(stored::putAll);
        skipped.clear();
        ListTag sk = in.getList("Skipped", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < sk.size(); i++) skipped.add(BuiltInRegistries.ITEM.get(new ResourceLocation(sk.getString(i))));
        craftQueue.clear();
        ListTag q = in.getList("CraftQueue", net.minecraft.nbt.Tag.TAG_COMPOUND);
        pendingQueueTag = q.isEmpty() ? null : q;
        int[] an = in.getIntArray("Anchor");
        this.anchor = an.length == 3 ? new BlockPos(an[0], an[1], an[2]) : null;
        this.rotation = Rotation.values()[Math.floorMod(in.getInt("Rotation"), 4)];
        this.mirror = Mirror.values()[Math.floorMod(in.getInt("Mirror"), 3)];
        this.queueIndex = in.getInt("QueueIndex");
        MinionPhase phase = MinionPhase.byId(in.getInt("Phase"));
        if (phase == MinionPhase.AWAITING_PLACEMENT) phase = MinionPhase.COLLECTING;
        if (phase == MinionPhase.BUILDING && (anchor == null || schematic == null)) phase = schematic == null ? MinionPhase.IDLE : MinionPhase.COLLECTING;
        setPhase(phase);
    }
}
