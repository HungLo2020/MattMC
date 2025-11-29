package net.matt.quantize.gui.menu;

import net.matt.quantize.Quantize;
import net.matt.quantize.block.BlockEntity.CrafterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CrafterMenu extends AbstractContainerMenu {
    // === Registration (same pattern as your other menus) ===
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Quantize.MOD_ID);

    public static final RegistryObject<MenuType<CrafterMenu>> CRAFTER_MENU =
            MENUS.register("crafter",
                    () -> IForgeMenuType.create((windowId, inv, buf) -> {
                        BlockPos pos = buf.readBlockPos();
                        Level level = inv.player.level();
                        BlockEntity be = level.getBlockEntity(pos);
                        if (be instanceof CrafterBlockEntity crafter) {
                            return new CrafterMenu(windowId, inv, crafter);
                        }
                        // Fallback to avoid crash if BE missing client-side
                        return new CrafterMenu(windowId, inv, null);
                    }));

    // === Instance ===
    private final CrafterBlockEntity be;

    // Layout (vanilla 176x166)
    private static final int SLOT = 18;

    // You set grids a bit higher, with middle 1 slot (18px) lower
    private static final int BASE_GRID_Y   = 8;

    private static final int LEFT_GRID_X   = 8;
    private static final int MIDDLE_GRID_X = 62;
    private static final int RIGHT_GRID_X  = 116;

    private static final int LEFT_GRID_Y   = BASE_GRID_Y;
    private static final int RIGHT_GRID_Y  = BASE_GRID_Y;
    private static final int MIDDLE_GRID_Y = BASE_GRID_Y + SLOT;

    // ----- Slot index ranges (filled in constructor in the same order we add slots) -----
    // Machine (0..26)
    private final int INPUT_START   = 0;
    private final int INPUT_END     = INPUT_START + 9;   // [0,9)
    private final int PATTERN_START = INPUT_END;         // 9
    private final int PATTERN_END   = PATTERN_START + 9; // [9,18)
    private final int OUTPUT_START  = PATTERN_END;       // 18
    private final int OUTPUT_END    = OUTPUT_START + 9;  // [18,27)

    // Player inventory (27 slots) then hotbar (9 slots)
    private final int PLAYER_START  = OUTPUT_END;        // 27
    private final int INV_START     = PLAYER_START;      // 27
    private final int INV_END       = INV_START + 27;    // [27,54)
    private final int HOTBAR_START  = INV_END;           // 54
    private final int HOTBAR_END    = HOTBAR_START + 9;  // [54,63)
    private final int PLAYER_END    = HOTBAR_END;        // 63 (exclusive)

    // Main constructor used by both server (BE#createMenu) and client (factory above)
    public CrafterMenu(int id, Inventory playerInv, CrafterBlockEntity be) {
        super(CRAFTER_MENU.get(), id);
        this.be = be;

        // Bind to BE handlers if present
        if (be != null) {
            addGrid(be.getInput(),   LEFT_GRID_X,   LEFT_GRID_Y,   false);
            addGrid(be.getPattern(), MIDDLE_GRID_X, MIDDLE_GRID_Y, false);
            addGrid(be.getOutput(),  RIGHT_GRID_X,  RIGHT_GRID_Y,  true);
        }

        // Player inventory (standard positions)
        addPlayerInventory(playerInv, 8, 84);
        addPlayerHotbar(playerInv, 8, 142);
    }

    private void addGrid(ItemStackHandler handler, int startX, int startY, boolean readOnly) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int idx = c + r * 3;
                int x = startX + c * SLOT;
                int y = startY + r * SLOT;
                this.addSlot(readOnly
                        ? new OutputSlot(handler, idx, x, y)
                        : new SlotItemHandler(handler, idx, x, y));
            }
        }
    }

    private void addPlayerInventory(Inventory inv, int startX, int startY) {
        for (int r = 0; r < 3; ++r) {
            for (int c = 0; c < 9; ++c) {
                this.addSlot(new Slot(inv, c + r * 9 + 9, startX + c * SLOT, startY + r * SLOT));
            }
        }
    }

    private void addPlayerHotbar(Inventory inv, int startX, int y) {
        for (int c = 0; c < 9; ++c) {
            this.addSlot(new Slot(inv, c, startX + c * SLOT, y));
        }
    }

    // ===== SHIFT-CLICK BEHAVIOR =====
    // - From player (27..62) -> only into INPUT (0..8)
    // - From machine (0..26)  -> into player (27..62)
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack ret = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ret = stack.copy();

        // From player inventory/hotbar to machine INPUT
        if (index >= PLAYER_START && index < PLAYER_END) {
            // Only try to move into INPUT slots (0..8)
            if (!this.moveItemStackTo(stack, INPUT_START, INPUT_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= INPUT_START && index < OUTPUT_END) {
            // From any machine slot (input/pattern/output) -> player inventory/hotbar
            if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_END, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Out of range (should not happen)
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return ret;
    }

    @Override
    public boolean stillValid(Player player) {
        // Accept for now; you can add distance checks against be.getBlockPos() if you want.
        return true;
    }

    // Read-only output slot
    private static class OutputSlot extends SlotItemHandler {
        public OutputSlot(ItemStackHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
