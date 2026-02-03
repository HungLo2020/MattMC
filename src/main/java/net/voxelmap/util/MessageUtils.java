package net.voxelmap.util;

import net.voxelmap.VoxelConstants;

public final class MessageUtils {
    private static final boolean debug = false;

    private MessageUtils() {}

    public static void chatInfo(String s) { VoxelConstants.getVoxelMapInstance().sendPlayerMessageOnMainThread(s); }

    public static void printDebug(String line) { if (debug) {
        VoxelConstants.getLogger().warn(line);
    } }
}