package net.matt.quantize.gui.menu;

import net.matt.quantize.Quantize;
import net.matt.quantize.block.BlockEntity.PulverizerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class PulverizerMenu extends AbstractContainerMenu {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Quantize.MOD_ID);

    private final ContainerLevelAccess access;

    public static final RegistryObject<MenuType<PulverizerMenu>> PULVERIZER_MENU =
            MENUS.register("pulverizer_menu",
                    () -> IForgeMenuType.create((id, inv, data) -> {
                        BlockPos pos = data.readBlockPos();
                        PulverizerBlockEntity blockEntity = (PulverizerBlockEntity) inv.player.getCommandSenderWorld().getBlockEntity(pos);
                        return new PulverizerMenu(id, inv, blockEntity);
                    }));
    private final PulverizerBlockEntity blockEntity;

    public PulverizerMenu(int id, Inventory playerInventory, PulverizerBlockEntity blockEntity) {
        super(PULVERIZER_MENU.get(), id);
        this.blockEntity = blockEntity;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());

        // Block entity inventory slots
        this.addSlot(new Slot(blockEntity, 0, 53, 26)); // Input slot
        this.addSlot(new Slot(blockEntity, 1, 116, 35)); // Output slot

        // Player inventory slots
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Hotbar slots
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity != null && blockEntity.getBlockPos().distSqr(player.blockPosition()) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            ItemStack copy = stack.copy();

            //System.out.println("Shift-clicked item: " + stack.getItem());

            if (index < 2) { // Block entity inventory
                if (!this.moveItemStackTo(stack, 2, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else { // Player inventory
                if (!this.moveItemStackTo(stack, 0, 1, false)) { // Input slot
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            return copy;
        }
        return ItemStack.EMPTY;
    }

    public int getEnergy() {
        return blockEntity != null ? blockEntity.getEnergyStored() : 0;
    }

    public int getMaxEnergy() {
        return blockEntity != null ? blockEntity.getMaxEnergyStored() : 0;
    }

    public int getProgress() {
        return blockEntity != null ? blockEntity.getProgress() : 0;
    }

    public int getMaxProgress() {
        return blockEntity != null ? blockEntity.getMaxProgress() : 0;
    }

}