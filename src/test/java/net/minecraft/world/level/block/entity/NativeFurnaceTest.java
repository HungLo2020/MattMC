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

    static List<String> history(boolean stock, int[] assembles) {
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
        var furnace = new FurnaceBlockEntity(pos, state);
        furnace.setLevel(level);
        var random = new java.util.Random(17);
        int burns = 0;
        for (int tick = 0; tick < 2000; tick++) {
            switch (random.nextInt(12)) {
                case 0 -> { var input = random.nextBoolean() ? Items.IRON_ORE : random.nextBoolean() ? Items.SAND : Items.DIRT; furnace.setItem(0, new ItemStack(input, 1 + random.nextInt(5))); }
                case 1 -> furnace.setItem(1, random.nextInt(4) == 0 ? new ItemStack(Items.LAVA_BUCKET) : new ItemStack(Items.COAL, 1 + random.nextInt(3)));
                case 2 -> { var taken = furnace.removeItem(2, 1 + random.nextInt(64)); events.add("took " + taken.getItem() + "x" + taken.getCount()); }
                case 3 -> furnace.setItem(2, random.nextInt(3) == 0 ? new ItemStack(Items.IRON_INGOT, 63) : random.nextBoolean() ? new ItemStack(Items.GLASS, 64) : ItemStack.EMPTY);
                default -> {}
            }
            ItemStack before = furnace.getItem(2); int beforeCount = before.getCount();
            AbstractFurnaceBlockEntity.serverTick(level, pos, furnace.getBlockState(), furnace);
            if (furnace.getItem(2) != before || furnace.getItem(2).getCount() != beforeCount) burns++;
            events.add(tick + " lit=" + furnace.litTimeRemaining + "/" + furnace.litTotalTime + " cook=" + furnace.cookingTimer + "/" + furnace.cookingTotalTime
                + " " + describe(furnace.getItem(0)) + " " + describe(furnace.getItem(1)) + " " + describe(furnace.getItem(2)));
            if (stock) assertNotSame(iron.resultIfPlainCopy(), furnace.getItem(2), "the stored result is a copy, never the recipe's own stack");
        }
        assertTrue(burns > 20, "the schedule must really burn: " + burns);
        if (stock) events.add("recipe results " + describe(iron.resultIfPlainCopy()) + " " + describe(glass.resultIfPlainCopy()));
        else events.add("recipe results " + describe(iron.assemble(null, null)) + " " + describe(glass.assemble(null, null)));
        return events;
    }

    static String describe(ItemStack stack) { return stack.getItem() + "x" + stack.getCount(); }

    @Test void stockResultTemplateMatchesTheAssemblePath() {
        int[] assembles = {0};
        assertEquals(history(false, assembles), history(true, new int[1]));
        assertTrue(assembles[0] > 100, "the subclass path really assembled every check: " + assembles[0]);
    }

    @Test void onlyStockCookingRecipesWithPureResultsExposeTheirResult() {
        var stock = new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE), new ItemStack(Items.IRON_INGOT), 0.7F, 7);
        assertSame(stock.resultIfPlainCopy(), stock.resultIfPlainCopy());
        assertNotNull(new net.minecraft.world.item.crafting.BlastingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE), new ItemStack(Items.IRON_INGOT), 0.7F, 7).resultIfPlainCopy());
        assertNull(new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE), new ItemStack(Items.IRON_INGOT), 0.7F, 7) {}.resultIfPlainCopy(), "a subclass may override assemble");
        assertNull(new net.minecraft.world.item.crafting.StonecutterRecipe("", Ingredient.of(Items.STONE), new ItemStack(Items.STONE_SLAB, 2)).resultIfPlainCopy(), "only cooking recipes are admitted");
    }
}
