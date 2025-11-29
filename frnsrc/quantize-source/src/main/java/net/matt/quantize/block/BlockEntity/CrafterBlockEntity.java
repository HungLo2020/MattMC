package net.matt.quantize.block.BlockEntity;

import net.matt.quantize.Quantize;
import net.matt.quantize.block.QBlockEntities;
import net.matt.quantize.block.block.CrafterBlock; // <-- for CRAFTING/TRIGGERED props
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class CrafterBlockEntity extends BlockEntity implements MenuProvider {

    // --- Debug cadence (logs once per second). Set to 1 for every tick.
    private static final int DEBUG_INTERVAL = 20;
    private int debugTicker = 0;

    // ==== PULSE (crafting/triggered) ====
    private static final int PULSE_TICKS = 20; // 1s at 20 tps
    private int pulseTicks = 0;

    // Handlers with setChanged() on edits
    private final ItemStackHandler input = new ItemStackHandler(9) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final ItemStackHandler pattern = new ItemStackHandler(9) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final ItemStackHandler output = new ItemStackHandler(9) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };

    // Expose a combined item handler capability (27 slots total)
    private final LazyOptional<IItemHandler> itemCap =
            LazyOptional.of(() -> new CombinedInvWrapper(input, pattern, output));

    // Side-specific caps (top/sides -> input, bottom -> output)
    private final LazyOptional<IItemHandler> inputCap  = LazyOptional.of(() -> input);
    private final LazyOptional<IItemHandler> outputCap = LazyOptional.of(() -> output);

    public CrafterBlockEntity(BlockPos pos, BlockState state) {
        super(QBlockEntities.CRAFTER.get(), pos, state);
    }

    // ---- Capability plumbing ----
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side == null) return itemCap.cast(); // players/GUI get full access

            if (side == Direction.DOWN) {
                return outputCap.cast(); // extract only
            } else {
                return inputCap.cast();  // insert only
            }
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
        inputCap.invalidate();
        outputCap.invalidate();
    }

    // ---- Save / Load NBT ----
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Input",   input.serializeNBT());
        tag.put("Pattern", pattern.serializeNBT());
        tag.put("Output",  output.serializeNBT());
        tag.putInt("PulseTicks", pulseTicks); // save pulse
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Input"))   input.deserializeNBT(tag.getCompound("Input"));
        if (tag.contains("Pattern")) pattern.deserializeNBT(tag.getCompound("Pattern"));
        if (tag.contains("Output"))  output.deserializeNBT(tag.getCompound("Output"));
        if (tag.contains("PulseTicks")) pulseTicks = tag.getInt("PulseTicks");
    }

    // ---- Accessors for the menu ----
    public ItemStackHandler getInput()   { return input; }
    public ItemStackHandler getPattern() { return pattern; }
    public ItemStackHandler getOutput()  { return output; }

    // ---- MenuProvider ----
    @Override public Component getDisplayName() {
        return Component.translatable("container.quantize.crafter");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
        return new net.matt.quantize.gui.menu.CrafterMenu(id, playerInv, this);
    }

    // ====== TICK ======
    public static void serverTick(Level level, BlockPos pos, BlockState state, CrafterBlockEntity be) {
        if (level.isClientSide) return;

        // Always tick the pulse so flags clear even when idle
        be.tickPulse(level, state);

        boolean doDebug = (++be.debugTicker % DEBUG_INTERVAL) == 0;

        // 1) If no pattern set, bail
        if (be.isPatternEmpty()) {
            if (doDebug) be.debug("no pattern; idle");
            return;
        }

        // Build a 3x3 crafting grid view of the current pattern
        CraftingContainer grid = be.buildPatternGrid();

        // 2) Find a vanilla crafting recipe that matches the pattern
        Optional<CraftingRecipe> recipeOpt = be.findRecipeFromPattern(grid);
        if (recipeOpt.isEmpty()) {
            if (doDebug) be.debug("did not find a recipe for current pattern");
            return;
        }

        CraftingRecipe recipe = recipeOpt.get();
        ItemStack result = recipe.assemble(grid, level.registryAccess());

        if (result.isEmpty()) {
            if (doDebug) be.debug("found recipe " + recipe.getId() + " but result was empty");
            return;
        } else if (doDebug) {
            be.debug("found recipe: " + recipe.getId() + " -> " + result.getCount() + "x " + result.getItem().toString());
        }

        // 4) Check we can output the result
        if (!be.canOutput(result)) {
            if (doDebug) be.debug("cannot output result (no space/merge)");
            return;
        }

        // 5) Check we have enough inputs to pay for the pattern
        if (!be.canConsumeInputsForPattern()) {
            if (doDebug) be.debug("missing required input items to match pattern");
            return;
        }

        // 6) Consume inputs and output the item
        be.consumeInputsForPattern();
        be.insertIntoOutput(result);
        be.setChanged();

        // 7) Start 1s pulse for blockstate flags
        be.startCraftPulse(level);

        if (doDebug) be.debug("crafted " + result.getCount() + "x " + result.getItem().toString());
    }

    private void debug(String msg) {
        Quantize.LOGGER.info("[Crafter {}] {}", this.worldPosition, msg);
    }

    // Build a fresh 3x3 CraftingContainer from the pattern slots
    private CraftingContainer buildPatternGrid() {
        TransientCraftingContainer grid = new TransientCraftingContainer(DUMMY_MENU, 3, 3);
        for (int i = 0; i < 9; i++) {
            grid.setItem(i, pattern.getStackInSlot(i).copy());
        }
        return grid;
    }

    // ====== RECIPE MATCHING ======
    private boolean isPatternEmpty() {
        for (int i = 0; i < pattern.getSlots(); i++) {
            if (!pattern.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }

    private Optional<CraftingRecipe> findRecipeFromPattern(CraftingContainer grid) {
        if (this.level == null) return Optional.empty();
        return this.level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, this.level);
    }

    // ====== OUTPUT HANDLING ======
    private boolean canOutput(ItemStack stack) {
        ItemStack toInsert = stack.copy();
        for (int i = 0; i < output.getSlots(); i++) {
            ItemStack in = output.getStackInSlot(i);
            if (in.isEmpty()) return true;
            if (ItemStack.isSameItemSameTags(in, toInsert)
                    && in.getCount() < Math.min(in.getMaxStackSize(), output.getSlotLimit(i))) {
                return true;
            }
        }
        return false;
    }

    private void insertIntoOutput(ItemStack stack) {
        ItemStack toInsert = stack.copy();

        // Merge into existing stacks
        for (int i = 0; i < output.getSlots() && !toInsert.isEmpty(); i++) {
            ItemStack in = output.getStackInSlot(i);
            if (!in.isEmpty() && ItemStack.isSameItemSameTags(in, toInsert)) {
                int canMove = Math.min(toInsert.getCount(),
                        Math.min(in.getMaxStackSize(), output.getSlotLimit(i)) - in.getCount());
                if (canMove > 0) {
                    in.grow(canMove);
                    toInsert.shrink(canMove);
                    output.setStackInSlot(i, in);
                }
            }
        }
        // Then empty slots
        for (int i = 0; i < output.getSlots() && !toInsert.isEmpty(); i++) {
            if (output.getStackInSlot(i).isEmpty()) {
                int move = Math.min(toInsert.getCount(), Math.min(toInsert.getMaxStackSize(), output.getSlotLimit(i)));
                ItemStack place = toInsert.copy();
                place.setCount(move);
                output.setStackInSlot(i, place);
                toInsert.shrink(move);
            }
        }
    }

    // ====== INPUT CONSUMPTION ACCORDING TO PATTERN ======
    private boolean canConsumeInputsForPattern() {
        for (int i = 0; i < 9; i++) {
            ItemStack need = pattern.getStackInSlot(i);
            if (need.isEmpty()) continue;

            int idx = findMatchingInputSlot(need);
            if (idx == -1) return false;
        }
        return true;
    }

    private void consumeInputsForPattern() {
        for (int i = 0; i < 9; i++) {
            ItemStack need = pattern.getStackInSlot(i);
            if (need.isEmpty()) continue;

            int idx = findMatchingInputSlot(need);
            if (idx != -1) {
                ItemStack in = input.getStackInSlot(idx).copy();
                in.shrink(1);
                input.setStackInSlot(idx, in);
            }
        }
    }

    /** Find an input slot index that has an item equal to 'need' (same item+tags) with count >= 1. */
    private int findMatchingInputSlot(ItemStack need) {
        for (int i = 0; i < input.getSlots(); i++) {
            ItemStack in = input.getStackInSlot(i);
            if (!in.isEmpty() && ItemStack.isSameItemSameTags(in, need) && in.getCount() >= 1) {
                return i;
            }
        }
        return -1;
    }

    // ==== Pulse helpers ====
    private void startCraftPulse(Level level) {
        pulseTicks = PULSE_TICKS;
        BlockState st = level.getBlockState(worldPosition);
        if (st.getBlock() instanceof CrafterBlock) {
            st = st.setValue(CrafterBlock.CRAFTING, true).setValue(CrafterBlock.TRIGGERED, true);
            level.setBlock(worldPosition, st, 3);
        }
        setChanged();
    }

    /** Keep blockstate flags in sync with the countdown; auto-clear at 0. */
    private void tickPulse(Level level, BlockState state) {
        if (pulseTicks > 0) {
            pulseTicks--;
            // ensure flags are on during pulse
            if ((!state.getValue(CrafterBlock.CRAFTING)) || (!state.getValue(CrafterBlock.TRIGGERED))) {
                BlockState st = state.setValue(CrafterBlock.CRAFTING, true).setValue(CrafterBlock.TRIGGERED, true);
                level.setBlock(worldPosition, st, 3);
            }
            if (pulseTicks == 0) {
                BlockState st = level.getBlockState(worldPosition);
                if (st.getBlock() instanceof CrafterBlock &&
                        (st.getValue(CrafterBlock.CRAFTING) || st.getValue(CrafterBlock.TRIGGERED))) {
                    st = st.setValue(CrafterBlock.CRAFTING, false).setValue(CrafterBlock.TRIGGERED, false);
                    level.setBlock(worldPosition, st, 3);
                }
                setChanged();
            }
        }
    }

    // Dummy menu to satisfy TransientCraftingContainer constructor
    private static final AbstractContainerMenu DUMMY_MENU = new AbstractContainerMenu(null, -1) {
        @Override public boolean stillValid(Player player) { return false; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    };
}
