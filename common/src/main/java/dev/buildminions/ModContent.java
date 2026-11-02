package dev.buildminions;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import dev.buildminions.minion.MinionEntity;
import dev.buildminions.minion.MinionItem;
import dev.buildminions.minion.MinionTier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Factories + holders for everything the mod registers. The loaders call the factories inside their
 * own registration system and then store the result in the suppliers below.
 */
public final class ModContent {
    public static final ResourceKey<EntityType<?>> MINION_KEY = ResourceKey.create(Registries.ENTITY_TYPE, BuildMinions.id("minion"));

    public static Supplier<EntityType<MinionEntity>> MINION = () -> { throw new IllegalStateException("not registered"); };
    public static final Map<MinionTier, Supplier<Item>> MINION_ITEMS = new EnumMap<>(MinionTier.class);
    public static Supplier<Item> MINION_CORE = () -> { throw new IllegalStateException("not registered"); };
    public static Supplier<net.minecraft.world.level.block.Block> WORKSTATION = () -> { throw new IllegalStateException("not registered"); };
    public static Supplier<Item> WORKSTATION_ITEM = () -> { throw new IllegalStateException("not registered"); };
    public static final ResourceKey<net.minecraft.world.level.block.Block> WORKSTATION_KEY =
            ResourceKey.create(Registries.BLOCK, BuildMinions.id("minion_workstation"));

    public static net.minecraft.world.level.block.Block createWorkstation() {
        return new dev.buildminions.workshop.WorkstationBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                .setId(WORKSTATION_KEY).strength(2.5f).noOcclusion()
                .sound(net.minecraft.world.level.block.SoundType.WOOD)
                .lightLevel(s -> s.getValue(dev.buildminions.workshop.WorkstationBlock.LIT) ? 11 : 0));
    }

    public static Item createWorkstationItem(net.minecraft.world.level.block.Block block) {
        return new net.minecraft.world.item.BlockItem(block, new Item.Properties().setId(itemKey("minion_workstation")).useBlockDescriptionPrefix());
    }

    private ModContent() {}

    public static ResourceKey<Item> itemKey(String name) {
        return ResourceKey.create(Registries.ITEM, BuildMinions.id(name));
    }

    public static EntityType<MinionEntity> createMinionType() {
        return EntityType.Builder.<MinionEntity>of(MinionEntity::new, MobCategory.MISC)
                .sized(0.6f, 1.1f)
                .eyeHeight(0.85f)
                .clientTrackingRange(10)
                .fireImmune()
                .build(MINION_KEY);
    }

    public static Item createMinionItem(MinionTier tier) {
        return new MinionItem(tier, new Item.Properties()
                .setId(itemKey(tier.itemName()))
                .durability(tier.maxBlocks)
                .rarity(tier == MinionTier.DIAMOND ? Rarity.EPIC : tier == MinionTier.GOLD ? Rarity.RARE : Rarity.UNCOMMON));
    }

    public static Item createCore() {
        return new Item(new Item.Properties().setId(itemKey("minion_core")).rarity(Rarity.UNCOMMON));
    }

    /** Fills a creative tab builder (vanilla, NeoForge and Fabric builders all extend CreativeModeTab.Builder). */
    public static CreativeModeTab.Builder fillTab(CreativeModeTab.Builder builder) {
        return builder
                .title(Component.translatable("itemGroup.buildminions"))
                .icon(() -> new ItemStack(MINION_ITEMS.get(MinionTier.DIAMOND).get()))
                .displayItems((params, output) -> {
                    output.accept(MINION_CORE.get());
                    output.accept(WORKSTATION_ITEM.get());
                    for (MinionTier t : MinionTier.values()) output.accept(MINION_ITEMS.get(t).get());
                });
    }
}
