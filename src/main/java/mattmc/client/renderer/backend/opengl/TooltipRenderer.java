package mattmc.client.renderer.backend.opengl;

import mattmc.client.renderer.CommandBuffer;

import mattmc.client.renderer.UIRenderLogic;

import mattmc.client.renderer.backend.DrawCommand;

import mattmc.client.renderer.backend.RenderBackend;

import mattmc.client.renderer.backend.opengl.gui.components.TextRenderer;
import mattmc.util.MathUtils;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Renders tooltips for inventory items.
 * Displays a small semi-transparent gray box with rounded edges, blue outline,
 * and the item name.
 * 
 * <p>Stage 4 refactor: Now supports backend rendering via UIRenderLogic + RenderBackend.
 */
public class TooltipRenderer extends AbstractBlurBox {
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
    
    private RenderBackend backend;
    private final UIRenderLogic logic = new UIRenderLogic();
    
    public TooltipRenderer() {
        super();
    }
    
    /**
     * Set the render backend to use for rendering.
     * When set, tooltips will be rendered via the backend.
     * 
     * @param backend the render backend
     */
    public void setBackend(RenderBackend backend) {
        this.backend = backend;
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
        
        if (backend != null) {
            // Use backend rendering
            CommandBuffer buffer = new CommandBuffer();
            logic.buildTooltipCommands(text, mouseFBX, mouseFBY, screenWidth, screenHeight, buffer);
            
            for (DrawCommand cmd : buffer.getCommands()) {
                backend.submit(cmd);
            }
        } else {
            // Legacy rendering (fallback)
            renderTooltipLegacy(text, mouseFBX, mouseFBY, screenWidth, screenHeight);
        }
    }
    
    /**
     * Legacy tooltip rendering (fallback when no backend is set).
     */
    private void renderTooltipLegacy(String text, float mouseFBX, float mouseFBY, int screenWidth, int screenHeight) {
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
        tooltipX = MathUtils.clamp(tooltipX, 0, screenWidth - boxWidth);
        tooltipY = MathUtils.clamp(tooltipY, 0, screenHeight - boxHeight);
        
        // Apply blur to the tooltip region BEFORE drawing the tooltip
        applyRegionalBlur(tooltipX, tooltipY, boxWidth, boxHeight, screenWidth, screenHeight);
        
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
     * Render tooltip directly (used by backend).
     * Package-private for backend access.
     */
    static void renderTooltipDirect(String text, float x, float y, int boxWidth, int boxHeight, float padding) {
        // NOTE: This is a simplified version for backend rendering
        // Full blur/border rendering requires instance methods which aren't available here
        // For now, just render the text (blur/border can be added later if needed)
        glColor4f(1f, 1f, 1f, 1f);
        TextRenderer.drawText(text, x + padding, y + padding, TEXT_SCALE);
    }
}
