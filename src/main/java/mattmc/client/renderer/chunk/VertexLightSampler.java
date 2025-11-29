package mattmc.client.renderer.chunk;

import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.client.settings.OptionsManager;
import mattmc.world.level.chunk.LevelChunk;

/**
 * Handles vertex light sampling for mesh building.
 * Uses Minecraft's 4-sample averaging approach for smooth lighting.
 * 
 * <p>For each vertex, samples 4 neighbors: the face-adjacent block + 2 edge neighbors + 1 corner.
 * Uses a blend function where if a sample is 0 (blocked), it's replaced with a fallback value
 * (the face-adjacent light value) before averaging. This matches Minecraft's approach.</p>
 * 
 * <p>When smooth lighting is disabled via {@link OptionsManager#isSmoothLightingEnabled()},
 * only the face-adjacent block's light value is used (flat lighting).</p>
 * 
 * Extracted from MeshBuilder as part of refactoring to single-purpose classes.
 */
public class VertexLightSampler {
    
    /**
     * Interface for sampling light values across chunk boundaries.
     */
    public interface ChunkLightAccessor {
        /**
         * Get skylight level at chunk-local coordinates, checking neighboring chunks if necessary.
         * @param chunk The current chunk
         * @param x Chunk-local X coordinate (can be outside 0-15 range)
         * @param y Chunk-local Y coordinate (0-383)
         * @param z Chunk-local Z coordinate (can be outside 0-15 range)
         * @return Skylight level (0-15)
         */
        int getSkyLightAcrossChunks(LevelChunk chunk, int x, int y, int z);
        
        /**
         * Get blocklight level at chunk-local coordinates, checking neighboring chunks if necessary.
         * @param chunk The current chunk
         * @param x Chunk-local X coordinate (can be outside 0-15 range)
         * @param y Chunk-local Y coordinate (0-383)
         * @param z Chunk-local Z coordinate (can be outside 0-15 range)
         * @return Blocklight level (0-15)
         */
        int getBlockLightAcrossChunks(LevelChunk chunk, int x, int y, int z);
        
        /**
         * Get blocklight RGB values at chunk-local coordinates, checking neighboring chunks if necessary.
         * @param chunk The current chunk
         * @param x Chunk-local X coordinate (can be outside 0-15 range)
         * @param y Chunk-local Y coordinate (0-383)
         * @param z Chunk-local Z coordinate (can be outside 0-15 range)
         * @return Blocklight RGB as array [R, G, B] (0-15 each)
         */
        default int[] getBlockLightRGBAcrossChunks(LevelChunk chunk, int x, int y, int z) {
            // Default implementation for backward compatibility - returns white light
            int intensity = getBlockLightAcrossChunks(chunk, x, y, z);
            return new int[] {intensity, intensity, intensity};
        }
    }
    
    private ChunkLightAccessor lightAccessor;
    
    /**
     * Set the light accessor for cross-chunk light sampling.
     */
    public void setLightAccessor(ChunkLightAccessor accessor) {
        this.lightAccessor = accessor;
    }
    
