package mattmc.client.renderer.item;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.world.item.ItemStack;

/**
 * Backend-agnostic interface for rendering items in the UI (hotbar, inventory, etc.).
 * 
 * <p>This interface abstracts the actual item rendering operations, allowing different
 * backend implementations (OpenGL, Vulkan, etc.) to provide their own rendering logic.
 * 
 * <p><b>Architecture:</b> This interface is used by:
 * <ul>
 *   <li>{@link mattmc.client.renderer.HotbarRenderer} - for rendering items in the hotbar</li>
 *   <li>{@link mattmc.client.gui.screens.inventory.InventoryRenderer} - for inventory screens</li>
 * </ul>
 * 
 * <p>For block items, renders an isometric 3D view by capturing the actual in-game
 * 3D geometry and projecting it to 2D screen coordinates.
 * For regular items, renders a 2D icon.
 * 
 * @see ItemDisplayContext
 * @see mattmc.client.renderer.backend.opengl.OpenGLItemRenderer OpenGL implementation
 */
public interface ItemRenderer {
    
    /**
     * Render an item at the specified screen position.
     * Renders block items as orthographic 3D cubes (isometric view).
     * 
     * @param stack The item stack to render
     * @param x Screen X position (center of item)
     * @param y Screen Y position (center of item)
     * @param size Size of the rendered item in pixels
     */
    void renderItem(ItemStack stack, float x, float y, float size);
    
    /**
     * Render an item at the specified screen position.
     * Renders block items as orthographic 3D cubes (isometric view).
     * 
     * @param stack The item stack to render
     * @param x Screen X position (center of item)
     * @param y Screen Y position (center of item)
     * @param size Size of the rendered item in pixels
     * @param applyInventoryOffset Apply +18f Y offset for inventory screen block item rendering
     */
    void renderItem(ItemStack stack, float x, float y, float size, boolean applyInventoryOffset);
    
    /**
     * Render an item using data-driven 3D perspective rendering with display transforms.
     * This method reads display transforms from the item's JSON model and applies them,
     * making it compatible with MattMC's data-driven rendering system.
     * 
     * @param stack The item stack to render
     * @param context The display context (GUI, firstperson, thirdperson, etc.)
     * @param x Screen X position (center of item)
     * @param y Screen Y position (center of item)
     * @param size Base size for rendering
     */
    void renderItemWithTransform(ItemStack stack, ItemDisplayContext context, float x, float y, float size);
    
    /**
     * Render an item using the backend architecture.
     * This method uses ItemRenderLogic to build commands and submits them to the backend.
     * 
     * @param stack the item stack to render
     * @param x screen X position
     * @param y screen Y position
     * @param size size of the item
     * @param backend the render backend to use
     */
    void render(ItemStack stack, float x, float y, float size, RenderBackend backend);
    
    /**
     * Render a fallback magenta square when texture is missing.
     * 
     * @param x Screen X position
     * @param y Screen Y position
     * @param size Size of the fallback item
     */
    void renderFallbackItem(float x, float y, float size);
    
    /**
     * Clear the texture cache.
     */
    void clearCache();
}
