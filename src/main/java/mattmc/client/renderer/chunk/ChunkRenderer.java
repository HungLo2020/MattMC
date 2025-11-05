package mattmc.client.renderer.chunk;

import mattmc.client.Minecraft;
import mattmc.client.renderer.ColorUtils;
import mattmc.client.renderer.texture.Texture;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.chunk.ChunkUtils;
import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.client.renderer.block.BlockFaceGeometry;
import mattmc.client.renderer.texture.TextureManager;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles rendering of chunks and blocks.
 * Similar to Minecraft's ChunkRenderer/BlockRenderer classes.
 * 
 * Optimizations:
 * - Display lists: Pre-compile chunk geometry into OpenGL display lists (massive performance boost)
 * - LevelChunk sections: Divide chunk into 16x16x16 sections, skip empty sections
 * - Face culling: Only render faces adjacent to air
 */
public class ChunkRenderer {
    
    // LevelChunk section size (16x16x16 blocks, same as Minecraft)
    private static final int SECTION_SIZE = 16;
    private static final int SECTIONS_PER_CHUNK = LevelChunk.HEIGHT / SECTION_SIZE;  // 384 / 16 = 24 sections
    
    // Display list cache: maps chunks to their compiled display lists
    // This is similar to Minecraft's chunk rendering optimization
    private final Map<LevelChunk, Integer> displayListCache = new HashMap<>();
    
    // VAO cache: maps chunks to their VAOs (new VBO/VAO rendering)
    private final Map<LevelChunk, ChunkVAO> vaoCache = new HashMap<>();
    
    // Cache for chunk key to chunk mapping (for mesh data uploads)
    private final Map<Long, LevelChunk> chunkByKey = new HashMap<>();
    
    // Texture manager for loading and binding block textures
    private final TextureManager textureManager = new TextureManager();
    
    // Flag to enable VBO/VAO rendering (set to true to use new system)
    private boolean useVBORendering = true;
    
    /**
     * Render a chunk using either VBO/VAO or display list.
     * If the chunk hasn't been compiled yet, compile it first.
     */
    public void renderChunk(LevelChunk chunk) {
        if (useVBORendering) {
            renderChunkVAO(chunk);
        } else {
            renderChunkDisplayList(chunk);
        }
    }
    
    /**
     * Render a chunk using VAO/VBO (new method).
     */
    private void renderChunkVAO(LevelChunk chunk) {
        // Check if chunk has been marked as dirty
        if (chunk.isDirty()) {
            invalidateChunk(chunk);
            chunk.setDirty(false);
        }
        
        // Get or create VAO
        ChunkVAO vao = vaoCache.get(chunk);
        if (vao == null) {
            // VAO not ready yet - it will be uploaded later from async mesh building
            // Chunks will appear once mesh buffer is uploaded from worker thread
            return;
        }
        
        // Track chunk by key for mesh data uploads
        long key = chunkKey(chunk.chunkX(), chunk.chunkZ());
        chunkByKey.put(key, chunk);
        
        // Render using VAO (single draw call!)
        vao.render();
    }
    
    /**
     * Render a chunk using a cached display list (old method).
     * If the chunk hasn't been compiled yet, compile it first.
     * Display lists are 10-100x faster than immediate mode rendering.
     */
    private void renderChunkDisplayList(LevelChunk chunk) {
        // Check if chunk has been marked as dirty
        if (chunk.isDirty()) {
            // Recompile display list
            invalidateChunk(chunk);
            chunk.setDirty(false);
        }
        
        // Get or create display list
        Integer displayList = displayListCache.get(chunk);
        if (displayList == null) {
            displayList = compileChunkToDisplayList(chunk);
            displayListCache.put(chunk, displayList);
        }
        
        // Track chunk by key for mesh data uploads
        long key = chunkKey(chunk.chunkX(), chunk.chunkZ());
        chunkByKey.put(key, chunk);
        
        // Render the display list (single draw call for entire chunk!)
        glCallList(displayList);
    }
    
