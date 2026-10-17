import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import static org.mockito.Mockito.*;

/**
 * S0034 per-tick player workload: the always-open InventoryMenu of a player (main
 * rows, armor, offhand, 2x2 crafting grid and result) broadcasting to a read-only
 * listener, as ServerPlayer does every tick. Timed: one broadcastChanges
 * (player_idle), or one main-row change plus the broadcast that ships it
 * (player_one_change). Identical stub-only mocked Player on every side. The
 * listener is a java.lang.reflect.Proxy that also carries the S34 read-only marker
 * where that type exists (Current side argument true).
 * Arguments: output CSV, then true on the Current side.
 */
public final class PlayerInventoryTimingReference {
    static final int POOL = 16, BATCHES = 64;
    static volatile long sink;
    static final List<String> rows = new ArrayList<>();
    static Class<?> MARKER;
    static final class Fixture {
        final String mode; final Inventory inventory; final InventoryMenu menu; long sends; int tick;
        Fixture(String mode) {
            this.mode = mode;
            Player player = mock(Player.class); EntityEquipment equipment = new EntityEquipment(); inventory = new Inventory(player, equipment); doReturn(inventory).when(player).getInventory();
            for (int i = 0; i < 36; i += 3) inventory.setItem(i, new ItemStack(Items.STICK, 1 + i));
            equipment.set(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET)); equipment.set(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE)); equipment.set(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            menu = new InventoryMenu(inventory, true, player);
            List<Class<?>> types = new ArrayList<>(List.of(ContainerListener.class)); if (MARKER != null) types.add(MARKER);
            Object listener = java.lang.reflect.Proxy.newProxyInstance(PlayerInventoryTimingReference.class.getClassLoader(), types.toArray(Class<?>[]::new), (proxy, method, args) -> {
                switch (method.getName()) {
                    case "slotChanged": this.sends += 31L * ((ItemStack)args[2]).getCount() + (Integer)args[1]; return null;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    case "toString": return "observer";
                    default: return null;
                }
            });
            menu.addSlotListener((ContainerListener)listener); menu.broadcastChanges();
        }
        long run() {
            long before = sends;
            if (mode.equals("player_one_change")) inventory.getItem(0).setCount(1 + (++tick & 31));
            menu.broadcastChanges(); return sends - before;
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
        rows.add("# boot_ns=" + (System.nanoTime() - boot) + " broadcast_marker=" + (MARKER != null) + " native_allocated_bytes=not_measured");
        rows.add("workload,sample,operations,elapsed_ns,checksum,compiler_ms,gc_count,allocated_bytes");
        for (String mode : List.of("player_idle", "player_one_change")) measure(mode);
        Files.write(Path.of(args[0]), rows); System.out.println("PLAYER_INVENTORY_TIMING sink=" + sink + " pid=" + ManagementFactory.getRuntimeMXBean().getPid());
    }
}
