package net.minecraft.advancements;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.DataFixer;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.critereon.RecipeCraftedTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.packs.NativePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.players.PlayerList;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.NativeEntityGeometry;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Headless integration of the bundled resource loaders, real menus/Crafter tick,
 * recipe triggers and PlayerAdvancements progress. World queries, player identity,
 * network and reward side effects are bounded mocks; no trigger or award is mocked.
 */
@Isolated("Temporarily loads bundled tags into the static registries")
class RecipeCraftedAdvancementTest {
    private static final String CRAFTER = "adventure/crafters_crafting_crafters";
    private static final String POT = "adventure/craft_decorated_pot_using_only_sherds";
    private static final String ANY = "adventure/trim_with_any_armor_pattern";
    private static final String ALL = "adventure/trim_with_all_exclusive_armor_patterns";
    private static final List<String> PATTERNS = List.of("bolt", "coast", "dune", "eye", "flow", "host", "raiser", "rib", "sentry", "shaper", "silence", "snout", "spire", "tide", "vex", "ward", "wayfinder", "wild");
    private static final List<String> EXCLUSIVE = List.of("rib", "silence", "snout", "spire", "tide", "vex", "ward", "wayfinder");
    private static final BlockPos POS = new BlockPos(0, 64, 0);
    private static final List<Runnable> RESTORE_TAGS = new ArrayList<>();
    private static RegistryAccess.Frozen registries;
    private static Loaded bundled;
    @TempDir Path temporary;

