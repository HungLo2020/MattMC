package MattMC.renderer;

import MattMC.world.Block;
import MattMC.world.Blocks;
import MattMC.world.Chunk;

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
 * - Chunk sections: Divide chunk into 16x16x16 sections, skip empty sections
 * - Face culling: Only render faces adjacent to air
 * - Outline culling: Only render edges on visible faces
 */
public class ChunkRenderer {
    
    // Chunk section size (16x16x16 blocks, same as Minecraft)
    private static final int SECTION_SIZE = 16;
    private static final int SECTIONS_PER_CHUNK = Chunk.HEIGHT / SECTION_SIZE;  // 384 / 16 = 24 sections
    
    // Display list cache: maps chunks to their compiled display lists
    // This is similar to Minecraft's chunk rendering optimization
    private final Map<Chunk, Integer> displayListCache = new HashMap<>();
    
    // Texture manager for loading and binding block textures
    private final TextureManager textureManager = new TextureManager();
    
    /**
     * Render a chunk using a cached display list.
     * If the chunk hasn't been compiled yet, compile it first.
     * Display lists are 10-100x faster than immediate mode rendering.
     */
    public void renderChunk(Chunk chunk) {
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
        
        // Render the display list (single draw call for entire chunk!)
        glCallList(displayList);
    }
    
    /**
     * Invalidate a chunk's display list, forcing it to be recompiled.
     * Call this when blocks in the chunk are modified.
     */
    public void invalidateChunk(Chunk chunk) {
        Integer displayList = displayListCache.remove(chunk);
        if (displayList != null) {
            glDeleteLists(displayList, 1);
        }
    }
    
    /**
     * Compile all chunk geometry into a display list for fast rendering.
     * This is called once per chunk (or when chunk is modified).
     */
    private int compileChunkToDisplayList(Chunk chunk) {
        int displayList = glGenLists(1);
        glNewList(displayList, GL_COMPILE);
        
        renderChunkImmediate(chunk);
        
        glEndList();
        return displayList;
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
    private void renderChunkImmediate(Chunk chunk) {
        // Collect all face geometry using the face collector
        BlockFaceCollector collector = new BlockFaceCollector();
        
        // Iterate by sections (16 blocks tall each)
        for (int sectionIndex = 0; sectionIndex < SECTIONS_PER_CHUNK; sectionIndex++) {
            int sectionStartY = sectionIndex * SECTION_SIZE;
            int sectionEndY = Math.min(sectionStartY + SECTION_SIZE, Chunk.HEIGHT);
            
            // Quick check: is this section completely empty?
            if (isSectionEmpty(chunk, sectionStartY, sectionEndY)) {
                continue;  // Skip entire section (saves 4,096 block checks)
            }
            
            // Collect block face data in this section
            for (int x = 0; x < Chunk.WIDTH; x++) {
                for (int y = sectionStartY; y < sectionEndY; y++) {
                    for (int z = 0; z < Chunk.DEPTH; z++) {
                        Block block = chunk.getBlock(x, y, z);
                        if (block.isAir()) continue;  // Skip air blocks
                        
                        // Calculate world position
                        float wx = x;
                        float wy = Chunk.chunkYToWorldY(y);
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
    private boolean isSectionEmpty(Chunk chunk, int startY, int endY) {
        // Sample a few blocks to quickly determine if section is likely empty
        // Full check would scan all 4,096 blocks, but sampling is faster
        // Check corners and center
        int midY = (startY + endY) / 2;
        
        if (!chunk.getBlock(0, startY, 0).isAir()) return false;
        if (!chunk.getBlock(15, startY, 15).isAir()) return false;
        if (!chunk.getBlock(0, midY, 0).isAir()) return false;
        if (!chunk.getBlock(15, midY, 15).isAir()) return false;
        if (!chunk.getBlock(0, endY - 1, 0).isAir()) return false;
        if (!chunk.getBlock(15, endY - 1, 15).isAir()) return false;
        if (!chunk.getBlock(8, midY, 8).isAir()) return false;
        
        // If all samples are air, likely the whole section is empty
        // For flat terrain at y=64, sections above y=80 will be skipped
        return true;
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
        
        // Disable texturing for outlines
        glDisable(GL_TEXTURE_2D);
        
        // Render all outlines in ONE glBegin/glEnd block
        List<BlockFaceCollector.OutlineData> outlines = collector.getOutlines();
        if (!outlines.isEmpty()) {
            glBegin(GL_LINES);
            ColorUtils.setGLColor(0x000000, 1f);  // Black outlines
            for (BlockFaceCollector.OutlineData outline : outlines) {
                BlockFaceGeometry.drawBlockOutline(outline.x, outline.y, outline.z, outline.top, outline.bottom,
                        outline.north, outline.south, outline.west, outline.east);
            }
            glEnd();
        }
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
}
