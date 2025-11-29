package net.matt.quantize.block.block;

import net.matt.quantize.block.BlockEntity.HydroponicsBasinBlockEntity;
import net.matt.quantize.block.QBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class HydroponicsBasinBlock extends MachineBlock implements EntityBlock {
    public HydroponicsBasinBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HydroponicsBasinBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == QBlockEntities.HYDROPONICS_BASIN.get() ?
                (BlockEntityTicker<T>) (lvl, pos, st, entity) -> {
                    if (entity instanceof HydroponicsBasinBlockEntity HydroponicsBasinEntity) {
                        HydroponicsBasinEntity.tick(lvl, pos, st);
                    }
                } : null;
    }
}