package net.matt.quantize.utils;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public class DurabilityEnergyBarUtil {
    // Returns true if the bar should be shown (not full)
    public static boolean isBarVisible(ItemStack stack) {
        IEnergyStorage energy = getEnergyStorage(stack);
        return energy != null && energy.getEnergyStored() < energy.getMaxEnergyStored();
    }

    // Returns the bar width (0-13) based on energy percent
    public static int getBarWidth(ItemStack stack) {
        IEnergyStorage energy = getEnergyStorage(stack);
        if (energy == null) return 0;
        float percent = (float) energy.getEnergyStored() / energy.getMaxEnergyStored();
        return Math.round(13.0F * percent);
    }

    // Returns the bar color (standard green to red)
    public static int getBarColor(ItemStack stack) {
        IEnergyStorage energy = getEnergyStorage(stack);
        if (energy == null) return 0xFFFFFF;
        float percent = (float) energy.getEnergyStored() / energy.getMaxEnergyStored();
        // Standard MC durability bar color calculation
        int i = Math.round(255.0F * (1.0F - percent));
        int j = Math.round(255.0F * percent);
        return (i << 16) | (j << 8);
    }

    // Helper to get the energy storage from the stack
    private static IEnergyStorage getEnergyStorage(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.ENERGY)
                .resolve()
                .orElse(null);
    }
}