package net.matt.quantize.block.BlockEntity;

import net.matt.quantize.block.QBlockEntities;
import net.matt.quantize.gui.menu.WirelessCapacitorMenu;
import net.matt.quantize.modules.energy.energynetwork.WirelessCapacitorNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

public class WirelessCapacitorBlockEntity extends MachineBlockEntity implements MenuProvider, Container {
    private int wirelessNetworkAddress; // Specific to this entity
    private WirelessCapacitorNetwork wirelessNetwork; // Reference to the WirelessCapacitorNetwork

    // Static map to manage networks by address
    private static final Map<Integer, WirelessCapacitorNetwork> networkMap = new HashMap<>();

    public WirelessCapacitorBlockEntity(BlockPos pos, BlockState state) {
        super(QBlockEntities.WIRELESS_CAPACITOR.get(), pos, state, 11000, 1);
        this.canSendEnergy = true;
        this.canReceiveEnergy = true;
        this.energyTransferRate = 100;
        this.priority = 60;
        this.wirelessNetworkAddress = 0; // Default value
    }

    public int getWirelessNetworkAddress() {
        return wirelessNetworkAddress;
    }

    public void setWirelessNetworkAddress(int address) {
        this.wirelessNetworkAddress = address;
        setChanged();
    }

    public WirelessCapacitorNetwork getWirelessNetwork() {
        return wirelessNetwork;
    }

    public void setWirelessNetwork(WirelessCapacitorNetwork network) {
        this.wirelessNetwork = network;
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("WirelessNetworkAddress", wirelessNetworkAddress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("WirelessNetworkAddress")) {
            this.wirelessNetworkAddress = tag.getInt("WirelessNetworkAddress");
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Wireless Capacitor");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new WirelessCapacitorMenu(id, playerInventory, this);
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide) {
            // Ensure the block entity is part of the correct network
            if (wirelessNetwork == null) {
                wirelessNetwork = networkMap.computeIfAbsent(wirelessNetworkAddress, WirelessCapacitorNetwork::new);
                wirelessNetwork.addMember(pos);
            }

            // Equalize energy using the network
            if (wirelessNetwork != null) {
                wirelessNetwork.tick(level);
            }

            // Charge item in slot 0
            ItemStack stack = this.getItem(0);
            if (!stack.isEmpty()) {
                stack.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY).ifPresent(itemEnergy -> {
                    int extractable = this.energyStorage.extractEnergy(this.energyTransferRate, true);
                    if (extractable > 0) {
                        int accepted = itemEnergy.receiveEnergy(extractable, false);
                        this.energyStorage.extractEnergy(accepted, false);

                        CompoundTag tag = stack.getOrCreateTag();
                        tag.putInt("Energy", itemEnergy.getEnergyStored());
                        stack.setTag(tag);
                    }
                });
            }

            sendUpdate();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!level.isClientSide) {
            wirelessNetwork = networkMap.computeIfAbsent(wirelessNetworkAddress, WirelessCapacitorNetwork::new);
            wirelessNetwork.addMember(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (!level.isClientSide && wirelessNetwork != null) {
            wirelessNetwork.removeMember(worldPosition);
            if (wirelessNetwork.isEmpty()) {
                networkMap.remove(wirelessNetworkAddress);
            }
        }
        super.setRemoved();
    }
}