    /**
     * Sample light for a vertex using Minecraft's 4-sample averaging approach.
     * 
     * <p>For each vertex, we sample light from 4 positions: face-adjacent + 2 edge neighbors + 1 corner.
     * This creates smooth lighting gradients across block edges.</p>
     * 
     * <p>If a sample has 0 light (e.g., blocked by solid block), it's replaced with the face-adjacent
     * light value as a fallback, matching Minecraft's blend() function behavior. All 4 samples are
     * always averaged together: (s0 + s1 + s2 + s3) * 0.25f</p>
     * 
     * <p>When {@link OptionsManager#isSmoothLightingEnabled()} returns false, only the face-adjacent
     * block's light is returned (flat lighting mode).</p>
     * 
     * @param face The face data containing chunk reference and position
     * @param normalIndex Which face (0=top, 1=bottom, 2=north, 3=south, 4=west, 5=east)
     * @param cornerIndex Which corner of the face (0-3)
     * @return [skyLight, blockLightR, blockLightG, blockLightB, ao] as floats (0-15 for light, 0-3 for ao)
     */
    public float[] sampleVertexLight(BlockFaceCollector.FaceData face, 
                                      int normalIndex,
                                      int cornerIndex) {
        // If no chunk reference, return default lighting
        if (face.chunk == null) {
            return new float[] {15.0f, 0.0f, 0.0f, 0.0f, 0.0f}; // Full skylight, no blocklight RGB, no AO
        }
        
        // Get chunk-local coordinates of the block
        int cx = face.cx;
        int cy = face.cy;
        int cz = face.cz;
        
        // Get the offsets for the 4 sample positions for this vertex
        int[] offsets = getVertexSampleOffsets(normalIndex, cornerIndex);
        
        // Position 0 is always the face-adjacent block (used as fallback in blend)
        int faceX = cx + offsets[0];
        int faceY = cy + offsets[1];
        int faceZ = cz + offsets[2];
        
        // Sample the face-adjacent block's light (this is the fallback/primary value)
        int faceSkyLight = getSkyLightSafe(face.chunk, faceX, faceY, faceZ);
        int[] faceBlockLightRGB = getBlockLightRGBSafe(face.chunk, faceX, faceY, faceZ);
        
        // If smooth lighting is disabled, just return the face-adjacent light (flat lighting)
        if (!OptionsManager.isSmoothLightingEnabled()) {
            return createLightArray(faceSkyLight, faceBlockLightRGB[0], faceBlockLightRGB[1], faceBlockLightRGB[2]);
        }
        
        // Smooth lighting enabled: sample all 4 positions and average
        // Using Minecraft's blend approach: if a sample is 0, use the face-adjacent value as fallback
        
        float skyLightSum = 0;
        float blockLightRSum = 0;
        float blockLightGSum = 0;
        float blockLightBSum = 0;
        
        // Sample all 4 positions
        for (int i = 0; i < 4; i++) {
            int dx = offsets[i * 3];
            int dy = offsets[i * 3 + 1];
            int dz = offsets[i * 3 + 2];
            
            int sx = cx + dx;
            int sy = cy + dy;
            int sz = cz + dz;
            
            // Sample light at this position
            int skyLight = getSkyLightSafe(face.chunk, sx, sy, sz);
            int[] blockLightRGB = getBlockLightRGBSafe(face.chunk, sx, sy, sz);
            
            // Minecraft's blend approach: if sample is 0, use the face-adjacent value as fallback
            skyLightSum += blendLight(skyLight, faceSkyLight);
            blockLightRSum += blendLight(blockLightRGB[0], faceBlockLightRGB[0]);
            blockLightGSum += blendLight(blockLightRGB[1], faceBlockLightRGB[1]);
            blockLightBSum += blendLight(blockLightRGB[2], faceBlockLightRGB[2]);
        }
        
        // Average all 4 samples (Minecraft: >> 2 which is * 0.25)
        return createLightArray(
            skyLightSum * 0.25f,
            blockLightRSum * 0.25f,
            blockLightGSum * 0.25f,
            blockLightBSum * 0.25f
        );
    }
    
    /**
     * Create a light data array from individual light values.
     * @param skyLight Skylight value (0-15)
     * @param blockLightR Block light red component (0-15)
     * @param blockLightG Block light green component (0-15)
     * @param blockLightB Block light blue component (0-15)
     * @return Array of [skyLight, blockLightR, blockLightG, blockLightB, ao]
     */
    private float[] createLightArray(float skyLight, float blockLightR, float blockLightG, float blockLightB) {
        return new float[] {skyLight, blockLightR, blockLightG, blockLightB, 0.0f}; // 0.0f = no AO yet
    }
    
