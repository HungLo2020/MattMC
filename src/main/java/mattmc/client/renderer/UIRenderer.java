package mattmc.client.renderer;

import mattmc.client.Minecraft;
import mattmc.client.gui.screens.Screen;

import mattmc.client.gui.components.TextRenderer;
import mattmc.client.renderer.texture.Texture;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.chunk.Region;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles rendering of UI elements.
 * Similar to Minecraft's GuiIngame class.
 */
public class UIRenderer {
    
    // Constants for command feedback positioning and sizing
    private static final int FEEDBACK_Y_OFFSET = 120; // Distance from bottom of screen
    private static final int CHAR_WIDTH_ESTIMATE = 8; // Approximate character width in pixels
    
    // Hotbar texture
    private Texture hotbarTexture;
    
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
    public void drawDebugInfo(int screenWidth, int screenHeight, float playerX, float playerY, float playerZ, 
                               float yaw, float pitch, float roll, double fps, 
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
        
        // Normalize yaw to 0-360 range for display
        float normalizedYaw = ((yaw % 360) + 360) % 360;
        
        // Calculate cardinal direction from normalized yaw
        String direction = getCardinalDirection(normalizedYaw);
        
        // Format position values to 2 decimal places for readability
        String posText = String.format("Position: %.2f, %.2f, %.2f (Facing: %s)", 
                                        playerX, playerY, playerZ, direction);
        String rotationText = String.format("Yaw: %.2f, Pitch: %.2f, Roll: %.2f", normalizedYaw, pitch, roll);
        String chunkText = String.format("LevelChunk: %d, %d", chunkX, chunkZ);
        String regionText = String.format("Region: %d, %d", regionX, regionZ);
        
        drawText(posText, x, y + lineHeight * 2, scale, 0xFFFFFF);
        drawText(rotationText, x, y + lineHeight * 3, scale, 0xFFFFFF);
        drawText(chunkText, x, y + lineHeight * 4, scale, 0xFFFFFF);
        drawText(regionText, x, y + lineHeight * 5, scale, 0xFFFFFF);
        
        // Chunk loading stats
        String loadedText = String.format("Loaded Chunks: %d", loadedChunks);
        String pendingText = String.format("Pending: %d | Workers: %d", pendingChunks, activeWorkers);
        
        drawText(loadedText, x, y + lineHeight * 6, scale, 0xFFFFFF);
        drawText(pendingText, x, y + lineHeight * 7, scale, 0xFFFFFF);
        
        // Frustum culling stats
        String renderText = String.format("Rendered: %d | Culled: %d", renderedChunks, culledChunks);
        drawText(renderText, x, y + lineHeight * 8, scale, 0xFFFFFF);
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
    
    /**
     * Draw hotbar at the bottom center of the screen.
     */
    public void drawHotbar(int screenWidth, int screenHeight) {
        // Load hotbar texture if not already loaded
        if (hotbarTexture == null) {
            hotbarTexture = Texture.load("/assets/textures/gui/sprites/hud/hotbar.png");
        }
        
        // Switch to 2D orthographic projection
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Enable blending for texture rendering
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Draw hotbar texture centered at bottom of screen
        if (hotbarTexture != null) {
            glEnable(GL_TEXTURE_2D);
            hotbarTexture.bind();
            glColor4f(1f, 1f, 1f, 1f);
            
            // Scale hotbar to 3x size for better visibility (like Minecraft GUI scale)
            float scale = 3.0f;
            float texWidth = hotbarTexture.width * scale;
            float texHeight = hotbarTexture.height * scale;
            float x = (screenWidth - texWidth) / 2f;
            float y = screenHeight - texHeight - 10; // 10 pixels from bottom
            
            glBegin(GL_QUADS);
            glTexCoord2f(0, 1); glVertex2f(x, y);
            glTexCoord2f(1, 1); glVertex2f(x + texWidth, y);
            glTexCoord2f(1, 0); glVertex2f(x + texWidth, y + texHeight);
            glTexCoord2f(0, 0); glVertex2f(x, y + texHeight);
            glEnd();
            
            glDisable(GL_TEXTURE_2D);
        }
        
        glDisable(GL_BLEND);
        
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
    public void drawCommandOverlay(int screenWidth, int screenHeight, String commandText) {
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
        
        glDisable(GL_BLEND);
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
    
    /**
     * Draw command feedback message above the hotbar area.
     * This message appears independently of the command input overlay and fades after a few seconds.
     * Similar to Minecraft's action bar messages.
     * 
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @param message The feedback message to display
     */
    public void drawCommandFeedback(int screenWidth, int screenHeight, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        
        // Switch to 2D orthographic projection
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Enable blending for semi-transparent text background
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Position message above the hotbar/command area (centered horizontally)
        int messageY = screenHeight - FEEDBACK_Y_OFFSET;
        
        // Draw semi-transparent background behind text
        int padding = 8;
        float textScale = 1.2f;
        // Estimate text width (rough approximation using character width estimate)
        int textWidth = (int)(message.length() * CHAR_WIDTH_ESTIMATE * textScale);
        int bgX = (screenWidth - textWidth) / 2 - padding;
        int bgY = messageY - padding;
        int bgWidth = textWidth + padding * 2;
        int bgHeight = (int)(16 * textScale) + padding * 2;
        
        // Draw background
        setColor(0x000000, 0.6f);
        fillRect(bgX, bgY, bgWidth, bgHeight);
        
        // Draw text centered
        int textX = (screenWidth - textWidth) / 2;
        drawText(message, textX, messageY, textScale, 0xFFFFFF);
        
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
    
    /**
     * Get cardinal direction from yaw angle.
     * Minecraft's yaw: 0 = South, 90 = West, 180 = North, 270 = East
     * @param normalizedYaw The yaw angle in degrees (should be normalized to 0-360 range)
     * @return Cardinal direction (N, S, E, W, NE, NW, SE, SW)
     */
    private String getCardinalDirection(float normalizedYaw) {
        // Determine direction based on yaw ranges
        // In Minecraft: South = 0, West = 90, North = 180, East = 270
        if (normalizedYaw >= 337.5 || normalizedYaw < 22.5) {
            return "South";
        } else if (normalizedYaw >= 22.5 && normalizedYaw < 67.5) {
            return "South-West";
        } else if (normalizedYaw >= 67.5 && normalizedYaw < 112.5) {
            return "West";
        } else if (normalizedYaw >= 112.5 && normalizedYaw < 157.5) {
            return "North-West";
        } else if (normalizedYaw >= 157.5 && normalizedYaw < 202.5) {
            return "North";
        } else if (normalizedYaw >= 202.5 && normalizedYaw < 247.5) {
            return "North-East";
        } else if (normalizedYaw >= 247.5 && normalizedYaw < 292.5) {
            return "East";
        } else {
            return "South-East";
        }
    }
}
