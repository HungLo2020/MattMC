package net.minecraft.world.level.block.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Stock barrels share the chest's native rows (NativeStockContainer): quick
 * moves, comparator signal and hopper ejects on an exact barrel match an
 * anonymous-subclass barrel, which keeps the original Java path.
 */
class NativeBarrelTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private static long counter(long index) {
        try {
            var handle = net.minecraft.util.NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_item_transfer_counter",
                java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_LONG, java.lang.foreign.ValueLayout.JAVA_LONG));
            return (long)handle.invokeExact(index);
        } catch (Throwable failure) { throw new AssertionError(failure); }
    }

    static BarrelBlockEntity barrel(boolean exact) {
        BlockState state = Blocks.BARREL.defaultBlockState();
        return exact ? new BarrelBlockEntity(BlockPos.ZERO, state) : new BarrelBlockEntity(BlockPos.ZERO, state) {};
    }

    static String contents(net.minecraft.world.Container container) {
        var line = new StringBuilder();
        for (int i = 0; i < container.getContainerSize(); i++) { ItemStack stack = container.getItem(i); line.append(stack.getItem()).append('x').append(stack.getCount()).append(' '); }
        return line.toString();
    }

    static List<String> quickMoves(boolean exact) {
        Inventory inventory = new Inventory(null, new EntityEquipment());
        BarrelBlockEntity barrel = barrel(exact);
        barrel.setRemoved(); ChestMenu menu = ChestMenu.threeRows(1, inventory, barrel); barrel.clearRemoved();
        inventory.setItem(9, new ItemStack(Items.STICK, 11)); inventory.setItem(10, new ItemStack(Items.STICK, 60));
        var results = new ArrayList<String>();
        results.add("forward " + menu.quickMoveStack(null, 27).getCount() + " " + menu.quickMoveStack(null, 28).getCount());
        results.add("reverse " + menu.quickMoveStack(null, 0).getCount());
        results.add("barrel " + contents(barrel)); results.add("inventory " + contents(inventory));
        return results;
    }

    @Test void barrelQuickMovesMatchTheOriginalPathAndStayPending() {
        assertEquals(quickMoves(false), quickMoves(true));
        Inventory inventory = new Inventory(null, new EntityEquipment());
        BarrelBlockEntity barrel = barrel(true); barrel.setRemoved(); ChestMenu menu = ChestMenu.threeRows(1, inventory, barrel); barrel.clearRemoved();
        inventory.setItem(9, new ItemStack(Items.STICK, 11));
        long published = counter(5), materialized = counter(6);
        menu.quickMoveStack(null, 27);
        assertEquals(published + 1, counter(5), "a barrel insertion is a native pending publication");
        assertEquals(1, AbstractContainerMenu.getRedstoneSignalFromContainer(barrel));
        assertEquals(materialized, counter(6));
        ItemStack held = barrel.getItem(0); assertEquals(11, held.getCount()); assertSame(held, menu.getSlot(0).getItem());
    }

    @Test void barrelComparatorSignalMatchesTheOriginalComputation() {
        var random = new java.util.Random(27);
        var items = List.of(Items.STICK, Items.ENDER_PEARL, Items.DIAMOND_SWORD);
        for (int round = 0; round < 100; round++) {
            BarrelBlockEntity exact = barrel(true), original = barrel(false);
            for (int i = 0; i < 27; i++) {
                if (random.nextInt(2) == 0) continue;
                var item = items.get(random.nextInt(items.size()));
                int count = 1 + random.nextInt(new ItemStack(item).getMaxStackSize());
                exact.setItem(i, new ItemStack(item, count)); original.setItem(i, new ItemStack(item, count));
            }
            assertEquals(AbstractContainerMenu.getRedstoneSignalFromContainer(original), AbstractContainerMenu.getRedstoneSignalFromContainer(exact), "round " + round);
            assertEquals(original.isEmpty(), exact.isEmpty());
        }
    }

    static List<String> hopperIntoBarrel(boolean exact) throws Exception {
        BlockPos hopperPos = new BlockPos(0, 64, 0), barrelPos = hopperPos.below();
        BlockState down = Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN);
        HopperBlockEntity hopper = exact ? new HopperBlockEntity(hopperPos, down) : new HopperBlockEntity(hopperPos, down) {};
        BarrelBlockEntity barrel = exact ? new BarrelBlockEntity(barrelPos, Blocks.BARREL.defaultBlockState()) : new BarrelBlockEntity(barrelPos, Blocks.BARREL.defaultBlockState()) {};
        Level level = mock(Level.class);
        when(level.getBlockState(hopperPos)).thenReturn(down);
        when(level.getBlockState(barrelPos)).thenReturn(Blocks.BARREL.defaultBlockState());
        when(level.getBlockEntity(barrelPos)).thenReturn(barrel);
        hopper.setLevel(level); barrel.setLevel(level);
        hopper.setItem(0, new ItemStack(Items.STICK, 3)); barrel.setItem(4, new ItemStack(Items.STICK, 63));
        var eject = HopperBlockEntity.class.getDeclaredMethod("ejectItems", Level.class, BlockPos.class, HopperBlockEntity.class); eject.setAccessible(true);
        var results = new ArrayList<String>();
        for (int i = 0; i < 4; i++) results.add("eject " + eject.invoke(null, level, hopperPos, hopper));
        results.add("barrel " + contents(barrel)); results.add("hopper " + contents(hopper));
        for (var invocation : mockingDetails(level).getInvocations()) {
            String name = invocation.getMethod().getName();
            if (name.equals("blockEntityChanged") || name.equals("updateNeighbourForOutputSignal")) results.add(name + " " + invocation.getArgument(0));
        }
        return results;
    }

    @Test void hopperEjectIntoBarrelMatchesTheOriginalPath() throws Exception {
        assertEquals(hopperIntoBarrel(false), hopperIntoBarrel(true));
    }

    @Test void doubleChestComparatorSignalMatchesTheOriginalComputation() {
        var random = new java.util.Random(54);
        var items = List.of(Items.STICK, Items.ENDER_PEARL, Items.DIAMOND_SWORD, Items.DIRT);
        for (int round = 0; round < 100; round++) {
            var a = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); var b = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            var c = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()) {}; var d = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()) {};
            var exact = new net.minecraft.world.CompoundContainer(a, b); var original = new net.minecraft.world.CompoundContainer(c, d);
            for (int i = 0; i < 54; i++) {
                if (random.nextInt(3) != 0) continue;
                var item = items.get(random.nextInt(items.size()));
                int count = 1 + random.nextInt(new ItemStack(item).getMaxStackSize());
                exact.setItem(i, new ItemStack(item, count)); original.setItem(i, new ItemStack(item, count));
            }
            assertEquals(AbstractContainerMenu.getRedstoneSignalFromContainer(original), AbstractContainerMenu.getRedstoneSignalFromContainer(exact), "round " + round);
        }
    }

    // A six-row menu over a double chest of two exact chests links each slot to
    // its half's native row (half offset kept in getContainerSlot); writes and
    // setChanged still go through the CompoundContainer. Subclass halves keep the
    // original unlinked path. Random clicks, quick moves and broadcasts must match.
    static List<String> doubleChestMenu(boolean exact) {
        Inventory inventory = new Inventory(null, new EntityEquipment());
        var player = mock(net.minecraft.world.entity.player.Player.class); var level = mock(Level.class);
        doReturn(inventory).when(player).getInventory(); doReturn(level).when(player).level();
        doReturn(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS).when(level).enabledFeatures();
        ChestBlockEntity a = exact ? new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()) : new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()) {};
        ChestBlockEntity b = exact ? new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()) : new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()) {};
        var compound = new net.minecraft.world.CompoundContainer(a, b);
        a.setRemoved(); b.setRemoved(); ChestMenu menu = ChestMenu.sixRows(1, inventory, compound); a.clearRemoved(); b.clearRemoved();
        var results = new ArrayList<String>();
        menu.addSlotListener(new net.minecraft.world.inventory.ContainerListener() {
            @Override public void slotChanged(AbstractContainerMenu changed, int slot, ItemStack stack) { results.add("listener " + slot + " " + stack.getItem() + "x" + stack.getCount()); }
            @Override public void dataChanged(AbstractContainerMenu changed, int id, int value) {}
        });
        var random = new java.util.Random(6);
        var items = List.of(Items.STICK, Items.ENDER_PEARL, Items.DIAMOND_SWORD, Items.DIRT);
        for (int i = 0; i < 54; i++) if (random.nextInt(3) == 0) { var item = items.get(random.nextInt(items.size())); compound.setItem(i, new ItemStack(item, 1 + random.nextInt(new ItemStack(item).getMaxStackSize()))); }
        for (int i = 0; i < 36; i++) if (random.nextInt(3) == 0) { var item = items.get(random.nextInt(items.size())); inventory.setItem(i, new ItemStack(item, 1 + random.nextInt(new ItemStack(item).getMaxStackSize()))); }
        for (int i = 0; i < menu.slots.size(); i++) results.add("slot " + i + " " + menu.getSlot(i).getContainerSlot() + " " + menu.getSlot(i).getItem().getCount());
        for (int step = 0; step < 400; step++) {
            int slot = random.nextInt(menu.slots.size());
            switch (random.nextInt(4)) {
                case 0 -> results.add(step + " quick " + slot + " " + menu.quickMoveStack(player, slot).getCount());
                case 1 -> { menu.clicked(slot, random.nextInt(2), net.minecraft.world.inventory.ClickType.PICKUP, player); results.add(step + " pickup " + slot); }
                case 2 -> { menu.clicked(slot, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player); results.add(step + " shift " + slot); }
                default -> { menu.clicked(slot, random.nextInt(9), net.minecraft.world.inventory.ClickType.SWAP, player); results.add(step + " swap " + slot); }
            }
            menu.broadcastChanges();
            results.add(step + " carried " + menu.getCarried().getItem() + "x" + menu.getCarried().getCount() + " item " + menu.getSlot(slot).getItem().getCount() + " has " + menu.getSlot(slot).hasItem());
        }
        results.add("first " + contents(a)); results.add("second " + contents(b)); results.add("inventory " + contents(inventory));
        return results;
    }

    @Test void doubleChestMenusMatchTheOriginalPath() {
        assertEquals(doubleChestMenu(false), doubleChestMenu(true));
        Inventory inventory = new Inventory(null, new EntityEquipment());
        var a = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); var b = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        a.setRemoved(); b.setRemoved(); ChestMenu menu = ChestMenu.sixRows(1, inventory, new net.minecraft.world.CompoundContainer(a, b)); a.clearRemoved(); b.clearRemoved();
        for (int i = 0; i < 54; i++) { assertNotNull(menu.getSlot(i).nativeLinkForMenu(), "slot " + i); assertEquals(i, menu.getSlot(i).getContainerSlot()); }
        var c = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()) {};
        a.setRemoved(); c.setRemoved(); ChestMenu mixed = ChestMenu.sixRows(2, inventory, new net.minecraft.world.CompoundContainer(a, c)); a.clearRemoved(); c.clearRemoved();
        assertNull(mixed.getSlot(0).nativeLinkForMenu(), "a subclass half keeps the whole compound unlinked");
        b.setItem(4, new ItemStack(Items.STICK, 9));
        assertEquals(9, menu.getSlot(31).getItem().getCount(), "the second half's row 4 is compound slot 31");
    }

    // A hopper ejecting into, and another sucking from, a stock double chest
    // (ChestBlock.getContainer -> CompoundContainer of two exact chests): the
    // native halves path matches the all-subclass original path.
    static List<String> doubleChestHoppers(boolean exact) throws Exception {
        BlockPos above = new BlockPos(0, 65, 0), left = new BlockPos(0, 64, 0), right = left.east(), below = left.below();
        BlockState leftState = Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.NORTH)
            .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT);
        BlockState rightState = leftState.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT);
        BlockState down = Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN);
        ChestBlockEntity a = exact ? new ChestBlockEntity(left, leftState) : new ChestBlockEntity(left, leftState) {};
        ChestBlockEntity b = exact ? new ChestBlockEntity(right, rightState) : new ChestBlockEntity(right, rightState) {};
        HopperBlockEntity feeder = exact ? new HopperBlockEntity(above, down) : new HopperBlockEntity(above, down) {};
        HopperBlockEntity drain = exact ? new HopperBlockEntity(below, down) : new HopperBlockEntity(below, down) {};
        Level level = mock(Level.class);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getBlockState(left)).thenReturn(leftState); when(level.getBlockState(right)).thenReturn(rightState);
        when(level.getBlockState(above)).thenReturn(down); when(level.getBlockState(below)).thenReturn(down);
        when(level.getBlockEntity(left)).thenReturn(a); when(level.getBlockEntity(right)).thenReturn(b);
        for (var entity : List.<BlockEntity>of(a, b, feeder, drain)) entity.setLevel(level);
        feeder.setItem(0, new ItemStack(Items.STICK, 2));
        for (int i = 0; i < 27; i++) a.setItem(i, new ItemStack(Items.DIRT, 64));
        b.setItem(5, new ItemStack(Items.STICK, 63));
        var eject = HopperBlockEntity.class.getDeclaredMethod("ejectItems", Level.class, BlockPos.class, HopperBlockEntity.class); eject.setAccessible(true);
        var results = new ArrayList<String>();
        for (int i = 0; i < 3; i++) results.add("eject " + eject.invoke(null, level, above, feeder));
        for (int i = 0; i < 2; i++) results.add("suck " + HopperBlockEntity.suckInItems(level, drain));
        results.add("left " + contents(a)); results.add("right " + contents(b)); results.add("feeder " + contents(feeder)); results.add("drain " + contents(drain));
        results.add("signal " + AbstractContainerMenu.getRedstoneSignalFromContainer(new net.minecraft.world.CompoundContainer(a, b)));
        for (var invocation : mockingDetails(level).getInvocations()) {
            String name = invocation.getMethod().getName();
            if (name.equals("blockEntityChanged") || name.equals("updateNeighbourForOutputSignal")) results.add(name + " " + invocation.getArgument(0));
        }
        return results;
    }

    @Test void hoppersOnADoubleChestMatchTheOriginalPath() throws Exception {
        assertEquals(doubleChestHoppers(false), doubleChestHoppers(true));
    }

    // A hopper ejecting into a stock shulker box: the box refuses shulker box
    // items through its faces, so the hopper moves its next slot instead.
    static List<String> hopperIntoShulker(boolean exact) throws Exception {
        BlockPos hopperPos = new BlockPos(0, 64, 0), boxPos = hopperPos.below();
        BlockState down = Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN);
        HopperBlockEntity hopper = exact ? new HopperBlockEntity(hopperPos, down) : new HopperBlockEntity(hopperPos, down) {};
        ShulkerBoxBlockEntity box = exact ? new ShulkerBoxBlockEntity(boxPos, Blocks.SHULKER_BOX.defaultBlockState()) : new ShulkerBoxBlockEntity(boxPos, Blocks.SHULKER_BOX.defaultBlockState()) {};
        Level level = mock(Level.class);
        when(level.getBlockState(hopperPos)).thenReturn(down);
        when(level.getBlockState(boxPos)).thenReturn(Blocks.SHULKER_BOX.defaultBlockState());
        when(level.getBlockEntity(boxPos)).thenReturn(box);
        hopper.setLevel(level); box.setLevel(level);
        hopper.setItem(0, new ItemStack(Items.WHITE_SHULKER_BOX)); hopper.setItem(1, new ItemStack(Items.STICK, 2)); box.setItem(3, new ItemStack(Items.STICK, 62));
        var eject = HopperBlockEntity.class.getDeclaredMethod("ejectItems", Level.class, BlockPos.class, HopperBlockEntity.class); eject.setAccessible(true);
        var results = new ArrayList<String>();
        for (int i = 0; i < 4; i++) results.add("eject " + eject.invoke(null, level, hopperPos, hopper));
        results.add("box " + contents(box)); results.add("hopper " + contents(hopper));
        results.add("signal " + AbstractContainerMenu.getRedstoneSignalFromContainer(box) + " empty " + box.isEmpty());
        for (var invocation : mockingDetails(level).getInvocations()) {
            String name = invocation.getMethod().getName();
            if (name.equals("blockEntityChanged") || name.equals("updateNeighbourForOutputSignal")) results.add(name + " " + invocation.getArgument(0));
        }
        return results;
    }

    @Test void hopperEjectIntoShulkerBoxMatchesTheOriginalPath() throws Exception {
        assertEquals(hopperIntoShulker(false), hopperIntoShulker(true));
    }

    static List<String> hopperIntoDropper(boolean exact) throws Exception {
        BlockPos hopperPos = new BlockPos(0, 64, 0), dropperPos = hopperPos.below();
        BlockState down = Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN);
        HopperBlockEntity hopper = exact ? new HopperBlockEntity(hopperPos, down) : new HopperBlockEntity(hopperPos, down) {};
        DropperBlockEntity dropper = exact ? new DropperBlockEntity(dropperPos, Blocks.DROPPER.defaultBlockState()) : new DropperBlockEntity(dropperPos, Blocks.DROPPER.defaultBlockState()) {};
        Level level = mock(Level.class);
        when(level.getBlockState(hopperPos)).thenReturn(down);
        when(level.getBlockState(dropperPos)).thenReturn(Blocks.DROPPER.defaultBlockState());
        when(level.getBlockEntity(dropperPos)).thenReturn(dropper);
        hopper.setLevel(level); dropper.setLevel(level);
        hopper.setItem(2, new ItemStack(Items.ENDER_PEARL, 3)); dropper.setItem(0, new ItemStack(Items.ENDER_PEARL, 15));
        var eject = HopperBlockEntity.class.getDeclaredMethod("ejectItems", Level.class, BlockPos.class, HopperBlockEntity.class); eject.setAccessible(true);
        var results = new ArrayList<String>();
        for (int i = 0; i < 4; i++) results.add("eject " + eject.invoke(null, level, hopperPos, hopper));
        results.add("dropper " + contents(dropper)); results.add("hopper " + contents(hopper));
        results.add("signal " + AbstractContainerMenu.getRedstoneSignalFromContainer(dropper) + " empty " + dropper.isEmpty());
        for (var invocation : mockingDetails(level).getInvocations()) {
            String name = invocation.getMethod().getName();
            if (name.equals("blockEntityChanged") || name.equals("updateNeighbourForOutputSignal")) results.add(name + " " + invocation.getArgument(0));
        }
        return results;
    }

    @Test void hopperEjectIntoDropperMatchesTheOriginalPath() throws Exception {
        assertEquals(hopperIntoDropper(false), hopperIntoDropper(true));
    }

    // Dispenser slot choice: the same random draws over native emptiness.
    @Test void dispenserRandomSlotMatchesTheOriginalDraws() {
        for (long seed = 0; seed < 50; seed++) {
            var exact = new DispenserBlockEntity(BlockPos.ZERO, Blocks.DISPENSER.defaultBlockState());
            var original = new DispenserBlockEntity(BlockPos.ZERO, Blocks.DISPENSER.defaultBlockState()) {};
            var fill = new java.util.Random(seed);
            for (int i = 0; i < 9; i++) if (fill.nextBoolean()) { exact.setItem(i, new ItemStack(Items.ARROW, 1 + i)); original.setItem(i, new ItemStack(Items.ARROW, 1 + i)); }
            var a = net.minecraft.util.RandomSource.create(seed); var b = net.minecraft.util.RandomSource.create(seed);
            for (int draw = 0; draw < 5; draw++) assertEquals(original.getRandomSlot(a), exact.getRandomSlot(b), "seed " + seed);
            assertEquals(a.nextLong(), b.nextLong(), "identical random consumption");
        }
    }
}

