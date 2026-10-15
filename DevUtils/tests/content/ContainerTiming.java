import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Container save cost in one fresh JVM, compiled unchanged against a
 * reference and Current: `ContainerHelper.saveAllItems` of 400 chests (27
 * slots; about two thirds filled, every fourth stack named with lore and
 * damage) into a registry-aware `TagValueOutput` with a collecting reporter,
 * as chunk saves do. Reports the first (cold) pass and the warm per-pass
 * average in milliseconds on one CONTAINER_TIMING line.
 */
public final class ContainerTiming {
    public static void main(String[] args) throws Exception {
        int rounds = args.length > 0 ? Integer.parseInt(args[0]) : 50;
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var registries = net.minecraft.server.RegistryLayer.createRegistryAccess().compositeAccess();
        var items = new ArrayList<net.minecraft.world.item.Item>();
        BuiltInRegistries.ITEM.forEach(item -> { if (item != net.minecraft.world.item.Items.AIR) items.add(item); });
        List<NonNullList<ItemStack>> chests = new ArrayList<>();
        int n = 0;
        for (int c = 0; c < 400; c++) {
            NonNullList<ItemStack> chest = NonNullList.withSize(27, ItemStack.EMPTY);
            for (int s = 0; s < 27; s++) {
                if ((c + s) % 3 == 0) continue;
                ItemStack stack = new ItemStack(items.get((c * 31 + s * 7) % items.size()), 1 + s % 16);
                if (n++ % 4 == 0) {
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal("Item " + n));
                    stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("line"))));
                    if (stack.isDamageableItem()) stack.set(DataComponents.DAMAGE, 3);
                }
                chest.set(s, stack);
            }
            chests.add(chest);
        }
        long a = System.nanoTime();
        int size = save(registries, chests);
        long b = System.nanoTime();
        // The same chests loaded back from their saves (decode, then save again).
        List<NonNullList<ItemStack>> loaded = new ArrayList<>();
        for (NonNullList<ItemStack> chest : chests) {
            var output = TagValueOutput.createWithContext(new ProblemReporter.Collector(), registries);
            ContainerHelper.saveAllItems(output, chest);
            var input = net.minecraft.world.level.storage.TagValueInput.create(new ProblemReporter.Collector(), registries, output.buildResult());
            NonNullList<ItemStack> back = NonNullList.withSize(27, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(input, back);
            loaded.add(back);
        }
        // The same items and counts without component patches.
        List<NonNullList<ItemStack>> plain = new ArrayList<>();
        for (NonNullList<ItemStack> chest : chests) {
            NonNullList<ItemStack> bare = NonNullList.withSize(27, ItemStack.EMPTY);
            for (int s = 0; s < 27; s++) {
                if (!chest.get(s).isEmpty()) bare.set(s, new ItemStack(chest.get(s).getItem(), chest.get(s).getCount()));
            }
            plain.add(bare);
        }
        long warm = 0, warmLoaded = 0, warmPlain = 0;
        for (int r = 0; r < rounds; r++) {
            long t0 = System.nanoTime();
            size += save(registries, chests);
            long t1 = System.nanoTime();
            size += save(registries, loaded);
            long t2 = System.nanoTime();
            size += save(registries, plain);
            long t3 = System.nanoTime();
            warm += t1 - t0;
            warmLoaded += t2 - t1;
            warmPlain += t3 - t2;
        }
        System.out.printf("CONTAINER_TIMING chests=%d save_cold=%.2f save=%.3f save_loaded=%.3f save_plain=%.3f check=%d%n", chests.size(), (b - a) / 1e6,
            warm / 1e6 / rounds, warmLoaded / 1e6 / rounds, warmPlain / 1e6 / rounds, size);
    }

    static int save(net.minecraft.core.HolderLookup.Provider registries, List<NonNullList<ItemStack>> chests) {
        int size = 0;
        for (NonNullList<ItemStack> chest : chests) {
            var reporter = new ProblemReporter.Collector();
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            ContainerHelper.saveAllItems(output, chest);
            size += output.buildResult().size();
        }
        return size;
    }
}
