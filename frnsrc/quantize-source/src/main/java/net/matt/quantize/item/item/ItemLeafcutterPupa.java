package net.matt.quantize.item.item;

import net.matt.quantize.block.QBlocks;
import net.matt.quantize.entities.QEntities;
import net.matt.quantize.entities.mobs.EntityLeafcutterAnt;
import net.matt.quantize.tags.QTags;
import net.matt.quantize.block.BlockEntity.TileEntityLeafcutterAnthill;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class ItemLeafcutterPupa extends Item {

    public ItemLeafcutterPupa(Properties props) {
        super(props);
    }

    private static final int leafcutterAntColonySize = 10;

    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos blockpos = context.getClickedPos();
        BlockState blockstate = world.getBlockState(blockpos);
        if (blockstate.is(QTags.LEAFCUTTER_PUPA_USABLE_ON) && world.getBlockState(blockpos.below()).is(QTags.LEAFCUTTER_PUPA_USABLE_ON)) {
            Player playerentity = context.getPlayer();
            if(playerentity != null){
                playerentity.gameEvent(GameEvent.BLOCK_PLACE);
            }
            world.playSound(playerentity, blockpos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!world.isClientSide) {
                world.setBlock(blockpos, QBlocks.LEAFCUTTER_ANTHILL.get().defaultBlockState(), 11);
                world.setBlock(blockpos.below(), QBlocks.LEAFCUTTER_ANT_CHAMBER.get().defaultBlockState(), 11);
                BlockEntity tileentity = world.getBlockEntity(blockpos);
                if (tileentity instanceof TileEntityLeafcutterAnthill) {
                    TileEntityLeafcutterAnthill beehivetileentity = (TileEntityLeafcutterAnthill)tileentity;
                    int j = Math.min(3, leafcutterAntColonySize);
                    for(int k = 0; k < j; ++k) {
                        EntityLeafcutterAnt beeentity = new EntityLeafcutterAnt(QEntities.LEAFCUTTER_ANT.get(), world);
                        beeentity.setQueen(k == 0);
                        beehivetileentity.tryEnterHive(beeentity, false, 100);
                    }
                }
                if (playerentity != null && !playerentity.isCreative()) {
                    context.getItemInHand().shrink(1);
                }
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }
}
