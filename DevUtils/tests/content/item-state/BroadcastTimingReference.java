import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.HashedPatchMap;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * S0034 supplemental complete per-tick workload: an open stock chest menu with a
 * recording synchronizer/listener (read-only; a java.lang.reflect.Proxy that also
 * carries the S34 read-only marker where that type exists, identically on every
 * side). Timed: one broadcastChanges (tick_idle), or a quick move followed by the
 * broadcastChanges that ships it (tick_quick_move_*). The double_* modes use a
 * six-row menu over a CompoundContainer of two chests (double_reverse_one moves
 * from the second half into an empty inventory). Reset and a settling
 * broadcast run outside the timer. The original 25 workloads are unchanged.
 * Arguments: output CSV, then true on the Current side.
 */
public final class BroadcastTimingReference {
    static final int POOL = 32, BATCHES = 64;
    static volatile long sink;
    static final List<String> rows = new ArrayList<>();
    /** Probed only on the Current side (mode argument true), so no other side loads a class it lacks. */
    static Class<?> MARKER;
    static final class Fixture {
        final String mode;
        final Inventory inventory = new Inventory(null, new EntityEquipment());
        final ChestBlockEntity chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        /** Second half for the double_* modes (a six-row menu over a CompoundContainer). */
        final ChestBlockEntity other = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        final ChestMenu menu;
        final ItemStack source = new ItemStack(Items.STICK, 11);
        long sends;
        Fixture(String mode) {
            this.mode = mode;
            chest.setRemoved(); other.setRemoved();
            menu = mode.startsWith("double") ? ChestMenu.sixRows(1, inventory, new net.minecraft.world.CompoundContainer(chest, other)) : ChestMenu.threeRows(1, inventory, chest);
            chest.clearRemoved(); other.clearRemoved();
            List<Class<?>> types = new ArrayList<>(List.of(ContainerSynchronizer.class, ContainerListener.class));
            if (MARKER != null) types.add(MARKER);
            HashedPatchMap.HashGenerator hasher = component -> component.hashCode();
            Object observer = java.lang.reflect.Proxy.newProxyInstance(BroadcastTimingReference.class.getClassLoader(), types.toArray(Class<?>[]::new), (proxy, method, args) -> {
                switch (method.getName()) {
                    case "createSlot": return new RemoteSlot.Synchronized(hasher);
                    case "sendSlotChange": case "slotChanged": this.sends += 31L * ((ItemStack)args[2]).getCount() + (Integer)args[1]; return null;
                    case "sendInitialData": this.sends++; return null;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    case "toString": return "observer";
                    default: return null;
                }
            });
            menu.setSynchronizer((ContainerSynchronizer)observer); menu.addSlotListener((ContainerListener)observer);
        }
        void reset() {
            source.setCount(11); inventory.setItem(9, source);
            // The double_* modes start each run from empty halves, so overflow from earlier runs
            // cannot make the workload depend on how many warmup rounds ran.
            if (mode.startsWith("double")) for (int i = 0; i < 27; i++) { chest.setItem(i, ItemStack.EMPTY); other.setItem(i, ItemStack.EMPTY); }
            // double_reverse_one: the source sits in the second half's row 4 (menu 31), the inventory is empty.
            if (mode.equals("double_reverse_one")) { inventory.setItem(9, ItemStack.EMPTY); other.setItem(4, source); }
            chest.setItem(0, mode.endsWith("one") ? new ItemStack(Items.STICK, 60) : ItemStack.EMPTY);
            menu.broadcastChanges();
        }
        long run() {
            long before = sends;
            if (!mode.endsWith("idle")) menu.quickMoveStack(null, mode.equals("double_reverse_one") ? 31 : mode.startsWith("double") ? 54 : 27);
            menu.broadcastChanges(); return sends - before;
        }
    }
    record Sample(long elapsed, long bytes, long checksum) {}
    static Sample run(Fixture[] fixtures) {
        long elapsed = 0, bytes = 0, checksum = 0;
        for (int batch = 0; batch < BATCHES; batch++) {
            for (var fixture : fixtures) fixture.reset();
            long allocated = StockChestTransferTimingReference.allocation(), start = System.nanoTime();
            for (var fixture : fixtures) checksum += fixture.run();
            elapsed += System.nanoTime() - start; bytes += StockChestTransferTimingReference.allocation() - allocated;
            for (var fixture : fixtures) checksum = checksum * 31 + fixture.source.getCount() + fixture.inventory.getItem(9).getCount();
        }
        return new Sample(elapsed, bytes, checksum);
    }
    static void measure(String mode) throws Exception {
        Fixture[] fixtures = new Fixture[POOL]; for (int i = 0; i < POOL; i++) fixtures[i] = new Fixture(mode);
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
        if (Boolean.parseBoolean(args[1])) MARKER = Class.forName("net.minecraft.world.inventory.AbstractContainerMenu$ReadOnlyBroadcastObserver");
        long boot = System.nanoTime(); net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        rows.add("# boot_ns=" + (System.nanoTime() - boot) + " broadcast_marker=" + (MARKER != null) + " reset_and_settling_broadcast=outside_timer native_allocated_bytes=not_measured");
        rows.add("workload,sample,operations,elapsed_ns,checksum,compiler_ms,gc_count,allocated_bytes");
        for (String mode : List.of("tick_idle", "tick_quick_move_empty", "tick_quick_move_one", "double_idle", "double_quick_move_one", "double_reverse_one")) measure(mode);
        Files.write(Path.of(args[0]), rows); System.out.println("BROADCAST_TIMING sink=" + sink + " pid=" + ManagementFactory.getRuntimeMXBean().getPid());
    }
}
