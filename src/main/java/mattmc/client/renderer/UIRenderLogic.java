package mattmc.client.renderer;
import mattmc.client.renderer.backend.RenderPass;
import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.UIMeshIds;

/**
 * Front-end logic for UI rendering that builds draw commands without making GL calls.
 * 
 * <p>This class is responsible for determining what UI elements to render and creating
 * {@link DrawCommand} objects that describe the rendering work. It does NOT make any
 * OpenGL calls directly - that's delegated to the {@link RenderBackend}.
 * 
 * <p><b>Architecture:</b> This is the "front-end" of UI rendering:
 * <ul>
 *   <li><b>Front-end (this class):</b> Decides <em>what</em> to draw, builds commands</li>
 *   <li><b>Back-end (RenderBackend):</b> Decides <em>how</em> to draw, issues GL calls</li>
 * </ul>
 * 
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Determine which UI elements to render (hotbar, crosshair, debug info, etc.)</li>
 *   <li>Compute UI element positions and sizes</li>
 *   <li>Assign texture/material IDs for UI elements</li>
 *   <li>Create and accumulate DrawCommand objects</li>
 * </ul>
 * 
 * <p><b>Design Note:</b> This separation allows:
 * <ul>
 *   <li>Testing without OpenGL context</li>
 *   <li>Easier debugging (inspect commands before rendering)</li>
 *   <li>Future optimization (sort/batch commands before submission)</li>
 *   <li>Support for multiple backends (OpenGL now, Vulkan later)</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b> This class is NOT thread-safe and must only be called from
 * the rendering thread.
 * 
 * @since Stage 4 of rendering refactor
 * @see CommandBuffer
 * @see DrawCommand
 * @see RenderBackend
 */
public class UIRenderLogic {
    
    // Instance-based text registry for proper lifecycle management
    // Each UIRenderLogic instance has its own registry to support testing and isolation
    private int nextTextId = 0;
    private final java.util.Map<Integer, TextRenderInfo> textRegistry = new java.util.HashMap<>();
    
    // Static reference for backward compatibility with OpenGLRenderBackend lookups
    // This is a temporary solution - ideally the backend would receive the registry reference
    private static UIRenderLogic currentInstance = null;
    
    /**
     * Create a new UIRenderLogic instance.
     * Registers itself as the current instance for static lookups.
     */
    public UIRenderLogic() {
        currentInstance = this;
    }
    
    /**
     * Begin a new frame - clears the text registry.
     * Should be called at the start of each frame before building commands.
     */
    public void beginFrame() {
        textRegistry.clear();
        nextTextId = 0;
    }
    
    /**
     * Builds draw commands for UI elements.
     * 
     * <p>This method analyzes what UI elements need to be rendered and creates
     * appropriate DrawCommand objects for each element.
     * 
     * <p>Commands are added to the provided buffer. The buffer is NOT cleared first,
     * allowing multiple logic classes to contribute commands to the same buffer.
     * 
     * @param screenWidth the screen width in pixels
     * @param screenHeight the screen height in pixels
     * @param buffer the buffer to add commands to
     */
    public void buildCommands(int screenWidth, int screenHeight, CommandBuffer buffer) {
        // Stage 4 implementation: Build draw commands for UI elements
        // This will be populated as we refactor each UI component
        
        // Example pattern (to be implemented for each UI component):
        // - Determine what to draw (e.g., hotbar, crosshair)
        // - Compute positions and sizes
        // - Create DrawCommand with appropriate meshId, materialId, transformIndex
        // - Add to buffer
    }
    
