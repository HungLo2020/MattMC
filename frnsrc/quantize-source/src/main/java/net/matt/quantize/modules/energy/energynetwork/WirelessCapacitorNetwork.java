// Java
package net.matt.quantize.modules.energy.energynetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.matt.quantize.block.BlockEntity.WirelessCapacitorBlockEntity;

import java.util.HashSet;
import java.util.Set;

public class WirelessCapacitorNetwork {
    private final int networkAddress;
    private final Set<BlockPos> members = new HashSet<>();

    public WirelessCapacitorNetwork(int networkAddress) {
        this.networkAddress = networkAddress;
    }

    public void addMember(BlockPos pos) {
        members.add(pos);
    }

    public void removeMember(BlockPos pos) {
        members.remove(pos);
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    public void tick(Level level) {
        int totalEnergy = 0;
        int memberCount = 0;

        // Collect energy from all members
        for (BlockPos pos : members) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WirelessCapacitorBlockEntity capacitor &&
                    capacitor.getWirelessNetworkAddress() == networkAddress) {
                totalEnergy += capacitor.getEnergyStored();
                memberCount++;
            }
        }

        if (memberCount > 0) {
            int averageEnergy = totalEnergy / memberCount;

            // Distribute energy equally among members
            for (BlockPos pos : members) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof WirelessCapacitorBlockEntity capacitor &&
                        capacitor.getWirelessNetworkAddress() == networkAddress) {
                    int currentEnergy = capacitor.getEnergyStored();
                    int difference = averageEnergy - currentEnergy;

                    if (difference > 0) {
                        capacitor.getEnergyStorage().receiveEnergy(difference, false);
                    } else if (difference < 0) {
                        capacitor.getEnergyStorage().extractEnergy(-difference, false);
                    }
                }
            }
        }
    }
}