package net.minecraft.world.level.block.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A lit furnace compares a stock smelting result instead of assembling a
 * transient copy each tick, and copies it once on burn. A recipe subclass keeps
 * the original assemble path; both must produce the same furnace history.
 */
class NativeFurnaceTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    static final ResourceKey<Recipe<?>> IRON = ResourceKey.create(Registries.RECIPE, ResourceLocation.withDefaultNamespace("test_iron"));
    static final ResourceKey<Recipe<?>> SAND = ResourceKey.create(Registries.RECIPE, ResourceLocation.withDefaultNamespace("test_glass"));

    static net.minecraft.core.HolderLookup.Provider registries() { return RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY); }

    static FurnaceBlockEntity furnace(boolean exact, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        return exact ? new FurnaceBlockEntity(pos, state) : new FurnaceBlockEntity(pos, state) {};
    }

    static List<String> history(boolean exact, boolean stock, int[] assembles) {
        var events = new ArrayList<String>();
        SmeltingRecipe iron = stock
            ? new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE), new ItemStack(Items.IRON_INGOT), 0.7F, 7)
            : new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE), new ItemStack(Items.IRON_INGOT), 0.7F, 7) {
                @Override public ItemStack assemble(SingleRecipeInput input, net.minecraft.core.HolderLookup.Provider provider) { assembles[0]++; return super.assemble(input, provider); }
            };
        SmeltingRecipe glass = stock
            ? new SmeltingRecipe("", CookingBookCategory.BLOCKS, Ingredient.of(Items.SAND), new ItemStack(Items.GLASS, 2), 0.1F, 5)
            : new SmeltingRecipe("", CookingBookCategory.BLOCKS, Ingredient.of(Items.SAND), new ItemStack(Items.GLASS, 2), 0.1F, 5) {};
        assertEquals(stock, iron.resultIfPlainCopy() != null);
        assertEquals(stock, glass.resultIfPlainCopy() != null);
        RecipeManager recipes = mock(RecipeManager.class);
        when(recipes.getRecipeFor(any(RecipeType.class), any(), any(), nullable(ResourceKey.class))).thenAnswer(call -> {
            ItemStack input = ((SingleRecipeInput)call.getArgument(1)).item();
            if (input.is(Items.IRON_ORE)) return Optional.of(new RecipeHolder<>(IRON, iron));
            if (input.is(Items.SAND)) return Optional.of(new RecipeHolder<>(SAND, glass));
            return Optional.empty();
        });
        net.minecraft.world.level.block.entity.FuelValues fuel = mock(net.minecraft.world.level.block.entity.FuelValues.class);
        when(fuel.burnDuration(any())).thenAnswer(call -> ((ItemStack)call.getArgument(0)).is(Items.COAL) ? 40 : ((ItemStack)call.getArgument(0)).is(Items.LAVA_BUCKET) ? 90 : 0);
        ServerLevel level = mock(ServerLevel.class);
        when(level.recipeAccess()).thenReturn(recipes);
        when(level.fuelValues()).thenReturn(fuel);
        when(level.registryAccess()).thenReturn(mock(RegistryAccess.class));
        doAnswer(call -> { events.add("setBlock lit=" + ((net.minecraft.world.level.block.state.BlockState)call.getArgument(1)).getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT)); return true; })
            .when(level).setBlock(any(), any(), anyInt());
        doAnswer(call -> { events.add("changed"); return null; }).when(level).blockEntityChanged(any());
        BlockPos pos = BlockPos.ZERO;
        var state = Blocks.FURNACE.defaultBlockState();
        var furnace = furnace(exact, pos, state);
        furnace.setLevel(level);
        assertEquals(exact, furnace.nativeTicksForTest(), "only the exact class ticks natively");
        var random = new java.util.Random(17);
        int burns = 0;
        for (int tick = 0; tick < 2000; tick++) {
            switch (random.nextInt(14)) {
                case 0 -> { var input = random.nextBoolean() ? Items.IRON_ORE : random.nextBoolean() ? Items.SAND : Items.DIRT; furnace.setItem(0, new ItemStack(input, 1 + random.nextInt(5))); }
                case 1 -> furnace.setItem(1, random.nextInt(3) == 0 ? ItemStack.EMPTY : random.nextInt(4) == 0 ? new ItemStack(Items.LAVA_BUCKET) : new ItemStack(Items.COAL, 1 + random.nextInt(3)));
                case 2 -> { var taken = furnace.removeItem(2, 1 + random.nextInt(64)); events.add("took " + taken.getItem() + "x" + taken.getCount()); }
                case 3 -> furnace.setItem(2, random.nextInt(3) == 0 ? new ItemStack(Items.IRON_INGOT, 63) : random.nextBoolean() ? new ItemStack(Items.GLASS, 64) : ItemStack.EMPTY);
                case 4 -> {
                    if (random.nextInt(4) == 0) {
                        // Save and load into a fresh furnace of the same kind.
                        var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, registries());
                        furnace.saveAdditional(out);
                        var loaded = furnace(exact, pos, state);
                        loaded.setLevel(level);
                        loaded.loadAdditional(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, registries(), out.buildResult()));
                        furnace = loaded;
                        assertEquals(exact, furnace.nativeTicksForTest(), "a loaded exact furnace rebinds native rows");
                        events.add("reloaded");
                    }
                }
                case 5 -> {
                    if (random.nextInt(6) == 0) {
                        // Replace the list: a plain list runs later ticks in Java, native rows rebind.
                        boolean rows = random.nextBoolean();
                        var list = rows ? net.minecraft.world.item.NativeItemSlots.withSize(3) : net.minecraft.core.NonNullList.withSize(3, ItemStack.EMPTY);
                        for (int i = 0; i < 3; i++) list.set(i, furnace.getItem(i));
                        furnace.setItems(list);
                        assertEquals(exact && rows, furnace.nativeTicksForTest(), "only native rows keep native ticks");
                        events.add("setItems rows=" + rows);
                    }
                }
                default -> {}
            }
            ItemStack before = furnace.getItem(2); int beforeCount = before.getCount();
            AbstractFurnaceBlockEntity.serverTick(level, pos, furnace.getBlockState(), furnace);
            if (furnace.getItem(2) != before || furnace.getItem(2).getCount() != beforeCount) burns++;
            events.add("data " + furnace.dataAccess.get(0) + "," + furnace.dataAccess.get(1) + "," + furnace.dataAccess.get(2) + "," + furnace.dataAccess.get(3));
            events.add(tick + " lit=" + furnace.litTimeRemaining() + "/" + furnace.litTotalTime() + " cook=" + furnace.cookingTimer() + "/" + furnace.cookingTotalTime()
                + " " + describe(furnace.getItem(0)) + " " + describe(furnace.getItem(1)) + " " + describe(furnace.getItem(2)));
            if (stock) assertNotSame(iron.resultIfPlainCopy(), furnace.getItem(2), "the stored result is a copy, never the recipe's own stack");
        }
        assertTrue(burns > 20, "the schedule must really burn: " + burns);
        if (stock) events.add("recipe results " + describe(iron.resultIfPlainCopy()) + " " + describe(glass.resultIfPlainCopy()));
        else events.add("recipe results " + describe(iron.assemble(null, null)) + " " + describe(glass.assemble(null, null)));
        return events;
    }

    static String describe(ItemStack stack) { return stack.getItem() + "x" + stack.getCount(); }

    static void same(List<String> expected, List<String> actual, String label) {
        for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
            if (!expected.get(i).equals(actual.get(i))) {
                fail(label + ": first difference at event " + i + "\n expected " + expected.subList(Math.max(0, i - 4), Math.min(expected.size(), i + 3))
                    + "\n actual   " + actual.subList(Math.max(0, i - 4), Math.min(actual.size(), i + 3)));
            }
        }
        assertEquals(expected.size(), actual.size(), label + ": event count");
    }

    @Test void stockResultTemplateMatchesTheAssemblePath() {
        int[] assembles = {0};
        var original = history(false, false, assembles);
        assertTrue(assembles[0] > 100, "the subclass path really assembled every check: " + assembles[0]);
        same(original, history(false, true, new int[1]), "Java furnace, stock template");
        same(original, history(true, false, new int[1]), "native furnace, recipe subclass (Java fallback segments)");
        long decisions = AbstractFurnaceBlockEntity.nativeDecisions, fallbacks = AbstractFurnaceBlockEntity.nativeFallbacks;
        same(original, history(true, true, new int[1]), "native furnace, stock template (native decisions)");
        long decided = AbstractFurnaceBlockEntity.nativeDecisions - decisions, fell = AbstractFurnaceBlockEntity.nativeFallbacks - fallbacks;
        assertTrue(decided > 500 && fell < decided / 4, "stock recipes are mostly decided natively: " + decided + " decided, " + fell + " fell back");
    }

    /** Fuel runs out mid-cook: the timer cools by 2 per tick, clamped, on both paths. */
    static List<String> coolDown(boolean exact) {
        var events = new ArrayList<String>();
        var iron = new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE), new ItemStack(Items.IRON_INGOT), 0.7F, 7);
        RecipeManager recipes = mock(RecipeManager.class);
        when(recipes.getRecipeFor(any(RecipeType.class), any(), any(), nullable(ResourceKey.class))).thenAnswer(call ->
            ((SingleRecipeInput)call.getArgument(1)).item().is(Items.IRON_ORE) ? Optional.of(new RecipeHolder<>(IRON, iron)) : Optional.empty());
        net.minecraft.world.level.block.entity.FuelValues fuel = mock(net.minecraft.world.level.block.entity.FuelValues.class);
        when(fuel.burnDuration(any())).thenAnswer(call -> ((ItemStack)call.getArgument(0)).is(Items.COAL) ? 40 : 0);
        ServerLevel level = mock(ServerLevel.class);
        when(level.recipeAccess()).thenReturn(recipes); when(level.fuelValues()).thenReturn(fuel); when(level.registryAccess()).thenReturn(mock(RegistryAccess.class));
        var furnace = furnace(exact, BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
        furnace.setLevel(level);
        furnace.setItem(0, new ItemStack(Items.IRON_ORE, 64)); furnace.setItem(1, new ItemStack(Items.COAL, 1));
        for (int tick = 0; tick < 60; tick++) {
            AbstractFurnaceBlockEntity.serverTick(level, BlockPos.ZERO, furnace.getBlockState(), furnace);
            events.add(tick + " lit=" + furnace.litTimeRemaining() + " cook=" + furnace.cookingTimer() + "/" + furnace.cookingTotalTime() + " " + describe(furnace.getItem(2)));
        }
        return events;
    }

    @Test void coolDownMatchesTheOriginal() {
        var original = coolDown(false);
        assertTrue(original.stream().anyMatch(e -> e.contains("lit=0 cook=3/")), "the scenario really cools a running timer: " + original);
        same(original, coolDown(true), "native cool-down");
    }

    /** A dropped exact furnace retires its native owner (and with it the rows reference). */
    @Test void droppedFurnaceRetiresItsNativeOwner() throws Exception {
        var field = AbstractFurnaceBlockEntity.class.getDeclaredField("nativeFurnace"); field.setAccessible(true);
        var handleField = net.minecraft.world.item.NativeItemState.Furnace.class.getDeclaredField("handle"); handleField.setAccessible(true);
        var live = net.minecraft.world.item.NativeItemState.class.getDeclaredMethod("liveForTest", long.class); live.setAccessible(true);
        long handle;
        { var furnace = new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState()); handle = (long)handleField.get(field.get(furnace)); }
        assertTrue((boolean)live.invoke(null, handle));
        long deadline = System.nanoTime() + 5_000_000_000L;
        while ((boolean)live.invoke(null, handle) && System.nanoTime() < deadline) { System.gc(); Thread.sleep(20); }
        assertFalse((boolean)live.invoke(null, handle), "the Cleaner released the native furnace owner");
    }

    @Test void onlyStockCookingRecipesWithPureResultsExposeTheirResult() {
        var stock = new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE), new ItemStack(Items.IRON_INGOT), 0.7F, 7);
        assertSame(stock.resultIfPlainCopy(), stock.resultIfPlainCopy());
        assertNotNull(new net.minecraft.world.item.crafting.BlastingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE), new ItemStack(Items.IRON_INGOT), 0.7F, 7).resultIfPlainCopy());
        assertNull(new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE), new ItemStack(Items.IRON_INGOT), 0.7F, 7) {}.resultIfPlainCopy(), "a subclass may override assemble");
        assertNull(new net.minecraft.world.item.crafting.StonecutterRecipe("", Ingredient.of(Items.STONE), new ItemStack(Items.STONE_SLAB, 2)).resultIfPlainCopy(), "only cooking recipes are admitted");
    }
}
