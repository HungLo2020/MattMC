package net.matt.quantize.block.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.EntityBlock;
import net.matt.quantize.block.BlockEntity.SolarPanelBlockEntity;
import net.matt.quantize.block.QBlockEntities;
import net.minecraft.world.level.Level;

public class SolarPanelBlock extends MachineBlock implements EntityBlock {
    public SolarPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarPanelBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == QBlockEntities.SOLAR_PANEL.get() ?
                (BlockEntityTicker<T>) (lvl, pos, st, entity) -> {
                    if (entity instanceof SolarPanelBlockEntity solarPanelEntity) {
                        solarPanelEntity.tick(lvl, pos, st);
                    }
                } : null;
    }
}