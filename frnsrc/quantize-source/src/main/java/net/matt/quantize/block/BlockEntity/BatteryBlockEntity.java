package net.matt.quantize.block.BlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.matt.quantize.block.QBlockEntities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;
import net.matt.quantize.gui.menu.BatteryMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.matt.quantize.modules.energy.energynetwork.EnergyNetworkHelper;

public class BatteryBlockEntity extends MachineBlockEntity implements MenuProvider, Container {
    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(QBlockEntities.BATTERY.get(), pos, state, 11000, 1);
        this.canSendEnergy = true; // Solar panels can send energy
        this.canReceiveEnergy = true; // Batteries can receive energy
        this.energyTransferRate = 100; // Set a reasonable energy transfer rate for solar panels
        this.priority = 60; // Set a priority for energy transfer
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Battery");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new BatteryMenu(id, playerInventory, this);
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide) {
            // Ensure the battery is part of the correct network
            if (network == null) {
                EnergyNetworkHelper.joinOrCreateNetwork(level, pos, this);
            }

            // Charge item in slot 0
            ItemStack stack = this.getItem(0);
            if (!stack.isEmpty()) {
                stack.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY).ifPresent(itemEnergy -> {
                    int extractable = this.energyStorage.extractEnergy(this.energyTransferRate, true);
                    if (extractable > 0) {
                        int accepted = itemEnergy.receiveEnergy(extractable, false);
                        this.energyStorage.extractEnergy(accepted, false);

                        // Save updated energy state to the ItemStack
                        CompoundTag tag = stack.getOrCreateTag();
                        tag.putInt("Energy", itemEnergy.getEnergyStored());
                        stack.setTag(tag);
                    }
                });
            }

            // Let the network handle energy transfer
            if (network != null) {
                network.tick(level);
            }

            sendUpdate();
        }
    }
}