    /**
     * Builds draw commands specifically for the hotbar UI element.
     * 
     * @param screenWidth screen width
     * @param screenHeight screen height
     * @param selectedSlot the currently selected hotbar slot (0-8)
     * @param buffer the command buffer to add commands to
     */
    public void buildHotbarCommands(int screenWidth, int screenHeight, int selectedSlot, CommandBuffer buffer) {
        // Constants from HotbarRenderer
        float HOTBAR_SCALE = 3.0f;
        
        // Calculate hotbar position (centered at bottom)
        // Hotbar texture is 182 pixels wide, 22 pixels tall
        float texWidth = 182 * HOTBAR_SCALE;
        float texHeight = 22 * HOTBAR_SCALE;
        float hotbarX = (screenWidth - texWidth) / 2f;
        float hotbarY = screenHeight - texHeight - 10;
        
        // Create command for hotbar background
        DrawCommand hotbarBg = new DrawCommand(
            UIMeshIds.HOTBAR, // hotbar background marker
            encodeHotbarData((int)hotbarX, (int)hotbarY, (int)texWidth, (int)texHeight, 0), // type 0 = background
            0,  // screen-space
            RenderPass.UI
        );
        buffer.add(hotbarBg);
        
        // Create command for selection overlay
        buildSelectionCommands(screenWidth, screenHeight, selectedSlot, buffer);
    }
    
    /**
     * Builds draw commands for the hotbar selection overlay.
     * 
     * @param screenWidth screen width
     * @param screenHeight screen height
     * @param selectedSlot the selected slot (0-8)
     * @param buffer the command buffer to add commands to
     */
    public void buildSelectionCommands(int screenWidth, int screenHeight, int selectedSlot, CommandBuffer buffer) {
        // Constants from HotbarRenderer
        float HOTBAR_SCALE = 3.0f;
        
        // Calculate hotbar position
        float hotbarTexWidth = 182 * HOTBAR_SCALE;
        float hotbarTexHeight = 22 * HOTBAR_SCALE;
        float hotbarX = (screenWidth - hotbarTexWidth) / 2f;
        float hotbarY = screenHeight - hotbarTexHeight - 10;
        
        // Calculate selection overlay position
        // Selection texture is 24 pixels wide, 24 pixels tall
        float slotWidth = hotbarTexWidth / 9f;
        float selectionWidth = 24 * HOTBAR_SCALE;
        float selectionHeight = 24 * HOTBAR_SCALE;
        
        float centerOffset = (selectionWidth - slotWidth) / 2f;
        float selectionX = hotbarX + (selectedSlot * slotWidth) - centerOffset;
        float selectionY = hotbarY - (1 * HOTBAR_SCALE);
        
        // Create command for selection overlay
        DrawCommand selection = new DrawCommand(
            UIMeshIds.HOTBAR, // hotbar marker
            encodeHotbarData((int)selectionX, (int)selectionY, (int)selectionWidth, (int)selectionHeight, 1), // type 1 = selection
            0,  // screen-space
            RenderPass.UI
        );
        buffer.add(selection);
    }
    
    /**
     * Encode hotbar UI data into a single integer.
     * 
     * @param x X position
     * @param y Y position  
     * @param width width
     * @param height height
     * @param type 0=background, 1=selection
     * @return encoded data
     */
    private int encodeHotbarData(int x, int y, int width, int height, int type) {
        // Encode type in lower bits, position in upper bits
        // Width and height are calculated by backend based on type
        return type | ((x & 0xFFF) << 2) | ((y & 0xFFF) << 14);
    }
    
    /**
     * Builds draw commands for the crosshair.
     * 
     * <p>The crosshair consists of two quads (horizontal and vertical lines) forming a cross
     * at the center of the screen.
     * 
     * @param screenWidth screen width
     * @param screenHeight screen height
     * @param buffer the command buffer to add commands to
     */
    public void buildCrosshairCommands(int screenWidth, int screenHeight, CommandBuffer buffer) {
        // Crosshair parameters (matching original CrosshairRenderer)
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;
        float size = 10f;
        float thickness = 2f;
        
        // Create commands for the two crosshair quads
        // For Stage 4, we use special mesh IDs to indicate UI quads
        // The backend will recognize these and render them as 2D quads
        
        // Horizontal line
        // materialId encodes position/size info
        // transformIndex = 0 for screen-space rendering
        DrawCommand horizontalLine = new DrawCommand(
            UIMeshIds.CROSSHAIR, // UI quad marker
            encodeCrosshairData((int)centerX, (int)centerY, (int)(size*2), (int)thickness, true),
            0,  // screen-space transform
            RenderPass.UI
        );
        
        // Vertical line
        // Note: we pass size*2 as the width parameter because that's what gets encoded as the line length
        DrawCommand verticalLine = new DrawCommand(
            UIMeshIds.CROSSHAIR, // UI quad marker
            encodeCrosshairData((int)centerX, (int)centerY, (int)(size*2), (int)thickness, false),
            0,  // screen-space transform
            RenderPass.UI
        );
        
        buffer.add(horizontalLine);
        buffer.add(verticalLine);
    }
    