    /**
     * Blend a light value with a fallback value.
     * If the light value is 0 (blocked), use the fallback value instead.
     * This matches Minecraft's blend() function behavior.
     * 
     * @param light The sampled light value
     * @param fallback The fallback value (typically the face-adjacent block's light)
     * @return The blended light value
     */
    private float blendLight(int light, int fallback) {
        return light == 0 ? (float) fallback : (float) light;
    }
    
    /**
     * Get offsets for the 4 sample positions for a vertex (3 sides + 1 diagonal).
     * Returns array of 12 ints: [dx0,dy0,dz0, dx1,dy1,dz1, dx2,dy2,dz2, dx3,dy3,dz3]
     */
    private int[] getVertexSampleOffsets(int normalIndex, int cornerIndex) {
        // For each face and corner, define the 4 sampling positions
        // Format: [dx,dy,dz, dx,dy,dz, dx,dy,dz, dx,dy,dz]
        
        // Top face (normal = 0,1,0)
        if (normalIndex == 0) {
            switch (cornerIndex) {
                case 0: return new int[] {0,1,0, -1,1,0, 0,1,-1, -1,1,-1}; // x0, z0
                case 1: return new int[] {0,1,0, -1,1,0, 0,1,1, -1,1,1};   // x0, z1
                case 2: return new int[] {0,1,0, 1,1,0, 0,1,1, 1,1,1};     // x1, z1
                case 3: return new int[] {0,1,0, 1,1,0, 0,1,-1, 1,1,-1};   // x1, z0
            }
        }
        // Bottom face (normal = 0,-1,0)
        else if (normalIndex == 1) {
            switch (cornerIndex) {
                case 0: return new int[] {0,-1,0, -1,-1,0, 0,-1,-1, -1,-1,-1}; // x0, z0
                case 1: return new int[] {0,-1,0, 1,-1,0, 0,-1,-1, 1,-1,-1};   // x1, z0
                case 2: return new int[] {0,-1,0, 1,-1,0, 0,-1,1, 1,-1,1};     // x1, z1
                case 3: return new int[] {0,-1,0, -1,-1,0, 0,-1,1, -1,-1,1};   // x0, z1
            }
        }
        // North face (normal = 0,0,-1)
        else if (normalIndex == 2) {
            switch (cornerIndex) {
                case 0: return new int[] {0,0,-1, 1,0,-1, 0,-1,-1, 1,-1,-1};   // x1, y0
                case 1: return new int[] {0,0,-1, -1,0,-1, 0,-1,-1, -1,-1,-1}; // x0, y0
                case 2: return new int[] {0,0,-1, -1,0,-1, 0,1,-1, -1,1,-1};   // x0, y1
                case 3: return new int[] {0,0,-1, 1,0,-1, 0,1,-1, 1,1,-1};     // x1, y1
            }
        }
        // South face (normal = 0,0,1)
        else if (normalIndex == 3) {
            switch (cornerIndex) {
                case 0: return new int[] {0,0,1, -1,0,1, 0,-1,1, -1,-1,1}; // x0, y0
                case 1: return new int[] {0,0,1, 1,0,1, 0,-1,1, 1,-1,1};   // x1, y0
                case 2: return new int[] {0,0,1, 1,0,1, 0,1,1, 1,1,1};     // x1, y1
                case 3: return new int[] {0,0,1, -1,0,1, 0,1,1, -1,1,1};   // x0, y1
            }
        }
        // West face (normal = -1,0,0)
        else if (normalIndex == 4) {
            switch (cornerIndex) {
                case 0: return new int[] {-1,0,0, -1,0,-1, -1,-1,0, -1,-1,-1}; // z0, y0
                case 1: return new int[] {-1,0,0, -1,0,1, -1,-1,0, -1,-1,1};   // z1, y0
                case 2: return new int[] {-1,0,0, -1,0,1, -1,1,0, -1,1,1};     // z1, y1
                case 3: return new int[] {-1,0,0, -1,0,-1, -1,1,0, -1,1,-1};   // z0, y1
            }
        }
        // East face (normal = 1,0,0)
        else if (normalIndex == 5) {
            switch (cornerIndex) {
                case 0: return new int[] {1,0,0, 1,0,1, 1,-1,0, 1,-1,1};   // z1, y0
                case 1: return new int[] {1,0,0, 1,0,-1, 1,-1,0, 1,-1,-1}; // z0, y0
                case 2: return new int[] {1,0,0, 1,0,-1, 1,1,0, 1,1,-1};   // z0, y1
                case 3: return new int[] {1,0,0, 1,0,1, 1,1,0, 1,1,1};     // z1, y1
            }
        }
        
        // Default: sample center position 4 times
        return new int[] {0,0,0, 0,0,0, 0,0,0, 0,0,0};
    }
    
