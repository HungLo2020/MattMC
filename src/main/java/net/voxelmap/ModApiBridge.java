package net.voxelmap;

public interface ModApiBridge {
    default boolean isModEnabled(String modID) {
        return false;
    }
}