    /**
     * Encode crosshair quad data into a single integer.
     * This is a temporary encoding scheme for Stage 4.
     * 
     * @param x center X
     * @param y center Y
     * @param width quad width
     * @param height quad height
     * @param horizontal true for horizontal line, false for vertical
     * @return encoded data
     */
    private int encodeCrosshairData(int x, int y, int width, int height, boolean horizontal) {
        // Simple encoding: use bits to pack data
        // This is a Stage 4 temporary solution
        // In a full implementation, we'd have proper data structures
        return (horizontal ? 1 : 0) | ((x & 0xFFF) << 1) | ((y & 0xFFF) << 13) | ((width & 0xFF) << 25);
    }
    
    /**
     * Information about text to render.
     */
    public static class TextRenderInfo {
        public final String text;
        public final float x;
        public final float y;
        public final float scale;
        public final int color;
        
        public TextRenderInfo(String text, float x, float y, float scale, int color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.color = color;
        }
    }
    
    /**
     * Register text for rendering and get an ID.
     * Uses instance registry for proper lifecycle management.
     */
    private int registerText(String text, float x, float y, float scale, int color) {
        int id = nextTextId++;
        textRegistry.put(id, new TextRenderInfo(text, x, y, scale, color));
        return id;
    }
    
    /**
     * Get text info by ID.
     * Uses the current instance's registry for lookup.
     * 
     * @param id the text ID to look up
     * @return the TextRenderInfo or null if not found
     */
    public static TextRenderInfo getTextInfo(int id) {
        if (currentInstance != null) {
            return currentInstance.textRegistry.get(id);
        }
        return null;
    }
    
    /**
     * Clear text registry (call at start of frame).
     * @deprecated Use instance method {@link #beginFrame()} instead
     */
    @Deprecated
    public static void clearTextRegistry() {
        if (currentInstance != null) {
            currentInstance.beginFrame();
        }
    }
    
    /**
     * Build commands for debug info rendering.
     * 
     * @param screenWidth screen width
     * @param screenHeight screen height
     * @param playerX player X position
     * @param playerY player Y position
     * @param playerZ player Z position
     * @param yaw camera yaw
     * @param pitch camera pitch
     * @param roll camera roll
     * @param fps frames per second
     * @param loadedChunks number of loaded chunks
     * @param pendingChunks number of pending chunks
     * @param activeWorkers number of active workers
     * @param renderedChunks number of rendered chunks
     * @param culledChunks number of culled chunks
     * @param buffer command buffer
     */
    public void buildDebugInfoCommands(int screenWidth, int screenHeight, 
                                      float playerX, float playerY, float playerZ,
                                      float yaw, float pitch, float roll, double fps,
                                      int loadedChunks, int pendingChunks, int activeWorkers,
                                      int renderedChunks, int culledChunks,
                                      CommandBuffer buffer) {
        float x = 10f;
        float y = 10f;
        float lineHeight = 20f;
        float scale = 1.5f;
        int color = 0xFFFFFF;
        
        // Build all debug text lines
        String[] lines = new String[9];
        lines[0] = "MattMC: " + mattmc.client.main.Main.VERSION + ": Debug Screen";
        lines[1] = String.format("FPS: %.0f", fps);
        
        float normalizedYaw = ((yaw % 360) + 360) % 360;
        String direction = getCardinalDirection(normalizedYaw);
        lines[2] = String.format("Position: %.2f, %.2f, %.2f (Facing: %s)", playerX, playerY, playerZ, direction);
        lines[3] = String.format("Yaw: %.2f, Pitch: %.2f, Roll: %.2f", normalizedYaw, pitch, roll);
        
        int chunkX = Math.floorDiv((int)playerX, 16); // LevelChunk.WIDTH
        int chunkZ = Math.floorDiv((int)playerZ, 16); // LevelChunk.DEPTH
        lines[4] = String.format("LevelChunk: %d, %d", chunkX, chunkZ);
        
        int regionX = Math.floorDiv(chunkX, 8); // Region.REGION_SIZE
        int regionZ = Math.floorDiv(chunkZ, 8);
        lines[5] = String.format("Region: %d, %d", regionX, regionZ);
        
        lines[6] = String.format("Loaded Chunks: %d", loadedChunks);
        lines[7] = String.format("Pending: %d | Workers: %d", pendingChunks, activeWorkers);
        lines[8] = String.format("Rendered: %d | Culled: %d", renderedChunks, culledChunks);
        
        // Create draw command for each line
        for (int i = 0; i < lines.length; i++) {
            int textId = registerText(lines[i], x, y + lineHeight * i, scale, color);
            DrawCommand cmd = new DrawCommand(
                UIMeshIds.DEBUG_TEXT, // debug info marker
                0,  // unused
                textId, // text ID in registry
                RenderPass.UI
            );
            buffer.add(cmd);
        }
    }
    
