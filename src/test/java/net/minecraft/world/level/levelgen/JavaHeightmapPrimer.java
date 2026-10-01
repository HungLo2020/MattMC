package net.minecraft.world.level.levelgen;

import java.util.Set;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Calls the original compatibility method, pinned verbatim by the driver.
 * No native dispatch participates in this reference path. */
final class JavaHeightmapPrimer {
    static void primeHeightmaps(ChunkAccess chunk, Set<Heightmap.Types> types) {
        Heightmap.primeHeightmapsJava(chunk, types);
    }
}
