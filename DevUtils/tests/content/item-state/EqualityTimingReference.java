import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * S0034 component-equality workload: ItemStack.matches over pools of stack pairs.
 * Natively decidable shapes (a damaged copy, independently damaged equal swords,
 * default helmets), the genuine Java fallback (equal custom names held by
 * distinct Component objects), and one equipment-tick shape (six mixed slot
 * pairs, as LivingEntity.collectEquipmentChanges compares last and live stacks).
 * copy_churn times ItemStack.copy of a damaged sword whose result is dropped at
 * once, so owner creation runs alongside Cleaner retirement.
 * Timed: the matches (or copy) calls only; pools are built once per mode outside the timer.
 * Arguments: output CSV, then a side flag (unused; identical code on every side).
 */
public final class EqualityTimingReference {
    static final int POOL = 64, BATCHES = 256;
    static volatile long sink;
    static final List<String> rows = new ArrayList<>();
    static ItemStack sword(int damage) { ItemStack stack = new ItemStack(Items.IRON_SWORD); stack.setDamageValue(damage); return stack; }
    static ItemStack named(String name) { ItemStack stack = new ItemStack(Items.STICK, 7); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return stack; }
    /** Pairs (left[i], right[i]) for one mode. */
    static ItemStack[][] pool(String mode) {
        ItemStack[] left = new ItemStack[POOL], right = new ItemStack[POOL];
        for (int i = 0; i < POOL; i++) {
            switch (mode) {
                case "native_damage_copy" -> { left[i] = sword(3); right[i] = left[i].copy(); }
                case "native_damage_equal" -> { left[i] = sword(3); right[i] = sword(3); }
                case "native_default" -> { left[i] = new ItemStack(Items.DIAMOND_HELMET); right[i] = new ItemStack(Items.DIAMOND_HELMET); }
                case "fallback_custom_name" -> { left[i] = named("a"); right[i] = named("a"); }
                default -> { // equipment_tick: six slots per entity, mixed like an armored mob.
                    switch (i % 6) {
                        case 0 -> { left[i] = sword(3); right[i] = left[i].copy(); }
                        case 1 -> { left[i] = ItemStack.EMPTY; right[i] = ItemStack.EMPTY; }
                        default -> { left[i] = new ItemStack(Items.IRON_HELMET); right[i] = left[i].copy(); }
                    }
                }
            }
        }
        return new ItemStack[][] {left, right};
    }
    record Sample(long elapsed, long bytes, long checksum) {}
    static boolean copyChurn;
    static Sample run(ItemStack[][] pairs) {
        long elapsed = 0, bytes = 0, checksum = 0;
        for (int batch = 0; batch < BATCHES; batch++) {
            long allocated = StockChestTransferTimingReference.allocation(), start = System.nanoTime(); int equal = 0;
            if (copyChurn) { for (int i = 0; i < POOL; i++) equal += pairs[0][i].copy().getCount(); }
            else for (int i = 0; i < POOL; i++) if (ItemStack.matches(pairs[0][i], pairs[1][i])) equal++;
            elapsed += System.nanoTime() - start; bytes += StockChestTransferTimingReference.allocation() - allocated;
            checksum = checksum * 31 + equal;
        }
        return new Sample(elapsed, bytes, checksum);
    }
    static void measure(String mode) throws Exception {
        copyChurn = mode.equals("copy_churn"); ItemStack[][] pairs = pool(copyChurn ? "native_damage_equal" : mode);
        long began = System.nanoTime(), quiet = began, compiler = StockChestTransferTimingReference.compilation(); int rounds = 0;
        do { sink ^= run(pairs).checksum(); rounds++; Thread.sleep(25); long next = StockChestTransferTimingReference.compilation(); if (next != compiler) quiet = System.nanoTime(); compiler = next;
            if (rounds >= 5 && System.nanoTime() - began >= 1_000_000_000L && System.nanoTime() - quiet >= 250_000_000L) break;
        } while (System.nanoTime() - began < 12_000_000_000L);
        rows.add("# warmup " + mode + " rounds=" + rounds + " ns=" + (System.nanoTime() - began));
        for (int sample = 0; sample < 5; sample++) {
            compiler = StockChestTransferTimingReference.compilation(); long gc = StockChestTransferTimingReference.collections(); var result = run(pairs); sink ^= result.checksum();
            rows.add(mode + "," + sample + "," + (POOL * BATCHES) + "," + result.elapsed() + "," + result.checksum() + "," + (StockChestTransferTimingReference.compilation() - compiler) + "," + (StockChestTransferTimingReference.collections() - gc) + "," + result.bytes());
        }
        // Untimed process sample after a forced collection: retention would show as growth across modes.
        System.gc(); Thread.sleep(50);
        long rss = Files.readAllLines(Path.of("/proc/self/status")).stream().filter(line -> line.startsWith("VmRSS:")).mapToLong(line -> Long.parseLong(line.replaceAll("[^0-9]", "")) * 1024).findFirst().orElse(-1);
        rows.add("# rss " + mode + " bytes=" + rss);
    }
    public static void main(String[] args) throws Exception {
        long boot = System.nanoTime(); net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        rows.add("# boot_ns=" + (System.nanoTime() - boot) + " pools_outside_timer native_allocated_bytes=not_measured");
        rows.add("workload,sample,operations,elapsed_ns,checksum,compiler_ms,gc_count,allocated_bytes");
        for (String mode : List.of("native_damage_copy", "native_damage_equal", "native_default", "fallback_custom_name", "equipment_tick", "copy_churn")) measure(mode);
        Files.write(Path.of(args[0]), rows); System.out.println("EQUALITY_TIMING sink=" + sink + " pid=" + ManagementFactory.getRuntimeMXBean().getPid());
    }
}
