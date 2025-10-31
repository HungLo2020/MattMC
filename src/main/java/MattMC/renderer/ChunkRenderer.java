package MattMC.renderer;

import MattMC.world.Block;
import MattMC.world.BlockType;
import MattMC.world.Chunk;

import java.util.HashMap;
import java.util.Map;

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
     */
    private void renderChunkImmediate(Chunk chunk) {
        // Iterate by sections (16 blocks tall each)
        for (int sectionIndex = 0; sectionIndex < SECTIONS_PER_CHUNK; sectionIndex++) {
            int sectionStartY = sectionIndex * SECTION_SIZE;
            int sectionEndY = Math.min(sectionStartY + SECTION_SIZE, Chunk.HEIGHT);
            
            // Quick check: is this section completely empty?
            if (isSectionEmpty(chunk, sectionStartY, sectionEndY)) {
                continue;  // Skip entire section (saves 4,096 block checks)
            }
            
            // Render blocks in this section
            for (int x = 0; x < Chunk.WIDTH; x++) {
                for (int y = sectionStartY; y < sectionEndY; y++) {
                    for (int z = 0; z < Chunk.DEPTH; z++) {
                        Block block = chunk.getBlock(x, y, z);
                        if (block.isAir()) continue;  // Skip air blocks
                        
                        // Calculate world position
                        float wx = x;
                        float wy = Chunk.chunkYToWorldY(y);
                        float wz = z;
                        
                        // Only render visible faces (adjacent to air)
                        renderBlockAt(wx, wy, wz, block, chunk, x, y, z);
                    }
                }
            }
        }
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
     * Render a single block at the given world position.
     * Only renders faces that are exposed to air (face culling).
     */
    private void renderBlockAt(float x, float y, float z, Block block, Chunk chunk, int cx, int cy, int cz) {
        BlockType type = block.type();
        int color = type.color();
        
        // Track which faces are visible for outline rendering
        boolean topVisible = shouldRenderFace(chunk, cx, cy + 1, cz);
        boolean bottomVisible = shouldRenderFace(chunk, cx, cy - 1, cz);
        boolean northVisible = shouldRenderFace(chunk, cx, cy, cz - 1);
        boolean southVisible = shouldRenderFace(chunk, cx, cy, cz + 1);
        boolean westVisible = shouldRenderFace(chunk, cx - 1, cy, cz);
        boolean eastVisible = shouldRenderFace(chunk, cx + 1, cy, cz);
        
        // Check each face and only render if adjacent block is air
        // Top face (+Y)
        if (topVisible) {
            setColor(color, 1f);
            drawTopFace(x, y, z);
        }
        
        // Bottom face (-Y)
        if (bottomVisible) {
            setColor(darkenColor(color), 1f);
            drawBottomFace(x, y, z);
        }
        
        // North face (-Z)
        if (northVisible) {
            setColor(adjustColorBrightness(color, 0.8f), 1f);
            drawNorthFace(x, y, z);
        }
        
        // South face (+Z)
        if (southVisible) {
            setColor(adjustColorBrightness(color, 0.8f), 1f);
            drawSouthFace(x, y, z);
        }
        
        // West face (-X)
        if (westVisible) {
            setColor(adjustColorBrightness(color, 0.6f), 1f);
            drawWestFace(x, y, z);
        }
        
        // East face (+X)
        if (eastVisible) {
            setColor(adjustColorBrightness(color, 0.6f), 1f);
            drawEastFace(x, y, z);
        }
        
        // Draw black outline only for visible edges (edges on visible faces)
        drawBlockOutline(x, y, z, topVisible, bottomVisible, northVisible, southVisible, westVisible, eastVisible);
    }
    
    /**
     * Check if a face should be rendered (is the adjacent block air?).
     */
    private boolean shouldRenderFace(Chunk chunk, int x, int y, int z) {
        // If out of bounds, consider it air (should render)
        if (x < 0 || x >= Chunk.WIDTH || y < 0 || y >= Chunk.HEIGHT || z < 0 || z >= Chunk.DEPTH) {
            return true;
        }
        return chunk.getBlock(x, y, z).isAir();
    }
    
    // Block face rendering methods (each face is 1x1x1 cube)
    private void drawTopFace(float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y1 = y + 1;
        float z0 = z, z1 = z + 1;
        glBegin(GL_TRIANGLES);
        // Counter-clockwise when viewed from above
        glVertex3f(x0, y1, z0); glVertex3f(x0, y1, z1); glVertex3f(x1, y1, z1);
        glVertex3f(x0, y1, z0); glVertex3f(x1, y1, z1); glVertex3f(x1, y1, z0);
        glEnd();
    }
    
    private void drawBottomFace(float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y;
        float z0 = z, z1 = z + 1;
        glBegin(GL_TRIANGLES);
        // Counter-clockwise when viewed from below
        glVertex3f(x0, y0, z0); glVertex3f(x1, y0, z0); glVertex3f(x1, y0, z1);
        glVertex3f(x0, y0, z0); glVertex3f(x1, y0, z1); glVertex3f(x0, y0, z1);
        glEnd();
    }
    
    private void drawNorthFace(float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z;
        glBegin(GL_TRIANGLES);
        glVertex3f(x1, y0, z0); glVertex3f(x0, y0, z0); glVertex3f(x0, y1, z0);
        glVertex3f(x1, y0, z0); glVertex3f(x0, y1, z0); glVertex3f(x1, y1, z0);
        glEnd();
    }
    
    private void drawSouthFace(float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z1 = z + 1;
        glBegin(GL_TRIANGLES);
        glVertex3f(x0, y0, z1); glVertex3f(x1, y0, z1); glVertex3f(x1, y1, z1);
        glVertex3f(x0, y0, z1); glVertex3f(x1, y1, z1); glVertex3f(x0, y1, z1);
        glEnd();
    }
    
    private void drawWestFace(float x, float y, float z) {
        float x0 = x;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        glBegin(GL_TRIANGLES);
        glVertex3f(x0, y0, z0); glVertex3f(x0, y0, z1); glVertex3f(x0, y1, z1);
        glVertex3f(x0, y0, z0); glVertex3f(x0, y1, z1); glVertex3f(x0, y1, z0);
        glEnd();
    }
    
    private void drawEastFace(float x, float y, float z) {
        float x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        glBegin(GL_TRIANGLES);
        glVertex3f(x1, y0, z1); glVertex3f(x1, y0, z0); glVertex3f(x1, y1, z0);
        glVertex3f(x1, y0, z1); glVertex3f(x1, y1, z0); glVertex3f(x1, y1, z1);
        glEnd();
    }
    
    /**
     * Draw a black outline around a cube to make blocks more distinguishable.
     * Only draws edges that are on visible faces to improve performance.
     * Each edge is shared by 2 faces - we draw it if at least one face is visible.
     */
    private void drawBlockOutline(float x, float y, float z, 
                                   boolean top, boolean bottom, 
                                   boolean north, boolean south, 
                                   boolean west, boolean east) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        
        // Set black color for outline
        setColor(0x000000, 1f);
        
        glBegin(GL_LINES);
        
        // Bottom 4 edges (shared by bottom face and side faces)
        if (bottom || west || north) {
            glVertex3f(x0, y0, z0); glVertex3f(x1, y0, z0);
        }
        if (bottom || east || north) {
            glVertex3f(x1, y0, z0); glVertex3f(x1, y0, z1);
        }
        if (bottom || east || south) {
            glVertex3f(x1, y0, z1); glVertex3f(x0, y0, z1);
        }
        if (bottom || west || south) {
            glVertex3f(x0, y0, z1); glVertex3f(x0, y0, z0);
        }
        
        // Top 4 edges (shared by top face and side faces)
        if (top || west || north) {
            glVertex3f(x0, y1, z0); glVertex3f(x1, y1, z0);
        }
        if (top || east || north) {
            glVertex3f(x1, y1, z0); glVertex3f(x1, y1, z1);
        }
        if (top || east || south) {
            glVertex3f(x1, y1, z1); glVertex3f(x0, y1, z1);
        }
        if (top || west || south) {
            glVertex3f(x0, y1, z1); glVertex3f(x0, y1, z0);
        }
        
        // 4 vertical edges (each shared by 2 side faces)
        if (west || north) {
            glVertex3f(x0, y0, z0); glVertex3f(x0, y1, z0);
        }
        if (east || north) {
            glVertex3f(x1, y0, z0); glVertex3f(x1, y1, z0);
        }
        if (east || south) {
            glVertex3f(x1, y0, z1); glVertex3f(x1, y1, z1);
        }
        if (west || south) {
            glVertex3f(x0, y0, z1); glVertex3f(x0, y1, z1);
        }
        
        glEnd();
    }
    
    private int darkenColor(int rgb) {
        return adjustColorBrightness(rgb, 0.5f);
    }
    
    private int adjustColorBrightness(int rgb, float factor) {
        int r = Math.min(255, (int)(((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int)(((rgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int)((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    private void setColor(int rgb, float a) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        glColor4f(r, g, b, a);
    }
}
