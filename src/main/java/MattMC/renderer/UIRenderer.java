package MattMC.renderer;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBEasyFont;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles rendering of UI elements.
 * Similar to Minecraft's GuiIngame class.
 */
public class UIRenderer {
    private final ByteBuffer fontBuffer = BufferUtils.createByteBuffer(16 * 4096);
    
    /**
     * Draw crosshair in the center of the screen.
     */
    public void drawCrosshair(int screenWidth, int screenHeight) {
        // Switch to 2D orthographic projection
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Draw white crosshair
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;
        float size = 10f;
        float thickness = 2f;
        
        glColor4f(1f, 1f, 1f, 1f);
        glBegin(GL_QUADS);
        // Horizontal line
        glVertex2f(centerX - size, centerY - thickness/2);
        glVertex2f(centerX + size, centerY - thickness/2);
        glVertex2f(centerX + size, centerY + thickness/2);
        glVertex2f(centerX - size, centerY + thickness/2);
        // Vertical line
        glVertex2f(centerX - thickness/2, centerY - size);
        glVertex2f(centerX + thickness/2, centerY - size);
        glVertex2f(centerX + thickness/2, centerY + size);
        glVertex2f(centerX - thickness/2, centerY + size);
        glEnd();
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
    
    /**
     * Draw debug information in the top-left corner.
     * Shows player position, chunk position, and region position.
     */
    public void drawDebugInfo(int screenWidth, int screenHeight, float playerX, float playerY, float playerZ) {
        // Calculate chunk position from player position
        int chunkX = Math.floorDiv((int)playerX, 16); // Chunk.WIDTH = 16
        int chunkZ = Math.floorDiv((int)playerZ, 16); // Chunk.DEPTH = 16
        
        // Calculate region position from chunk position
        int regionX = Math.floorDiv(chunkX, 32); // Region.REGION_SIZE = 32
        int regionZ = Math.floorDiv(chunkZ, 32);
        
        // Switch to 2D orthographic projection
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Draw debug text in top-left corner
        float x = 10f;
        float y = 10f;
        float lineHeight = 15f;
        float scale = 1.0f;
        
        // Format position values to 2 decimal places for readability
        String posText = String.format("Position: %.2f, %.2f, %.2f", playerX, playerY, playerZ);
        String chunkText = String.format("Chunk: %d, %d", chunkX, chunkZ);
        String regionText = String.format("Region: %d, %d", regionX, regionZ);
        
        drawText(posText, x, y, scale, 0xFFFFFF);
        drawText(chunkText, x, y + lineHeight, scale, 0xFFFFFF);
        drawText(regionText, x, y + lineHeight * 2, scale, 0xFFFFFF);
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
    
    /**
     * Helper method to draw text at the specified position.
     */
    private void drawText(String text, float x, float y, float scale, int rgb) {
        setColor(rgb, 1f);
        fontBuffer.clear();
        int quads = STBEasyFont.stb_easy_font_print(0, 0, text, null, fontBuffer);
        
        glPushMatrix();
        glTranslatef(x, y, 0f);
        glScalef(scale, scale, 1f);
        
        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(2, GL_FLOAT, 16, fontBuffer);
        glDrawArrays(GL_QUADS, 0, quads * 4);
        glDisableClientState(GL_VERTEX_ARRAY);
        
        glPopMatrix();
    }
    
    /**
     * Helper method to set color from RGB integer.
     */
    private void setColor(int rgb, float a) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        glColor4f(r, g, b, a);
    }
}
