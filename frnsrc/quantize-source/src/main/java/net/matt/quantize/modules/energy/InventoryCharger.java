package net.matt.quantize.modules.energy;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

public class InventoryCharger {

    public static void chargeInventory(Player player, ItemStack energySource, boolean OnlyChargeWireless) {
        energySource.getCapability(ForgeCapabilities.ENERGY).ifPresent(energySourceStorage -> {
            // Charge main inventory
            player.getInventory().items.forEach(stack -> tryChargeItem(stack, energySourceStorage, OnlyChargeWireless));
            // Charge armor slots
            player.getInventory().armor.forEach(stack -> tryChargeItem(stack, energySourceStorage, OnlyChargeWireless));
            // Charge offhand slot
            tryChargeItem(player.getInventory().offhand.get(0), energySourceStorage, OnlyChargeWireless);
        });
    }

    private static void tryChargeItem(ItemStack stack, IEnergyStorage energySourceStorage, boolean OnlyChargeWireless) {
        if (stack.getItem() instanceof IEnergyItem energyItem) {
            // Check if the item is wireless chargeable if required
            if (!OnlyChargeWireless || energyItem.isWirelessChargeable()) {
                stack.getCapability(ForgeCapabilities.ENERGY).ifPresent(itemEnergy -> {
                    int energyToTransfer = Math.min(energySourceStorage.extractEnergy(100, true), itemEnergy.receiveEnergy(100, true));
                    if (energyToTransfer > 0) {
                        energySourceStorage.extractEnergy(energyToTransfer, false);
                        itemEnergy.receiveEnergy(energyToTransfer, false);
                    }
                });
            }
        }
    }
}