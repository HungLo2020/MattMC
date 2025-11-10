package mattmc.client.renderer;

import mattmc.client.gui.components.TextRenderer;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Renders tooltips for inventory items.
 * Displays a small semi-transparent gray box with rounded edges, blue outline,
 * and blurred background showing the item name.
 */
public class TooltipRenderer {
    private static final float TOOLTIP_PADDING = 6f;
    private static final float TOOLTIP_CORNER_RADIUS = 4f;
    private static final float TOOLTIP_OFFSET_X = 12f;
    private static final float TOOLTIP_OFFSET_Y = -12f;
    private static final float TEXT_SCALE = 1.2f;
    
    // Colors
    private static final float BG_GRAY = 0.3f;
    private static final float BG_ALPHA = 0.7f;
    private static final float BORDER_R = 0.3f;
    private static final float BORDER_G = 0.5f;
    private static final float BORDER_B = 1.0f;
    private static final float BORDER_ALPHA = 1.0f;
    private static final float BORDER_WIDTH = 2f;
    
    private TooltipBlurEffect blurEffect;
    
    public TooltipRenderer() {
        this.blurEffect = new TooltipBlurEffect();
    }
    
    /**
     * Render a tooltip at the specified mouse position.
     * @param text The text to display
     * @param mouseX Mouse X position in window coordinates
     * @param mouseY Mouse Y position in window coordinates
     * @param windowHandle GLFW window handle
     */
    public void renderTooltip(String text, double mouseX, double mouseY, long windowHandle) {
        if (text == null || text.isEmpty()) {
            return;
        }
        
        // Convert window coordinates to framebuffer coordinates
        float mouseFBX, mouseFBY;
        int fbWidth, fbHeight;
        try (MemoryStack stack = stackPush()) {
            IntBuffer winW = stack.mallocInt(1), winH = stack.mallocInt(1);
            IntBuffer fbW  = stack.mallocInt(1), fbH  = stack.mallocInt(1);
            glfwGetWindowSize(windowHandle, winW, winH);
            glfwGetFramebufferSize(windowHandle, fbW, fbH);
            float sx = fbW.get(0) / Math.max(1f, winW.get(0));
            float sy = fbH.get(0) / Math.max(1f, winH.get(0));
            mouseFBX = (float) mouseX * sx;
            mouseFBY = (float) mouseY * sy;
            fbWidth = fbW.get(0);
            fbHeight = fbH.get(0);
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
        tooltipX = Math.max(0, Math.min(tooltipX, fbWidth - boxWidth));
        tooltipY = Math.max(0, Math.min(tooltipY, fbHeight - boxHeight));
        
        // Capture the area behind the tooltip for blur
        int captureX = (int) tooltipX;
        int captureY = (int) (fbHeight - tooltipY - boxHeight); // OpenGL Y is bottom-up
        int captureWidth = (int) Math.ceil(boxWidth);
        int captureHeight = (int) Math.ceil(boxHeight);
        
        // Ensure capture dimensions are valid
        captureWidth = Math.max(1, Math.min(captureWidth, fbWidth - captureX));
        captureHeight = Math.max(1, Math.min(captureHeight, fbHeight - captureY));
        
        // Capture screen region to texture
        int captureTexture = glGenTextures();
        try {
            glBindTexture(GL_TEXTURE_2D, captureTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, captureWidth, captureHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, captureX, captureY, captureWidth, captureHeight);
            
            // Apply blur to captured region
            Framebuffer blurredFB = blurEffect.applyBlur(captureTexture, captureWidth, captureHeight);
            
            // Set up 2D projection for rendering
            glMatrixMode(GL_PROJECTION);
            glPushMatrix();
            glLoadIdentity();
            glOrtho(0, fbWidth, fbHeight, 0, -1, 1);
            glMatrixMode(GL_MODELVIEW);
            glPushMatrix();
            glLoadIdentity();
            
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            
            // Render blurred background
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, blurredFB.getTextureId());
            glColor4f(1f, 1f, 1f, 1f);
            
            glBegin(GL_QUADS);
            glTexCoord2f(0, 1); glVertex2f(tooltipX, tooltipY);
            glTexCoord2f(1, 1); glVertex2f(tooltipX + boxWidth, tooltipY);
            glTexCoord2f(1, 0); glVertex2f(tooltipX + boxWidth, tooltipY + boxHeight);
            glTexCoord2f(0, 0); glVertex2f(tooltipX, tooltipY + boxHeight);
            glEnd();
            
            glDisable(GL_TEXTURE_2D);
            
            // Draw semi-transparent gray background with rounded corners
            drawRoundedRect(tooltipX, tooltipY, boxWidth, boxHeight, TOOLTIP_CORNER_RADIUS, 
                           BG_GRAY, BG_GRAY, BG_GRAY, BG_ALPHA);
            
            // Draw blue border with rounded corners
            drawRoundedRectBorder(tooltipX, tooltipY, boxWidth, boxHeight, TOOLTIP_CORNER_RADIUS, 
                                 BORDER_WIDTH, BORDER_R, BORDER_G, BORDER_B, BORDER_ALPHA);
            
            // Draw text
            glColor4f(1f, 1f, 1f, 1f);
            TextRenderer.drawText(text, tooltipX + TOOLTIP_PADDING, tooltipY + TOOLTIP_PADDING, TEXT_SCALE);
            
            glDisable(GL_BLEND);
            
            // Restore matrices
            glMatrixMode(GL_PROJECTION);
            glPopMatrix();
            glMatrixMode(GL_MODELVIEW);
            glPopMatrix();
            
        } finally {
            // Clean up capture texture
            glDeleteTextures(captureTexture);
        }
    }
    
