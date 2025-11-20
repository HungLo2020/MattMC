package mattmc.client.renderer.item;

/**
 * Represents the context in which an item is being rendered.
 * This determines which display transform to use from the item model's "display" section.
 * 
 * Matches Minecraft's ItemDisplayContext enum for compatibility with vanilla JSON models.
 * 
 * Example usage in model JSON:
 * {
 *   "display": {
 *     "gui": { "rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625] },
 *     "ground": { "rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25] },
 *     "firstperson_righthand": { "rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.40, 0.40, 0.40] }
 *   }
 * }
 */
public enum ItemDisplayContext {
    /**
     * No specific context (fallback)
     */
    NONE,
    
    /**
     * Item held in third person view (left hand)
     */
    THIRDPERSON_LEFTHAND,
    
    /**
     * Item held in third person view (right hand)
     */
    THIRDPERSON_RIGHTHAND,
    
    /**
     * Item held in first person view (left hand)
     */
    FIRSTPERSON_LEFTHAND,
    
    /**
     * Item held in first person view (right hand)
     */
    FIRSTPERSON_RIGHTHAND,
    
    /**
     * Item worn on head (like pumpkins, mob heads)
     */
    HEAD,
    
    /**
     * Item displayed in GUI/inventory screens
     */
    GUI,
    
    /**
     * Item dropped on the ground
     */
    GROUND,
    
    /**
     * Item in an item frame
     */
    FIXED;
    
    /**
     * Get the JSON key for this context.
     * Converts enum name to lowercase for JSON compatibility.
     * 
     * @return The JSON key (e.g., "gui", "firstperson_righthand")
     */
    public String getJsonKey() {
        return this.name().toLowerCase();
    }
}
