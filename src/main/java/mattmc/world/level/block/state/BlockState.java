package mattmc.world.level.block.state;

import mattmc.world.level.block.state.properties.Direction;
import mattmc.world.level.block.state.properties.Half;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the state of a block with its properties.
 * Similar to Minecraft's BlockState class.
 * 
 * BlockStates store property values for blocks like stairs (facing, half, shape).
 */
public class BlockState {
    private final Map<String, Object> properties;
    
    public BlockState() {
        this.properties = new HashMap<>();
    }
    
    /**
     * Set a property value.
     */
    public BlockState setValue(String property, Object value) {
        properties.put(property, value);
        return this;
    }
    
    /**
     * Get a property value.
     */
    public Object getValue(String property) {
        return properties.get(property);
    }
    
    /**
     * Get a Direction property.
     */
    public Direction getDirection(String property) {
        Object value = properties.get(property);
        return value instanceof Direction ? (Direction) value : Direction.NORTH;
    }
    
    /**
     * Get a Half property.
     */
    public Half getHalf(String property) {
        Object value = properties.get(property);
        return value instanceof Half ? (Half) value : Half.BOTTOM;
    }
    
    /**
     * Check if this blockstate has a property.
     */
    public boolean hasProperty(String property) {
        return properties.containsKey(property);
    }
    
    /**
     * Create a copy of this blockstate.
     */
    public BlockState copy() {
        BlockState copy = new BlockState();
        copy.properties.putAll(this.properties);
        return copy;
    }
    
    /**
     * Convert this blockstate to an NBT-compatible map.
     */
    public Map<String, Object> toNBT() {
        Map<String, Object> nbt = new HashMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Object value = entry.getValue();
            // Convert enum values to strings
            if (value instanceof Enum) {
                nbt.put(entry.getKey(), ((Enum<?>) value).name());
            } else {
                nbt.put(entry.getKey(), value);
            }
        }
        return nbt;
    }
    
    /**
     * Load a blockstate from an NBT-compatible map.
     */
    public static BlockState fromNBT(Map<String, Object> nbt) {
        BlockState state = new BlockState();
        for (Map.Entry<String, Object> entry : nbt.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Convert string values back to enums
            if (value instanceof String) {
                String str = (String) value;
                // Try to parse as Direction
                try {
                    Direction dir = Direction.valueOf(str);
                    state.setValue(key, dir);
                    continue;
                } catch (IllegalArgumentException ignored) {}
                
                // Try to parse as Half
                try {
                    Half half = Half.valueOf(str);
                    state.setValue(key, half);
                    continue;
                } catch (IllegalArgumentException ignored) {}
                
                // Try to parse as StairsShape
                try {
                    mattmc.world.level.block.state.properties.StairsShape shape = 
                        mattmc.world.level.block.state.properties.StairsShape.valueOf(str);
                    state.setValue(key, shape);
                    continue;
                } catch (IllegalArgumentException ignored) {}
                
                // Fall back to string
                state.setValue(key, value);
            } else {
                state.setValue(key, value);
            }
        }
        return state;
    }
    
    /**
     * Check if this blockstate is empty (has no properties).
     */
    public boolean isEmpty() {
        return properties.isEmpty();
    }
    
    /**
     * Convert this blockstate to a string key for blockstate variant lookup.
     * Format: "facing=north,half=bottom,shape=straight" (sorted alphabetically).
     * 
     * @return The state key string
     */
    @Override
    public String toString() {
        if (properties.isEmpty()) {
            return "";
        }
        
        // Sort properties alphabetically for consistent keys
        StringBuilder sb = new StringBuilder();
        properties.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(entry.getKey()).append('=');
                Object value = entry.getValue();
                // Convert enum values to lowercase names
                if (value instanceof Enum) {
                    sb.append(((Enum<?>) value).name().toLowerCase());
                } else {
                    sb.append(value.toString().toLowerCase());
                }
            });
        return sb.toString();
    }
}
