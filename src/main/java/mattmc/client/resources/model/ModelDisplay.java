package mattmc.client.resources.model;

import mattmc.client.renderer.item.ItemDisplayContext;

import java.util.List;
import java.util.Map;

/**
 * Represents display transforms for different view modes in Minecraft's model format.
 * Controls how items/blocks appear in GUI, hand, head, etc.
 */
public class ModelDisplay {
    private Map<String, Transform> display;
    
    public Map<String, Transform> getDisplay() {
        return display;
    }
    
    public void setDisplay(Map<String, Transform> display) {
        this.display = display;
    }
    
    /**
     * Get transform for a specific display mode.
     * @param mode Display mode (gui, head, thirdperson_righthand, thirdperson_lefthand, etc.)
     * @return Transform for that mode, or null if not defined
     */
    public Transform getTransform(String mode) {
        return display != null ? display.get(mode) : null;
    }
    
    /**
     * Get transform for a specific display context.
     * Returns null if no transform is defined for this context.
     * 
     * @param context The display context enum
     * @return Transform for that context, or null if not defined
     */
    public Transform getTransform(ItemDisplayContext context) {
        return getTransform(context.getJsonKey());
    }
    
    /**
     * Represents a single display transform (rotation, translation, scale).
     */
    public static class Transform {
        private List<Float> rotation;    // [x, y, z] rotation in degrees
        private List<Float> translation; // [x, y, z] translation
        private List<Float> scale;       // [x, y, z] scale factors
        
        public List<Float> getRotation() {
            return rotation;
        }
        
        public void setRotation(List<Float> rotation) {
            this.rotation = rotation;
        }
        
        public List<Float> getTranslation() {
            return translation;
        }
        
        public void setTranslation(List<Float> translation) {
            this.translation = translation;
        }
        
        public List<Float> getScale() {
            return scale;
        }
        
        public void setScale(List<Float> scale) {
            this.scale = scale;
        }
    }
}
