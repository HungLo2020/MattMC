// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.program;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches compiled shader programs to avoid recompilation.
 * 
 * Based on IRIS's ShaderMap pattern, adapted for MattMC's simpler program management.
 * Reference: frnsrc/Iris-1.21.9/.../pipeline/programs/ShaderMap.java
 * 
 * IRIS uses an array-based map with ShaderKey enum for program identification.
 * For Step 13, we use a hash-based cache with string keys (program names).
 * The ShaderKey enum approach will be added in Step 15 (Program Set Management).
 * 
 * Step 13 of NEW-SHADER-PLAN.md
 */
public class ProgramCache {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProgramCache.class);
	
	// Thread-safe map for storing compiled programs
	// Key: program name, Value: compiled Program
	private final Map<String, Program> cache;
	
	// Track cache statistics
	private int hits = 0;
	private int misses = 0;

	/**
	 * Creates a new empty program cache.
	 * 
	 * Using ConcurrentHashMap for thread-safe access, similar to IRIS's approach.
	 */
	public ProgramCache() {
		this.cache = new ConcurrentHashMap<>();
	}

	/**
	 * Retrieves a program from the cache.
	 * 
	 * Following IRIS's ShaderMap.getShader() pattern (ShaderMap.java:52-54).
	 * 
	 * @param name The program name
	 * @return The cached program, or null if not found
	 */
	public Program get(String name) {
		Program program = cache.get(name);
		if (program != null) {
			hits++;
			LOGGER.debug("Cache hit for program: {}", name);
		} else {
			misses++;
			LOGGER.debug("Cache miss for program: {}", name);
		}
		return program;
	}

	/**
	 * Stores a program in the cache.
	 * 
	 * Following IRIS's pattern of storing compiled programs for reuse.
	 * 
	 * @param name The program name (key)
	 * @param program The compiled program
	 */
	public void put(String name, Program program) {
		if (name == null || program == null) {
			throw new IllegalArgumentException("Program name and program cannot be null");
		}
		
		cache.put(name, program);
		LOGGER.debug("Cached program: {}", name);
	}

	/**
	 * Checks if a program exists in the cache.
	 * 
	 * @param name The program name
	 * @return true if the program is cached
	 */
	public boolean contains(String name) {
		return cache.containsKey(name);
	}

	/**
	 * Removes a program from the cache.
	 * 
	 * @param name The program name
	 * @return The removed program, or null if not found
	 */
	public Program remove(String name) {
		Program removed = cache.remove(name);
		if (removed != null) {
			LOGGER.debug("Removed program from cache: {}", name);
		}
		return removed;
	}

	/**
	 * Clears all programs from the cache.
	 * 
	 * Note: This does NOT call destroyInternal() on the programs.
	 * The caller is responsible for proper cleanup of OpenGL resources.
	 */
	public void clear() {
		int size = cache.size();
		cache.clear();
		LOGGER.debug("Cleared program cache ({} programs)", size);
		
		// Reset statistics
		hits = 0;
		misses = 0;
	}

	/**
	 * Clears the cache and destroys all cached programs.
	 * 
	 * This properly cleans up OpenGL resources for all cached programs.
	 */
	public void clearAndDestroy() {
		for (Map.Entry<String, Program> entry : cache.entrySet()) {
			try {
				entry.getValue().destroyInternal();
				LOGGER.debug("Destroyed cached program: {}", entry.getKey());
			} catch (Exception e) {
				LOGGER.error("Error destroying cached program: {}", entry.getKey(), e);
			}
		}
		clear();
	}

	/**
	 * Gets the number of programs in the cache.
	 * 
	 * @return The cache size
	 */
	public int size() {
		return cache.size();
	}

	/**
	 * Checks if the cache is empty.
	 * 
	 * @return true if the cache contains no programs
	 */
	public boolean isEmpty() {
		return cache.isEmpty();
	}

	/**
	 * Gets the number of cache hits.
	 * 
	 * @return The hit count
	 */
	public int getHits() {
		return hits;
	}

	/**
	 * Gets the number of cache misses.
	 * 
	 * @return The miss count
	 */
	public int getMisses() {
		return misses;
	}

	/**
	 * Gets the cache hit rate as a percentage.
	 * 
	 * @return The hit rate (0.0 to 1.0), or 0.0 if no lookups performed
	 */
	public double getHitRate() {
		int total = hits + misses;
		if (total == 0) {
			return 0.0;
		}
		return (double) hits / total;
	}

	/**
	 * Logs cache statistics.
	 */
	public void logStatistics() {
		int total = hits + misses;
		if (total > 0) {
			LOGGER.info("Program cache statistics: {} entries, {} hits, {} misses, {:.1f}% hit rate",
				size(), hits, misses, getHitRate() * 100);
		} else {
			LOGGER.info("Program cache statistics: {} entries, no lookups performed", size());
		}
	}
}
