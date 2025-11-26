package mattmc.world.level.block.state;

import mattmc.world.level.block.state.properties.Axis;
import mattmc.world.level.block.state.properties.BlockStateProperties;
import mattmc.world.level.block.state.properties.Direction;
import mattmc.world.level.block.state.properties.Half;
import mattmc.world.level.block.state.properties.Property;
import mattmc.world.level.block.state.properties.StairsShape;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the state of a block with its properties.
 * Similar to Minecraft's BlockState class.
 * 
 * BlockStates store property values for blocks like stairs (facing, half, shape) and pillars (axis).
 * 
 * This class uses a type-safe Property&lt;T&gt; API for compile-time validation.
 */
public class BlockState {
    private final Map<String, Object> properties;
    
    public BlockState() {
        this.properties = new HashMap<>();
    }
    
    // ==================== Type-Safe API ====================
    
    /**
     * Set a property value using type-safe property.
     * 
     * @param property The property to set
     * @param value The value to set (must be a valid value for this property)
     * @return This blockstate for chaining
     * @throws IllegalArgumentException if value is not valid for this property
     */
    public <T extends Comparable<T>> BlockState setValue(Property<T> property, T value) {
        if (!property.isValidValue(value)) {
            throw new IllegalArgumentException("Value " + value + " is not valid for property " + property.getName() + 
                ". Valid values: " + property.getPossibleValues());
        }
        properties.put(property.getName(), value);
        return this;
    }
    
    /**
     * Get a property value using type-safe property.
     * 
     * @param property The property to get
     * @return The value, or the property's default value if not set
     */
    @SuppressWarnings("unchecked")
    public <T extends Comparable<T>> T getValue(Property<T> property) {
        Object value = properties.get(property.getName());
        if (value == null) {
            return property.getDefaultValue();
        }
        if (property.getValueClass().isInstance(value)) {
            return (T) value;
        }
        // Value exists but wrong type - try to parse it as a string
        if (value instanceof String) {
            java.util.Optional<T> parsed = property.parseValue((String) value);
            if (parsed.isPresent()) {
                // Cache the parsed value for future lookups
                properties.put(property.getName(), parsed.get());
                return parsed.get();
            }
        }
        // Could not parse - return default
        return property.getDefaultValue();
    }
    
    /**
     * Check if this blockstate has a specific property set.
     * 
     * @param property The property to check
     * @return true if the property is set
     */
    public boolean hasProperty(Property<?> property) {
        return properties.containsKey(property.getName());
    }
    
    // ==================== Utility Methods ====================
    
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
            
            // Convert string values back to enums based on property key
            if (value instanceof String) {
                String str = (String) value;
                Object parsedValue = parsePropertyValue(key, str);
                state.properties.put(key, parsedValue);
            } else {
                state.properties.put(key, value);
            }
        }
        return state;
    }
    
    /**
     * Parse a property value from string based on property name.
     */
    private static Object parsePropertyValue(String key, String str) {
        // Try to parse based on known property names
        switch (key) {
            case "facing":
                try { return Direction.valueOf(str); } catch (IllegalArgumentException ignored) {}
                break;
            case "half":
                try { return Half.valueOf(str); } catch (IllegalArgumentException ignored) {}
                break;
            case "axis":
                try { return Axis.valueOf(str); } catch (IllegalArgumentException ignored) {}
                break;
            case "shape":
                try { return StairsShape.valueOf(str); } catch (IllegalArgumentException ignored) {}
                break;
        }
        // Fall back to string
        return str;
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
                // Convert enum values to lowercase strings (Minecraft convention)
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
