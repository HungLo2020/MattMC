import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Development attribution for S0030 (copied-stack production/publication):
 * the primitives inside a complete forward-empty quick move, each timed in
 * long interleaved loops in one warmed JVM. Not acceptance evidence; the
 * complete-operation costs come from MeasureItemState. Argument: rounds.
 */
public final class CopyPublicationPrimitiveTiming {
    static volatile long sink;
    interface Op { long run(int i); }

    public static void main(String[] args) {
        int rounds = args.length > 0 ? Integer.parseInt(args[0]) : 9;
        String only = args.length > 1 ? args[1] : null;
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        final int n = 32;
        ItemStack stick = new ItemStack(Items.STICK, 11);
        ChestBlockEntity[] chests = new ChestBlockEntity[n];
        Inventory[] inventories = new Inventory[n];
        ChestMenu[] menus = new ChestMenu[n];
        ItemStack[] sources = new ItemStack[n];
        ItemStack[] copies = new ItemStack[n];
        for (int i = 0; i < n; i++) {
            chests[i] = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            inventories[i] = new Inventory(null, new EntityEquipment());
            chests[i].setRemoved(); menus[i] = ChestMenu.threeRows(1, inventories[i], chests[i]); chests[i].clearRemoved();
            sources[i] = new ItemStack(Items.STICK, 11);
            copies[i] = stick.copy();
        }
        var list = new LinkedHashMap<String, Op>();
        list.put("copy_pure", i -> stick.copy().getCount());
        ItemStack[] keep = new ItemStack[200_000];
        list.put("copy_retained", i -> { ItemStack c = stick.copy(); keep[i] = c; return c.getCount(); });
        list.put("copy_with_count", i -> stick.copyForNativeTransferWithCount(5).getCount());
        list.put("slot_publish_pair", i -> {
            var chest = chests[i % n];
            chest.setItem(0, copies[i % n]); chest.setItem(0, ItemStack.EMPTY); return 1;
        });
        list.put("quick_move_empty", i -> {
            int k = i % n; sources[k].setCount(11); inventories[k].setItem(9, sources[k]); chests[k].setItem(0, ItemStack.EMPTY);
            return menus[k].quickMoveStack(null, 27).getCount();
        });
        list.put("reset_only", i -> {
            int k = i % n; sources[k].setCount(11); inventories[k].setItem(9, sources[k]); chests[k].setItem(0, ItemStack.EMPTY); return 1;
        });
        int ops = 200_000;
        if (only != null) list.keySet().retainAll(Set.of(only.split(",")));
        var results = new LinkedHashMap<String, List<Double>>();
        for (var name : list.keySet()) results.put(name, new ArrayList<>());
        for (int round = 0; round < rounds + 3; round++) {
            for (var e : list.entrySet()) {
                Op op = e.getValue(); long s = 0; long start = System.nanoTime();
                for (int i = 0; i < ops; i++) s += op.run(i);
                long elapsed = System.nanoTime() - start; sink ^= s;
                if (round >= 3) results.get(e.getKey()).add(elapsed / (double)ops);
            }
        }
        for (var e : results.entrySet()) {
            var v = e.getValue(); Collections.sort(v);
            System.out.printf("PRIMITIVE %s median=%.1f min=%.1f max=%.1f ns/op%n", e.getKey(), v.get(v.size() / 2), v.get(0), v.get(v.size() - 1));
        }
    }
}
