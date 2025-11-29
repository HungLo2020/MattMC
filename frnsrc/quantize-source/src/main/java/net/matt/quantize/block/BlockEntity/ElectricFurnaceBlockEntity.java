package net.matt.quantize.block.BlockEntity;

import net.matt.quantize.block.QBlockEntities;
import net.matt.quantize.gui.menu.ElectricFurnaceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.matt.quantize.block.block.MachineBlock;
import net.minecraft.world.level.block.Block;

public class ElectricFurnaceBlockEntity extends MachineBlockEntity implements MenuProvider, Container {
    public ElectricFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(QBlockEntities.ELECTRIC_FURNACE.get(), pos, state, 10000, 2);
        this.energyConsumptionRate = 20; // Set a reasonable energy consumption rate
        this.canReceiveEnergy = true;
        this.TICKS_PER_OPERATION = 200;
        this.energyTransferRate = 100;
        this.priority = 10; // Set a priority for energy transfer
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Electric Furnace");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new ElectricFurnaceMenu(id, playerInventory, this);
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide) {
            ItemStack input = getItem(0);
            ItemStack output = getItem(1);

            // Ensure maxProgress is set correctly
            if (getMaxProgress() != TICKS_PER_OPERATION) {
                setMaxProgress(TICKS_PER_OPERATION);
            }

            boolean wasActive = state.getValue(MachineBlock.ACTIVE);
            boolean isActive = !input.isEmpty() && canSmelt(input, output);

            // Update the active state if it has changed
            if (isActive != wasActive) {
                level.setBlock(pos, state.setValue(MachineBlock.ACTIVE, isActive), Block.UPDATE_ALL);
            }

            if (isActive) {
                if (this.energyStorage.getEnergyStored() >= energyConsumptionRate) {
                    this.energyStorage.extractEnergy(energyConsumptionRate, false);
                    setProgress(getProgress() + 1);

                    if (getProgress() >= getMaxProgress()) {
                        smeltItem(input, output);
                        setProgress(0);
                    }
                }
            } else {
                setProgress(0);
            }

            sendUpdate();
        }
    }

    private boolean canSmelt(ItemStack input, ItemStack output) {
        SmeltingRecipe recipe = getSmeltingRecipe(input);
        if (recipe == null) {
            //System.out.println("No valid recipe for input: " + input.getItem());
            return false;
        }

        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (result.isEmpty()) {
            //System.out.println("Recipe result is empty for input: " + input.getItem());
            return false;
        }

        if (output.isEmpty()) return true;
        if (!output.is(result.getItem())) {
            //System.out.println("Output slot contains a different item: " + output.getItem());
            return false;
        }
        if (output.getCount() + result.getCount() > output.getMaxStackSize()) {
            //System.out.println("Not enough space in output slot for: " + result.getItem());
            return false;
        }

        return true;
    }

    private void smeltItem(ItemStack input, ItemStack output) {
        SmeltingRecipe recipe = getSmeltingRecipe(input);
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

    private SmeltingRecipe getSmeltingRecipe(ItemStack input) {
        Container container = new SimpleContainer(input);
        SmeltingRecipe recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, container, level).orElse(null);
        if (recipe == null) {
            //System.out.println("No smelting recipe found for: " + input.getItem());
        }
        return recipe;
    }
}