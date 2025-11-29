package net.matt.quantize.modules.energy;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public interface IEnergyItem {
    int getCapacity();
    int getTransfer();
    boolean isWirelessChargeable();

    default ICapabilityProvider createEnergyCapabilityProvider(ItemStack stack) {
        int capacity = getCapacity();
        int transfer = getTransfer();
        return new ICapabilityProvider() {
            private final LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> new IEnergyStorage() {
                @Override
                public int receiveEnergy(int maxReceive, boolean simulate) {
                    int energy = getEnergyStored();
                    int energyReceived = Math.min(capacity - energy, Math.min(transfer, maxReceive));
                    if (!simulate && energyReceived > 0) {
                        setEnergyStored(energy + energyReceived);
                    }
                    return energyReceived;
                }
                @Override
                public int extractEnergy(int maxExtract, boolean simulate) {
                    int energy = getEnergyStored();
                    int energyExtracted = Math.min(energy, Math.min(transfer, maxExtract));
                    if (!simulate && energyExtracted > 0) {
                        setEnergyStored(energy - energyExtracted);
                    }
                    return energyExtracted;
                }
                @Override
                public int getEnergyStored() {
                    CompoundTag tag = stack.getOrCreateTag();
                    return tag.getInt("Energy");
                }
                @Override
                public int getMaxEnergyStored() {
                    return capacity;
                }
                @Override
                public boolean canExtract() { return true; }
                @Override
                public boolean canReceive() { return true; }
                private void setEnergyStored(int value) {
                    CompoundTag tag = stack.getOrCreateTag();
                    tag.putInt("Energy", Math.max(0, Math.min(capacity, value)));
                }
            });
            @Override
            public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable net.minecraft.core.Direction side) {
                if (cap == ForgeCapabilities.ENERGY) {
                    return energy.cast();
                }
                return LazyOptional.empty();
            }
        };
    }
}