    /**
     * Get cardinal direction from yaw.
     */
    private String getCardinalDirection(float normalizedYaw) {
        if (normalizedYaw >= 337.5 || normalizedYaw < 22.5) return "South";
        else if (normalizedYaw >= 22.5 && normalizedYaw < 67.5) return "South-West";
        else if (normalizedYaw >= 67.5 && normalizedYaw < 112.5) return "West";
        else if (normalizedYaw >= 112.5 && normalizedYaw < 157.5) return "North-West";
        else if (normalizedYaw >= 157.5 && normalizedYaw < 202.5) return "North";
        else if (normalizedYaw >= 202.5 && normalizedYaw < 247.5) return "North-East";
        else if (normalizedYaw >= 247.5 && normalizedYaw < 292.5) return "East";
        else return "South-East";
    }
    
    /**
     * Build commands for command overlay rendering.
     * 
     * @param screenWidth screen width
     * @param screenHeight screen height
     * @param commandText the command text to display
     * @param buffer command buffer
     */
    public void buildCommandOverlayCommands(int screenWidth, int screenHeight, String commandText, CommandBuffer buffer) {
        // Register command text
        int boxHeight = 50;
        int boxY = screenHeight - boxHeight - 20;
        int boxX = 20;
        
        String displayText = commandText + "_";
        int textId = registerText(displayText, boxX + 10, boxY + 15, 1.5f, 0xFFFFFF);
        
        // Create command for overlay box and text
        // Encode box dimensions in materialId
        int encoded = encodeCommandUIData(boxX, boxY, screenWidth - 40, boxHeight, 0); // type 0 = overlay
        DrawCommand cmd = new DrawCommand(
            UIMeshIds.COMMAND_UI, // command UI marker
            encoded,
            textId, // text ID
            RenderPass.UI
        );
        buffer.add(cmd);
    }
    
