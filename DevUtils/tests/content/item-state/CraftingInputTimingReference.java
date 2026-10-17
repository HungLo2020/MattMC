import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import static org.mockito.Mockito.*;

/**
 * S0034 crafting consumer: the positioned recipe input that CraftingMenu builds on
 * every grid change and ResultSlot builds on every take. Timed: one complete
 * asPositionedCraftInput on an exact grid, including CraftingInput construction and
 * its stacked-contents accounting. Shapes: two planks in one 3x3 column (cropped,
 * sticks), a full 3x3 grid, and one item in the player's 2x2 grid. Identical
 * stub-only mocked menu on every side. Arguments: output CSV, then a side flag
 * (unused; kept for the shared driver).
 */
public final class CraftingInputTimingReference {
    static final int POOL = 16, BATCHES = 256;
    static volatile long sink;
    static final List<String> rows = new ArrayList<>();
    static final class Fixture {
        final TransientCraftingContainer grid;
        Fixture(String mode, AbstractContainerMenu menu) {
            switch (mode) {
                case "craft_input_3x3_column" -> { grid = new TransientCraftingContainer(menu, 3, 3); grid.setItem(1, new ItemStack(Items.OAK_PLANKS, 12)); grid.setItem(4, new ItemStack(Items.OAK_PLANKS, 7)); }
                case "craft_input_3x3_full" -> { grid = new TransientCraftingContainer(menu, 3, 3); for (int i = 0; i < 9; i++) grid.setItem(i, new ItemStack(i == 4 ? Items.STICK : Items.COBBLESTONE, 1 + i)); }
                default -> { grid = new TransientCraftingContainer(menu, 2, 2); grid.setItem(3, new ItemStack(Items.OAK_LOG, 5)); }
            }
        }
        long run() {
            CraftingInput.Positioned positioned = grid.asPositionedCraftInput(); CraftingInput input = positioned.input();
            return positioned.left() + 3L * positioned.top() + 7L * input.width() + 11L * input.height() + 13L * input.ingredientCount() + input.getItem(input.size() - 1).getCount();
        }
    }
    record Sample(long elapsed, long bytes, long checksum) {}
    static Sample run(Fixture[] fixtures) {
        long elapsed = 0, bytes = 0, checksum = 0;
        for (int batch = 0; batch < BATCHES; batch++) {
            long allocated = StockChestTransferTimingReference.allocation(), start = System.nanoTime();
            for (var fixture : fixtures) checksum += fixture.run();
            elapsed += System.nanoTime() - start; bytes += StockChestTransferTimingReference.allocation() - allocated;
        }
        return new Sample(elapsed, bytes, checksum);
    }
    static void measure(String mode, AbstractContainerMenu menu) throws Exception {
        Fixture[] fixtures = new Fixture[POOL]; for (int i = 0; i < POOL; i++) fixtures[i] = new Fixture(mode, menu);
        long began = System.nanoTime(), quiet = began, compiler = StockChestTransferTimingReference.compilation(); int rounds = 0;
        do { sink ^= run(fixtures).checksum(); rounds++; Thread.sleep(25); long next = StockChestTransferTimingReference.compilation(); if (next != compiler) quiet = System.nanoTime(); compiler = next;
            if (rounds >= 5 && System.nanoTime() - began >= 1_000_000_000L && System.nanoTime() - quiet >= 250_000_000L) break;
        } while (System.nanoTime() - began < 12_000_000_000L);
        rows.add("# warmup " + mode + " rounds=" + rounds + " ns=" + (System.nanoTime() - began));
        for (int sample = 0; sample < 5; sample++) {
            compiler = StockChestTransferTimingReference.compilation(); long gc = StockChestTransferTimingReference.collections(); var result = run(fixtures); sink ^= result.checksum();
            rows.add(mode + "," + sample + "," + (POOL * BATCHES) + "," + result.elapsed() + "," + result.checksum() + "," + (StockChestTransferTimingReference.compilation() - compiler) + "," + (StockChestTransferTimingReference.collections() - gc) + "," + result.bytes());
        }
    }
    public static void main(String[] args) throws Exception {
        long boot = System.nanoTime(); net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        AbstractContainerMenu menu = mock(AbstractContainerMenu.class);
        rows.add("# boot_ns=" + (System.nanoTime() - boot) + " native_allocated_bytes=not_measured");
        rows.add("workload,sample,operations,elapsed_ns,checksum,compiler_ms,gc_count,allocated_bytes");
        for (String mode : List.of("craft_input_3x3_column", "craft_input_3x3_full", "craft_input_2x2_one")) measure(mode, menu);
        Files.write(Path.of(args[0]), rows); System.out.println("CRAFTING_INPUT_TIMING sink=" + sink + " pid=" + ManagementFactory.getRuntimeMXBean().getPid());
    }
}
