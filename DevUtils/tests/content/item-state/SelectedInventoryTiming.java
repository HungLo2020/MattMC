import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Narrow follow-up timing for selected inventory workloads only, with the
 * unchanged InventoryTimingReference/StockChestTransferTimingReference
 * measure methods (same warmup, five samples, reset and checks outside the
 * timer). Arguments: output CSV, require-native flag, then workloads
 * (quick_move_<shape> or an InventoryTimingReference mode).
 */
public final class SelectedInventoryTiming {
    public static void main(String[] args) throws Exception {
        long boot = System.nanoTime();
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        StockChestTransferTimingReference.registries = net.minecraft.server.RegistryLayer.createRegistryAccess().compositeAccess();
        InventoryTimingReference.requireNative = Boolean.parseBoolean(args[1]);
        var rows = StockChestTransferTimingReference.rows;
        rows.add("# boot_ns=" + (System.nanoTime() - boot) + " selected=" + String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        rows.add("workload,sample,operations,elapsed_ns,checksum,compiler_ms,gc_count,allocated_bytes");
        for (String workload : Arrays.copyOfRange(args, 2, args.length)) {
            if (workload.startsWith("quick_move_")) StockChestTransferTimingReference.measure(workload.substring("quick_move_".length()));
            else InventoryTimingReference.measure(workload);
        }
        rows.addAll(InventoryTimingReference.rows);
        Files.write(Path.of(args[0]), rows);
    }
}
