package mattmc.client.renderer;

import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.RenderBackend;
import mattmc.world.level.block.Block;

/**
 * Backend-agnostic block name display renderer.
 * 
 * <p>This class is responsible for determining <em>what</em> to draw (block name in top-left)
 * and coordinating with the backend to actually render it. It contains NO OpenGL-specific
 * code and works purely through the {@link RenderBackend} abstraction.
 * 
 * <p><b>Architecture:</b> This is a "coordinator" class that:
 * <ul>
 *   <li>Uses {@link UIRenderLogic} to build draw commands (what to draw)</li>
 *   <li>Submits commands to the {@link RenderBackend} (how to draw)</li>
 *   <li>Delegates projection setup to the backend (backend-agnostic)</li>
 * </ul>
 * 
 * <p><b>Note:</b> Blur effects have been removed in this backend-agnostic version.
 * The blur functionality was tightly coupled to OpenGL framebuffers and would require
 * significant refactoring to support multiple backends. For now, this displays block
 * names without the blurred background.
 */
public class BlockNameDisplay {
    private static final int PADDING = 10;
    private static final int MARGIN = 10;
    
    
    private final UIRenderLogic logic;
    private final CommandBuffer buffer;
    private RenderBackend backend;
    
    /**
     * Create a new block name display renderer.
     */
    public BlockNameDisplay() {
        this.logic = new UIRenderLogic();
        this.buffer = new CommandBuffer();
    }
    
    /**
     * Set the render backend to use for rendering.
     * 
     * @param backend the backend to use (must not be null)
     * @throws IllegalArgumentException if backend is null
     */
    public void setBackend(RenderBackend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("Backend cannot be null");
        }
        this.backend = backend;
    }
    
    /**
     * Render the block name display in the top-left corner.
     * 
     * <p>This method is completely backend-agnostic. It delegates projection setup,
     * builds draw commands, and submits them to the backend for rendering.
     * 
     * @param screenWidth The width of the screen in pixels
     * @param screenHeight The height of the screen in pixels
     * @param block The block to display the name of (can be null)
     * @throws IllegalStateException if backend has not been set
     */
    public void render(int screenWidth, int screenHeight, Block block) {
        if (backend == null) {
            throw new IllegalStateException("Backend must be set before calling render()");
        }
        
        if (block == null || block.isAir()) {
            return; // Don't display anything if no block is targeted
        }
        
        String blockName = getDisplayName(block);
        if (blockName == null || blockName.isEmpty()) {
            return;
        }
        
        // Setup 2D projection for UI rendering (delegated to backend)
        backend.setup2DProjection(screenWidth, screenHeight);
        
        // Build draw commands using backend-agnostic logic
        buffer.clear();
        logic.buildBlockNameDisplayCommands(blockName, MARGIN, MARGIN, buffer);
        
        // Submit commands to backend with frame management
        backend.beginFrame();
        for (DrawCommand cmd : buffer.getCommands()) {
            backend.submit(cmd);
        }
        backend.endFrame();
        
        // Restore projection (delegated to backend)
        backend.restore2DProjection();
    }
    
    /**
     * Convert the block identifier to a display name.
     * Converts "mattmc:stone" to "Stone", "mattmc:grass_block" to "Grass Block", etc.
     */
    private String getDisplayName(Block block) {
        String identifier = block.getIdentifier();
        if (identifier == null) {
            return null;
        }
        
        // Extract block name from identifier (e.g., "mattmc:stone" -> "stone")
        String blockName = identifier.contains(":") 
            ? identifier.substring(identifier.indexOf(':') + 1) 
            : identifier;
        
        // Convert to title case and replace underscores with spaces
        // "grass_block" -> "Grass Block"
        String[] words = blockName.split("_");
        StringBuilder displayName = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (!word.isEmpty()) {
                // Capitalize first letter, lowercase the rest
                displayName.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    displayName.append(word.substring(1).toLowerCase());
                }
                
                // Add space between words (but not after the last word)
                if (i < words.length - 1) {
                    displayName.append(" ");
                }
            }
        }
        
        return displayName.toString();
    }
}
