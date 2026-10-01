package net.minecraft.world.level.lighting;

import net.minecraft.world.level.chunk.ChunkAccess;

/** The driver pins the original implementation and every helper against Git. */
final class JavaSkyLightSources {
    static void fill(ChunkSkyLightSources sources,ChunkAccess chunk) { sources.fillFromJava(chunk); }
}
