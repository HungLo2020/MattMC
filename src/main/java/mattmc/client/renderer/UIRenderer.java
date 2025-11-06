package mattmc.client.renderer;

import mattmc.client.Minecraft;
import mattmc.client.gui.screens.Screen;

import mattmc.client.gui.components.TextRenderer;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.chunk.Region;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles rendering of UI elements.
 * Similar to Minecraft's GuiIngame class.
 */
public class UIRenderer {
    
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
     * Shows version, FPS, player position, chunk position, region position, and culling stats.
     */
    public void drawDebugInfo(int screenWidth, int screenHeight, float playerX, float playerY, float playerZ, double fps, 
                               int loadedChunks, int pendingChunks, int activeWorkers, int renderedChunks, int culledChunks) {
        // Calculate chunk position from player position
        int chunkX = Math.floorDiv((int)playerX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv((int)playerZ, LevelChunk.DEPTH);
        
        // Calculate region position from chunk position
        int regionX = Math.floorDiv(chunkX, Region.REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, Region.REGION_SIZE);
        
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
        float lineHeight = 20f;
        float scale = 1.5f;
        
        // Header line with version
        String headerText = "MattMC: " + mattmc.client.main.Main.VERSION + ": Debug Screen";
        drawText(headerText, x, y, scale, 0xFFFFFF);
        
        // FPS display
        String fpsText = String.format("FPS: %.0f", fps);
        drawText(fpsText, x, y + lineHeight, scale, 0xFFFFFF);
        
        // Format position values to 2 decimal places for readability
        String posText = String.format("Position: %.2f, %.2f, %.2f", playerX, playerY, playerZ);
        String chunkText = String.format("LevelChunk: %d, %d", chunkX, chunkZ);
        String regionText = String.format("Region: %d, %d", regionX, regionZ);
        
        drawText(posText, x, y + lineHeight * 2, scale, 0xFFFFFF);
        drawText(chunkText, x, y + lineHeight * 3, scale, 0xFFFFFF);
        drawText(regionText, x, y + lineHeight * 4, scale, 0xFFFFFF);
        
        // Chunk loading stats
        String loadedText = String.format("Loaded Chunks: %d", loadedChunks);
        String pendingText = String.format("Pending: %d | Workers: %d", pendingChunks, activeWorkers);
        
        drawText(loadedText, x, y + lineHeight * 5, scale, 0xFFFFFF);
        drawText(pendingText, x, y + lineHeight * 6, scale, 0xFFFFFF);
        
        // Frustum culling stats
        String renderText = String.format("Rendered: %d | Culled: %d", renderedChunks, culledChunks);
        drawText(renderText, x, y + lineHeight * 7, scale, 0xFFFFFF);
        
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
        TextRenderer.drawText(text, x, y, scale);
    }
    
    /**
     * Draw command overlay at bottom of screen (like Minecraft).
     * Shows command input box with cursor.
     */
    public void drawCommandOverlay(int screenWidth, int screenHeight, String commandText, String errorMessage, boolean showError) {
        // Switch to 2D orthographic projection
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Enable blending for semi-transparent overlay
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Draw command input box at bottom of screen
        int boxHeight = 50;
        int boxY = screenHeight - boxHeight - 20;
        int boxX = 20;
        int boxWidth = screenWidth - 40;
        
        // Draw semi-transparent background
        setColor(0x000000, 0.7f);
        fillRect(boxX, boxY, boxWidth, boxHeight);
        
        // Draw border
        setColor(0xFFFFFF, 1.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(boxX, boxY);
        glVertex2f(boxX + boxWidth, boxY);
        glVertex2f(boxX + boxWidth, boxY + boxHeight);
        glVertex2f(boxX, boxY + boxHeight);
        glEnd();
        
        // Draw command text with blinking cursor
        String displayText = commandText + "_";
        drawText(displayText, boxX + 10, boxY + 15, 1.5f, 0xFFFFFF);
        
        // Draw error message if present
        if (showError && !errorMessage.isEmpty()) {
            drawText(errorMessage, boxX + 10, boxY - 25, 1.0f, 0xFF0000);
        }
        
        glDisable(GL_BLEND);
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
    
    /**
     * Helper method to fill a rectangle.
     */
    private void fillRect(int x, int y, int w, int h) {
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();
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
