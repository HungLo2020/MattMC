package mattmc.client.renderer;

import mattmc.client.gui.components.TextRenderer;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Renders tooltips for inventory items.
 * Displays a small semi-transparent gray box with rounded edges, blue outline,
 * and the item name.
 */
public class TooltipRenderer {
    private static final float TOOLTIP_PADDING = 13.5f;  // Increased by another 50% (9 * 1.5 = 13.5)
    private static final float TOOLTIP_CORNER_RADIUS = 9f;  // Increased by 50% (6 * 1.5 = 9)
    private static final float TOOLTIP_OFFSET_X = 18f;  // Kept same
    private static final float TOOLTIP_OFFSET_Y = -18f;  // Kept same
    private static final float TEXT_SCALE = 1.8f;  // Kept same as requested
    
    // Colors
    private static final float BG_GRAY = 0.3f;
    private static final float BG_ALPHA = 0.08f;  // Very subtle gray tint to show blur through
    private static final float BORDER_R = 0.3f;
    private static final float BORDER_G = 0.5f;
    private static final float BORDER_B = 1.0f;
    private static final float BORDER_ALPHA = 1.0f;
    private static final float BORDER_WIDTH = 6f;  // Increased 3x (2 * 3 = 6)
    
    private final TooltipBlurEffect blurEffect;
    
    public TooltipRenderer() {
        this.blurEffect = new TooltipBlurEffect();
    }
    
    /**
     * Render a tooltip at the specified mouse position.
     * Assumes the projection matrix is already set to screen coordinates.
     * @param text The text to display
     * @param mouseX Mouse X position in window coordinates
     * @param mouseY Mouse Y position in window coordinates
     * @param windowHandle GLFW window handle
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     */
    public void renderTooltip(String text, double mouseX, double mouseY, long windowHandle, int screenWidth, int screenHeight) {
        if (text == null || text.isEmpty()) {
            return;
        }
        
        // Convert window coordinates to framebuffer coordinates
        float mouseFBX, mouseFBY;
        try (MemoryStack stack = stackPush()) {
            IntBuffer winW = stack.mallocInt(1), winH = stack.mallocInt(1);
            IntBuffer fbW  = stack.mallocInt(1), fbH  = stack.mallocInt(1);
            glfwGetWindowSize(windowHandle, winW, winH);
            glfwGetFramebufferSize(windowHandle, fbW, fbH);
            float sx = fbW.get(0) / Math.max(1f, winW.get(0));
            float sy = fbH.get(0) / Math.max(1f, winH.get(0));
            mouseFBX = (float) mouseX * sx;
            mouseFBY = (float) mouseY * sy;
        }
        
        // Calculate text dimensions
        float textWidth = TextRenderer.getTextWidth(text, TEXT_SCALE);
        float textHeight = TextRenderer.getTextHeight(text, TEXT_SCALE);
        
        // Calculate tooltip box dimensions
        float boxWidth = textWidth + TOOLTIP_PADDING * 2;
        float boxHeight = textHeight + TOOLTIP_PADDING * 2;
        
        // Calculate tooltip position (above and to the right of cursor)
        float tooltipX = mouseFBX + TOOLTIP_OFFSET_X;
        float tooltipY = mouseFBY + TOOLTIP_OFFSET_Y - boxHeight;
        
        // Clamp to screen bounds
        tooltipX = Math.max(0, Math.min(tooltipX, screenWidth - boxWidth));
        tooltipY = Math.max(0, Math.min(tooltipY, screenHeight - boxHeight));
        
        // Apply blur to the tooltip region BEFORE drawing the tooltip
        blurEffect.applyRegionalBlur(tooltipX, tooltipY, boxWidth, boxHeight, screenWidth, screenHeight);
        
        // NOTE: Gray tint disabled - blur effect provides background separation
        // If needed in future, ensure very low alpha (< 0.05) to avoid obscuring blur
        
        // Draw blue border with rounded corners
        drawRoundedRectBorder(tooltipX, tooltipY, boxWidth, boxHeight, TOOLTIP_CORNER_RADIUS, 
                             BORDER_WIDTH, BORDER_R, BORDER_G, BORDER_B, BORDER_ALPHA);
        
        // Draw text
        glColor4f(1f, 1f, 1f, 1f);
        TextRenderer.drawText(text, tooltipX + TOOLTIP_PADDING, tooltipY + TOOLTIP_PADDING, TEXT_SCALE);
    }
    
