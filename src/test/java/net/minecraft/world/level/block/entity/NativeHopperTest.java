package net.minecraft.world.level.block.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The native stock hopper eject (exact HopperBlockEntity pair, native rows)
 * against the original Java path (anonymous subclasses, plain lists) over the
 * same scenarios: results, contents, cooldowns and the ordered setChanged
 * trace (blockEntityChanged/updateNeighbourForOutputSignal) must match.
 */
class NativeHopperTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    static final BlockPos SOURCE = new BlockPos(0, 64, 0), TARGET = SOURCE.below();

    record Pair(Level level, HopperBlockEntity source, HopperBlockEntity target) {}

    static Pair pair(boolean exact) {
        BlockState down = Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN);
        HopperBlockEntity source = exact ? new HopperBlockEntity(SOURCE, down) : new HopperBlockEntity(SOURCE, down) {};
        HopperBlockEntity target = exact ? new HopperBlockEntity(TARGET, down) : new HopperBlockEntity(TARGET, down) {};
        Level level = mock(Level.class);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(down);
        when(level.getBlockEntity(TARGET)).thenReturn(target);
        when(level.getBlockEntity(SOURCE)).thenReturn(source);
        source.setLevel(level); target.setLevel(level);
        return new Pair(level, source, target);
    }

    static boolean eject(Pair pair) throws Exception {
        var method = HopperBlockEntity.class.getDeclaredMethod("ejectItems", Level.class, BlockPos.class, HopperBlockEntity.class);
        method.setAccessible(true);
        return (boolean)method.invoke(null, pair.level, SOURCE, pair.source);
    }

    static String describe(HopperBlockEntity hopper) throws Exception {
        var cooldown = HopperBlockEntity.class.getDeclaredField("cooldownTime"); cooldown.setAccessible(true);
        var line = new StringBuilder("cooldown=").append(cooldown.getInt(hopper));
        for (int i = 0; i < hopper.getContainerSize(); i++) { ItemStack stack = hopper.getItem(i); line.append(' ').append(stack.getItem()).append('x').append(stack.getCount()); }
        return line.toString();
    }

    static List<String> trace(Level level) {
        var events = new ArrayList<String>();
        for (var invocation : mockingDetails(level).getInvocations()) {
            String name = invocation.getMethod().getName();
            if (name.equals("blockEntityChanged") || name.equals("updateNeighbourForOutputSignal")) events.add(name + " " + invocation.getArgument(0));
        }
        return events;
    }

    static List<String> run(boolean exact, Consumer<Pair> setup, int ejects) throws Exception {
        Pair pair = pair(exact); setup.accept(pair);
        var results = new ArrayList<String>();
        for (int i = 0; i < ejects; i++) results.add("eject " + eject(pair));
        results.add("source " + describe(pair.source)); results.add("target " + describe(pair.target));
        results.addAll(trace(pair.level));
        return results;
    }

    static void scenario(String name, Consumer<Pair> setup, int ejects) throws Exception {
        assertEquals(run(false, setup, ejects), run(true, setup, ejects), name);
    }

    @Test void nativeEjectMatchesTheOriginalHopperPath() throws Exception {
        scenario("empty target", pair -> pair.source.setItem(0, new ItemStack(Items.STICK, 3)), 4);
        scenario("merge", pair -> { pair.source.setItem(0, new ItemStack(Items.STICK, 3)); pair.target.setItem(0, new ItemStack(Items.STICK, 5)); }, 2);
        scenario("full target", pair -> { pair.source.setItem(0, new ItemStack(Items.STICK, 3)); for (int i = 0; i < 5; i++) pair.target.setItem(i, new ItemStack(Items.DIRT, 64)); }, 1);
        scenario("skip to later slot", pair -> {
            pair.source.setItem(0, new ItemStack(Items.DIRT, 2)); pair.source.setItem(1, new ItemStack(Items.STICK, 2));
            for (int i = 0; i < 4; i++) pair.target.setItem(i, new ItemStack(Items.STICK, 64)); pair.target.setItem(4, new ItemStack(Items.DIAMOND, 64));
        }, 2);
        scenario("nearly full merge", pair -> { pair.source.setItem(2, new ItemStack(Items.STICK, 1)); pair.target.setItem(3, new ItemStack(Items.STICK, 63)); }, 2);
    }

    // A bulk read (toArray, as List.copyOf uses) returns every bound row's facade and
    // materializes a pending row exactly as the sequential gets do, once.
    @Test void bulkReadMaterializesAPendingRowBetweenBoundRows() throws Exception {
        Pair pair = pair(true); pair.source.setItem(0, new ItemStack(Items.STICK, 3));
        for (int i = 0; i < 2; i++) pair.target.setItem(i, new ItemStack(Items.DIRT, 64));
        pair.target.setItem(3, new ItemStack(Items.DIAMOND, 5));
        ItemStack[] before = {pair.target.getItem(0), pair.target.getItem(1), null, pair.target.getItem(3), pair.target.getItem(4)};
        assertTrue(eject(pair));
        var rows = (net.minecraft.world.item.NativeItemSlots)pair.target.getItems();
        var pendingForTest = net.minecraft.world.item.NativeItemSlots.class.getDeclaredMethod("pendingForTest"); pendingForTest.setAccessible(true);
        assertEquals(1, (int)pendingForTest.invoke(rows), "the placed copy is pending in row 2");
        Object[] all = rows.toArray();
        assertEquals(0, (int)pendingForTest.invoke(rows), "the bulk read materialized it");
        assertEquals(5, all.length);
        for (int i = 0; i < 5; i++) if (i != 2) assertSame(before[i], all[i], "row " + i);
        assertSame(pair.target.getItem(2), all[2]); assertEquals(Items.STICK, ((ItemStack)all[2]).getItem()); assertEquals(1, ((ItemStack)all[2]).getCount());
        Object[] again = rows.toArray(); var copy = java.util.List.copyOf(rows);
        for (int i = 0; i < 5; i++) { assertSame(all[i], again[i]); assertSame(all[i], copy.get(i)); }
    }

    @Test void nativeEjectLeavesThePlacedCopyPendingUntilRead() throws Exception {
        Pair pair = pair(true); pair.source.setItem(0, new ItemStack(Items.STICK, 3));
        assertTrue(eject(pair));
        var rows = (net.minecraft.world.item.NativeItemSlots)pair.target.getItems();
        var pendingForTest = net.minecraft.world.item.NativeItemSlots.class.getDeclaredMethod("pendingForTest"); pendingForTest.setAccessible(true);
        assertEquals(1, (int)pendingForTest.invoke(rows), "the one-item copy is published without a facade");
        ItemStack placed = pair.target.getItem(0);
        assertEquals(0, (int)pendingForTest.invoke(rows)); assertEquals(1, placed.getCount()); assertSame(placed, pair.target.getItem(0));
        assertEquals(2, pair.source.getItem(0).getCount());
    }

    // Suck: the lower hopper (TARGET) pulls from the upper one (SOURCE) through
    // the public suckInItems; same twin comparison.
    static List<String> runSuck(boolean exact, Consumer<Pair> setup, int sucks) throws Exception {
        Pair pair = pair(exact); setup.accept(pair);
        var results = new ArrayList<String>();
        for (int i = 0; i < sucks; i++) results.add("suck " + HopperBlockEntity.suckInItems(pair.level, pair.target));
        results.add("source " + describe(pair.source)); results.add("target " + describe(pair.target));
        results.addAll(trace(pair.level));
        return results;
    }
    static void suckScenario(String name, Consumer<Pair> setup, int sucks) throws Exception {
        assertEquals(runSuck(false, setup, sucks), runSuck(true, setup, sucks), name);
    }

    @Test void nativeSuckMatchesTheOriginalHopperPath() throws Exception {
        suckScenario("into empty", pair -> pair.source.setItem(1, new ItemStack(Items.STICK, 2)), 3);
        suckScenario("merge", pair -> { pair.source.setItem(0, new ItemStack(Items.STICK, 2)); pair.target.setItem(4, new ItemStack(Items.STICK, 10)); }, 2);
        suckScenario("first slot cannot move (original path)", pair -> {
            pair.source.setItem(0, new ItemStack(Items.DIRT, 1)); pair.source.setItem(1, new ItemStack(Items.STICK, 1));
            for (int i = 0; i < 5; i++) pair.target.setItem(i, new ItemStack(Items.STICK, 64));
        }, 1);
        suckScenario("nothing above", pair -> {}, 1);
    }

    @Test void nativeOccupancyMatchesTheOriginalScans() throws Exception {
        var full = HopperBlockEntity.class.getDeclaredMethod("inventoryFull"); full.setAccessible(true);
        List<Consumer<HopperBlockEntity>> fills = List.of(
            hopper -> {},
            hopper -> hopper.setItem(2, new ItemStack(Items.STICK, 5)),
            hopper -> { for (int i = 0; i < 5; i++) hopper.setItem(i, new ItemStack(Items.STICK, 64)); },
            hopper -> { for (int i = 0; i < 5; i++) hopper.setItem(i, new ItemStack(Items.ENDER_PEARL, i == 3 ? 15 : 16)); },
            hopper -> { for (int i = 0; i < 5; i++) hopper.setItem(i, new ItemStack(Items.DIAMOND_SWORD)); },
            hopper -> { hopper.setItem(0, new ItemStack(Items.STICK, 64)); hopper.getItem(0).setCount(70); for (int i = 1; i < 5; i++) hopper.setItem(i, new ItemStack(Items.STICK, 64)); },
            hopper -> { var zero = new ItemStack(Items.STICK, 1); zero.setCount(0); hopper.setItem(1, zero); });
        for (int round = 0; round < fills.size(); round++) {
            Pair exact = pair(true), original = pair(false);
            fills.get(round).accept(exact.source); fills.get(round).accept(original.source);
            assertEquals(original.source.isEmpty(), exact.source.isEmpty(), "isEmpty " + round);
            assertEquals(full.invoke(original.source), full.invoke(exact.source), "inventoryFull " + round);
        }
    }

    // Collecting an item entity: native rows (exact hopper) vs the original path
    // (subclass), same results, contents, cooldown, entity stack/removal, trace.
    static List<String> runCollect(boolean exact, Consumer<Pair> setup, ItemStack dropped) throws Exception {
        Pair pair = pair(exact); setup.accept(pair);
        // Only getItem/setItem/discard are used; a mock records the original's entity effects.
        var entity = mock(net.minecraft.world.entity.item.ItemEntity.class);
        when(entity.getItem()).thenReturn(dropped.copy());
        var results = new ArrayList<String>();
        results.add("collect " + HopperBlockEntity.addItem(pair.target, entity));
        for (var invocation : mockingDetails(entity).getInvocations()) {
            String name = invocation.getMethod().getName();
            if (name.equals("setItem")) { ItemStack set = invocation.getArgument(0); results.add("entity.setItem " + set.getItem() + "x" + set.getCount() + (set == ItemStack.EMPTY ? " EMPTY" : "")); }
            if (name.equals("discard")) results.add("entity.discard");
        }
        results.add("target " + describe(pair.target));
        results.addAll(trace(pair.level));
        return results;
    }
    static void collectScenario(String name, Consumer<Pair> setup, ItemStack dropped) throws Exception {
        assertEquals(runCollect(false, setup, dropped), runCollect(true, setup, dropped), name);
    }

    @Test void nativeEntityCollectionMatchesTheOriginalHopperPath() throws Exception {
        collectScenario("empty hopper", pair -> {}, new ItemStack(Items.STICK, 20));
        collectScenario("merge then remainder", pair -> { pair.target.setItem(1, new ItemStack(Items.STICK, 60)); pair.target.setItem(3, new ItemStack(Items.DIRT, 64)); }, new ItemStack(Items.STICK, 20));
        collectScenario("full hopper keeps the entity", pair -> { for (int i = 0; i < 5; i++) pair.target.setItem(i, new ItemStack(Items.DIRT, 64)); }, new ItemStack(Items.STICK, 20));
        collectScenario("merges only, partial", pair -> { for (int i = 0; i < 5; i++) pair.target.setItem(i, new ItemStack(Items.STICK, 63)); }, new ItemStack(Items.STICK, 9));
        var overstacked = new ItemStack(Items.ENDER_PEARL, 16); overstacked.setCount(40);
        collectScenario("over-max remainder is limited", pair -> {}, overstacked);
    }

    private static long counter(long index) {
        try {
            var handle = net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_transfer_counter",
                java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_LONG, java.lang.foreign.ValueLayout.JAVA_LONG));
            return (long)handle.invokeExact(index);
        } catch (Throwable failure) { throw new AssertionError(failure); }
    }

    // A hopper chain keeps native contents native: two ejects publish then merge
    // into the lower hopper; comparator reads, occupancy checks and a save leave
    // them pending; the first genuine read builds exactly one canonical facade.
    @Test void hopperChainContentsStayNativeUntilGenuinelyRead() throws Exception {
        Pair pair = pair(true); pair.source.setItem(0, new ItemStack(Items.STICK, 5));
        long published = counter(5), materialized = counter(6);
        assertTrue(eject(pair)); assertTrue(eject(pair));
        assertEquals(published + 1, counter(5), "one pending publication, then a native merge");
        assertEquals(1, net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromContainer(pair.target));
        assertFalse(pair.target.isEmpty());
        var full = HopperBlockEntity.class.getDeclaredMethod("inventoryFull"); full.setAccessible(true); assertFalse((boolean)full.invoke(pair.target));
        var registries = net.minecraft.server.RegistryLayer.createRegistryAccess().compositeAccess();
        var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, registries);
        net.minecraft.world.ContainerHelper.saveAllItems(output, pair.target.getItems());
        assertTrue(output.buildResult().toString().contains("stick"), output.buildResult().toString());
        assertEquals(materialized, counter(6), "comparator, occupancy and saving read native contents without a facade");
        ItemStack held = pair.target.getItem(0);
        assertEquals(materialized + 1, counter(6)); assertEquals(2, held.getCount()); assertSame(held, pair.target.getItem(0));
        assertEquals(3, pair.source.getItem(0).getCount());
    }
}

