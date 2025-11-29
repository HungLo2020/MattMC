package net.matt.quantize.block.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.EntityBlock;
import net.matt.quantize.block.BlockEntity.BatteryBlockEntity;
import net.matt.quantize.block.QBlockEntities;
import net.minecraft.world.level.Level;

public class BatteryBlock extends MachineBlock implements EntityBlock {
    public BatteryBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BatteryBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == QBlockEntities.BATTERY.get() ?
                (BlockEntityTicker<T>) (lvl, pos, st, entity) -> {
                    if (entity instanceof BatteryBlockEntity batteryEntity) {
                        batteryEntity.tick(lvl, pos, st);
                    }
                } : null;
    }
}