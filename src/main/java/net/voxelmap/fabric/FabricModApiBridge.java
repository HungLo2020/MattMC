package net.voxelmap.fabric;

import net.voxelmap.ModApiBridge;
import net.fabricmc.loader.api.FabricLoader;

public class FabricModApiBridge implements ModApiBridge {
    @Override
    public boolean isModEnabled(String modID) {
        return FabricLoader.getInstance().isModLoaded(modID);
    }
}
