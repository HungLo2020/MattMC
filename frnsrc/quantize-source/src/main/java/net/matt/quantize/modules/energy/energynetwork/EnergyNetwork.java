// src/main/java/net/matt/quantize/modules/energy/energynetwork/EnergyNetwork.java
package net.matt.quantize.modules.energy.energynetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.matt.quantize.block.BlockEntity.MachineBlockEntity;
import net.matt.quantize.block.BlockEntity.WirelessCapacitorBlockEntity;

import java.util.*;

public class EnergyNetwork {
    private final Set<BlockPos> members = new HashSet<>();

    public void addMember(BlockPos pos) {
        members.add(pos);
    }

    public void removeMember(BlockPos pos) {
        members.remove(pos);
    }

    public void tick(Level level) {
        //System.out.println("Ticking EnergyNetwork with members: " + members.size());

        Set<MachineBlockEntity> senders = new HashSet<>();
        List<MachineBlockEntity> receivers = new ArrayList<>();

        for (BlockPos pos : members) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MachineBlockEntity machine) {
                if (machine.canSendEnergy() && machine.getEnergyStored() > 0) {
                    senders.add(machine);
                }
                if (machine.canReceiveEnergy() && machine.getEnergyStored() < machine.getMaxEnergyStored()) {
                    receivers.add(machine);
                }
            }
        }

        // Sort receivers by priority (lowest value first)
        receivers.sort(Comparator.comparingInt(MachineBlockEntity::getPriority));

        for (MachineBlockEntity sender : senders) {
            int available = sender.getEnergyStored();
            if (available <= 0) continue;

            for (MachineBlockEntity receiver : receivers) {
                if (sender == receiver) continue;
                int needed = receiver.getMaxEnergyStored() - receiver.getEnergyStored();
                if (needed <= 0) continue;

                int transfer = Math.min(sender.getEnergyTransferRate(), Math.min(available, needed));
                if (transfer > 0) {
                    int accepted = receiver.getEnergyStorage().receiveEnergy(transfer, false);
                    sender.getEnergyStorage().extractEnergy(accepted, false);
                    available -= accepted;
                    if (available <= 0) break;
                }
            }
        }

        // --- Energy Equalization for same-priority, send+receive machines ---
        Map<Integer, List<MachineBlockEntity>> equalizeGroups = new HashMap<>();
        for (BlockPos pos : members) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MachineBlockEntity machine) {
                if (machine.canSendEnergy() && machine.canReceiveEnergy()) {
                    equalizeGroups.computeIfAbsent(machine.getPriority(), k -> new ArrayList<>()).add(machine);
                }
            }
        }

        for (List<MachineBlockEntity> group : equalizeGroups.values()) {
            if (group.size() < 2) continue;
            int totalEnergy = group.stream().mapToInt(MachineBlockEntity::getEnergyStored).sum();
            int avgEnergy = totalEnergy / group.size();

            // First, extract excess energy from machines above average
            int pool = 0;
            for (MachineBlockEntity machine : group) {
                int current = machine.getEnergyStored();
                if (current > avgEnergy) {
                    int toExtract = current - avgEnergy;
                    int extracted = machine.getEnergyStorage().extractEnergy(toExtract, false);
                    pool += extracted;
                }
            }
            // Then, fill up machines below average from the pool
            for (MachineBlockEntity machine : group) {
                int current = machine.getEnergyStored();
                if (current < avgEnergy && pool > 0) {
                    int toFill = avgEnergy - current;
                    int accepted = machine.getEnergyStorage().receiveEnergy(Math.min(toFill, pool), false);
                    pool -= accepted;
                }
            }
        }
    }

    public void merge(EnergyNetwork other, Level level) {
        for (BlockPos pos : other.members) {
            this.members.add(pos);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof net.matt.quantize.block.BlockEntity.MachineBlockEntity machine) {
                machine.setNetwork(this);
            }
        }
        other.members.clear();
    }

    public boolean containsAddress(Level level, int address) {
        for (BlockPos pos : members) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WirelessCapacitorBlockEntity machine) {
                if (machine.getWirelessNetworkAddress() == address) {
                    return true;
                }
            }
        }
        return false;
    }
}