    /**
     * Build commands for command feedback rendering.
     * 
     * @param screenWidth screen width
     * @param screenHeight screen height
     * @param message the feedback message
     * @param buffer command buffer
     */
    public void buildCommandFeedbackCommands(int screenWidth, int screenHeight, String message, CommandBuffer buffer) {
        if (message == null || message.isEmpty()) return;
        
        int messageY = screenHeight - 120; // FEEDBACK_Y_OFFSET
        float textScale = 1.2f;
        
        // Estimate text width
        int textWidth = (int)(message.length() * 8 * textScale); // CHAR_WIDTH_ESTIMATE
        int textX = (screenWidth - textWidth) / 2;
        
        // Register feedback text
        int textId = registerText(message, textX, messageY, textScale, 0xFFFFFF);
        
        // Create command for feedback
        int padding = 8;
        int bgX = (screenWidth - textWidth) / 2 - padding;
        int bgY = messageY - padding;
        int bgWidth = textWidth + padding * 2;
        int bgHeight = (int)(16 * textScale) + padding * 2;
        
        int encoded = encodeCommandUIData(bgX, bgY, bgWidth, bgHeight, 1); // type 1 = feedback
        DrawCommand cmd = new DrawCommand(
            UIMeshIds.COMMAND_UI, // command UI marker
            encoded,
            textId, // text ID
            RenderPass.UI
        );
        buffer.add(cmd);
    }
    
    /**
     * Encode command UI data.
     */
    private int encodeCommandUIData(int x, int y, int width, int height, int type) {
        // Encode type and position data
        return type | ((x & 0xFFF) << 2) | ((y & 0xFFF) << 14) | ((width & 0x3F) << 26);
    }
    
    /**
     * Build commands for system info rendering.
     * 
     * @param screenWidth screen width
     * @param screenHeight screen height
     * @param systemInfo array of system info strings (Java, memory, CPU, display, GPU, GPU usage)
     * @param buffer command buffer
     */
    public void buildSystemInfoCommands(int screenWidth, int screenHeight, String[] systemInfo, CommandBuffer buffer) {
        if (systemInfo == null || systemInfo.length == 0) return;
        
        float lineHeight = 20f;
        float scale = 1.5f;
        float y = 10f;
        
        // Register all system info text lines
        int firstTextId = -1;
        for (int i = 0; i < systemInfo.length; i++) {
            int x = screenWidth - 10; // right-aligned
            int textId = registerText(systemInfo[i], (int)x, (int)y, scale, 0xFFFFFF);
            if (i == 0) firstTextId = textId;
            y += lineHeight;
        }
        
        // Create single command with first text ID (backend will look up all in sequence)
        DrawCommand cmd = new DrawCommand(
            UIMeshIds.SYSTEM_INFO, // system info marker
            systemInfo.length, // number of lines
            firstTextId, // first text ID
            RenderPass.UI
        );
        buffer.add(cmd);
    }
    
    /**
     * Build commands for tooltip rendering.
     * 
     * @param text tooltip text
     * @param mouseX mouse X position (framebuffer coords)
     * @param mouseY mouse Y position (framebuffer coords)
     * @param screenWidth screen width
     * @param screenHeight screen height
     * @param buffer command buffer
     */
    public void buildTooltipCommands(String text, float mouseX, float mouseY, int screenWidth, int screenHeight, CommandBuffer buffer) {
        if (text == null || text.isEmpty()) return;
        
        // Constants from TooltipRenderer
        float TOOLTIP_PADDING = 13.5f;
        float TOOLTIP_OFFSET_X = 18f;
        float TOOLTIP_OFFSET_Y = -18f;
        float TEXT_SCALE = 1.8f;
        
        // Calculate text dimensions (approximate)
        float textWidth = text.length() * 8 * TEXT_SCALE; // approximate
        float textHeight = 16 * TEXT_SCALE;
        
        // Calculate tooltip box dimensions
        float boxWidth = textWidth + TOOLTIP_PADDING * 2;
        float boxHeight = textHeight + TOOLTIP_PADDING * 2;
        
        // Calculate tooltip position (above and to the right of cursor)
        float tooltipX = mouseX + TOOLTIP_OFFSET_X;
        float tooltipY = mouseY + TOOLTIP_OFFSET_Y - boxHeight;
        
        // Clamp to screen bounds
        tooltipX = Math.max(0, Math.min(tooltipX, screenWidth - boxWidth));
        tooltipY = Math.max(0, Math.min(tooltipY, screenHeight - boxHeight));
        
        // Register tooltip text
        int textId = registerText(text, (int)(tooltipX + TOOLTIP_PADDING), (int)(tooltipY + TOOLTIP_PADDING), TEXT_SCALE, 0xFFFFFF);
        
        // Encode tooltip data: position and size
        // Use materialId to encode position (x, y)
        int encodedPos = ((int)tooltipX & 0xFFFF) | (((int)tooltipY & 0xFFFF) << 16);
        
        // Use transformIndex to encode size and text ID
        int encodedSize = ((int)boxWidth & 0xFFFF) | (((int)boxHeight & 0xFFFF) << 16);
        
        // Create command for tooltip
        DrawCommand cmd = new DrawCommand(
            UIMeshIds.TOOLTIP, // tooltip marker
            encodedPos, // position
            textId, // text ID (also used for size lookup via transformIndex pattern)
            RenderPass.UI
        );
        buffer.add(cmd);
        
        // Store size info using second command with special encoding
        DrawCommand sizeCmd = new DrawCommand(
            UIMeshIds.TOOLTIP, // tooltip marker
            encodedSize, // size
            textId | 0x80000000, // mark as size info with high bit
            RenderPass.UI
        );
        buffer.add(sizeCmd);
    }
    
