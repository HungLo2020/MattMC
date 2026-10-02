package net.alexsmobs.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mojang.serialization.Lifecycle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Isolated("Loads the bundled item tags required by the real kangaroo factory")
class EntityKangarooInventoryTest {
    private static String previousByteBuddy;
    private static RegistryAccess registries;
    private static Holder<Enchantment> testEnchantment;
    private static Map<TagKey<Item>, List<Holder<Item>>> originalItemTags;
    private ServerLevel level;

    @BeforeAll
    static void bootstrap() {
        previousByteBuddy = System.getProperty("net.bytebuddy.experimental");
        System.setProperty("net.bytebuddy.experimental", "true");
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        originalItemTags = BuiltInRegistries.ITEM.listTags().collect(Collectors.toMap(HolderSet.Named::key, tag -> tag.stream().toList()));
        try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(ServerPacksSource.createVanillaPackSource()))) {
            TagLoader.loadTagsForExistingRegistries(resources, new RegistryAccess.ImmutableRegistryAccess(List.of(BuiltInRegistries.ITEM)))
                .forEach(Registry.PendingTags::apply);
        }
        // A real registry-backed component, without loading world-generation registries.
        MappedRegistry<Enchantment> enchantments = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());
        ResourceLocation id = ResourceLocation.withDefaultNamespace("kangaroo_pouch_test");
        testEnchantment = Registry.registerForHolder(enchantments, id,
            Enchantment.enchantment(Enchantment.definition(HolderSet.direct(Items.DIAMOND_SWORD.builtInRegistryHolder()),
                    1, 3, Enchantment.constantCost(1), Enchantment.constantCost(10), 1, EquipmentSlotGroup.ANY))
                .withEffect(EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP).build(id));
        enchantments.freeze();
        registries = new RegistryAccess.ImmutableRegistryAccess(Stream.concat(
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).registries(),
            Stream.of(new RegistryAccess.RegistryEntry<>(Registries.ENCHANTMENT, enchantments))));
    }

    @AfterAll
    static void restoreProperties() {
        BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(BuiltInRegistries.ITEM.key(), originalItemTags)).apply();
        if (previousByteBuddy == null) {
            System.clearProperty("net.bytebuddy.experimental");
        } else {
            System.setProperty("net.bytebuddy.experimental", previousByteBuddy);
        }
    }

    @BeforeEach
    void createLevel() {
        level = mock(ServerLevel.class, RETURNS_DEEP_STUBS);
        when(level.enabledFeatures()).thenReturn(FeatureFlags.DEFAULT_FLAGS);
        when(level.registryAccess()).thenReturn(registries);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)).thenReturn(true);
    }

    @Test
    void newEntityRoundTripPreservesAllNineSlotsAndFullComponents() {
        EntityKangaroo source = kangaroo();
        List<ItemStack> expected = fillPouch(source);
        source.setTame(true, false);
        source.setOwnerReference(EntityReference.of(UUID.randomUUID()));
        source.setCommand(2);
        source.setOrderedToSit(true);

        EntityKangaroo restored = load(save(source));

        assertNotSame(source, restored);
        assertNotSame(source.kangarooInventory, restored.kangarooInventory);
        assertPouch(expected, restored);
        assertTrue(restored.isTame());
        assertEquals(2, restored.getCommand());
        assertTrue(restored.isSitting());
        assertEquals(3, restored.kangarooInventory.getItem(0).getEnchantments().getLevel(testEnchantment));
        assertFalse(restored.kangarooInventory.getItem(7).has(DataComponents.FOOD));
        assertSelected(restored, 0, 1, 2);
        assertPouch(expected, load(save(restored)));
    }

    @Test
    void sparseSlotsRemainSeparateRatherThanMergingOrCompacting() {
        EntityKangaroo source = kangaroo();
        source.kangarooInventory.setItem(3, new ItemStack(Items.STONE, 11));
        source.kangarooInventory.setItem(8, new ItemStack(Items.STONE, 7));
        CompoundTag saved = save(source);
        assertEquals(2, saved.getListOrEmpty("Items").size());

        assertPouch(snapshot(source), load(saved));
        assertSelected(load(saved), -1, -1, -1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emptyAndMissingLegacyDataClearAnExistingPouch(boolean legacy) {
        CompoundTag empty = save(kangaroo());
        if (legacy) {
            empty.remove("Items");
        }
        empty.putInt("SwordInvIndex", 999);
        empty.putInt("HelmetInvIndex", 0);
        empty.putInt("ChestInvIndex", -8);
        EntityKangaroo existing = kangaroo();
        fillPouch(existing);
        SimpleContainer original = existing.kangarooInventory;

        readInto(existing, empty);
        readInto(existing, empty);

        assertSame(original, existing.kangarooInventory);
        assertTrue(existing.kangarooInventory.isEmpty());
        assertSelected(existing, -1, -1, -1);
        assertTrue(load(empty).kangarooInventory.isEmpty());
    }

    @Test
    void selectionRunsOnceAfterCompleteRestoreAndIgnoresSavedIndexes() {
        EntityKangaroo source = kangaroo();
        List<ItemStack> expected = fillPouch(source);
        CompoundTag saved = save(source);
        saved.putInt("SwordInvIndex", 8);
        saved.putInt("HelmetInvIndex", 999);
        saved.putInt("ChestInvIndex", -3);
        SelectionKangaroo restored = new SelectionKangaroo(level, expected);

        readInto(restored, saved);

        assertEquals(1, restored.selections);
        assertSelected(restored, 0, 1, 2);
    }

    @Test
    void selectionKeepsBestGearFirstTiesAndBabyArmorRules() {
        EntityKangaroo source = kangaroo();
        source.kangarooInventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
        source.kangarooInventory.setItem(1, new ItemStack(Items.LEATHER_HELMET));
        source.kangarooInventory.setItem(2, new ItemStack(Items.LEATHER_CHESTPLATE));
        source.kangarooInventory.setItem(3, new ItemStack(Items.DIAMOND_SWORD));
        source.kangarooInventory.setItem(4, new ItemStack(Items.DIAMOND_HELMET));
        source.kangarooInventory.setItem(5, new ItemStack(Items.DIAMOND_CHESTPLATE));
        source.kangarooInventory.setItem(6, new ItemStack(Items.DIAMOND_SWORD));
        source.kangarooInventory.setItem(7, new ItemStack(Items.DIAMOND_HELMET));
        source.kangarooInventory.setItem(8, new ItemStack(Items.DIAMOND_CHESTPLATE));

        assertSelected(load(save(source)), 3, 4, 5);
        source.setBaby(true);
        EntityKangaroo baby = load(save(source));
        assertPouch(snapshot(source), baby);
        assertSelected(baby, 3, -1, -1);
    }

    @Test
    void nonArmorHeadEquipmentRetainsFirstEligibleFallback() {
        EntityKangaroo source = kangaroo();
        source.kangarooInventory.setItem(3, new ItemStack(Items.CARVED_PUMPKIN));
        source.kangarooInventory.setItem(8, new ItemStack(Items.CARVED_PUMPKIN));

        assertSelected(load(save(source)), -1, 3, -1);
    }

    @ParameterizedTest
    @MethodSource("malformedSlots")
    void malformedSlotsAreReportedAndCannotOverwriteValidSlots(Tag slot) {
        EntityKangaroo source = kangaroo();
        source.kangarooInventory.setItem(0, new ItemStack(Items.EMERALD, 5));
        source.kangarooInventory.setItem(8, new ItemStack(Items.STONE, 7));
        CompoundTag saved = save(source);
        CompoundTag bad = encodedStack(new ItemStack(Items.DIRT, 64));
        if (slot != null) {
            bad.put("Slot", slot);
        }
        saved.getListOrEmpty("Items").add(bad);
        ProblemReporter.Collector problems = new ProblemReporter.Collector();

        EntityKangaroo restored = load(saved, problems);

        assertPouch(snapshot(source), restored);
        assertFalse(problems.isEmpty(), "Invalid slots must retain the standard data-error reporting");
        assertTrue(problems.getReport().contains("Items"), problems::getReport);
    }

    static Stream<Tag> malformedSlots() {
        return Stream.of(null, StringTag.valueOf("bad"), IntTag.valueOf(-1), IntTag.valueOf(9),
            IntTag.valueOf(256), LongTag.valueOf(1L << 32), LongTag.valueOf(Long.MAX_VALUE),
            DoubleTag.valueOf(0.5), DoubleTag.valueOf(Double.NaN), DoubleTag.valueOf(Double.POSITIVE_INFINITY));
    }

    @Test
    void corruptEntriesAndInventoryTypeAreReportedWithoutCrashing() {
        EntityKangaroo source = kangaroo();
        source.kangarooInventory.setItem(4, new ItemStack(Items.EMERALD, 5));
        CompoundTag saved = save(source);
        CompoundTag badItem = new CompoundTag();
        badItem.putInt("Slot", 4);
        badItem.putString("id", "minecraft:no_such_item_for_kangaroo_test");
        saved.getListOrEmpty("Items").add(badItem);
        saved.getListOrEmpty("Items").add(StringTag.valueOf("not a stack"));
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        assertPouch(snapshot(source), load(saved, problems));
        assertFalse(problems.isEmpty());

        saved.putString("Items", "not a list");
        problems = new ProblemReporter.Collector();
        assertTrue(load(saved, problems).kangarooInventory.isEmpty());
        assertFalse(problems.isEmpty());
    }

    @Test
    void duplicateValidSlotRecordsUseLastRecordWithoutASecondStack() {
        EntityKangaroo source = kangaroo();
        source.kangarooInventory.setItem(4, new ItemStack(Items.EMERALD, 5));
        CompoundTag saved = save(source);
        CompoundTag replacement = encodedStack(new ItemStack(Items.STONE, 8));
        replacement.putByte("Slot", (byte)4);
        saved.getListOrEmpty("Items").add(replacement);

        EntityKangaroo restored = load(saved);

        assertEquals(8, restored.kangarooInventory.getItem(4).getCount());
        assertTrue(restored.kangarooInventory.getItem(4).is(Items.STONE));
        assertEquals(1, save(restored).getListOrEmpty("Items").size());
    }

    @Test
    void closingReopeningAndReinitializingKeepOneContainerAndItsItems() throws Exception {
        EntityKangaroo source = load(save(filledKangaroo()));
        List<ItemStack> expected = snapshot(source);
        SimpleContainer pouch = source.kangarooInventory;
        Player player = mock(Player.class);
        Inventory inventory = new Inventory(player, new EntityEquipment());
        Method initialize = EntityKangaroo.class.getDeclaredMethod("initKangarooInventory");
        initialize.setAccessible(true);
        for (int i = 0; i < 3; i++) {
            DispenserMenu menu = new DispenserMenu(i + 1, inventory, pouch);
            initialize.invoke(source);
            assertSame(pouch, source.kangarooInventory);
            for (int slot = 0; slot < 9; slot++) {
                assertSame(pouch.getItem(slot), menu.getSlot(slot).getItem());
            }
            menu.removed(player);
            assertPouch(expected, source);
        }
        assertPouch(expected, load(save(source)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deathDropsEveryPouchStackExactlyOnceWithOriginalComponents(boolean mobLoot) {
        List<ItemStack> expected = snapshot(filledKangaroo());
        DeathKangaroo restored = new DeathKangaroo(level);
        readInto(restored, save(filledKangaroo()));
        // Force Mob's old extra-drop path, including ordinary helmet/chest items.
        restored.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        restored.setGuaranteedDrop(EquipmentSlot.HEAD);
        restored.setGuaranteedDrop(EquipmentSlot.CHEST);
        when(level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)).thenReturn(mobLoot);
        DamageSource damage = mock(DamageSource.class);

        restored.dropAll(damage);
        restored.dropAll(damage);

        assertEquals(9, restored.drops.size());
        for (int slot = 0; slot < 9; slot++) {
            assertTrue(ItemStack.matches(expected.get(slot), restored.drops.get(slot)), "Dropped slot " + slot);
        }
        assertTrue(restored.kangarooInventory.isEmpty());
        assertSelected(restored, -1, -1, -1);
        assertTrue(load(save(restored)).kangarooInventory.isEmpty());
    }

    private EntityKangaroo kangaroo() {
        EntityKangaroo kangaroo = EntityType.KANGAROO.create(level, EntitySpawnReason.COMMAND);
        assertNotNull(kangaroo);
        return kangaroo;
    }

    private EntityKangaroo filledKangaroo() {
        EntityKangaroo kangaroo = kangaroo();
        fillPouch(kangaroo);
        return kangaroo;
    }

    private List<ItemStack> fillPouch(EntityKangaroo kangaroo) {
        List<ItemStack> items = new ArrayList<>(List.of(new ItemStack(Items.DIAMOND_SWORD),
            new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.CARROT, 17), new ItemStack(Items.STICK, 44), new ItemStack(Items.STONE, 55),
            new ItemStack(Items.STONE, 9), new ItemStack(Items.APPLE, 2), new ItemStack(Items.BUNDLE)));
        items.get(0).setDamageValue(17);
        items.get(0).enchant(testEnchantment, 3);
        items.get(1).setDamageValue(9);
        items.get(2).setDamageValue(31);
        items.get(4).set(DataComponents.CUSTOM_NAME, Component.literal("Pouch keeps every component"));
        CompoundTag custom = new CompoundTag();
        custom.putString("marker", "kangaroo regression");
        custom.putIntArray("numbers", new int[]{3, 1, 4});
        items.get(4).set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        items.get(7).remove(DataComponents.FOOD);
        items.get(8).set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(items.get(4).copyWithCount(2))));
        for (int slot = 0; slot < items.size(); slot++) {
            kangaroo.kangarooInventory.setItem(slot, items.get(slot));
        }
        return snapshot(kangaroo);
    }

    private CompoundTag save(EntityKangaroo kangaroo) {
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(problems, registries);
        assertTrue(kangaroo.save(output));
        assertTrue(problems.isEmpty(), problems::getReport);
        return output.buildResult();
    }

    private CompoundTag encodedStack(ItemStack item) {
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(problems, registries);
        output.store("item", ItemStack.CODEC, item);
        assertTrue(problems.isEmpty(), problems::getReport);
        return output.buildResult().getCompoundOrEmpty("item");
    }

    private EntityKangaroo load(CompoundTag tag) {
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        EntityKangaroo restored = load(tag, problems);
        assertTrue(problems.isEmpty(), problems::getReport);
        return restored;
    }

    private EntityKangaroo load(CompoundTag tag, ProblemReporter.Collector problems) {
        return assertInstanceOf(EntityKangaroo.class,
            EntityType.create(TagValueInput.create(problems, registries, tag), level, EntitySpawnReason.LOAD).orElseThrow());
    }

    private void readInto(EntityKangaroo kangaroo, CompoundTag tag) {
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        kangaroo.load(TagValueInput.create(problems, registries, tag));
        assertTrue(problems.isEmpty(), problems::getReport);
    }

    private static List<ItemStack> snapshot(EntityKangaroo kangaroo) {
        return kangaroo.kangarooInventory.getItems().stream().map(ItemStack::copy).toList();
    }

    private static void assertPouch(List<ItemStack> expected, EntityKangaroo kangaroo) {
        assertEquals(9, kangaroo.kangarooInventory.getContainerSize());
        for (int slot = 0; slot < 9; slot++) {
            assertTrue(ItemStack.matches(expected.get(slot), kangaroo.kangarooInventory.getItem(slot)), "Pouch slot " + slot);
        }
    }

    private static void assertSelected(EntityKangaroo kangaroo, int weapon, int helmet, int chest) {
        assertSelected(kangaroo, EquipmentSlot.MAINHAND, weapon);
        assertSelected(kangaroo, EquipmentSlot.HEAD, helmet);
        assertSelected(kangaroo, EquipmentSlot.CHEST, chest);
    }

    private static void assertSelected(EntityKangaroo kangaroo, EquipmentSlot equipment, int slot) {
        ItemStack actual = kangaroo.getItemBySlot(equipment);
        if (slot < 0) {
            assertTrue(actual.isEmpty());
        } else {
            assertSame(kangaroo.kangarooInventory.getItem(slot), actual);
        }
    }

    private static class SelectionKangaroo extends EntityKangaroo {
        private final List<ItemStack> expected;
        int selections;

        SelectionKangaroo(ServerLevel level, List<ItemStack> expected) {
            super(EntityType.KANGAROO, level);
            this.expected = expected;
        }

        @Override
        public void resetKangarooSlots() {
            if (expected != null) {
                assertPouch(expected, this);
                selections++;
            }
            super.resetKangarooSlots();
        }
    }

    private static class DeathKangaroo extends EntityKangaroo {
        final List<ItemStack> drops = new ArrayList<>();

        DeathKangaroo(ServerLevel level) {
            super(EntityType.KANGAROO, level);
        }

        void dropAll(DamageSource damage) {
            super.dropAllDeathLoot((ServerLevel)level(), damage);
        }

        @Override
        public ItemEntity spawnAtLocation(ServerLevel level, ItemStack stack, float offset) {
            drops.add(stack.copy());
            return null;
        }

        @Override
        protected void dropFromLootTable(ServerLevel level, DamageSource damage, boolean recentlyHit) {
            // Only world loot-table I/O is omitted; the real death hooks and ordering run.
        }

        @Override
        protected void dropExperience(ServerLevel level, net.minecraft.world.entity.Entity attacker) {
        }
    }
}
