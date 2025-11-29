package net.matt.quantize.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class BlockPosUtils {

    public static boolean canSeeSky(Level level, BlockPos pos) {
        for (int y = pos.getY() + 1; y < level.getMaxBuildHeight(); y++) {
            BlockPos abovePos = new BlockPos(pos.getX(), y, pos.getZ());
            if (!level.isEmptyBlock(abovePos)) {
                return false; // Obstruction found
            }
        }
        return true; // No obstructions
    }

    public static boolean isDay(Level level) {
        return level.dimensionType().hasSkyLight() && level.getDayTime() % 24000 < 12000;
    }
}