    /**
     * Build draw commands for lighting debug overlay.
     * Shows relight scheduler statistics in top-right corner.
     * 
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @param backlogSize Number of pending light updates
     * @param nodesProcessed Number of nodes processed last frame
     * @param timeSpent Time spent in milliseconds last frame
     * @param buffer Command buffer to add commands to
     */
    public void buildLightingDebugCommands(int screenWidth, int screenHeight, 
                                          int backlogSize, int nodesProcessed, 
                                          double timeSpent, CommandBuffer buffer) {
        // Position in top-right corner
        float x = screenWidth - 250;
        float y = 10f;
        float lineHeight = 20f;
        float scale = 1.5f;
        
        // Register background quad (semi-transparent black)
        // Note: Background rendering would need special handling in backend
        // For now, we'll just render the text
        
        // Build text lines
        int textId1 = registerText("Lighting Debug", (int)x, (int)y, scale, 0xFFFFFF);
        int textId2 = registerText(String.format("Backlog: %d", backlogSize), 
                                   (int)x, (int)(y + lineHeight), scale, 0xFFFFFF);
        int textId3 = registerText(String.format("Nodes/frame: %d", nodesProcessed), 
                                   (int)x, (int)(y + lineHeight * 2), scale, 0xFFFFFF);
        
        // Color code time spent (green if < 2ms, yellow if < 5ms, red if >= 5ms)
        int timeColor = timeSpent < 2.0 ? 0x00FF00 : (timeSpent < 5.0 ? 0xFFFF00 : 0xFF0000);
        int textId4 = registerText(String.format("Time: %.2fms", timeSpent), 
                                   (int)x, (int)(y + lineHeight * 3), scale, timeColor);
        
        // Create draw commands for each text line
        buffer.add(new DrawCommand(UIMeshIds.DEBUG_TEXT, 0, textId1, RenderPass.UI));
        buffer.add(new DrawCommand(UIMeshIds.DEBUG_TEXT, 0, textId2, RenderPass.UI));
        buffer.add(new DrawCommand(UIMeshIds.DEBUG_TEXT, 0, textId3, RenderPass.UI));
        buffer.add(new DrawCommand(UIMeshIds.DEBUG_TEXT, 0, textId4, RenderPass.UI));
    }
    
    /**
     * Build draw commands for block name display.
     * Shows the name of the targeted block in top-left corner.
     * 
     * @param blockName The display name of the block
     * @param x X position (left margin)
     * @param y Y position (top margin)
     * @param buffer Command buffer to add commands to
     */
    public void buildBlockNameDisplayCommands(String blockName, int x, int y, 
                                             CommandBuffer buffer) {
        float textScale = 1.5f;
        
        // Register and draw the block name text
        int textId = registerText(blockName, x + 10, y + 10, textScale, 0xFFFFFF);
        
        // Create draw command for the text
        buffer.add(new DrawCommand(UIMeshIds.DEBUG_TEXT, 0, textId, RenderPass.UI));
    }
}
