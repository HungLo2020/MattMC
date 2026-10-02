package net.minecraft.world.level.storage.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

/** Uses the production registry/tag loader, item tool rules and bundled loot codec.
 * The package permits a loot context without starting a server; this is not a
 * substitute for an in-game Survival break and neighbor-update integration test. */
@Isolated("Reloads global block and item registry tags")
class AmbersolHarvestingTest {
    private static RegistryAccess.Frozen registries;
    private static Map<TagKey<Block>, List<Holder<Block>>> originalBlockTags;
    private static Map<TagKey<Item>, List<Holder<Item>>> originalItemTags;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        originalBlockTags = snapshotTags(BuiltInRegistries.BLOCK);
        originalItemTags = snapshotTags(BuiltInRegistries.ITEM);
    }

    @BeforeEach
    void loadBundledTags() {
        reloadTags(null);
    }

    @AfterAll
    static void restoreTags() {
        try {
            BuiltInRegistries.BLOCK.prepareTagReload(new TagLoader.LoadResult<>(BuiltInRegistries.BLOCK.key(), originalBlockTags)).apply();
        } finally {
            BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(BuiltInRegistries.ITEM.key(), originalItemTags)).apply();
        }
        assertEquals(originalBlockTags, snapshotTags(BuiltInRegistries.BLOCK));
        assertEquals(originalItemTags, snapshotTags(BuiltInRegistries.ITEM));
    }

    @Test
    void bundledTagsRequireAStoneTierOrBetterPickaxe() {
        BlockState state = Blocks.AMBERSOL.defaultBlockState();
        assertTrue(state.requiresCorrectToolForDrops());
        assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE));
        assertTrue(state.is(BlockTags.NEEDS_STONE_TOOL));
        for (Item item : List.of(Items.STONE_PICKAXE, Items.COPPER_PICKAXE, Items.IRON_PICKAXE,
                Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE)) {
            assertTrue(new ItemStack(item).isCorrectToolForDrops(state), item.toString());
        }
        assertFalse(ItemStack.EMPTY.isCorrectToolForDrops(state));
        for (Item item : List.of(Items.WOODEN_PICKAXE, Items.GOLDEN_PICKAXE, Items.DIAMOND_AXE,
                Items.DIAMOND_HOE, Items.DIAMOND_SHOVEL, Items.DIAMOND_SWORD, Items.SHEARS)) {
            assertFalse(new ItemStack(item).isCorrectToolForDrops(state), item.toString());
        }
    }

    @Test
    void efficiencyDoesNotBypassToolTypeOrTier() throws IOException {
        Holder<Enchantment> efficiency = loadEfficiency();
        for (Item item : List.of(Items.STONE_PICKAXE, Items.WOODEN_PICKAXE, Items.GOLDEN_PICKAXE, Items.DIAMOND_AXE)) {
            ItemStack stack = new ItemStack(item);
            boolean correctBeforeEnchanting = stack.isCorrectToolForDrops(Blocks.AMBERSOL.defaultBlockState());
            stack.enchant(efficiency, 5);
            assertTrue(stack.isEnchanted());
            assertEquals(correctBeforeEnchanting, stack.isCorrectToolForDrops(Blocks.AMBERSOL.defaultBlockState()), item.toString());
        }
    }

    @Test
    void damagedPickaxeWorksUntilItsLastDurabilityIsUsed() {
        ItemStack stack = new ItemStack(Items.STONE_PICKAXE);
        BlockState state = Blocks.AMBERSOL.defaultBlockState();
        stack.setDamageValue(stack.getMaxDamage() - 1);
        assertTrue(stack.isCorrectToolForDrops(state));
        assertTrue(stack.getDestroySpeed(state) > 1.0F);
        AtomicInteger breaks = new AtomicInteger();
        int damagePerBlock = stack.get(DataComponents.TOOL).damagePerBlock();
        assertEquals(1, damagePerBlock);
        // An unenchanted stack needs no server lookup to apply normal mining damage.
        stack.hurtAndBreak(damagePerBlock, null, null, item -> breaks.incrementAndGet());
        assertTrue(stack.isBroken());
        assertEquals(1, stack.getCount());
        assertFalse(stack.isCorrectToolForDrops(state));
        assertEquals(1.0F, stack.getDestroySpeed(state));
        stack.hurtAndBreak(damagePerBlock, null, null, item -> breaks.incrementAndGet());
        assertEquals(1, breaks.get());
    }

    @Test
    void bundledLootStillDropsExactlyOneAmbersolForOrdinaryAndEnchantedTools() throws IOException {
        try (var resources = resources(null)) {
            var location = ResourceLocation.withDefaultNamespace("loot_table/blocks/ambersol.json");
            LootTable loot;
            try (Reader reader = resources.getResourceOrThrow(location).openAsReader()) {
                loot = LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), JsonParser.parseReader(reader)).getOrThrow();
            }
            assertEquals(ResourceLocation.withDefaultNamespace("blocks/ambersol"), Blocks.AMBERSOL.getLootTable().orElseThrow().location());
            ItemStack ordinary = new ItemStack(Items.STONE_PICKAXE);
            ItemStack enchanted = ordinary.copy();
            enchanted.enchant(loadEfficiency(), 5);
            for (ItemStack tool : List.of(ordinary, enchanted)) {
                LootParams params = new LootParams.Builder(null)
                    .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                    .withParameter(LootContextParams.BLOCK_STATE, Blocks.AMBERSOL.defaultBlockState())
                    .withParameter(LootContextParams.TOOL, tool)
                    .create(loot.getParamSet());
                List<ItemStack> drops = new ArrayList<>();
                loot.getRandomItemsRaw(new LootContext(params, RandomSource.create(0), registries), drops::add);
                assertEquals(1, drops.size());
                assertTrue(drops.getFirst().is(Items.AMBERSOL));
                assertEquals(1, drops.getFirst().getCount());
            }
        }
    }

    @Test
    void dataPackCanRaiseTierAndReloadRestoresBundledBehavior(@TempDir Path pack) throws IOException {
        ItemStack stone = new ItemStack(Items.STONE_PICKAXE);
        ItemStack iron = new ItemStack(Items.IRON_PICKAXE);
        assertTrue(stone.isCorrectToolForDrops(Blocks.AMBERSOL.defaultBlockState()));
        writeTag(pack, "needs_iron_tool", "{\"values\":[\"minecraft:ambersol\"]}");
        reloadTags(pack);
        assertFalse(stone.isCorrectToolForDrops(Blocks.AMBERSOL.defaultBlockState()));
        assertTrue(iron.isCorrectToolForDrops(Blocks.AMBERSOL.defaultBlockState()));
        assertTrue(stone.isCorrectToolForDrops(Blocks.COPPER_ORE.defaultBlockState()));
        reloadTags(null);
        assertTrue(stone.isCorrectToolForDrops(Blocks.AMBERSOL.defaultBlockState()));
        assertUnrelatedHarvestingRules();
    }

    @Test
    void dataPackCanReplacePickaxeTagOnExistingToolStacks(@TempDir Path pack) throws IOException {
        ItemStack stone = new ItemStack(Items.STONE_PICKAXE);
        assertTrue(stone.isCorrectToolForDrops(Blocks.AMBERSOL.defaultBlockState()));
        writeTag(pack, "mineable/pickaxe", "{\"replace\":true,\"values\":[\"minecraft:stone\"]}");
        reloadTags(pack);
        assertFalse(stone.isCorrectToolForDrops(Blocks.AMBERSOL.defaultBlockState()));
        assertTrue(stone.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()));
        reloadTags(null);
        assertTrue(stone.isCorrectToolForDrops(Blocks.AMBERSOL.defaultBlockState()));
        assertUnrelatedHarvestingRules();
    }

    private static void assertUnrelatedHarvestingRules() {
        assertTrue(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(Blocks.STONE.defaultBlockState()));
        assertFalse(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(Blocks.COPPER_ORE.defaultBlockState()));
        assertTrue(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(Blocks.COPPER_ORE.defaultBlockState()));
        assertFalse(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState()));
        assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState()));
        assertTrue(new ItemStack(Items.WOODEN_AXE).isCorrectToolForDrops(Blocks.OAK_LOG.defaultBlockState()));
    }

    private static Holder<Enchantment> loadEfficiency() throws IOException {
        try (var resources = resources(null);
                Reader reader = resources.getResourceOrThrow(ResourceLocation.withDefaultNamespace("enchantment/efficiency.json")).openAsReader()) {
            return Holder.direct(Enchantment.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), JsonParser.parseReader(reader)).getOrThrow());
        }
    }

    private static <T> Map<TagKey<T>, List<Holder<T>>> snapshotTags(Registry<T> registry) {
        return registry.listTags().collect(Collectors.toMap(HolderSet.Named::key, tag -> tag.stream().toList()));
    }

    private static void reloadTags(Path overlay) {
        try (var resources = resources(overlay)) {
            var access = new RegistryAccess.ImmutableRegistryAccess(List.of(BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM));
            TagLoader.loadTagsForExistingRegistries(resources, access).forEach(Registry.PendingTags::apply);
        }
    }

    private static MultiPackResourceManager resources(Path overlay) {
        List<PackResources> packs = new ArrayList<>();
        packs.add(ServerPacksSource.createVanillaPackSource());
        if (overlay != null) {
            var info = new PackLocationInfo("ambersol-test", Component.literal("Ambersol test"), PackSource.WORLD, Optional.empty());
            packs.add(new PathPackResources(info, overlay));
        }
        return new MultiPackResourceManager(PackType.SERVER_DATA, packs);
    }

    private static void writeTag(Path pack, String name, String json) throws IOException {
        Path file = pack.resolve("data/minecraft/tags/block/" + name + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
    }
}
