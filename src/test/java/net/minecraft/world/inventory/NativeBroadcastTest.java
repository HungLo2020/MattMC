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

    record World(Inventory inventory, net.minecraft.world.Container chest, ChestMenu menu, Recorder recorder) {
        void tick() { this.menu.broadcastChanges(); this.recorder.events.add("tick"); }
    }
    /** The same world over a stock barrel (a ChestMenu over native barrel rows). */
    static World barrelWorld(boolean admitted) {
        Inventory inventory = new Inventory(null, new EntityEquipment());
        var barrel = new net.minecraft.world.level.block.entity.BarrelBlockEntity(BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
        barrel.setRemoved(); var menu = ChestMenu.threeRows(1, inventory, barrel); barrel.clearRemoved();
        return new World(inventory, barrel, menu, admitted ? new MarkedRecorder() : new Recorder());
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

    // Comparator signal: the native sum over exact chest rows equals the
    // original per-slot computation (anonymous subclass) for random contents,
    // and reading it leaves pending contents unmaterialized.
    @Test void nativeComparatorSignalMatchesTheOriginalComputation() {
        var random = new java.util.Random(34);
        var items = List.of(Items.STICK, Items.DIRT, Items.ENDER_PEARL, Items.DIAMOND_SWORD, Items.SNOWBALL);
        for (int round = 0; round < 200; round++) {
            var exact = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            var original = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()) {};
            for (int i = 0; i < 27; i++) {
                if (random.nextInt(3) == 0) continue;
                var item = items.get(random.nextInt(items.size()));
                int count = 1 + random.nextInt(new ItemStack(item).getMaxStackSize());
                exact.setItem(i, new ItemStack(item, count)); original.setItem(i, new ItemStack(item, count));
            }
            assertEquals(AbstractContainerMenu.getRedstoneSignalFromContainer(original), AbstractContainerMenu.getRedstoneSignalFromContainer(exact), "round " + round);
        }
        World w = world(true); w.inventory.setItem(9, new ItemStack(Items.STICK, 11)); w.menu.quickMoveStack(null, 27);
        long before = materialized();
        assertEquals(1, AbstractContainerMenu.getRedstoneSignalFromContainer(w.chest));
        assertEquals(before, materialized(), "the native signal reads pending contents without a facade");
    }

    @Test void barrelBroadcastMatchesTheOriginalLoop() {
        World original = barrelWorld(false); sequence(original);
        World admitted = barrelWorld(true); sequence(admitted);
        assertEquals(original.recorder.events, admitted.recorder.events);
    }

    // The menu's slot list keeps a Java mirror of its native rows: every get after
    // add/set/remove (and their failures) matches a plain list, as does the original
    // out-of-bounds message.
    @Test void menuSlotListMirrorTracksEveryEdit() {
        Inventory inventory = new Inventory(null, new EntityEquipment());
        var chest = new net.minecraft.world.level.block.entity.ChestBlockEntity(BlockPos.ZERO, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        chest.setRemoved(); ChestMenu menu = ChestMenu.threeRows(1, inventory, chest); chest.clearRemoved();
        var expected = new ArrayList<Slot>(menu.slots);
        var random = new java.util.Random(11);
        for (int step = 0; step < 300; step++) {
            int op = random.nextInt(3);
            Slot slot = new Slot(inventory, random.nextInt(36), 0, 0);
            if (op == 0 && !expected.isEmpty()) { int index = random.nextInt(expected.size()); assertSame(expected.set(index, slot), menu.slots.set(index, slot)); }
            else if (op == 1) { int index = random.nextInt(expected.size() + 1); expected.add(index, slot); menu.slots.add(index, slot); }
            else if (!expected.isEmpty()) { int index = random.nextInt(expected.size()); assertSame(expected.remove(index), menu.slots.remove(index)); }
            assertEquals(expected.size(), menu.slots.size());
            for (int i = 0; i < expected.size(); i++) assertSame(expected.get(i), menu.slots.get(i), "step " + step + " index " + i);
        }
        int size = expected.size();
        var failure = assertThrows(IndexOutOfBoundsException.class, () -> menu.slots.get(size));
        assertEquals("Index " + size + " out of bounds for length " + size, failure.getMessage());
        assertThrows(IndexOutOfBoundsException.class, () -> menu.slots.get(-1));
        assertThrows(NullPointerException.class, () -> menu.slots.set(0, null));
        assertSame(expected.get(0), menu.slots.get(0), "a refused edit leaves the mirror unchanged");
        // Every refused edit (bounds, null) fails before the native edit: projection and rows stay equal.
        Slot spare = new Slot(inventory, 0, 0, 0);
        for (Runnable refused : List.<Runnable>of(() -> menu.slots.set(size, spare), () -> menu.slots.set(-1, spare), () -> menu.slots.add(size + 1, spare),
                () -> menu.slots.add(-1, spare), () -> menu.slots.remove(size), () -> menu.slots.remove(-1), () -> menu.slots.add(0, null))) {
            assertThrows(RuntimeException.class, refused::run);
            assertEquals(expected.size(), menu.slots.size());
            for (int i = 0; i < expected.size(); i++) assertSame(expected.get(i), menu.slots.get(i));
        }
    }

    // Player InventoryMenu: armor/offhand slots (Slot subclasses keeping Slot.getItem) get
    // broadcast-only native digests from the equipment rows; quick-move links are unchanged.
    // Random equipment/main/grid changes must broadcast exactly like an unlinked subclass inventory.
    static final class ReadOnlyListener implements ContainerListener, AbstractContainerMenu.ReadOnlyBroadcastObserver {
        final List<String> events;
        ReadOnlyListener(List<String> events) { this.events = events; }
        @Override public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) { events.add("slot " + slot + " " + stack.getItem() + "x" + stack.getCount() + " d" + stack.getDamageValue()); }
        @Override public void dataChanged(AbstractContainerMenu menu, int id, int value) {}
    }
    static List<String> playerMenu(boolean exact) {
        var player = org.mockito.Mockito.mock(net.minecraft.world.entity.player.Player.class);
        var equipment = new EntityEquipment();
        Inventory inventory = exact ? new Inventory(player, equipment) : new Inventory(player, equipment) {};
        org.mockito.Mockito.doReturn(inventory).when(player).getInventory();
        var menu = new InventoryMenu(inventory, true, player);
        var events = new ArrayList<String>();
        menu.addSlotListener(new ReadOnlyListener(events));
        menu.broadcastChanges();
        var random = new java.util.Random(23);
        var slots = net.minecraft.world.entity.EquipmentSlot.values();
        var items = List.of(net.minecraft.world.item.Items.IRON_HELMET, net.minecraft.world.item.Items.SHIELD, net.minecraft.world.item.Items.STICK, net.minecraft.world.item.Items.DIAMOND_BOOTS);
        for (int step = 0; step < 300; step++) {
            switch (random.nextInt(5)) {
                case 0 -> { var slot = slots[random.nextInt(slots.length)]; equipment.set(slot, random.nextInt(3) == 0 ? ItemStack.EMPTY : new ItemStack(items.get(random.nextInt(items.size())), 1 + random.nextInt(2))); }
                case 1 -> { var stack = equipment.get(slots[random.nextInt(slots.length)]); if (!stack.isEmpty()) { if (stack.isDamageableItem()) stack.setDamageValue(random.nextInt(5)); else stack.setCount(1 + random.nextInt(3)); } }
                case 2 -> inventory.setItem(random.nextInt(36), random.nextInt(3) == 0 ? ItemStack.EMPTY : new ItemStack(net.minecraft.world.item.Items.STICK, 1 + random.nextInt(9)));
                case 3 -> menu.getSlot(1 + random.nextInt(4)).set(random.nextInt(2) == 0 ? ItemStack.EMPTY : new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 1 + random.nextInt(4)));
                default -> {}
            }
            menu.broadcastChanges();
            events.add("tick " + step);
        }
        return events;
    }
    @Test void playerMenuEquipmentBroadcastMatchesTheOriginal() {
        assertEquals(playerMenu(false), playerMenu(true));
        var player = org.mockito.Mockito.mock(net.minecraft.world.entity.player.Player.class);
        var inventory = new Inventory(player, new EntityEquipment()); org.mockito.Mockito.doReturn(inventory).when(player).getInventory();
        var menu = new InventoryMenu(inventory, true, player);
        Slot helmet = menu.getSlot(5);
        assertEquals(39, helmet.getContainerSlot());
        assertNull(helmet.nativeLinkForMenu(), "armor slots keep the original quick-move path");
        assertNotNull(helmet.broadcastLinkForMenu());
        assertTrue(helmet.broadcastLinkForMenu().isEquipment());
        assertNotNull(menu.getSlot(45).broadcastLinkForMenu(), "the offhand slot keeps Slot.getItem");
        assertNotNull(menu.getSlot(0).broadcastLinkForMenu(), "the result slot keeps Slot.getItem over the native result cell");
        assertNull(menu.getSlot(0).nativeLinkForMenu(), "ResultSlot keeps the original quick-move path");
    }

    // A null equipment (legal for Inventory's constructor) keeps the original prefix:
    // building the slot succeeds and the failure stays in getItem, as for an unlinked slot.
    static String nullEquipment(boolean exact) {
        Inventory inventory = exact ? new Inventory(null, null) : new Inventory(null, null) {};
        Slot slot;
        try { slot = new Slot(inventory, 36, 0, 0); } catch (Throwable failure) { return "construct " + failure.getClass().getName(); }
        try { return "item " + slot.getItem(); } catch (Throwable failure) { return "getItem " + failure.getClass().getName() + " link " + (slot.broadcastLinkForMenu() != null); }
    }
    @Test void nullEquipmentKeepsTheOriginalConstructionPrefix() {
        assertEquals(nullEquipment(false), nullEquipment(true));
        assertEquals("getItem java.lang.NullPointerException link false", nullEquipment(true));
    }

    // Inventory.EQUIPMENT_SLOT_MAPPING is public and mutable; Inventory.getItem re-reads it.
    // After a remap the equipment digest must not hide a real change of what the slot reads.
    static List<String> remappedEquipment(boolean exact) {
        var mapping = Inventory.EQUIPMENT_SLOT_MAPPING;
        var original = mapping.get(36);
        var player = org.mockito.Mockito.mock(net.minecraft.world.entity.player.Player.class);
        var equipment = new EntityEquipment();
        Inventory inventory = exact ? new Inventory(player, equipment) : new Inventory(player, equipment) {};
        org.mockito.Mockito.doReturn(inventory).when(player).getInventory();
        var menu = new InventoryMenu(inventory, true, player);
        var events = new ArrayList<String>();
        menu.addSlotListener(new ReadOnlyListener(events));
        try {
            equipment.set(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS));
            menu.broadcastChanges(); events.add("tick feet");
            mapping.put(36, net.minecraft.world.entity.EquipmentSlot.HEAD);
            equipment.set(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
            menu.broadcastChanges(); events.add("tick remapped");
            equipment.set(net.minecraft.world.entity.EquipmentSlot.FEET, ItemStack.EMPTY);
            menu.broadcastChanges(); events.add("tick feet cleared");
            mapping.remove(36);
            menu.broadcastChanges(); events.add("tick removed");
        } finally { mapping.put(36, original); }
        return events;
    }
    @Test void remappedEquipmentRowsBroadcastLikeTheOriginal() {
        assertEquals(remappedEquipment(false), remappedEquipment(true));
        assertEquals(net.minecraft.world.entity.EquipmentSlot.FEET, Inventory.EQUIPMENT_SLOT_MAPPING.get(36), "the shared mapping was restored");
    }
}

