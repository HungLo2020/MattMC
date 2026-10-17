import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import net.minecraft.world.item.ItemStack;

/**
 * S0030 work/dispatch diagnostic, not timing evidence. Counts native-to-Java
 * item callbacks (by method and action code) and native transfer/prepared-copy
 * counters per complete operation for selected inventory workloads, using the
 * unchanged InventoryTimingReference/StockChestTransferTimingReference fixtures.
 * Reset runs outside counting. Arguments: operations per workload, then
 * workloads (quick_move_<shape> or an InventoryTimingReference mode).
 */
public final class PreparedPathWorkCounts {
    public static final Map<String, LongAdder> CALLS = new ConcurrentHashMap<>();
    public static volatile boolean counting;

    public static final class Count {
        @Advice.OnMethodEnter
        static void enter(@Advice.Origin("#m") String method, @Advice.Argument(0) long action) {
            if (counting) hit(method + ":" + action);
        }
    }

    public static void hit(String key) {
        LongAdder adder = CALLS.get(key);
        if (adder == null) { CALLS.putIfAbsent(key, new LongAdder()); adder = CALLS.get(key); }
        adder.increment();
    }

    public static void main(String[] args) throws Throwable {
        int operations = Integer.parseInt(args[0]);
        new AgentBuilder.Default().disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .type(ElementMatchers.named("net.minecraft.world.item.NativeItemState"))
            .transform((builder, type, loader, module, domain) -> builder.visit(Advice.to(Count.class)
                .on(ElementMatchers.namedOneOf("menuActionEffect", "inventoryAddEffect", "inventoryVisitEffect", "transferEffect"))))
            .installOn(ByteBuddyAgent.install());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        MethodHandle counter = (MethodHandle) Class.forName("net.minecraft.util.NativeLibraryLoader")
            .getMethod("downcallHandle", String.class, String.class, FunctionDescriptor.class)
            .invoke(null, "mattmc_rust", "mattmc_item_transfer_counter", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        String[] counters = {"transfer_steps", "transfer_native_defaults", "transfer_native_slots", "prepared_copies", "prepared_retired"};
        for (String workload : Arrays.copyOfRange(args, 1, args.length)) {
            boolean stock = workload.startsWith("quick_move_");
            var chestFixture = stock ? new StockChestTransferTimingReference.Fixture(workload.substring("quick_move_".length())) : null;
            var inventoryFixture = stock ? null : new InventoryTimingReference.Fixture(workload);
            long checksum = 0;
            long[] before = new long[counters.length];
            CALLS.clear();
            for (int i = -1; i < operations; i++) {
                if (stock) chestFixture.reset(); else inventoryFixture.reset();
                if (i == 0) for (int c = 0; c < counters.length; c++) before[c] = (long) counter.invokeExact((long) c);
                counting = i >= 0;
                ItemStack returned = stock ? chestFixture.menu.quickMoveStack(null, 27) : inventoryFixture.run();
                counting = false;
                checksum += stock ? chestFixture.consume(returned) : inventoryFixture.consume(returned);
            }
            var line = new StringBuilder("WORK ").append(workload).append(" operations=").append(operations).append(" checksum=").append(checksum);
            for (int c = 0; c < counters.length; c++) {
                long after = (long) counter.invokeExact((long) c);
                line.append(' ').append(counters[c]).append('=').append(after == -2 || before[c] == -2 ? "absent" : String.format("%.3f", (after - before[c]) / (double) operations));
            }
            new TreeMap<>(CALLS).forEach((key, value) -> line.append(' ').append(key).append('=').append(String.format("%.3f", value.sum() / (double) operations)));
            System.out.println(line);
        }
    }
}