    /**
     * Upload mesh data to GPU and create display list.
     * This is called on the render thread with pre-built mesh data from a worker thread.
     * Returns true if upload was successful.
     */
    public boolean uploadMeshData(ChunkMeshData meshData) {
        long key = chunkKey(meshData.getChunkX(), meshData.getChunkZ());
        LevelChunk chunk = chunkByKey.get(key);
        
        if (chunk == null) {
            // Chunk not loaded yet or was unloaded
            return false;
        }
        
        // Remove old display list if it exists
        invalidateChunk(chunk);
        
        // Create new display list from mesh data
        int displayList = compileChunkToDisplayListFromMesh(meshData.getFaceCollector());
        displayListCache.put(chunk, displayList);
        
        return true;
    }
    
    /**
     * Upload mesh buffer to GPU and create VAO (new VBO/VAO method).
     * This is called on the render thread with pre-built mesh buffer from a worker thread.
     * Returns true if upload was successful.
     */
    public boolean uploadMeshBuffer(ChunkMeshBuffer meshBuffer) {
        long key = chunkKey(meshBuffer.getChunkX(), meshBuffer.getChunkZ());
        LevelChunk chunk = chunkByKey.get(key);
        
        if (chunk == null) {
            // Chunk not loaded yet or was unloaded
            return false;
        }
        
        // Remove old VAO if it exists
        ChunkVAO oldVAO = vaoCache.remove(chunk);
        if (oldVAO != null) {
            oldVAO.delete();
        }
        
        // Create new VAO from mesh buffer
        if (!meshBuffer.isEmpty()) {
            ChunkVAO vao = new ChunkVAO(meshBuffer);
            vaoCache.put(chunk, vao);
        }
        
        return true;
    }
    
    /**
     * Invalidate a chunk's rendering resources, forcing it to be rebuilt.
     * Call this when blocks in the chunk are modified.
     */
    public void invalidateChunk(LevelChunk chunk) {
        // Delete display list if it exists
        Integer displayList = displayListCache.remove(chunk);
        if (displayList != null) {
            glDeleteLists(displayList, 1);
        }
        
        // Delete VAO if it exists
        ChunkVAO vao = vaoCache.remove(chunk);
        if (vao != null) {
            vao.delete();
        }
    }
    
    /**
     * Compile all chunk geometry into a display list for fast rendering.
     * This is called once per chunk (or when chunk is modified).
     */
    private int compileChunkToDisplayList(LevelChunk chunk) {
        int displayList = glGenLists(1);
        glNewList(displayList, GL_COMPILE);
        
        renderChunkImmediate(chunk);
        
        glEndList();
        return displayList;
    }
    
    /**
     * Compile pre-built mesh data into a display list.
     * The mesh data was prepared on a background thread.
     */
    private int compileChunkToDisplayListFromMesh(BlockFaceCollector collector) {
        int displayList = glGenLists(1);
        glNewList(displayList, GL_COMPILE);
        
        // Render using pre-collected faces
        renderBatchedFaces(collector);
        
        glEndList();
        return displayList;
    }
    
    /**
     * Helper to create chunk key from coordinates.
     */
    private static long chunkKey(int chunkX, int chunkZ) {
        return ChunkUtils.chunkKey(chunkX, chunkZ);
    }
    
    /**
     * Remove a chunk from the tracking cache.
     * Call this when a chunk is unloaded.
     */
    public void removeChunkFromCache(LevelChunk chunk) {
        long key = chunkKey(chunk.chunkX(), chunk.chunkZ());
        chunkByKey.remove(key);
    }
    
