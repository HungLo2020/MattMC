package net.matt.quantize.item.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface IFlightItem {
    boolean canEnableFlight(Player player, ItemStack stack);
}