package net.voxelmap.util;

import net.voxelmap.MapSettingsManager;
import net.voxelmap.VoxelConstants;
import java.util.Objects;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;

public class MapUtils {
    private static volatile MapSettingsManager options;
    private static Random slimeRandom = new Random();
    private static String lastSeed;
    private static long lastSeedLong;
    private static int lastSlimeX;
    private static int lastSlimeZ;
    private static boolean isSlimeChunk;

    public static void reset() {
        options = VoxelConstants.getVoxelMapInstance().getMapOptions();
    }

    public static int doSlimeAndGrid(int color24, ClientLevel world, int mcX, int mcZ) {
        MapSettingsManager settings = options;
        if (settings == null) {
            // The live-map worker can begin calculating while the world/map
            // objects are still being connected.  A missing settings snapshot
            // is not a rendering capability failure; keep the source pixel
            // unchanged until the normal reset() publication completes.
            try {
                settings = VoxelConstants.getVoxelMapInstance().getMapOptions();
                options = settings;
            } catch (RuntimeException ignored) {
                return color24;
            }
            if (settings == null) {
                return color24;
            }
        }
        if (settings.slimeChunks && isSlimeChunk(mcX, mcZ)) {
            color24 = ColorUtils.colorAdder(0x7D00FF00, color24);
        }

        if (settings.chunkGrid) {
            if (mcX % 512 != 0 && mcZ % 512 != 0) {
                if (mcX % 16 == 0 || mcZ % 16 == 0) {
                    color24 = ColorUtils.colorAdder(0x7D000000, color24);
                }
            } else {
                color24 = ColorUtils.colorAdder(0x7DFF0000, color24);
            }
        }

        return color24;
    }

    public synchronized static boolean isSlimeChunk(int mcX, int mcZ) {
        int xPosition = mcX >> 4;
        int zPosition = mcZ >> 4;
        String seedString = VoxelConstants.getVoxelMapInstance().getWorldSeed();
        if (seedString.isEmpty()) {
            return false;
        }
        if (!Objects.equals(lastSeed, seedString)) {
            lastSeed = seedString;
            lastSlimeX = Integer.MIN_VALUE;
            try {
                lastSeedLong = Long.parseLong(seedString);
            } catch (NumberFormatException var8) {
                lastSeedLong = seedString.hashCode();
            }
        }

        if (xPosition != lastSlimeX || zPosition != lastSlimeZ) {
            lastSlimeX = xPosition;
            lastSlimeZ = zPosition;
            slimeRandom.setSeed(lastSeedLong + (int) (xPosition * xPosition * 0x4C1906) + (int) (xPosition * 0x5ac0db) + (int) (zPosition * zPosition) * 0x4307a7L + (int) (zPosition * 0x5f24f) ^ 0x3ad8025fL);
            isSlimeChunk = slimeRandom.nextInt(10) == 0;
        }

        return isSlimeChunk;
    }
}
