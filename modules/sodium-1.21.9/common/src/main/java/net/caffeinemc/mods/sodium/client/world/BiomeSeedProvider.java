package net.caffeinemc.mods.sodium.client.world;

import net.caffeinemc.mods.sodium.client.hooks.SodiumClientLevelHook;
import net.minecraft.client.multiplayer.ClientLevel;

public interface BiomeSeedProvider {
    static long getBiomeZoomSeed(ClientLevel level) {
        return SodiumClientLevelHook.getBiomeZoomSeed(level);
    }

    long sodium$getBiomeZoomSeed();
}
