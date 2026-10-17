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
}
