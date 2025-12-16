package net.minecraft.client.renderer.sodium.world;

import net.minecraft.client.multiplayer.ClientLevel;

public interface BiomeSeedProvider {
    static long getBiomeZoomSeed(ClientLevel level) {
        return ((BiomeSeedProvider) level).sodium$getBiomeZoomSeed();
    }

    long sodium$getBiomeZoomSeed();
}
