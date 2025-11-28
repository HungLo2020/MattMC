package mattmc.world.level.block.state;

import mattmc.world.level.block.state.properties.Axis;
import mattmc.world.level.block.state.properties.Direction;
import mattmc.world.level.block.state.properties.Half;
import mattmc.world.level.block.state.properties.SlabType;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the state of a block with its properties.
 * Similar to MattMC's BlockState class.
 * 
 * BlockStates store property values for blocks like stairs (facing, half, shape) and pillars (axis).
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
     * Get an Axis property.
     */
    public Axis getAxis(String property) {
        Object value = properties.get(property);
        return value instanceof Axis ? (Axis) value : Axis.Y;
    }
    
    /**
     * Get a SlabType property.
     */
    public SlabType getSlabType(String property) {
        Object value = properties.get(property);
        return value instanceof SlabType ? (SlabType) value : SlabType.BOTTOM;
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
                
                // Try to parse as Axis
                try {
                    Axis axis = Axis.valueOf(str);
                    state.setValue(key, axis);
                    continue;
                } catch (IllegalArgumentException ignored) {}
                
                // Try to parse as SlabType
                try {
                    SlabType slabType = SlabType.valueOf(str);
                    state.setValue(key, slabType);
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
     * Convert this blockstate to a variant string for blockstate JSON lookup.
     * Example: "facing=north,half=bottom,shape=straight"
     * Properties are sorted alphabetically for consistent lookup.
     */
    public String toVariantString() {
        if (properties.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        // Sort properties alphabetically for consistent lookup
        properties.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(entry.getKey()).append("=");
                Object value = entry.getValue();
                // Convert enum values to lowercase strings (MattMC convention)
                // For other types, use toString() as-is
                if (value instanceof Enum) {
                    sb.append(((Enum<?>) value).name().toLowerCase());
                } else if (value instanceof String) {
                    // String values should be lowercase in blockstate variants
                    sb.append(((String) value).toLowerCase());
                } else {
                    // Other types (numbers, booleans) use their string representation as-is
                    sb.append(value.toString());
                }
            });
        return sb.toString();
    }
}
