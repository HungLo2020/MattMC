package com.github.alexthe666.alexsmobs.item;

import com.github.alexthe666.alexsmobs.entity.EntityLeafcutterAnt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public class ItemLeafcutterPupa extends Item {

    public ItemLeafcutterPupa(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        
        if (!world.isClientSide() && world.getBlockState(pos).getBlock() == Blocks.LEAFCUTTER_ANTHILL) {
            // Spawn a baby ant near the anthill
            EntityLeafcutterAnt ant = new EntityLeafcutterAnt(EntityType.LEAFCUTTER_ANT, world);
            ant.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
            ant.setBaby(true);
            world.addFreshEntity(ant);
            
            if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
                context.getItemInHand().shrink(1);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
