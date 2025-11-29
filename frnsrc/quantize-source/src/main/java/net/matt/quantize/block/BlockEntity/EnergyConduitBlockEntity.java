package net.matt.quantize.block.BlockEntity;

import net.matt.quantize.block.QBlockEntities;
import net.matt.quantize.gui.menu.EnergyConduitMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class EnergyConduitBlockEntity extends MachineBlockEntity implements MenuProvider {
    public EnergyConduitBlockEntity(BlockPos pos, BlockState state) {
        super(QBlockEntities.ENERGY_CONDUIT.get(), pos, state, 11000, 0);
        this.canSendEnergy = true;
        this.canReceiveEnergy = true;
        this.energyTransferRate = 100;
        this.priority = 40; // Set a priority for energy transfer
        setChanged();
        sendUpdate();
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Energy Conduit");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new EnergyConduitMenu(id, playerInventory, this);
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level != null && network != null) {
            network.tick(level);
            sendUpdate();
        }
    }
}