package net.matt.quantize.block.BlockEntity;

import net.matt.quantize.block.QBlockEntities;
import net.matt.quantize.gui.menu.PulverizerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.matt.quantize.recipes.PulverizingRecipe;
import net.matt.quantize.block.block.MachineBlock;
import net.minecraft.world.level.block.Block;

public class PulverizerBlockEntity extends MachineBlockEntity implements MenuProvider, Container {
    public PulverizerBlockEntity(BlockPos pos, BlockState state) {
        super(QBlockEntities.PULVERIZER.get(), pos, state, 10000, 2);
        this.energyConsumptionRate = 20; // Set a reasonable energy consumption rate
        this.canReceiveEnergy = true;
        this.TICKS_PER_OPERATION = 200;
        this.energyTransferRate = 100;
        this.priority = 10; // Set a priority for energy transfer
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Pulverizer");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new PulverizerMenu(id, playerInventory, this);
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide) {
            boolean wasActive = state.getValue(MachineBlock.ACTIVE);
            boolean isActive = !getItem(0).isEmpty() && canPulverize(getItem(0), getItem(1));
            if (getMaxProgress() != TICKS_PER_OPERATION) {
                setMaxProgress(TICKS_PER_OPERATION);
            }

            if (isActive != wasActive) {
                level.setBlock(pos, state.setValue(MachineBlock.ACTIVE, isActive), Block.UPDATE_ALL);
            }

            if (isActive) {
                if (this.energyStorage.getEnergyStored() >= energyConsumptionRate) {
                    this.energyStorage.extractEnergy(energyConsumptionRate, false);
                    setProgress(getProgress() + 1);

                    if (getProgress() >= getMaxProgress()) {
                        pulverizeItem(getItem(0), getItem(1));
                        setProgress(0);
                    }
                }
            } else {
                setProgress(0);
            }

            sendUpdate();
        }
    }

    private boolean canPulverize(ItemStack input, ItemStack output) {
        PulverizingRecipe recipe = getPulverizingRecipe(input);
        if (recipe == null) {
            return false;
        }

        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (result.isEmpty()) {
            return false;
        }

        if (output.isEmpty()) return true;
        if (!output.is(result.getItem())) {
            return false;
        }
        if (output.getCount() + result.getCount() > output.getMaxStackSize()) {
            return false;
        }

        return true;
    }

    private void pulverizeItem(ItemStack input, ItemStack output) {
        PulverizingRecipe recipe = getPulverizingRecipe(input);
        if (recipe != null) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (output.isEmpty()) {
                setItem(1, result.copy());
            } else {
                output.grow(result.getCount());
            }
            input.shrink(1); // Consume one input item
        }
        setChanged();
    }

    private PulverizingRecipe getPulverizingRecipe(ItemStack input) {
        SimpleContainer container = new SimpleContainer(input);
        PulverizingRecipe recipe = level.getRecipeManager()
                .getRecipeFor(PulverizingRecipe.Type.INSTANCE, container, level)
                .orElse(null);

        if (recipe == null) {
            //System.out.println("No matching recipe found for input: " + input);
        } else {
            //System.out.println("Found recipe for input: " + input + ", output: " + recipe.getResultItem(level.registryAccess()));
        }

        return recipe;
    }
}