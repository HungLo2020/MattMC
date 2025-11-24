package mattmc.client.renderer;

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
    
    // Registry to store ItemStack references for backend rendering
    // This is a temporary solution for Stage 4 - maps transform index to ItemStack
    private static final Map<Integer, ItemStackRenderInfo> itemRegistry = new HashMap<>();
    private static int nextItemId = 1000; // Start at 1000 to avoid conflicts
    
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
     */
    public static ItemStackRenderInfo getItemInfo(int transformIndex) {
        return itemRegistry.get(transformIndex);
    }
    
    /**
     * Clear the item registry (call at end of frame).
     */
    public static void clearItemRegistry() {
        itemRegistry.clear();
        nextItemId = 1000;
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
        if (itemId == null) {
            return;
        }
        
        // Extract item name from identifier
        String itemName = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        
        // Get texture paths for this item
        mattmc.client.resources.ResourceManager resourceManager = new mattmc.client.resources.ResourceManager();
        java.util.Map<String, String> texturePaths = mattmc.client.resources.ResourceManager.getItemTexturePaths(itemName);
        
        if (texturePaths == null || texturePaths.isEmpty()) {
            // Create command for fallback rendering (magenta square)
            DrawCommand cmd = new DrawCommand(-2, -1, 0, RenderPass.UI);
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
        // meshId: -3 = isometric cube, -4 = isometric stairs, -5 = flat item, -2 = fallback
        // materialId: not used for items (0)
        // transformIndex: unique ID to look up item in registry
        
        int meshId;
        if (isBlockItem) {
            meshId = isStairs ? -4 : -3; // -3 = cube, -4 = stairs
        } else {
            meshId = -5; // -5 = flat item
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