    @BeforeAll
    static void loadBundledServerData() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var layers = RegistryLayer.createRegistryAccess();
        layers.getLayer(RegistryLayer.STATIC).registries().forEach(entry -> rememberTags(entry.value()));
        try (var resources = resources(null)) {
            var pending = TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
            var lookups = TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.WORLDGEN), pending);
            var worldgen = RegistryDataLoader.load(resources, lookups, RegistryDataLoader.WORLDGEN_REGISTRIES);
            pending.forEach(Registry.PendingTags::apply);
            registries = layers.replaceFrom(RegistryLayer.WORLDGEN, worldgen).compositeAccess();
            bundled = load(resources);
        }
    }

    @AfterAll
    static void restoreGlobalTags() {
        RESTORE_TAGS.forEach(Runnable::run);
    }

    @Test
    void everyBundledRecipeCriterionResolvesToItsIntendedLoadedRecipe() {
        List<String> actual = new ArrayList<>();
        for (AdvancementHolder advancement : bundled.advancements.getAllAdvancements()) {
            advancement.value().criteria().forEach((name, criterion) -> {
                if (criterion.triggerInstance() instanceof RecipeCraftedTrigger.TriggerInstance trigger) {
                    actual.add(advancement.id() + "/" + name);
                    assertTrue(bundled.recipes.byKey(trigger.recipeId()).isPresent(),
                        advancement.id() + "/" + name + " references unloaded " + trigger.recipeId().location());
                }
            });
        }
        assertEquals(28, actual.size(), "Inventory every recipe-crafted criterion, including newly added advancements");
        assertRecipeKey(CRAFTER, "crafter_crafted_crafter", "crafting/crafter");
        assertRecipeKey(POT, "pot_crafted_using_only_sherds", "crafting/decorated_pot");
        for (String pattern : PATTERNS) assertRecipeKey(ANY, trimCriterion(pattern), trimRecipe(pattern));
        for (String pattern : EXCLUSIVE) assertRecipeKey(ALL, trimCriterion(pattern), trimRecipe(pattern));
        assertTrue(bundled.recipes.byKey(recipeKey("crafter")).isEmpty(), "Do not add compatibility recipes for flat keys");
        assertTrue(bundled.recipes.byKey(recipeKey("decorated_pot")).isEmpty());
    }

    @Test
    void ingredientAndAnyAllRequirementsArePreserved() {
        var pot = (RecipeCraftedTrigger.TriggerInstance) advancement(POT).value().criteria().get("pot_crafted_using_only_sherds").triggerInstance();
        assertEquals(4, pot.ingredients().size());
        pot.ingredients().forEach(ingredient -> {
            assertTrue(ingredient.test(new ItemStack(Items.ANGLER_POTTERY_SHERD)));
            assertFalse(ingredient.test(new ItemStack(Items.BRICK)));
        });
        assertEquals(List.of(PATTERNS.stream().map(RecipeCraftedAdvancementTest::trimCriterion).toList()), advancement(ANY).value().requirements().requirements());
        assertEquals(EXCLUSIVE.stream().map(RecipeCraftedAdvancementTest::trimCriterion).map(List::of).toList(), advancement(ALL).value().requirements().requirements());
    }

    @Test
    void crafterLooseOutputAwardsNearbyPlayerButDifferentRecipeDoesNot() throws Exception {
        try (Fixture f = new Fixture(bundled)) {
            f.crafter(stickInputs());
            assertTrue(f.drops.getLast().getItem().is(Items.STICK));
            assertFalse(f.progress(CRAFTER).hasProgress(), "Different successfully crafted recipe must not award");
            f.crafter(crafterInputs());
            assertTrue(f.drops.getLast().getItem().is(Items.CRAFTER));
            assertTrue(f.progress(CRAFTER).getCriterion("crafter_crafted_crafter").isDone());
            assertTrue(f.progress(CRAFTER).isDone());
            verify(f.level, times(2)).getEntitiesOfClass(eq(ServerPlayer.class), eq(AABB.ofSize(Vec3.atCenterOf(POS), 17, 17, 17)));
        }
    }

    @Test
    void crafterDoesNotAwardAnOutOfRangePlayer() throws Exception {
        try (Fixture f = new Fixture(bundled)) {
            f.playerPosition = Vec3.atCenterOf(POS).add(20, 0, 0);
            f.crafter(crafterInputs());
            assertEquals(1, f.drops.size());
            assertFalse(f.progress(CRAFTER).hasProgress());
        }
    }

    @Test
    void crafterFullyAcceptedFrontContainerRetainsExistingNoDispatchBoundary() throws Exception {
        try (Fixture f = new Fixture(bundled)) {
            BlockPos front = POS.north();
            var barrel = new BarrelBlockEntity(front, Blocks.BARREL.defaultBlockState());
            when(f.level.getBlockState(front)).thenReturn(Blocks.BARREL.defaultBlockState());
            when(f.level.getBlockEntity(front)).thenReturn(barrel);
            f.crafter(crafterInputs());
            assertTrue(barrel.getItem(0).is(Items.CRAFTER));
            assertTrue(f.drops.isEmpty());
            assertFalse(f.progress(CRAFTER).hasProgress());
            verify(f.level, never()).getEntitiesOfClass(eq(ServerPlayer.class), any(AABB.class));
        }
    }

    @Test
    void craftingFourSherdsAwardsPotCriterionThroughResultSlot() throws Exception {
        try (Fixture f = new Fixture(bundled)) {
            ItemStack pot = f.pot(-1);
            assertTrue(pot.is(Items.DECORATED_POT));
            assertTrue(f.progress(POT).getCriterion("pot_crafted_using_only_sherds").isDone());
            assertTrue(f.progress(POT).isDone());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void aBrickInAnyPotIngredientDoesNotAward(int brickIndex) throws Exception {
        try (Fixture f = new Fixture(bundled)) {
            assertTrue(f.pot(brickIndex).is(Items.DECORATED_POT), "Negative control must still craft a pot");
            assertFalse(f.progress(POT).hasProgress(), "All four ingredient predicates must match distinct sherds");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"bolt", "coast", "dune", "eye", "flow", "host", "raiser", "rib", "sentry", "shaper", "silence", "snout", "spire", "tide", "vex", "ward", "wayfinder", "wild"})
    void everyTrimAwardsItsOwnCriterionThroughSmithingOutput(String pattern) throws Exception {
        try (Fixture f = new Fixture(bundled)) {
            f.smith(pattern);
            assertTrue(f.progress(ANY).getCriterion(trimCriterion(pattern)).isDone());
            assertEquals(1, StreamSupport.completedCount(f.progress(ANY)));
            assertTrue(f.progress(ANY).isDone(), "Any single pattern completes the OR requirement");
            assertFalse(f.progress(ALL).isDone());
            assertEquals(EXCLUSIVE.contains(pattern) ? 1 : 0, StreamSupport.completedCount(f.progress(ALL)));
            verify(f.player).awardRecipes(argThat(recipes -> recipes.size() == 1
                && recipes.iterator().next().id().equals(recipeKey(trimRecipe(pattern)))));
        }
    }

    @Test
    void exclusiveAdvancementRequiresEightDifferentSmithingOutputs() throws Exception {
        try (Fixture f = new Fixture(bundled)) {
            f.smith("sentry");
            assertFalse(f.progress(ALL).hasProgress(), "Non-exclusive patterns do not advance the AND requirement");
            for (int i = 0; i < EXCLUSIVE.size(); i++) {
                f.smith(EXCLUSIVE.get(i));
                assertEquals(i + 1, StreamSupport.completedCount(f.progress(ALL)));
                assertEquals(i == 7, f.progress(ALL).isDone(), "Completion must occur only after all eight distinct patterns");
                if (i == 0) {
                    f.smith(EXCLUSIVE.get(i));
                    assertEquals(1, StreamSupport.completedCount(f.progress(ALL)), "Repeating a pattern is not a second requirement");
                }
            }
        }
    }

    @Test
    void highPriorityRecipeAndAdvancementOverridesReloadAndThenRevert() throws Exception {
        Path pack = temporary.resolve("override");
        JsonObject override;
        try (var resources = resources(null); Reader reader = resources.getResourceOrThrow(id("advancement/" + CRAFTER + ".json")).openAsReader()) {
            override = JsonParser.parseReader(reader).getAsJsonObject();
        }
        override.getAsJsonObject("criteria").getAsJsonObject("crafter_crafted_crafter").getAsJsonObject("conditions").addProperty("recipe_id", "minecraft:crafting/stick");
        writeResource(pack, "advancement/" + CRAFTER + ".json", override.toString());
        writeResource(pack, "recipe/crafting/stick.json", """
            {"type":"minecraft:crafting_shaped","category":"misc","key":{"#":"minecraft:oak_planks"},"pattern":["#","#"],"result":{"id":"minecraft:diamond","count":2}}
            """);
        try (Fixture f = new Fixture(bundled)) {
            try (var resources = resources(pack)) {
                assertEquals("recipe-advancement-test", resources.getResourceOrThrow(id("advancement/" + CRAFTER + ".json")).sourcePackId());
                f.reload(load(resources));
            }
            f.crafter(crafterInputs());
            assertFalse(f.progress(CRAFTER).hasProgress(), "Reload must remove listeners for the old recipe key");
            f.crafter(stickInputs());
            assertTrue(f.drops.getLast().getItem().is(Items.DIAMOND));
            assertEquals(2, f.drops.getLast().getItem().getCount());
            assertTrue(f.progress(CRAFTER).isDone(), "Override criterion follows the loaded override recipe");
            try (var resources = resources(null)) { f.reload(load(resources)); }
            f.crafter(stickInputs());
            assertTrue(f.drops.getLast().getItem().is(Items.STICK));
            assertFalse(f.progress(CRAFTER).hasProgress(), "Removed override must leave no stale listener");
            f.crafter(crafterInputs());
            assertTrue(f.progress(CRAFTER).isDone());
        }
    }

    private static AdvancementHolder advancement(String name) {
        return assertNotNullHolder(bundled.advancements.get(id(name)), name);
    }

    private static AdvancementHolder assertNotNullHolder(AdvancementHolder holder, String name) {
        assertNotNull(holder, "Actual advancement loader did not load " + name);
        return holder;
    }

    private static void assertRecipeKey(String advancement, String criterion, String recipe) {
        var trigger = (RecipeCraftedTrigger.TriggerInstance) advancement(advancement).value().criteria().get(criterion).triggerInstance();
        assertEquals(recipeKey(recipe), trigger.recipeId(), advancement + "/" + criterion);
        assertTrue(bundled.recipes.byKey(recipeKey(recipe)).isPresent());
    }

    private static ResourceLocation id(String path) { return ResourceLocation.withDefaultNamespace(path); }
    private static ResourceKey<Recipe<?>> recipeKey(String path) { return ResourceKey.create(Registries.RECIPE, id(path)); }
    private static String trimCriterion(String pattern) { return "armor_trimmed_minecraft:" + pattern + "_armor_trim_smithing_template_smithing_trim"; }
    private static String trimRecipe(String pattern) { return "smithing/" + pattern + "_armor_trim_smithing_template_smithing_trim"; }

    private static Item[] crafterInputs() {
        return new Item[] { Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT, Items.CRAFTING_TABLE, Items.IRON_INGOT, Items.REDSTONE, Items.DROPPER, Items.REDSTONE };
    }

    private static Item[] stickInputs() {
        return new Item[] { Items.OAK_PLANKS, Items.AIR, Items.AIR, Items.OAK_PLANKS, Items.AIR, Items.AIR, Items.AIR, Items.AIR, Items.AIR };
    }

    private static <T> void rememberTags(Registry<T> registry) {
        Map<TagKey<T>, List<Holder<T>>> old = registry.listTags().collect(Collectors.toMap(HolderSet.Named::key, tag -> tag.stream().toList()));
        RESTORE_TAGS.add(() -> registry.prepareTagReload(new TagLoader.LoadResult<>(registry.key(), old)).apply());
    }

    private static MultiPackResourceManager resources(Path override) {
        List<PackResources> packs = new ArrayList<>();
        packs.add(ServerPacksSource.createVanillaPackSource());
        if (override != null) packs.add(NativePackResources.directory(new PackLocationInfo("recipe-advancement-test", Component.literal("Recipe advancement test"), PackSource.WORLD, Optional.empty()), override, List.of()));
        return new MultiPackResourceManager(PackType.SERVER_DATA, packs);
    }

    private static void writeResource(Path pack, String path, String json) throws Exception {
        Path file = pack.resolve("data/minecraft/" + path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
    }

    private static Loaded load(ResourceManager resources) {
        RecipeManager recipes = new RecipeManager(registries);
        ServerAdvancementManager advancements = new ServerAdvancementManager(registries);
        var state = new PreparableReloadListener.SharedState(resources);
        var barrier = new PreparableReloadListener.PreparationBarrier() {
            @Override public <T> CompletableFuture<T> wait(T value) { return CompletableFuture.completedFuture(value); }
        };
        recipes.reload(state, Runnable::run, barrier, Runnable::run).join();
        recipes.finalizeRecipeLoading(FeatureFlags.DEFAULT_FLAGS);
        advancements.reload(state, Runnable::run, barrier, Runnable::run).join();
        return new Loaded(recipes, advancements);
    }

    private record Loaded(RecipeManager recipes, ServerAdvancementManager advancements) {}

    private static class StreamSupport {
        static long completedCount(AdvancementProgress progress) {
            return java.util.stream.StreamSupport.stream(progress.getCompletedCriteria().spliterator(), false).count();
        }
    }

    private final class Fixture implements AutoCloseable {
        final ServerLevel level = mock(ServerLevel.class);
        final MinecraftServer server = mock(MinecraftServer.class);
        final ServerPlayer player = mock(ServerPlayer.class);
        final Inventory inventory = new Inventory(player, new EntityEquipment());
        final List<ItemEntity> drops = new ArrayList<>();
        final PlayerAdvancements progress;
        Vec3 playerPosition = Vec3.atCenterOf(POS).add(2, 0, 0);
        Loaded loaded;

        Fixture(Loaded loaded) throws Exception {
            this.loaded = loaded;
            when(level.registryAccess()).thenReturn(registries);
            when(level.recipeAccess()).thenAnswer(call -> this.loaded.recipes);
            when(level.getServer()).thenReturn(server);
            when(level.enabledFeatures()).thenReturn(FeatureFlags.DEFAULT_FLAGS);
            when(level.getGameRules()).thenReturn(mock(GameRules.class));
            when(level.getChunkSource()).thenReturn(mock(ServerChunkCache.class));
            when(level.nativeEntityGeometry()).thenReturn(new NativeEntityGeometry());
            when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
            RandomSource random = RandomSource.create(825);
            Field randomField = Level.class.getDeclaredField("random");
            randomField.setAccessible(true);
            randomField.set(level, random);
            when(level.getRandom()).thenReturn(random);
            when(server.getRecipeManager()).thenAnswer(call -> this.loaded.recipes);
            when(server.reloadableRegistries()).thenReturn(new ReloadableServerRegistries.Holder(registries));
            when(player.level()).thenReturn(level);
            when(player.position()).thenAnswer(call -> playerPosition);
            when(player.getInventory()).thenReturn(inventory);
            player.connection = mock(ServerGamePacketListenerImpl.class);
            doCallRealMethod().when(player).triggerRecipeCrafted(any(), anyList());
            doCallRealMethod().when(player).awardRecipes(anyCollection());
            when(level.getEntitiesOfClass(eq(ServerPlayer.class), any(AABB.class))).thenAnswer(call -> ((AABB) call.getArgument(1)).contains(playerPosition) ? List.of(player) : List.of());
            when(level.addFreshEntity(any())).thenAnswer(call -> { drops.add((ItemEntity) call.getArgument(0)); return true; });
            progress = new PlayerAdvancements(mock(DataFixer.class), mock(PlayerList.class), loaded.advancements, temporary.resolve(UUID.randomUUID() + ".json"), player);
            when(player.getAdvancements()).thenReturn(progress);
        }

        void reload(Loaded replacement) {
            loaded = replacement;
            progress.reload(replacement.advancements);
        }

        AdvancementProgress progress(String name) {
            return progress.getOrStartProgress(assertNotNullHolder(loaded.advancements.get(id(name)), name));
        }

        void crafter(Item[] ingredients) {
            BlockState state = Blocks.CRAFTER.defaultBlockState();
            CrafterBlockEntity crafter = new CrafterBlockEntity(POS, state);
            for (int i = 0; i < ingredients.length; i++) crafter.setItem(i, new ItemStack(ingredients[i]));
            when(level.getBlockEntity(POS)).thenReturn(crafter);
            state.tick(level, POS, level.getRandom());
            for (int i = 0; i < 9; i++) assertTrue(crafter.getItem(i).isEmpty(), "A successful Crafter tick consumes inputs");
        }

        ItemStack pot(int brickIndex) {
            CraftingMenu menu = new CraftingMenu(1, inventory, ContainerLevelAccess.create(level, POS));
            int[] inputSlots = {2, 4, 6, 8};
            Item[] sherds = {Items.ANGLER_POTTERY_SHERD, Items.ARCHER_POTTERY_SHERD, Items.ARMS_UP_POTTERY_SHERD, Items.BLADE_POTTERY_SHERD};
            for (int i = 0; i < 4; i++) menu.getSlot(inputSlots[i]).set(new ItemStack(i == brickIndex ? Items.BRICK : sherds[i]));
            Slot result = menu.getSlot(CraftingMenu.RESULT_SLOT);
            assertTrue(result.getItem().is(Items.DECORATED_POT), "Bundled pot recipe must match the real crafting grid");
            ItemStack taken = result.remove(1);
            result.onTake(player, taken);
            for (int i : inputSlots) assertTrue(menu.getSlot(i).getItem().isEmpty());
            return taken;
        }

        void smith(String pattern) {
            SmithingMenu menu = new SmithingMenu(2, inventory, ContainerLevelAccess.create(level, POS));
            Item template = BuiltInRegistries.ITEM.getValue(id(pattern + "_armor_trim_smithing_template"));
            assertNotNull(template);
            menu.getSlot(0).set(new ItemStack(template));
            menu.getSlot(1).set(new ItemStack(Items.DIAMOND_CHESTPLATE));
            menu.getSlot(2).set(new ItemStack(Items.REDSTONE));
            Slot result = menu.getSlot(SmithingMenu.RESULT_SLOT);
            assertTrue(result.getItem().is(Items.DIAMOND_CHESTPLATE), "Bundled " + pattern + " smithing recipe must match");
            ItemStack taken = result.remove(1);
            assertEquals(id(pattern), taken.get(DataComponents.TRIM).pattern().unwrapKey().orElseThrow().location());
            result.onTake(player, taken);
            for (int i = 0; i < 3; i++) assertTrue(menu.getSlot(i).getItem().isEmpty(), "Taking smithing output consumes each input");
        }

        @Override public void close() { progress.stopListening(); }
    }
}
