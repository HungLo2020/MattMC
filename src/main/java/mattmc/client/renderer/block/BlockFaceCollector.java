package mattmc.client.renderer.block;

import mattmc.client.renderer.ColorUtils;

import mattmc.world.level.block.Block;
import mattmc.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects visible block faces for batched rendering with face culling.
 * Separates faces by direction for optimized rendering.
 */
public class BlockFaceCollector {
    
    /**
     * Interface for querying blocks across chunk boundaries.
     */
    public interface ChunkNeighborAccessor {
        /**
         * Get a block at chunk-local coordinates, checking neighboring chunks if necessary.
         * @param chunk The current chunk
         * @param x Chunk-local X coordinate (can be outside 0-15 range)
         * @param y Chunk-local Y coordinate (0-383)
         * @param z Chunk-local Z coordinate (can be outside 0-15 range)
         * @return The block at the specified position
         */
        Block getBlockAcrossChunks(LevelChunk chunk, int x, int y, int z);
    }
    
    // Face data storage
    private final List<FaceData> topFaces = new ArrayList<>();
    private final List<FaceData> bottomFaces = new ArrayList<>();
    private final List<FaceData> northFaces = new ArrayList<>();
    private final List<FaceData> southFaces = new ArrayList<>();
    private final List<FaceData> westFaces = new ArrayList<>();
    private final List<FaceData> eastFaces = new ArrayList<>();
    
    // Neighbor accessor for cross-chunk queries
    private ChunkNeighborAccessor neighborAccessor;
    
    /**
     * Set the chunk neighbor accessor for cross-chunk face culling.
     */
    public void setNeighborAccessor(ChunkNeighborAccessor accessor) {
        this.neighborAccessor = accessor;
    }
    
    /**
     * Clear all collected face data.
     */
    public void clear() {
        topFaces.clear();
        bottomFaces.clear();
        northFaces.clear();
        southFaces.clear();
        westFaces.clear();
        eastFaces.clear();
    }
    
    /**
     * Collect face data for a single block (for batched rendering).
     * Only collects faces that are exposed to air (face culling).
     */
    public void collectBlockFaces(float x, float y, float z, Block block, LevelChunk chunk, 
                                  int cx, int cy, int cz) {
        // Check if this block uses custom rendering (e.g., stairs)
        if (block.hasCustomRendering()) {
            // For stairs blocks, add a special marker that MeshBuilder will handle
            int color = 0xFFFFFF;
            // Add to topFaces with a special marker
            topFaces.add(new FaceData(x, y, z, color, 1f, 1f, block, "stairs", null));
            return;
        }
        
        // Use white color (0xFFFFFF) by default - textures will show their natural colors
        // Fallback magenta color will only be applied if texture is missing (handled in bindTextureForBlock)
        int color = 0xFFFFFF;
        
        // Track which faces are visible for outline rendering
        boolean topVisible = shouldRenderFace(chunk, cx, cy + 1, cz);
        boolean bottomVisible = shouldRenderFace(chunk, cx, cy - 1, cz);
        boolean northVisible = shouldRenderFace(chunk, cx, cy, cz - 1);
        boolean southVisible = shouldRenderFace(chunk, cx, cy, cz + 1);
        boolean westVisible = shouldRenderFace(chunk, cx - 1, cy, cz);
        boolean eastVisible = shouldRenderFace(chunk, cx + 1, cy, cz);
        
        // Collect visible faces for batched rendering
        // Store both the adjusted color and the brightness factor for fallback color
        if (topVisible) {
            topFaces.add(new FaceData(x, y, z, color, 1f, 1f, block, "top", 
                BlockFaceGeometry::drawTopFace));
        }
        if (bottomVisible) {
            bottomFaces.add(new FaceData(x, y, z, ColorUtils.darkenColor(color), 1f, 0.5f, block, "bottom", 
                BlockFaceGeometry::drawBottomFace));
        }
        if (northVisible) {
            northFaces.add(new FaceData(x, y, z, ColorUtils.adjustColorBrightness(color, 0.8f), 1f, 0.8f, block, "side", 
                BlockFaceGeometry::drawNorthFace));
        }
        if (southVisible) {
            southFaces.add(new FaceData(x, y, z, ColorUtils.adjustColorBrightness(color, 0.8f), 1f, 0.8f, block, "side", 
                BlockFaceGeometry::drawSouthFace));
        }
        if (westVisible) {
            westFaces.add(new FaceData(x, y, z, ColorUtils.adjustColorBrightness(color, 0.6f), 1f, 0.6f, block, "side", 
                BlockFaceGeometry::drawWestFace));
        }
        if (eastVisible) {
            eastFaces.add(new FaceData(x, y, z, ColorUtils.adjustColorBrightness(color, 0.6f), 1f, 0.6f, block, "side", 
                BlockFaceGeometry::drawEastFace));
        }
    }
    
    /**
     * Check if a face should be rendered (is the adjacent block air?).
     */
    private boolean shouldRenderFace(LevelChunk chunk, int x, int y, int z) {
        // Check Y bounds first (no neighboring chunks in Y direction)
        if (y < 0 || y >= LevelChunk.HEIGHT) {
            return true; // Out of world bounds, render the face
        }
        
        // If within chunk bounds, use direct chunk access
        if (x >= 0 && x < LevelChunk.WIDTH && z >= 0 && z < LevelChunk.DEPTH) {
            return chunk.getBlock(x, y, z).isAir();
        }
        
        // Out of chunk bounds in X or Z - need to check neighboring chunk
        if (neighborAccessor != null) {
            Block adjacentBlock = neighborAccessor.getBlockAcrossChunks(chunk, x, y, z);
            return adjacentBlock.isAir();
        }
        
        // No neighbor accessor available - fall back to old behavior
        return true;
    }
    
    // Getters for face lists
    public List<FaceData> getTopFaces() { return topFaces; }
    public List<FaceData> getBottomFaces() { return bottomFaces; }
    public List<FaceData> getNorthFaces() { return northFaces; }
    public List<FaceData> getSouthFaces() { return southFaces; }
    public List<FaceData> getWestFaces() { return westFaces; }
    public List<FaceData> getEastFaces() { return eastFaces; }
    
    /**
     * Functional interface for rendering a single face.
     */
    @FunctionalInterface
    public interface FaceRenderer {
        void render(float x, float y, float z);
    }
    
    /**
     * Data class to store face rendering information.
     */
    public static class FaceData {
        public final float x, y, z;
        public final int color;
        public final float brightness;
        public final float colorBrightness; // Brightness adjustment for the base color (for fallback)
        public final Block block;
        public final String faceType; // "top", "bottom", "side", etc.
        public final FaceRenderer renderer; // The renderer method to use for drawing this face
        
        public FaceData(float x, float y, float z, int color, float brightness, float colorBrightness, 
                       Block block, String faceType, FaceRenderer renderer) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
            this.brightness = brightness;
            this.colorBrightness = colorBrightness;
            this.block = block;
            this.faceType = faceType;
            this.renderer = renderer;
        }
    }
}