    /**
     * Get skylight value safely, returning 15 if out of bounds.
     */
    private int getSkyLightSafe(LevelChunk chunk, int x, int y, int z) {
        // Check bounds
        if (y < 0 || y >= LevelChunk.HEIGHT) {
            return 15; // Out of bounds: full skylight
        }
        
        // If we have a light accessor and coordinates are out of chunk bounds, use cross-chunk sampling
        if (lightAccessor != null && 
            (x < 0 || x >= LevelChunk.WIDTH ||
             z < 0 || z >= LevelChunk.DEPTH)) {
            return lightAccessor.getSkyLightAcrossChunks(chunk, x, y, z);
        }
        
        // Within chunk bounds - use direct access
        if (x < 0 || x >= LevelChunk.WIDTH ||
            z < 0 || z >= LevelChunk.DEPTH) {
            return 15; // Out of chunk bounds without accessor: full skylight
        }
        
        return chunk.getSkyLight(x, y, z);
    }
    
    /**
     * Get blocklight RGB values safely, returning [0,0,0] if out of bounds.
     * Scales RGB values by intensity to properly attenuate light with distance.
     * @return Array of [R, G, B] values (0-15 each), scaled by intensity
     */
    private int[] getBlockLightRGBSafe(LevelChunk chunk, int x, int y, int z) {
        // Check bounds
        if (y < 0 || y >= LevelChunk.HEIGHT) {
            return new int[] {0, 0, 0}; // Out of bounds: no blocklight
        }
        
        // If we have a light accessor and coordinates are out of chunk bounds, use cross-chunk sampling
        if (lightAccessor != null && 
            (x < 0 || x >= LevelChunk.WIDTH ||
             z < 0 || z >= LevelChunk.DEPTH)) {
            return lightAccessor.getBlockLightRGBAcrossChunks(chunk, x, y, z);
        }
        
        // Within chunk bounds - use direct access
        if (x < 0 || x >= LevelChunk.WIDTH ||
            z < 0 || z >= LevelChunk.DEPTH) {
            return new int[] {0, 0, 0}; // Out of chunk bounds without accessor: no blocklight
        }
        
        // Get RGB and intensity values
        int r = chunk.getBlockLightR(x, y, z);
        int g = chunk.getBlockLightG(x, y, z);
        int b = chunk.getBlockLightB(x, y, z);
        int intensity = chunk.getBlockLightI(x, y, z);
        
        // If no light, return early
        if (intensity == 0) {
            return new int[] {0, 0, 0};
        }
        
        // Scale RGB by intensity ratio to properly attenuate light
        // RGB values stay constant during propagation, but intensity decrements
        // We need to scale RGB to match the current intensity
        int maxRGB = Math.max(r, Math.max(g, b));
        
        // If maxRGB is 0 but intensity is not, something is wrong - return intensity as white light
        if (maxRGB == 0) {
            return new int[] {intensity, intensity, intensity};
        }
        
        // Scale RGB by the intensity ratio
        float scale = (float) intensity / maxRGB;
        int scaledR = Math.round(r * scale);
        int scaledG = Math.round(g * scale);
        int scaledB = Math.round(b * scale);
        
        return new int[] {scaledR, scaledG, scaledB};
    }
}
