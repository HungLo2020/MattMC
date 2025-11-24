package mattmc.client.renderer.backend.opengl;

import static org.lwjgl.opengl.GL11.*;

/**
 * Generates vertex geometry for individual block faces.
 * Each method draws the vertices for one face of a cube block with texture coordinates.
 */
public final class BlockFaceGeometry {
    
    private BlockFaceGeometry() {} // Prevent instantiation
    
    /**
     * Draw vertices for the top face of a block.
     */
    public static void drawTopFace(float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y1 = y + 1;
        float z0 = z, z1 = z + 1;
        // Counter-clockwise when viewed from above, with texture coordinates
        glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);
        glTexCoord2f(0, 1); glVertex3f(x0, y1, z1);
        glTexCoord2f(1, 1); glVertex3f(x1, y1, z1);
        
        glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);
        glTexCoord2f(1, 1); glVertex3f(x1, y1, z1);
        glTexCoord2f(1, 0); glVertex3f(x1, y1, z0);
    }
    
    /**
     * Draw vertices for the bottom face of a block.
     */
    public static void drawBottomFace(float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y;
        float z0 = z, z1 = z + 1;
        // Counter-clockwise when viewed from below, with texture coordinates
        glTexCoord2f(0, 0); glVertex3f(x0, y0, z0);
        glTexCoord2f(1, 0); glVertex3f(x1, y0, z0);
        glTexCoord2f(1, 1); glVertex3f(x1, y0, z1);
        
        glTexCoord2f(0, 0); glVertex3f(x0, y0, z0);
        glTexCoord2f(1, 1); glVertex3f(x1, y0, z1);
        glTexCoord2f(0, 1); glVertex3f(x0, y0, z1);
    }
    
    /**
     * Draw vertices for the north face of a block.
     */
    public static void drawNorthFace(float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z;
        // With texture coordinates (V flipped to correct upside-down textures)
        glTexCoord2f(1, 1); glVertex3f(x1, y0, z0);
        glTexCoord2f(0, 1); glVertex3f(x0, y0, z0);
        glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);
        
        glTexCoord2f(1, 1); glVertex3f(x1, y0, z0);
        glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);
        glTexCoord2f(1, 0); glVertex3f(x1, y1, z0);
    }
    
    /**
     * Draw vertices for the south face of a block.
     */
    public static void drawSouthFace(float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z1 = z + 1;
        // With texture coordinates (V flipped to correct upside-down textures)
        glTexCoord2f(0, 1); glVertex3f(x0, y0, z1);
        glTexCoord2f(1, 1); glVertex3f(x1, y0, z1);
        glTexCoord2f(1, 0); glVertex3f(x1, y1, z1);
        
        glTexCoord2f(0, 1); glVertex3f(x0, y0, z1);
        glTexCoord2f(1, 0); glVertex3f(x1, y1, z1);
        glTexCoord2f(0, 0); glVertex3f(x0, y1, z1);
    }
    
    /**
     * Draw vertices for the west face of a block.
     */
    public static void drawWestFace(float x, float y, float z) {
        float x0 = x;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        // With texture coordinates (V flipped to correct upside-down textures)
        glTexCoord2f(0, 1); glVertex3f(x0, y0, z0);
        glTexCoord2f(1, 1); glVertex3f(x0, y0, z1);
        glTexCoord2f(1, 0); glVertex3f(x0, y1, z1);
        
        glTexCoord2f(0, 1); glVertex3f(x0, y0, z0);
        glTexCoord2f(1, 0); glVertex3f(x0, y1, z1);
        glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);
    }
    
    /**
     * Draw vertices for the east face of a block.
     */
    public static void drawEastFace(float x, float y, float z) {
        float x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        // With texture coordinates (V flipped to correct upside-down textures)
        glTexCoord2f(1, 1); glVertex3f(x1, y0, z1);
        glTexCoord2f(0, 1); glVertex3f(x1, y0, z0);
        glTexCoord2f(0, 0); glVertex3f(x1, y1, z0);
        
        glTexCoord2f(1, 1); glVertex3f(x1, y0, z1);
        glTexCoord2f(0, 0); glVertex3f(x1, y1, z0);
        glTexCoord2f(1, 0); glVertex3f(x1, y1, z1);
    }
    
    /**
     * Draw a complete outline around a cube (all 12 edges).
     * Used for highlighting the targeted block.
     * Must be called within a GL_LINES block (between glBegin(GL_LINES) and glEnd()).
     */
    public static void drawCompleteBlockOutline(float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        
        // Bottom 4 edges
        glVertex3f(x0, y0, z0); glVertex3f(x1, y0, z0);
        glVertex3f(x1, y0, z0); glVertex3f(x1, y0, z1);
        glVertex3f(x1, y0, z1); glVertex3f(x0, y0, z1);
        glVertex3f(x0, y0, z1); glVertex3f(x0, y0, z0);
        
        // Top 4 edges
        glVertex3f(x0, y1, z0); glVertex3f(x1, y1, z0);
        glVertex3f(x1, y1, z0); glVertex3f(x1, y1, z1);
        glVertex3f(x1, y1, z1); glVertex3f(x0, y1, z1);
        glVertex3f(x0, y1, z1); glVertex3f(x0, y1, z0);
        
        // 4 vertical edges
        glVertex3f(x0, y0, z0); glVertex3f(x0, y1, z0);
        glVertex3f(x1, y0, z0); glVertex3f(x1, y1, z0);
        glVertex3f(x1, y0, z1); glVertex3f(x1, y1, z1);
        glVertex3f(x0, y0, z1); glVertex3f(x0, y1, z1);
    }
    
    /**
     * Draw a black outline around a cube to make blocks more distinguishable.
     * Only draws edges that are on visible faces to improve performance.
     * Each edge is shared by 2 faces - we draw it if at least one face is visible.
     */
    public static void drawBlockOutline(float x, float y, float z, 
                                        boolean top, boolean bottom, 
                                        boolean north, boolean south, 
                                        boolean west, boolean east) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        
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
    }
    
    /**
     * Draw a north-facing bottom stairs block.
     * This draws the geometry for stairs with the step facing north.
     * The stairs consist of a bottom slab (full block, half height) 
     * and a top step (north half only, full height).
     */
    public static void drawStairsNorthBottom(float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y05 = y + 0.5f, y1 = y + 1;
        float z0 = z, z05 = z + 0.5f, z1 = z + 1;
        
        // Bottom slab (0, 0, 0) to (1, 0.5, 1)
        // Top face of bottom slab
        glTexCoord2f(0, 0); glVertex3f(x0, y05, z0);
        glTexCoord2f(0, 1); glVertex3f(x0, y05, z1);
        glTexCoord2f(1, 1); glVertex3f(x1, y05, z1);
        
        glTexCoord2f(0, 0); glVertex3f(x0, y05, z0);
        glTexCoord2f(1, 1); glVertex3f(x1, y05, z1);
        glTexCoord2f(1, 0); glVertex3f(x1, y05, z0);
        
        // Bottom face of slab
        glTexCoord2f(0, 0); glVertex3f(x0, y0, z0);
        glTexCoord2f(1, 0); glVertex3f(x1, y0, z0);
        glTexCoord2f(1, 1); glVertex3f(x1, y0, z1);
        
        glTexCoord2f(0, 0); glVertex3f(x0, y0, z0);
        glTexCoord2f(1, 1); glVertex3f(x1, y0, z1);
        glTexCoord2f(0, 1); glVertex3f(x0, y0, z1);
        
        // North face of slab
        glTexCoord2f(1, 1); glVertex3f(x1, y0, z0);
        glTexCoord2f(0, 1); glVertex3f(x0, y0, z0);
        glTexCoord2f(0, 0.5f); glVertex3f(x0, y05, z0);
        
        glTexCoord2f(1, 1); glVertex3f(x1, y0, z0);
        glTexCoord2f(0, 0.5f); glVertex3f(x0, y05, z0);
        glTexCoord2f(1, 0.5f); glVertex3f(x1, y05, z0);
        
        // South face of slab
        glTexCoord2f(0, 1); glVertex3f(x0, y0, z1);
        glTexCoord2f(1, 1); glVertex3f(x1, y0, z1);
        glTexCoord2f(1, 0.5f); glVertex3f(x1, y05, z1);
        
        glTexCoord2f(0, 1); glVertex3f(x0, y0, z1);
        glTexCoord2f(1, 0.5f); glVertex3f(x1, y05, z1);
        glTexCoord2f(0, 0.5f); glVertex3f(x0, y05, z1);
        
        // West face of slab
        glTexCoord2f(0, 1); glVertex3f(x0, y0, z0);
        glTexCoord2f(1, 1); glVertex3f(x0, y0, z1);
        glTexCoord2f(1, 0.5f); glVertex3f(x0, y05, z1);
        
        glTexCoord2f(0, 1); glVertex3f(x0, y0, z0);
        glTexCoord2f(1, 0.5f); glVertex3f(x0, y05, z1);
        glTexCoord2f(0, 0.5f); glVertex3f(x0, y05, z0);
        
        // East face of slab
        glTexCoord2f(1, 1); glVertex3f(x1, y0, z1);
        glTexCoord2f(0, 1); glVertex3f(x1, y0, z0);
        glTexCoord2f(0, 0.5f); glVertex3f(x1, y05, z0);
        
        glTexCoord2f(1, 1); glVertex3f(x1, y0, z1);
        glTexCoord2f(0, 0.5f); glVertex3f(x1, y05, z0);
        glTexCoord2f(1, 0.5f); glVertex3f(x1, y05, z1);
        
        // Top step (0, 0.5, 0) to (1, 1, 0.5) - north half only
        // Top face of step
        glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);
        glTexCoord2f(0, 0.5f); glVertex3f(x0, y1, z05);
        glTexCoord2f(1, 0.5f); glVertex3f(x1, y1, z05);
        
        glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);
        glTexCoord2f(1, 0.5f); glVertex3f(x1, y1, z05);
        glTexCoord2f(1, 0); glVertex3f(x1, y1, z0);
        
        // North face of step (full)
        glTexCoord2f(1, 0.5f); glVertex3f(x1, y05, z0);
        glTexCoord2f(0, 0.5f); glVertex3f(x0, y05, z0);
        glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);
        
        glTexCoord2f(1, 0.5f); glVertex3f(x1, y05, z0);
        glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);
        glTexCoord2f(1, 0); glVertex3f(x1, y1, z0);
        
        // South face of step (inner step face)
        glTexCoord2f(0, 0.5f); glVertex3f(x0, y05, z05);
        glTexCoord2f(1, 0.5f); glVertex3f(x1, y05, z05);
        glTexCoord2f(1, 0); glVertex3f(x1, y1, z05);
        
        glTexCoord2f(0, 0.5f); glVertex3f(x0, y05, z05);
        glTexCoord2f(1, 0); glVertex3f(x1, y1, z05);
        glTexCoord2f(0, 0); glVertex3f(x0, y1, z05);
        
        // West face of step
        glTexCoord2f(0, 0.5f); glVertex3f(x0, y05, z0);
        glTexCoord2f(0.5f, 0.5f); glVertex3f(x0, y05, z05);
        glTexCoord2f(0.5f, 0); glVertex3f(x0, y1, z05);
        
        glTexCoord2f(0, 0.5f); glVertex3f(x0, y05, z0);
        glTexCoord2f(0.5f, 0); glVertex3f(x0, y1, z05);
        glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);
        
        // East face of step
        glTexCoord2f(0.5f, 0.5f); glVertex3f(x1, y05, z05);
        glTexCoord2f(0, 0.5f); glVertex3f(x1, y05, z0);
        glTexCoord2f(0, 0); glVertex3f(x1, y1, z0);
        
        glTexCoord2f(0.5f, 0.5f); glVertex3f(x1, y05, z05);
        glTexCoord2f(0, 0); glVertex3f(x1, y1, z0);
        glTexCoord2f(0.5f, 0); glVertex3f(x1, y1, z05);
    }
}

