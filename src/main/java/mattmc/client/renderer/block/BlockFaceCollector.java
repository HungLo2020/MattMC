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
            // Get the blockstate for this position
            mattmc.world.level.block.state.BlockState state = chunk.getBlockState(cx, cy, cz);
            int color = 0xFFFFFF;
            
            // Calculate brightness from light level (0-15) to (0.0-1.0)
            // Minecraft uses a minimum brightness to ensure blocks aren't completely black
            int lightLevel = chunk.getLightLevel(cx, cy, cz);
            float lightBrightness = calculateBrightnessFromLightLevel(lightLevel);
            
            // Add to topFaces with a special marker, storing blockstate in the FaceData
            topFaces.add(new FaceData(x, y, z, color, lightBrightness, lightBrightness, block, "stairs", null, state));
            return;
        }
        
        // Use white color (0xFFFFFF) by default - textures will show their natural colors
        // Fallback magenta color will only be applied if texture is missing (handled in bindTextureForBlock)
        int color = 0xFFFFFF;
        
        // Get the light level for this block position (0-15)
        int lightLevel = chunk.getLightLevel(cx, cy, cz);
        float lightBrightness = calculateBrightnessFromLightLevel(lightLevel);
        
        // Track which faces are visible for outline rendering
        boolean topVisible = shouldRenderFace(chunk, cx, cy + 1, cz);
        boolean bottomVisible = shouldRenderFace(chunk, cx, cy - 1, cz);
        boolean northVisible = shouldRenderFace(chunk, cx, cy, cz - 1);
        boolean southVisible = shouldRenderFace(chunk, cx, cy, cz + 1);
        boolean westVisible = shouldRenderFace(chunk, cx - 1, cy, cz);
        boolean eastVisible = shouldRenderFace(chunk, cx + 1, cy, cz);
        
        // Collect visible faces for batched rendering
        // Combine directional brightness (for shading) with light level brightness
        // Store both the adjusted color and the brightness factor for fallback color
        if (topVisible) {
            topFaces.add(new FaceData(x, y, z, color, lightBrightness * 1.0f, lightBrightness * 1.0f, block, "top", 
                BlockFaceGeometry::drawTopFace));
        }
        if (bottomVisible) {
            bottomFaces.add(new FaceData(x, y, z, ColorUtils.darkenColor(color), lightBrightness * 0.5f, lightBrightness * 0.5f, block, "bottom", 
                BlockFaceGeometry::drawBottomFace));
        }
        if (northVisible) {
            northFaces.add(new FaceData(x, y, z, ColorUtils.adjustColorBrightness(color, 0.8f), lightBrightness * 0.8f, lightBrightness * 0.8f, block, "side", 
                BlockFaceGeometry::drawNorthFace));
        }
        if (southVisible) {
            southFaces.add(new FaceData(x, y, z, ColorUtils.adjustColorBrightness(color, 0.8f), lightBrightness * 0.8f, lightBrightness * 0.8f, block, "side", 
                BlockFaceGeometry::drawSouthFace));
        }
        if (westVisible) {
            westFaces.add(new FaceData(x, y, z, ColorUtils.adjustColorBrightness(color, 0.6f), lightBrightness * 0.6f, lightBrightness * 0.6f, block, "side", 
                BlockFaceGeometry::drawWestFace));
        }
        if (eastVisible) {
            eastFaces.add(new FaceData(x, y, z, ColorUtils.adjustColorBrightness(color, 0.6f), lightBrightness * 0.6f, lightBrightness * 0.6f, block, "side", 
                BlockFaceGeometry::drawEastFace));
        }
    }
    
    /**
     * Convert a light level (0-15) to a brightness multiplier (0.0-1.0).
     * Uses a custom formula to ensure visibility even at low light levels.
     * 
     * @param lightLevel Light level from 0 (dark) to 15 (bright)
     * @return Brightness multiplier from ~0.25 (minimum) to 1.0 (maximum)
     */
    private float calculateBrightnessFromLightLevel(int lightLevel) {
        // Adjusted formula: brightness = 0.25 + (lightLevel / 15.0) * 0.75
        // This ensures blocks are never too dark (minimum 25% brightness)
        // and full light (15) gives 100% brightness
        // Light level 0: 0.25 (25%)
        // Light level 7: 0.60 (60%)
        // Light level 15: 1.00 (100%)
        return 0.25f + (lightLevel / 15.0f) * 0.75f;
    }
    
    /**
     * Check if a face should be rendered (is the adjacent block air or transparent?).
     */
    private boolean shouldRenderFace(LevelChunk chunk, int x, int y, int z) {
        // Check Y bounds first (no neighboring chunks in Y direction)
        if (y < 0 || y >= LevelChunk.HEIGHT) {
            return true; // Out of world bounds, render the face
        }
        
        // If within chunk bounds, use direct chunk access
        if (x >= 0 && x < LevelChunk.WIDTH && z >= 0 && z < LevelChunk.DEPTH) {
            Block adjacent = chunk.getBlock(x, y, z);
            return adjacent.isAir() || !adjacent.isSolid();  // Render if air or transparent (non-solid)
        }
        
        // Out of chunk bounds in X or Z - need to check neighboring chunk
        if (neighborAccessor != null) {
            Block adjacentBlock = neighborAccessor.getBlockAcrossChunks(chunk, x, y, z);
            return adjacentBlock.isAir() || !adjacentBlock.isSolid();  // Render if air or transparent
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
        public final mattmc.world.level.block.state.BlockState blockState; // Block state for custom rendering
        
        public FaceData(float x, float y, float z, int color, float brightness, float colorBrightness, 
                       Block block, String faceType, FaceRenderer renderer) {
            this(x, y, z, color, brightness, colorBrightness, block, faceType, renderer, null);
        }
        
        public FaceData(float x, float y, float z, int color, float brightness, float colorBrightness, 
                       Block block, String faceType, FaceRenderer renderer, mattmc.world.level.block.state.BlockState blockState) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
            this.brightness = brightness;
            this.colorBrightness = colorBrightness;
            this.block = block;
            this.faceType = faceType;
            this.renderer = renderer;
            this.blockState = blockState;
        }
    }
}
