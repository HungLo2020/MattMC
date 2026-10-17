import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.util.ProblemReporter;

/** Early matched cost probe. Uses the exact stock chest and consumes complete NBT.
 * This is not a final performance campaign or proof of direct retained serialization.
 */
public final class ItemStateTimingReference {
    static final int OPERATIONS = 200_000;
    static final ItemStack[] stacks = new ItemStack[64];
    static final List<String> rows = new ArrayList<>();
    static HolderLookup.Provider registries;
    static volatile long sink;
    static long compilation() { var bean = ManagementFactory.getCompilationMXBean(); return bean.isCompilationTimeMonitoringSupported() ? bean.getTotalCompilationTime() : -1; }
    static long collections() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionCount())).sum(); }
    static long countRead() { long sum = 0; for (int i = 0; i < OPERATIONS; i++) sum += stacks[i & 63].getCount(); return sum; }
    static long countMutation() {
        long sum = 0;
        for (int i = 0; i < OPERATIONS; i++) { var stack = stacks[i & 63]; stack.setCount(((i >>> 6) & 31) + 1); sum += stack.getCount(); }
        return sum;
    }
    static long componentRead() { long sum = 0; for (int i = 0; i < OPERATIONS; i++) sum += stacks[i & 63].getOrDefault(DataComponents.REPAIR_COST, 0); return sum; }
    static long componentMutation() {
        long sum = 0;
        for (int i = 0; i < OPERATIONS; i++) { Integer previous = stacks[i & 63].set(DataComponents.REPAIR_COST, (i >>> 6) & 7); sum += previous == null ? -1 : previous; }
        return sum;
    }
    static ChestBlockEntity chest() { return new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); }
    static long chestRoundTrip() {
        long sum = 0;
        for (int batch = 0; batch < 8; batch++) {
            var first = chest(); var second = chest();
            for (int slot = 0; slot < 27; slot++) {
                var stack = new ItemStack(Items.STICK, slot + 1); stack.set(DataComponents.REPAIR_COST, slot & 7); first.setItem(slot, stack);
            }
            // Keep the alias live through mutation and list transfer.
            var shared = first.getItem(0); first.setItem(1, shared); shared.setCount(9);
            ChestBlockEntity.swapContents(first, second);
            var before = second.saveWithFullMetadata(registries); var loaded = chest();
            loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, before));
            var after = loaded.saveWithFullMetadata(registries);
            if (!before.equals(after)) throw new AssertionError("complete chest save/load output changed");
            sum += before.hashCode(); sum += after.hashCode();
            for (int slot = 0; slot < 27; slot++) sum += loaded.getItem(slot).getCount();
        }
        return sum;
    }
    static void measure(String name, int operations, LongSupplier work) throws InterruptedException {
        // A zero compiler delta in one short sample can precede queued C2 work.
        long began = System.nanoTime(), quietSince = began, previousCompiler = compilation(); int warmups = 0;
        while (true) {
            sink ^= work.getAsLong(); warmups++; Thread.sleep(25);
            long now = System.nanoTime(), currentCompiler = compilation();
            if (currentCompiler != previousCompiler) quietSince = now;
            previousCompiler = currentCompiler;
            if (warmups >= 5 && now - began >= 1_000_000_000L && now - quietSince >= 250_000_000L) break;
            if (now - began >= 12_000_000_000L) break;
        }
        // Adaptive warmup may perform different counts; restore identical measured input.
        for (int i = 0; i < stacks.length; i++) { stacks[i].setCount(i + 1); stacks[i].set(DataComponents.REPAIR_COST, i & 7); }
        rows.add("# warmup " + name + " rounds=" + warmups + " ns=" + (System.nanoTime() - began));
        for (int sample = 0; sample < 5; sample++) {
            long compiler = compilation(), gc = collections(), start = System.nanoTime();
            long checksum = work.getAsLong(); long elapsed = System.nanoTime() - start; sink ^= checksum;
            rows.add(name + "," + sample + "," + operations + "," + elapsed + "," + checksum + "," + (compilation() - compiler) + "," + (collections() - gc));
        }
    }
    public static void main(String[] args) throws Exception {
        long boot = System.nanoTime(); net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        registries = net.minecraft.server.RegistryLayer.createRegistryAccess().compositeAccess();
        for (int i = 0; i < stacks.length; i++) { stacks[i] = new ItemStack(Items.STICK, i + 1); stacks[i].set(DataComponents.REPAIR_COST, i & 7); }
        boolean stackNative;
        try {
            var field = ItemStack.class.getDeclaredField("state"); field.setAccessible(true); var owner = field.get(stacks[0]).getClass();
            stackNative = owner.getName().equals("net.minecraft.world.item.NativeItemState$Stack")
                || java.util.Arrays.stream(owner.getInterfaces()).anyMatch(type -> type.getName().equals("net.minecraft.world.item.NativeItemState$Stack"));
        }
        catch (NoSuchFieldException absent) { stackNative = false; }
        var getItems = ChestBlockEntity.class.getDeclaredMethod("getItems"); getItems.setAccessible(true);
        boolean slotsNative = getItems.invoke(chest()).getClass().getName().equals("net.minecraft.world.item.NativeItemSlots");
        if (Boolean.parseBoolean(args[1]) && !(stackNative && slotsNative)) throw new AssertionError("native owner admission absent");
        rows.add("# boot_ns=" + (System.nanoTime() - boot) + " stack_native=" + stackNative + " slots_native=" + slotsNative + " direct_output=not_checked");
        rows.add("workload,sample,operations,elapsed_ns,checksum,compiler_ms,gc_count");
        measure("count_read", OPERATIONS, ItemStateTimingReference::countRead);
        measure("count_mutation_read", OPERATIONS, ItemStateTimingReference::countMutation);
        measure("component_read", OPERATIONS, ItemStateTimingReference::componentRead);
        measure("component_mutation", OPERATIONS, ItemStateTimingReference::componentMutation);
        measure("stock_chest_create_mutate_swap_save_load_save_consume", 8, ItemStateTimingReference::chestRoundTrip);
        Files.write(Path.of(args[0]), rows); System.out.println("ITEM_STATE_EARLY_TIMING sink=" + sink);
    }
}
