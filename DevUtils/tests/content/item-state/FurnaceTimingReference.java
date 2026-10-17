import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import org.objenesis.ObjenesisStd;

/**
 * S0034 furnace consumer: one complete AbstractFurnaceBlockEntity.serverTick of a
 * lit furnace smelting iron ore (furnace_cooking), including the per-tick recipe
 * lookup and result checks and every seventh tick's burn. Results are cleared and
 * input/fuel refilled outside timing. The level, recipe lookup and fuel values
 * are plain subclasses instantiated without their constructors (Objenesis), so
 * no mocking framework runs on the timed path; identical on every side.
 * Arguments: output CSV, then a side flag (unused; kept for the shared driver).
 */
public final class FurnaceTimingReference {
    static final int POOL = 16, BATCHES = 256;
    static volatile long sink;
    static final List<String> rows = new ArrayList<>();
    static ServerLevel level;
    static final class Fixture {
        final FurnaceBlockEntity furnace = new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
        Fixture() { furnace.setLevel(level); refill(); }
        void refill() {
            if (furnace.getItem(0).getCount() < 8) furnace.setItem(0, new ItemStack(Items.IRON_ORE, 64));
            if (furnace.getItem(1).getCount() < 8) furnace.setItem(1, new ItemStack(Items.COAL, 64));
            if (furnace.getItem(2).getCount() > 48) furnace.setItem(2, ItemStack.EMPTY);
        }
        long run() { AbstractFurnaceBlockEntity.serverTick(level, BlockPos.ZERO, furnace.getBlockState(), furnace); return furnace.getItem(2).getCount() + 7L * furnace.getItem(0).getCount(); }
    }
    static final class Recipes extends RecipeManager {
        static Optional<?> HOLDER;
        Recipes() { super(null); }
        @SuppressWarnings("unchecked")
        @Override public <I extends net.minecraft.world.item.crafting.RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(RecipeType<T> type, I input, net.minecraft.world.level.Level level, ResourceKey<Recipe<?>> last) {
            return input instanceof net.minecraft.world.item.crafting.SingleRecipeInput single && single.item().is(Items.IRON_ORE) ? (Optional<RecipeHolder<T>>)HOLDER : Optional.empty();
        }
    }
    static final class Level extends ServerLevel {
        static RecipeManager RECIPES; static FuelValues FUEL;
        Level() { super(null, null, null, null, null, null, false, 0L, null, false, null); }
        @Override public RecipeManager recipeAccess() { return RECIPES; }
        @Override public FuelValues fuelValues() { return FUEL; }
        @Override public RegistryAccess registryAccess() { return RegistryAccess.EMPTY; }
        @Override public void blockEntityChanged(BlockPos pos) {}
        @Override public void updateNeighbourForOutputSignal(BlockPos pos, net.minecraft.world.level.block.Block block) {}
        @Override public boolean setBlock(BlockPos pos, net.minecraft.world.level.block.state.BlockState state, int flags) { return true; }
    }
    record Sample(long elapsed, long bytes, long checksum) {}
    static Sample run(Fixture[] fixtures) {
        long elapsed = 0, bytes = 0, checksum = 0;
        for (int batch = 0; batch < BATCHES; batch++) {
            for (var fixture : fixtures) fixture.refill();
            long allocated = StockChestTransferTimingReference.allocation(), start = System.nanoTime();
            for (var fixture : fixtures) checksum += fixture.run();
            elapsed += System.nanoTime() - start; bytes += StockChestTransferTimingReference.allocation() - allocated;
        }
        return new Sample(elapsed, bytes, checksum);
    }
    static void measure(String mode) throws Exception {
        Fixture[] fixtures = new Fixture[POOL]; for (int i = 0; i < POOL; i++) fixtures[i] = new Fixture();
        long began = System.nanoTime(), quiet = began, compiler = StockChestTransferTimingReference.compilation(); int rounds = 0;
        do { sink ^= run(fixtures).checksum(); rounds++; Thread.sleep(25); long next = StockChestTransferTimingReference.compilation(); if (next != compiler) quiet = System.nanoTime(); compiler = next;
            if (rounds >= 5 && System.nanoTime() - began >= 1_000_000_000L && System.nanoTime() - quiet >= 250_000_000L) break;
        } while (System.nanoTime() - began < 12_000_000_000L);
        rows.add("# warmup " + mode + " rounds=" + rounds + " ns=" + (System.nanoTime() - began));
        for (int sample = 0; sample < 5; sample++) {
            // Fresh furnaces per sample: the checksum must not depend on the warmup round count.
            for (int i = 0; i < POOL; i++) fixtures[i] = new Fixture();
            compiler = StockChestTransferTimingReference.compilation(); long gc = StockChestTransferTimingReference.collections(); var result = run(fixtures); sink ^= result.checksum();
            rows.add(mode + "," + sample + "," + (POOL * BATCHES) + "," + result.elapsed() + "," + result.checksum() + "," + (StockChestTransferTimingReference.compilation() - compiler) + "," + (StockChestTransferTimingReference.collections() - gc) + "," + result.bytes());
        }
    }
    public static void main(String[] args) throws Exception {
        long boot = System.nanoTime(); net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, ResourceLocation.withDefaultNamespace("timing_iron"));
        var objenesis = new ObjenesisStd();
        var holder = Optional.of(new RecipeHolder<>(key, new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE), new ItemStack(Items.IRON_INGOT), 0.7F, 7)));
        Recipes.HOLDER = holder;
        Level.RECIPES = objenesis.newInstance(Recipes.class);
        // Real FuelValues holding only coal (its constructor is package-private).
        FuelValues fuel = objenesis.newInstance(FuelValues.class);
        var values = new it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap<net.minecraft.world.item.Item>(); values.put(Items.COAL, 1600);
        var field = FuelValues.class.getDeclaredField("values"); field.setAccessible(true); field.set(fuel, values);
        Level.FUEL = fuel;
        level = objenesis.newInstance(Level.class);
        rows.add("# boot_ns=" + (System.nanoTime() - boot) + " native_allocated_bytes=not_measured");
        rows.add("workload,sample,operations,elapsed_ns,checksum,compiler_ms,gc_count,allocated_bytes");
        measure("furnace_cooking");
        Files.write(Path.of(args[0]), rows); System.out.println("FURNACE_TIMING sink=" + sink + " pid=" + ManagementFactory.getRuntimeMXBean().getPid());
    }
}
