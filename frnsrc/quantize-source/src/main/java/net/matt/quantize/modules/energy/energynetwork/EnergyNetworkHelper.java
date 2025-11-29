// src/main/java/net/matt/quantize/modules/energy/EnergyNetworkHelper.java
package net.matt.quantize.modules.energy.energynetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.matt.quantize.block.BlockEntity.MachineBlockEntity;
import net.matt.quantize.block.BlockEntity.WirelessCapacitorBlockEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class EnergyNetworkHelper {

    private static final HashMap<Integer, EnergyNetwork> wirelessNetworks = new HashMap<>();

    public static void joinOrCreateWirelessNetwork(Level level, BlockPos pos, WirelessCapacitorBlockEntity machine) {
        int address = machine.getWirelessNetworkAddress();
        EnergyNetwork network = wirelessNetworks.computeIfAbsent(address, k -> new EnergyNetwork());

        // Check for existing networks with the same address and merge them
        Set<EnergyNetwork> networksToMerge = new HashSet<>();
        for (EnergyNetwork existingNetwork : wirelessNetworks.values()) {
            if (existingNetwork != network && existingNetwork.containsAddress(level, address)) {
                networksToMerge.add(existingNetwork);
            }
        }

        for (EnergyNetwork otherNetwork : networksToMerge) {
            network.merge(otherNetwork, level);
            wirelessNetworks.values().removeIf(n -> n == otherNetwork); // Remove merged network
        }

        network.addMember(pos);
        machine.setNetwork(network);
        wirelessNetworks.put(address, network); // Ensure the map points to the updated network
    }

    public static void joinOrCreateNetwork(Level level, BlockPos pos, MachineBlockEntity machine) {
        // Find adjacent networks
        Set<EnergyNetwork> adjacentNetworks = new HashSet<>();
        for (var dir : net.minecraft.core.Direction.values()) {
            BlockEntity be = level.getBlockEntity(pos.relative(dir));
            if (be instanceof MachineBlockEntity mb && mb.getNetwork() != null) {
                adjacentNetworks.add(mb.getNetwork());
            }
        }
        EnergyNetwork network;
        if (adjacentNetworks.isEmpty()) {
            network = new EnergyNetwork();
        } else {
            network = adjacentNetworks.iterator().next();
            for (EnergyNetwork other : adjacentNetworks) {
                if (other != network) {
                    network.merge(other, level);
                }
            }
        }
        network.addMember(pos);
        machine.setNetwork(network);
    }

    public static void leaveNetwork(Level level, BlockPos pos, MachineBlockEntity machine) {
        EnergyNetwork network = machine.getNetwork();
        if (network != null) {
            network.removeMember(pos);
            machine.setNetwork(null);
        }
    }
}