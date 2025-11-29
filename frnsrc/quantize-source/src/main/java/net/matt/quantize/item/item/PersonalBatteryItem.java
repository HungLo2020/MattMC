package net.matt.quantize.item.item;

import net.matt.quantize.Quantize;
import net.matt.quantize.modules.energy.IEnergyItem;
import net.matt.quantize.utils.DurabilityEnergyBarUtil;
import net.matt.quantize.modules.energy.InventoryCharger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Mod.EventBusSubscriber(modid = Quantize.MOD_ID)
public class PersonalBatteryItem extends Item implements IEnergyItem {

    public static final int CAPACITY = 100000;
    public static final int TRANSFER = 1000;
    public static final int ENERGY_USE = 1;

    public PersonalBatteryItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getCapacity() { return CAPACITY; }

    @Override
    public int getTransfer() { return TRANSFER; }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return IEnergyItem.super.createEnergyCapabilityProvider(stack);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent event) {
        Player player = event.player;

        // Check if the item in the offhand slot is an AppleWatchItem
        ItemStack offhandItem = player.getInventory().offhand.get(0);
        if (offhandItem.getItem() instanceof PersonalBatteryItem) {

            InventoryCharger.chargeInventory(player, offhandItem, false);

        }
    }

    @Override
    public boolean isWirelessChargeable() {
        return false; // JetpackItem does not support wireless charging
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return DurabilityEnergyBarUtil.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return DurabilityEnergyBarUtil.getBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return DurabilityEnergyBarUtil.getBarColor(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        stack.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
            int currentEnergy = energy.getEnergyStored();
            int maxEnergy = energy.getMaxEnergyStored();
            tooltip.add(Component.translatable("tooltip.quantize.jetpack.energy", currentEnergy, maxEnergy));
        });
    }
}