package net.matt.quantize.item.item;

import net.matt.quantize.utils.FlightManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.matt.quantize.Quantize;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import net.matt.quantize.modules.energy.IEnergyItem;
import net.matt.quantize.utils.DurabilityEnergyBarUtil;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.entity.EquipmentSlot;

@Mod.EventBusSubscriber(modid = Quantize.MOD_ID)
public class JetpackItem extends ArmorItem implements IEnergyItem, IFlightItem{

    public static final int CAPACITY = 10000;
    public static final int TRANSFER = 1000;
    public static final int ENERGY_USE = 1;

    public JetpackItem(ArmorMaterial material, Item.Properties properties) {
        super(material, ArmorItem.Type.CHESTPLATE, properties);
    }

    @Override
    public int getCapacity() { return CAPACITY; }

    @Override
    public boolean isWirelessChargeable() {
        return false; // JetpackItem does not support wireless charging
    }

    @Override
    public int getTransfer() { return TRANSFER; }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return IEnergyItem.super.createEnergyCapabilityProvider(stack);
    }

    @Override
    public boolean canEnableFlight(Player player, ItemStack stack) {
        //Quantize.LOGGER.info("Checking canEnableFlight for JetpackItem");

        // Debug the chestplate slot
        ItemStack chestplate = player.getInventory().armor.get(2);
        //Quantize.LOGGER.info("Chestplate slot contains: " + chestplate.getItem());

        // Use ItemStack.is to compare item types
        if (!chestplate.is(stack.getItem())) {
            //Quantize.LOGGER.info("Jetpack is not equipped in the chestplate slot");
            return false;
        }

        return stack.getCapability(ForgeCapabilities.ENERGY)
                .map(energy -> {
                    boolean canFly = player.getAbilities().flying && energy.extractEnergy(ENERGY_USE, true) >= ENERGY_USE;
                    //Quantize.LOGGER.info("Energy check for JetpackItem: " + canFly);
                    if (canFly) {
                        energy.extractEnergy(ENERGY_USE, false);
                    }
                    return canFly || !player.getAbilities().flying;
                })
                .orElse(false);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent event) {

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