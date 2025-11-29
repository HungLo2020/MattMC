package net.matt.quantize.block.BlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.common.util.LazyOptional;
import net.matt.quantize.Quantize;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;
import net.matt.quantize.modules.energy.ModEnergyStorage;
import net.matt.quantize.modules.energy.energynetwork.EnergyNetworkHelper;
import net.matt.quantize.modules.energy.energynetwork.EnergyNetwork;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class MachineBlockEntity extends BlockEntity {

    protected NonNullList<ItemStack> inventory;

    protected final ModEnergyStorage energyStorage;
    private final LazyOptional<EnergyStorage> energyCapability;

    protected int progress = 0; // Progress for crafting or processing tasks
    protected int maxProgress = 0; // Default max progress, can be overridden in subclasses
    protected int energyCapacity = 10000; // Default energy capacity, can be overridden in subclasses
    protected int energyTransferRate = 100; // Default energy transfer rate, can be overridden in subclasses
    protected int energyConsumptionRate = 0; // Default energy consumption rate, can be overridden in subclasses
    protected boolean canSendEnergy = false;
    protected boolean canReceiveEnergy = false;
    protected int TICKS_PER_OPERATION = 0; // Default ticks per operation, can be overridden in subclasses
    protected boolean shouldJoinNetwork = true; // Default value for joining network, can be overridden in subclasses
    protected int priority = 1; // Default priority for energy transfer, can be overridden in subclasses

    public MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int energyCapacity, int inventorySize) {
        super(type, pos, state);
        this.energyStorage = new ModEnergyStorage(energyCapacity, energyTransferRate) {
            @Override
            public void onEnergyChanged() {
                setChanged();
                // Add synchronization logic here (e.g., send packets to clients)
            }
        };
        this.energyCapability = LazyOptional.of(() -> energyStorage);
        this.inventory = NonNullList.withSize(inventorySize, ItemStack.EMPTY); // Initialize inventory
    }

    // Getters for energy properties
    public int getEnergyStored() {
        return this.energyStorage.getEnergyStored();
    }
    public int getMaxEnergyStored() {
        return this.energyStorage.getMaxEnergyStored();
    }
    public int getEnergyCapacity() {
        return energyCapacity;
    }
    public int getEnergyTransferRate() {
        return energyTransferRate;
    }
    public int getEnergyConsumptionRate() {
        return energyConsumptionRate;
    }
    public int getProgress() {
        return progress;
    }
    public int getMaxProgress() {
        return maxProgress;
    }
    public int setProgress(int progress) {
        this.progress = progress;
        setChanged();
        return this.progress; // Return the updated progress value
    }
    public void setMaxProgress(int maxProgress) {
        this.maxProgress = maxProgress;
        setChanged();
    }
    public ModEnergyStorage getEnergyStorage() {
        return this.energyStorage;
    }
    public int getPriority() {
        return this.priority;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide) {
            sendUpdate();
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        // Save inventory
        CompoundTag inventoryTag = new CompoundTag();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                stack.save(itemTag);
                inventoryTag.put("Slot" + i, itemTag);
            }
        }
        tag.put("Inventory", inventoryTag);

        // Save energy
        CompoundTag energyTag = new CompoundTag();
        energyTag.put("Energy", this.energyStorage.serializeNBT());
        tag.put(Quantize.MOD_ID, energyTag);
        //System.out.println("Saved Inventory: " + inventoryTag);

        // save boolean flags
        tag.putBoolean("CanSendEnergy", this.canSendEnergy);
        tag.putBoolean("CanReceiveEnergy", this.canReceiveEnergy);

        //save progress
        tag.putInt("Progress", this.progress);
        tag.putInt("MaxProgress", this.maxProgress);
        //System.out.println("[DEBUG] saveAdditional: " + this.worldPosition + " canSend: " + canSendEnergy + " canReceive: " + canReceiveEnergy + " energy: " + this.energyStorage.getEnergyStored());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        // Load inventory
        if (tag.contains("Inventory")) {
            CompoundTag inventoryTag = tag.getCompound("Inventory");
            for (int i = 0; i < inventory.size(); i++) {
                String key = "Slot" + i;
                if (inventoryTag.contains(key)) {
                    inventory.set(i, ItemStack.of(inventoryTag.getCompound(key)));
                } else {
                    inventory.set(i, ItemStack.EMPTY);
                }
            }
        }

        // Load energy
        if (tag.contains(Quantize.MOD_ID)) {
            CompoundTag energyTag = tag.getCompound(Quantize.MOD_ID);
            if (energyTag.contains("Energy")) {
                this.energyStorage.deserializeNBT(energyTag.get("Energy"));
            }
        }

        // Load energy flags
        if (tag.contains("CanSendEnergy")) {
            this.canSendEnergy = tag.getBoolean("CanSendEnergy");
        }
        if (tag.contains("CanReceiveEnergy")) {
            this.canReceiveEnergy = tag.getBoolean("CanReceiveEnergy");
        }

        // Load progress
        if (tag.contains("Progress")) {
            this.progress = tag.getInt("Progress");
        }
        if (tag.contains("MaxProgress")) {
            this.maxProgress = tag.getInt("MaxProgress");
        }

        //System.out.println("Loaded Inventory: " + inventory);
        sendUpdate(); // Synchronize with the client
        //System.out.println("[DEBUG] load: " + this.worldPosition + " canSend: " + canSendEnergy + " canReceive: " + canReceiveEnergy + " energy: " + this.energyStorage.getEnergyStored());
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    protected void sendUpdate() {
        setChanged();

        if(this.level != null)
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        //System.out.println("Sending update for energy: " + this.energyStorage.getEnergyStored());
    }

    public boolean canSendEnergy() {
        return this.canSendEnergy;
    }
    public boolean canReceiveEnergy() {
        return this.canReceiveEnergy;
    }

    public boolean stillValid(Player player) {
        return true; // Allow interaction with the block entity
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt) {
        load(pkt.getTag());
    }

    // Inventory management methods
    public int getContainerSize() {
        return inventory.size(); }
    public boolean isEmpty() {
        setChanged();
        return inventory.isEmpty();
    }
    public ItemStack getItem(int slot) {
        setChanged();
        return inventory.get(slot);
    }
    public ItemStack removeItem(int slot, int amount) {
        setChanged();
        ItemStack stack = inventory.get(slot);
        if (!stack.isEmpty()) {
            ItemStack result = stack.split(amount); // Split the stack
            if (stack.isEmpty()) {
                inventory.set(slot, ItemStack.EMPTY); // Clear the slot if empty
            }
            setChanged();
            return result;
        }
        return ItemStack.EMPTY;
    }
    public ItemStack removeItemNoUpdate(int slot) {
        setChanged();
        ItemStack stack = inventory.get(slot);
        inventory.set(slot, ItemStack.EMPTY); // Clear the slot without updating
        return stack;
    }
    public void setItem(int slot, ItemStack stack) {
        setChanged();
        inventory.set(slot, stack);
    }
    public void clearContent() {
        setChanged();
        for (int i = 0; i < inventory.size(); i++) {
            inventory.set(i, ItemStack.EMPTY); // Clear each slot
        }
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!level.isClientSide && shouldJoinNetwork) {
            EnergyNetworkHelper.joinOrCreateNetwork(level, worldPosition, this);
        }
    }

    @Override
    public void setRemoved() {
        if (!level.isClientSide && network != null) {
            network.removeMember(worldPosition);
        }
        super.setRemoved();
    }

    protected EnergyNetwork network;

    public EnergyNetwork getNetwork() {
        return network;
    }

    public void setNetwork(EnergyNetwork network) {
        this.network = network;
    }
}