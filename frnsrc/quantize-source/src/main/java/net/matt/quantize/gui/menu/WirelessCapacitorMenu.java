package net.matt.quantize.gui.menu;

import net.matt.quantize.Quantize;
import net.matt.quantize.block.BlockEntity.WirelessCapacitorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class WirelessCapacitorMenu extends AbstractContainerMenu {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Quantize.MOD_ID);

    public static final RegistryObject<MenuType<WirelessCapacitorMenu>> WIRELESS_CAPACITOR_MENU =
            MENUS.register("wireless_capacitor_menu",
                    () -> IForgeMenuType.create((id, inv, data) -> {
                        BlockPos pos = data.readBlockPos();
                        BlockEntity blockEntity = inv.player.getCommandSenderWorld().getBlockEntity(pos);
                        if (blockEntity instanceof WirelessCapacitorBlockEntity) {
                            //System.out.println("BatteryBlockEntity found at " + pos);
                            return new WirelessCapacitorMenu(id, inv, (WirelessCapacitorBlockEntity) blockEntity);
                        }
                        //System.err.println("Failed to retrieve BatteryBlockEntity at " + pos);
                        return null;
                    }));
    private final WirelessCapacitorBlockEntity blockEntity;

    public WirelessCapacitorMenu(int id, Inventory playerInventory, WirelessCapacitorBlockEntity blockEntity) {
        super(WIRELESS_CAPACITOR_MENU.get(), id);
        this.blockEntity = blockEntity;
        this.addSlot(new Slot(blockEntity, 0, 80, 35));

        // Add player inventory slots (3 rows of 9)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Add player hotbar slots (1 row of 9)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        // Add custom slots for the block entity (if needed)
        // Example: this.addSlot(new Slot(container, 0, 80, 35));
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity != null && blockEntity.getBlockPos().distSqr(player.blockPosition()) <= 64;
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        // Implement logic for shift-clicking items between inventory and container
        return net.minecraft.world.item.ItemStack.EMPTY; // Placeholder implementation
    }

    public int getEnergy() {
        return blockEntity != null ? blockEntity.getEnergyStored() : 0;
    }

    public int getMaxEnergy() {
        return blockEntity != null ? blockEntity.getMaxEnergyStored() : 0;
    }
}