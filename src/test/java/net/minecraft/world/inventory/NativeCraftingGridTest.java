package net.minecraft.world.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * An exact TransientCraftingContainer keeps its grid in native rows. Every
 * container operation and its menu.slotsChanged notification must match a
 * subclass container, which keeps the original list.
 */
class NativeCraftingGridTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    static List<String> grid(boolean exact) {
        var events = new ArrayList<String>();
        AbstractContainerMenu menu = mock(AbstractContainerMenu.class);
        doAnswer(call -> { events.add("slotsChanged"); return null; }).when(menu).slotsChanged(any());
        TransientCraftingContainer grid = exact ? new TransientCraftingContainer(menu, 3, 3) : new TransientCraftingContainer(menu, 3, 3) {};
        assertEquals(exact, grid.nativeSlotsForTransfer() != null);
        var random = new java.util.Random(9);
        var items = List.of(Items.STICK, Items.DIRT, Items.DIAMOND_SWORD, Items.OAK_PLANKS);
        for (int step = 0; step < 400; step++) {
            int slot = random.nextInt(10); // 9 is out of range for getItem
            switch (random.nextInt(7)) {
                case 0 -> { var item = items.get(random.nextInt(items.size())); grid.setItem(slot % 9, new ItemStack(item, 1 + random.nextInt(new ItemStack(item).getMaxStackSize()))); events.add("set " + slot % 9); }
                case 1 -> { var taken = grid.removeItem(slot % 9, 1 + random.nextInt(8)); events.add("remove " + taken.getItem() + "x" + taken.getCount()); }
                case 2 -> { var taken = grid.removeItemNoUpdate(slot % 9); events.add("take " + taken.getItem() + "x" + taken.getCount()); }
                case 3 -> { var stack = grid.getItem(slot); events.add("get " + slot + " " + stack.getItem() + "x" + stack.getCount()); }
                case 4 -> events.add("empty " + grid.isEmpty() + " items " + grid.getItems().stream().map(s -> s.getItem() + "x" + s.getCount()).toList());
                case 5 -> { var contents = new StackedItemContents(); grid.fillStackedContents(contents); events.add("stacked"); }
                default -> { if (random.nextInt(8) == 0) { grid.clearContent(); events.add("clear"); } }
            }
        }
        return events;
    }

    @Test void nativeGridMatchesTheOriginalContainer() {
        assertEquals(grid(false), grid(true));
    }

    @Test void exactGridSlotsLinkTheirNativeRows() {
        AbstractContainerMenu menu = mock(AbstractContainerMenu.class);
        var grid = new TransientCraftingContainer(menu, 2, 2);
        var slot = new Slot(grid, 3, 0, 0);
        assertNotNull(slot.nativeLinkForMenu());
        assertEquals(3, slot.getContainerSlot());
        grid.setItem(3, new ItemStack(Items.STICK, 5));
        assertEquals(5, slot.getItem().getCount());
        assertSame(grid.getItem(3), slot.getItem());
        assertNull(new Slot(new TransientCraftingContainer(menu, 2, 2) {}, 0, 0, 0).nativeLinkForMenu(), "a subclass grid stays unlinked");
    }

    // Sizes outside 1..27 (including negative and overflowing products) keep the
    // original list: the same exception class and message, or the same empty grid.
    static String construct(boolean exact, int width, int height) {
        AbstractContainerMenu menu = mock(AbstractContainerMenu.class);
        try {
            var grid = exact ? new TransientCraftingContainer(menu, width, height) : new TransientCraftingContainer(menu, width, height) {};
            return "size " + grid.getContainerSize() + " empty " + grid.isEmpty() + " native " + (grid.nativeSlotsForTransfer() != null);
        } catch (Throwable failure) { return failure.getClass().getName() + ":" + failure.getMessage(); }
    }
    @Test void constructorBoundariesMatchTheOriginal() {
        int[][] shapes = {{-1, 1}, {1, -1}, {-3, -3}, {0, 3}, {3, 0}, {0, 0}, {1, 1}, {3, 9}, {4, 7}, {65536, 65537}, {Integer.MIN_VALUE, -1}, {46341, 46341}, {Integer.MAX_VALUE, 2}};
        for (int[] shape : shapes) {
            String original = construct(false, shape[0], shape[1]).replace(" native false", "");
            String exact = construct(true, shape[0], shape[1]).replace(" native true", "").replace(" native false", "");
            assertEquals(original, exact, shape[0] + "x" + shape[1]);
        }
        assertTrue(construct(true, -3, -3).endsWith("native true"), "a positive product of negative dimensions keeps native rows like the original list size");
    }

    // The exact ResultContainer keeps its one cell in native rows; every method,
    // including reading cell 0 for any index, must match a subclass container.
    static List<String> result(boolean exact) {
        var events = new ArrayList<String>();
        ResultContainer container = exact ? new ResultContainer() : new ResultContainer() {};
        assertEquals(exact, container.nativeSlotsForTransfer() != null);
        var random = new java.util.Random(31);
        for (int step = 0; step < 300; step++) {
            int index = random.nextInt(4) - 1;
            switch (random.nextInt(6)) {
                case 0 -> { container.setItem(index, random.nextInt(4) == 0 ? ItemStack.EMPTY : new ItemStack(Items.STICK, 1 + random.nextInt(64))); events.add("set " + index); }
                case 1 -> { var stack = container.getItem(index); events.add("get " + index + " " + stack.getItem() + "x" + stack.getCount()); }
                case 2 -> { var taken = container.removeItem(index, 1 + random.nextInt(8)); events.add("remove " + taken.getItem() + "x" + taken.getCount()); }
                case 3 -> { var taken = container.removeItemNoUpdate(index); events.add("take " + taken.getItem() + "x" + taken.getCount()); }
                case 4 -> events.add("empty " + container.isEmpty());
                default -> { if (random.nextInt(6) == 0) { container.clearContent(); events.add("clear"); } }
            }
        }
        container.setRecipeUsed(null); events.add("recipe " + container.getRecipeUsed());
        return events;
    }
    @Test void nativeResultContainerMatchesTheOriginal() {
        assertEquals(result(false), result(true));
        var container = new ResultContainer();
        var linked = new Slot(container, 0, 0, 0); var other = new Slot(container, 1, 0, 0);
        assertNotNull(linked.nativeLinkForMenu()); assertNull(other.nativeLinkForMenu(), "only index 0 links the single cell");
        container.setItem(0, new ItemStack(Items.STICK, 4));
        assertSame(container.getItem(0), linked.getItem()); assertSame(container.getItem(0), other.getItem());
        assertEquals(1, other.getContainerSlot());
    }
}
