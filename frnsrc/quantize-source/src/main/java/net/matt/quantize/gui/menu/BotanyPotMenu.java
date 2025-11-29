package net.matt.quantize.gui.menu;

import net.matt.quantize.Quantize;
import net.matt.quantize.block.BlockEntity.BotanyPotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.inventory.DataSlot;

public class BotanyPotMenu extends AbstractContainerMenu {

    // client mirrors (filled via DataSlot#set on the client)
    private int clientProgress = 0;
    private int clientMaxProgress = 0;
    private int clientEnergy = 0;
    private int clientMaxEnergy = 0;

    // ---------- registry ----------
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Quantize.MOD_ID);

    public static final RegistryObject<MenuType<BotanyPotMenu>> BOTANY_POT_MENU =
            MENUS.register("botany_pot_menu",
                    () -> IForgeMenuType.create((id, inv, data) -> {
                        BlockPos pos = data.readBlockPos();
                        BotanyPotBlockEntity be = (BotanyPotBlockEntity) inv.player.getCommandSenderWorld().getBlockEntity(pos);
                        return new BotanyPotMenu(id, inv, be);
                    }));

    // ---------- slot indices ----------
    private static final int TE_SEED_SLOT      = 0;
    private static final int TE_SOIL_SLOT      = 1;
    private static final int TE_OUTPUT_START   = 2;    // outputs now start after seed+soil
    private static final int TE_OUTPUT_COUNT   = 12;   // 12 outputs (2..13)
    private static final int TE_OUTPUT_END_EXC = TE_OUTPUT_START + TE_OUTPUT_COUNT; // 14
    private static final int TE_SLOTS          = TE_OUTPUT_END_EXC;                 // total tile slots = 14

    private static final int PLAYER_INV_START = TE_SLOTS;                 // 14
    private static final int PLAYER_INV_COUNT = 27;
    private static final int HOTBAR_START     = PLAYER_INV_START + PLAYER_INV_COUNT; // 41
    private static final int HOTBAR_COUNT     = 9;
    private static final int PLAYER_END       = HOTBAR_START + HOTBAR_COUNT;         // 50

    private final ContainerLevelAccess access;
    private final BotanyPotBlockEntity blockEntity;

    // take-only output slot
    private static class OutputSlot extends Slot {
        public OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }

    public BotanyPotMenu(int id, Inventory playerInventory, BotanyPotBlockEntity blockEntity) {
        super(BOTANY_POT_MENU.get(), id);
        this.blockEntity = blockEntity;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());

        // --- inputs ---
        this.addSlot(new Slot(blockEntity, TE_SEED_SLOT, 44, 22)); // seed
        this.addSlot(new Slot(blockEntity, TE_SOIL_SLOT, 44, 48)); // soil (below seed)

        // --- outputs grid (3 rows x 4 cols), top-left is output #1 (inv index 2) ---
        final int originX = 80;
        final int originY = 17;
        final int step    = 18;

        for (int i = 0; i < TE_OUTPUT_COUNT; i++) {
            int row = i / 4;
            int col = i % 4;
            int x = originX + col * step;
            int y = originY + row * step;

            int invIndex = TE_OUTPUT_START + i; // 2..13 in BE inventory
            this.addSlot(new OutputSlot(blockEntity, invIndex, x, y));
        }

        // --- player inventory (3 rows)
        for (int r = 0; r < 3; ++r) {
            for (int c = 0; c < 9; ++c) {
                this.addSlot(new Slot(playerInventory, c + r * 9 + 9,
                        8 + c * 18, 84 + r * 18));
            }
        }

        // --- hotbar ---
        for (int c = 0; c < 9; ++c) {
            this.addSlot(new Slot(playerInventory, c, 8 + c * 18, 142));
        }

        // --- sync fields ---
        this.addDataSlot(new DataSlot() {
            @Override public int get() { return blockEntity.getProgress(); }
            @Override public void set(int value) { clientProgress = value; }
        });
        this.addDataSlot(new DataSlot() {
            @Override public int get() { return blockEntity.getMaxProgress(); }
            @Override public void set(int value) { clientMaxProgress = value; }
        });
        this.addDataSlot(new DataSlot() {
            @Override public int get() { return blockEntity.getEnergyStored(); }
            @Override public void set(int value) { clientEnergy = value; }
        });
        this.addDataSlot(new DataSlot() {
            @Override public int get() { return blockEntity.getMaxEnergyStored(); }
            @Override public void set(int value) { clientMaxEnergy = value; }
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity != null && blockEntity.getBlockPos().distSqr(player.blockPosition()) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack ret = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            ret = stack.copy();

            if (index < TE_SLOTS) {
                // from tile -> to player inventory/hotbar
                if (!this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // from player -> try to place into seed/soil inputs (0..2 exclusive -> slots 0 and 1)
                if (!this.moveItemStackTo(stack, TE_SEED_SLOT, TE_SOIL_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }

        return ret;
    }

    // mirrored values on client, real on server
    public int getProgress() {
        return (blockEntity != null && blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide)
                ? clientProgress
                : (blockEntity != null ? blockEntity.getProgress() : 0);
    }
    public int getMaxProgress() {
        return (blockEntity != null && blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide)
                ? clientMaxProgress
                : (blockEntity != null ? blockEntity.getMaxProgress() : 0);
    }
    public int getEnergy() {
        return (blockEntity != null && blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide)
                ? clientEnergy
                : (blockEntity != null ? blockEntity.getEnergyStored() : 0);
    }
    public int getMaxEnergy() {
        return (blockEntity != null && blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide)
                ? clientMaxEnergy
                : (blockEntity != null ? blockEntity.getMaxEnergyStored() : 0);
    }
}
