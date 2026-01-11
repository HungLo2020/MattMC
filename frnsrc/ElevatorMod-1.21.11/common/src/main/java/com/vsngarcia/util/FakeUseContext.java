package com.vsngarcia.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;


public class FakeUseContext extends BlockPlaceContext {
    public FakeUseContext(Player player, InteractionHand handIn, BlockHitResult hit) {
        super(new UseOnContext(player, handIn, hit));
    }

    @Override
    public BlockPos getClickedPos() {
        return getHitResult().getBlockPos();
    }
}
