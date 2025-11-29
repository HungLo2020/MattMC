package net.matt.quantize.block.BlockEntity;

import net.matt.quantize.block.QBlockEntities;
import net.matt.quantize.block.block.MachineBlock;
import net.matt.quantize.gui.menu.HydroponicsBasinMenu;
import net.matt.quantize.recipes.BotanyRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class HydroponicsBasinBlockEntity extends MachineBlockEntity implements MenuProvider, Container {

    // inputs: 0 = seed, 1 = soil; outputs: 2..13 (12 slots)
    private static final int SLOT_SEED        = 0;
    private static final int SLOT_SOIL        = 1;
    private static final int SLOT_OUT_START   = 2;
    private static final int SLOT_OUT_END_EXC = 14; // loops 2..13

    private static final double GROWTH_SPEED_MULT = 2.0;  // base machine speedup
    public double UPGRADE_GROWTH_SPEED_MULT = 1.0;        // set by upgrades
    private double FINAL_GROWTH_SPEED_MULT = 1.0;

    private static final int DEFAULT_TICKS_PER_OPERATION = 200;

    public HydroponicsBasinBlockEntity(BlockPos pos, BlockState state) {
        // inventory size = 14 (0..13)
        super(QBlockEntities.HYDROPONICS_BASIN.get(), pos, state, 10000, 14);
        this.energyConsumptionRate = 20;
        this.canReceiveEnergy = true;
        this.TICKS_PER_OPERATION = DEFAULT_TICKS_PER_OPERATION;
        this.energyTransferRate = 100;
        this.priority = 10;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Hydroponics Basin");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new HydroponicsBasinMenu(id, playerInventory, this);
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;

        // combine base + upgrades
        FINAL_GROWTH_SPEED_MULT = GROWTH_SPEED_MULT * UPGRADE_GROWTH_SPEED_MULT;

        final ItemStack seed = getItem(SLOT_SEED);
        final ItemStack soil = getItem(SLOT_SOIL);
        final BotanyRecipe recipe = getBotanyRecipe(seed, soil);

        // sync maxProgress to recipe (faster if multipliers > 1)
        final int baseTicks = (recipe != null ? Math.max(1, recipe.getGrowthTicks()) : DEFAULT_TICKS_PER_OPERATION);
        final int targetMax = Math.max(1, (int) Math.ceil(baseTicks / FINAL_GROWTH_SPEED_MULT));
        if (getMaxProgress() != targetMax) setMaxProgress(targetMax);

        final boolean hasInputs = !seed.isEmpty() && !soil.isEmpty() && recipe != null;
        final boolean hasRoom   = hasOutputRoom();
        final boolean hasEnergy = this.energyStorage.getEnergyStored() >= energyConsumptionRate;

        final boolean isActive = hasInputs && hasRoom && hasEnergy;
        final boolean wasActive = state.getValue(MachineBlock.ACTIVE);
        if (isActive != wasActive) {
            level.setBlock(pos, state.setValue(MachineBlock.ACTIVE, isActive), Block.UPDATE_ALL);
        }

        if (isActive) {
            this.energyStorage.extractEnergy(energyConsumptionRate, false);
            setProgress(getProgress() + 1);

            if (getProgress() >= getMaxProgress()) {
                harvest(recipe);
                setProgress(0);
                sendUpdate();
            }
        } else {
            setProgress(0);
        }
    }

    /* ========================= Harvest logic ========================= */

    private void harvest(BotanyRecipe recipe) {
        if (recipe == null || level == null) return;

        RandomSource rng = level.getRandom();
        List<BotanyRecipe.Drop> drops = recipe.getDrops();

        for (BotanyRecipe.Drop d : drops) {
            if (rng.nextFloat() > d.chance()) continue;

            int amount = Mth.nextInt(rng, d.min(), d.max());
            if (amount <= 0) continue;

            ItemStack base = d.stack().copy();
            int maxStack = Math.min(base.getMaxStackSize(), base.getItem().getMaxStackSize());
            int remaining = amount;

            while (remaining > 0) {
                int toPlace = Math.min(maxStack, remaining);
                ItemStack piece = base.copy();
                piece.setCount(toPlace);
                remaining -= toPlace;

                ItemStack leftover = insertIntoOutputs(piece);
                if (!leftover.isEmpty()) {
                    Block.popResource(level, worldPosition, leftover);
                    break;
                }
            }
        }

        setChanged();
    }

    private ItemStack insertIntoOutputs(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        // pass 1: merge
        for (int i = SLOT_OUT_START; i < SLOT_OUT_END_EXC; i++) {
            ItemStack existing = getItem(i);
            if (existing.isEmpty()) continue;
            if (!ItemStack.isSameItemSameTags(existing, stack)) continue;

            int canMove = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
            if (canMove <= 0) continue;

            existing.grow(canMove);
            stack.shrink(canMove);
            if (stack.isEmpty()) return ItemStack.EMPTY;
        }

        // pass 2: empty slots
        for (int i = SLOT_OUT_START; i < SLOT_OUT_END_EXC; i++) {
            ItemStack existing = getItem(i);
            if (!existing.isEmpty()) continue;

            int toPlace = Math.min(stack.getCount(), stack.getMaxStackSize());
            ItemStack placed = stack.copy();
            placed.setCount(toPlace);
            setItem(i, placed);

            stack.shrink(toPlace);
            if (stack.isEmpty()) return ItemStack.EMPTY;
        }

        return stack;
    }

    private boolean hasOutputRoom() {
        for (int i = SLOT_OUT_START; i < SLOT_OUT_END_EXC; i++) {
            ItemStack st = getItem(i);
            if (st.isEmpty()) return true;
            if (st.getCount() < st.getMaxStackSize()) return true;
        }
        return false;
    }

    /* ========================= Recipe lookup ========================= */

    private BotanyRecipe getBotanyRecipe(ItemStack seed, ItemStack soil) {
        if (level == null || seed.isEmpty() || soil.isEmpty()) return null;
        // BotanyRecipe.matches expects: slot 0 = seed, slot 1 = soil
        SimpleContainer container = new SimpleContainer(seed, soil);
        return level.getRecipeManager()
                .getRecipeFor(BotanyRecipe.Type.INSTANCE, container, level)
                .orElse(null);
    }
}