    /**
     * Draw a rounded rectangle (filled).
     * Draws the main body plus four rounded corners.
     */
    private void drawRoundedRect(float x, float y, float width, float height, float radius, 
                                 float r, float g, float b, float a) {
        glColor4f(r, g, b, a);
        
        // Clamp radius to not exceed half the smaller dimension
        radius = Math.min(radius, Math.min(width, height) / 2f);
        
        int segments = 32; // More segments for very smooth corners
        
        // Draw the main rectangle body as one continuous shape
        glBegin(GL_TRIANGLE_FAN);
        
        // Center point
        glVertex2f(x + width / 2f, y + height / 2f);
        
        // Top-left corner arc (from left to top)
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI + ((float) Math.PI * 0.5f * i / segments);
            glVertex2f(x + radius + radius * (float) Math.cos(angle), 
                      y + radius + radius * (float) Math.sin(angle));
        }
        
        // Top-right corner arc (from top to right)
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI * 1.5f + ((float) Math.PI * 0.5f * i / segments);
            glVertex2f(x + width - radius + radius * (float) Math.cos(angle), 
                      y + radius + radius * (float) Math.sin(angle));
        }
        
        // Bottom-right corner arc (from right to bottom)
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI * 0.0f + ((float) Math.PI * 0.5f * i / segments);
            glVertex2f(x + width - radius + radius * (float) Math.cos(angle), 
                      y + height - radius + radius * (float) Math.sin(angle));
        }
        
        // Bottom-left corner arc (from bottom to left)
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI * 0.5f + ((float) Math.PI * 0.5f * i / segments);
            glVertex2f(x + radius + radius * (float) Math.cos(angle), 
                      y + height - radius + radius * (float) Math.sin(angle));
        }
        
        // Close the shape back to the first point of top-left corner
        float angle = (float) Math.PI;
        glVertex2f(x + radius + radius * (float) Math.cos(angle), 
                  y + radius + radius * (float) Math.sin(angle));
        
        glEnd();
    }
    
    /**
     * Draw a rounded rectangle border (outline).
     * Draws lines connecting the four rounded corners.
     */
    private void drawRoundedRectBorder(float x, float y, float width, float height, float radius, 
                                       float borderWidth, float r, float g, float b, float a) {
        glColor4f(r, g, b, a);
        glLineWidth(borderWidth);
        
        // Clamp radius to not exceed half the smaller dimension
        radius = Math.min(radius, Math.min(width, height) / 2f);
        
        int segments = 32; // More segments for very smooth corners
        
        glBegin(GL_LINE_STRIP);
        
        // Top-left corner (starting from left side going to top)
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI * 1.0f + ((float) Math.PI * 0.5f * i / segments);
            glVertex2f(x + radius + radius * (float) Math.cos(angle), 
                      y + radius + radius * (float) Math.sin(angle));
        }
        
        // Top-right corner (from top going to right)
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI * 1.5f + ((float) Math.PI * 0.5f * i / segments);
            glVertex2f(x + width - radius + radius * (float) Math.cos(angle), 
                      y + radius + radius * (float) Math.sin(angle));
        }
        
        // Bottom-right corner (from right going to bottom)
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI * 0.0f + ((float) Math.PI * 0.5f * i / segments);
            glVertex2f(x + width - radius + radius * (float) Math.cos(angle), 
                      y + height - radius + radius * (float) Math.sin(angle));
        }
        
        // Bottom-left corner (from bottom going to left)
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI * 0.5f + ((float) Math.PI * 0.5f * i / segments);
            glVertex2f(x + radius + radius * (float) Math.cos(angle), 
                      y + height - radius + radius * (float) Math.sin(angle));
        }
        
        // Close the loop back to start
        float angle = (float) Math.PI * 1.0f;
        glVertex2f(x + radius + radius * (float) Math.cos(angle), 
                  y + radius + radius * (float) Math.sin(angle));
        
        glEnd();
        
        glLineWidth(1f); // Reset line width
    }
    
    /**
     * Clean up resources.
     */
    public void close() {
        if (blurEffect != null) {
            blurEffect.close();
        }
    }
}
