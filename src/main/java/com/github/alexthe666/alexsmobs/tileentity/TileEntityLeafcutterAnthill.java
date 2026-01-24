package com.github.alexthe666.alexsmobs.tileentity;

import com.github.alexthe666.alexsmobs.entity.EntityLeafcutterAnt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

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
            EntityLeafcutterAnt queen = new EntityLeafcutterAnt(net.minecraft.world.entity.EntityType.LEAFCUTTER_ANT, level);
            queen.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5);
            queen.setQueen(true);
            level.addFreshEntity(queen);
            hasQueen = false;
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("HasQueen")) {
            this.hasQueen = tag.getBoolean("HasQueen");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("HasQueen", this.hasQueen);
    }
}
