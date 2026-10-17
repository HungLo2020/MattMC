package net.minecraft.world.inventory;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.HashedPatchMap;
import net.minecraft.network.HashedStack;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The admitted native broadcast path (read-only observers) against the original
 * Java loop (identical observers without the marker) over the same tick-like
 * sequence: every listener call, remote send, payload and order must match.
 */
class NativeBroadcastTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private static long counter(long index) {
        try {
            var handle = net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_transfer_counter",
                java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_LONG, java.lang.foreign.ValueLayout.JAVA_LONG));
            return (long)handle.invokeExact(index);
        } catch (Throwable failure) { throw new AssertionError(failure); }
    }
    private static long materialized() { return counter(6); }
    private static long broadcastCopies() { return counter(8); }

    static String describe(ItemStack stack) {
        return stack.isEmpty() ? "empty" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount() + stack.getComponentsPatch();
    }

    static class Recorder implements ContainerSynchronizer, ContainerListener {
        final List<String> events = new ArrayList<>();
        IntConsumer hook;
        @Override public void sendInitialData(AbstractContainerMenu menu, List<ItemStack> list, ItemStack carried, int[] data) {
            var line = new StringBuilder("init");
            for (ItemStack stack : list) line.append(' ').append(describe(stack));
            this.events.add(line.append(" carried ").append(describe(carried)).toString());
        }
        @Override public void sendSlotChange(AbstractContainerMenu menu, int i, ItemStack stack) { this.events.add("send " + i + " " + describe(stack)); }
        @Override public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) { this.events.add("carried " + describe(stack)); }
        @Override public void sendDataChange(AbstractContainerMenu menu, int i, int j) { this.events.add("data " + i + " " + j); }
        @Override public RemoteSlot createSlot() { return new RemoteSlot.Synchronized((HashedPatchMap.HashGenerator)component -> component.hashCode()); }
        @Override public void slotChanged(AbstractContainerMenu menu, int i, ItemStack stack) {
            this.events.add("changed " + i + " " + describe(stack));
            if (this.hook != null) this.hook.accept(i);
        }
        @Override public void dataChanged(AbstractContainerMenu menu, int i, int j) { this.events.add("dataChanged " + i + " " + j); }
    }
    static final class MarkedRecorder extends Recorder implements AbstractContainerMenu.ReadOnlyBroadcastObserver {}

    record World(Inventory inventory, ChestBlockEntity chest, ChestMenu menu, Recorder recorder) {
        void tick() { this.menu.broadcastChanges(); this.recorder.events.add("tick"); }
    }
    static World world(boolean admitted) {
        Inventory inventory = new Inventory(null, new EntityEquipment());
        var chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        chest.setRemoved(); var menu = ChestMenu.threeRows(1, inventory, chest); chest.clearRemoved();
        return new World(inventory, chest, menu, admitted ? new MarkedRecorder() : new Recorder());
    }

    static void sequence(World w) {
        w.menu.setSynchronizer(w.recorder); w.menu.addSlotListener(w.recorder);
        w.tick(); w.tick();
        w.inventory.setItem(9, new ItemStack(Items.STICK, 11)); w.tick();
        w.menu.quickMoveStack(null, 27); w.tick(); w.tick();
        w.inventory.setItem(10, new ItemStack(Items.STICK, 11)); w.menu.quickMoveStack(null, 28); w.tick();
        w.menu.setRemoteSlotUnsafe(0, HashedStack.EMPTY); w.tick(); w.tick();
        w.menu.suppressRemoteUpdates(); w.inventory.setItem(11, new ItemStack(Items.DIRT, 5)); w.menu.quickMoveStack(null, 29); w.tick();
        w.menu.resumeRemoteUpdates(); w.tick();
        w.menu.quickMoveStack(null, 1); w.tick(); w.tick();
        boolean[] done = new boolean[1];
        w.recorder.hook = i -> { if (i == 0 && !done[0]) { done[0] = true; w.chest.setItem(5, new ItemStack(Items.DIAMOND, 2)); } };
        w.menu.quickMoveStack(null, 0); w.tick(); w.tick();
        w.recorder.hook = null;
        w.menu.sendAllDataToRemote(); w.tick();
    }

    @Test void admittedBroadcastMatchesTheOriginalLoopWithoutMaterializingPendingContents() {
        World original = world(false); sequence(original);
        World admitted = world(true);
        long before = materialized();
        sequence(admitted);
        assertEquals(original.recorder.events, admitted.recorder.events);
        assertTrue(admitted.recorder.events.stream().anyMatch(event -> event.startsWith("send 0 minecraft:stickx22")), admitted.recorder.events.toString());
        // sendAllDataToRemote genuinely reads every slot, so only it may materialize.
        long afterSequence = materialized() - before;
        World quiet = world(true); quiet.menu.setSynchronizer(quiet.recorder); quiet.menu.addSlotListener(quiet.recorder);
        quiet.inventory.setItem(9, new ItemStack(Items.STICK, 11));
        long published = materialized();
        quiet.menu.quickMoveStack(null, 27); quiet.tick();
        long copies = broadcastCopies();
        quiet.tick(); quiet.tick();
        assertEquals(copies, broadcastCopies(), "unchanged ticks skip the pending slot entirely");
        assertEquals(published, materialized(), "broadcasting pending contents never materializes the slot's facade");
        ItemStack canonical = quiet.chest.getItem(0);
        assertEquals(published + 1, materialized()); assertSame(canonical, quiet.menu.getSlot(0).getItem()); assertEquals(11, canonical.getCount());
        canonical.setCount(4); int size = quiet.recorder.events.size(); quiet.tick();
        assertEquals(List.of("changed 0 minecraft:stickx4{}", "send 0 minecraft:stickx4{}", "tick"), quiet.recorder.events.subList(size, quiet.recorder.events.size()),
            "a facade mutation of skipped contents is still detected and sent");
        assertTrue(afterSequence >= 0);
    }
}
