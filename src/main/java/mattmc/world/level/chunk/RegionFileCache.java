package mattmc.world.level.chunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cache for RegionFile instances to avoid repeatedly opening/closing region files.
 * Based on Minecraft Java Edition's region file caching strategy.
 * Uses LRU eviction to limit memory usage.
 */
public class RegionFileCache implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(RegionFileCache.class);
    private static final int MAX_CACHE_SIZE = 256; // Maximum number of region files to keep open
    
    private final Path regionDirectory;
    private final Map<Long, RegionFile> cache;
    
    public RegionFileCache(Path regionDirectory) {
        this.regionDirectory = regionDirectory;
        // LinkedHashMap with access-order for LRU
        this.cache = new LinkedHashMap<Long, RegionFile>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, RegionFile> eldest) {
                if (size() > MAX_CACHE_SIZE) {
                    try {
                        eldest.getValue().close();
                    } catch (IOException e) {
                        logger.error("Error closing region file: {}", e.getMessage(), e);
                    }
                    return true;
                }
                return false;
            }
        };
    }
    
    /**
     * Get or create a RegionFile for the given chunk coordinates.
     */
    public RegionFile getRegionFile(int chunkX, int chunkZ) throws IOException {
        int[] regionCoords = RegionFile.getRegionCoords(chunkX, chunkZ);
        int regionX = regionCoords[0];
        int regionZ = regionCoords[1];
        
        long key = regionKey(regionX, regionZ);
        
        RegionFile regionFile = cache.get(key);
        if (regionFile == null) {
            Path regionPath = regionDirectory.resolve(String.format("r.%d.%d.mca", regionX, regionZ));
            regionFile = new RegionFile(regionPath, regionX, regionZ);
            cache.put(key, regionFile);
        }
        
        return regionFile;
    }
    
    /**
     * Flush all cached region files to disk.
     */
    public void flush() throws IOException {
        for (RegionFile regionFile : cache.values()) {
            regionFile.flush();
        }
    }
    
    /**
     * Close all cached region files.
     */
    @Override
    public void close() throws IOException {
        IOException firstException = null;
        
        for (RegionFile regionFile : cache.values()) {
            try {
                regionFile.close();
            } catch (IOException e) {
                if (firstException == null) {
                    firstException = e;
                }
            }
        }
        
        cache.clear();
        
        if (firstException != null) {
            throw firstException;
        }
    }
    
    /**
     * Get the number of cached region files.
     */
    public int getCacheSize() {
        return cache.size();
    }
    
    /**
     * Create a unique key for a region.
     */
    private static long regionKey(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
    }
}
