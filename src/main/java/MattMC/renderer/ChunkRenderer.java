package MattMC.renderer;

import MattMC.world.Block;
import MattMC.world.BlockType;
import MattMC.world.Chunk;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles rendering of chunks and blocks.
 * Similar to Minecraft's ChunkRenderer/BlockRenderer classes.
 */
public class ChunkRenderer {
    
    /**
     * Render all blocks in a chunk with face culling.
     * 
     * Note: This iterates all 98,304 possible block positions but skips air blocks
     * immediately with 'continue'. For larger worlds, consider using a sparse data
     * structure or chunk sections like Minecraft does (16x16x16 sections).
     */
    public void renderChunk(Chunk chunk) {
        for (int x = 0; x < Chunk.WIDTH; x++) {
            for (int y = 0; y < Chunk.HEIGHT; y++) {
                for (int z = 0; z < Chunk.DEPTH; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.isAir()) continue;  // Skip air blocks (most of the chunk)
                    
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
    
    /**
     * Render a single block at the given world position.
     * Only renders faces that are exposed to air (face culling).
     */
    private void renderBlockAt(float x, float y, float z, Block block, Chunk chunk, int cx, int cy, int cz) {
        BlockType type = block.type();
        int color = type.color();
        
        // Check each face and only render if adjacent block is air
        // Top face (+Y)
        if (shouldRenderFace(chunk, cx, cy + 1, cz)) {
            setColor(color, 1f);
            drawTopFace(x, y, z);
        }
        
        // Bottom face (-Y)
        if (shouldRenderFace(chunk, cx, cy - 1, cz)) {
            setColor(darkenColor(color), 1f);
            drawBottomFace(x, y, z);
        }
        
        // North face (-Z)
        if (shouldRenderFace(chunk, cx, cy, cz - 1)) {
            setColor(adjustColorBrightness(color, 0.8f), 1f);
            drawNorthFace(x, y, z);
        }
        
        // South face (+Z)
        if (shouldRenderFace(chunk, cx, cy, cz + 1)) {
            setColor(adjustColorBrightness(color, 0.8f), 1f);
            drawSouthFace(x, y, z);
        }
        
        // West face (-X)
        if (shouldRenderFace(chunk, cx - 1, cy, cz)) {
            setColor(adjustColorBrightness(color, 0.6f), 1f);
            drawWestFace(x, y, z);
        }
        
        // East face (+X)
        if (shouldRenderFace(chunk, cx + 1, cy, cz)) {
            setColor(adjustColorBrightness(color, 0.6f), 1f);
            drawEastFace(x, y, z);
        }
        
        // Draw black outline around the cube
        drawBlockOutline(x, y, z);
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
     * Draws the 12 edges of the cube.
     */
    private void drawBlockOutline(float x, float y, float z) {
        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;
        
        // Set black color for outline
        setColor(0x000000, 1f);
        
        glBegin(GL_LINES);
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
