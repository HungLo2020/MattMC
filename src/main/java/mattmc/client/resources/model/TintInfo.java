package mattmc.client.resources.model;

/**
 * Represents tint information for block/item models.
 * Similar to Minecraft's tint format in item models.
 * 
 * Example:
 * {
 *   "type": "minecraft:grass",
 *   "downfall": 1.0,
 *   "temperature": 0.5
 * }
 */
public class TintInfo {
    private String type;
    private float downfall;
    private float temperature;
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public float getDownfall() {
        return downfall;
    }
    
    public void setDownfall(float downfall) {
        this.downfall = downfall;
    }
    
    public float getTemperature() {
        return temperature;
    }
    
    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }
    
    /**
     * Calculate the tint color based on type and biome parameters.
     * For now, we use a simplified grass tint calculation.
     * 
     * @return RGB color value (e.g., 0x5BB53B for grass green)
     */
    public int getTintColor() {
        if ("minecraft:grass".equals(type) || "grass".equals(type)) {
            // Return grass green color
            // In full Minecraft, this would vary based on temperature and downfall
            // For now, we use the standard grass green
            return 0x5BB53B;
        }
        
        // Default: no tint (white)
        return 0xFFFFFF;
    }
}
