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
    private static final float BG_ALPHA = 0.75f;
    private static final float BORDER_R = 0.3f;
    private static final float BORDER_G = 0.5f;
    private static final float BORDER_B = 1.0f;
    private static final float BORDER_ALPHA = 1.0f;
    private static final float BORDER_WIDTH = 6f;  // Increased 3x (2 * 3 = 6)
    
    public TooltipRenderer() {
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
        
        // Draw semi-transparent gray background with rounded corners
        drawRoundedRect(tooltipX, tooltipY, boxWidth, boxHeight, TOOLTIP_CORNER_RADIUS, 
                       BG_GRAY, BG_GRAY, BG_GRAY, BG_ALPHA);
        
        // Draw blue border with rounded corners
        drawRoundedRectBorder(tooltipX, tooltipY, boxWidth, boxHeight, TOOLTIP_CORNER_RADIUS, 
                             BORDER_WIDTH, BORDER_R, BORDER_G, BORDER_B, BORDER_ALPHA);
        
        // Draw text
        glColor4f(1f, 1f, 1f, 1f);
        TextRenderer.drawText(text, tooltipX + TOOLTIP_PADDING, tooltipY + TOOLTIP_PADDING, TEXT_SCALE);
    }
    
    /**
     * Draw a rounded rectangle (filled).
     * Uses a simple approach with just straight lines and small corner rounding.
     */
    private void drawRoundedRect(float x, float y, float width, float height, float radius, 
                                 float r, float g, float b, float a) {
        glColor4f(r, g, b, a);
        
        // Clamp radius to not exceed half the smaller dimension
        radius = Math.min(radius, Math.min(width, height) / 2f);
        
        // Draw main rectangle (just a simple filled quad)
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
    }
    
    /**
     * Draw a rounded rectangle border (outline).
     * Uses a simple approach with just straight lines.
     */
    private void drawRoundedRectBorder(float x, float y, float width, float height, float radius, 
                                       float borderWidth, float r, float g, float b, float a) {
        glColor4f(r, g, b, a);
        glLineWidth(borderWidth);
        
        // Draw border as a simple rectangle outline
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
        
        glLineWidth(1f); // Reset line width
    }
    
    /**
     * Clean up resources.
     */
    public void close() {
        // No resources to clean up
    }
}
