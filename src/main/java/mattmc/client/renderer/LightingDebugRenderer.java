package mattmc.client.renderer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders lighting debug overlay showing relight scheduler statistics.
 */
public class LightingDebugRenderer {
    
    /**
     * Draw lighting debug overlay showing relight scheduler statistics.
     * Displays backlog size, nodes processed, and time spent.
     * 
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @param backlogSize Number of pending light updates
     * @param nodesProcessed Number of nodes processed last frame
     * @param timeSpent Time spent in milliseconds last frame
     */
    public void render(int screenWidth, int screenHeight, int backlogSize, 
                       int nodesProcessed, double timeSpent) {
        UIRenderHelper.setup2DProjection(screenWidth, screenHeight);
        
        // Position in top-right corner
        float x = screenWidth - 250;
        float y = 10f;
        float lineHeight = 20f;
        float scale = 1.5f;
        
        // Draw semi-transparent background
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(0f, 0f, 0f, 0.5f);
        glBegin(GL_QUADS);
        glVertex2f(x - 5, y - 5);
        glVertex2f(x + 240, y - 5);
        glVertex2f(x + 240, y + 75);
        glVertex2f(x - 5, y + 75);
        glEnd();
        
        // Enable texture for text rendering
        glEnable(GL_TEXTURE_2D);
        
        // Draw text
        UIRenderHelper.drawText("Lighting Debug", x, y, scale, 0xFFFFFF);
        y += lineHeight;
        
        UIRenderHelper.drawText(String.format("Backlog: %d", backlogSize), x, y, scale, 0xFFFFFF);
        y += lineHeight;
        
        UIRenderHelper.drawText(String.format("Nodes/frame: %d", nodesProcessed), x, y, scale, 0xFFFFFF);
        y += lineHeight;
        
        // Color code time spent (green if < 2ms, yellow if < 5ms, red if >= 5ms)
        int timeColor = timeSpent < 2.0 ? 0x00FF00 : (timeSpent < 5.0 ? 0xFFFF00 : 0xFF0000);
        UIRenderHelper.drawText(String.format("Time: %.2fms", timeSpent), x, y, scale, timeColor);
        
        UIRenderHelper.restore2DProjection();
    }
}
