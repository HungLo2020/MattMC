package com.github.alexthe666.alexsmobs.tileentity;

import com.github.alexthe666.alexsmobs.entity.EntityLeafcutterAnt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class TileEntityLeafcutterAnthill extends BlockEntity {
    
    private boolean hasQueen = false;

    public TileEntityLeafcutterAnthill(BlockPos pos, BlockState state) {
        super(BlockEntityType.LEAFCUTTER_ANTHILL, pos, state);
    }

    public boolean hasQueen() {
        return hasQueen;
    }

    public void releaseQueens() {
        // Stub - spawn queen ant
        if (level != null && !level.isClientSide()) {
            EntityLeafcutterAnt queen = net.minecraft.world.entity.EntityType.LEAFCUTTER_ANT.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (queen != null) {
                queen.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5);
                queen.setQueen(true);
                level.addFreshEntity(queen);
                hasQueen = false;
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.hasQueen = input.getBooleanOr("HasQueen", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("HasQueen", this.hasQueen);
    }

    public boolean isNearFire() {
        return false;
    }

    public boolean isFullOfAnts() {
        return false;
    }

    public void tryEnterHive(EntityLeafcutterAnt ant, boolean hasLeaf) {
        // Stub implementation
    }
}
