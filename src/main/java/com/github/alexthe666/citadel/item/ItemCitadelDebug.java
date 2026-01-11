package com.github.alexthe666.citadel.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ItemCitadelDebug extends Item {

    public ItemCitadelDebug(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level worldIn, Player playerIn, InteractionHand handIn) {
        ItemStack itemStackIn = playerIn.getItemInHand(handIn);
        playerIn.getCooldowns().addCooldown(this, 15);
        return InteractionResult.PASS;
    }

}
