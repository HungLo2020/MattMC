package net.matt.quantize.item.item;

import net.matt.quantize.Quantize;
import net.matt.quantize.modules.energy.IEnergyItem;
import net.matt.quantize.utils.DurabilityEnergyBarUtil;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.matt.quantize.modules.energy.InventoryCharger;

import java.util.List;

@Mod.EventBusSubscriber(modid = Quantize.MOD_ID)
public class AppleWatchItem extends Item implements IEnergyItem, IFlightItem {

    public static final int CAPACITY = 1000000;
    public static final int TRANSFER = 1000;
    public static final int ENERGY_USE = 1;

    public AppleWatchItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isWirelessChargeable() {
        return true; // AppleWatchItem supports wireless charging
    }

    @Override
    public int getCapacity() { return CAPACITY; }

    @Override
    public int getTransfer() { return TRANSFER; }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return IEnergyItem.super.createEnergyCapabilityProvider(stack);
    }

    @Override
    public boolean canEnableFlight(Player player, ItemStack stack) {
        // Check if the item is in the offhand slot
        if (!player.getInventory().offhand.get(0).equals(stack)) {
            return false;
        }

        return stack.getCapability(ForgeCapabilities.ENERGY)
                .map(energy -> {
                    if (player.getAbilities().flying && energy.extractEnergy(ENERGY_USE, true) >= ENERGY_USE) {
                        energy.extractEnergy(ENERGY_USE, false); // Consume energy
                        return true;
                    }
                    return !player.getAbilities().flying; // Allow flight if not flying
                })
                .orElse(false);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent event) {
        Player player = event.player;

        // Check if the item in the offhand slot is an AppleWatchItem
        ItemStack offhandItem = player.getInventory().offhand.get(0);
        if (offhandItem.getItem() instanceof AppleWatchItem) {

            // Charge the inventory with the offhand item
            InventoryCharger.chargeInventory(player, offhandItem, false);

            // Generate energy during the day if conditions are met
            if (!player.level().isClientSide
                    && player.level().isDay()
                    && player.level().canSeeSky(player.blockPosition())
                    && player.level().dimensionType().hasSkyLight()) {
                offhandItem.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
                    if (energy.getEnergyStored() < energy.getMaxEnergyStored()) {
                        energy.receiveEnergy(10, false); // Generate 10 FE per tick
                    }
                });
            }

            // Apply effects based on energy stored in the offhand item
            offhandItem.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
                if (energy.getEnergyStored() >= 10) {
                    if (player.getHealth() < player.getMaxHealth() && !player.hasEffect(MobEffects.REGENERATION)) {
                        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, false));
                        energy.extractEnergy(10, false); // Consume 10 energy
                    }
                }
                if (energy.getEnergyStored() >= 10) {
                    if (!player.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
                        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 2, true, false));
                        energy.extractEnergy(10, false); // Consume 10 energy
                    }
                }
                if (energy.getEnergyStored() >= 10) {
                    if (player.isUnderWater() && !player.hasEffect(MobEffects.WATER_BREATHING)) {
                        player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100, 0, true, false));
                        energy.extractEnergy(10, false); // Consume 10 energy
                    }
                }
            });
        }
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