    /**
     * Render all blocks in a chunk with face culling and section optimization.
     * 
     * Uses chunk sections (16x16x16) to skip large empty areas.
     * Each chunk has 24 vertical sections. Empty sections are skipped entirely.
     * 
     * PERFORMANCE CRITICAL: Batches all geometry into single glBegin/glEnd blocks.
     * This reduces draw calls from ~50,000 to ~7 per chunk (100x+ speedup).
     */
    private void renderChunkImmediate(LevelChunk chunk) {
        // Collect all face geometry using the face collector
        BlockFaceCollector collector = new BlockFaceCollector();
        
        // Iterate by sections (16 blocks tall each)
        for (int sectionIndex = 0; sectionIndex < SECTIONS_PER_CHUNK; sectionIndex++) {
            int sectionStartY = sectionIndex * SECTION_SIZE;
            int sectionEndY = Math.min(sectionStartY + SECTION_SIZE, LevelChunk.HEIGHT);
            
            // Quick check: is this section completely empty?
            if (isSectionEmpty(chunk, sectionStartY, sectionEndY)) {
                continue;  // Skip entire section (saves 4,096 block checks)
            }
            
            // Collect block face data in this section
            for (int x = 0; x < LevelChunk.WIDTH; x++) {
                for (int y = sectionStartY; y < sectionEndY; y++) {
                    for (int z = 0; z < LevelChunk.DEPTH; z++) {
                        Block block = chunk.getBlock(x, y, z);
                        if (block.isAir()) continue;  // Skip air blocks
                        
                        // Calculate world position
                        float wx = x;
                        float wy = LevelChunk.chunkYToWorldY(y);
                        float wz = z;
                        
                        // Collect visible faces
                        collector.collectBlockFaces(wx, wy, wz, block, chunk, x, y, z);
                    }
                }
            }
        }
        
        // Now render all collected faces in batched glBegin/glEnd blocks
        // This is MUCH faster than individual glBegin/glEnd per face
        renderBatchedFaces(collector);
    }
    

    
    /**
     * Check if a chunk section is completely empty (all air blocks).
     * This allows us to skip entire 16x16x16 sections very quickly.
     */
    private boolean isSectionEmpty(LevelChunk chunk, int startY, int endY) {
        return ChunkUtils.isSectionEmpty(chunk, startY, endY);
    }
    
    /**
     * Render all collected faces in batched glBegin/glEnd blocks.
     * This is CRITICAL for performance - reduces draw calls from ~50,000 to ~7 per chunk.
     */
    private void renderBatchedFaces(BlockFaceCollector collector) {
        // Enable texturing
        glEnable(GL_TEXTURE_2D);
        
        // Render faces grouped by block type (to minimize texture bindings)
        renderFacesByType(collector.getTopFaces());
        renderFacesByType(collector.getBottomFaces());
        renderFacesByType(collector.getNorthFaces());
        renderFacesByType(collector.getSouthFaces());
        renderFacesByType(collector.getWestFaces());
        renderFacesByType(collector.getEastFaces());
        
        // Render overlays AFTER all base textures (for grass_block sides)
        // Collect all side faces for overlay rendering
        List<BlockFaceCollector.FaceData> allSideFaces = new ArrayList<>();
        allSideFaces.addAll(collector.getNorthFaces());
        allSideFaces.addAll(collector.getSouthFaces());
        allSideFaces.addAll(collector.getWestFaces());
        allSideFaces.addAll(collector.getEastFaces());
        renderOverlaysForSideFaces(allSideFaces);
    }
    
    /**
     * Render faces grouped by block and texture to minimize texture binding
     */
    private void renderFacesByType(List<BlockFaceCollector.FaceData> faces) {
        if (faces.isEmpty()) return;
        
        // Group faces by block and texture
        Map<String, List<BlockFaceCollector.FaceData>> facesByTexture = new HashMap<>();
        for (BlockFaceCollector.FaceData face : faces) {
            // Get texture path for this face
            String texturePath = face.block.getTexturePath(face.faceType);
            if (texturePath == null) {
                texturePath = "fallback"; // Use special key for fallback
            }
            facesByTexture.computeIfAbsent(texturePath, k -> new ArrayList<>()).add(face);
        }
        
        // Render each texture group
        for (Map.Entry<String, List<BlockFaceCollector.FaceData>> entry : facesByTexture.entrySet()) {
            String texturePath = entry.getKey();
            List<BlockFaceCollector.FaceData> textureFaces = entry.getValue();
            
            // Check if this is a fallback render
            boolean hasFallback = texturePath.equals("fallback");
            boolean hasTexture = false;
            
            if (!hasFallback) {
                int textureId = textureManager.loadTexture(texturePath);
                hasTexture = (textureId != 0);
                
                if (hasTexture) {
                    textureManager.bindTexture(textureId);
                } else {
                    textureManager.unbindTexture();
                }
            } else {
                textureManager.unbindTexture();
            }
            
            // Render all faces with this texture
            glBegin(GL_TRIANGLES);
            for (BlockFaceCollector.FaceData face : textureFaces) {
                // Use fallback magenta color if no texture, otherwise use the face color (white)
                int renderColor = (hasTexture && !hasFallback) ? face.color : 
                    ColorUtils.adjustColorBrightness(face.block.getFallbackColor(), face.colorBrightness);
                
                // Apply grass green tint for grass_block top face (vanilla Minecraft-like)
                if (face.block == Blocks.GRASS_BLOCK && "top".equals(face.faceType)) {
                    renderColor = ColorUtils.applyTint(renderColor, 0x5BB53B, face.colorBrightness);
                }
                
                ColorUtils.setGLColor(renderColor, face.brightness);
                face.renderer.render(face.x, face.y, face.z);
            }
            glEnd();
        }
    }
    
