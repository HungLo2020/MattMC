package mattmc.world.level.chunk;

/**
 * Smooth lighting and ambient occlusion calculator for Minecraft-style rendering.
 * 
 * Implements:
 * - Smooth lighting: Interpolates light values at block corners for gradients
 * - Ambient occlusion: Darkens corners based on adjacent solid blocks
 * 
 * Based on Minecraft's lighting system.
 */
public class SmoothLighting {
    
    /**
     * Calculate smooth lighting and AO for a vertex.
     * 
     * @param chunk The chunk containing the block
     * @param x Block X coordinate (chunk-local)
     * @param y Block Y coordinate (chunk-local)
     * @param z Block Z coordinate (chunk-local)
     * @param dx Offset in X direction for this vertex (-1, 0, or 1)
     * @param dy Offset in Y direction for this vertex (-1, 0, or 1)
     * @param dz Offset in Z direction for this vertex (-1, 0, or 1)
     * @return Array containing [lightValue, aoValue] where lightValue is 0-15 and aoValue is 0.0-1.0
     */
    public static float[] calculateVertexLighting(LevelChunk chunk, int x, int y, int z, 
                                                  int dx, int dy, int dz) {
        // For smooth lighting, we sample light from the 4 blocks surrounding each vertex
        // For a vertex, we check the blocks in a 2x2x2 cube around it
        
        int lightSum = 0;
        int lightCount = 0;
        int aoFactor = 0; // Count of solid blocks for AO
        
        // Sample adjacent blocks for this vertex
        // We check blocks along the three edges meeting at this vertex
        int[][] sampleOffsets;
        
        // Determine which blocks to sample based on the vertex position
        if (dx != 0 && dy != 0 && dz == 0) {
            // Corner along Z edge (north/south faces)
            sampleOffsets = new int[][] {
                {dx, 0, 0},     // Side
                {0, dy, 0},     // Top/bottom
                {dx, dy, 0},    // Diagonal
                {0, 0, 0}       // Center (the block itself)
            };
        } else if (dx != 0 && dy == 0 && dz != 0) {
            // Corner along Y edge (east/west faces)
            sampleOffsets = new int[][] {
                {dx, 0, 0},     // Side
                {0, 0, dz},     // Front/back
                {dx, 0, dz},    // Diagonal
                {0, 0, 0}       // Center
            };
        } else if (dx == 0 && dy != 0 && dz != 0) {
            // Corner along X edge (top/bottom faces)
            sampleOffsets = new int[][] {
                {0, dy, 0},     // Top/bottom
                {0, 0, dz},     // Front/back
                {0, dy, dz},    // Diagonal
                {0, 0, 0}       // Center
            };
        } else {
            // Fallback for edge cases - just use the block itself
            sampleOffsets = new int[][] {{0, 0, 0}};
        }
        
        // Sample light and check for solid blocks
        for (int[] offset : sampleOffsets) {
            int sx = x + offset[0];
            int sy = y + offset[1];
            int sz = z + offset[2];
            
            // Get light value
            int light = chunk.getLightLevel(sx, sy, sz);
            lightSum += light;
            lightCount++;
            
            // Check if block is solid for AO
            if (sx >= 0 && sx < LevelChunk.WIDTH && 
                sy >= 0 && sy < LevelChunk.HEIGHT && 
                sz >= 0 && sz < LevelChunk.DEPTH) {
                if (chunk.getBlock(sx, sy, sz).isSolid()) {
                    aoFactor++;
                }
            }
        }
        
        // Calculate average light level
        float avgLight = lightCount > 0 ? (float) lightSum / lightCount : 15.0f;
        
        // Calculate AO multiplier (darker when more solid blocks nearby)
        // 0 solid blocks = 1.0 (full brightness)
        // 1 solid block = 0.8
        // 2 solid blocks = 0.6
        // 3+ solid blocks = 0.4
        float aoMultiplier = 1.0f - (Math.min(aoFactor, 3) * 0.2f);
        
        return new float[] {avgLight, aoMultiplier};
    }
    
    /**
     * Calculate lighting for the 4 corners of a block face.
     * Returns an array of 4 light/AO pairs, one for each vertex.
     * 
     * @param chunk The chunk
     * @param x Block X coordinate (chunk-local)
     * @param y Block Y coordinate (chunk-local)
     * @param z Block Z coordinate (chunk-local)
     * @param faceDirection The face direction (0=bottom, 1=top, 2=north, 3=south, 4=west, 5=east)
     * @return Array of 4 vertex lighting values
     */
    public static float[][] calculateFaceLighting(LevelChunk chunk, int x, int y, int z, int faceDirection) {
        float[][] vertices = new float[4][2]; // 4 vertices, each with [light, ao]
        
        switch (faceDirection) {
            case 0: // Bottom face (y-)
                vertices[0] = calculateVertexLighting(chunk, x, y, z, 0, -1, 0);   // Base
                vertices[1] = calculateVertexLighting(chunk, x, y, z, 1, -1, 0);   // Base
                vertices[2] = calculateVertexLighting(chunk, x, y, z, 1, -1, 1);   // Base
                vertices[3] = calculateVertexLighting(chunk, x, y, z, 0, -1, 1);   // Base
                break;
            case 1: // Top face (y+)
                vertices[0] = calculateVertexLighting(chunk, x, y, z, 0, 1, 0);    // Base
                vertices[1] = calculateVertexLighting(chunk, x, y, z, 1, 1, 0);    // Base
                vertices[2] = calculateVertexLighting(chunk, x, y, z, 1, 1, 1);    // Base
                vertices[3] = calculateVertexLighting(chunk, x, y, z, 0, 1, 1);    // Base
                break;
            case 2: // North face (z-)
                vertices[0] = calculateVertexLighting(chunk, x, y, z, 0, 0, -1);   // Base
                vertices[1] = calculateVertexLighting(chunk, x, y, z, 1, 0, -1);   // Base
                vertices[2] = calculateVertexLighting(chunk, x, y, z, 1, 1, -1);   // Base
                vertices[3] = calculateVertexLighting(chunk, x, y, z, 0, 1, -1);   // Base
                break;
            case 3: // South face (z+)
                vertices[0] = calculateVertexLighting(chunk, x, y, z, 0, 0, 1);    // Base
                vertices[1] = calculateVertexLighting(chunk, x, y, z, 1, 0, 1);    // Base
                vertices[2] = calculateVertexLighting(chunk, x, y, z, 1, 1, 1);    // Base
                vertices[3] = calculateVertexLighting(chunk, x, y, z, 0, 1, 1);    // Base
                break;
            case 4: // West face (x-)
                vertices[0] = calculateVertexLighting(chunk, x, y, z, -1, 0, 0);   // Base
                vertices[1] = calculateVertexLighting(chunk, x, y, z, -1, 0, 1);   // Base
                vertices[2] = calculateVertexLighting(chunk, x, y, z, -1, 1, 1);   // Base
                vertices[3] = calculateVertexLighting(chunk, x, y, z, -1, 1, 0);   // Base
                break;
            case 5: // East face (x+)
                vertices[0] = calculateVertexLighting(chunk, x, y, z, 1, 0, 0);    // Base
                vertices[1] = calculateVertexLighting(chunk, x, y, z, 1, 0, 1);    // Base
                vertices[2] = calculateVertexLighting(chunk, x, y, z, 1, 1, 1);    // Base
                vertices[3] = calculateVertexLighting(chunk, x, y, z, 1, 1, 0);    // Base
                break;
        }
        
        return vertices;
    }
}
