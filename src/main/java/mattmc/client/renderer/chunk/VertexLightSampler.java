package mattmc.client.renderer.chunk;

import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.world.level.chunk.LevelChunk;

/**
 * Handles vertex light sampling for mesh building.
 * Samples light from adjacent blocks to create smooth lighting gradients.
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
     * Sample light for a vertex using smart smooth lighting.
     * 
     * For each vertex, we sample light from the adjacent air block (in the face normal
     * direction) and average with neighbors for smooth transitions.
     * 
     * FIX for wall lighting asymmetry: Wall faces now sample primarily from the
     * block at the current position (not just the adjacent block), ensuring
     * consistent lighting for all wall faces at the same position.
     * 
     * Only non-zero light samples are averaged to prevent interior corners from being too dark.
     * This fixes the issue where solid blocks (with 0 light) would darken adjacent corners.
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
        
        // For wall faces (normalIndex 2-5), sample from current block position
        // to ensure consistent lighting across all wall faces at the same position
        boolean isWallFace = normalIndex >= FACE_NORTH && normalIndex <= FACE_EAST;
        
        // Sample light from positions based on face type
        int[] offsets = getVertexSampleOffsets(normalIndex, cornerIndex);
        
        float skyLightSum = 0;
        float blockLightRSum = 0;
        float blockLightGSum = 0;
        float blockLightBSum = 0;
        int skyLightSamples = 0;
        int blockLightSamples = 0;
        
        // For wall faces, always include the current block's light as the primary sample
        // This ensures all walls at the same position get similar lighting
        if (isWallFace) {
            int skyLight = getSkyLightSafe(face.chunk, cx, cy, cz);
            int[] blockLightRGB = getBlockLightRGBSafe(face.chunk, cx, cy, cz);
            
            if (skyLight > 0) {
                skyLightSum += skyLight;
                skyLightSamples++;
            }
            if (blockLightRGB[0] > 0 || blockLightRGB[1] > 0 || blockLightRGB[2] > 0) {
                blockLightRSum += blockLightRGB[0];
                blockLightGSum += blockLightRGB[1];
                blockLightBSum += blockLightRGB[2];
                blockLightSamples++;
            }
        }
        
        // Sample additional positions for smooth lighting (3-4 positions)
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
            
            // Only include non-zero skylight samples in the average
            // This prevents solid blocks (with 0 skylight) from darkening interior corners
            if (skyLight > 0) {
                skyLightSum += skyLight;
                skyLightSamples++;
            }
            
            // Only include non-zero blocklight samples in the average
            // Check if any RGB component is non-zero
            if (blockLightRGB[0] > 0 || blockLightRGB[1] > 0 || blockLightRGB[2] > 0) {
                blockLightRSum += blockLightRGB[0];
                blockLightGSum += blockLightRGB[1];
                blockLightBSum += blockLightRGB[2];
                blockLightSamples++;
            }
        }
        
        // Average only the non-zero samples to prevent interior corners from being too dark
        // If all samples are zero, use zero (fully dark, but shader has minimum brightness)
        float avgSkyLight = skyLightSamples > 0 ? skyLightSum / skyLightSamples : 0.0f;
        float avgBlockLightR = blockLightSamples > 0 ? blockLightRSum / blockLightSamples : 0.0f;
        float avgBlockLightG = blockLightSamples > 0 ? blockLightGSum / blockLightSamples : 0.0f;
        float avgBlockLightB = blockLightSamples > 0 ? blockLightBSum / blockLightSamples : 0.0f;
        float ao = 0.0f; // No AO yet
        
        return new float[] {avgSkyLight, avgBlockLightR, avgBlockLightG, avgBlockLightB, ao};
    }
    
    // Face normal index constants for readability
    private static final int FACE_TOP = 0;
    private static final int FACE_BOTTOM = 1;
    private static final int FACE_NORTH = 2;
    private static final int FACE_SOUTH = 3;
    private static final int FACE_WEST = 4;
    private static final int FACE_EAST = 5;
    
    /**
     * Get offsets for the 4 sample positions for a vertex (3 sides + 1 diagonal).
     * Returns array of 12 ints: [dx0,dy0,dz0, dx1,dy1,dz1, dx2,dy2,dz2, dx3,dy3,dz3]
     * 
     * FIX for wall lighting asymmetry bug: For all faces, we now sample from the
     * air block that the face is visible from (in the normal direction), plus
     * neighbors around each specific corner vertex for smooth lighting.
     */
    private int[] getVertexSampleOffsets(int normalIndex, int cornerIndex) {
        // For each face and corner, sample from:
        // 1. The block in the face normal direction (where the face is visible from)
        // 2-4. Three neighbors around the vertex corner for smooth gradients
        
        // Top face (normal = 0,1,0)
        if (normalIndex == FACE_TOP) {
            switch (cornerIndex) {
                case 0: return new int[] {0,1,0, -1,1,0, 0,1,-1, -1,1,-1}; // x0, z0 corner
                case 1: return new int[] {0,1,0, -1,1,0, 0,1,1, -1,1,1};   // x0, z1 corner
                case 2: return new int[] {0,1,0, 1,1,0, 0,1,1, 1,1,1};     // x1, z1 corner
                case 3: return new int[] {0,1,0, 1,1,0, 0,1,-1, 1,1,-1};   // x1, z0 corner
            }
        }
        // Bottom face (normal = 0,-1,0)
        else if (normalIndex == FACE_BOTTOM) {
            switch (cornerIndex) {
                case 0: return new int[] {0,-1,0, -1,-1,0, 0,-1,-1, -1,-1,-1}; // x0, z0 corner
                case 1: return new int[] {0,-1,0, 1,-1,0, 0,-1,-1, 1,-1,-1};   // x1, z0 corner
                case 2: return new int[] {0,-1,0, 1,-1,0, 0,-1,1, 1,-1,1};     // x1, z1 corner
                case 3: return new int[] {0,-1,0, -1,-1,0, 0,-1,1, -1,-1,1};   // x0, z1 corner
            }
        }
        // North face (normal = 0,0,-1) - vertices ordered: x1y0, x0y0, x0y1, x1y1
        else if (normalIndex == FACE_NORTH) {
            switch (cornerIndex) {
                case 0: return new int[] {0,0,-1, 1,0,-1, 0,-1,-1, 1,-1,-1};   // x1, y0 corner
                case 1: return new int[] {0,0,-1, -1,0,-1, 0,-1,-1, -1,-1,-1}; // x0, y0 corner
                case 2: return new int[] {0,0,-1, -1,0,-1, 0,1,-1, -1,1,-1};   // x0, y1 corner
                case 3: return new int[] {0,0,-1, 1,0,-1, 0,1,-1, 1,1,-1};     // x1, y1 corner
            }
        }
        // South face (normal = 0,0,1) - vertices ordered: x0y0, x1y0, x1y1, x0y1
        else if (normalIndex == FACE_SOUTH) {
            switch (cornerIndex) {
                case 0: return new int[] {0,0,1, -1,0,1, 0,-1,1, -1,-1,1}; // x0, y0 corner
                case 1: return new int[] {0,0,1, 1,0,1, 0,-1,1, 1,-1,1};   // x1, y0 corner
                case 2: return new int[] {0,0,1, 1,0,1, 0,1,1, 1,1,1};     // x1, y1 corner
                case 3: return new int[] {0,0,1, -1,0,1, 0,1,1, -1,1,1};   // x0, y1 corner
            }
        }
        // West face (normal = -1,0,0) - vertices ordered: z0y0, z1y0, z1y1, z0y1
        else if (normalIndex == FACE_WEST) {
            switch (cornerIndex) {
                case 0: return new int[] {-1,0,0, -1,0,-1, -1,-1,0, -1,-1,-1}; // z0, y0 corner
                case 1: return new int[] {-1,0,0, -1,0,1, -1,-1,0, -1,-1,1};   // z1, y0 corner
                case 2: return new int[] {-1,0,0, -1,0,1, -1,1,0, -1,1,1};     // z1, y1 corner
                case 3: return new int[] {-1,0,0, -1,0,-1, -1,1,0, -1,1,-1};   // z0, y1 corner
            }
        }
        // East face (normal = 1,0,0) - vertices ordered: z1y0, z0y0, z0y1, z1y1
        else if (normalIndex == FACE_EAST) {
            switch (cornerIndex) {
                case 0: return new int[] {1,0,0, 1,0,1, 1,-1,0, 1,-1,1};   // z1, y0 corner
                case 1: return new int[] {1,0,0, 1,0,-1, 1,-1,0, 1,-1,-1}; // z0, y0 corner
                case 2: return new int[] {1,0,0, 1,0,-1, 1,1,0, 1,1,-1};   // z0, y1 corner
                case 3: return new int[] {1,0,0, 1,0,1, 1,1,0, 1,1,1};     // z1, y1 corner
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
     * Get blocklight value safely, returning 0 if out of bounds.
     */
    private int getBlockLightSafe(LevelChunk chunk, int x, int y, int z) {
        // Check bounds
        if (y < 0 || y >= LevelChunk.HEIGHT) {
            return 0; // Out of bounds: no blocklight
        }
        
        // If we have a light accessor and coordinates are out of chunk bounds, use cross-chunk sampling
        if (lightAccessor != null && 
            (x < 0 || x >= LevelChunk.WIDTH ||
             z < 0 || z >= LevelChunk.DEPTH)) {
            return lightAccessor.getBlockLightAcrossChunks(chunk, x, y, z);
        }
        
        // Within chunk bounds - use direct access
        if (x < 0 || x >= LevelChunk.WIDTH ||
            z < 0 || z >= LevelChunk.DEPTH) {
            return 0; // Out of chunk bounds without accessor: no blocklight
        }
        
        return chunk.getBlockLightI(x, y, z);
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