    /**
     * Render overlay textures for side faces that have them (e.g., grass_block sides).
     * This is called AFTER all base textures are rendered to ensure overlays appear on top.
     */
    private void renderOverlaysForSideFaces(List<BlockFaceCollector.FaceData> sideFaces) {
        if (sideFaces.isEmpty()) return;
        
        // Group side faces by block to check for overlay texture
        Map<Block, List<BlockFaceCollector.FaceData>> facesByBlock = new HashMap<>();
        
        for (BlockFaceCollector.FaceData face : sideFaces) {
            // Only process side faces
            if ("side".equals(face.faceType)) {
                Block block = face.block;
                
                // Check if block has overlay texture
                String overlayPath = block.getTexturePath("overlay");
                if (overlayPath != null) {
                    facesByBlock.computeIfAbsent(block, k -> new ArrayList<>()).add(face);
                }
            }
        }
        
        if (facesByBlock.isEmpty()) {
            return; // No overlays to render
        }
        
        // Enable alpha blending for overlay transparency
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Disable depth writing so overlays don't block other geometry
        // But keep depth testing enabled so they respect existing depth
        glDepthMask(false);
        
        // Use polygon offset to prevent z-fighting with base texture
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(-1.0f, -1.0f);
        
        // Render overlays for each block type
        for (Map.Entry<Block, List<BlockFaceCollector.FaceData>> blockEntry : facesByBlock.entrySet()) {
            Block block = blockEntry.getKey();
            List<BlockFaceCollector.FaceData> blockFaces = blockEntry.getValue();
            
            // Get overlay texture
            String overlayPath = block.getTexturePath("overlay");
            if (overlayPath == null) {
                continue;
            }
            
            int overlayTextureId = textureManager.loadTexture(overlayPath);
            if (overlayTextureId == 0) {
                continue; // Overlay texture not found
            }
            
            // Bind overlay texture
            textureManager.bindTexture(overlayTextureId);
            
            // Render all side faces with the overlay texture
            glBegin(GL_TRIANGLES);
            for (BlockFaceCollector.FaceData face : blockFaces) {
                // Apply grass green tint for grass_block overlay (vanilla Minecraft-like)
                int tintedColor = face.color;
                if (block == Blocks.GRASS_BLOCK) {
                    // Grass green tint: 0x5BB53B (RGB 91, 181, 59)
                    tintedColor = ColorUtils.applyTint(face.color, 0x5BB53B, face.colorBrightness);
                }
                ColorUtils.setGLColor(tintedColor, face.brightness);
                // Use the renderer that was stored in the FaceData
                face.renderer.render(face.x, face.y, face.z);
            }
            glEnd();
        }
        
        // Restore OpenGL state
        glDisable(GL_POLYGON_OFFSET_FILL);
        glDepthMask(true);
        glDisable(GL_BLEND);
    }
    
    /**
     * Set whether to use VBO/VAO rendering (true) or display lists (false).
     * Default is true (VBO rendering).
     */
    public void setUseVBORendering(boolean useVBO) {
        this.useVBORendering = useVBO;
    }
    
    /**
     * Get whether VBO/VAO rendering is enabled.
     */
    public boolean isUsingVBORendering() {
        return useVBORendering;
    }
}