    /**
     * Draw a rounded rectangle (filled).
     */
    private void drawRoundedRect(float x, float y, float width, float height, float radius, 
                                 float r, float g, float b, float a) {
        glColor4f(r, g, b, a);
        
        // Clamp radius to not exceed half the smaller dimension
        radius = Math.min(radius, Math.min(width, height) / 2f);
        
        int segments = 8; // Number of segments per corner
        
        glBegin(GL_TRIANGLE_FAN);
        
        // Center point
        glVertex2f(x + width / 2f, y + height / 2f);
        
        // Top-left corner
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI + i * (float) Math.PI / 2f / segments;
            float cx = x + radius;
            float cy = y + radius;
            glVertex2f(cx + radius * (float) Math.cos(angle), cy + radius * (float) Math.sin(angle));
        }
        
        // Bottom-left corner
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI * 1.5f + i * (float) Math.PI / 2f / segments;
            float cx = x + radius;
            float cy = y + height - radius;
            glVertex2f(cx + radius * (float) Math.cos(angle), cy + radius * (float) Math.sin(angle));
        }
        
        // Bottom-right corner
        for (int i = 0; i <= segments; i++) {
            float angle = i * (float) Math.PI / 2f / segments;
            float cx = x + width - radius;
            float cy = y + height - radius;
            glVertex2f(cx + radius * (float) Math.cos(angle), cy + radius * (float) Math.sin(angle));
        }
        
        // Top-right corner
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI / 2f + i * (float) Math.PI / 2f / segments;
            float cx = x + width - radius;
            float cy = y + radius;
            glVertex2f(cx + radius * (float) Math.cos(angle), cy + radius * (float) Math.sin(angle));
        }
        
        // Close the loop
        float angle = (float) Math.PI;
        glVertex2f(x + radius + radius * (float) Math.cos(angle), y + radius + radius * (float) Math.sin(angle));
        
        glEnd();
    }
    
    /**
     * Draw a rounded rectangle border (outline).
     */
    private void drawRoundedRectBorder(float x, float y, float width, float height, float radius, 
                                       float borderWidth, float r, float g, float b, float a) {
        glColor4f(r, g, b, a);
        glLineWidth(borderWidth);
        
        // Clamp radius to not exceed half the smaller dimension
        radius = Math.min(radius, Math.min(width, height) / 2f);
        
        int segments = 8; // Number of segments per corner
        
        glBegin(GL_LINE_STRIP);
        
        // Top-left corner
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI + i * (float) Math.PI / 2f / segments;
            float cx = x + radius;
            float cy = y + radius;
            glVertex2f(cx + radius * (float) Math.cos(angle), cy + radius * (float) Math.sin(angle));
        }
        
        // Bottom-left corner
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI * 1.5f + i * (float) Math.PI / 2f / segments;
            float cx = x + radius;
            float cy = y + height - radius;
            glVertex2f(cx + radius * (float) Math.cos(angle), cy + radius * (float) Math.sin(angle));
        }
        
        // Bottom-right corner
        for (int i = 0; i <= segments; i++) {
            float angle = i * (float) Math.PI / 2f / segments;
            float cx = x + width - radius;
            float cy = y + height - radius;
            glVertex2f(cx + radius * (float) Math.cos(angle), cy + radius * (float) Math.sin(angle));
        }
        
        // Top-right corner
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI / 2f + i * (float) Math.PI / 2f / segments;
            float cx = x + width - radius;
            float cy = y + radius;
            glVertex2f(cx + radius * (float) Math.cos(angle), cy + radius * (float) Math.sin(angle));
        }
        
        // Close the loop
        float angle = (float) Math.PI;
        glVertex2f(x + radius + radius * (float) Math.cos(angle), y + radius + radius * (float) Math.sin(angle));
        
        glEnd();
        
        glLineWidth(1f); // Reset line width
    }
    
    /**
     * Clean up resources.
     */
    public void close() {
        if (blurEffect != null) {
            blurEffect.close();
            blurEffect = null;
        }
    }
}
