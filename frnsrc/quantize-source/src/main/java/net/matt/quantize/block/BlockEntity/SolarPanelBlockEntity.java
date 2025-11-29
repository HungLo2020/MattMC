package net.matt.quantize.block.BlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.matt.quantize.block.QBlockEntities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;
import net.matt.quantize.gui.menu.SolarPanelMenu;
import net.minecraft.world.MenuProvider;
import net.matt.quantize.utils.BlockPosUtils;

public class SolarPanelBlockEntity extends MachineBlockEntity implements MenuProvider {
    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(QBlockEntities.SOLAR_PANEL.get(), pos, state, 11000, 0);
        this.canSendEnergy = true; // Solar panels can send energy
        this.energyTransferRate = 100; // Set a reasonable energy transfer rate for solar panels
        this.priority = 100; // Set a priority for energy transfer
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Solar Panel");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new SolarPanelMenu(id, playerInventory, this);
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level != null) {
            if (BlockPosUtils.isDay(level) && BlockPosUtils.canSeeSky(level, pos)) {
                this.energyStorage.receiveEnergy(50, false);
            }
            sendUpdate();
            //pushEnergy(level, pos, new java.util.HashSet<>(), null);
        }
    }
}