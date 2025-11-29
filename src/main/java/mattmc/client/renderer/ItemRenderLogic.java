package mattmc.client.renderer;
import mattmc.client.renderer.backend.RenderPass;
import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.UIMeshIds;

import mattmc.world.item.ItemStack;
import java.util.HashMap;
import java.util.Map;

/**
 * Front-end logic for item rendering that builds draw commands without making GL calls.
 * 
 * <p>This class is responsible for determining how to render items (in GUI, first-person,
 * third-person contexts) and creating {@link DrawCommand} objects that describe the
 * rendering work. It does NOT make any OpenGL calls directly - that's delegated to the
 * {@link RenderBackend}.
 * 
 * <p><b>Architecture:</b> This is the "front-end" of item rendering:
 * <ul>
 *   <li><b>Front-end (this class):</b> Decides <em>what</em> to draw, builds commands</li>
 *   <li><b>Back-end (RenderBackend):</b> Decides <em>how</em> to draw, issues GL calls</li>
 * </ul>
 * 
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Determine appropriate display context for each item (GUI, first-person, etc.)</li>
 *   <li>Select correct mesh/model for item stack</li>
 *   <li>Compute transforms for item rendering (rotation, scale, position)</li>
 *   <li>Assign material/texture IDs</li>
 *   <li>Create and accumulate DrawCommand objects</li>
 * </ul>
 * 
 * <p><b>Design Note:</b> This separation allows:
 * <ul>
 *   <li>Testing without OpenGL context</li>
 *   <li>Easier debugging (inspect commands before rendering)</li>
 *   <li>Future optimization (batch similar items)</li>
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
public class ItemRenderLogic {
    
    // Instance-based item registry for proper lifecycle management
    // Each ItemRenderLogic instance has its own registry to support testing and isolation
    private final Map<Integer, ItemStackRenderInfo> itemRegistry = new HashMap<>();
    private int nextItemId = 1000; // Start at 1000 to avoid conflicts
    
    // Static reference for backward compatibility with OpenGLRenderBackend lookups
    // This is a temporary solution - ideally the backend would receive the registry reference
    private static ItemRenderLogic currentInstance = null;
    
    /**
     * Create a new ItemRenderLogic instance.
     * Registers itself as the current instance for static lookups.
     */
    public ItemRenderLogic() {
        currentInstance = this;
    }
    
    /**
     * Begin a new frame - clears the item registry.
     * Should be called at the start of each frame before building commands.
     */
    public void beginFrame() {
        itemRegistry.clear();
        nextItemId = 1000;
    }
    
    /**
     * Information needed to render an item.
     */
    public static class ItemStackRenderInfo {
        public final ItemStack stack;
        public final float x;
        public final float y;
        public final float size;
        
        public ItemStackRenderInfo(ItemStack stack, float x, float y, float size) {
            this.stack = stack;
            this.x = x;
            this.y = y;
            this.size = size;
        }
    }
    
    /**
     * Get item render info by transform index.
     * Used by OpenGLRenderBackend to retrieve item details.
     * Uses the current instance's registry for lookup.
     * 
     * @param transformIndex the transform index to look up
     * @return the ItemStackRenderInfo or null if not found
     */
    public static ItemStackRenderInfo getItemInfo(int transformIndex) {
        if (currentInstance != null) {
            return currentInstance.itemRegistry.get(transformIndex);
        }
        return null;
    }
    
    /**
     * Clear the item registry (call at end of frame).
     * @deprecated Use instance method {@link #beginFrame()} instead
     */
    @Deprecated
    public static void clearItemRegistry() {
        if (currentInstance != null) {
            currentInstance.beginFrame();
        }
    }
    
    /**
     * Builds draw commands for rendering an item stack.
     * 
     * <p>This method determines the appropriate mesh, material, and transform for
     * the given item stack and creates a DrawCommand.
     * 
     * @param stack the item stack to render
     * @param x screen X position
     * @param y screen Y position
     * @param size size of the item rendering
     * @param buffer the buffer to add commands to
     */
    public void buildItemCommand(ItemStack stack, float x, float y, float size, CommandBuffer buffer) {
        if (stack == null || stack.getItem() == null) {
            return;
        }
        
        String itemId = stack.getItem().getIdentifier();
        if (itemId == null || itemId.isEmpty()) {
            return;
        }
        
        // Extract item name from identifier
        String itemName = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        
        // Guard against empty itemName after splitting
        if (itemName.isEmpty()) {
            // Fallback rendering for malformed identifier
            DrawCommand cmd = new DrawCommand(UIMeshIds.ITEM_FALLBACK, -1, 0, RenderPass.UI);
            buffer.add(cmd);
            return;
        }
        
        // Get texture paths for this item
        java.util.Map<String, String> texturePaths = mattmc.client.resources.ResourceManager.getItemTexturePaths(itemName);
        
        if (texturePaths == null || texturePaths.isEmpty()) {
            // Create command for fallback rendering (magenta square)
            DrawCommand cmd = new DrawCommand(UIMeshIds.ITEM_FALLBACK, -1, 0, RenderPass.UI);
            buffer.add(cmd);
            return;
        }
        
        // Check if this is a block item (has block textures)
        boolean isBlockItem = texturePaths.containsKey("all") || texturePaths.containsKey("top") || 
                              texturePaths.containsKey("side") || texturePaths.containsKey("bottom");
        
        // For block items, check if it's stairs
        boolean isStairs = false;
        if (isBlockItem) {
            mattmc.client.resources.model.BlockModel itemModel = mattmc.client.resources.ResourceManager.resolveItemModel(itemName);
            String originalParent = itemModel != null ? itemModel.getOriginalParent() : null;
            isStairs = originalParent != null && originalParent.contains("stairs");
        }
        
        // Encode item rendering data in DrawCommand fields:
        // meshId: see UIMeshIds.ITEM_* constants
        // materialId: not used for items (0)
        // transformIndex: unique ID to look up item in registry
        
        int meshId;
        if (isBlockItem) {
            meshId = isStairs ? UIMeshIds.ITEM_STAIRS : UIMeshIds.ITEM_CUBE;
        } else {
            meshId = UIMeshIds.ITEM_FLAT;
        }
        
        // Register the item stack for backend rendering
        int registryId = nextItemId++;
        itemRegistry.put(registryId, new ItemStackRenderInfo(stack, x, y, size));
        
        DrawCommand cmd = new DrawCommand(meshId, 0, registryId, RenderPass.UI);
        buffer.add(cmd);
    }
    
    /**
     * Builds draw commands for rendering multiple items (e.g., in inventory grid).
     * 
     * @param stacks array of item stacks
     * @param startX starting X position
     * @param startY starting Y position
     * @param itemSize size of each item
     * @param spacing spacing between items
     * @param itemsPerRow items per row for grid layout
     * @param buffer the buffer to add commands to
     */
    public void buildInventoryItemCommands(ItemStack[] stacks, float startX, float startY, 
                                           float itemSize, float spacing, int itemsPerRow,
                                           CommandBuffer buffer) {
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] != null) {
                int row = i / itemsPerRow;
                int col = i % itemsPerRow;
                float x = startX + col * (itemSize + spacing);
                float y = startY + row * (itemSize + spacing);
                buildItemCommand(stacks[i], x, y, itemSize, buffer);
            }
        }
    }
}
