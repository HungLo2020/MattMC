package net.matt.quantize.item.item;

import net.matt.quantize.modules.energy.IEnergyItem;
import net.matt.quantize.utils.DurabilityEnergyBarUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;

import java.util.List;


public class EnergyRocketItem extends Item implements IEnergyItem {

    private static final int ENERGY_COST = 500; // Energy required per boost
    private static final int CAPACITY = 10000; // Maximum energy capacity
    private static final int TRANSFER = 1000; // Energy transfer rate

    public EnergyRocketItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getCapacity() {
        return CAPACITY;
    }

    @Override
    public int getTransfer() {
        return TRANSFER;
    }

    @Override
    public boolean isWirelessChargeable() {
        return false; // Allow wireless charging
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return IEnergyItem.super.createEnergyCapabilityProvider(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE; // No specific animation
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            stack.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
                if (energy.extractEnergy(ENERGY_COST, true) >= ENERGY_COST) {
                    energy.extractEnergy(ENERGY_COST, false); // Consume energy

                    if (player.isFallFlying()) {
                        // Spawn a firework rocket entity (unarmed) to boost the player
                        ItemStack fakeRocket = new ItemStack(Items.FIREWORK_ROCKET);
                        FireworkRocketEntity firework = new FireworkRocketEntity(level, stack, player);
                        level.addFreshEntity(firework);
                    }

                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.0F);
                }
            });
        }

        return InteractionResultHolder.success(stack);
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