package net.minecraft.client.renderer.sodium.world;

import net.minecraft.world.level.chunk.Palette;

public interface BitStorageExtension {
    <T> void sodium$unpack(T[] out, Palette<T> palette);
}
