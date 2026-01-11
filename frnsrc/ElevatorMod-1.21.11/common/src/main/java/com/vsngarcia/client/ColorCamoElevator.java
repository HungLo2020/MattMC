package com.vsngarcia.client;

import com.vsngarcia.ElevatorBlockBase;
import com.vsngarcia.level.ElevatorBlockEntityBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

public class ColorCamoElevator implements BlockColor {

    @Override
    public int getColor(BlockState state, BlockAndTintGetter lightReader, BlockPos pos, int tintIndex) {
        if (lightReader == null || pos == null) {
            return -1;
        }

        if (state.getBlock() instanceof ElevatorBlockBase &&
                lightReader.getBlockEntity(pos) instanceof ElevatorBlockEntityBase tile) {
            if (tile.getHeldState() != null) {
                return Minecraft.getInstance().getBlockColors().getColor(
                        tile.getHeldState(),
                        lightReader,
                        pos,
                        tintIndex
                );
            }
        }
        return -1;
    }
}
